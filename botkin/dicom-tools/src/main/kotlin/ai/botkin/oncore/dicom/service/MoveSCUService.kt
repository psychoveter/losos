package ai.botkin.oncore.dicom.service

import ai.botkin.oncore.dicom.service.dto.MoveSCURequest
import ai.botkin.oncore.dicom.util.DicomUtil
import org.dcm4che3.data.Attributes
import org.dcm4che3.data.Tag
import org.dcm4che3.data.UID
import org.dcm4che3.media.RecordType
import org.dcm4che3.net.Association
import org.dcm4che3.net.DimseRSPHandler
import org.dcm4che3.net.Status
import org.slf4j.LoggerFactory
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.system.measureTimeMillis

class MoveSCUService(): BotkinDicomService {

    private val logger = LoggerFactory.getLogger(MoveSCUService::class.java)

    fun execute(
        cmd: MoveSCURequest,
        association: Association
    ) {
        val input = Attributes()
        setLevel(input, cmd.level)

        if (cmd.level == RecordType.STUDY) {
            addSimpleKey(input, Tag.StudyInstanceUID, cmd.studyInstanceUID!!)
        }

        if (cmd.level == RecordType.SERIES) {
            addSimpleKey(input, Tag.SeriesInstanceUID, cmd.seriesInstanceUID!!)
        }
        if (cmd.level == RecordType.IMAGE) {
            addSimpleKey(input, Tag.SOPInstanceUID, cmd.sopInstanceUID!!)
        }

        val messageId = association.nextMessageID()
        val answerLatch = CountDownLatch(1)
        val handler = ResponseHandler(messageId, association, answerLatch)

        //TODO: Laval implementation is blocking. So, take it into account!! Should be reimplemented
        val time = measureTimeMillis {
            association.cmove(
                UID.StudyRootQueryRetrieveInformationModelMOVE,
                0,
                input,
                null,
                cmd.destination,
                handler)
        }

        answerLatch.await(cmd.awaitTimeout, TimeUnit.MILLISECONDS)
        logger.info("C-Move done ${handler.isSuccess} in $time")
    }

    inner class ResponseHandler(
        val messageId: Int,
        val association: Association,
        val answerLatch: CountDownLatch
    ): DimseRSPHandler(messageId) {

        var isSuccess = false

        override fun onDimseRSP(association: Association?, cmd: Attributes?, data: Attributes?) {
            super.onDimseRSP(association, cmd, data)

            val status = cmd!!.getInt(Tag.Status, -1)

            if (isStatusFailure(status)) {
                logger.error("C-Move strange status $status: ${DicomUtil.getStatusString(status)}")
                answerLatch.countDown()
            }

            when (status) {
                Status.Success -> {
                    logger.info("C-Move successful for ... ")
                    isSuccess = true
                    answerLatch.countDown()
                }
                Status.Pending -> {
                    logger.info("C-Move pending")
                }
                Status.PendingWarning -> {
                    logger.info("C-Move pending warning")
                }
            }
        }
    }

    companion object {
        fun isStatusFailure(status: Int): Boolean =
            status == Status.SOPclassNotSupported ||
                    status == Status.Cancel ||
                    status == Status.ProcessingFailure ||
                    status == Status.DuplicateInvocation ||
                    status == Status.MistypedArgument ||
                    status == Status.UnrecognizedOperation ||
                    status == Status.NotAuthorized ||
                    status == Status.SOPclassNotSupported ||
                    status == Status.OutOfResources
    }

}