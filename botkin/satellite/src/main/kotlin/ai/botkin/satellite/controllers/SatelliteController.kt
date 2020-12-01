package ai.botkin.satellite.controllers

import ai.botkin.satellite.datamodel.*
import ai.botkin.satellite.messages.*
import ai.botkin.satellite.service.ServiceActionManagerImpl
import ai.botkin.satellite.task.JobRequest
import ai.botkin.satellite.datamodel.*
import ai.botkin.satellite.messages.*
import ai.botkin.satellite.task.SavePaths
import com.fasterxml.jackson.databind.ObjectMapper
import io.losos.process.planner.ServiceTask
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.util.concurrent.ConcurrentLinkedDeque

@RestController
@RequestMapping("/api")
class SatelliteController {
//
//    @Autowired
//    private lateinit var jobDao: JobDao
//    @Autowired
//    private lateinit var taskDao: TaskDao
//    @Autowired
//    private lateinit var quarantineDao: QuarantineDao

    @Autowired
    private lateinit var serviceActionManagerImpl: ServiceActionManagerImpl

//    @Autowired
//    lateinit var messageQueue: ConcurrentLinkedDeque<TEPMessage>

    private val logger = LoggerFactory.getLogger(javaClass)

    @GetMapping("/")
    fun index():ResponseEntity<String>{
        return ResponseEntity<String>(
            "index",
            HttpStatus.OK)
    }

    @GetMapping("/get_task/{workerType}")
    fun getTask(@PathVariable workerType:String):ResponseEntity<String>{
        //task request
        logger.debug("Got schedule request from $workerType worker")
//        messageQueue.add(Schedule(workerType=workerType))
        serviceActionManagerImpl.requestTask(workerType)
        return ResponseEntity<String>(
            "Ok",
            HttpStatus.OK)
    }

    @PostMapping("/ok")
    fun postOk(@RequestBody ok: Ok):ResponseEntity<String>{
        //confirm task
        logger.debug("Got ${ok} from worker ${ok.workerType}")
//        messageQueue.add(ok)
        serviceActionManagerImpl.addTEPMessage(ok)
        return ResponseEntity<String>(
            "ok", HttpStatus.OK)
    }

    @PostMapping("/rejected")
    fun postRejected(@RequestBody reject: Rejected):ResponseEntity<String>{
        //agent cannot take any tasks now
        logger.debug("Got ${reject} from worker ${reject.workerType}")
//        messageQueue.add(reject)
        serviceActionManagerImpl.addTEPMessage(reject)
        return ResponseEntity<String>(
            "ok", HttpStatus.OK)
    }

    @PostMapping("/failed")
    fun postFailed(@RequestBody failed: Failed):ResponseEntity<String>{
        //task failed during execution. retry or delete
        logger.debug("Got ${failed} from worker ${failed.workerType}")
//        messageQueue.add(failed)
        serviceActionManagerImpl.addTEPMessage(failed)
        return ResponseEntity<String>(
            "ok", HttpStatus.OK)
    }

    @PostMapping("/processing")
    fun PostMapping(){}


    @PostMapping("/done")
    fun postDone(@RequestBody done: Done):ResponseEntity<String>{
        //change task state to finished
        logger.debug("Got $done from worker ${done.workerType}")
//        messageQueue.add(done)
        serviceActionManagerImpl.addTEPMessage(done)
        return ResponseEntity<String>(
            "ok", HttpStatus.OK)
    }

    @GetMapping("/job/{workerType}")
    /**
     * testing function
     * */
    fun postJob(@PathVariable workerType: String):ResponseEntity<String>{
//        messageQueue.add(ScheduleJob(workerType = "satellite", studyUID = jobRequest.studyUID, target = jobRequest.target))
        val mapper = ObjectMapper()
        when (workerType) {
            "ml" -> {
                val node = mapper.createObjectNode()
                    .put("id", "ml-1")
                    .put("dicomPath",
                        "/tmp/dicoms/1.2.392.200036.9116.2.5.1.11341.1409398444.1586329368.638766/1.2.392.200036.9116.2.5.1.11341.1409398444.1586329856.857024")
                    .put("markupPath", "/tmp/markups/1.2.392.200036.9116.2.5.1.11341.1409398444.1586329368.638766.json")
                    .put("target", "FCT")
                serviceActionManagerImpl.invokeService(ServiceTask(ServiceActionManagerImpl.SERVICE_ML, "",
                    args = node), "")
            }
            "report" -> {
                val node  = mapper.createObjectNode()
                    .put("id", "report-1")
                    .put("dicomPath", "/tmp/dicoms/1.2.392.200036.9116.2.5.1.11341.1409398444.1586329368.638766/1.2.392.200036.9116.2.5.1.11341.1409398444.1586329856.857024")
                    .put("markupPath", "/tmp/markups/1.2.392.200036.9116.2.5.1.11341.1409398444.1586329368.638766.json")
                    .put("target", "FCT")
                    .putPOJO(
                        "savePaths",
                        SavePaths(
                            "/tmp/reports/1.2.392.200036.9116.2.5.1.11341.1409398444.1586329368.638766/sr.dcm",
                            "/tmp/reports/1.2.392.200036.9116.2.5.1.11341.1409398444.1586329368.638766/sr.dcm",
                            "/tmp/reports/1.2.392.200036.9116.2.5.1.11341.1409398444.1586329368.638766/SC"
                        )
                    )
                serviceActionManagerImpl.invokeService(ServiceTask(ServiceActionManagerImpl.SERVICE_REPORTER, "",
                    args = node), "")

            }

        }
        return ResponseEntity("ok", HttpStatus.OK)
    }
//    @GetMapping("/jobs")
//    //get all jobs
//    fun getJobs():ResponseEntity<List<Job>>{
//        return ResponseEntity(jobDao.getAll(), HttpStatus.OK)
//    }
//
//    @GetMapping("/jobs/{status}")
//    //get all jobs by status
//    fun getJobsByStatus(@PathVariable status:String):ResponseEntity<List<Job>>{
//        return ResponseEntity(jobDao.getJobsByStatus(status), HttpStatus.OK)
//    }
//    @GetMapping("/tasks")
//    //get all tasks
//    fun getTasks():ResponseEntity<List<Task>>{
//        return ResponseEntity(taskDao.getAll(),HttpStatus.OK)
//    }
//
//    @GetMapping("/quarantine")
//    //get all tasks
//    fun getQuarantineInfo():ResponseEntity<List<Quarantine>>{
//        return ResponseEntity(quarantineDao.getAll(),HttpStatus.OK)
//    }


}
