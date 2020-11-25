package io.losos.process.planner

import com.fasterxml.jackson.databind.node.ObjectNode
import io.losos.process.engine.actions.ServiceActionConfig
import org.slf4j.LoggerFactory
import org.springframework.http.HttpEntity
import org.springframework.web.client.RestTemplate


interface ServiceActionManager {

    fun start()

    fun stop()

    fun invokeService(
        config: ServiceActionConfig,
        args: ObjectNode,
        resultEventPath: String
    )
}
