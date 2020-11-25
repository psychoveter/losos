package ai.botkin.satellite

import botkin.ai.RemoteClient
import botkin.ai.RestClient

import botkin.ai.messages.Message
import io.jaegertracing.internal.JaegerTracer
import io.jaegertracing.internal.samplers.ProbabilisticSampler
import io.opentracing.Tracer
import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.ConstructorBinding
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration
import org.springframework.stereotype.Component
import springfox.documentation.builders.PathSelectors
import springfox.documentation.builders.RequestHandlerSelectors
import springfox.documentation.spi.DocumentationType
import springfox.documentation.spring.web.plugins.Docket
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
//@EnableConfigurationProperties(AgentsConfig::class)
class SatelliteApplication {

    //    @Autowired lateinit var processor:Processor
    @Bean
    fun messageQueue():ConcurrentLinkedDeque<Message>{
        return ConcurrentLinkedDeque<Message>()
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
