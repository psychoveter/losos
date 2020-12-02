package ai.botkin.oncore.dicom.service

import ai.botkin.oncore.dicom.util.DicomUtil
import ai.botkin.oncore.dicom.client.exc.PacsExecutionException
import ai.botkin.oncore.dicom.pipeline.dto.ImageDto
import ai.botkin.oncore.dicom.service.dto.FindSCURequest
import org.dcm4che3.data.Attributes
import org.dcm4che3.data.Tag
import org.dcm4che3.data.UID
import org.dcm4che3.net.Association
import org.dcm4che3.net.DimseRSPHandler
import org.dcm4che3.net.Priority
import org.dcm4che3.net.Status
import org.dcm4che3.tool.common.CLIUtils
import org.slf4j.LoggerFactory
import java.lang.Exception
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class FindSCUService(
    //TODO: make  async interface and move latch to interface sync wrapper method
    val syncAwaitTimeout: Long = 30000
) : BotkinDicomService {

    private val logger = LoggerFactory.getLogger(BotkinDicomService::class.java)

    /**
     * Attribute matching rules:
     * http://dicom.nema.org/medical/dicom/current/output/chtml/part04/sect_C.2.2.2.html
     *
     * Also check: http://dicom.nema.org/medical/dicom/current/output/chtml/part03/sect_C.4.23.html
     * FIND may return some entries but trying MOVE on them could fail
     */

    fun execute(
        cmd: FindSCURequest,
        association: Association): List<Attributes> {

        val input = Attributes()
        setLevel(input, cmd.level)

        //set attributes to be filled in response
        for (tag in cmd.returnTags) {
            CLIUtils.addAttributes(input, intArrayOf(tag))
        }
        //set matching keys
        for (match in cmd.matchingKeys) {
            val tagId = match.first
            val value = match.second
            addKey(input, tagId, value)
        }

        val nextMessageID = association.nextMessageID()

        //Temporary mechanism, blocks client thread
        val latch = CountDownLatch(1)
        val items = mutableListOf<Attributes>()
        var exception: PacsExecutionException? = null

        val rspHandler: DimseRSPHandler = object : DimseRSPHandler(nextMessageID) {


            override fun onDimseRSP(association: Association, cmd: Attributes, data: Attributes?) {
                try {
                    super.onDimseRSP(association, cmd, data)

                    val status = cmd.getInt(Tag.Status, -1)
                    val errorMsg = cmd.getString(Tag.ErrorComment)

                    logger.info("FIND-RSP: status: ${DicomUtil.getStatusString(status)}, error: ${errorMsg}")

                    if (logger.isDebugEnabled) {
                        DicomUtil.logAttributes(cmd, logger, "CMD")
                    }

                    if (logger.isTraceEnabled) {
                        if (data != null)
                            DicomUtil.logAttributes(data, logger, "DATA")
                    }
             

                    //Status semantics http://dicom.nema.org/medical/dicom/current/output/html/part04.html#sect_C.4.1.1.4
                    //Status semantics http://dicom.nema.org/medical/dicom/current/output/html/part07.html#sect_9.1.2.1.6
                    if (data != null) {
                            items.add(data)
                    }

                    if (status == Status.Success) {
                        logger.info("Find executed successfully")
                        latch.countDown()
                    }

                    if (isStatusNotOk(status)) {
                        DicomUtil.logAttributes(cmd, logger, "FIND-ERROR-CMD")
                        if (data != null)
                            DicomUtil.logAttributes(data, logger, "FIND-ERROR-DATA")
                        logger.error("Some error on find operation, status ($status: ${DicomUtil.getStatusString(status)})")
                        val errId = cmd.getInt(Tag.ErrorID, -1)
                        val errMsg = cmd.getString(Tag.ErrorComment)

                        exception = PacsExecutionException("Some error on find operation, " +
                            "status ($status: ${DicomUtil.getStatusString(status)}), " +
                            "errorId: $errId, " +
                            "errorComment: $errMsg")
                        latch.countDown()
                    }

                } catch (e: Exception) {
                    logger.error("Exception on FIND SCU", e)
                    latch.countDown()
                }
            }
        }

        logger.info("Run c-find")
        association.cfind(
            UID.StudyRootQueryRetrieveInformationModelFIND,
            Priority.NORMAL,
            input,
            null,
            rspHandler
        )

        latch.await(syncAwaitTimeout, TimeUnit.MILLISECONDS)
        if (exception != null)
            throw exception as PacsExecutionException

        return items
    }

    fun isStatusNotOk(status: Int): Boolean {
        return status == Status.OutOfResources ||
               status == Status.SOPclassNotSupported ||
               status == Status.Cancel ||
               status == Status.UnableToProcess
    }


}
