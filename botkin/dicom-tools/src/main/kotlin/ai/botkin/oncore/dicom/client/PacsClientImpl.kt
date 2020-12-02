package ai.botkin.oncore.dicom.client

import ai.botkin.oncore.dicom.util.DicomUtil
import ai.botkin.oncore.dicom.PacsClient
import ai.botkin.oncore.dicom.PacsDownloadHandle
import ai.botkin.oncore.dicom.client.exc.BotkinServiceExecuteException
import ai.botkin.oncore.dicom.client.exc.PacsExecutionException
import ai.botkin.oncore.dicom.depers.DeperService
import ai.botkin.oncore.dicom.dsl.DicomFactory
import ai.botkin.oncore.dicom.pipeline.DepersProcessor
import ai.botkin.oncore.dicom.pipeline.PacsSubscriber
import ai.botkin.oncore.dicom.pipeline.TransferState
import ai.botkin.oncore.dicom.pipeline.dto.ImageDto
import ai.botkin.oncore.dicom.pipeline.dto.TransferDto
import ai.botkin.oncore.dicom.service.*
import ai.botkin.oncore.dicom.service.dto.FindSCURequest
import ai.botkin.oncore.dicom.service.dto.GetSCURequest
import ai.botkin.oncore.dicom.service.dto.MoveSCURequest
import ai.botkin.oncore.dicom.service.dto.StoreSCURequest
import org.dcm4che3.data.Attributes
import org.dcm4che3.io.DicomInputStream
import org.dcm4che3.net.ApplicationEntity
import org.dcm4che3.net.Association
import org.dcm4che3.net.Connection
import org.dcm4che3.net.Device
import org.slf4j.LoggerFactory
import java.io.File
import java.io.IOException
import java.lang.IllegalStateException
import java.util.concurrent.*

