package io.losos.process.engine

import io.losos.KeyConvention
import io.losos.platform.LososPlatform
import io.losos.process.engine.action.AsyncActionManager
import io.losos.process.engine.action.ServiceActionManager
import io.losos.process.library.ProcessLibrary
import io.losos.process.model.ProcessDef
import io.losos.process.planner.SubprocessPlanner
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import java.util.*


interface IDGenerator {
    fun newUniquePID(): String
}

object IDGenUUID: IDGenerator {
    override fun newUniquePID(): String = UUID.randomUUID().toString()
}

data class NodeInfo (
    val name: String,
    val host: String,
    val processDefNames: Collection<String> = listOf()
)

class NodeManager(
    val platform: LososPlatform,
    val processLibrary: ProcessLibrary,
    val name: String = UUID.randomUUID().toString(),
    val host: String = "localhost",
    val idGen: IDGenerator = IDGenUUID
) {

    private val logger = LoggerFactory.getLogger(NodeManager::class.java)

    val processManager = ProcessManager(this)
    val asyncActionManager = AsyncActionManager(this)
    val serviceActionManager = ServiceActionManager(this)
    val subprocessPlanner = SubprocessPlanner(platform)
    val processDefCache: Map<String, ProcessDef> = processLibrary.getAvailableProcesses()


    private var isRunning = true
    private val leaseThread = Thread {
        platform.runInKeepAlive(KeyConvention.keyNodeLease(name), info()) {
            while(isRunning) {
                try { Thread.sleep(1000) } catch (e: InterruptedException) {}
            }
        }
    }

    fun start() {
        for(proc in processDefCache.values) {
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

    fun info(): NodeInfo = NodeInfo(name, host, processDefCache.values.map { it.name })
}