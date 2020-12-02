package ai.botkin.oncore.dicom.pipeline

import ai.botkin.oncore.dicom.depers.DeperService
import ai.botkin.oncore.dicom.pipeline.dto.ImageDto
import ai.botkin.oncore.dicom.pipeline.dto.TransferDto
import org.slf4j.LoggerFactory
import java.util.concurrent.Flow
import java.util.concurrent.SubmissionPublisher

class DepersProcessor(val depers: DeperService):
    SubmissionPublisher<TransferDto<ImageDto>>(),
    Flow.Processor<TransferDto<ImageDto>, TransferDto<ImageDto>>
{

    private val logger = LoggerFactory.getLogger(DepersProcessor::class.java)
    private lateinit var subs: Flow.Subscription

    override fun onComplete() {
        // I'm not sure it is correct way, should be checked: multithreading
        subscribers.forEach { it.onComplete() }
    }

    override fun onSubscribe(p0: Flow.Subscription?) {
        logger.info("Subscribed")
        subs = p0!!
        subs.request(1)
    }

    override fun onNext(dto: TransferDto<ImageDto>) {
        if (dto.payload?.dataset != null) {
            //stateful operation?...
            //current implementation
            //it should be stateless
            depers.depersonalize(dto.payload.dataset)
        }
        submit(dto)
        subs.request(1)
    }

    override fun onError(p0: Throwable?) {
        // I'm not sure it is  correct way, should be checked
        subscribers.forEach { it.onError(p0) }
    }


}