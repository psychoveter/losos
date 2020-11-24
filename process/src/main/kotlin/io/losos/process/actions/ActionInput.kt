package io.losos.process.actions

import com.fasterxml.jackson.databind.node.ObjectNode
import io.losos.platform.LososPlatform
import io.losos.process.engine.Slot
import io.losos.process.engine.SlotId
import io.losos.process.engine.SlotWithValue


/**
 * Action input accepts slots of activated guard
 */
data class ActionInput(
    val slots: Map<String, Slot>,
    private val platform: LososPlatform
) {

    @Suppress("UNCHECKED_CAST")
    operator fun <T: Slot> get(slotId: SlotId<T>): T? {
        return slots[slotId.name] as? T
    }

    fun <T: Slot> data(slotId: SlotId<T>): ObjectNode? = data(slotId, ObjectNode::class.java)

    fun <T: Slot, P> data(slotId: SlotId<T>, clazz: Class<P>): P? {
        val slot = get(slotId) ?: return null
        if (slot is SlotWithValue<*>) {
            val payload = slot.data ?: return null

            return if (clazz == ObjectNode::class.java) {
                if (payload is ObjectNode)
                    payload as P
                else
                    platform.object2json(payload) as P
            } else {
                if (payload is ObjectNode)
                    platform.json2object(payload, clazz)
                else
                    payload as P
            }
        }
        return null
    }

    fun jsonData(platform: LososPlatform): ObjectNode {
        val result = platform.jsonMapper.createObjectNode()
        slots.values
            .filterIsInstance<SlotWithValue<*>>()
            .filter { it.data != null }
            .forEach {
                val key = it.id
                val value: ObjectNode = platform.object2json(it.data!!)
                result.set(key, value) as ObjectNode
            }
        return result
    }

}