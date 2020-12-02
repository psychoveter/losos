package io.losos.process.planner

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import io.losos.common.AsyncTask
import io.losos.common.InvocationResult
import io.losos.platform.LososPlatform
import io.losos.process.engine.NodeManager
import org.slf4j.LoggerFactory
import java.sql.Time
import java.util.concurrent.*
import java.util.function.Supplier

enum class AsyncTaskStatus {
    CREATED, RUNNING, ERROR, FAILED, DONE
}

class AsyncTaskData (
    val type: String,
    val attempt: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long? = null,
    val status: AsyncTaskStatus = AsyncTaskStatus.CREATED,
    val reason: String? = null
) {
    fun update(
        type: String = this.type,
        attempt: Int = this.attempt,
        createdAt: Long = this.createdAt,
        updatedAt: Long = System.currentTimeMillis(),
        status: AsyncTaskStatus = this.status,
        reason: String? = this.reason
    ) = AsyncTaskData(type, attempt, createdAt, updatedAt, status, reason)
}

data class AsyncTaskCtx(
    val task: AsyncTask,
    val data: AsyncTaskData
)

class AsyncActionManager(
    val platform: LososPlatform
) {


    private val logger = LoggerFactory.getLogger(AsyncActionManager::class.java)

    private val executor = Executors.newScheduledThreadPool(2)

    lateinit var taskInitInterceptor: (AsyncTask) -> Unit

    private fun executeTask(
        task: AsyncTask,
        args: ObjectNode?,
        settings: ObjectNode?,
        dataPath: String,
        resultEventPath: String,
        data: AsyncTaskData,
        maxAttempt: Int,
        attemptDelay: Long
    ) {
        executor.schedule({
            var updata = data.update(attempt = data.attempt + 1, status = AsyncTaskStatus.RUNNING)
            try {
                logger.info("Start task execution: ${task.javaClass.name}, args: $args")
                platform.put(dataPath, updata)
                val result = task.execute(args, settings)
                val json = platform.object2json(result)
                updata = data.update(status = AsyncTaskStatus.DONE)
                platform.put(dataPath, updata)
                platform.put(resultEventPath, InvocationResult(json))
            } catch (e: Exception) {
                logger.error("Execution failed", e)
                if (updata.attempt > maxAttempt) {
                    logger.info("Attempts are exceeded, fail task")
                    updata = updata.update(status = AsyncTaskStatus.FAILED, reason = e.message)
                    platform.put(dataPath, updata)
                    platform.put(resultEventPath, InvocationResult.fail(
                        platform.emptyObject().put("reason", e.message)
                    ))
                } else {
                    logger.info("Schedule one more attempt")
                    updata = updata.update(status = AsyncTaskStatus.ERROR, reason = e.message)
                    platform.put(dataPath, updata)
                    executeTask(task, args, settings, dataPath, resultEventPath, updata, maxAttempt, attemptDelay)
                }
            }
        }, if(data.attempt == 0) 0 else attemptDelay, TimeUnit.MILLISECONDS)
    }


    fun executeAsyncAction (
        actionClass: String,
        dataPath: String,
        resultEventPath: String,
        args: ObjectNode?,
        settings: ObjectNode?
    ) {
        logger.info("Start schedule async task of type $actionClass")
        val taskInstance = javaClass.classLoader.loadClass(actionClass)
            .getDeclaredConstructor(LososPlatform::class.java)
            .newInstance(platform) as AsyncTask

        if (this::taskInitInterceptor.isInitialized)
            taskInitInterceptor.invoke(taskInstance)

        executeTask(
            task = taskInstance,
            args = args,
            settings = settings,
            dataPath = dataPath,
            resultEventPath = resultEventPath,
            data = AsyncTaskData(type = actionClass),
            maxAttempt = 10,
            attemptDelay = 5000
        )
    }

}