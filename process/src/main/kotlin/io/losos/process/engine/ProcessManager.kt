package io.losos.process.engine

import com.fasterxml.jackson.databind.node.ObjectNode
import io.losos.common.FlowStatus
import io.losos.common.InvocationResult
import io.losos.KeyConvention
import io.losos.common.ActionDef
import io.losos.common.GuardDef
import io.losos.common.GuardRelationType
import io.losos.common.ProcessDef
import io.losos.platform.Event
import io.losos.platform.ProcessEvent
import io.losos.platform.Subscription
import io.losos.process.engine.actions.*
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
    private val subscriptions = ConcurrentHashMap<String, MutableList<Subscription<*>>>()
    private val slots         = ConcurrentHashMap<String, MutableList<Slot<*>>>()

    private val busChannel = Channel<Event>()

    @Volatile private var isStarted = false

    /**
     * Create new process and subscribe it for it's related events
     */
    fun createProcess(
        def: ProcessDef,
        initialPID: String? = null,
        resultEventPath: String? = null,
        args: ObjectNode? = null
    ): Process {
        val pid = initialPID ?: nodeManager.idGen.newUniquePID()
        slots[pid] = CopyOnWriteArrayList()
        subscriptions[pid] = CopyOnWriteArrayList()
        val context = ProcessContext(pid, def)
        val process = Process(pid, def, context, resultEventPath)
        processes[pid] = process

        // Subscribe for all process events
        subscriptions[pid]!!.add(nodeManager.platform.subscribe(context.pathState()) {
            busChannel.send(it)
        })

        nodeManager.platform.put(
            process.context.pathRegistry(),
            ProcessStartCall(pid, def.name, resultEventPath, args)
        )
        logger.info("Created process $pid")

        process.run()

        //if args are provided and process is guarded by solo event guard, then kick off it
        if (args != null && process.hasStartingEventGuard())
            nodeManager.platform.put(
                process.guardStart.eventGuardSlot()!!.eventPath(),
                InvocationResult(args)
            )

        return process
    }

    /**
     * Restores process from history events at the point it was terminated
     */
    fun restoreProcess(pid: String, def: ProcessDef, resultEventPath: String? = null, args: ObjectNode? = null): Process {
        //TODO("Restore should take into account finished actions and start from them if any")
        logger.info("Restore process ${pid}...")
        return if (!processes.containsKey(pid)) {
            logger.info("Found process ${pid} which is not created, creating...")
            createProcess(
                def = def,
                initialPID = pid,
                resultEventPath = resultEventPath,
                args = args)
        } else {
            logger.info("Process $pid found as active, do nothing")
            processes[pid]!!
        }
    }

    /**
     * Deletes process from registry and cleans up all it's state
     */
    fun deleteProcess(process: Process) {
        processes.remove(process.pid)
        nodeManager.platform.delete(process.context.pathRegistry())
    }

    fun startBrokering() {
        nodeManager.platform.subscribe(KeyConvention.keyProcessRegistry(nodeManager.name), ProcessEvent::class.java) {
            logger.info("Got ProcessStartCall notification: ${it}")
            val call = it.info
            val def = nodeManager.processLibrary.getAvailableProcesses()[call.procName]
            if (def == null) {
                logger.error("No process def for name ${call.procName}, " +
                             "available: ${nodeManager.processLibrary.getAvailableProcesses().keys}")

                if (call.resultEventPath != null) {
                    nodeManager.platform.put(
                        call.resultEventPath,
                        InvocationResult.fail(nodeManager.platform
                            .emptyObject()
                                .put("reason", "Process def ${call.procName} not found"))
                    )
                }
            } else {
                restoreProcess(
                    pid = call.pid,
                    def = def,
                    resultEventPath = call.resultEventPath,
                    args = call.args)
            }
        }

        job = GlobalScope.launch {
            try {
                isStarted = true
                while (isStarted) {
                    logger.info("started brokering loop")
                    while (!busChannel.isClosedForReceive) {
                        logger.debug("await for event...")
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


    inner class ProcessContext(
        override val pid: String,
        override val def: ProcessDef
    ):
        io.losos.process.engine.ProcessContext {
        //TODO: Def validation
        //TODO: Process creation failure processing

        override fun pathState(): String = KeyConvention.keyProcessState(nodeManager.name, pid)
        override fun pathRegistry(): String = KeyConvention.keyProcessEntry(nodeManager.name, pid)
//        override fun pathLease(): String = "/proc/$host/lease/$pid"

        override fun nodeManager(): NodeManager = nodeManager


        override fun action(id: String): Action<*> = action(def.getActionDef(id)!!)

        override fun action(def: ActionDef): Action<*> = when(def) {
            is TestActionDef -> TestAction(def, this)
            is AgentTaskActionDef -> AgentTaskAction(def, this)
            is InvocationActionDef -> InvocationAction(def, this)
            else -> throw RuntimeException("Unkown action type")
        }

        override fun guard(id: String, block: Guard.() -> Unit): Guard
                = guard(def.getGuardDef(id)!!, block)


        override fun guard(gdef: GuardDef, block: Guard.() -> Unit): Guard {
            val action = if(gdef.action == null) null
                         else action(def.getActionDef(gdef.action)!!)
            val timeoutAction = if(gdef.timeoutAction == null) null
                                else action(def.getActionDef(gdef.timeoutAction)!!)

            val g = Guard(
                def = gdef,
                type = gdef.type,
                context = this,
                action = action,
                timeout = gdef.timeout,
                timeoutAction = timeoutAction
            )

            g.block()

            return g
        }

        override fun filterXorGuards(guardId: String, guards: Set<Guard>): List<Guard> {
            //TODO: optimize
            val relatedGuardIds: List<String> = def.guardRelations
                    .filter { it.type == GuardRelationType.XOR }
                    .filter { it.guards.contains(guardId) }
                    .map { it.guards }
                    .flatten()
                    .filter { !it.equals(guardId) }
            return guards.filter { relatedGuardIds.contains(it.def.id) }
        }

        override fun registerSlot(s: Slot<*>) = slots[pid]!!.add(s)

        override fun deregisterSlot(s: Slot<*>) = slots[pid]!!.remove(s)

    }
}
