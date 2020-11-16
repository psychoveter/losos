package io.losos.process.engine

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import io.losos.process.actions.AgentTaskActionDef
import io.losos.process.actions.TestActionDef


data class ActionInput(val slots: Map<String, Slot>) {

    @Suppress("UNCHECKED_CAST")
    operator fun <T: Slot> get(slotId: SlotId<T>): T? {
        return slots[slotId.name] as? T
    }
}

interface Action<T: ActionDef> {
    val def: T
    suspend fun execute(input: ActionInput): List<CmdGAN>
}


abstract class AbstractAction<T: ActionDef>(
        override val def: T,
        private val ctx: GANContext
): Action<T> {

    protected val context = ctx
    private val cmds = ArrayList<CmdGAN>()

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

    fun work(block: GAN.() -> Unit) {
        cmds.add(CmdWork(block))
    }

    fun log(msg: String) = io.losos.log("[action ${def.id}]: $msg")

}


//==definitions=========================================================================================================

@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.PROPERTY,
    property = "type"
)
@JsonSubTypes(
        JsonSubTypes.Type(value = TestActionDef::class, name = "test"),
        JsonSubTypes.Type(value = AgentTaskActionDef::class, name = "agent_task")
)
open class ActionDef (
    open val id: String,
    open val runGuards: List<String>
)
