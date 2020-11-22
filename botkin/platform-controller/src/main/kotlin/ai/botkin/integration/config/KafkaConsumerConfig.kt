package ai.botkin.oncore.gateway.dzm.config

import org.apache.kafka.clients.CommonClientConfigs
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.common.config.SslConfigs
import org.apache.kafka.common.serialization.StringDeserializer
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.annotation.EnableKafka
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory
import org.springframework.kafka.core.ConsumerFactory
import org.springframework.kafka.core.DefaultKafkaConsumerFactory
import java.util.*

//@EnableKafka
//@Configuration
class KafkaConsumerConfig(
    @Value("\${kafka.consumer.host:kafka.local:9092}")
    private val kafkaConsumerHost: String,
    @Value("\${kafka.consumer.truststore.location:%PROJECT_FOLDER%/gw/src/main/resources/kafka_cert/consumer.truststore.jks}")
    private val kafkaConsumerTruststoreLocation: String,
    @Value("\${kafka.consumer.keystore.location:%PROJECT_FOLDER%/gw/src/main/resources/kafka_cert/consumer.keystore.jks}")
    private val kafkaConsumerKeystoreLocation: String,
    @Value("\${kafka.consumer.truststore.password:datahub}")
    private val kafkaConsumerTruststorePassword: String,
    @Value("\${kafka.consumer.keystore.password:datahub}")
    private val kafkaConsumerKeystorePassword: String,
    @Value("\${kafka.consumer.dzm-group-id:botkinai}")
    private val kafkaConsumerDZMGroupId: String,
    @Value("\${kafka.consumer.ssl.enabled:true}")
    private val kafkaConsumerSSLEnabled: Boolean
) {

    @Bean
    fun consumerFactory(): ConsumerFactory<String?, String?> {
        val configProps: MutableMap<String, Any> = HashMap()
        configProps[ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG] = kafkaConsumerHost
        configProps[ConsumerConfig.GROUP_ID_CONFIG] = kafkaConsumerDZMGroupId
        configProps[ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG] = StringDeserializer::class.java
        configProps[ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG] = StringDeserializer::class.java

        if (kafkaConsumerSSLEnabled) {
            configProps[CommonClientConfigs.SECURITY_PROTOCOL_CONFIG] = "SSL"
            configProps[SslConfigs.SSL_TRUSTSTORE_LOCATION_CONFIG] = kafkaConsumerTruststoreLocation
            configProps[SslConfigs.SSL_TRUSTSTORE_PASSWORD_CONFIG] = kafkaConsumerTruststorePassword
            configProps[SslConfigs.SSL_TRUSTSTORE_TYPE_CONFIG] = "JKS"

            //TODO this is redundant data - but let it be, since our colleagues so want
            configProps[SslConfigs.SSL_KEYSTORE_LOCATION_CONFIG] = kafkaConsumerKeystoreLocation
            configProps[SslConfigs.SSL_KEYSTORE_PASSWORD_CONFIG] = kafkaConsumerKeystorePassword
            configProps[SslConfigs.SSL_KEYSTORE_TYPE_CONFIG] = "JKS"
        }

        return DefaultKafkaConsumerFactory(configProps)
    }

    @Bean
    fun kafkaListenerContainerFactory(): ConcurrentKafkaListenerContainerFactory<String, String>? {
        val factory = ConcurrentKafkaListenerContainerFactory<String, String>()
        factory.consumerFactory = consumerFactory()
        return factory
    }
}
