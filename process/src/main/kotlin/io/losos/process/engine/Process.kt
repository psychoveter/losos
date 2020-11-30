package io.losos.process.engine

import com.fasterxml.jackson.databind.node.ObjectNode
import io.losos.common.InvocationExitCode
import io.losos.common.InvocationResult
import io.losos.platform.Event
import io.losos.actor.Actor
import io.losos.common.*
import io.losos.process.engine.actions.Action
import io.losos.process.engine.exc.GANException
import io.losos.process.engine.exc.WorkException
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import java.lang.Exception


interface ProcessContext {

    //--general-methods-------------------------------------------------------------------------------------------------

    val pid: String
    val def: ProcessDef

    fun nodeManager(): NodeManager
    fun platform() = nodeManager().platform
    fun pathState(): String
    fun pathRegistry(): String

    //--factory-methods-------------------------------------------------------------------------------------------------

    fun action(id: String): Action<*>
    fun action(def: ActionDef): Action<*>
    fun guard(id: String): Guard = guard(def.getGuardDef(id)!!)
    fun guard(def: GuardDef): Guard = guard(def) {
        def.slots.values
                .filterIsInstance<InvocationSlotDef>()
                .forEach { slot(SlotId.eventOnGuardId(it.name)) }
    }
    fun guard(id: String, block: Guard.() -> Unit): Guard
    fun guard(def: GuardDef, block: Guard.() -> Unit): Guard

    //--methods-for-GAN-------------------------------------------------------------------------------------------------
    fun registerSlot(s: EventSlot<*>): Boolean
    fun deregisterSlot(s: EventSlot<*>): Boolean
    fun filterXorGuards(guardId: String, guards: List<Guard>): List<Guard>
}


data class ProcessStartCall (
    val pid: String,
    val procName: String,
    val resultEventPath: String?,
    val args: ObjectNode?
)


interface CmdGAN
data class CmdGuardRegister(val guard: Guard): CmdGAN
data class CmdGuardOpen(val guard: Guard): CmdGAN
data class CmdGuardTimeout(val guard: Guard): CmdGAN
data class CmdGuardCancel(val guard: Guard, val by: Guard): CmdGAN
data class CmdEvent(val event: Event): CmdGAN
data class CmdHalt(val reason: InvocationResult): CmdGAN
data class CmdWork(val block: Process.() -> Unit): CmdGAN
data class CmdAction(val action: Action<*>, val firedGuard: Guard): CmdGAN



/**
 * Guard-action network
 */
class Process(
    val pid: String,
    val def: ProcessDef,
    val context: ProcessContext,
    val resultEventPath: String? = null
): Actor<CmdGAN>() {

    private val logger = LoggerFactory.getLogger(Process::class.java)
    private var activeGuards = ArrayList<Guard>()
    lateinit var guardStart: Guard
        private set
    lateinit var guardEnd: Guard
        private set

    private fun removeGuard(g: Guard) {
        logger.info("remove existing guard: $g")
        g.getEventSlots().forEach { context.deregisterSlot(it) }
        activeGuards.remove(g)
    }

    override suspend fun beforeStart() {
        logger.info("Before start")
        //create timeout graph checker

        GlobalScope.launch {
            while(isRunning()) {
                activeGuards
                        .filter { it.isTimeout() }
                        .forEach{ timeoutGuard(it) }

                delay(100)
            }
        }

        guardStart = context.guard(def.getGuardDef(def.startGuard)!!)
        registerGuard(guardStart)
        guardEnd = context.guard(def.getGuardDef(def.startGuard)!!)
        registerGuard(guardEnd)

        //TODO: publish process state
    }

    override suspend fun afterStop() {
        logger.info("After stop")
        //TODO: clean context, resources, subscriptions, etc
    }

    //--Interface-------------------------------------------------------------------------------------------------------

    fun info(): ProcessInfo {
        return ProcessInfo(pid, def)
    }

    fun hasStartingEventGuard(): Boolean = guardStart[Guard.SLOT_EVENT_GUARD] != null

    //--Command-factory-------------------------------------------------------------------------------------------------

    private suspend fun cmdAction(guard: Guard, action: Action<*>) {
        logger.info("Fire action: $action")
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
        logger.info("Received event ${message}")
        try {
            when (message) {
                is CmdGuardRegister -> {
                    if (this::guardEnd.isInitialized && message.guard.def.id == guardEnd.def.id) {
                        logger.debug("Skip emitting end guard")
                    }

                    message.guard.state = GuardState.WAITING

                    logger.info("add new guard: ${message.guard}")
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
                        logger.info("Finish guard ${message.guard.def.id} opened: exit process")

                        if (resultEventPath != null) {
                            context.platform().put(
                                resultEventPath,
                                InvocationResult(
                                    InvocationExitCode.OK,
                                    message.guard.slotJson()
                                )
                            )
                        }

                        close()
                    }
                    publishGuard(message.guard)
                }
                is CmdGuardTimeout -> {
                    logger.info("Guard timeout happens: ${message.guard.def.id}")
                    if(message.guard.timeoutAction == null)
                        throw RuntimeException("Timeout guard has no timeout action")
                    message.guard.state = GuardState.TIMEOUT
                    handleRelatedGuards(message.guard)
                    handleGuardOpen(message.guard, message.guard.timeoutAction)
                    publishGuard(message.guard)
                }
                is CmdGuardCancel -> {
                    logger.info("Remove cancelled guard: ${message.guard.def.id}")
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
                        val input = message.firedGuard.toActionInput()
                        message.action.execute(input)
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
                is CmdHalt -> {
                    halt(message.reason)
                }

                else -> { throw RuntimeException("Unknown command type") }
            }
        } catch (e: Exception) {
            logger.error("Failed process execution", e)
            if (resultEventPath != null)
                context.platform().put(resultEventPath,
                    InvocationResult(
                        InvocationExitCode.FAILED,
                        context.platform().emptyObject().put("reason", e.message)
                    )
                )
            this.close()
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

        //TODO open related OR guards
    }

    private suspend fun handleGuardOpen(guard: Guard, action: Action<*>?) {
        if(action != null)
            cmdAction(guard, action)
        removeGuard(guard)
    }


    private fun publishGuard(guard: Guard) {
        if (def.publishGuardEvents) {
            val path = guard.path()
            val payload = guard.stateJson()
            context.platform().put(path, payload)
        }
    }

    private fun publishAction(action: Action<*>, firedGuard: Guard) {
        if(def.publishGuardEvents) {
            val path = action.path()
            val payload = context.platform().emptyObject()
            context.platform().put(path, payload)
        }
    }

    private fun halt(reason: InvocationResult) {
        logger.info("End guard ${guardEnd} opened: exit process")

        if (resultEventPath != null) {
            context.platform().put(resultEventPath, reason)
        }

        close()
    }

}
