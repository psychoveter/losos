package io.losos.executor

import com.fasterxml.jackson.databind.node.ObjectNode
import io.losos.Framework
import io.losos.actor.Actor
import io.losos.platform.LososPlatform
import io.losos.platform.Subscription
import io.losos.common.AgentDescriptor
import io.losos.common.AgentTask
import java.lang.Exception

class KotlinTaskExecutor(
    val agentName: String,
    val eventBus: LososPlatform,
    val descriptor: AgentDescriptor,
    val block: (input: AgentTask) -> ObjectNode
): Actor<AgentTask>() {

    private fun log(msg: String) {
        io.losos.log("[executor ${agentName}]: $msg")
    }

    companion object {

        /**
         * Run blocking
         */
        fun runExecutor(
            agentName: String,
            eventBus: LososPlatform,
            descriptor: AgentDescriptor,
            block: (input: AgentTask) -> ObjectNode
        ) {
            val executor = KotlinTaskExecutor(agentName, eventBus, descriptor, block)
            eventBus.runInKeepAlive("${LososPlatform.PREFIX_AGENT_LEASE}/$agentName") {
                executor.run()
            }
        }
    }

    private val tasksPath = LososPlatform.agentTasksPath(agentName)

    private var jobsSubscription: Subscription<ObjectNode>? = null


    override suspend fun beforeStart() {

        //subscribe for tasks
        log("subscribe for tasks at ${tasksPath}")
        jobsSubscription = eventBus.subscribe(tasksPath) {
            try {
                val task = Framework.json2object(it.payload, AgentTask::class.java)
                send(task)
            } catch (e: Exception) {
                log("Failed to convert payload to agent task: ${it.payload.toString()}")
                e.printStackTrace()
            }
        }

        //register as agent
        eventBus.register(agentName, descriptor)
    }

    override suspend fun afterStop() {
        log("after stop")
    }

    /**
     * Path /tasks/<agent_name>/<task_id> holds KV with task description.
     * Task receives serialized slots representation hold by running guard.
     * Example of the task:
     * {
     *   "type": "DoCalculus",
     *   "input": {
     *      "id": "task_unique_id",
     *      "type": "task_type",
     *      "payload": { "someField": "someDataHere" }
     *   }
     * }
     */
    override suspend fun process(message: AgentTask) {
        try {
            log("received task: ${message.toString()}")
            val result = block(message)
            success(message, result)
        } catch (e: Exception) {
            failure(message, e)
        }
    }

    private fun success(input: AgentTask, result: ObjectNode?) {
        eventBus.put(
                input.successEventPath,
                result?:Framework.jsonMapper.createObjectNode()
        )
    }


    private fun retry(input: AgentTask, e: Throwable?) {
        val payload = if( e == null ) Framework.jsonMapper.createObjectNode()
        else Framework.jsonMapper.createObjectNode().put("reason", e.message)
        eventBus.put(
                input.retryEventPath,
                payload
        )
    }

    private fun failure(input: AgentTask, e: Throwable?) {
        val payload = if( e == null ) Framework.jsonMapper.createObjectNode()
                      else Framework.jsonMapper.createObjectNode().put("reason", e.message)
        eventBus.put(
                input.failureEventPath,
                payload
        )
    }


}
