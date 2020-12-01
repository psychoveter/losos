package io.losos.process.engine

import com.fasterxml.jackson.annotation.JsonTypeName
import io.losos.KeyConvention
import io.losos.common.InvocationResult
import io.losos.platform.Event
import io.losos.common.SlotDef
import io.losos.platform.InvocationEvent
import java.lang.IllegalStateException
import kotlin.reflect.KClass


data class SlotId<T: Slot<*>>(val name: String, val clazz: KClass<T>) {
    companion object {

        /**
         * Default name of the event on guard is "guard"
         */
        fun invocationId(name: String = "guard") = SlotId(name, InvocationSlot::class)
        fun eventCustomId(name: String) = SlotId(name, EventCustomSlot::class)
    }
}

/**
 * Stateful class which holds corresponding event
 */
open class Slot<T: Event>(
    val id: String, val guard: Guard, val eventType: Class<T>
) {

    init {
        guard.slots[id] = this
    }

    lateinit var event: T
        private set

    fun accept(e: Event): Boolean {
        if( !isEmpty() )
            throw IllegalStateException("Slot already has event, cannot accept more")

        if (!eventType.isInstance(e))
            return false

        if (match(e)) {
            this.event = e as T
            return true
        }

        return false
    }

    fun isEmpty() = !this::event.isInitialized

    open fun match(e: Event): Boolean = e.fullPath == eventPath()

    open fun eventPath(): String = KeyConvention.keyInvocationEvent (
        guard.context.nodeManager().name,
        guard.context.pid,
        guard.def.id,
        id
    )

}

class InvocationSlot(id: String, guard: Guard): Slot<InvocationEvent>(id, guard, InvocationEvent::class.java) {

    override fun toString() = "InvocationSlot(path=${eventPath()})"

    override fun eventPath(): String = KeyConvention.keyInvocationEvent (
        guard.context.nodeManager().name,
        guard.context.pid,
        guard.def.id,
        id
    )
}

class EventCustomSlot<T: Event>(
    fullPath: String,
    guard: Guard,
    val selector: Selector,
    eventType: Class<T>
): Slot<T>(fullPath, guard, eventType) {

    override fun match(e: Event): Boolean = selector.check(e)
    override fun eventPath(): String = this.id
    override fun toString() = "EventCustom(path=${eventPath()})"
}


//==defenitions=========================================================================================================

@JsonTypeName("invocation")
data class InvocationSlotDef(override val name: String = "default"): SlotDef(name)

@JsonTypeName("event")
data class EventCustomSlotDef(override val name: String, val selector: SelectorDef): SlotDef(name)
