package io.losos.process.engine.actions

import com.fasterxml.jackson.databind.node.ObjectNode
import io.losos.KeyConvention
import io.losos.process.engine.*
import io.losos.common.ActionDef
import io.losos.common.InvocationResult
import org.slf4j.LoggerFactory

abstract class AbstractAction<T: ActionDef>(
        override val def: T,
        override val ctx: ProcessContext
): Action<T> {

    private val logger = LoggerFactory.getLogger(AbstractAction::class.java)


    private val cmds = ArrayList<CmdGAN>()

    override suspend fun execute(input: ObjectNode?): List<CmdGAN> {
        action(input)
        return cmds
    }

    abstract suspend fun action(input: ObjectNode?)

    fun guard(guardId: String, block: Guard.() -> Unit = {}): Guard {
        val guard = ctx.guard(guardId, block)
        logger.info("Created guard: $guard")
        cmds.add(CmdGuardRegister(guard))

        for(invoke in def.invokes)
            scheduleOnProcess {
                val path = KeyConvention.keyInvocationEvent(
                    context.nodeManager().name,
                    context.pid,
                    invoke.guard,
                    invoke.slot
                )
                this.context.platform().put(
                    path,
                    InvocationResult(invoke.data, invoke.status)
                )
            }
        return guard
    }

    fun scheduleOnProcess (block: Process.() -> Unit) {
        cmds.add(CmdWork(block))
    }

    fun logInfo(msg: String) = logger.info("[action ${def.id}]: $msg")
    fun logDebug(msg: String) = logger.debug("[action ${def.id}]: $msg")

}