package ai.botkin.satellite.config

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.stereotype.Component
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter



@Configuration @Component
/**
 * TODO: move common parts of satellite and platform into common module
 */
open class ObjectMapperConfig {

    @Bean
    open fun objectMapper(): ObjectMapper {
        val mapper = jacksonObjectMapper()

        val timeModule = JavaTimeModule()
        timeModule.addDeserializer(
            LocalDateTime::class.java,
            LocalDateTimeDeserializer(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSZ"))
        )
        mapper.registerModule(timeModule)

        return mapper
    }
}