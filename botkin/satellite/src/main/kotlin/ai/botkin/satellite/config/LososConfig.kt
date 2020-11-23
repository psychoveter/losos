package ai.botkin.satellite.config

import com.fasterxml.jackson.databind.ObjectMapper
import io.etcd.recipes.common.connectToEtcd
import io.losos.platform.LososPlatform
import io.losos.platform.etcd.EtcdLososPlatform
import io.losos.process.engine.NodeManager
import io.losos.process.library.EtcdProcessLibrary
import io.losos.process.library.ProcessLibrary
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
open class LososConfig {

    val etcdUrls: List<String> = listOf("http://81.29.130.10:2379")

    @Autowired
    lateinit var objectMapper: ObjectMapper

    @Value("\${losos.node-name:satellite-1}")
    lateinit var nodeName: String

    @Bean
    open fun platform(): LososPlatform {
        val etcdClient = connectToEtcd(etcdUrls)
        val etcdBus = EtcdLososPlatform(etcdClient, objectMapper)
        return etcdBus
    }

    @Bean
    open fun library(): ProcessLibrary = EtcdProcessLibrary(platform(), "/node/$nodeName/library/")

    @Bean
    open fun nodeManager(): NodeManager {
        val manager = NodeManager(
            platform(),
            library(),

            //TODO: setup from environment
            name = nodeName,
            host = "localhost"
        )
        manager.start()
        return manager
    }
}