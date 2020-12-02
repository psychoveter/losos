package ai.botkin.oncore.dicom.service

import ai.botkin.oncore.dicom.util.DicomUtil
import ai.botkin.oncore.dicom.pipeline.TransferState
import ai.botkin.oncore.dicom.pipeline.dto.ImageDto
import ai.botkin.oncore.dicom.pipeline.dto.TransferDto
import ai.botkin.oncore.dicom.service.dto.GetSCURequest
import org.dcm4che3.data.Attributes
import org.dcm4che3.data.Tag
import org.dcm4che3.data.UID
import org.dcm4che3.io.DicomInputStream
import org.dcm4che3.media.RecordType
import org.dcm4che3.net.*
import org.dcm4che3.net.pdu.PresentationContext
import org.dcm4che3.net.service.BasicCStoreSCP
import org.slf4j.LoggerFactory
import java.lang.IllegalStateException
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicBoolean

/**
 * TODO: add mammography and xray image storage SOP Classes
 * TODO: failover logic, correct association closing logic
 */
class GetSCUService(sopClasses: List<String>): BasicCStoreSCP(*sopClasses.toTypedArray()), BotkinDicomService {

    private val logger = LoggerFactory.getLogger(GetSCUService::class.java)

    private val handlers = ConcurrentHashMap<Association, ResponseHandler>()

    fun execute(
        cmd: GetSCURequest,
        association: Association,
        publisher: SubmissionPublisher<TransferDto<ImageDto>>
    ): ResponseHandler {
        if (handlers.contains(association)) {
            throw IllegalStateException("Another get operation is being processed on the association $association")
        }

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
        val handler = ResponseHandler(messageId, publisher, association)
        handlers[association] = handler

        association.cget(
            UID.StudyRootQueryRetrieveInformationModelGET,
            Priority.NORMAL,
            input,
            //TODO: why it happens?: if there actual ts, then client fails to find pair AS+TS...
            null,
            handler
        )
        val startedTransferState = TransferState.Started()
        publisher.submit(TransferDto(startedTransferState))

        return handler
    }


    override fun store(association: Association, pc: PresentationContext, rq: Attributes, data: PDVInputStream, rsp: Attributes) {
        logger.debug("Image received in cget for association $association")

        if (logger.isTraceEnabled) {
            DicomUtil.logAttributes(rq, logger, "GET-STORE-RQ")
        }

        val handler = handlers[association]
        if (handler == null) {
            logger.error("GET-STORE-RQ: No GET operation under processing, ignore request")
        } else {
            handler.store(association, pc, rq, data)
        }

        if (logger.isTraceEnabled) {
            DicomUtil.logAttributes(rsp, logger, "GET-STORE-RSP")
        }
    }

    override fun onClose(association: Association) {
        super.onClose(association)
        logger.info("Closing association: GET StoreSCP {}", association)

        if (association.exception != null)
            association.exception.printStackTrace()
    }


    /**
     * status semantics http://dicom.nema.org/medical/dicom/current/output/html/part07.html#sect_9.1.3.1.6
     */
    private fun computeTransferState(cmd: Attributes): TransferState {
        val status = cmd.getInt(Tag.Status, -1)
        val uid = cmd.getString(Tag.AffectedSOPInstanceUID)
        val completed = cmd.getInt(Tag.NumberOfCompletedSuboperations, 0)
        val failed = cmd.getInt(Tag.NumberOfFailedSuboperations, 0)
        val remaining = cmd.getInt(Tag.NumberOfRemainingSuboperations, 0)
        val warning = cmd.getInt(Tag.NumberOfWarningSuboperations, 0)
        val messageInitiatorId = cmd.getInt(Tag.MessageIDBeingRespondedTo, -1)
        val messageId = cmd.getInt(Tag.MessageID, -1)
        val errorId = cmd.getInt(Tag.ErrorID, -1)
        val errorMsg = cmd.getString(Tag.ErrorComment)

        return when {
            status == Status.Pending || status == Status.PendingWarning -> {
                TransferState.Receiving(
                    dicomStatus = status,
                    total = completed + failed + remaining + warning,
                    completed = completed,
                    remaining = remaining,
                    failed = failed,
                    warning = warning
                )
            }
            status == Status.Success -> {
                TransferState.Completed(
                    dicomStatus = status,
                    total = completed + failed + remaining + warning,
                    completed = completed,
                    failed = failed,
                    remaining = remaining,
                    warning = warning
                )
            }
            isStatusFailure(status) -> {
                TransferState.Failed(
                    dicomStatus = status,
                    total = completed + failed + remaining + warning,
                    completed = completed,
                    failed = failed,
                    remaining = remaining,
                    warning = warning,
                    reason = "errorId: $errorId, " +
                        "errorMessage: $errorMsg, " +
                        "status: ${DicomUtil.getStatusString(status)}"
                )
            }
            else -> {
                TransferState.Failed(
                    dicomStatus = status,
                    total = completed + failed + remaining + warning,
                    completed = completed,
                    failed = failed,
                    remaining = remaining,
                    warning = warning,
                    reason = "Got unknown status: ${DicomUtil.getStatusString(status)}, " +
                        "errorId: $errorId, " +
                        "errorMessage: $errorMsg"
                )
            }
        }
    }


