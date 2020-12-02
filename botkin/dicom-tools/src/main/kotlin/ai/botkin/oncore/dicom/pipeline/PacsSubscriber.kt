package ai.botkin.oncore.dicom.pipeline

import ai.botkin.oncore.dicom.pipeline.dto.ImageDto
import ai.botkin.oncore.dicom.pipeline.dto.TransferDto
import org.slf4j.LoggerFactory
import java.util.concurrent.Flow

class PacsSubscriber(
    val errorHandler: (e: Throwable?) -> Unit = {},
    val completeHandler: () -> Unit = {},
    val dtoHandler: (dto: TransferDto<ImageDto>) -> Unit
): Flow.Subscriber<TransferDto<ImageDto>>{

    private val logger = LoggerFactory.getLogger(PacsSubscriber::class.java)

    private lateinit var subscription: Flow.Subscription

    override fun onComplete() {
        logger.info("Completed PacsSubscriber")
        completeHandler()
    }

    override fun onSubscribe(p0: Flow.Subscription?) {
        subscription = p0!!
        subscription.request(1)
    }

    override fun onNext(p0: TransferDto<ImageDto>?) {
        dtoHandler(p0!!)
        subscription.request(1)
    }

    override fun onError(p0: Throwable?) {
        logger.error("Error on processing", p0)
        errorHandler(p0)
    }

}