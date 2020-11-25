package botkin.ai.datamodel

import botkin.ai.Context
import botkin.ai.NitriteManager
import org.dizitart.kno2.filters.eq
import org.dizitart.no2.objects.Id
import org.dizitart.no2.objects.ObjectRepository
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Bean
import org.springframework.stereotype.Component
import java.util.*

class Job(@Id val id:String = UUID.randomUUID().toString(),
          val startTime: Date = Date(),
          var endTime: Date? = null,
          var status: String = "new",
          val target: String, val studyUID:String,
          var seriesPath:String? = null,
          var seriesUID:String? = null)

fun createFromTargetAndStudyUID(target: String, studyUID: String): Job {
    return Job(startTime=Date(), status="new", target=target, studyUID=studyUID)
}

interface JobDao{
    fun getEarliestJobByStatus(status:String): Job?
    fun createJob(job: Job):String
    fun getAll():List<Job>
    fun updateJobStatus(jobId: String, status:String)
    fun getJobsByStatus(status:String):List<Job>
    fun getJobById(jobId:String):Job
    fun addSeriesInfo(jobId:String, seriesPath:String)
}

@Component
class JobDaoImpl(
    val jobs:ObjectRepository<Job> = NitriteManager.getDBInstance(Context()).getRepository(
        Job::class.java)): JobDao {

    override fun addSeriesInfo(jobId: String, seriesPath: String) {
        val job = jobs.find(Job::id eq jobId).firstOrDefault()
        job.seriesPath = seriesPath
        job.seriesUID = seriesPath.split("/").last()
        jobs.update(job)
    }

    override fun getEarliestJobByStatus(status:String): Job? {
        return jobs.find(Job::status eq status).sortedBy { it.startTime }.firstOrNull()
    }

    override fun createJob(job: Job):String {
        jobs.insert(job)
        return job.id
    }

    override fun getAll(): List<Job> {
        return jobs.find().toList()
    }

    override fun updateJobStatus(jobId: String, status: String) {
        val job = jobs.find(Job::id eq jobId).firstOrDefault()
        job.status = status
        if (status == "done"){
            job.endTime = Date()
        }
        jobs.update(job)
    }

    override fun getJobsByStatus(status: String):List<Job> {
        return jobs.find(Job::status eq status).toList()
    }

    override fun getJobById(jobId: String): Job {
        return jobs.find(Job::id eq jobId).firstOrDefault()
    }
}

