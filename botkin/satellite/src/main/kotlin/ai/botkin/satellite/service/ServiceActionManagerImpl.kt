package ai.botkin.satellite.service

import ai.botkin.satellite.kuberclient.Fabric8KubernetesClient
import ai.botkin.satellite.kuberclient.KubernetesClient
import ai.botkin.satellite.messages.Done
import ai.botkin.satellite.messages.Failed
import ai.botkin.satellite.messages.Ok
import ai.botkin.satellite.messages.TEPMessage
import ai.botkin.satellite.task.*
import ai.botkin.satellite.tracing.CustomTracingRestTemplateInterceptor
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.etcd.recipes.common.setTo
import io.losos.platform.LososPlatform
import io.losos.process.engine.NodeManager
import io.losos.process.engine.actions.ServiceActionConfig
import io.losos.process.planner.*
import io.opentracing.Span
import io.opentracing.Tracer
import kotlinx.coroutines.*
import org.slf4j.LoggerFactory
import org.springframework.http.HttpEntity
import org.springframework.stereotype.Component
import org.springframework.web.client.RestTemplate
import org.springframework.web.client.getForObject
import java.lang.Exception
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.math.log

/**
 * Service manager implements Task Execution Protocol
 */
@Component
class ServiceActionManagerImpl(

    val restTemplate: RestTemplate,
    val platform: LososPlatform,
    val tracer: Tracer

): ServiceActionManager {

    private val mapper = jacksonObjectMapper()

    private val logger = LoggerFactory.getLogger(ServiceActionManagerImpl::class.java.name)

    companion object {
        const val SERVICE_ML = "ml"
        const val SERVICE_REPORTER = "report"
        const val SERVICE_GATEWAY = "gateway"
    }



    private val serviceRegistry: Map<String, AbstractTEPServiceProvider> = mapOf(
        SERVICE_REPORTER to ReporterServiceProvider("http://192.168.2.2:8000", restTemplate, tracer),
        SERVICE_ML to MLServiceProvider("http://192.168.2.2:8001", restTemplate, tracer),
        SERVICE_GATEWAY to GatewayServiceProvider("http://192.168.2.2:8002", restTemplate, tracer)
    )

    private val serviceErrorResolvers: Map<String, AbstractServiceErrorResolver> = mapOf(
        SERVICE_ML to MLServiceErrorResolver("http://192.168.2.2:8000", restTemplate, Fabric8KubernetesClient()),
        SERVICE_REPORTER to ReportServiceErrorResolver("http://192.168.2.2:8000", restTemplate, Fabric8KubernetesClient()),
        SERVICE_GATEWAY to GatewayServiceErrorResolver("http://192.168.2.2:8000", restTemplate, Fabric8KubernetesClient())
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

    private val tepMessageQueue =  mapOf(
        SERVICE_ML to ConcurrentLinkedQueue<TEPMessage>(),
        SERVICE_REPORTER to ConcurrentLinkedQueue<TEPMessage>(),
        SERVICE_GATEWAY to ConcurrentLinkedQueue<TEPMessage>())



//    @Volatile var isRunning = true

    private suspend fun jobHandler(workerType:String){
            var finished = false
            try {
                while (taskQueue[workerType]!!.isEmpty()){
                    logger.info("Waiting for request from $workerType")
                    delay(1000)
                }
                    if (taskQueue[workerType]!!.isNotEmpty()){
                        var taskId:String? = null

                        val serviceTask = taskQueue[workerType]!!.poll()
                        if(taskRequests[serviceTask.workerType]!! > 0){
                            taskRequests[serviceTask.workerType] = taskRequests[serviceTask.workerType]!! - 1
                            when(serviceTask.workerType){
                                SERVICE_ML -> {
                                    logger.info("Sending task to ML Service")
                                    val requestML = assembleRequestML(serviceTask.args)
                                    taskId = requestML.tasks[0].id

                                    val mlProvider = serviceRegistry[SERVICE_ML]
                                    mlProvider!!.postEntity(requestML, tracer.buildSpan("123").start(),
                                        "${mlProvider!!.url}/task")
                                }

                                SERVICE_REPORTER -> {
                                    logger.info("Sending task to REPORT Service")
                                    val requestReport = assembleRequestReport(serviceTask.args)
                                    taskId = requestReport.tasks[0].id
                                    val reportProvider = serviceRegistry[SERVICE_REPORTER]
                                    reportProvider!!.postEntity(requestReport,
                                        tracer.buildSpan("123").start(),
                                        "${reportProvider!!.url}/task")
                                }
                            }
                        }
                        while (!finished){
                            if(tepMessageQueue[serviceTask.workerType]!!.isNotEmpty()){
                                when(val message = tepMessageQueue[serviceTask.workerType]!!.poll()) {
                                    is Ok -> {
                                        logger.info("Got Ok message from ${message.workerType} for task ${message.taskId}")
                                    }
                                    is Failed -> {
                                        logger.info("Got Failed message from ${message.workerType} for task ${message.taskId}")
                                        //finish job and report failure
                                        finished = true
                                    }
                                    is Done -> {
                                        logger.info("Got Done message from ${message.workerType} for task ${message.taskId}")
                                        //finish job and report completion
                                        finished = true
                                    }
                                }
                            }
                            else{
                                logger.info("Waiting for messages from service with task id $taskId")
                                logger.info("Checking $workerType service state")
                                val isAlive = serviceErrorResolvers[workerType]!!
                                    .liveness(serviceErrorResolvers[workerType]!!.url)
                                if (!isAlive){
                                    logger.error("$workerType service returned liveness - FALSE")
                                }
                                delay(5000)

                            }

                        }
                    }
                }

            catch (e:Exception){
                logger.error(e.toString())
                //report failure
            }
        }


    /**
     * This method is called by rest controller to request task for execution
     */
    fun requestTask(workerType: String) {
        taskRequests[workerType] = taskRequests[workerType]!! + 1
    }

    /**
     * This method is called by rest controller to pass message from service to ServiceActionManager
     * */
    fun addTEPMessage(message: TEPMessage){
        //maybe service watchdog should use this method when killing service process
        tepMessageQueue[message.workerType]!!.add(message)
    }


    override fun start() {
        //start task post loop
//        while (isRunning){
            Thread.sleep(1000)
//        }
    }

    override fun stop() {
        //stop task post loop
    }

    fun assembleRequestML(args:ObjectNode):Request<MLTask> {
        val mlTask = mapper.readValue(args.traverse(), MLTask::class.java)
        return Request("ml", listOf(mlTask))
    }


    fun assembleRequestReport(args:ObjectNode):Request<ReportTask>{
        val reportTask = mapper.readValue(args.traverse(), ReportTask::class.java)
        return Request("report", listOf(reportTask))
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
            GlobalScope.launch { jobHandler(serviceTask.workerType) }
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
    fun handleOk(taskId:String){

    }
    fun handleRejected(taskId:String, reason:String){
        //publish to platform
    }
    fun handleFailed(taskId:String, reason:String){
        //publish to platform
    }
    fun handleDone(taskId: String){
        //publish to platform
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
//        postEntity()
    }
}


interface ServiceErrorResolver{
    fun liveness(serviceUrl: String):Boolean

}

abstract class AbstractServiceErrorResolver(val url:String, val rest: RestTemplate,
                                            val kubernetesClient: KubernetesClient):ServiceErrorResolver{
    override fun liveness(serviceUrl:String): Boolean {
        val response = rest.getForObject("$url/liveness", LivenessStatus::class.java)
        return response!!.isAlive
    }
}


class MLServiceErrorResolver(url:String, rest: RestTemplate, kubernetesClient: KubernetesClient):
    AbstractServiceErrorResolver(url, rest, kubernetesClient) {
    fun checkDrivers():Boolean{
        //ask ml service whether it has nvidia drivers installed
        return false
    }
}
class ReportServiceErrorResolver(url:String, rest: RestTemplate, kubernetesClient: KubernetesClient):
    AbstractServiceErrorResolver(url, rest, kubernetesClient) {
    //check liveness of python and c++ services
}
class GatewayServiceErrorResolver(url:String, rest: RestTemplate, kubernetesClient: KubernetesClient):
    AbstractServiceErrorResolver(url, rest, kubernetesClient) {

}

class LivenessStatus(val isAlive:Boolean)

enum class RetryPolicy{
    SIMPLE_RETRY,
}

class RetryConfig(val retries:Int, timeout:Int = 30)
class TaskScheduler(val retryPolicy:RetryPolicy = RetryPolicy.SIMPLE_RETRY, retryConfig:RetryConfig){

}