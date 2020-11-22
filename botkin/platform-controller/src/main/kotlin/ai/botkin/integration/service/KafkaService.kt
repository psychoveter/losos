package ai.botkin.integration.service

import org.springframework.beans.factory.annotation.Value
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Component

@Component
class KafkaService(
    private val kafka: KafkaTemplate<String, String>,
    @Value("\${kafka.sender-topic:senderTest}")
    private val senderTopic: String
) {

    fun send(message: String) {
        kafka.send(senderTopic, message)
    }

}