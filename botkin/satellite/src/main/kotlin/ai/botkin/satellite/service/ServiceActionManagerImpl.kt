package ai.botkin.satellite.service


import ai.botkin.satellite.kuberclient.Fabric8KubernetesClient
import ai.botkin.satellite.kuberclient.KubernetesClient
import ai.botkin.satellite.messages.Done
import ai.botkin.satellite.messages.Failed
import ai.botkin.satellite.messages.Ok
import ai.botkin.satellite.messages.TEPMessage
import ai.botkin.satellite.scheduler.TaskFinishState
import ai.botkin.satellite.scheduler.TaskScheduler
import ai.botkin.satellite.task.*
import ai.botkin.satellite.tracing.CustomTracingRestTemplateInterceptor
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.losos.platform.LososPlatform
import io.losos.process.planner.*
import io.opentracing.Span
import io.opentracing.Tracer
import kotlinx.coroutines.*
import kotlinx.coroutines.NonCancellable.isActive
import org.slf4j.LoggerFactory
import org.springframework.http.HttpEntity
import org.springframework.stereotype.Component
import org.springframework.web.client.RestTemplate
import java.lang.Exception
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.stream.Stream

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

//    @Autowired
//    lateinit var agentsConfig: AgentsConfig

    companion object {
        const val SERVICE_ML = "ml"
        const val SERVICE_REPORTER = "report"
        const val SERVICE_GATEWAY = "gateway"
    }



    private val serviceRegistry: Map<String, AbstractTEPServiceProvider> = mapOf(
        SERVICE_REPORTER to ReporterServiceProvider("http://192.168.2.2:8000",tracer),
        SERVICE_ML to MLServiceProvider("http://192.168.2.2:8001",  tracer),
        SERVICE_GATEWAY to GatewayServiceProvider("http://192.168.2.2:8002", tracer)
    )

    private val serviceErrorResolvers: Map<String, AbstractServiceErrorResolver> = mapOf(
        SERVICE_ML to MLServiceErrorResolver(serviceRegistry[SERVICE_ML]!!.url, RestTemplate(),
            Fabric8KubernetesClient()),
        SERVICE_REPORTER to ReportServiceErrorResolver(serviceRegistry[SERVICE_REPORTER]!!.url, RestTemplate(),
            Fabric8KubernetesClient()),
        SERVICE_GATEWAY to GatewayServiceErrorResolver(serviceRegistry[SERVICE_GATEWAY]!!.url, RestTemplate(),
            Fabric8KubernetesClient())
    )

    private val taskQueue: Map<String, ConcurrentLinkedQueue<ServiceTask>> = mapOf (
        SERVICE_ML to ConcurrentLinkedQueue(),
        SERVICE_REPORTER to ConcurrentLinkedQueue(),
        SERVICE_GATEWAY to ConcurrentLinkedQueue()
    )

    private val taskRequests = ConcurrentHashMap<String, Int>(
        mutableMapOf(
            SERVICE_ML to 0,
            SERVICE_REPORTER to 0,
            SERVICE_GATEWAY to 0
    ))

    fun MutableMap<String, Int>.isEmpty(key:String):Boolean{
        return this[key] == 0
    }

    private val tepMessageQueue =  mapOf(
        SERVICE_ML to mutableMapOf<String, Queue<TEPMessage>>(),
        SERVICE_REPORTER to mutableMapOf<String, Queue<TEPMessage>>(),
        SERVICE_GATEWAY to mutableMapOf<String, Queue<TEPMessage>>())

    private val scheduler = TaskScheduler()

    private fun cleanTaskContext(workerType:String, taskId:String){
        scheduler.removeTask(taskId)
        tepMessageQueue[workerType]!!.remove(taskId)
    }

    private suspend fun runServiceTask(workerType: String){
        val serviceTask = taskQueue[workerType]!!.poll()
        val taskId = serviceTask.args!!.get("id").asText()
        scheduler.scheduleTask(workerType, taskId)
        var status = TaskFinishState.NOTHING
        tepMessageQueue[workerType]!![taskId] = LinkedList<TEPMessage>()
        try {
            while(status != TaskFinishState.DONE && scheduler.allowedToRetry(taskId)){
                //TODO change to custom Coroutine scope
                val job = GlobalScope.async{processTask(taskId, serviceTask)}
                while (!job.isCompleted){
//                    logger.info("Checking $workerType service status")
                    val res = serviceErrorResolvers[workerType]!!.checkService()
                    if(!res){
                        status = TaskFinishState.HUNG
                        job.cancelAndJoin()
                        logger.info("Restarting hung $workerType service")
//                    serviceErrorResolvers[serviceTask.workerType]!!
//                        .kubernetesClient.deletePod("default", "")
                        cleanTaskContext(workerType, taskId)
                        return
                    }
                    delay(5000)
                }

                status = job.await()
                if (status == TaskFinishState.FAILED){
                    logger.info("Retrying task $taskId on $workerType")
                    delay(scheduler.retryConfig.timeout)
                    scheduler.updateRetry(taskId)
                }
            }
            when(status){
                TaskFinishState.FAILED -> {
                    logger.info("Failing task $taskId on $workerType")
                    //report failure
                    cleanTaskContext(workerType, taskId)
                    return
                }

                TaskFinishState.DONE -> {
                    //report completion
                    logger.info("Finishing task $taskId on $workerType")
                    cleanTaskContext(workerType, taskId)
                    return
                }
                //obsolete
                else -> throw RuntimeException("Task finished state is not defined")
            }
        }
        catch (e:RuntimeException){
            logger.error(e.toString())
        }
}
    private suspend fun processTask(taskId:String, serviceTask:ServiceTask):TaskFinishState {
            try {
                while (taskRequests.isEmpty(serviceTask.workerType)){
//                    logger.info("")
                    delay(1000)
                }
                if(!taskRequests.isEmpty(key = serviceTask.workerType)){
                    taskRequests[serviceTask.workerType] = taskRequests[serviceTask.workerType]!! - 1
                    when(serviceTask.workerType){
                        SERVICE_ML -> {
                            logger.info("Sending task to ML Service")

                            val requestML = assembleRequestML(serviceTask.args)
                            val mlProvider = serviceRegistry[SERVICE_ML]
                            mlProvider!!.postEntity(requestML, tracer.buildSpan(serviceTask.workerType).start(),
                                "${mlProvider.url}/task")
                        }

                        SERVICE_REPORTER -> {
                            logger.info("Sending task to REPORT Service")
                            val requestReport = assembleRequestReport(serviceTask.args)
                            val reportProvider = serviceRegistry[SERVICE_REPORTER]
                            reportProvider!!.postEntity(requestReport,
                                tracer.buildSpan(serviceTask.workerType).start(),
                                "${reportProvider.url}/task")
                        }
                    }
                }
                while (true){
                    if(tepMessageQueue[serviceTask.workerType]!![taskId]!!.isNotEmpty()) {
                            when (val message = tepMessageQueue[serviceTask.workerType]!![taskId]!!.poll()) {
                                is Ok -> {
                                    logger.info("Got Ok message from ${message.workerType} for task ${message.taskId}")
                                }
                                is Failed -> {
                                    logger.info(
                                        "Got Failed message from ${message.workerType} " +
                                                "for task ${message.taskId}, with reason: ${message.reason}"
                                    )

                                    return TaskFinishState.FAILED

                                }
                                is Done -> {
                                    logger.info("Got Done message from ${message.workerType} for task ${message.taskId}")
                                    //finish job and report success
                                    //taskQueue[workerType]!!.poll()
                                    return TaskFinishState.DONE
                                }
                            }

                    }
                    else{
//                            logger.info("Waiting for task request from $workerType service with task id $taskId")
//                            logger.info("Checking $workerType service state")
//                        logger.info("Waiting for messages from $workerType service for task $taskId")
                        delay(5000)
                        yield()
                    }
                }
                }

            catch (e:Exception){
                throw e
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

        tepMessageQueue[message.workerType]!![message.taskId]!!.add(message)
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

    fun assembleRequestML(args:ObjectNode?):Request<MLTask> {
        val mlTask = mapper.readValue(args!!.traverse(), MLTask::class.java)
        return Request("ml", listOf(mlTask))
    }


    fun assembleRequestReport(args:ObjectNode?):Request<ReportTask>{
        val reportTask = mapper.readValue(args!!.traverse(), ReportTask::class.java)
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
            GlobalScope.launch { runServiceTask(serviceTask.workerType) }
        } else throw RuntimeException("No service of workerType $workerType")
    }
}


