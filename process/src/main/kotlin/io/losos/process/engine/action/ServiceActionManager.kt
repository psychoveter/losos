package io.losos.process.engine.action

import com.fasterxml.jackson.databind.node.ObjectNode
import io.losos.process.actions.ServiceActionConfig
import io.losos.process.engine.NodeManager
import java.util.concurrent.ConcurrentLinkedQueue


/**
 * Service manager implements Task Execution Protocol
 */
class ServiceActionManager (
    val nodeManager: NodeManager
) {

    companion object {
        const val SERVICE_ML = "ml"
        const val SERVICE_REPORTER = "reporter"
        const val SERVICE_GATEWAY = "gateway"
    }

    private val serviceRegistry: Map<String, TEPServiceProvider> = mapOf(
        SERVICE_ML to MLServiceProvider(),
        SERVICE_REPORTER to ReporterServiceProvider(),
        SERVICE_GATEWAY to GatewayServiceProvider()
    )

    private val taskQueue: Map<String, ConcurrentLinkedQueue<ObjectNode>> = mapOf (
        SERVICE_ML to ConcurrentLinkedQueue(),
        SERVICE_REPORTER to ConcurrentLinkedQueue(),
        SERVICE_GATEWAY to ConcurrentLinkedQueue()
    )

    /**
     * This method is called by service to request task for execution
     */
    fun requestTask(workerType: String) {

    }


    /**
     * @see io.losos.process.actions.InvocationAction
     */
    fun invokeService(
        config: ServiceActionConfig,
        args: ObjectNode?,
        resultEventPath: String
    ) {

    }

}

interface TEPServiceProvider {
    fun postTask(workerType: String)
}

class MLServiceProvider: TEPServiceProvider {
    override fun postTask(workerType: String) {
        TODO("Not yet implemented")
    }
}

class ReporterServiceProvider: TEPServiceProvider {
    override fun postTask(workerType: String) {
        TODO("Not yet implemented")
    }
}

//may be gateway task is better implement as async task inside satellite itself?..
class GatewayServiceProvider: TEPServiceProvider {
    override fun postTask(workerType: String) {
        TODO("Not yet implemented")
    }
}