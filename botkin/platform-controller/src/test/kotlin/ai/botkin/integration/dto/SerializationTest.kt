package ai.botkin.integration.dto

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.Test
import org.junit.runner.RunWith
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.junit4.SpringRunner

@SpringBootTest
@RunWith(SpringRunner::class)
class SerializationTest {

    private val logger = LoggerFactory.getLogger(SerializationTest::class.java)

    @Autowired
    lateinit var objectMapper: ObjectMapper

    @Test
    fun testKafkaSerialzation() {
        val sampleMessage = """
            { 
                "studyIUID":"1.2.392.200036.9116.2.5.1.11341.1409398444.1586589799.894234",
                "modelId":1014,"studyDate":"2020-09-25T13:40:04.558+0300",
                "researchParams":{"modalityTypeCode":"CT","anatomicalAreasCode":["CHEST"],"ageGroup":"ADULT"}
            }
        """.trimIndent()

        val parsed = objectMapper.readValue(sampleMessage, KafkaMessageDto::class.java)
        assert(parsed.modelId == 1014)
        logger.info(parsed.toString())
    }

}