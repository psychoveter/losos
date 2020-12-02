package ai.botkin.oncore.dicom.service

import ai.botkin.oncore.dicom.client.exc.BotkinServiceExecuteException
import ai.botkin.oncore.dicom.service.dto.StoreSCURequest
import org.dcm4che3.data.Attributes
import org.dcm4che3.data.Tag
import org.dcm4che3.io.DicomInputStream
import org.dcm4che3.net.*
import org.slf4j.LoggerFactory
import java.io.ByteArrayInputStream

class StoreSCUService(

): BotkinDicomService {

    private val logger = LoggerFactory.getLogger(StoreSCUService::class.java)

    fun execute(
        cmd: StoreSCURequest,
        association: Association,
        onSuccess: () -> Unit,
        onError: (cmd: Attributes) -> Unit
    ) {
        logger.info("Received store request")
        try {
            val dicomInputStream = DicomInputStream(ByteArrayInputStream(cmd.data))
            dicomInputStream.includeBulkData = DicomInputStream.IncludeBulkData.URI

            val attributes = dicomInputStream.readDataset(-1, -1)
            val cuid = attributes.getString(Tag.SOPClassUID)
            val iuid = attributes.getString(Tag.SOPInstanceUID)

            val nextMessageID = association.nextMessageID()
            val handler = ResponseHandler(nextMessageID, onSuccess, onError)

            association.cstore(cuid, iuid, Priority.NORMAL, DataWriterAdapter(attributes), cmd.transferSyntax, handler)
        } catch (e: Exception) {
            throw BotkinServiceExecuteException("Failed to run cstore request", e)
        }

    }

    inner class ResponseHandler(
        val nextMessageId: Int,
        val onSuccess: () -> Unit = {},
        val onError: (cmd: Attributes) -> Unit = {}
    ): DimseRSPHandler(nextMessageId) {
        override fun onDimseRSP(association: Association, cmd: Attributes, data: Attributes?) {
            super.onDimseRSP(association, cmd, data)
            logger.info("onDimseRSP cmd $cmd data $data with messageId $nextMessageId")
            val status = cmd.getInt(Tag.Status, -1)
            if (status == Status.Success) {
                logger.info("File uploaded")
                onSuccess()
            } else {
                //http://dicom.nema.org/dicom/2013/output/chtml/part07/chapter_C.html
                //As we see in integration testing we should receive Success.
                logger.error("Error or warning storing data to PACS with status $status cmd $cmd ")
                onError(cmd)
            }
        }


    }

}