class PacsClientImpl(
    val device: Device,
    val applicationEntity: ApplicationEntity,
    val myConnection: Connection,
    val pacsConnection: Connection,
    val dicomFactory: DicomFactory,
    val getSCUService: GetSCUService?,
    val moveSCUService: MoveSCUService?,
    val storeSCUService: StoreSCUService?,
    val findSCUService: FindSCUService?,
    val storeSCPService: StoreSCPService?,
    val depers: DeperService?
): PacsClient {

    private val restartRetryMs: Long = 1000 * 10
    private var logger = LoggerFactory.getLogger(PacsClientImpl::class.java)
    private var association: Association? = null
    init {
        start()
    }


    //--Lifecycle-------------------------------------------------------------------------------------------------------

    override fun start() {
        fun singleStart() {
            logger.info("Starting...")
            device.bindConnections()

            val start = Thread {
                association = applicationEntity.connect(
                    myConnection,
                    pacsConnection,
                    dicomFactory.buildAssociationRequest()
                )
            }
            start.start()
            start.join(1000 * 60 * 10)

            if (association == null) {
                throw TimeoutException("Artificial timeout on connection to target PACS")
            }
        }

        while (association == null) {
            try {
                singleStart()
            } catch (e: Exception) {
                logger.error("Fail to start client, retry in $restartRetryMs ms", e)
                release()
                try { Thread.sleep(restartRetryMs) } catch (ee: Exception) {}
            }
        }
    }


    private fun release() {
        logger.info("Release pacs client...")
        if (association != null && association?.isReadyForDataTransfer!!) {
            try {
                logger.info("waitForOutstandingRSP...")
                association?.waitForOutstandingRSP()
            } catch (e: InterruptedException) {
                logger.error("Error while waitForOutstandingRSP()", e)
            }
            try {
                logger.info("Association release...")
                association?.release()
                logger.info("Association state: ${association?.state}")
            } catch (e: IOException) {
                logger.error("Error releasing as", e)
            }
        }
        device.unbindConnections()
    }


    override fun restart() {
        release()
        start()
    }

    override fun shutdown() {
        release()
        DicomUtil.shutdownDevice(device)
        logger.info("Association state: ${association?.state}")
    }


    //--API-------------------------------------------------------------------------------------------------------------

    override fun findSync(cmd: FindSCURequest): List<Attributes> {
        if (association == null)
            throw BotkinServiceExecuteException("Client is not connected")

        if (findSCUService == null)
            throw BotkinServiceExecuteException("Find SCU is not configured")

        return safeExecute { findSCUService.execute(cmd, association!!) }
    }

    override fun get(cmd: GetSCURequest): PacsDownloadHandle<ImageDto> {
        if (association == null)
            throw BotkinServiceExecuteException("Client is not connected")

        if (getSCUService == null)
            throw BotkinServiceExecuteException("Get SCU is not configured")

        return object:PacsDownloadHandle<ImageDto>, Flow.Subscriber<TransferDto<ImageDto>> {

            private lateinit var serviceHandle: GetSCUService.ResponseHandler
            private lateinit var onImageCallback: (ImageDto) -> Unit
            private lateinit var subscription: Flow.Subscription
            private var accumulate = false
            private var result: MutableList<ImageDto> = mutableListOf()
            private var tmpFiles = mutableListOf<File>()

            override val future = object : CompletableFuture<List<ImageDto>>() {
                override fun cancel(mayInterrupt: Boolean): Boolean {
                    logger.info("Cancel get request by future cancel")
                    val cancelled = super.cancel(mayInterrupt)
                    if (cancelled)
                        serviceHandle.cancel()
                    return cancelled
                }

                override fun orTimeout(timeout: Long, unit: TimeUnit?): CompletableFuture<List<ImageDto>> {
                    return super
                        .orTimeout(timeout, unit)
                        .whenComplete { res, exc ->
                            if (exc is TimeoutException) {
                                serviceHandle.cancel()
                            }
                        }
                }
            }

            //--client-handle-----------------------------------------------------------------
            override fun start(): PacsDownloadHandle<ImageDto> {
                val publisher = SubmissionPublisher<TransferDto<ImageDto>>()
                if (depers != null) {
                    val dproc = DepersProcessor(depers)
                    publisher.subscribe(dproc)
                    dproc.subscribe(this)
                } else {
                    publisher.subscribe(this)
                }
                serviceHandle = getSCUService.execute(cmd, association!!, publisher)
                return this
            }

            override fun onEntry(block: (ImageDto) -> Unit): PacsDownloadHandle<ImageDto> {
                if (this::serviceHandle.isInitialized)
                    throw IllegalStateException("Already started")
                onImageCallback = block
                return this
            }

            override fun accumulate():  PacsDownloadHandle<ImageDto> {
                if (this::serviceHandle.isInitialized)
                    throw IllegalStateException("Already started")
                accumulate = true

                return this
            }

            override fun clear() {
                logger.info("Clearing handle")
                for (f in tmpFiles) {
                    try {
                        f.delete()
                    } catch (e: Exception) {
                        logger.error("Failed to delete tmp file: ${f.absolutePath}")
                    }
                }
            }

            //--service-handle----------------------------------------------------------------

            override fun onComplete() {
                synchronized(this) {
                    future.complete(result)
                }
            }

            override fun onSubscribe(p0: Flow.Subscription?) {
                subscription = p0!!
                subscription.request(1)
            }

            override fun onNext(p0: TransferDto<ImageDto>?) {
                if (p0 != null) {
                    when (p0.status) {
                        is TransferState.Receiving -> {
                            val data = p0.payload!!
                            if (accumulate)
                                result.add(data)

                            if (data.bulkDataFiles != null)
                                tmpFiles.addAll(data.bulkDataFiles)

                            try {
                                if (this::onImageCallback.isInitialized)
                                    synchronized(this) {
                                        onImageCallback(data)
                                    }
                            } catch (e: Exception) {
                                logger.warn("Failed image callback: ${e.message}")
                            }
                        }
                        else -> {}
                    }
                }
                subscription.request(1)
            }

            override fun onError(p0: Throwable?) {
                future.completeExceptionally(p0)
            }

        }
    }

    override fun move(cmd: MoveSCURequest) {
        if (association == null)
            throw BotkinServiceExecuteException("Client is not connected")

        if (moveSCUService == null)
            throw BotkinServiceExecuteException("Move SCU is not configured")

        moveSCUService.execute(cmd, association!!)
    }

    override fun store(cmd: StoreSCURequest, onSuccess: () -> Unit, onError: (cmd: Attributes) -> Unit) {
        if (association == null)
            throw BotkinServiceExecuteException("Client is not connected")

        if (storeSCUService == null)
            throw BotkinServiceExecuteException("Store SCU is not configured")
        storeSCUService.execute(cmd, association!!, onSuccess, onError)
    }

    override fun subscribeOnStore(subscriber: PacsSubscriber) {
        TODO("Not yet implemented")
    }

    //--Utils-----------------------------------------------------------------------------------------------------------

    private fun <T> safeExecute(block: () -> T): T {
        //TODO: validate execution and coordinate with reconnection policies
        try {
            return block()
        } catch(e: Exception) {
            logger.error("Failed during execution", e)

            if (!association?.isReadyForDataTransfer!!) {
                restart()
            }

            throw e
        }
    }


}