package io.losos.process.actions

import com.fasterxml.jackson.annotation.JsonTypeName
import io.losos.process.engine.*
import kotlinx.coroutines.delay


//==entities============================================================================================================

class TestAction(def: TestActionDef, ctx: GANContext): AbstractAction<TestActionDef>(def, ctx) {
    override suspend fun action(input: ActionInput) {
        log("Action ${def.id} is executing")
        if(def.message != null)
            log(def.message)

        input.slots.forEach{ log("Slot received: ${it.toString()}" ) }
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