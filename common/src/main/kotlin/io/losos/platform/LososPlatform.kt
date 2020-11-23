package io.losos.platform

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.databind.node.TreeTraversingParser
import io.etcd.jetcd.ByteSequence
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


    }

    //--event-methods---------------------------------------------------------------------------------------------------

    /**
     * Return all events stored for this prefix by their revision
     */
    fun history(prefix: String): List<Event<*>>

    fun subscribe(prefix: String, callback: suspend (e: Event<ObjectNode>) -> Unit) =
        subscribe(prefix, com.fasterxml.jackson.databind.node.ObjectNode::class.java, callback)

    fun <T> subscribe(prefix: String, clazz: Class<T>, callback: suspend (e: Event<T>) -> Unit): Subscription<T>

    fun <T> subscribeDelete(path: String, clazz: Class<T>, callback: suspend (e: Event<T>) -> Unit): Subscription<T>
    fun subscribeDelete(path: String, callback: suspend (e: Event<ObjectNode>) -> Unit) =
        subscribeDelete(path, ObjectNode::class.java, callback)

    fun put(path: String, payload: Any)

    fun put(e: Event<*>)

    fun delete(path: String)



    /**
     * Read value for the specified key
     */
    fun <T> getOne(path: String, clazz: Class<T>): T?
    fun getOne(path: String) = getOne(path, ObjectNode::class.java)

    /**
     * Read all keys and corresponding values matching this prefix
     */
    fun <T> getPrefix(prefix: String, clazz: Class<T>): Map<String, T>
    fun getPrefix(prefix: String) = getPrefix(prefix, ObjectNode::class.java)



    //--process-methods-------------------------------------------------------------------------------------------------



    //--agent-menthods--------------------------------------------------------------------------------------------------
    // this methods are deprecated

    /**
     * Creates key which will be deleted when block finishes execution or crashes
     */
    fun runInKeepAlive(key: String, value: Any, block: () -> Unit)
    fun runInKeepAlive(key: String, block: () -> Unit) = runInKeepAlive(key, emptyObject(), block)

    fun register(agentName: String, descriptor: AgentDescriptor)

    fun deregister(agentName: String)

    fun invoke(actionId: String, actionType: String, params: ObjectNode?)


    //--json-methods----------------------------------------------------------------------------------------------------

    val jsonMapper: ObjectMapper

    fun <T> json2object(json: ObjectNode, clazz: Class<T>): T = jsonMapper.readValue(TreeTraversingParser(json), clazz)

    fun object2json(obj: Any): ObjectNode = jsonMapper.convertValue(obj, ObjectNode::class.java)

    fun emptyObject() = jsonMapper.createObjectNode()

    //TODO: remove, redundant
    fun fromJson(json: ObjectNode): ByteSequence = fromObject(json)

    fun fromObject(obj: Any): ByteSequence = ByteSequence.from(jsonMapper.writeValueAsBytes(obj))

    fun fromString(str: String): ByteSequence = ByteSequence.from(str, Charset.forName("UTF-8"))

    fun bytes2json(bytes: ByteSequence): ObjectNode  = bytes2object(bytes, ObjectNode::class.java)

    fun <T> bytes2object(bytes: ByteSequence, clazz: Class<T>) = jsonMapper.readValue(bytes.bytes, clazz)

    fun bytes2string(bytes: ByteSequence): String = bytes.toString(Charset.forName("UTF-8"))


}
