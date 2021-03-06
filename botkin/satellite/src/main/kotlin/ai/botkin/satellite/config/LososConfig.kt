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
import io.losos.process.planner.AsyncActionManager
import io.losos.process.planner.ServiceActionManager
import io.opentracing.Tracer
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.beans.factory.config.AutowireCapableBeanFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.client.RestTemplate

@Configuration
open class LososConfig {

    val etcdUrls: List<String> = listOf("http://127.0.0.1:2379")

    @Autowired
    lateinit var objectMapper: ObjectMapper

    @Autowired
    lateinit var autowireFactory: AutowireCapableBeanFactory

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
    open fun serviceManager(): ServiceActionManager = ServiceActionManagerImpl(restTemplate(), platform(), tracer())


    @Bean
    open fun asyncManager(): AsyncActionManager {
        val aam = AsyncActionManager(platform())
        aam.taskInitInterceptor = {
            autowireFactory.autowireBean(it)
        }
        return aam
    }

    @Bean
    open fun nodeManager(): NodeManager {
        val manager = NodeManager(
            platform(),
            library(),
            serviceActionManager = serviceManager(),
            asyncActionManager = asyncManager(),
            //TODO: setup from environment
            name = nodeName,
            host = "localhost"
        )
        manager.start()
        return manager
    }
//
//    @Bean
//    @ConfigurationProperties(prefix = "agent")
//    fun agentsConfig():AgentsConfig{
//        return AgentsConfig()
//
//    }
}