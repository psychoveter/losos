package ai.botkin.satellite.service

import ai.botkin.satellite.task.Request
import ai.botkin.satellite.tracing.CustomTracingRestTemplateInterceptor
import com.fasterxml.jackson.databind.node.ObjectNode
import io.losos.platform.LososPlatform
import io.losos.process.engine.NodeManager
import io.losos.process.engine.actions.ServiceActionConfig
import io.losos.process.planner.*
import io.opentracing.Span
import io.opentracing.Tracer
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import org.springframework.http.HttpEntity
import org.springframework.web.client.RestTemplate
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Service manager implements Task Execution Protocol
 */
class ServiceActionManagerImpl(
    val restTemplate: RestTemplate,
    val platform: LososPlatform,
    val tracer: Tracer
): ServiceActionManager {

    companion object {
        const val SERVICE_ML = "ml"
        const val SERVICE_REPORTER = "reporter"
        const val SERVICE_GATEWAY = "gateway"
    }

    private val serviceRegistry: Map<String, TEPServiceProvider> = mapOf(
        SERVICE_REPORTER to ReporterServiceProvider("http://192.168.2.5:8000", restTemplate, tracer),
        SERVICE_ML to MLServiceProvider("http://192.168.2.5:8001", restTemplate, tracer),
        SERVICE_GATEWAY to GatewayServiceProvider("http://192.168.2.5:8002", restTemplate, tracer)
    )

    private val taskQueue: Map<String, ConcurrentLinkedQueue<ServiceTask>> = mapOf (
        SERVICE_ML to ConcurrentLinkedQueue(),
        SERVICE_REPORTER to ConcurrentLinkedQueue(),
        SERVICE_GATEWAY to ConcurrentLinkedQueue()
    )

    private val taskRequests: MutableMap<String, Int> = mutableMapOf (
        SERVICE_ML to 0,
        SERVICE_REPORTER to 0,
        SERVICE_GATEWAY to 0
    )

    /**
     * This method is called by rest controller to request task for execution
     */
    fun requestTask(workerType: String) {
        taskRequests[workerType] = taskRequests[workerType]!! + 1
    }

    @Volatile var isRunning = true
    override fun start() {
        //start task post loop

    }

    override fun stop() {
        //stop task post loop
    }


    /**
     * @see io.losos.process.actions.InvocationAction
     */
    override fun invokeService(
        serviceTask: ServiceTask,
        resultEventPath: String
    ) {
        val workerType = serviceTask.workerType
        val taskType = serviceTask.taskType
        if (serviceRegistry.containsKey(workerType)) {
            taskQueue[workerType]!!.add(serviceTask)
        } else throw RuntimeException("No service of workerType $workerType")
    }
}


interface TEPServiceProvider {
    fun postTask(workerType: String)
}

abstract class AbstractTEPServiceProvider(
    val url: String,
    val rest: RestTemplate,
    val tracer: Tracer
): TEPServiceProvider {

    private val logger = LoggerFactory.getLogger(AbstractTEPServiceProvider::class.java)

    fun <T> postEntity(request: Request<T>, span: Span, url:String){

        rest.interceptors.add(CustomTracingRestTemplateInterceptor(tracer, span=span))

        val message = rest.postForObject(
            url,
            HttpEntity(request), String::class.java
        )
        logger.info("CLIENT: Message from the server: $message")
    }

}

class MLServiceProvider(
    url: String,
    rest: RestTemplate,
    tracer: Tracer
): AbstractTEPServiceProvider(url, rest, tracer) {
    override fun postTask(workerType: String) {
        TODO("Not yet implemented")
    }
}

class ReporterServiceProvider(
    url: String,
    rest: RestTemplate,
    tracer: Tracer
): AbstractTEPServiceProvider(url, rest, tracer) {
    override fun postTask(workerType: String) {
        TODO("Not yet implemented")
    }
}

//may be gateway task is better implement as async task inside satellite itself?..
class GatewayServiceProvider(
    url: String,
    rest: RestTemplate,
    tracer: Tracer
): AbstractTEPServiceProvider(url, rest, tracer) {
    override fun postTask(workerType: String) {
        TODO("Not yet implemented")
    }
}