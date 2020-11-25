package io.losos.process

import io.losos.TestUtils
import io.losos.process.engine.actions.TestActionDef
import io.losos.process.engine.EventOnGuardSlotDef
import io.losos.process.model.*
import org.junit.Assert
import org.junit.Test

class SerializationTest {

    @Test fun mapperTest() {

        val slot1 = EventOnGuardSlotDef("s1")
        val slot2 = EventOnGuardSlotDef("s2")
        val slot3 = EventOnGuardSlotDef("s3")
        val slot4 = EventOnGuardSlotDef("s4")

        val g = GuardDef("g1", mapOf(slot1.name to slot1, slot2.name to slot2), "action1", GuardType.AND)
        val action1 = TestActionDef("action1", listOf())

        val gan = ProcessDef(
                name =  "p1",
                description = "sample process",
                startGuard = "g1",
                finishGuard = "g2",
                guards = listOf(g),
                guardRelations = listOf(),
                actions = listOf(action1)
        )

        val string = TestUtils.jsonMapper.writeValueAsString(gan)
        println(string)

        val gg = TestUtils.jsonMapper.readValue(string, ProcessDef::class.java)

        val string2 = TestUtils.jsonMapper.writeValueAsString(gg)

        Assert.assertEquals(string, string2)
    }
}