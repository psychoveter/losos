package ai.botkin.oncore.dicom.service

import ai.botkin.oncore.dicom.util.DicomUtil
import ai.botkin.oncore.dicom.pipeline.dto.ImageDto
import ai.botkin.oncore.dicom.pipeline.dto.TransferDto
import org.dcm4che3.data.Attributes
import org.dcm4che3.io.DicomInputStream
import org.dcm4che3.net.ApplicationEntity
import org.dcm4che3.net.Association
import org.dcm4che3.net.PDVInputStream
import org.dcm4che3.net.pdu.PresentationContext
import org.dcm4che3.net.service.BasicCStoreSCP
import org.slf4j.LoggerFactory
import java.lang.RuntimeException
import java.util.concurrent.SubmissionPublisher

class StoreSCPService(
    val applicationEntity: ApplicationEntity,
    val storeDir: String
): BasicCStoreSCP("*"), BotkinDicomService {

    private val logger = LoggerFactory.getLogger(StoreSCPService::class.java)

    private val fileStorage = FileStorage(storeDir)

    override fun store(
        association: Association,
        pc: PresentationContext,
        rq: Attributes,
        data: PDVInputStream,
        rsp: Attributes
    ) {
        DicomUtil.logAttributes(rq, logger, "STORE-RQ")
        try {
            fileStorage.storeToLocal(association, pc, rq, data, rsp)
        } catch (e: Exception) {
            logger.error("Failed to store file", e)
        }
    }

    override fun onClose(association: Association) {
        super.onClose(association)
    }
}