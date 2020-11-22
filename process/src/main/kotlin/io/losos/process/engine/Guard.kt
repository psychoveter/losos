package io.losos.process.engine


import com.fasterxml.jackson.databind.node.ObjectNode
import io.losos.Framework
import io.losos.platform.Event
import io.losos.process.actions.Action
import io.losos.process.model.GuardDef
import io.losos.process.model.GuardState
import io.losos.process.model.GuardType
import java.lang.IllegalStateException
import java.lang.RuntimeException


/**
 * Guard is a watcher that expects some state to become.
 * State is represented by number of slots and relations between them.
 * Slot is an expectation of some specific event.
 * Essentially guard is a predicate over slot (now either AND or OR).
 *
 * Guards are emitted by actions. By emitting guards actions manage control flow of the process.
 * Actions can emit:
 *  - already opened guards to run next action without expecting other actions
 *     - optionally with already filled slots
 *  - guards expecting some events
 *     - optionally with some already filled slots
 *  Guard may have timeout, when timeout happens, then timeout action is called
 *  There are may be OR and XOR relations between guards:
 *    - if or-related guard is opened, then the guard is opened
 *    - if xor-related guard is opened, then the guard is cancelled
 *
 * Any logical control flow behaviour can be implemented with action-guard semantics.
 */
class Guard(
    val def: GuardDef,
    val type: GuardType,
    val context: ProcessManager.ProcessContext,
    val action: Action<*>?,
    val slots: MutableMap<String, Slot> = mutableMapOf(),
    val timeout: Long = -1,
    val timeoutAction: Action<*>? = null,
    val incarnation: Int = 1
) {
    val log = io.losos.logger("Guard(${def.id})")

    val createdAt = System.currentTimeMillis()
    val deadLine = createdAt + timeout

    var state: GuardState = GuardState.NEW
        internal set

    var cancelledBy: Guard? = null
        internal set

    internal fun accept(e: Event<ObjectNode>): Boolean = slots.values
            .filterIsInstance<EventOnGuardSlot>()
            .filter { it.isEmpty() }
            .map { it.accept(e) }
            .fold(false) { a, b -> a || b }

    //--state-management------------------------------------------------------------------------------------------------

    fun canBeOpened(): Boolean {
        return when(state) {
            GuardState.NEW -> false
            GuardState.WAITING -> when (type) {
                    GuardType.AND -> allSlotsFilled()
                    GuardType.OR -> anySlotFilled()
            }
            GuardState.OPENED -> true
            GuardState.CANCELLED -> throw IllegalStateException("Guard is already dead")
            GuardState.TIMEOUT -> throw IllegalStateException("Guard is already timedout")
        }
    }

    fun isTimeout(): Boolean {
        if(timeout == -1L)
            return false
        return deadLine - System.currentTimeMillis() < 0
    }


    fun allSlotsFilled(): Boolean = slots.values
            .filterIsInstance<EventOnGuardSlot>()
            .map { !it.isEmpty() }
            .fold(true) { a,b -> a && b }

    fun anySlotFilled(): Boolean = if (slots.isEmpty()) true
    else slots.values
            .filterIsInstance<EventOnGuardSlot>()
            .map { !it.isEmpty() }
            .fold(false) { a,b -> a || b }


    fun getEventSlots(): List<EventSlot> = slots.values.filterIsInstance<EventSlot>()

    @Suppress("UNCHECKED_CAST")
    operator fun <T: Slot> get(id: String): T? = slots[id] as? T

    @Suppress("UNCHECKED_CAST")
    operator fun <T: Slot> get(id: SlotId<T>): T = slots[id.name]!! as T

    fun path(): String = "${context.pathState()}/guard/${def.id}/1"

    //--creation--------------------------------------------------------------------------------------------------------

    fun addEventSlots(): Unit = def.slots.values
                .filterIsInstance<EventOnGuardSlotDef>()
                .forEach { eventOnGuardSlot(it.name) }


    @Suppress("UNCHECKED_CAST")
    fun <T: Slot> slot(slotId: SlotId<T>, block: T.() -> Unit = {}): T {
        val slot: Slot = when(slotId.clazz) {
            VarSlot::class -> varSlot(slotId.name, block as VarSlot.() -> Unit)
            EventCustomSlot::class -> eventCustomSlot(slotId.name)
            EventOnGuardSlot::class -> eventOnGuardSlot(slotId.name)
            else -> throw RuntimeException("Unsupported slot type")
        }
        slots[slot.id] = slot
        return slot as T
    }

    private fun eventOnGuardSlot(name: String): EventOnGuardSlot {
        val slotDef: EventOnGuardSlotDef = def.slots[name] as EventOnGuardSlotDef
        return EventOnGuardSlot(slotDef.name, this)
    }

    private fun eventCustomSlot(name: String): EventCustomSlot {
        val slotDef: EventCustomSlotDef = def.slots[name] as EventCustomSlotDef
        val selectorDef = slotDef.selector
        val selector = when(selectorDef) {
            is PrefixSelectorDef -> if (selectorDef.absolute) PrefixSelector(selectorDef.prefix)
            else PrefixSelector(this.context.pathState() + "/" + selectorDef.prefix)
            else -> throw RuntimeException("Unknown selector type ${selectorDef.toString()}")
        }
        return EventCustomSlot(slotDef.name, this, selector)
    }

    private fun varSlot(name: String, block: VarSlot.() -> Unit): VarSlot {
        val slot = VarSlot(name, this)
        slot.block()
        return slot
    }

    //--presentation----------------------------------------------------------------------------------------------------

    override fun toString() = "Guard[${def.id}, state: $state, to: $timeout, toact: ${timeoutAction?.def?.id}]"

    fun stateJson(): ObjectNode = Framework.jsonMapper.createObjectNode()
                                                    .put("state", state.name)

}
