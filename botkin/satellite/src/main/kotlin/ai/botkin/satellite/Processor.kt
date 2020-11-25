package botkin.ai


import ai.botkin.satellite.task.*
import botkin.ai.datamodel.*
import botkin.ai.messages.*
import io.opentracing.Span
import io.opentracing.Tracer
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClientException
import java.util.*
import java.util.concurrent.ConcurrentLinkedDeque

@Component
class Processor: Thread() {

    private val logger = LoggerFactory.getLogger(javaClass)
    @Autowired
    lateinit var messageQueue: ConcurrentLinkedDeque<Message>
    @Autowired
    lateinit var client:RemoteClient


    @Autowired
    lateinit var tracer: Tracer
    @Autowired
    private lateinit var jobDao: JobDao
    @Autowired
    private lateinit var taskDao: TaskDao
    @Autowired
    private lateinit var quarantineDao: QuarantineDao

    private val jobIdToSpan = mutableMapOf<String, Span>()

    private fun getID():String{
        return UUID.randomUUID().toString()
    }

    override fun run() {
        logger.info("Started botkin.ai.Processor thread")
        sleep(5000)
        while (true){
            sleep(200)
            if (messageQueue.isNotEmpty()){
                val message = messageQueue.pop()
                logger.debug("Got message of type ${message::class}")
                when (message) {
                    is Schedule -> handleTaskRequest(message.workerType)
                    is ScheduleJob -> handleScheduleJob(message)
                    is Ok -> handleOk(message.taskId)
                    is Rejected -> handleRejected(message.taskId, message.reason)
                    is Done -> handleDone(message.taskId)
                    is Failed -> handleFailed(message.taskId, message.reason)
                    is Processing -> handleProcessing(message.taskId)
                }
            }
        }
    }
    private fun getTaskForWorker(workerType: String): String? {
        when (workerType) {
            "download" -> {
                val job = jobDao.getEarliestJobByStatus("new")
                if (job != null){
                    val downloadTask = Task(id = getID(), jobId = job.id, startTime = Date(), endTime = null,
                        workerType=workerType, status = "new")
                    val taskId = taskDao.createTask(downloadTask)
                    jobDao.updateJobStatus(job.id, "processing")
                    return taskId
                }
                return null
            }
            "ml" -> {
                val task = taskDao.getEarliestFinishedTaskByWorkerType("download")
                if (task != null){
                    val mlTask = Task(id = getID(), jobId = task.jobId, startTime = Date(), endTime = null,
                        workerType=workerType, status = "new")
                    val taskId = taskDao.createTask(mlTask)
                    taskDao.updateNext(task.id, taskId)
                    return taskId
                }
                return null
            }
            "report" -> {
                val task = taskDao.getEarliestFinishedTaskByWorkerType("ml")
                if (task != null){
                        val report = Task(id = getID(), jobId = task.jobId, startTime = Date(), endTime = null,
                            workerType=workerType, status = "new")
                        val taskId = taskDao.createTask(report)
                        taskDao.updateNext(task.id, taskId)
                        return taskId
                    }
                return null
            }

            "upload" -> {
                val task = taskDao.getEarliestFinishedTaskByWorkerType("report")
                if (task != null){
                    val uploadTask = Task(id = getID(), jobId = task.jobId, startTime = Date(), endTime = null,
                        workerType=workerType, status = "new")
                    val taskId = taskDao.createTask(uploadTask)
                    taskDao.updateNext(task.id, taskId)
                    return taskId
                }
                return null
            }
        }
        return null
    }
    private fun handleTaskRequest(workerType: String){
        val taskId = getTaskForWorker(workerType)
        try {
            if (taskId != null) {
                logger.info("Task id $taskId for worker $workerType")
                var currentJob = jobDao.getJobById(taskDao.getTaskById(taskId)!!.jobId)
    
                var seriesPath:String? = currentJob.seriesPath

                if (currentJob.seriesPath == null && workerType == "ml"){

                    seriesPath = DataProvider.chooseSeries(currentJob.studyUID, currentJob.target)
                    jobDao.addSeriesInfo(currentJob.id, seriesPath.toString())
                    currentJob = jobDao.getJobById(currentJob.id)

                    if(seriesPath == null){
                        //send notification to parent wf
                        logger.error("Could not choose series for study ${currentJob.studyUID}")
                        return
                    }
                }
                val markupPath = "${DataProvider.paths.markupBasePath}/${currentJob.studyUID}.json"

                when (workerType){
                    "ml" -> client.postTaskForML(workerType, Request(workerType,
                        listOf(
                            MLTask(taskId, seriesPath!!,
                                markupPath, currentJob.target)
                            )
                        )
                    , span = jobIdToSpan[currentJob.id]!!)
                    "report" -> client.postTaskReport(workerType, Request(workerType,
                        listOf(
                            ReportTask(taskId, seriesPath!!, markupPath, currentJob.target,
                            SavePaths("${DataProvider.paths.reportsBasePath}/${currentJob.studyUID}/sr.dcm",
                                      "${DataProvider.paths.reportsBasePath}/${currentJob.studyUID}/pr.dcm",
                                      "${DataProvider.paths.reportsBasePath}/${currentJob.studyUID}/SC")))),
                            span = jobIdToSpan[currentJob.id]!!
                            )
                    "download" -> client.postTaskGatewayDownload(workerType, Request(workerType,
                        listOf(DownloadTask(taskId, currentJob.studyUID,
                            "${DataProvider.paths.studiesBasePath}/${currentJob.studyUID}"))),
                            span = jobIdToSpan[currentJob.id]!!)
                    "upload" -> client.postTaskGatewayUpload(
                        workerType, Request(workerType, listOf(
                            UploadTask(taskId,
                            UploadFiles(
                                "${DataProvider.paths.reportsBasePath}/${currentJob.studyUID}/sr.dcm",
                                "${DataProvider.paths.reportsBasePath}/${currentJob.studyUID}/SC",
                                "${DataProvider.paths.reportsBasePath}/${currentJob.studyUID}/pr.dcm",
                                markupPath),
                                "destination")
                            )
                        ),
                        span = jobIdToSpan[currentJob.id]!!
                    )
                }
            }
            else{
                logger.info("There are no tasks for worker $workerType")
            }
        }
        catch (e: RestClientException){
            logger.error(e.message)
            if(taskId != null){
                taskDao.rollbackTask(taskId)
            }
        }
    }
    fun handleOk(taskId:String){
        try {
            taskDao.updateTaskStatus(taskId, "processing")
        }
        catch (e: Exception){
            logger.error(e.message)
        }
    }
    fun handleRejected(taskId:String, reason:String){
        try{
//            taskDao.updateTaskStatus(taskId, "rejected")
            taskDao.rollbackTask(taskId)
        }
        catch (e: Exception){
            logger.error(e.message)
        }
    }
    fun handleDone(taskId:String){
        try{
            taskDao.updateTaskStatus(taskId, "done")
            val task = taskDao.getTaskById(taskId)
            if (task != null){
                if (task.workerType == "upload"){
                    jobDao.updateJobStatus(task.jobId, "done")
                    jobIdToSpan[task.jobId]!!.finish()
                    jobIdToSpan.remove(task.jobId)


                }
            }
        }
        catch (e: Exception){
            logger.error(e.message)
        }
    }
    fun handleFailed(taskId:String, reason: String){
        try{
            //simplify
            taskDao.updateTaskStatus(taskId, "failed")
            taskDao.setError(taskId, reason)
            val task = taskDao.getTaskById(taskId)
            val job = jobDao.getJobById(task!!.jobId)
            jobDao.updateJobStatus(job.id, "failed")
            val quarantine = Quarantine(
                id=getID(),
                studyUID = job.studyUID,
                seriaUID = job.seriesUID.toString(),
                target = job.target,
                reason = reason,
                workerType = task.workerType)
            quarantineDao.createQuarantineReport(quarantine)
            val span = jobIdToSpan[job.id]
            span!!.setTag("error", reason)
            span.finish()
            jobIdToSpan.remove(job.id)
        }
        catch (e: Exception){
            logger.error(e.message)
        }
    }
    fun handleProcessing(taskId:String){
        //the need in processing message is in question
        taskDao.updateTaskStatus(taskId, "processing")
    }
    fun handleScheduleJob(message: ScheduleJob){
        val jobId = jobDao.createJob(Job(studyUID = message.studyUID, target= message.target))
        val span = tracer.buildSpan("Job").start()
        jobIdToSpan[jobId] = span
        span.log("Started Job $jobId for target ${message.target} and studyUID ${message.studyUID}")
        span.setTag("target", message.target)
        span.setTag("studyUID", message.studyUID)
    }
    private fun checkIfTaskIsAlreadySubmitted(workerType: String, jobId:String):Boolean{
        val task = taskDao.findTaskByWorkerTypeAndJobId(workerType, jobId)
        return task != null
    }
    init {
        Thread(this).start()
    }
}