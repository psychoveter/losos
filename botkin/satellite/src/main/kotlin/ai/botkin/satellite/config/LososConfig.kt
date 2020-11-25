package ai.botkin.satellite.config

import ai.botkin.satellite.service.ServiceActionManagerImpl
import com.fasterxml.jackson.databind.ObjectMapper
import io.etcd.recipes.common.connectToEtcd
import io.losos.KeyConvention
import io.losos.platform.LososPlatform
import io.losos.platform.etcd.EtcdLososPlatform
import io.losos.process.engine.NodeManager
import io.losos.process.library.EtcdProcessLibrary
import io.losos.process.library.ProcessLibrary
import io.opentracing.Tracer
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.client.RestTemplate

@Configuration
open class LososConfig {

    val etcdUrls: List<String> = listOf("http://81.29.130.10:2379")

    @Autowired
    lateinit var objectMapper: ObjectMapper

    @Value("\${losos.node-name:satellite-1}")
    lateinit var nodeName: String

    @Bean
    open fun tracer():Tracer {
        return io.jaegertracing.Configuration.fromEnv(nodeName).tracer
    }

    @Bean
    open fun restTemplate() = RestTemplate()

    @Bean
    open fun platform(): LososPlatform {
        val etcdClient = connectToEtcd(etcdUrls)
        val etcdBus = EtcdLososPlatform(etcdClient, objectMapper)
        return etcdBus
    }

    @Bean
    open fun library(): ProcessLibrary = EtcdProcessLibrary(platform(), KeyConvention.keyNodeLibrary(nodeName))

    @Bean
    open fun nodeManager(): NodeManager {
        val manager = NodeManager(
            platform(),
            library(),
            serviceActionManager = ServiceActionManagerImpl(restTemplate(), platform(), tracer()),
            //TODO: setup from environment
            name = nodeName,
            host = "localhost"
        )
        manager.start()
        return manager
    }
}