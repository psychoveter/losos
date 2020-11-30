package io.losos.process.engine.actions

import io.losos.process.engine.*
import io.losos.common.ActionDef
import org.slf4j.LoggerFactory

abstract class AbstractAction<T: ActionDef>(
        override val def: T,
        override val ctx: ProcessContext
): Action<T> {

    private val logger = LoggerFactory.getLogger(AbstractAction::class.java)


    private val cmds = ArrayList<CmdGAN>()

    override suspend fun execute(input: ActionInput): List<CmdGAN> {
        action(input)
        return cmds
    }

    abstract suspend fun action(input: ActionInput)

    fun guard(guardId: String, block: Guard.() -> Unit = {}): Guard {
        val guard = ctx.guard(guardId, block)
        logger.info("Created guard: $guard")
        cmds.add(CmdGuardRegister(guard))
        return guard
    }

    fun scheduleOnProcess(block: Process.() -> Unit) {
        cmds.add(CmdWork(block))
    }

    fun logInfo(msg: String) = logger.info("[action ${def.id}]: $msg")
    fun logDebug(msg: String) = logger.debug("[action ${def.id}]: $msg")

}