package ai.botkin.oncore.dicom.service.dto

import org.dcm4che3.data.UID

data class StoreSCURequest(
    val data: ByteArray,
    val transferSyntax: String = UID.ExplicitVRLittleEndian
): ServiceRequest