package io.losos.process.model


import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import io.losos.process.actions.AgentTaskActionDef
import io.losos.process.actions.TestActionDef
import io.losos.process.engine.EventCustomSlotDef
import io.losos.process.engine.EventOnGuardSlotDef
import io.losos.process.engine.VarSlotDef

enum class GuardType { AND, OR }
enum class GuardState { NEW, WAITING, OPENED, CANCELLED, TIMEOUT }
enum class GuardRelationType { XOR, OR }


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
        JsonSubTypes.Type(value = AgentTaskActionDef::class, name = "agent_task")
)
open class ActionDef (
        open val id: String,
        open val runGuards: List<String>
)


@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.PROPERTY,
        property = "type"
)
@JsonSubTypes(
        JsonSubTypes.Type(value = EventOnGuardSlotDef::class, name = "event_on_guard"),
        JsonSubTypes.Type(value = EventCustomSlotDef::class, name = "event_custom"),
        JsonSubTypes.Type(value = VarSlotDef::class, name = "var")
)
open class SlotDef(
        open val name: String
)


data class GuardDef(
        val id: String,
        val slots: Map<String, SlotDef>,
        val type: GuardType,
        val action: String?,
        val timeout: Long = -1,
        val timeoutAction: String? = null
)
