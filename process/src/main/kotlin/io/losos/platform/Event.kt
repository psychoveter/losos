package io.losos.platform

import com.fasterxml.jackson.databind.node.ObjectNode
import io.losos.common.InvocationResult
import io.losos.common.ProcessInfo
import io.losos.common.NodeInfo
import io.losos.common.ProcessDef
import io.losos.process.engine.ProcessStartCall


interface Event {
    val fullPath: String
}

data class JsonEvent(
    override val fullPath: String,
    val payload: ObjectNode
): Event

data class ActionEntryEvent(
    override val fullPath: String,
    val node: String,
    val pid: String,
    val actionDefId: String
): Event

data class GuardEntryEvent(
    override val fullPath: String,
    val payload: ObjectNode,
    val node: String,
    val pid: String,
    val guardDefId: String
): Event

data class InvocationEvent(
    override val fullPath: String,
    val payload: InvocationResult,
    val node: String,
    val pid: String,
    val guardDefId: String,
    val slotId: String
): Event

data class ProcessEvent(
    override val fullPath: String,
    val info: ProcessStartCall,
    val node: String,
    val pid: String
): Event

data class NodeEvent(
    override val fullPath: String,
    val info: NodeInfo,
    val node: String
): Event

data class LibraryEntryEvent(
    override val fullPath: String,
    val def: ProcessDef,
    val node: String
): Event