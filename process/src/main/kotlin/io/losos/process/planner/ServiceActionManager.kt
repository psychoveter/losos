package io.losos.process.planner

import com.fasterxml.jackson.databind.node.ObjectNode
import io.losos.process.engine.actions.ServiceActionConfig

interface ServiceActionManager {

    fun start()

    fun stop()

    fun invokeService(
        config: ServiceActionConfig,
        args: ObjectNode,
        resultEventPath: String
    )
}
