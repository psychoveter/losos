package io.losos.eventbus

import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.databind.node.TreeTraversingParser
import io.losos.JsonObj
import io.etcd.jetcd.ByteSequence
import io.losos.Framework
import java.nio.charset.Charset
import io.losos.common.AgentDescriptor


interface Subscription {
    val id: String
    val prefix: String
    val callback: suspend (Event) -> Unit
    fun cancel(): Unit
}

interface EventBus {

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

        fun fromJson(json: JsonObj): ByteSequence = ByteSequence.from(Framework.jsonMapper.writeValueAsBytes(json))

        fun fromString(str: String): ByteSequence = ByteSequence.from(str, Charset.forName("UTF-8"))

        fun bytes2json(bytes: ByteSequence): JsonObj  = Framework.jsonMapper.readValue(bytes.bytes, JsonObj::class.java)

        fun bytes2string(bytes: ByteSequence): String = bytes.toString(Charset.forName("UTF-8"))


    }

    /**
     * Return all events stored for this prefix by their revision
     */
    fun history(prefix: String): List<Event>

    fun subscribe(prefix: String, callback: suspend (e: Event) -> Unit): Subscription

    fun subscribeDelete(path: String, callback: suspend (e: Event) -> Unit): Subscription

    fun emit(path: String, payload: JsonObj)

    fun emit(e: Event)

    /**
     * Read value for the specified key
     */
    suspend fun readOne(path: String): JsonObj?

    /**
     * Read all keys and corresponding values matching this prefix
     */
    suspend fun readPrefix(prefix: String): Map<String, JsonObj>

    /**
     * Creates key which will be deleted when block finishes execution or crashes
     */
    fun runInKeepAlive(key: String, block: () -> Unit)

    fun register(agentName: String, descriptor: AgentDescriptor)

    fun deregister(agentName: String)
}
