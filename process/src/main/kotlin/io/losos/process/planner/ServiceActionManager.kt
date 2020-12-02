package io.losos.process.planner

import com.fasterxml.jackson.databind.node.ObjectNode

interface ServiceActionManager {

    fun start()

    fun stop()

    fun invokeService(
        serviceTask: ServiceTask,
        resultEventPath: String
    )
}

data class ServiceTask(
    val workerType: String,
    val taskType: String,
    val args: ObjectNode?
)
