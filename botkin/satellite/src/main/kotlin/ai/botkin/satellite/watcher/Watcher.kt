package ai.botkin.satellite.watcher

import ai.botkin.satellite.service.ServiceActionManagerImpl
import org.springframework.beans.factory.annotation.Autowired

class Watcher() {
    @Autowired
    lateinit var serviceActionManagerImpl: ServiceActionManagerImpl

    fun watch(){}

}
