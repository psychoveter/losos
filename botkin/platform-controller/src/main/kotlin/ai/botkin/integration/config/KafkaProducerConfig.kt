package ai.botkin.oncore.gateway.dzm.config

import org.apache.kafka.clients.CommonClientConfigs
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.common.config.SslConfigs
import org.apache.kafka.common.serialization.StringSerializer
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.core.DefaultKafkaProducerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.core.ProducerFactory

//@Configuration
class KafkaProducerConfig(
    @Value("\${kafka.producer.host:kafka.local:9092}")
    private val kafkaProducerHost: String,
    @Value("\${kafka.producer.truststore.location:%PROJECT_FOLDER%/gw/src/main/resources/kafka_cert/producer.truststore.jks}")
    private val kafkaProducerTruststoreLocation: String,
    @Value("\${kafka.producer.keystore.location:%PROJECT_FOLDER%/gw/src/main/resources/kafka_cert/producer.keystore.jks}")
    private val kafkaProducerKeystoreLocation: String,
    @Value("\${kafka.producer.truststore.password:datahub}")
    private val kafkaProducerTruststorePassword: String,
    @Value("\${kafka.producer.keystore.password:localhost:datahub}")
    private val kafkaProducerKeystorePassword: String,
    @Value("\${kafka.producer.ssl.enabled:true}")
    private val kafkaProducerSSLEnabled: Boolean
) {

    @Bean
    fun producerFactory(): ProducerFactory<String?, String?>? {
        val configProps: MutableMap<String, Any> = HashMap()
        configProps[ProducerConfig.BOOTSTRAP_SERVERS_CONFIG] = kafkaProducerHost
        configProps[ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG] = StringSerializer::class.java
        configProps[ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG] = StringSerializer::class.java

        if (kafkaProducerSSLEnabled) {
            configProps[CommonClientConfigs.SECURITY_PROTOCOL_CONFIG] = "SSL"
            configProps[SslConfigs.SSL_TRUSTSTORE_LOCATION_CONFIG] = kafkaProducerTruststoreLocation
            configProps[SslConfigs.SSL_TRUSTSTORE_PASSWORD_CONFIG] = kafkaProducerTruststorePassword
            configProps[SslConfigs.SSL_TRUSTSTORE_TYPE_CONFIG] = "JKS"

            configProps[SslConfigs.SSL_KEYSTORE_LOCATION_CONFIG] = kafkaProducerKeystoreLocation
            configProps[SslConfigs.SSL_KEYSTORE_PASSWORD_CONFIG] = kafkaProducerKeystorePassword
            configProps[SslConfigs.SSL_KEYSTORE_TYPE_CONFIG] = "JKS"
        }

        return DefaultKafkaProducerFactory(configProps)
    }

    @Bean
    fun kafkaTemplate(): KafkaTemplate<String?, String?>? {
        return KafkaTemplate(producerFactory()!!)
    }
}
