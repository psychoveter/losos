package ai.botkin.satellite.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Configuration
import org.springframework.stereotype.Component

@ConfigurationProperties(prefix = "agent")
@Component
class AgentsConfig{
    lateinit var ml:String
    lateinit var reporter:String
    lateinit var gateway:String
}
