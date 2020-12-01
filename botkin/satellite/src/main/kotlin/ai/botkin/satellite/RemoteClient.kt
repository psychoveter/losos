package ai.botkin.satellite



import ai.botkin.satellite.task.*
import ai.botkin.satellite.tracing.CustomTracingRestTemplateInterceptor
import io.opentracing.Span
import io.opentracing.Tracer
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpEntity
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClientException
import org.springframework.web.client.RestTemplate





interface RemoteClient{

     fun postTaskForML(workerType: String, request: Request<MLTask>, span:Span)
     fun postTaskReport(workerType: String, request: Request<ReportTask>, span: Span)
     fun postTaskGatewayDownload(workerType: String, request: Request<DownloadTask>, span: Span)
     fun postTaskGatewayUpload(workerType: String, request: Request<UploadTask>, span: Span)
}

@Component
class RestClient: RemoteClient {

    private val logger = LoggerFactory.getLogger(javaClass)
    @Autowired
    lateinit var tracer: Tracer

    @Autowired
    lateinit var agentsConfig: AgentsConfig

    fun <T> postEntity(request: Request<T>, span: Span, url:String){

        val restTemplate = RestTemplate()
        restTemplate.interceptors.add(CustomTracingRestTemplateInterceptor(tracer, span=span))

        val message = restTemplate.postForObject(
            url,
            HttpEntity(request), String::class.java
        )
        logger.info("CLIENT: Message from the server: $message")
    }


    override fun postTaskForML(workerType: String, request: Request<MLTask>, span: Span) {
        try{


            postEntity(request, span, "${agentsConfig.ml}/task")
        }
        catch (e: RestClientException){
            logger.error(e.message)
            throw e
        }
    }

    override fun postTaskReport(workerType: String, request: Request<ReportTask>, span:Span) {
        try{

            postEntity(request, span, "${agentsConfig.reporter}/task",)

        }
        catch (e: RestClientException){
            logger.error(e.message)
            throw e
        }

    }

    override fun postTaskGatewayDownload(workerType: String, request: Request<DownloadTask>, span:Span) {
        try{


            postEntity(request, span, "${agentsConfig.gateway}/task/download")
        }
        catch (e: RestClientException){
            logger.error(e.message)
            throw e
        }
    }
    override fun postTaskGatewayUpload(workerType: String, request: Request<UploadTask>, span:Span) {
        try{

            postEntity(request, span, "${agentsConfig.gateway}/task/upload")
        }
        catch (e: RestClientException){
            logger.error(e.message)
            throw e
        }
    }
}



