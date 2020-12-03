package ai.botkin.satellite.scheduler

import java.time.LocalDateTime
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration
import kotlin.time.ExperimentalTime
import kotlin.time.seconds

enum class RetryPolicy{
    SIMPLE_RETRY,
}

class RetryConfig(val retries:Int = 3, val timeout: Long = 5000)

enum class TaskFinishState{
    DONE,
    FAILED,
    HUNG,
    REJECTED,
    NOTHING,
    TIMEOUT
}

class TaskState(val workerType: String, val startTime:Long, var currentDuration: Long = 0, var retries:Int = 0)

class TaskScheduler(val retryPolicy:RetryPolicy = RetryPolicy.SIMPLE_RETRY, val retryConfig:RetryConfig = RetryConfig()){

    private val tasks = ConcurrentHashMap <String, TaskState>()

    fun scheduleTask(workerType:String, taskId:String){
        val taskState = TaskState(workerType, System.currentTimeMillis())
        tasks[taskId] = taskState
    }
    fun updateTaskDuration(workerType: String){
        tasks[workerType]!!.currentDuration = System.currentTimeMillis() -  tasks[workerType]!!.startTime
    }

    fun getTaskState(taskId: String):TaskState{
        return tasks[taskId]!!
    }
    fun allowedToRetry(taskId: String):Boolean{
        val taskState = tasks[taskId]
        when(retryPolicy){
            RetryPolicy.SIMPLE_RETRY -> {
                if (taskState!!.retries < retryConfig.retries){
                    taskState.retries += 1
                    return true
                }
                return false
            }
        }
    }
    fun updateRetry(taskId: String){
        tasks[taskId]!!.retries += 1
    }

    fun removeTask(taskId: String){
        tasks.remove(taskId)
    }
}