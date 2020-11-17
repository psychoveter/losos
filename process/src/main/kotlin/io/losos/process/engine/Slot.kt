package io.losos.process.engine

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.annotation.JsonTypeName
import io.losos.eventbus.Event
import io.losos.process.model.SlotDef
import java.lang.IllegalStateException
import kotlin.reflect.KClass


data class SlotId<T: Slot>(val name: String, val clazz: KClass<T>) {
    companion object {
        fun varId(name: String) = SlotId(name, VarSlot::class)
        fun eventOnGuardId(name: String) = SlotId(name, EventOnGuardSlot::class)
        fun eventCustomId(name: String) = SlotId(name, EventCustomSlot::class)
    }
}

/**
 * Stateful class which holds corresponding event
 */
open class Slot(val id: String, val guard: Guard)

abstract class EventSlot(id: String, guard: Guard): Slot(id, guard) {
    var event: Event? = null
        private set

    fun isEmpty() = event == null

    fun accept(e: Event): Boolean {
        if( !isEmpty() )
            throw IllegalStateException("Slot already has event, cannot accept more")

        if ( e.fullPath == eventPath() ) {
            event = e
            return true
        }

        return false
    }

    abstract fun match(e: Event): Boolean
    abstract fun eventPath(): String
}

class EventOnGuardSlot(id: String, guard: Guard): EventSlot(id, guard) {
    override fun match(e: Event): Boolean = e.fullPath == eventPath()
    override fun eventPath(): String = "${guard.path()}/$id"
}

class EventCustomSlot(fullPath: String, guard: Guard, val selector: Selector): EventSlot(fullPath, guard) {
    override fun match(e: Event): Boolean = selector.check(e)
    override fun eventPath(): String = this.id
}

class VarSlot(
    id: String,
    guard: Guard,
    var payload: Any? = null
): Slot(id, guard) {
    fun withPayload(pl: Any): VarSlot {
        this.payload = pl
        return this
    }
}

//==defenitions=========================================================================================================




@JsonTypeName("event_on_guard")
data class EventOnGuardSlotDef(override val name: String): SlotDef(name)

@JsonTypeName("event_custom")
data class EventCustomSlotDef(override val name: String, val selector: SelectorDef): SlotDef(name)

@JsonTypeName("var")
data class VarSlotDef(override val name: String): SlotDef(name)