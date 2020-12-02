package ai.botkin.oncore.dicom.service

import ai.botkin.oncore.dicom.service.dto.MoveSCURequest
import org.dcm4che3.data.Attributes
import org.dcm4che3.data.Tag
import org.dcm4che3.data.UID
import org.dcm4che3.media.RecordType
import org.dcm4che3.net.Association
import org.dcm4che3.net.DimseRSPHandler
import org.dcm4che3.net.Status
import org.slf4j.LoggerFactory
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
        val handler = ResponseHandler(messageId, association)

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
        logger.info("C-Move done in $time")

    }

    inner class ResponseHandler(val messageId: Int, val association: Association): DimseRSPHandler(messageId) {
        override fun onDimseRSP(association: Association?, cmd: Attributes?, data: Attributes?) {
            super.onDimseRSP(association, cmd, data)

            val status = cmd?.getInt(Tag.Status, -1)
            if (status == Status.Success) {
                logger.info("C-Move successful for ... ")

            } else {
                logger.error("C-Move strange status $status")
            }
        }
    }

}