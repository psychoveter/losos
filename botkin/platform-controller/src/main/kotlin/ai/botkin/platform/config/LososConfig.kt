package ai.botkin.platform.config

import com.fasterxml.jackson.databind.ObjectMapper
import io.etcd.recipes.common.connectToEtcd
import io.losos.KeyConvention
import io.losos.platform.etcd.EtcdLososPlatform
import io.losos.platform.LososPlatform
import io.losos.process.engine.NodeManager
import io.losos.process.library.EtcdProcessLibrary
import io.losos.process.library.ProcessLibrary
import io.losos.process.planner.AsyncActionManager
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.config.AutowireCapableBeanFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration


@Configuration
open class LososConfig(
    val etcdUrls: List<String> = listOf("http://81.29.130.10:2379")
) {

    @Autowired
    lateinit var objectMapper: ObjectMapper

    @Autowired
    lateinit var autowireFactory: AutowireCapableBeanFactory

    val nodeName = "platform-controller"

    @Bean
    open fun platform(): LososPlatform {
        val etcdClient = connectToEtcd(etcdUrls)
        val etcdBus = EtcdLososPlatform(etcdClient, objectMapper)
        return etcdBus
    }


    @Bean
    open fun library(): ProcessLibrary = EtcdProcessLibrary(platform(), KeyConvention.keyNodeLibrary(nodeName))

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
            asyncActionManager = asyncManager(),
            //TODO: setup from environment
            name = nodeName,
            host = "localhost"
        )
        manager.start()
        return manager
    }

}