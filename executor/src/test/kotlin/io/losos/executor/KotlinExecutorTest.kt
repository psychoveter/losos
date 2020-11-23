package io.losos.executor

import io.etcd.recipes.common.connectToEtcd
import io.losos.TestUtils
import io.losos.platform.etcd.EtcdLososPlatform
import io.losos.platform.LososPlatform
import io.losos.common.AgentTask
import io.losos.common.StringADescriptor
import org.junit.Test
import org.slf4j.LoggerFactory
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class KotlinExecutorTest {

    val logger = LoggerFactory.getLogger(KotlinExecutorTest::class.java)

    @Test
    fun getTask() {
        /**
         * Create simple kotlin executor, send task for it and await success response on the event bus.
         */


        val agentName = "agent1"
        val taskType = "type1"
        val taskPayload = TestUtils.jsonMapper
                .createObjectNode()
                .put("field1", "value1")
        val taskId = "task1"
        val task = AgentTask(
                id = taskId,
                type = taskType,
                payload = taskPayload,
                successEventPath = "${LososPlatform.PREFIX_TASKS_STATE}/$taskId/success",
                retryEventPath = "${LososPlatform.PREFIX_TASKS_STATE}/$taskId/retry",
                failureEventPath = "${LososPlatform.PREFIX_TASKS_STATE}/$taskId/failure"

        )

        val success = AtomicBoolean(false)
        val executorLeased = CountDownLatch(1)

        connectToEtcd(TestUtils.Test.ETCD_URLS) { client ->
            val bus = EtcdLososPlatform(client, TestUtils.jsonMapper)

            //bus.subscribe(EventBus.agentTasksPath(agentName)) { log(it.toString()) }
            bus.subscribe(LososPlatform.PREFIX_AGENT_LEASE) {
                logger.info(it.toString())
                if(it.payload["action"].textValue() == "DELETE")
                    executorLeased.countDown()
            }

            bus.subscribe(task.successEventPath) {
                logger.info(it.toString())
                success.set(true)
            }

            KotlinTaskExecutor.runExecutor(agentName, bus, StringADescriptor(taskType)) { input ->
                logger.info("Processing task: ${input}")
                Thread.sleep(1000)
                TestUtils.jsonMapper
                    .createObjectNode()
                    .put("response", "ok")
            }

            Thread.sleep(500)

            bus.put(LososPlatform.agentTaskPath(agentName, task.id), bus.object2json(task))

            executorLeased.await(10, TimeUnit.SECONDS)

            if(!success.get())
                throw RuntimeException("Failed test: didn't receive success event")

//TODO: lease doesn't work?
//            if(executorLeased.count != 0L)
//                throw RuntimeException("Lease key deletion expected")
        }
    }

}