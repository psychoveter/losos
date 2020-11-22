package ai.botkin.platform.config

import io.etcd.recipes.common.connectToEtcd
import io.losos.platform.etcd.EtcdLososPlatform
import io.losos.platform.LososPlatform
import io.losos.process.engine.IDGenUUID
import io.losos.process.engine.NodeManager
import io.losos.process.engine.ProcessManager
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration


@Configuration
open class LososConfig(
    val etcdUrls: List<String> = listOf("http://81.29.130.10:2379")
) {

    @Bean
    open fun eventBus(): LososPlatform {
        val etcdClient = connectToEtcd(etcdUrls)
        val etcdBus = EtcdLososPlatform(etcdClient)
        return etcdBus
    }

    @Bean
    open fun nodeManager(): NodeManager {
        val manager = NodeManager(eventBus(), "localhost")
        manager.start()
        return manager
    }

}