package ai.botkin

import io.etcd.jetcd.options.DeleteOption
import io.etcd.recipes.common.connectToEtcd
import io.losos.KeyConvention
import io.losos.TestUtils
import io.losos.platform.etcd.EtcdLososPlatform
import io.losos.common.ProcessDef
import java.io.File

class EtcdInitializer(val urls: List<String>){

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

    fun clean(): EtcdInitializer {
        connectToEtcd(urls) { client ->
            val platform = EtcdLososPlatform(client, TestUtils.jsonMapper)
            client.kvClient.delete(
                platform.fromString("/"),
                DeleteOption
                    .newBuilder()
                    .withPrefix(platform.fromString("/"))
                    .build())
                .get()
        }

        return this
    }

    fun initialize(): EtcdInitializer {
        val platform1 = "platform-controller"
        val satellite1 = "satellite-1"

        connectToEtcd(urls) { client ->
            val platform = EtcdLososPlatform(client, TestUtils.jsonMapper)
            //initialize platform
            getPlatformProcesses().forEach {
                platform.put(KeyConvention.keyNodeLibraryEntry(platform1, it.name), it)
            }

            //initialize satellites
            getSatelliteProcesses().forEach {
                platform.put(KeyConvention.keyNodeLibraryEntry(satellite1, it.name), it)
            }
        }
        return this
    }

}

fun main(args: Array<String>) {
    EtcdInitializer(TestUtils.Test.ETCD_URLS)
        .clean()
        .initialize()
}