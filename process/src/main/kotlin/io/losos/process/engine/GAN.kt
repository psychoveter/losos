package io.losos.process.engine

import io.losos.JsonObj
import io.losos.eventbus.Event
import io.losos.eventbus.EventBus
import io.losos.actor.Actor
import io.losos.process.engine.exc.GANException
import io.losos.process.engine.exc.WorkException
import io.losos.process.model.*
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.lang.Exception


interface GANContext {

    //--general-methods-------------------------------------------------------------------------------------------------

    fun definition(): GANDef
    fun contextPath(): String
    fun eventBus(): EventBus

    //--factory-methods-------------------------------------------------------------------------------------------------

    fun action(id: String): Action<*>
    fun action(def: ActionDef): Action<*>

    fun guard(id: String): Guard = guard(definition().getGuardDef(id)!!)

    fun guard(def: GuardDef): Guard = guard(def) {
        def.slots.values
                .filterIsInstance<EventOnGuardSlotDef>()
                .forEach { slot(SlotId.eventOnGuardId(it.name)) }
    }

    fun guard(id: String, block: Guard.() -> Unit): Guard
    fun guard(def: GuardDef, block: Guard.() -> Unit): Guard

    //--methods-for-GAN-------------------------------------------------------------------------------------------------
    fun registerSlot(s: EventSlot): Boolean
    fun deregisterSlot(s: EventSlot): Boolean
    fun filterXorGuards(guardId: String, guards: List<Guard>): List<Guard>
}


data class GuardEvent(
        override val fullPath: String,
        override val payload: JsonObj,
        val newState: GuardState
): Event

data class ActionEvent(
        override val fullPath: String,
        override val payload: JsonObj,
        val action: Action<*>,
        val firedGuard: Guard
): Event



interface CmdGAN

data class CmdGuardRegister(val guard: Guard): CmdGAN
data class CmdGuardOpen(val guard: Guard): CmdGAN
data class CmdGuardTimeout(val guard: Guard): CmdGAN
data class CmdGuardCancel(val guard: Guard, val by: Guard): CmdGAN
data class CmdEvent(val event: Event): CmdGAN
data class CmdWork(val block: GAN.() -> Unit): CmdGAN
data class CmdAction(val action: Action<*>, val firedGuard: Guard): CmdGAN



/**
 * Guard-action network
 */
