package botkin.ai.controllers


import ai.botkin.satellite.task.JobRequest
import botkin.ai.datamodel.*
import botkin.ai.messages.*
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.util.concurrent.ConcurrentLinkedDeque

@RestController
@RequestMapping("/api")
class SatelliteController {

    @Autowired
    private lateinit var jobDao: JobDao
    @Autowired
    private lateinit var taskDao: TaskDao
    @Autowired
    private lateinit var quarantineDao: QuarantineDao

    @Autowired
    lateinit var messageQueue: ConcurrentLinkedDeque<Message>

    private val logger = LoggerFactory.getLogger(javaClass)

    @GetMapping("/")
    fun index():ResponseEntity<String>{
        return ResponseEntity<String>(
            "index",
            HttpStatus.OK);
    }

    @GetMapping("/get_task/{workerType}")
    fun getTask(@PathVariable workerType:String):ResponseEntity<String>{
        //task request
        logger.debug("Got schedule request from $workerType worker")
        messageQueue.add(Schedule(workerType=workerType))
        return ResponseEntity<String>(
            "Ok",
            HttpStatus.OK);
    }

    @PostMapping("/ok")
    fun postOk(@RequestBody ok: Ok):ResponseEntity<String>{
        //confirm task
        logger.debug("Got ${ok} from worker ${ok.workerType}")
        messageQueue.add(ok)

        return ResponseEntity<String>(
            "ok", HttpStatus.OK)
    }

    @PostMapping("/rejected")
    fun postRejected(@RequestBody reject: Rejected):ResponseEntity<String>{
        //agent cannot take any tasks now
        logger.debug("Got ${reject} from worker ${reject.workerType}")
        messageQueue.add(reject)
        return ResponseEntity<String>(
            "ok", HttpStatus.OK)
    }

    @PostMapping("/failed")
    fun postFailed(@RequestBody failed: Failed):ResponseEntity<String>{
        //task failed during execution. retry or delete
        logger.debug("Got ${failed} from worker ${failed.workerType}")
        messageQueue.add(failed)
        return ResponseEntity<String>(
            "ok", HttpStatus.OK)
    }

    @PostMapping("/processing")
    fun PostMapping(){}


    @PostMapping("/done")
    fun postDone(@RequestBody done: Done):ResponseEntity<String>{
        //change task state to finished
        logger.debug("Got $done from worker ${done.workerType}")
        messageQueue.add(done)
        return ResponseEntity<String>(
            "ok", HttpStatus.OK)
    }

    @PostMapping("/job")
    //getting job from parent wf
    fun postJob(@RequestBody jobRequest: JobRequest):ResponseEntity<String>{
        messageQueue.add(ScheduleJob(workerType = "satellite", studyUID = jobRequest.studyUID, target = jobRequest.target))
        return ResponseEntity("ok", HttpStatus.OK)
    }
    @GetMapping("/jobs")
    //get all jobs
    fun getJobs():ResponseEntity<List<Job>>{
        return ResponseEntity(jobDao.getAll(), HttpStatus.OK)
    }

    @GetMapping("/jobs/{status}")
    //get all jobs by status
    fun getJobsByStatus(@PathVariable status:String):ResponseEntity<List<Job>>{
        return ResponseEntity(jobDao.getJobsByStatus(status), HttpStatus.OK)
    }
    @GetMapping("/tasks")
    //get all tasks
    fun getTasks():ResponseEntity<List<Task>>{
        return ResponseEntity(taskDao.getAll(),HttpStatus.OK)
    }

    @GetMapping("/quarantine")
    //get all tasks
    fun getQuarantineInfo():ResponseEntity<List<Quarantine>>{
        return ResponseEntity(quarantineDao.getAll(),HttpStatus.OK)
    }


}
