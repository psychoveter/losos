package ai.botkin.satellite.datamodel

import ai.botkin.satellite.Context
import ai.botkin.satellite.NitriteManager
import org.dizitart.kno2.filters.and
import org.dizitart.kno2.filters.eq
import org.dizitart.no2.IndexType
import org.dizitart.no2.objects.Id
import org.dizitart.no2.objects.Index
import org.dizitart.no2.objects.Indices
import org.dizitart.no2.objects.ObjectRepository
import org.springframework.stereotype.Component
import java.util.*

@Indices(Index(value = "status", type = IndexType.NonUnique))
data class Task(@Id val id:String, val jobId:String, val startTime: Date,
                var endTime: Date?, val workerType:String, var status: String,
                var next:String? = null, var error:String = "")

fun createFromWorkerTypeAndJobId(workerType: String, jobId: String): Task {
    return Task(id= UUID.randomUUID().toString(), jobId = jobId, startTime=Date(),
        endTime=null, status="new", workerType = workerType)
}

interface TaskDao{
    fun getEarliestFinishedTaskByWorkerType(workerType: String): Task?
    fun createTask(task: Task):String
    fun updateTaskStatus(taskId:String, status:String)
    fun getAll():List<Task>
    fun findTaskByWorkerTypeAndJobId(workerType: String, jobId: String): Task?
    fun getTaskById(id:String): Task?
    fun updateNext(currId:String,nextId: String)
    fun rollbackTask(taskId:String)
    fun removePendingTasks()
    fun setError(taskId:String, error:String)
}

//@Component
class TaskDaoImpl(private val tasks: ObjectRepository<Task> =
     NitriteManager.getDBInstance(Context()).getRepository(
        Task::class.java)): TaskDao
    {
        override fun setError(taskId:String, error: String) {
            val task = tasks.find(Task::id eq taskId).firstOrDefault()
            task.error = error
            tasks.update(task)
        }

        override fun getEarliestFinishedTaskByWorkerType(workerType: String): Task?{
        return tasks.find((Task::workerType eq workerType) and (Task::status eq "done")
                and (Task::next eq null) )
            .sortedBy { it.startTime }.firstOrNull()
    }

    override fun createTask(task: Task):String {
        tasks.insert(task)
        return task.id
    }

    override fun updateTaskStatus(taskId: String, status: String) {
        val task = tasks.find(Task::id eq taskId).firstOrDefault()
        task.status = status
        if (status == "done"){
            task.endTime = Date()
        }
        tasks.update(task)
    }

    override fun getAll(): List<Task> {
        return tasks.find().toList()
    }

    override fun findTaskByWorkerTypeAndJobId(workerType: String, jobId: String): Task? {
        return tasks.find((Task::jobId eq jobId)and (Task::workerType eq workerType)).firstOrNull()
    }

    override fun getTaskById(id: String): Task? {
        return tasks.find(Task::id eq id).firstOrNull()
    }

    override fun updateNext(currId:String,nextId: String) {
        val task = tasks.find(Task::id eq currId).firstOrDefault()
        task.next = nextId
        tasks.update(task)

    }

    override fun rollbackTask(taskId: String) {
        val prevTask = tasks.find(Task::next eq taskId).firstOrDefault()
        val curTask = tasks.find(Task::id eq taskId).firstOrDefault()
        prevTask.next = null
        tasks.remove(curTask)
    }

    override fun removePendingTasks() {
        // remove all pending tasks from db when pod restarts
        val taskIds = tasks.find(Task::status eq "processing").map { it.id }
        if (taskIds.isNotEmpty()){
            val prevTasks = taskIds.map { tasks.find(Task::next eq it).firstOrNull()}
            for (task in prevTasks){
                if(task != null){
                    tasks.remove(Task::id eq task.next)
                    task.next = null
                }
            }
        }
    }
}
