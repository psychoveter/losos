import botkin.ai.datamodel.Job
import botkin.ai.datamodel.JobDaoImpl
import botkin.ai.datamodel.Task
import botkin.ai.datamodel.TaskDaoImpl
import org.dizitart.kno2.nitrite
import org.dizitart.no2.NitriteId
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertNotNull


class TestNitrite {

    fun getId():Long{
        return NitriteId.newId().idValue
    }

    val db = nitrite{
        autoCommitBufferSize = 2048
        compress = true
        autoCompact = false
    }
    val taskDao = TaskDaoImpl(db.getRepository(Task::class.java))
    val jobDao = JobDaoImpl(db.getRepository(Job::class.java))

}

fun main() {
    val testDb = TestNitrite()

    val jobId1 = testDb.jobDao.createJob(
        Job(id = UUID.randomUUID().toString(),  startTime = Date(), endTime = null, status = "new",
        studyUID = "123.456", target = "DX")
    )
    Thread.sleep(3000)
    val jobId2 = testDb.jobDao.createJob(
        Job(id = UUID.randomUUID().toString(),  startTime = Date(), endTime = null, status = "new",
        studyUID = "123.456", target = "DX")
    )
    assertNotNull(jobId1)
    assertNotNull(jobId2)
    val job = testDb.jobDao.getEarliestJobByStatus("new")
    assertNotNull(job)
    assertEquals(job.id, jobId1)
    println(job)

//    testDb.tasks.insert(Task(2, 1, Date(), null, "ml", "new"))
//    val task = testDb.tasks.find(Task::id eq 2).firstOrDefault()
//
//
//    task.status = "processing"
//    testDb.tasks.update(task)
//
//    val job = testDb.jobs.find(Job::id eq 1).firstOrDefault()
//    job.tasks.add(task)
//    testDb.jobs.update(job)
//
//    val cursor = testDb.tasks.find(Task::status eq "processing")
//    for (task in cursor.iterator()){
//        println(task)
//    }
//
//    task.status = "done"
//    testDb.tasks.update(task)
//    val cursor1 = testDb.jobs.find(Task::id eq 1)
//    for (job in cursor1.iterator()){
//        println(job)
//    }
}