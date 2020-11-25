package io.losos.process.engine.actions

import com.fasterxml.jackson.annotation.JsonTypeName
import io.losos.process.engine.*
import io.losos.process.model.ActionDef
import kotlinx.coroutines.delay


//==entities============================================================================================================

class TestAction(def: TestActionDef, ctx: ProcessContext): AbstractAction<TestActionDef>(def, ctx) {
    override suspend fun action(input: ActionInput) {
        logInfo("Action ${def.id} is executing. Input: ${input.jsonData(context.nodeManager().platform)}")
        if(def.message != null)
            logInfo(def.message)

        input.slots.forEach{ logInfo("Slot received: ${it.toString()}" ) }
        delay(def.delay)
        def.runGuards.forEach {
            guard(it) {addEventSlots()}
        }
    }
}


//==definitions=========================================================================================================

@JsonTypeName("test")
data class TestActionDef(
        override val id: String,
        override val runGuards: List<String>,
        val message: String? = null,
        val delay: Long = 0L
): ActionDef(id, runGuards)