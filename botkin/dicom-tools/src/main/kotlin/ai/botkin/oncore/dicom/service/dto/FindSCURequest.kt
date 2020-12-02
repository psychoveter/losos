package ai.botkin.oncore.dicom.service.dto

import org.dcm4che3.media.RecordType

data class FindSCURequest(
//    val patientId: String?,
//    val studyInstanceUID: String?,
//    val seriesInstanceUID: String?,
//    val sopInstanceUID: String?,
//    val modality: String?,
//    val studyDateTimeQuery: DateTimeQuery?,
//    val seriesDateTimeQuery: DateTimeQuery?,
    val level: RecordType = RecordType.STUDY,
    val matchingKeys: List<Pair<Int, String>>,
    val returnTags: List<Int>
): ServiceRequest {
    companion object {
        operator fun invoke(init: FindSCURequestDsl.() -> Unit): FindSCURequest {
            val dsl = FindSCURequestDsl().apply(init)
            return dsl.build()
        }
    }
}



class FindSCURequestDsl {
    private lateinit var _level: RecordType
    private val matchingKeys: MutableList<Pair<Int, String>> = mutableListOf()
    private val returnKeys = mutableListOf<Int>()

    fun match(tag: Int, value: String) {
        val pair = Pair(tag, value)
        matchingKeys.add(pair)
    }

    fun returnTags(vararg tags: Int) {
        tags.forEach { returnKeys.add(it) }
    }

    fun level(l: RecordType) {
        _level = l
    }

    /**
     * TODO:
     *  - Validate parameters for different request levels
     *  - Set default max value of responses
     */
    fun build(): FindSCURequest {
        return FindSCURequest(
            level = _level,
            matchingKeys = matchingKeys.toList(),
            returnTags = returnKeys.toList()
        )
    }
}

data class DateTimeQuery(
    val dateFrom: String?,
    val dateTo: String?,
    val timeFrom: String?,
    val timeTo: String?
)