interface TEPServiceProvider {
    fun postTask(workerType: String)

}

abstract class AbstractTEPServiceProvider(
    val url: String,
    val tracer: Tracer
): TEPServiceProvider {

    private val logger = LoggerFactory.getLogger(AbstractTEPServiceProvider::class.java)

    fun <T> postEntity(request: Request<T>, span: Span, url:String){
        val rest = RestTemplate()
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
    tracer: Tracer
): AbstractTEPServiceProvider(url, tracer) {
    override fun postTask(workerType: String) {
        TODO("Not yet implemented")
    }
}

class ReporterServiceProvider(
    url: String,
    tracer: Tracer
): AbstractTEPServiceProvider(url,tracer) {
    override fun postTask(workerType: String) {
        TODO("Not yet implemented")
    }
}

//may be gateway task is better implement as async task inside satellite itself?..
class GatewayServiceProvider(
    url: String,
    tracer: Tracer
): AbstractTEPServiceProvider(url, tracer) {
    override fun postTask(workerType: String) {
//        postEntity()
    }
}


interface ServiceErrorResolver{
    fun liveness(serviceUrl: String):Boolean
    fun checkService():Boolean

}

abstract class AbstractServiceErrorResolver(val url:String, val rest: RestTemplate,
                                            val kubernetesClient: KubernetesClient):ServiceErrorResolver{
    override fun liveness(serviceUrl:String): Boolean {
        return rest.getForObject("$url/liveness", LivenessStatus::class.java)!!.isAlive

    }

}


class MLServiceErrorResolver(url:String, rest: RestTemplate, kubernetesClient: KubernetesClient):
                                AbstractServiceErrorResolver(url, rest, kubernetesClient) {
    fun checkDrivers():Boolean{
        //ask ml service whether it has nvidia drivers installed
        return false
    }

    override fun checkService(): Boolean {
        val isAlive = liveness(url)
        return isAlive
    }
}
class ReportServiceErrorResolver(url:String, rest: RestTemplate, kubernetesClient: KubernetesClient):
    AbstractServiceErrorResolver(url, rest, kubernetesClient) {
    override fun checkService(): Boolean {
        val isAlive = liveness(url)
        return isAlive
    }
    //check liveness of python and c++ services
}
class GatewayServiceErrorResolver(url:String, rest: RestTemplate, kubernetesClient: KubernetesClient):
    AbstractServiceErrorResolver(url, rest, kubernetesClient) {
    override fun checkService(): Boolean {
        TODO("Not yet implemented")
    }

}

class LivenessStatus(val isAlive:Boolean)

