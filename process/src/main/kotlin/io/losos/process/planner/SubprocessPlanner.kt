package io.losos.process.planner

import com.fasterxml.jackson.databind.node.ObjectNode
import io.losos.KeyConvention
import io.losos.platform.LososPlatform
import io.losos.process.engine.NodeInfo
import io.losos.process.engine.ProcessStartCall
import org.slf4j.LoggerFactory
import java.util.*

class SubprocessPlanner(
    val platform: LososPlatform
) {

    private val logger = LoggerFactory.getLogger(SubprocessPlanner::class.java)

    /**
     * process name -> set of node names exposing the process
     */
    private val defTable = mutableMapOf<String, MutableSet<String>>()

    private val assignmentsCount = mutableMapOf<String, Int>()

    private fun addNodeToCache(info: NodeInfo) {
        info.processDefNames.forEach {
            if( !defTable.containsKey(it) )
                defTable[it] = mutableSetOf()
            defTable[it]?.add(info.name)
        }
        assignmentsCount[info.name] = 0
    }

    private fun removeNodeFromCache(nodeName: String) {
        defTable.forEach {
            it.value.remove(nodeName)
        }
        assignmentsCount.remove(nodeName)
    }

    init {
        //read active nodes
        logger.info("Init subprocess planner. Loading active nodes...")
        val nodes = platform.getPrefix(KeyConvention.NODE_LEASE_ROOT, NodeInfo::class.java)

        logger.info("Loaded ${nodes}")
        nodes.values.forEach { addNodeToCache(it) }

        logger.info("Subscribe for registry changes...")

        platform.subscribeDelete(KeyConvention.NODE_LEASE_ROOT, NodeInfo::class.java) {
            logger.info("Node ${it.payload?.name} missed, remove it")
            removeNodeFromCache(it.payload!!.name)
        }

        platform.subscribe(KeyConvention.NODE_LEASE_ROOT, NodeInfo::class.java) {
            logger.info("Node ${it.payload} appears: add it")
            removeNodeFromCache(it.payload!!.name)
            addNodeToCache(it.payload!!)
        }
    }

    /**
     * Assigns process to a node, generating PID.
     * @param procName - process name to start
     * @param resultEventPath - event that should be emitted at the subprocess end, @see ProcessStartCall
     * @param args - arguments for the process to run with
     * @return PID or null if no appropriate node to run process
     */
    fun assignSubprocess(
        procName: String,
        resultEventPath: String,
        args: ObjectNode?
    ): SubprocessAssignmentDto? {
        val availableNodes = defTable[procName]
        if (availableNodes != null && availableNodes.size > 0) {
            val node = availableNodes
                .map { it to assignmentsCount.get(it) }
                .minBy { it.second!! }!!.first

            val pid = UUID.randomUUID().toString()
            platform.put(
                KeyConvention.keyProcessEntry(node, pid),
                ProcessStartCall(
                    pid = pid,
                    procName = procName,
                    resultEventPath = resultEventPath,
                    args = args ?: platform.emptyObject()
                )
            )
            return SubprocessAssignmentDto(pid, node)
        } else {
            logger.info("No nodes found for process ${procName}")
            return null
        }
    }

}

data class SubprocessAssignmentDto(
    val pid: String,
    val node: String
)