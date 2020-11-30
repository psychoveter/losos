package io.losos.process.engine.actions

import com.fasterxml.jackson.databind.node.ObjectNode
import io.losos.common.InvocationExitCode


/**
 * Action input accepts slots of activated guard
 */

open class ActionInput(
    val result: InvocationExitCode = InvocationExitCode.OK,
    val reason: ObjectNode? = null
) {

}

class ActionInputSingle<T>(
    val data: T?,
    result: InvocationExitCode = InvocationExitCode.OK,
    reason: ObjectNode? = null
): ActionInput(result, reason) {
    override fun toString() = "SingleInput: $data"
}

class ActionInputList<T>(
    val data: List<T?>,
    result: InvocationExitCode = InvocationExitCode.OK,
    reason: ObjectNode? = null
): ActionInput(result, reason) {
    override fun toString() = "ListInput: [${data.map { it.toString() }.joinToString(separator = ",")}]"
}

class ActionInputMap(
    val data: Map<String, Any?>,
    result: InvocationExitCode = InvocationExitCode.OK,
    reason: ObjectNode? = null
): ActionInput(result, reason) {
    operator fun get(key: String) = data[key]
    override fun toString() = "MapInput: $data"
}


//data class ActionInput(
//    val slots: Map<String, Slot>,
//    private val platform: LososPlatform
//) {
//
//    @Suppress("UNCHECKED_CAST")
//    operator fun <T: Slot> get(slotId: SlotId<T>): T? {
//        return slots[slotId.name] as? T
//    }
//


//
//    fun <T: Slot> data(slotId: SlotId<T>): ObjectNode? = data(slotId, ObjectNode::class.java)
//
//    fun <T: Slot, P> data(slotId: SlotId<T>, clazz: Class<P>): P? {
//        val slot = get(slotId) ?: return null
//        if (slot is SlotWithValue<*>) {
//            val payload = slot.data ?: return null
//
//            return if (clazz == ObjectNode::class.java) {
//                if (payload is ObjectNode)
//                    payload as P
//                else
//                    platform.object2json(payload) as P
//            } else {
//                if (payload is ObjectNode)
//                    platform.json2object(payload, clazz)
//                else
//                    payload as P
//            }
//        }
//        return null
//    }
//
//    fun jsonData(platform: LososPlatform): ObjectNode {
//        val result = platform.jsonMapper.createObjectNode()
//        slots.values
//            .filterIsInstance<SlotWithValue<*>>()
//            .filter { it.data != null }
//            .forEach {
//                val key = it.id
//                val value: ObjectNode = platform.object2json(it.data!!)
//                result.set(key, value) as ObjectNode
//            }
//        return result
//    }
//}