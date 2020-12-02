package ai.botkin.oncore.dicom.service.dto

import org.dcm4che3.media.RecordType

data class GetSCURequest(
    val studyInstanceUID: String? = null,
    val seriesInstanceUID: String? = null,
    val sopInstanceUID: String? = null,
    val level: RecordType = RecordType.STUDY
): ServiceRequest {

    companion object {

    }

}

class GetSCURequestDsl {

}

