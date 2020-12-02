package ai.botkin.oncore.dicom.util

import ai.botkin.oncore.dicom.depers.DateGenerator
import ai.botkin.oncore.dicom.depers.FieldGenerator
import org.dcm4che3.data.*
import org.dcm4che3.net.ApplicationEntity
import org.dcm4che3.net.Device
import org.dcm4che3.net.Status
import org.dcm4che3.net.TransferCapability
import org.dcm4che3.net.pdu.AAssociateRQ
import org.dcm4che3.net.pdu.PresentationContext
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.lang.StringBuilder
import java.lang.reflect.Modifier
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.TimeUnit

object DicomUtil {

    private val logger = LoggerFactory.getLogger(DicomUtil::class.java)

    val dateGenerator = DateGenerator()

    val typeGen = mapOf<VR, () -> String?>(
        VR.UI to { FieldGenerator.nextDicomUID() },
        VR.DA to { dateGenerator.randomDate() },
        VR.DT to { dateGenerator.randomDate() },
        VR.PN to { UUID.randomUUID().toString() },
        /**
         *  VR Name    | Definition                                |Character Repertoire | Length of Value
         *  ------------------------------------------------------------------------------------------------
         *  LO         | A character string that may be padded with| Default Character   | 64 chars maximum
         *             | leading and/or trailing spaces. The       | ​Repertoire and/or   | (see Note inSection 6.2)
         *  Long       | character code 5CH (the BACKSLASH         | as defined by       |
         *  String     | "\" in ISO-IR 6) shall not be present,    | (0008,0005).        |
         *             | as it is used as the delimiter between    |                     |
         *             | values in multiple valued data elements.  |                     |
         *             | The string shall not have                 |                     |
         *             | Control Characters except for ESC.        |                     |
         */
        VR.LO to { FieldGenerator.randomStringForArray(CharArray(64)) },
        /**
         *  VR Name    | Definition                                |Character Repertoire | Length of Value
         *  ------------------------------------------------------------------------------------------------
         *  SH         | A character string that may be padded with| Default Character   | 16 chars maximum
         *             | leading and/or trailing spaces. The       | ​Repertoire and/or   | (see Note inSection 6.2)
         *  Short      | character code 5CH (the BACKSLASH         | as defined by       |
         *  String     | "\" in ISO-IR 6) shall not be present,    | (0008,0005).        |
         *             | as it is used as the delimiter between    |                     |
         *             | values for multiple data elements.        |                     |
         *             | The string shall not have                 |                     |
         *             | Control Characters except for ESC.        |                     |
         */
        VR.SH to { FieldGenerator.randomStringForArray(CharArray(16)) },
        /**
         *  VR Name    | Definition                                 |Character Repertoire | Length of Value
         *  ------------------------------------------------------------------------------------------------
         *  ST         | A character string that may contain one    | Default Character   | 1024 chars maximum
         *             | or more paragraphs. It may contain the     | ​Repertoire and/or   | (see Note inSection 6.2)
         *  Short Text | Graphic Character set and the Control      | as defined by       |
         *             | Characters, CR,LF, FF, and ESC. It may be  | (0008,0005).        |
         *             | padded with trailing spaces, which maybe   |                     |
         *             | ignored, but leading spaces are considered |                     |
         *             | to be significant. DataElements with this  |                     |
         *             | VR shall not be multi-valued and therefore |                     |
         *             | character code 5CH (the BACKSLASH "\" in   |                     |
         *             | ISO-IR 6) may be used.                     |                     |
         */
        VR.ST to { FieldGenerator.randomStringForArray(CharArray(16)) },
        /**
         *  VR Name    | Definition                                |Character Repertoire | Length of Value
         *  ------------------------------------------------------------------------------------------------
         *  SQ         | Value is a Sequence of zero or more Items,| not applicable      | not applicable
         *             | as defined in Section 7.5.                | (seeSection 7.5)    | (seeSection 7.5)
         *  Sequence   |                                           |                     |
         *  of Items   |                                           |                     |
         */
        VR.SQ to { FieldGenerator.nextDicomUID() }
    )

    fun randomizeOrDelete(attributes: Attributes, tag: Int) {
        val vr = ElementDictionary.getStandardElementDictionary().vrOf(tag)
        if (vr == null) {
            attributes.remove(tag)
            return
        }
        val generator = typeGen[vr]
        if (generator == null) {
            attributes.remove(tag)
            return
        }
        val newValue = generator()
        attributes.setString(tag, vr, newValue)
    }

