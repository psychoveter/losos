package io.losos.process

import com.fasterxml.jackson.databind.node.ObjectNode
import io.etcd.jetcd.shaded.io.grpc.netty.GrpcSslContexts
import io.etcd.jetcd.shaded.io.netty.handler.codec.http2.Http2SecurityUtil
import io.etcd.jetcd.shaded.io.netty.handler.ssl.SslProvider
import io.etcd.jetcd.shaded.io.netty.handler.ssl.SupportedCipherSuiteFilter
import io.etcd.recipes.common.connectToEtcd
import io.losos.TestUtils
import io.losos.platform.Event
import io.losos.platform.etcd.EtcdLososPlatform
import kotlinx.coroutines.launch
import kotlinx.coroutines.newSingleThreadContext
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.slf4j.LoggerFactory
import java.io.File
import java.nio.charset.Charset
import java.nio.file.Files
import java.security.KeyFactory
import java.security.spec.PKCS8EncodedKeySpec
import java.util.*
import java.util.concurrent.CountDownLatch


class LososPlatformTest {

    val logger = LoggerFactory.getLogger(LososPlatformTest::class.java)


    @Test
    fun testEtcdSecure() {
        /**
         *
         * https://gitlab.botkin.ai/botkin/development/etcd/-/tree/secure
         *
         * etcdctl \
         *   --endpoints=https://etcd1.sandboxes.botkin.ai:443,https://etcd2.sandboxes.botkin.ai:443,https://etcd3.sandboxes.botkin.ai:443 \
         *   --cert="./etcd.pem" \
         *   --key="./etcd-key.pem" \
         *   endpoint status --write-out=table
         *
         * Смотрите, можно для тестов во время разработки использовать стенд sandboxes.
         * https://etcd1.sandboxes.botkin.ai:443
         * https://etcd2.sandboxes.botkin.ai:443
         * https://etcd3.sandboxes.botkin.ai:443
         * Либо сразу в TPAK, потом почистим перед непосредственной интеграцией  с ТПАК
         * https://etcd1.dzm.botkin.ai:443
         * https://etcd2.dzm.botkin.ai:443
         * https://etcd3.dzm.botkin.ai:443
         *
         *
         */

        val certsPath = "/home/veter/braingarden/onko/repos/etcd/java-pkcs8-jetcd-keys"

        val client = connectToEtcd(
            listOf(
                "https://etcd1.sandboxes.botkin.ai:443",
                "https://etcd2.sandboxes.botkin.ai:443",
                "https://etcd3.sandboxes.botkin.ai:443")
        ) {
            sslContext(GrpcSslContexts
                .forClient()
                .sslProvider(SslProvider.JDK)
                .ciphers(Http2SecurityUtil.CIPHERS, SupportedCipherSuiteFilter.INSTANCE)
//                .ciphers(listOf("ECDHE_RSA_AES128_GCM_SHA256"))
//                .trustManager(File("$certsPath/ca.pem"))
                .keyManager(
                    File("$certsPath/fullchain.pem"),
                    File("$certsPath/pkcs8-key.pem")
                ).build()
            )
        }

        val value = TestUtils.jsonMapper.createObjectNode().put("field", "value")
        val platform = EtcdLososPlatform(client, TestUtils.jsonMapper)
        platform.put("/key", value)
        val read = platform.getOne("/key", ObjectNode::class.java)
        assert(read?.equals(value) == true)
    }

    @Test
    fun testSubs() {
        logger.info("Test etcd subscription")
        val latch = CountDownLatch(2)
        connectToEtcd(TestUtils.Test.ETCD_URLS) { client ->
            val bus = EtcdLososPlatform(client, TestUtils.jsonMapper)
            val callback: suspend (e: Event) -> Unit = { event: Event ->
                val text = "[${Thread.currentThread().name}] Received event: $event"
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
            val callback: suspend (e: Event) -> Unit = { event: Event ->
                val text = "Received event: $event"
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

