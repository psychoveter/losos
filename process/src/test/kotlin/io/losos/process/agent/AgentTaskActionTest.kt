package io.losos.process.agent

import io.etcd.recipes.common.connectToEtcd
import io.losos.TestUtils
import io.losos.common.StringADescriptor
import io.losos.platform.etcd.EtcdLososPlatform
import io.losos.platform.Event
import io.losos.platform.LososPlatform
import io.losos.executor.KotlinTaskExecutor
import io.losos.process.engine.NodeManager
import io.losos.process.engine.ProcessManager
import io.losos.process.library.EtcdProcessLibrary
import io.losos.process.model.ProcessDef
import org.junit.Test
import org.slf4j.LoggerFactory
import java.io.File


open class LososTest {

    fun onPlatform(block: (LososPlatform) -> Unit) {
        connectToEtcd(TestUtils.Test.ETCD_URLS) { client ->
            val eventBus = EtcdLososPlatform(client, TestUtils.jsonMapper)
            block(eventBus)
        }
    }


    fun withProcessManagerEtcdEB(block: LososTest.(LososPlatform, ProcessManager) -> Unit) {

        onPlatform { bus ->
            val library = EtcdProcessLibrary(bus, "/node/library/testnode")
            val nodeManager = NodeManager(bus, library, name = "testnode")
            nodeManager.start()
            val pm = nodeManager.processManager
            this.block(bus, pm)
            nodeManager.stop()
        }
    }

}

class AgentTaskActionTest: LososTest() {

    private val logger = LoggerFactory.getLogger(AgentTaskActionTest::class.java)

//    @Test
    fun directScheduleSuccess() = withProcessManagerEtcdEB { eventBus, pm ->

        val ganDef = TestUtils
                .jsonMapper
                .readValue(File("src/test/resources/cases/graphAgentTaskAction.json"), ProcessDef::class.java)


        val executor = KotlinTaskExecutor.runExecutor(
                    agentName = "agent1",
                    eventBus = eventBus,
                    descriptor = StringADescriptor("taskType")) { input ->
                logger.info("Processing task: ${input}")
                Thread.sleep(1000)
                TestUtils.jsonMapper
                        .createObjectNode()
                        .put("response", "ok")
        }


        val gan = pm.createProcess(ganDef)

        eventBus.subscribe(gan.context.pathState()) {
                logger.info(it.toString())
        }

        gan.run()

        Thread.sleep(5)
        eventBus.put("${gan.context.pathState()}/start", TestUtils.jsonMapper.createObjectNode())

        gan.joinThread(10000)
        logger.info("done")
    }

//    @Test
    fun testOneTimeoutDirectSchedule() {

    }

}