    fun logAttributes(att: Attributes, logger: Logger, pref: String = "") {
        logger.debug("[$pref]: properties=${att.properties}, " +
            "isEmpty=${att.isEmpty}, " +
            "isRoot=${att.isRoot}, " +
            "length=${att.length}, " +
            "itemPosition=${att.itemPosition}, " +
            "specificCharacterSet=${att.specificCharacterSet}")

        att.accept({ attrs, tag, vr, value ->
            try {
                val sb = StringBuilder()
                vr?.prompt(value, false, attrs?.specificCharacterSet, 1000, sb)
                logger.info("[$pref]:" +
                    " ${StandardElementDictionary.INSTANCE.keywordOf(tag)} : ${vr.toString()} : ${sb.toString()}"
                )
            } catch (e: Exception) {
                logger.error(
                    "[$pref]: Failed to print vr: ${vr}, tag: ${StandardElementDictionary.INSTANCE.keywordOf(tag)}",
                    e
                )
            }
            true
        }, true)
    }

    fun addPresentationContext(rq: AAssociateRQ, abstractSyntax: String, vararg tss: String) {
        val pcid = 2 * rq.numberOfPresentationContexts + 1
        logger.info("Adding presentation context pcid {} as {} tss {}", pcid, abstractSyntax, tss)
        rq.addPresentationContext(PresentationContext(pcid, abstractSyntax, *tss))
    }

    fun getStatusString(status: Int): String? {
        for (f in Status::class.java.getDeclaredFields()) {
            val mod: Int = f.getModifiers()
            if (
                Modifier.isStatic(mod) &&
                Modifier.isPublic(mod) &&
                Modifier.isFinal(mod)
            ) {
                try {
                    val name = f.name
                    val value = f.get(null)
                    if (value == status)
                        return name
                } catch (e: IllegalAccessException) {
                    return null
                }
            }
        }
        return null
    }

    fun configureStoreTransferCapability(ae: ApplicationEntity) {
        val sopClasses = listOf(
            UID.VerificationSOPClass,
            UID.DigitalXRayImageStorageForPresentation,
            UID.DigitalXRayImageStorageForProcessing,
            UID.DigitalMammographyXRayImageStorageForPresentation,
            UID.DigitalMammographyXRayImageStorageForProcessing,
            UID.DigitalIntraOralXRayImageStorageForPresentation,
            UID.DigitalIntraOralXRayImageStorageForProcessing,
            UID.CTImageStorage,
            UID.EnhancedCTImageStorage,
            UID.XRayAngiographicImageStorage,
            UID.XRayRadiofluoroscopicImageStorage,
            UID.XRay3DAngiographicImageStorage,
            UID.XRay3DCraniofacialImageStorage,
            UID.XRayAngiographicBiPlaneImageStorageRetired,
            UID.MammographyCADSRStorage,
            UID.SecondaryCaptureImageStorage,
            UID.GrayscaleSoftcopyPresentationStateStorage,
            UID.ComprehensiveSRStorage,
            UID.ComputedRadiographyImageStorage,
            UID.BasicTextSRStorage,
            UID.KeyObjectSelectionDocumentStorage,
            UID.EnhancedSRStorage
        )

        for (cuid in sopClasses) {
            val tc = TransferCapability(null,
                cuid,
                TransferCapability.Role.SCP,
                UID.ImplicitVRLittleEndian, UID.ExplicitVRLittleEndian, UID.DeflatedExplicitVRLittleEndian, UID.JPEGLossless
            )
            ae.addTransferCapability(tc)
        }
    }

    /**
     * Shutdowns device and it's internal executors
     * @param device - device to shut down
     */
    fun shutdownDevice(device: Device) {
        fun shutdownExecutor(executorService: ExecutorService) {
            logger.info("Shutdown executor service...")
            executorService.shutdown()
            try {
                val seconds2wait: Long = 5
                logger.info("Await termination $seconds2wait")
                if (!executorService.awaitTermination(seconds2wait, TimeUnit.SECONDS)) {
                    executorService.shutdownNow()
                }
            } catch (e: InterruptedException) {
                executorService.shutdownNow()
            }
        }

        shutdownExecutor(device.executor as ExecutorService)
        shutdownExecutor(device.scheduledExecutor)
    }

}
