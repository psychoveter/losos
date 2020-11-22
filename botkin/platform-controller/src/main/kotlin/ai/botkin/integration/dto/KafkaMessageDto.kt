package ai.botkin.integration.dto

import com.fasterxml.jackson.annotation.JsonProperty
import java.time.LocalDateTime

data class KafkaMessageDto (
    val studyIUID: String,
    val modelId: Int,
    val studyDate: LocalDateTime,
    val researchParams: ResearchParams
)

data class ResearchParams(
    val modalityTypeCode: String,
    val anatomicalAreasCode: List<String>,
    val ageGroup: String
)