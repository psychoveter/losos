package io.losos.process.engine

import io.losos.etcd.EtcdEventBus
import io.losos.eventbus.Event
import io.losos.process.model.GANDef
import io.etcd.recipes.common.connectToEtcd
import io.losos.Framework
import io.losos.process.model.GuardState
import org.junit.Test
import java.io.File
import java.lang.RuntimeException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit


class GANTest {

    //Open questions:
    // Waiting room for events: if guard was not raised before event has been received

    @Test fun simpleLineTest() {
        Framework.init(mapOf(
                "process" to true,
                "etcdbus" to false,
                "pm" to true,
                "simpleLineTest" to false
        ))

        val log = io.losos.logger("simpleLineTest")
        val mapper = io.losos.Framework.jsonMapper
        val ganDef = mapper.readValue(File("src/test/resources/cases/graphLine.json"), GANDef::class.java)

        log("Read process definition: ${mapper.writeValueAsString(ganDef)}")

        val latch = CountDownLatch(1)
        val guardSet = mutableSetOf(
                "/guard/guard_one/1",
                "/guard/guard_two/1",
                "/guard/guard_three/1",
                "/guard/guard_four/1",
                "/guard/guard_exit/1",
                "/go3",
                "/start"
        )

        connectToEtcd(Framework.Test.ETCD_URLS) { client ->
            val eBus = EtcdEventBus(client)
            val pm = ProcessManager(eBus, IDGenUUID)
            pm.startBrokering()

            val gan = pm.createProcess(ganDef)

            eBus.subscribe(gan.context.contextPath()) {
                log("47 TEST LISTEN: $it")
                val relativePath = it.fullPath.removePrefix(gan.context.contextPath())
                log("49 relativePath: $relativePath, fullPath: ${it.fullPath}")
                if(relativePath.startsWith("/guard")) {
                    val state = GuardState.valueOf(it.payload["state"]!!.textValue())
                    if(state == GuardState.OPENED)
                        guardSet.remove(relativePath)
                }
                if(relativePath.startsWith("/go3") || relativePath.startsWith("/start"))
                    guardSet.remove(relativePath)

                if(guardSet.isEmpty())
                    latch.countDown()
            }

            gan.run()

            eBus.emit("${gan.context.contextPath()}/start", Event.emptyPayload())
            Thread.sleep(200)
            eBus.emit("${gan.context.contextPath()}/go3", Event.emptyPayload())

            latch.await(10, TimeUnit.SECONDS)

            if(guardSet.isNotEmpty())
                throw RuntimeException("Some guards weren't opened: ${guardSet}")
        }

    }

    @Test fun xorGuardsTest() {
        //TODO
    }

}


