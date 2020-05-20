package io.losos.process.engine

import io.losos.eventbus.Event
import io.losos.eventbus.EventBus
import io.losos.eventbus.Subscription
import io.losos.process.actions.AgentTaskAction
import io.losos.process.actions.AgentTaskActionDef
import io.losos.process.actions.TestAction
import io.losos.process.actions.TestActionDef
import io.losos.process.model.*
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import java.lang.RuntimeException
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList


interface IDGenerator {
    fun newUniquePID(): String
}

object IDGenUUID: IDGenerator {
    override fun newUniquePID(): String = UUID.randomUUID().toString()
}

class ProcessManager (
        private val eventBus: EventBus,
        private val idGenerator: IDGenerator
) {

    val log = io.losos.logger("pm")

    companion object {
        val PATH_PROC_STATE     = "/proc/state"
        val PATH_PROC_REGISTRY_ = "/proc/registry"
        val PATH_PROC_LEASE     = "/proc/lease"
    }

    private var job: Job? = null

    private val subscriptions = ConcurrentHashMap<String, MutableList<Subscription>>()
    private val slots     = ConcurrentHashMap<String, MutableList<EventSlot>>()
    private val gans          = ConcurrentHashMap<String, GAN>()

    private val busChannel = Channel<Event>()

    @Volatile private var isStarted = false

    /**
     * Restores process from history events at the point it was terminated
     */
    fun restoreProcess(): GAN {
        TODO("Not implemented")
    }

    fun createProcess(def: GANDef): GAN {
        val pid = idGenerator.newUniquePID()
        slots[pid] = CopyOnWriteArrayList()
        subscriptions[pid] = CopyOnWriteArrayList()
        val context = ProcessContext(pid, def)
        val gan = GAN(def, context)
        gans[pid] = gan
        subscriptions[pid]!!.add(eventBus.subscribe(context.contextPath()) {
            busChannel.send(it)
        })
        return gan
    }

    fun startBrokering() {
        job = GlobalScope.launch {
            try {
                isStarted = true
                while (isStarted) {
                    log("started brokering loop")
                    while (!busChannel.isClosedForReceive) {
                        //log("brokering inner loop iteration")
                        val event = busChannel.receive()
                        log("received event: ${event}")

                        slots.forEach {
                            it.value.forEach { slot ->
                                if ( slot.match(event) ) {
                                    val cmd = CmdEvent(event)
                                    val gan = gans[it.key]!!
                                    if(gan.isRunning())
                                        gan.send(cmd)
                                }
                            }
                        }
                    }
                }
            } finally {
                isStarted = false
            }
        }
    }

    fun stopBrokering() {
        busChannel.close()
    }


    inner class ProcessContext(val pid: String, val ganDef: GANDef): GANContext {
        //TODO: Def validation
        //TODO: Process creation failure processing

        override fun definition(): GANDef = ganDef

        override fun contextPath(): String = "$PATH_PROC_STATE/${ganDef.name}/$pid"

        override fun eventBus(): EventBus = eventBus

        override fun action(id: String): Action<*> = action(ganDef.getActionDef(id)!!)

        override fun action(def: ActionDef): Action<*> = when(def) {
            is TestActionDef -> TestAction(def, this)
            is AgentTaskActionDef -> AgentTaskAction(def, this)
            else -> throw RuntimeException("Unkown action type")
        }

        override fun guard(id: String, block: Guard.() -> Unit): Guard
                = guard(ganDef.getGuardDef(id)!!, block)


        override fun guard(def: GuardDef, block: Guard.() -> Unit): Guard {
            val action = if(def.action == null) null
                         else action(ganDef.getActionDef(def.action)!!)
            val timeoutAction = if(def.timeoutAction == null) null
                                else action(ganDef.getActionDef(def.timeoutAction)!!)

            val g = Guard(
                def = def,
                type = def.type,
                context = this,
                action = action,
                timeout = def.timeout,
                timeoutAction = timeoutAction
            )

            g.block()

            return g
        }

        override fun filterXorGuards(guardId: String, guards: List<Guard>): List<Guard> {
            //TODO: optimize
            val relatedGuardIds: List<String> = ganDef.guardRelations
                    .filter { it.type == GuardRelationType.XOR }
                    .filter { it.guards.contains(guardId) }
                    .map { it.guards }
                    .flatten()
                    .filter { !it.equals(guardId) }
            return guards.filter { relatedGuardIds.contains(it.def.id) }
        }

        override fun registerSlot(s: EventSlot) = slots[pid]!!.add(s)

        override fun deregisterSlot(s: EventSlot) = slots[pid]!!.remove(s)

    }
}
