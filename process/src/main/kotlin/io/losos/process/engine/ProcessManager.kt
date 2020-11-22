package io.losos.process.engine

import com.fasterxml.jackson.databind.node.ObjectNode
import io.losos.platform.Event
import io.losos.platform.Subscription
import io.losos.process.actions.*
import io.losos.process.model.*
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import java.lang.RuntimeException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList


class ProcessManager (
    val nodeManager: NodeManager
) {

    private val logger = LoggerFactory.getLogger(ProcessManager::class.java)

    private var job: Job? = null

    private val processes     = ConcurrentHashMap<String, Process>()
    private val subscriptions = ConcurrentHashMap<String, MutableList<Subscription<ObjectNode>>>()
    private val slots         = ConcurrentHashMap<String, MutableList<EventSlot>>()

    private val busChannel = Channel<Event<ObjectNode>>()

    @Volatile private var isStarted = false


    /**
     * Create new process and subscribe it for it's related events
     */
    fun createProcess(def: ProcessDef): Process {
        val pid = nodeManager.idGen.newUniquePID()
        slots[pid] = CopyOnWriteArrayList()
        subscriptions[pid] = CopyOnWriteArrayList()
        val context = ProcessContext(pid, def)
        val gan = Process(pid, def, context)
        processes[pid] = gan
        subscriptions[pid]!!.add(nodeManager.platform.subscribe(context.pathState()) {
            busChannel.send(it)
        })

        nodeManager.platform.put(gan.context.pathRegistry(), Event.emptyPayload())

        return gan
    }

    /**
     * Restores process from history events at the point it was terminated
     */
    fun restoreProcess(): Process {
        TODO("Not implemented")
    }

    /**
     * Deletes process from registry and cleans up all it's state
     */
    fun deleteProcess(process: Process) {
        nodeManager.platform.delete(process.context.pathRegistry())
    }

    fun startBrokering() {
        nodeManager.platform.subscribe("/proc/${nodeManager.host}/register", ProcessInfo::class.java) {
            
        }

        job = GlobalScope.launch {
            try {
                isStarted = true
                while (isStarted) {
                    logger.info("started brokering loop")
                    while (!busChannel.isClosedForReceive) {
                        //log("brokering inner loop iteration")
                        val event = busChannel.receive()
                        logger.debug("received event: ${event}")

                        slots.forEach {
                            it.value.forEach { slot ->
                                if ( slot.match(event) ) {
                                    val cmd = CmdEvent(event)
                                    val gan = processes[it.key]!!
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


    inner class ProcessContext(val pid: String, val processDef: ProcessDef):
        io.losos.process.engine.ProcessContext {
        //TODO: Def validation
        //TODO: Process creation failure processing

        override fun definition(): ProcessDef = processDef

        override fun pathState(): String = "/proc/${nodeManager.host}/state/$pid"
        override fun pathRegistry(): String = "/proc/${nodeManager.host}/registry/$pid"
//        override fun pathLease(): String = "/proc/$host/lease/$pid"

        override fun nodeManager(): NodeManager = nodeManager


        override fun action(id: String): Action<*> = action(processDef.getActionDef(id)!!)

        override fun action(def: ActionDef): Action<*> = when(def) {
            is TestActionDef -> TestAction(def, this)
            is AgentTaskActionDef -> AgentTaskAction(def, this)
            else -> throw RuntimeException("Unkown action type")
        }

        override fun guard(id: String, block: Guard.() -> Unit): Guard
                = guard(processDef.getGuardDef(id)!!, block)


        override fun guard(def: GuardDef, block: Guard.() -> Unit): Guard {
            val action = if(def.action == null) null
                         else action(processDef.getActionDef(def.action)!!)
            val timeoutAction = if(def.timeoutAction == null) null
                                else action(processDef.getActionDef(def.timeoutAction)!!)

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
            val relatedGuardIds: List<String> = processDef.guardRelations
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
