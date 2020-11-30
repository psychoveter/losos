package io.losos.process.engine

import com.fasterxml.jackson.annotation.JsonTypeName
import io.losos.common.InvocationResult
import io.losos.platform.Event
import io.losos.common.SlotDef
import java.lang.IllegalStateException
import kotlin.reflect.KClass


data class SlotId<T: Slot<*>>(val name: String, val clazz: KClass<T>) {
    companion object {
        fun varId(name: String) = SlotId(name, VarSlot::class)

        /**
         * Default name of the event on guard is "guard"
         */
        fun eventOnGuardId(name: String = "guard") = SlotId(name, InvocationSlot::class)
        fun eventCustomId(name: String) = SlotId(name, EventCustomSlot::class)
    }
}

/**
 * Stateful class which holds corresponding event
 */
open class Slot<T>(val id: String, val guard: Guard) {
    init {
        guard.slots[id] = this
    }

    var data: T? = null
        protected set

    fun isEmpty() = data == null
}


abstract class EventSlot<T>(id: String, guard: Guard): Slot<T>(id, guard) {

    protected lateinit var event: Event<T>

    fun accept(e: Event<T>): Boolean {
        if( !isEmpty() )
            throw IllegalStateException("Slot already has event, cannot accept more")

        if ( e.fullPath == eventPath() ) {
            data = e.payload
            return true
        }

        return false
    }

    abstract fun match(e: Event<*>): Boolean
    abstract fun eventPath(): String


}

class InvocationSlot(id: String, guard: Guard): EventSlot<InvocationResult>(id, guard) {
    override fun match(e: Event<*>): Boolean = e.fullPath == eventPath()
    override fun eventPath(): String = "${guard.path()}/$id"

    override fun toString() = "EventOnGuard(path=${eventPath()})"
}

class EventCustomSlot<T>(val fullPath: String, guard: Guard, val selector: Selector): EventSlot<T>(fullPath, guard) {
    override fun match(e: Event<*>): Boolean = selector.check(e)
    override fun eventPath(): String = this.id

    override fun toString() = "EventCustom(path=$fullPath)"
}

class VarSlot<T>(id: String, guard: Guard, payload: T? = null): Slot<T>(id, guard) {
    init { this.data = payload }

    fun withPayload(pl: T): VarSlot<T> {
        this.data = pl
        return this
    }

    override fun toString() = "VarSlot(id=$id)"
}

//==defenitions=========================================================================================================




@JsonTypeName("invocation")
data class InvocationSlotDef(override val name: String): SlotDef(name)

@JsonTypeName("event")
data class EventCustomSlotDef(override val name: String, val selector: SelectorDef): SlotDef(name)

@JsonTypeName("var")
data class VarSlotDef(override val name: String): SlotDef(name)