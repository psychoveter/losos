package ai.botkin.oncore.dicom.client

import ai.botkin.oncore.dicom.util.DicomUtil
import ai.botkin.oncore.dicom.PacsClient
import ai.botkin.oncore.dicom.PacsDownloadHandle
import ai.botkin.oncore.dicom.pipeline.PacsSubscriber
import ai.botkin.oncore.dicom.pipeline.dto.ImageDto
import ai.botkin.oncore.dicom.service.StoreSCPService
import ai.botkin.oncore.dicom.service.dto.FindSCURequest
import ai.botkin.oncore.dicom.service.dto.GetSCURequest
import ai.botkin.oncore.dicom.service.dto.MoveSCURequest
import ai.botkin.oncore.dicom.service.dto.StoreSCURequest
import org.dcm4che3.data.Attributes
import org.slf4j.LoggerFactory
import java.lang.UnsupportedOperationException

class PacsClientOnlyStoreSCPImpl(
    val storeScp: StoreSCPService
): PacsClient {

    private val logger = LoggerFactory.getLogger(PacsClientOnlyStoreSCPImpl::class.java)

    private val applicationEntity = storeScp.applicationEntity
    private val device = applicationEntity.device

    init { start() }

    override fun start() {
        logger.info("Starting...")
        device.bindConnections()
    }

    private fun release() {
        logger.info("Releasing...")
        device.unbindConnections()
    }

    override fun restart() {
        release()
        start()
    }

    override fun shutdown() {
        release()
        DicomUtil.shutdownDevice(device)
    }

    override fun get(cmd: GetSCURequest): PacsDownloadHandle<ImageDto> {
        throw UnsupportedOperationException()
    }

    override fun findSync(cmd: FindSCURequest): List<Attributes> {
        throw UnsupportedOperationException()
    }

    override fun move(cmd: MoveSCURequest) {
        throw UnsupportedOperationException()
    }

    override fun store(cmd: StoreSCURequest, onSuccess: () -> Unit, onError: (cmd: Attributes) -> Unit) {
        throw UnsupportedOperationException()
    }

    override fun subscribeOnStore(subscriber: PacsSubscriber) {

    }

}