package io.losos.common


import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.databind.node.ObjectNode
import io.losos.process.engine.actions.AgentTaskActionDef
import io.losos.process.engine.actions.InvocationActionDef
import io.losos.process.engine.actions.TestActionDef
import io.losos.process.engine.EventCustomSlotDef
import io.losos.process.engine.InvocationSlotDef

enum class GuardType { AND, OR }
enum class GuardState { NEW, WAITING, OPENED, CANCELLED, TIMEOUT }
enum class GuardRelationType { XOR, OR }
enum class GuardSignature { SINGLE, ARRAY, MAP }

data class GuardRelation (
        val type: GuardRelationType,
        val guards: List<String>
)

data class ProcessDef(
        val name: String,
        val description: String,
        val startGuard: String,
        val finishGuard: String,
        val guards: List<GuardDef>,
        val guardRelations: List<GuardRelation>,
        val actions: List<ActionDef>,
        val publishGuardEvents: Boolean = false
) {
    fun getGuardDef(id: String): GuardDef? = guards.find { it.id == id }
    fun getActionDef(id: String): ActionDef? = actions.find { it.id == id }

}

@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.PROPERTY,
        property = "type"
)
@JsonSubTypes(
        JsonSubTypes.Type(value = TestActionDef::class, name = "test"),
        JsonSubTypes.Type(value = AgentTaskActionDef::class, name = "agent_task"),
        JsonSubTypes.Type(value = InvocationActionDef::class, name = "invoke")
)
open class ActionDef (
        open val id: String,
        open val runGuards: List<String>,
        open val invokes: List<InvokeDef>
)

data class InvokeDef (
        val guard: String,
        val slot: String,
        val data: ObjectNode? = null,
        val status: FlowStatus = FlowStatus.OK
)


@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.PROPERTY,
        property = "type"
)
@JsonSubTypes(
        JsonSubTypes.Type(value = InvocationSlotDef::class, name = "invocation"),
        JsonSubTypes.Type(value = EventCustomSlotDef::class, name = "event")
)
open class SlotDef(
        open val name: String
)


data class GuardDef(
        val id: String,
        val slots: List<SlotDef>,
        val action: String?,
        val type: GuardType = GuardType.AND,
        val signature: GuardSignature = GuardSignature.SINGLE,
        val timeout: Long = -1,
        val timeoutAction: String? = null
) {
        fun slot(name: String) = slots.first { it.name == name }
}
