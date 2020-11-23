package io.losos.process.actions

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import io.losos.TestUtils
import io.losos.platform.LososPlatform
import io.losos.process.engine.Slot
import io.losos.process.engine.SlotId
import io.losos.process.engine.SlotWithValue

data class ActionInput(val slots: Map<String, Slot>) {

    @Suppress("UNCHECKED_CAST")
    operator fun <T: Slot> get(slotId: SlotId<T>): T? {
        return slots[slotId.name] as? T
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