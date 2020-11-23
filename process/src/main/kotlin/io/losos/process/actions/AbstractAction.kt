package io.losos.process.actions

import io.losos.process.engine.*
import io.losos.process.model.ActionDef
import org.slf4j.LoggerFactory

abstract class AbstractAction<T: ActionDef>(
        override val def: T,
        val ctx: ProcessContext
): Action<T> {

    companion object {
        val logger = LoggerFactory.getLogger(AbstractAction::class.java)
    }

    protected val context = ctx
    private val cmds = ArrayList<CmdGAN>()

    override fun path() = "${context.pathState()}/action/${def.id}"

    override suspend fun execute(input: ActionInput): List<CmdGAN> {
        action(input)
        return cmds
    }

    abstract suspend fun action(input: ActionInput)


    fun guard(guardId: String, block: Guard.() -> Unit = {}): Guard {
        val guard = ctx.guard(guardId, block)
        cmds.add(CmdGuardRegister(guard))
        return guard
    }

    fun scheduleOnProcess(block: Process.() -> Unit) {
        cmds.add(CmdWork(block))
    }

    fun logInfo(msg: String) = logger.info("[action ${def.id}]: $msg")
    fun logDebug(msg: String) = logger.debug("[action ${def.id}]: $msg")

}