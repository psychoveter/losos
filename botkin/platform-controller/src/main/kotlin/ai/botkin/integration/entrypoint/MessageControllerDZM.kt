package ai.botkin.integration.entrypoint

import ai.botkin.integration.dto.KafkaMessageDto
import ai.botkin.platform.service.LososService
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.web.bind.annotation.*

@RestController
class MessageControllerDZM (
    val objectMapper: ObjectMapper,
    val lososService: LososService
) {

    private val logger = LoggerFactory.getLogger(MessageControllerDZM::class.java)

    //TODO: should be filtered by workflow filters, not at controller layer
    private val allowedModelIds = setOf(1001, 1012, 1013, 1031)

    @PostMapping("/kafka")
    fun processKafkaMessage(
        @RequestBody message: KafkaMessageDto
    ): ResponseEntity<String> {
        logger.info("Got Kafka POST for processing (studyUid=${message.studyIUID})")

        val pid = lososService.runProcess(objectMapper
            .createObjectNode()
                .put("studyUid", message.studyIUID)
                .put("modelId", message.modelId))

        //TODO: filter model id
        //TODO: start workflow processing
        return ResponseEntity(pid, HttpStatus.OK)
    }

    @KafkaListener(topics = ["\${kafka.listener-topic:test}"], autoStartup = "\${kafka.enabled:false}")
    fun receiveMessage(message: String) {
        logger.info("Got message: $message")
        val parsed = try {
            objectMapper.readValue(message, KafkaMessageDto::class.java)
        } catch (e: Exception) {
            logger.error("Failed to parse message", e)
            throw e
        }
        processKafkaMessage(parsed)
    }


}