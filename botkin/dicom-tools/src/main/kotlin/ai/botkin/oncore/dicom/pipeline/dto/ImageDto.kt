package ai.botkin.oncore.dicom.pipeline.dto

import org.dcm4che3.data.Attributes
import org.dcm4che3.io.DicomInputStream
import java.io.File

data class ImageDto(
    val sopInstanceUID: String,
    val dataset: Attributes?,
    val meta: Attributes?,
    val includeBulk: DicomInputStream.IncludeBulkData? = null,
    val bulkDataFiles: List<File>? = null
) {
    fun replaceDataset(dataset: Attributes) = ImageDto(sopInstanceUID, dataset, meta, includeBulk)
}