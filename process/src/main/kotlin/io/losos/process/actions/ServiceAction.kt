package io.losos.process.actions

import com.fasterxml.jackson.annotation.JsonTypeName
import io.losos.process.engine.AbstractAction
import io.losos.process.engine.ActionInput
import io.losos.process.engine.GANContext
import io.losos.process.model.ActionDef


/**
 * This action starts invocation of an external service
 */
class ServiceAction(def: ServiceActionDef, ctx: GANContext): AbstractAction<ServiceActionDef>(def, ctx) {

    override suspend fun action(input: ActionInput) {
        TODO("Not yet implemented")
    }

}

@JsonTypeName("service_action")
data class ServiceActionDef (
    override val id: String,
    override val runGuards: List<String>
): ActionDef(id, runGuards)