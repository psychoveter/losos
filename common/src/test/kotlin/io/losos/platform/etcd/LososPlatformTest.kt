package io.losos.platform.etcd

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.module.kotlin.KotlinModule
import io.losos.platform.Event
import io.etcd.recipes.common.connectToEtcd
import io.losos.TestUtils
import kotlinx.coroutines.*
import org.junit.Test
import org.slf4j.LoggerFactory
import java.lang.RuntimeException
import java.util.concurrent.CountDownLatch

class LososPlatformTest {

    val logger = LoggerFactory.getLogger(LososPlatformTest::class.java)

    @Test
    fun testSubs() {
        logger.info("Test etcd subscription")
        val latch = CountDownLatch(2)
        connectToEtcd(TestUtils.Test.ETCD_URLS) { client ->
            val bus = EtcdLososPlatform(client, TestUtils.jsonMapper)
            val callback: suspend (e: Event<ObjectNode>) -> Unit = { event: Event<ObjectNode> ->
                val text = "[${Thread.currentThread().name}] Received event: ${event.fullPath}: ${event.payload.toString()}"
                logger.info(text)
                latch.countDown()
            }

            logger.info("subscribe")
            val subs = bus.subscribe("/proc/p1", callback)

            logger.info("send")

            bus.put(path = "/proc/p2",
                    payload = TestUtils.jsonMapper
                            .createObjectNode()
                            .put("key", "should not receive"))
            
            bus.put(path = "/proc/p1",
                    payload = TestUtils.jsonMapper
                            .createObjectNode()
                            .put("key", "value"))
            bus.put(path = "/proc/p1/a",
                     payload = TestUtils.jsonMapper
                            .createObjectNode()
                                .put("key1", "value2"))

            latch.await()
            logger.info("close subs")
            subs.cancel()
        }
    }

    @Test
    fun testKeepAliveUsual() {
        testKeepAliveWithAction {
            (1..5).forEach { i ->
                logger.info("[${Thread.currentThread().name}] Tick: $i")
                Thread.sleep(1000)
            }
        }
    }

    @Test
    fun testKeepAliveExceptional() {
        testKeepAliveWithAction {
            throw RuntimeException("Something goes wrong")
        }
    }

    fun testKeepAliveWithAction(action: () -> Unit) {

        connectToEtcd(TestUtils.Test.ETCD_URLS) { client ->
            val latch = CountDownLatch(1)
            val callback: suspend (e: Event<ObjectNode>) -> Unit = { event: Event<ObjectNode> ->
                val text = "Received event: ${event.fullPath}: ${event.payload}"
                logger.info(text)
                latch.countDown()
            }

            runBlocking {
                val kaKey = "/watch/p1"
                logger.info("[${Thread.currentThread().name}] Create event bus")
                val bus = EtcdLososPlatform(client, TestUtils.jsonMapper)

                logger.info("[${Thread.currentThread().name}] Subscribe to KA")
                val subs = bus.subscribeDelete(kaKey, callback)

                val ctx = newSingleThreadContext("st_ctx")
                launch(ctx) {
                    logger.info("[${Thread.currentThread().name}] Inside launch before runInKeepAlive")
                    try {
                        bus.runInKeepAlive(kaKey) {
                            action()
                        }
                    } catch (exc: Exception) { logger.error("Exception happened: ${exc.localizedMessage}", exc) }
                    logger.info("finished ttl job")
                }.join()
            }

            latch.await()
        }

    }

}