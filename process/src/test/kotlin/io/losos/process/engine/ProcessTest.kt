package io.losos.process.engine

import io.losos.platform.etcd.EtcdLososPlatform
import io.losos.common.ProcessDef
import io.etcd.recipes.common.connectToEtcd
import io.losos.TestUtils
import io.losos.process.library.EtcdProcessLibrary
import io.losos.common.GuardState
import org.junit.Test
import org.slf4j.LoggerFactory
import java.io.File
import java.lang.RuntimeException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit


class ProcessTest {

    //Open questions:
    // Waiting room for events: if guard was not raised before event has been received
    val logger = LoggerFactory.getLogger(ProcessTest::class.java)

    @Test fun simpleLineTest() {

//        val mapper = io.losos.TestUtils.jsonMapper
//        val ganDef = mapper.readValue(File("src/test/resources/cases/graphLine.json"), ProcessDef::class.java)
//
//        logger.info("Read process definition: ${mapper.writeValueAsString(ganDef)}")
//
//        val latch = CountDownLatch(1)
//        val guardSet = mutableSetOf(
//                "/guard/guard_one/1",
//                "/guard/guard_two/1",
//                "/guard/guard_three/1",
//                "/guard/guard_four/1",
//                "/guard/guard_exit/1",
//                "/go3",
//                "/start"
//        )
//
//        connectToEtcd(TestUtils.Test.ETCD_URLS) { client ->
//            val eBus = EtcdLososPlatform(client, TestUtils.jsonMapper)
//            val library = EtcdProcessLibrary(eBus, "/node/library/testnode")
//            val nodeManager = NodeManager(eBus, library, name = "testnode")
//            val pm = nodeManager.processManager
//
//            pm.startBrokering()
//
//            val gan = pm.createProcess(ganDef)
//
//            eBus.subscribe(gan.context.pathState()) {
//                logger.info("47 TEST LISTEN: $it")
//                val relativePath = it.fullPath.removePrefix(gan.context.pathState())
//                logger.info("49 relativePath: $relativePath, fullPath: ${it.fullPath}")
//                if(relativePath.startsWith("/guard")) {
//                    val state = GuardState.valueOf(it.payload?.get("state")!!.textValue())
//                    if(state == GuardState.OPENED)
//                        guardSet.remove(relativePath)
//                }
//                if(relativePath.startsWith("/go3") || relativePath.startsWith("/start"))
//                    guardSet.remove(relativePath)
//
//                if(guardSet.isEmpty())
//                    latch.countDown()
//            }
//
//            gan.run()
//
//            eBus.put("${gan.context.pathState()}/start", TestUtils.jsonMapper.createObjectNode())
//            Thread.sleep(200)
//            eBus.put("${gan.context.pathState()}/go3", TestUtils.jsonMapper.createObjectNode())
//
//            latch.await(10, TimeUnit.SECONDS)
//
//            if(guardSet.isNotEmpty())
//                throw RuntimeException("Some guards weren't opened: ${guardSet}")
//        }

    }

    @Test fun xorGuardsTest() {
        //TODO
    }

}


