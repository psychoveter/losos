package io.losos.process.agent

import io.etcd.recipes.common.connectToEtcd
import io.losos.Framework
import io.losos.common.StringADescriptor
import io.losos.etcd.EtcdEventBus
import io.losos.eventbus.Event
import io.losos.eventbus.EventBus
import io.losos.executor.KotlinTaskExecutor
import io.losos.process.engine.IDGenUUID
import io.losos.process.engine.ProcessManager
import io.losos.process.model.GANDef
import org.junit.Test
import java.io.File


open class LososTest {

    fun onEventBus(block: (EventBus) -> Unit) {
        connectToEtcd(Framework.Test.ETCD_URLS) {client ->
            val eventBus = EtcdEventBus(client)
            block(eventBus)
        }
    }


    fun withProcessManagerEtcdEB(block: LososTest.(EventBus, ProcessManager) -> Unit) {

        onEventBus { bus ->
            val pm = ProcessManager(bus, IDGenUUID)
            pm.startBrokering()
            this.block(bus, pm)
            pm.stopBrokering()
        }
    }

}

class AgentTaskActionTest: LososTest() {

    @Test fun directScheduleSuccess() = withProcessManagerEtcdEB { eventBus, pm ->
        Framework.init(mapOf(
                "process" to true,
                "etcdbus" to false,
                "pm" to true,
                "testDirectSchedule" to false
        ))

        val log = io.losos.logger("testDirectSchedule")
        val ganDef = Framework
                .jsonMapper
                .readValue(File("src/test/resources/cases/graphAgentTaskAction.json"), GANDef::class.java)


        val executor = KotlinTaskExecutor.runExecutor(
                    agentName = "agent1",
                    eventBus = eventBus,
                    descriptor = StringADescriptor("taskType")) { input ->
                log("Processing task: ${input}")
                Thread.sleep(1000)
                Framework.jsonMapper
                        .createObjectNode()
                        .put("response", "ok")
        }


        val gan = pm.createProcess(ganDef)

        eventBus.subscribe(gan.context.contextPath()) {
                log(it.toString())
        }

        gan.run()

        Thread.sleep(5)
        eventBus.emit("${gan.context.contextPath()}/start", Event.emptyPayload())

        gan.joinThread(10000)
        io.losos.log("done")
    }

    @Test fun testOneTimeoutDirectSchedule() {

    }

}