    //===ResponseHandler================================================================================================

    inner class ResponseHandler(
        val msgId: Int,
        val publisher: SubmissionPublisher<TransferDto<ImageDto>>,
        val association: Association
    ) : DimseRSPHandler(msgId) {

        var isClosed = AtomicBoolean(false)

        var nextObjectCmd: Attributes? = null
        var nextTransferState: TransferState? = null
        var completed: Int = 0

        /**
         * Future: check multithreading behavior at dcm4che library level
         */
        override fun onDimseRSP(association: Association, cmd: Attributes, data: Attributes?) {
            super.onDimseRSP(association, cmd, data)

            if (logger.isTraceEnabled) {
                DicomUtil.logAttributes(cmd, logger, "GET-CMD")
            }

            nextObjectCmd = cmd
            nextTransferState = computeTransferState(cmd)

            logger.info("Next transfer state: ${nextTransferState.toString()}")

            when (nextTransferState) {
                is TransferState.Receiving -> {
                    //skip, wait store call with actual image
                }
                is TransferState.Completed -> {
                    logger.info("Process TransferState.Completed")
                    //complete downstream pipeline
                    publisher.submit(TransferDto(nextTransferState!!))
                    publisher.close()

//                    cleanup handler
                    handlers.remove(association)
                }
                is TransferState.Failed -> {
                    logger.info("Process TransferState.Failed")
                    //fail downstream pipeline
                    if (!publisher.isClosed) {
                        publisher.submit(TransferDto(nextTransferState!!))
                        publisher.close()
                    }

                    //cleanup handler
                    handlers.remove(association)
                }
                else -> throw IllegalStateException("Unexpected transfer status")
            }
        }

        fun store(association: Association, pc: PresentationContext, rq: Attributes, data: PDVInputStream) {
            if (isClosed.get()) {
                logger.warn("Received GET-STORE-RQ but is closed")
                return
            }

            if (nextTransferState == null) {
                //server doesn't send GET-RSP at each image, because it's optional
                //so we simulate progress response
                nextTransferState = TransferState.Receiving(
                    dicomStatus = Status.Pending,
                    completed = completed
                )
            }

            if (logger.isTraceEnabled) {
                DicomUtil.logAttributes(rq, logger, "GET-STORE-RQ")
            }

            //extract bulk data from attributes
            val includeBulkData = DicomInputStream.IncludeBulkData.URI
            val cuid = rq.getString(Tag.AffectedSOPClassUID)
            val iuid = rq.getString(Tag.AffectedSOPInstanceUID)
            val transferSyntax = pc.transferSyntax

            logger.info("Processing image, affected sopInstanceUID: $iuid")
            val imageDto = DicomInputStream(data).use { dis ->
                dis.includeBulkData = includeBulkData
                ImageDto(
                    sopInstanceUID = iuid,
                    dataset = dis.readDataset(-1, -1),
                    meta = association.createFileMetaInformation(iuid, cuid, transferSyntax),
                    includeBulk = includeBulkData,
                    bulkDataFiles = dis.bulkDataFiles
                )
            }

            val transferDto = TransferDto(
                status = nextTransferState!!,
                payload = imageDto
            )

            publisher.submit(transferDto)

            nextTransferState = null
            completed++
        }

        fun cancel() = synchronized(this) {
            if (isClosed.get())
                return

            logger.warn("Cancelling C-GET retrieval (msgId: $msgId)")
            val cancelRQ = Commands.mkCCancelRQ(msgId)
            val pc = association.pcFor(UID.StudyRootQueryRetrieveInformationModelGET, null)
            association.tryWriteDimseRSP(pc, cancelRQ)
            onClose(association)
        }

        override fun onClose(association: Association?): Unit = synchronized(this) {
            if (isClosed.get())
                return

            isClosed.set(true)

            logger.info("Close Get RSP Handler")
            //shutdown downstream pipeline? should be closed on c-get-rsp message
            //remove this from association
            if (!publisher.isClosed) {
                val dto = TransferDto<ImageDto>(TransferState.Failed(
                    dicomStatus = -1,
                    reason = "Association was exceptionally closed",
                    exception = association?.exception
                ))
                publisher.submit(dto)
                publisher.close()
            }

            handlers.remove(association)
        }


    }

    //==================================================================================================================

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