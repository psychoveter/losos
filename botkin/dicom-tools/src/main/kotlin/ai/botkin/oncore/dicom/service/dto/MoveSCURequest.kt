package ai.botkin.oncore.dicom.service.dto

import org.dcm4che3.media.RecordType

class MoveSCURequest(
    val destination: String,
    val studyInstanceUID: String? = null,
    val seriesInstanceUID: String? = null,
    val sopInstanceUID: String? = null,
    val level: RecordType = RecordType.STUDY,
    val awaitTimeout: Long = 5 * 60 * 1000 //5 min
): ServiceRequest {
}