package io.losos.platform

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.databind.node.TreeTraversingParser
import io.etcd.jetcd.ByteSequence
import io.losos.common.*
import io.losos.process.engine.ProcessStartCall
import java.nio.charset.Charset
import java.lang.IllegalArgumentException

interface Subscription<T: Event> {
    val id: String
    val prefix: String
    val callback: suspend (T) -> Unit
    val type: Class<T>
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

        fun agentTasksPath(agentName: String)                      = "$PREFIX_TASKS/$agentName"
        fun agentTaskPath(agentName: String, taskId: String)       = "$PREFIX_TASKS/$agentName/${taskId}"
        fun agentTaskStatePath(taskId: String)                     = "$PREFIX_TASKS_STATE/${taskId}"
        fun agentLeasePath(agentName: String)                      = "$PREFIX_AGENT_LEASE/$agentName"


        //--serialization-utils-----------------------------------------------------------------------------------------


    }

    //--event-methods---------------------------------------------------------------------------------------------------

    /**
     * Return all events stored for this prefix by their revision
     */
    fun history(prefix: String): List<Event>

    fun <T: Event> subscribe(prefix: String, clazz: Class<T>, callback: suspend (e: T) -> Unit): Subscription<T>
    fun subscribe(prefix: String, callback: suspend (e: Event) -> Unit) =
        subscribe(prefix, Event::class.java, callback)

    fun <T: Event> subscribeDelete(path: String, clazz: Class<T>, callback: suspend (e: T) -> Unit): Subscription<T>
    fun subscribeDelete(path: String, callback: suspend (e: Event) -> Unit) =
        subscribeDelete(path, Event::class.java, callback)

    fun put(path: String, payload: Any)

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

    fun object2json(obj: Any?): ObjectNode = if (obj == null) jsonMapper.createObjectNode()
                                                         else jsonMapper.convertValue(obj, ObjectNode::class.java)

    fun emptyObject() = jsonMapper.createObjectNode()

    //TODO: remove, redundant
    fun fromJson(json: ObjectNode): ByteSequence = fromObject(json)

    fun fromObject(obj: Any): ByteSequence = ByteSequence.from(jsonMapper.writeValueAsBytes(obj))

    fun fromString(str: String): ByteSequence = ByteSequence.from(str, Charset.forName("UTF-8"))

    fun bytes2json(bytes: ByteSequence): ObjectNode  = bytes2object(bytes, ObjectNode::class.java)

    fun <T> bytes2object(bytes: ByteSequence, clazz: Class<T>) = jsonMapper.readValue(bytes.bytes, clazz)

    fun bytes2string(bytes: ByteSequence): String = bytes.toString(Charset.forName("UTF-8"))


    //--event-restoration-----------------------------------------------------------------------------------------------

    /**
     * Parses input event based on it's path structure.
     * @see io.losos.KeyConvention
     */
    fun restoreEvent(path: String, data: ByteSequence): Event {
        val tokens = path.split("/")
        if (tokens.size < 1)
            throw IllegalArgumentException("Unexpected key: $path")

        val event = when (tokens[1]) {
            "node" -> when(tokens[2]) {
                "registry" -> NodeEvent(path, bytes2object(data, NodeInfo::class.java), node = tokens[3])
                "library" -> LibraryEntryEvent(path, bytes2object(data, ProcessDef::class.java), tokens[3])
                "lease" -> NodeEvent(path, bytes2object(data, NodeInfo::class.java), node = tokens[3])
                else -> null
            }

            "proc" -> when(tokens[3]) {
                "registry" -> ProcessEvent(path, bytes2object(data, ProcessStartCall::class.java), tokens[2], tokens[4])
                "state" -> when (tokens[5]) {
                    "action" -> ActionEntryEvent(path, tokens[2], tokens[4], tokens[6])
                    "guard" -> GuardEntryEvent(path, bytes2json(data), tokens[2], tokens[4], tokens[6])
                    "invoke" -> InvocationEvent(path, bytes2object(data, InvocationResult::class.java),
                                                    tokens[2], tokens[4], tokens[6], tokens[7])
                    else -> null
                }
                else -> null
            }
            else -> null
        }

        return event ?: throw IllegalArgumentException("Unexpected event path: $path")
    }

}
