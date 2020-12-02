package io.losos.process.engine

import io.losos.KeyConvention
import io.losos.common.NodeInfo
import io.losos.platform.LososPlatform
import io.losos.process.planner.AsyncActionManager
import io.losos.process.planner.ServiceActionManager
import io.losos.process.library.ProcessLibrary
import io.losos.process.planner.SubprocessPlanner
import org.slf4j.LoggerFactory
import java.util.*


interface IDGenerator {
    fun newUniquePID(): String
}

object IDGenUUID: IDGenerator {
    override fun newUniquePID(): String = UUID.randomUUID().toString()
}

class NodeManager (
    val platform: LososPlatform,
    val processLibrary: ProcessLibrary,
    val serviceActionManager: ServiceActionManager? = null,
    val asyncActionManager: AsyncActionManager? = null,
    val name: String = UUID.randomUUID().toString(),
    val host: String = "localhost",
    val idGen: IDGenerator = IDGenUUID
) {

    private val logger = LoggerFactory.getLogger(NodeManager::class.java)

    val processManager = ProcessManager(this)
    val subprocessPlanner = SubprocessPlanner(platform)

    private var isRunning = true
    private val leaseThread = Thread {
        platform.runInKeepAlive(KeyConvention.keyNodeLease(name), info()) {
            while(isRunning) {
                try { Thread.sleep(1000) } catch (e: InterruptedException) {}
            }
        }
    }

    fun start() {
        for(proc in processLibrary.getAvailableProcesses().values) {
            logger.info("Found ${proc.name} in the library")
        }

        platform.put(KeyConvention.keyNodeRegistry(name), info())
        leaseThread.start()

        processManager.startBrokering()
    }

    fun stop() {
        processManager.stopBrokering()
        isRunning = false
        leaseThread.interrupt()
    }

    fun info(): NodeInfo = NodeInfo(
        name,
        host,
        processLibrary.getAvailableProcesses().values.map { it.name })
}