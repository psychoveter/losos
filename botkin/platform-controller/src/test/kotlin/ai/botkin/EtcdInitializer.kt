package ai.botkin

import com.fasterxml.jackson.databind.ObjectMapper
import io.etcd.recipes.common.connectToEtcd
import io.losos.KeyConvention
import io.losos.TestUtils
import io.losos.platform.etcd.EtcdLososPlatform
import io.losos.process.model.ProcessDef
import java.io.File

class EtcdInitializer {

    fun getPlatformProcesses(): List<ProcessDef> {
        val dir = File("/home/veter/braingarden/losos/botkin/platform-controller/src/main/resources/defs")
        return dir.listFiles()
            .map {
                TestUtils.jsonMapper.readValue(it, ProcessDef::class.java)
            }
            .toList()
    }

    fun getSatelliteProcesses(): List<ProcessDef> {
        val dir = File("/home/veter/braingarden/losos/botkin/satellite/src/main/resources/defs")
        return dir.listFiles()
            .map {
                TestUtils.jsonMapper.readValue(it, ProcessDef::class.java)
            }
            .toList()
    }

    fun initialize(urls: List<String>) {
        val platform1 = "platform-controller"
        val satellite1 = "satellite-1"

        connectToEtcd(urls) { client ->
            val platform = EtcdLososPlatform(client, TestUtils.jsonMapper)
            //initialize platform
            getPlatformProcesses().forEach {
                platform.put(KeyConvention.keyProcessLibrary(platform1, it.name), it)
            }

            //initialize satellites
            getSatelliteProcesses().forEach {
                platform.put(KeyConvention.keyProcessLibrary(satellite1, it.name), it)
            }
        }

    }

}

fun main(args: Array<String>) {
    EtcdInitializer().initialize(TestUtils.Test.ETCD_URLS)
}