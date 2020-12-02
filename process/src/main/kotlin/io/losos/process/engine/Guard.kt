package io.losos.process.engine


import com.fasterxml.jackson.databind.node.ObjectNode
import io.losos.KeyConvention
import io.losos.common.*
import io.losos.platform.Event
import io.losos.process.engine.actions.*
import io.losos.platform.JsonEvent
import org.slf4j.LoggerFactory
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
 *
 *
 *
 */
class Guard(
    val def: GuardDef,
    val type: GuardType,
    val context: ProcessManager.ProcessContext,
    val action: Action<*>?,
    val slots: MutableMap<String, Slot<*>> = mutableMapOf(),
    val timeout: Long = -1,
    val timeoutAction: Action<*>? = null
) {
    companion object {
        val SLOT_DEFAULT = SlotId.invocationId("default")
    }


    private val logger = LoggerFactory.getLogger(Guard::class.java)

    val createdAt = System.currentTimeMillis()
    val deadLine = createdAt + timeout

    var state: GuardState = GuardState.NEW
        internal set

    var cancelledBy: Guard? = null
        internal set

    internal fun accept(e: Event): Boolean = slots.values
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
            .map { !it.isEmpty() }
            .fold(true) { a,b -> a && b }

    fun anySlotFilled(): Boolean = if (slots.isEmpty()) true
    else slots.values
            .map { !it.isEmpty() }
            .fold(false) { a,b -> a || b }


    @Suppress("UNCHECKED_CAST")
    operator fun <T: Slot<*>> get(id: String): T? = slots[id] as? T

    @Suppress("UNCHECKED_CAST")
    operator fun <T: Slot<*>> get(id: SlotId<T>): T? = slots[id.name] as? T

    fun path(): String = KeyConvention.keyGuard(context.nodeManager().name, context.pid, def.id)

    fun eventGuardSlot() = get(SLOT_DEFAULT)

    //--creation--------------------------------------------------------------------------------------------------------

    fun addEventSlots(): Unit = def.slots
                .filterIsInstance<InvocationSlotDef>()
                .forEach { invocationSlot(it.name) }


    @Suppress("UNCHECKED_CAST")
    fun <T: Slot<*>> slot(slotId: SlotId<T>, block: T.() -> Unit = {}): T {
        val slot: Slot<*> = when(slotId.clazz) {
            EventCustomSlot::class -> eventSlot(slotId.name)
            InvocationSlot::class -> invocationSlot(slotId.name)
            else -> throw RuntimeException("Unsupported slot type")
        }
        slots[slot.id] = slot
        return slot as T
    }

    private fun invocationSlot(name: String): InvocationSlot {
        val slotDef: InvocationSlotDef = def.slot(name) as InvocationSlotDef
        return InvocationSlot(slotDef.name, this)
    }

    private fun eventSlot(name: String): EventCustomSlot<JsonEvent> {
        val slotDef: EventCustomSlotDef = def.slot(name) as EventCustomSlotDef
        val selectorDef = slotDef.selector
        val selector = when(selectorDef) {
            is PrefixSelectorDef -> if (selectorDef.absolute) PrefixSelector(selectorDef.prefix)
            else PrefixSelector(this.context.pathState() + "/" + selectorDef.prefix)
            else -> throw RuntimeException("Unknown selector type ${selectorDef.toString()}")
        }
        return EventCustomSlot(slotDef.name, this, selector, JsonEvent::class.java)
    }


    //--presentation----------------------------------------------------------------------------------------------------

    override fun toString() = "Guard[${def.id}, " +
            "state: $state, " +
            "to: $timeout, " +
            "toact: ${timeoutAction?.def?.id}, " +
            "slots: ${slots.values}]"

    override fun hashCode(): Int = def.id.hashCode()

    override fun equals(another: Any?): Boolean {
        if (another == null)
            return false
        return if (another is Guard) {
            another.def.id == def.id
        } else false
    }

    fun stateJson(): ObjectNode = context.nodeManager()
                                        .platform.jsonMapper
                                        .createObjectNode()
                                            .put("state", state.name)

    fun result(): InvocationResult {
        if (!canBeOpened())
            throw RuntimeException("Cannot generate ActionInput: Guard is not opened")

        // It's temporary solution
        // Only one invocation slot is supported
        // Future solution suggests a reducer function which collapses slot events into InvocationResult
        if (slots.size != 1)
            throw IllegalStateException("Should be only 1 slot")

        val event = (slots.values.first() as InvocationSlot).event
        return event.payload
    }
}
