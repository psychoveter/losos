package ai.botkin.oncore.dicom

import ai.botkin.oncore.dicom.client.exc.BotkinServiceExecuteException
import ai.botkin.oncore.dicom.pipeline.PacsSubscriber
import ai.botkin.oncore.dicom.pipeline.TransferState
import ai.botkin.oncore.dicom.pipeline.dto.ImageDto
import ai.botkin.oncore.dicom.service.dto.FindSCURequest
import ai.botkin.oncore.dicom.service.dto.GetSCURequest
import ai.botkin.oncore.dicom.service.dto.MoveSCURequest
import ai.botkin.oncore.dicom.service.dto.StoreSCURequest
import ai.botkin.oncore.dicom.util.DicomUtil
import org.dcm4che3.data.Attributes
import org.dcm4che3.data.Tag
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

interface PacsClient {

    /**
     * Start all client resources
     */
    fun start()

    /**
     * Clean ups all resources of the client and restarts it
     */
    fun restart()

    /**
     * Clean ups all resources of the client and shutdown it (non restartable)
     */
    fun shutdown()


    /**
     * This get method is asynchronous
     */
    fun get(cmd: GetSCURequest): PacsDownloadHandle<ImageDto>

    /**
     * Sync implementation of get.
     * You have to clear ImageDto.bulkDataFile by yourself if you use this method
     */
    fun getSync(cmd: GetSCURequest, timeoutSec: Long = 5 * 60): List<ImageDto> {
        return get(cmd)
            .accumulate()
            .start()
            .future
            .orTimeout(timeoutSec, TimeUnit.SECONDS)
            .join()
    }

    fun findSync(cmd: FindSCURequest): List<Attributes>

    fun move(cmd: MoveSCURequest)

    fun store(cmd: StoreSCURequest, onSuccess: () -> Unit, onError: (cmd: Attributes) -> Unit)

    fun storeSync(cmd: StoreSCURequest, timeoutSec: Long = 5 * 60) {
        val latch = CountDownLatch(1)
        var error: Attributes? = null
        store(
            cmd,
            onSuccess = {
                latch.countDown()
            },
            onError = {
                error = it
                latch.countDown()
            }
        )

        if (!latch.await(timeoutSec, TimeUnit.SECONDS)) {
            throw BotkinServiceExecuteException("Timeout on storeSync: $timeoutSec sec")
        }

        if (error != null) {
            val status = error?.getInt(Tag.Status, -1)!!
            val errorId = error?.getInt(Tag.ErrorID, -1)
            val errorMsg = error?.getString(Tag.ErrorComment)

            throw BotkinServiceExecuteException(
                "Error while handling store: " +
                    "status: ($status:${DicomUtil.getStatusString(status)}," +
                    "errorId: ${errorId}," +
                    "errorComment: ${errorMsg}"
            )
        }
    }

    /**
     * Subscribes for incoming images
     * @throws BotkinConfigurationException if Store SCP is not configured
     */
    fun subscribeOnStore(subscriber: PacsSubscriber)
}

