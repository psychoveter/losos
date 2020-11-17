package io.losos.process.actions

import io.losos.process.engine.AbstractAction
import io.losos.process.engine.ActionInput
import io.losos.process.engine.GANContext
import io.losos.process.model.ActionDef

/**
 * Invocation action is intended to run some process of execution.
 * There are several types of processes:
 * <b>Service action</b> - call some external service
 * <b>SubProcess action</b> - create child sub-process
 *
 * Invocation actions always rise three xor-related guards:
 * <ul>
 *     <li>Success guard - call if invocation is successful</li>
 *     <li>Failure guard - call if invocation failed</li>
 *     <li>Retry guard - call if invocation failed but it make sense to retry it</li>
 * </ul>
 */
class InvocationAction<T: InvocationActionDef>(def: T, ctx: GANContext): AbstractAction<T>(def, ctx) {
    companion object {
        const val GUARD_SUCCESS = "guard_success"
        const val GUARD_FAILURE = "guard_failure"
        const val GUARD_RETRY = "guard_retry"
    }

    override suspend fun action(input: ActionInput) {
        TODO("Not yet implemented")
    }
}

open class InvocationActionDef (
    override val id: String
): ActionDef(id, listOf(
    "$id/${InvocationAction.GUARD_SUCCESS}",
    "$id/${InvocationAction.GUARD_FAILURE}",
    "$id/${InvocationAction.GUARD_RETRY}"
))