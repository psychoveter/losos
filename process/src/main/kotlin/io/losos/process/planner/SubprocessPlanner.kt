package io.losos.process.planner

import io.losos.KeyConvention
import io.losos.platform.LososPlatform
import io.losos.process.engine.NodeInfo
import org.slf4j.LoggerFactory

class SubprocessPlanner(
    val platform: LososPlatform
) {

    private val logger = LoggerFactory.getLogger(SubprocessPlanner::class.java)

    /**
     * process name -> set of node names exposing the process
     */
    private val defTable = mutableMapOf<String, MutableSet<String>>()

    private fun addNodeToCache(info: NodeInfo) {
        info.processDefNames.forEach {
            if( !defTable.containsKey(it) )
                defTable[it] = mutableSetOf()
            defTable[it]?.add(info.name)
        }
    }

    private fun removeNodeFromCache(nodeName: String) {
        defTable.forEach {
            it.value.remove(nodeName)
        }
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

}