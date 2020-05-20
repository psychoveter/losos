package io.losos.process.model


import io.losos.process.engine.ActionDef
import io.losos.process.engine.SlotDef

enum class GuardType { AND, OR }
enum class GuardState { NEW, WAITING, OPENED, CANCELLED, TIMEOUT }
enum class GuardRelationType { XOR, OR }


data class GuardRelation (
        val type: GuardRelationType,
        val guards: List<String>
)

data class GANDef(
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
    fun getActionDef(id: String): ActionDef? = actions.findLast { it.id == id }

}

//==defenitions=========================================================================================================


data class GuardDef(
        val id: String,
        val slots: Map<String, SlotDef>,
        val type: GuardType,
        val action: String?,
        val timeout: Long = -1,
        val timeoutAction: String? = null
)
