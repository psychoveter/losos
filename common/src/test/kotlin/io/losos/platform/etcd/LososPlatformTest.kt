package io.losos.platform.etcd

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.module.kotlin.KotlinModule
import io.losos.platform.Event
import io.etcd.recipes.common.connectToEtcd
import io.losos.TestUtils
import kotlinx.coroutines.*
import org.junit.Test
import java.lang.RuntimeException
import java.util.concurrent.CountDownLatch

class LososPlatformTest {


    @Test fun testSubs() {
        println("Test etcd subscription")
        val latch = CountDownLatch(2)
        connectToEtcd(TestUtils.Test.ETCD_URLS) { client ->
            val bus = EtcdLososPlatform(client, TestUtils.jsonMapper)
            val callback: suspend (e: Event<ObjectNode>) -> Unit = { event: Event<ObjectNode> ->
                val text = "[${Thread.currentThread().name}] Received event: ${event.fullPath}: ${event.payload.toString()}"
                println(text)
                latch.countDown()
            }

            println("subscribe")
            val subs = bus.subscribe("/proc/p1", callback)

            println("send")

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
            println("close subs")
            subs.cancel()
        }
    }

    @Test fun testKeepAliveUsual() {
        testKeepAliveWithAction {
            (1..5).forEach { i ->
                println("[${Thread.currentThread().name}] Tick: $i")
                Thread.sleep(1000)
            }
        }
    }

    @Test fun testKeepAliveExceptional() {
        testKeepAliveWithAction {
            throw RuntimeException("Something goes wrong")
        }
    }

    fun testKeepAliveWithAction(action: () -> Unit) {

        connectToEtcd(TestUtils.Test.ETCD_URLS) { client ->
            val latch = CountDownLatch(1)
            val callback: suspend (e: Event<ObjectNode>) -> Unit = { event: Event<ObjectNode> ->
                val text = "Received event: ${event.fullPath}: ${event.payload}"
                println(text)
                latch.countDown()
            }

            runBlocking {
                val kaKey = "/watch/p1"
                println("[${Thread.currentThread().name}] Create event bus")
                val bus = EtcdLososPlatform(client, TestUtils.jsonMapper)

                println("[${Thread.currentThread().name}] Subscribe to KA")
                val subs = bus.subscribeDelete(kaKey, callback)

                val ctx = newSingleThreadContext("st_ctx")
                launch(ctx) {
                    println("[${Thread.currentThread().name}] Inside launch before runInKeepAlive")
                    try {
                        bus.runInKeepAlive(kaKey) {
                            action()
                        }
                    } catch (exc: Exception) { println("Exception happened: ${exc.localizedMessage}")}
                    println("finished ttl job")
                }.join()
            }

            latch.await()
        }

    }

}