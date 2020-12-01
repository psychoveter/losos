package ai.botkin.satellite

import ai.botkin.satellite.messages.TEPMessage
import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration
import springfox.documentation.builders.PathSelectors
import springfox.documentation.builders.RequestHandlerSelectors
import springfox.documentation.spi.DocumentationType
import springfox.documentation.spring.web.plugins.Docket
import springfox.documentation.swagger2.annotations.EnableSwagger2
import java.util.concurrent.ConcurrentLinkedDeque

@Configuration
@ConfigurationProperties(prefix = "agent")
class AgentsConfig{
    lateinit var ml:String
    lateinit var reporter:String
    lateinit var gateway:String
}


@SpringBootApplication
@Configuration
@ComponentScan
@EnableSwagger2

class SatelliteApplication {

    //    @Autowired lateinit var processor:Processor
    @Bean
    fun messageQueue():ConcurrentLinkedDeque<TEPMessage>{
        return ConcurrentLinkedDeque<TEPMessage>()
    }
    @Bean
    fun client(): RemoteClient {
        return RestClient()
    }


    @Bean
    fun api(): Docket? {
        return Docket(DocumentationType.SWAGGER_2)
            .select()
            .apis(RequestHandlerSelectors.any())
            .paths(PathSelectors.any())
            .build()
    }
}



fun main(args: Array<String>) {
    SpringApplication.run(SatelliteApplication::class.java, *args)
}
