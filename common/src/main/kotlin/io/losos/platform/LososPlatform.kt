package io.losos.platform

import com.fasterxml.jackson.databind.node.ObjectNode
import io.etcd.jetcd.ByteSequence
import io.losos.Framework
import java.nio.charset.Charset
import io.losos.common.AgentDescriptor


interface Subscription<T> {
    val id: String
    val prefix: String
    val callback: suspend (Event<T>) -> Unit
    fun cancel(): Unit
}

/**
 * It is not actually event bus, it is an event-oriented communication platform.
 */
interface LososPlatform {

    companion object {
        //--paths-------------------------------------------------------------------------------------------------------

        val PREFIX_AGENT_LEASE = "/agent_lease"
        val PREFIX_AGENTS      = "/agents"
        val PREFIX_TASKS       = "/tasks"
        val PREFIX_TASKS_STATE = "/state_tasks"

        fun agentTasksPath(agentName: String)                      = "${PREFIX_TASKS}/$agentName"
        fun agentTaskPath(agentName: String, taskId: String)       = "${PREFIX_TASKS}/$agentName/${taskId}"
        fun agentTaskStatePath(taskId: String)                     = "${PREFIX_TASKS_STATE}/${taskId}"
        fun agentLeasePath(agentName: String)                      = "${PREFIX_AGENT_LEASE}/$agentName"


        //--serialization-utils-----------------------------------------------------------------------------------------

        fun fromJson(json: ObjectNode): ByteSequence = ByteSequence.from(Framework.jsonMapper.writeValueAsBytes(json))

        fun fromString(str: String): ByteSequence = ByteSequence.from(str, Charset.forName("UTF-8"))

        fun bytes2json(bytes: ByteSequence): ObjectNode  = Framework.jsonMapper.readValue(bytes.bytes, ObjectNode::class.java)

        fun bytes2string(bytes: ByteSequence): String = bytes.toString(Charset.forName("UTF-8"))


    }

    //--event-methods---------------------------------------------------------------------------------------------------

    /**
     * Return all events stored for this prefix by their revision
     */
    fun history(prefix: String): List<Event<*>>

    fun subscribe(prefix: String, callback: suspend (e: Event<ObjectNode>) -> Unit): Subscription<ObjectNode>

    fun <T> subscribe(prefix: String, clazz: Class<T>, callback: suspend (e: Event<T>) -> Unit): Subscription<T>

    fun subscribeDelete(path: String, callback: suspend (e: Event<ObjectNode>) -> Unit): Subscription<ObjectNode>

    fun put(path: String, payload: ObjectNode)

    fun put(e: Event<*>)

    fun delete(path: String)

    /**
     * Read value for the specified key
     */
    suspend fun readOne(path: String): ObjectNode?

    /**
     * Read all keys and corresponding values matching this prefix
     */
    suspend fun readPrefix(prefix: String): Map<String, ObjectNode>

    //--process-methods-------------------------------------------------------------------------------------------------



    //--agent-menthods--------------------------------------------------------------------------------------------------
    // this methods are deprecated

    /**
     * Creates key which will be deleted when block finishes execution or crashes
     */
    fun runInKeepAlive(key: String, block: () -> Unit)

    fun register(agentName: String, descriptor: AgentDescriptor)

    fun deregister(agentName: String)

    fun invoke(actionId: String, actionType: String, params: ObjectNode?)

}
