package io.losos.process.actions

import com.fasterxml.jackson.annotation.JsonTypeName
import io.losos.process.engine.AbstractAction
import io.losos.process.engine.ActionInput
import io.losos.process.engine.GANContext
import io.losos.process.model.ActionDef


class SubProcessAction(def: SubProcessActionDef, ctx: GANContext): AbstractAction<SubProcessActionDef>(def, ctx) {

    override suspend fun action(input: ActionInput) {
        TODO("Not yet implemented")
    }

}

@JsonTypeName("subprocess_action")
data class SubProcessActionDef(
    override val id: String,
    override val runGuards: List<String>
): ActionDef(id, runGuards)