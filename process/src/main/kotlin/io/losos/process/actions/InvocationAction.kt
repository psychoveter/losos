package io.losos.process.actions

import com.fasterxml.jackson.databind.node.ObjectNode
import io.losos.process.engine.ProcessContext
import io.losos.process.model.ActionDef
import java.util.*

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
class InvocationAction<T: InvocationActionDef>(def: T, ctx: ProcessContext): AbstractAction<T>(def, ctx) {
    companion object {
        const val GUARD_SUCCESS = "guard_success"
        const val GUARD_FAILURE = "guard_failure"
    }

    override suspend fun action(input: ActionInput) {
        //1. Rise guards
        guard(def.guardSuccessName) { addEventSlots() }
        guard(def.guardFailureName) { addEventSlots() }

        //2. place action object:
        val uid = UUID.randomUUID().toString()
        val type = def.type
        val params = def.config
        params.set<ObjectNode>("data", input.jsonData(ctx.nodeManager().platform))


        when (type) {
            InvocationType.ASYNC -> {

            }

            InvocationType.SERVICE -> {

            }

            InvocationType.SUBPROCESS -> {

            }
        }

    }
}

enum class InvocationType {
    ASYNC, SUBPROCESS, SERVICE
}

open class InvocationActionDef (
    override val id: String,
    val type: InvocationType,
    val config: ObjectNode
): ActionDef(id, listOf(
    "$id/${InvocationAction.GUARD_SUCCESS}",
    "$id/${InvocationAction.GUARD_FAILURE}"
)) {
    val guardSuccessName = "$id/${InvocationAction.GUARD_SUCCESS}"
    val guardFailureName = "$id/${InvocationAction.GUARD_FAILURE}"
}