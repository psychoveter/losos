package io.losos.process.engine


import com.fasterxml.jackson.databind.node.ObjectNode
import io.losos.KeyConvention
import io.losos.platform.Event
import io.losos.process.engine.actions.*
import io.losos.common.GuardDef
import io.losos.common.GuardSignature
import io.losos.common.GuardState
import io.losos.common.GuardType
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
    val timeoutAction: Action<*>? = null,
    val incarnation: Int = 1
) {
    companion object {
        val SLOT_EVENT_GUARD = SlotId.eventOnGuardId("guard")
    }


    private val logger = LoggerFactory.getLogger(Guard::class.java)

    val createdAt = System.currentTimeMillis()
    val deadLine = createdAt + timeout

    var state: GuardState = GuardState.NEW
        internal set

    var cancelledBy: Guard? = null
        internal set

    internal fun accept(e: Event<ObjectNode>): Boolean = slots.values
            .filterIsInstance<EventSlot<ObjectNode>>()
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
            .filterIsInstance<EventSlot<ObjectNode>>()
            .map { !it.isEmpty() }
            .fold(true) { a,b -> a && b }

    fun anySlotFilled(): Boolean = if (slots.isEmpty()) true
    else slots.values
            .filterIsInstance<EventSlot<ObjectNode>>()
            .map { !it.isEmpty() }
            .fold(false) { a,b -> a || b }


    fun getEventSlots(): List<EventSlot<ObjectNode>> = slots.values.filterIsInstance<EventSlot<ObjectNode>>()

    @Suppress("UNCHECKED_CAST")
    operator fun <T: Slot<*>> get(id: String): T? = slots[id] as? T

    @Suppress("UNCHECKED_CAST")
    operator fun <T: Slot<*>> get(id: SlotId<T>): T? = slots[id.name] as? T

    fun path(): String = KeyConvention.keyGuard(context.nodeManager().name, context.pid, def.id)

    fun eventGuardSlot() = get(SLOT_EVENT_GUARD)

    //--creation--------------------------------------------------------------------------------------------------------

    fun addEventSlots(): Unit = def.slots.values
                .filterIsInstance<InvocationSlotDef>()
                .forEach { invocationSlot(it.name) }


    @Suppress("UNCHECKED_CAST")
    fun <T: Slot<*>> slot(slotId: SlotId<T>, block: T.() -> Unit = {}): T {
        val slot: Slot<*> = when(slotId.clazz) {
            VarSlot::class -> varSlot(slotId.name, block as VarSlot<Any?>.() -> Unit)
            EventCustomSlot::class -> eventSlot(slotId.name)
            InvocationSlot::class -> invocationSlot(slotId.name)
            else -> throw RuntimeException("Unsupported slot type")
        }
        slots[slot.id] = slot
        return slot as T
    }

    private fun invocationSlot(name: String): InvocationSlot {
        val slotDef: InvocationSlotDef = def.slots[name] as InvocationSlotDef
        return InvocationSlot(slotDef.name, this)
    }

    private fun eventSlot(name: String): EventCustomSlot<ObjectNode> {
        val slotDef: EventCustomSlotDef = def.slots[name] as EventCustomSlotDef
        val selectorDef = slotDef.selector
        val selector = when(selectorDef) {
            is PrefixSelectorDef -> if (selectorDef.absolute) PrefixSelector(selectorDef.prefix)
            else PrefixSelector(this.context.pathState() + "/" + selectorDef.prefix)
            else -> throw RuntimeException("Unknown selector type ${selectorDef.toString()}")
        }
        return EventCustomSlot(slotDef.name, this, selector)
    }

    private fun <T> varSlot(name: String, block: VarSlot<T>.() -> Unit): VarSlot<T> {
        val slot = VarSlot<T>(name, this)
        slot.block()
        return slot
    }

    //--presentation----------------------------------------------------------------------------------------------------

    override fun toString() = "Guard[${def.id}, " +
            "state: $state, " +
            "to: $timeout, " +
            "toact: ${timeoutAction?.def?.id}, " +
            "slots: ${slots.values}]"

    fun stateJson(): ObjectNode = context.nodeManager()
                                        .platform.jsonMapper
                                        .createObjectNode()
                                            .put("state", state.name)
    fun slotJson(): ObjectNode = context.nodeManager().platform.jsonMapper.createObjectNode().apply {
        slots.values
            .forEach {
                val name = it.id
                val value = context.nodeManager().platform.object2json(it.data)
                this.put(name, value)
            }
    }

    fun toActionInput(): ActionInput {
        if (!canBeOpened())
            throw RuntimeException("Cannot generate ActionInput: Guard is not opened")

        return when (def.signature) {
            GuardSignature.SINGLE -> {
                val data = slots.values.first().data
                ActionInputSingle(data)
            }
            GuardSignature.ARRAY -> {
                val data = slots.values.map { it.data }.toList()
                ActionInputList(data)
            }
            GuardSignature.MAP -> {
                val data = slots.entries.map { it.key to it.value.data }.toMap()
                ActionInputMap(data)
            }
        }
    }
}
