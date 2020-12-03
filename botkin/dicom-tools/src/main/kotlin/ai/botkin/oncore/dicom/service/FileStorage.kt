package ai.botkin.oncore.dicom.service

import org.dcm4che3.data.Attributes
import org.dcm4che3.data.Tag
import org.dcm4che3.data.VR
import org.dcm4che3.io.DicomOutputStream
import org.dcm4che3.net.Association
import org.dcm4che3.net.PDVInputStream
import org.dcm4che3.net.Status
import org.dcm4che3.net.pdu.PresentationContext
import org.dcm4che3.net.service.DicomServiceException
import org.slf4j.LoggerFactory
import java.io.File
import kotlin.system.measureTimeMillis

class FileStorage(
    private val storagePath: String
) {

    private val logger = LoggerFactory.getLogger(this::class.java)

    fun storeToLocal(
        association: Association,
        pc: PresentationContext,
        rq: Attributes,
        data: PDVInputStream,
        rsp: Attributes
    ) {
        rsp.setInt(Tag.Status, VR.US, 0)
        val cuid = rq.getString(Tag.AffectedSOPClassUID)
        val iuid = rq.getString(Tag.AffectedSOPInstanceUID)
        val tsuid = pc.transferSyntax

        val fileName = "$iuid.dcm"
        val sourceLocalFile = File(storagePath, fileName)

        logger.info("Storing file $fileName")

        if (sourceLocalFile.exists()) {
            logger.info("File $fileName already exists, skip")
            return
        }

        val fileAbsolutePath = sourceLocalFile.absolutePath
        try {
            val time = measureTimeMillis {
                storeTo(association.createFileMetaInformation(iuid, cuid, tsuid), data, sourceLocalFile)
            }
            logger.info("Stored to file $fileAbsolutePath length ${sourceLocalFile.length()} for $time ms")
        } catch (e: Exception) {
            logger.error("Error storing file {}", fileAbsolutePath)
            deleteFile(association, sourceLocalFile)
            throw DicomServiceException(Status.ProcessingFailure, e)
        }
    }

    private fun storeTo(fmi: Attributes, data: PDVInputStream, file: File) {
        file.parentFile.mkdirs()
        val out = DicomOutputStream(file)
        out.use {
            out.writeFileMetaInformation(fmi)
            data.copyTo(out)
        }
    }

    private fun deleteFile(association: Association, file: File) {
        if (file.delete()) {
            logger.info("{}: M-DELETE {}", association, file)
        } else {
            logger.warn("{}: M-DELETE {} failed!", association, file)
        }
    }
}