class GAN(
    val def: GANDef,
    val context: GANContext
): Actor<CmdGAN>() {

    val log = io.losos.logger("process")

    private var activeGuards = ArrayList<Guard>()

    private fun removeGuard(g: Guard) {
        log("remove existing guard: $g")
        g.getEventSlots().forEach { context.deregisterSlot(it) }
        activeGuards.remove(g)
    }

    override suspend fun beforeStart() {
        log("Before start")
        //create timeout graph checker
        GlobalScope.launch {
            while(isRunning()) {
                activeGuards
                        .filter { it.isTimeout() }
                        .forEach{ timeoutGuard(it) }

                delay(100)
            }
        }

        val startGuard = context.guard(def.getGuardDef(def.startGuard)!!)
        registerGuard(startGuard)
    }

    override suspend fun afterStop() {
        log("After stop")
        //TODO: clean context, resources, subscriptions, etc
    }

    //--Command-factory-------------------------------------------------------------------------------------------------

    private suspend fun cmdAction(guard: Guard, action: Action<*>) {
        log("Fire action: $action")
        val cmd = CmdAction(action, guard)
        send(cmd)
    }

    private suspend fun registerGuard(guard: Guard) {
        val cmd = CmdGuardRegister(guard)
        send(cmd)
    }

    private suspend fun openGuard(guard: Guard) {
        val cmd = CmdGuardOpen(guard)
        send(cmd)
    }

    private suspend fun timeoutGuard(guard: Guard) {
        val cmd = CmdGuardTimeout(guard)
        send(cmd)
    }

    private suspend fun cancelGuard(guard: Guard, by: Guard) {
        val cmd = CmdGuardCancel(guard, by)
        send(cmd)
    }

    //------------------------------------------------------------------------------------------------------------------

    @Throws(GANException::class)
    /**
     * There should be some action chunk emitted by business logic actions.
     * These action chunks are ordered actions leading to publishing of events.
     * This mechanism is needed for precedence guarantees. Of course to provide this,
     * event bus implementation also should guarantee ordering of published events.
     */
    override suspend fun process(message: CmdGAN) {
        log("Received event ${message}")
        when (message) {
            is CmdGuardRegister -> {
                message.guard.state = GuardState.WAITING

                log("add new guard: ${message.guard}")
                activeGuards.add(message.guard)

                if(message.guard.canBeOpened())
                    openGuard(message.guard)
                else
                    message.guard.getEventSlots()
                            .forEach { context.registerSlot(it) }

                publishGuard(message.guard)
            }
            is CmdGuardOpen -> {
                message.guard.state = GuardState.OPENED
                handleRelatedGuards(message.guard)
                handleGuardOpen(message.guard, message.guard.action)
                if(message.guard.def.id == def.finishGuard) {
                    log("Finish guard ${message.guard.def.id} opened: exit process")
                    close()
                }
                publishGuard(message.guard)
            }
            is CmdGuardTimeout -> {
                log("Guard timeout happens: ${message.guard.def.id}")
                if(message.guard.timeoutAction == null)
                    throw RuntimeException("Timeout guard has no timeout action")
                message.guard.state = GuardState.TIMEOUT
                handleRelatedGuards(message.guard)
                handleGuardOpen(message.guard, message.guard.timeoutAction)
                publishGuard(message.guard)
            }
            is CmdGuardCancel -> {
                log("Remove cancelled guard: ${message.guard.def.id}")
                message.guard.state = GuardState.CANCELLED
                message.guard.cancelledBy = message.by
                removeGuard(message.guard)
            }
            is CmdWork -> {
                try {
                    message.block(this)
                } catch (e: Exception) {
                    throw WorkException(e)
                }
            }
            is CmdAction -> {
                publishAction(message.action, message.firedGuard)
                val cmds = try {
                    message.action.execute(ActionInput(message.firedGuard.slots))
                } catch (e: Exception) {
                    throw GANException(e)
                }
                cmds.forEach{send(it)}
            }
            is CmdEvent -> {
                activeGuards
                        .filter { it.accept(message.event) }
                        .filter { it.canBeOpened() }
                        .forEach{ openGuard(it) }
            }

            else -> { throw RuntimeException("Unknown command type") }
        }
    }

    private suspend fun handleRelatedGuards(guard: Guard) {
        //cancel and remove related XOR guards
        val xorGuards: List<Guard> = context.filterXorGuards(guard.def.id, activeGuards)
        xorGuards.forEach {
            if(it.state == GuardState.WAITING) {
                cancelGuard(it, guard)
            }
        }

        //open related OR guards
        //TODO
    }

    private suspend fun handleGuardOpen(guard: Guard, action: Action<*>?) {
        if(action != null)
            cmdAction(guard, action)
        removeGuard(guard)
    }


    private fun publishGuard(guard: Guard) {
        if (def.publishGuardEvents) {
            val guardEvent = GuardEvent(
                    fullPath = "${context.contextPath()}/guard/${guard.def.id}/${guard.incarnation}",
                    payload = guard.stateJson(),
                    newState = guard.state
            )
            context.eventBus().emit(guardEvent)
        }
    }

    private fun publishAction(action: Action<*>, firedGuard: Guard) {
        if(def.publishGuardEvents) {
            val evt = ActionEvent(
                    "${context.contextPath()}/action/${action.def.id}",
                    Event.emptyPayload(),
                    action,
                    firedGuard
            )
            context.eventBus().emit(evt)
        }
    }
}
