package io.losos.process.engine.actions

import io.losos.KeyConvention
import io.losos.process.engine.*
import io.losos.common.ActionDef


/**
 * Actions are main logic blocks of the GAN.
 * Actions produces commands which being put into GAN execution queue.
 * There several types of commands:
 * - Rise guard. Guards expect for some conditions to initiate consequent action execution.
 * - Do some work in context of the GAN
 * Actions executed on GAN coroutine and should not be blocking. For external calls there are specific subtypes
 * of actions which rises ActionControllers to track async action processing.
 */
interface Action<T: ActionDef> {
    val def: T
    val ctx: ProcessContext
    fun path() = KeyConvention.keyAction(ctx.nodeManager().name, ctx.pid, def.id)

    suspend fun execute(input: ActionInput): List<CmdGAN>
}


