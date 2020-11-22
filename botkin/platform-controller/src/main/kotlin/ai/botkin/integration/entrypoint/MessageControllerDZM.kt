package ai.botkin.integration.entrypoint

import ai.botkin.integration.dto.KafkaMessageDto
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
class MessageControllerDZM (
    val objectMapper: ObjectMapper
) {

    private val logger = LoggerFactory.getLogger(MessageControllerDZM::class.java)

    @GetMapping
    fun processKafkaMessage(
        @RequestParam("message") message: String
    ) {
        logger.info("Got message: $message")
        val parsed = try {
            objectMapper.readValue(message, KafkaMessageDto::class.java)
        } catch (e: Exception) {
            logger.error("Failed to parse message", e)
            throw e
        }

        val studyUid = parsed.studyIUID

        //TODO: filter model id
        //TODO: start workflow processing

    }

    @KafkaListener(topics = ["\${kafka.listener-topic:test}"], autoStartup = "\${kafka.enabled:false}")
    fun receiveMessage(message: String) = processKafkaMessage(message)


}