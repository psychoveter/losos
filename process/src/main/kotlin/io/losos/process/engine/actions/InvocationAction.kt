package io.losos.process.engine.actions

import com.fasterxml.jackson.annotation.JsonTypeName
import com.fasterxml.jackson.databind.node.ObjectNode
import io.losos.process.engine.ProcessContext
import io.losos.process.engine.SlotId
import io.losos.common.ActionDef
import io.losos.common.InvocationExitCode
import io.losos.common.InvocationResult
import io.losos.process.planner.ServiceTask
import org.slf4j.LoggerFactory
import java.lang.RuntimeException

/**
 * Invocation action is intended to run some process of execution.
 * Invocation actions always rise two xor-related guards:
 * <ul>
 *     <li>Success guard - call if invocation is successful</li>
 *     <li>Failure guard - call if invocation failed</li>
 * </ul>
 *
 * Invocation action created action-config object which is managed by corresponding ActionController.
 * ActionController is responsible for actual action execution or communication with external services.
 *
 * There are several types of actions and related controllers:
 * <b>Async action </b> - async action is any long-running operation that should not block processing of the parent GAN.
 * Async action is executed at the same virtual machine as GAN.
 * @see io.losos.process.actions.AsyncAction
 * <b>Service action</b> - call some external service. The service should implement TEP-T protocol
 * @see io.losos.process.actions.ServiceAction
 * <b>SubProcess action</b> - create child sub-process using provided GANDef
 * @see io.losos.process.actions.SubProcessAction
 */
class InvocationAction<T: InvocationActionDef>(def: T, ctx: ProcessContext):
    AbstractAction<T>(def, ctx)
{

    private val logger = LoggerFactory.getLogger(InvocationAction::class.java)

    companion object {
        const val GUARD_RESULT = "guard_result"
        val SLOT_INPUT = SlotId.eventOnGuardId("guard")
    }

    override suspend fun action(input: ActionInput) {
        //1. Rise guards
        val resultGuard = guard(def.guardResult) { addEventSlots() }
        val resultEventPath = resultGuard.eventGuardSlot()!!.eventPath()
        when (input) {
            is ActionInputSingle<*> -> actionForSingleInput(input as ActionInputSingle<ObjectNode>, resultEventPath)
            is ActionInputList<*> -> throw NotImplementedError()
            is ActionInputMap -> throw NotImplementedError()
        }

    }

    private fun actionForSingleInput(input: ActionInputSingle<ObjectNode>, resultEventPath: String) {
        val payload = input.data!!

        when (def.invoke_type) {
            InvocationType.ASYNC -> {
                throw NotImplementedError()
            }

            InvocationType.SERVICE -> {
                if (ctx.nodeManager().serviceActionManager != null) {
                    val config = ctx.platform().json2object(def.config, ServiceActionConfig::class.java)
                    ctx.nodeManager().serviceActionManager!!.invokeService(
                        ServiceTask(config.workerType, config.taskType, payload),
                        resultEventPath
                    )
                } else {
                    throw RuntimeException("ServiceActionManager is not configured")
                }
            }

            InvocationType.SERVICE_STUB -> {
                val config = ctx.platform().json2object(def.config, ServiceActionStubConfig::class.java)

                Thread {
                    Thread.sleep(config.delay)
                    logger.info("[STUB_SERVICE]: args = $payload")
                    ctx.platform().put(resultEventPath, InvocationResult(
                        if (config.fail) InvocationExitCode.FAILED else InvocationExitCode.OK,
                        ctx.platform().emptyObject()
                            .put("key", "value")
                    )
                    )
                }.start()
            }

            InvocationType.SUBPROCESS -> {
                val config = ctx.nodeManager().platform.json2object(def.config, SubprocessActionConfig::class.java)
                val result = ctx.nodeManager().subprocessPlanner.assignSubprocess(
                    config.processName,
                    resultEventPath,
                    payload
                )
                if (result != null) {
                    logger.info("Scheduled ${config.processName} at node ${result.node} with pid ${result.pid}")
                }
            }
        }
    }
}

enum class InvocationType {
    ASYNC, SUBPROCESS, SERVICE,
    SERVICE_STUB
}

@JsonTypeName("invoke")
open class InvocationActionDef (
    override val id: String,
    val invoke_type: InvocationType,
    val config: ObjectNode
): ActionDef(id, listOf(
    "$id/${InvocationAction.GUARD_RESULT}"
)) {
    val guardResult = "$id/${InvocationAction.GUARD_RESULT}"
}

data class SubprocessActionConfig(
    val processName: String,
    val args: ObjectNode?
)

data class ServiceActionConfig(
    val workerType: String,
    val taskType: String
)

data class ServiceActionStubConfig(
    val workerType: String,
    val taskType: String,
    val delay: Long = 1000,
    val fail: Boolean = false
)