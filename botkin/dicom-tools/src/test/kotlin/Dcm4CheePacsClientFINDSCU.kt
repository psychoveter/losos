import ai.botkin.oncore.test.PacsClientTest
import ai.botkin.oncore.dicom.dsl.PacsClientDsl
import ai.botkin.oncore.dicom.service.dto.FindSCURequest
import org.dcm4che3.data.Tag
import org.dcm4che3.data.UID
import org.dcm4che3.media.RecordType
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.slf4j.LoggerFactory

@TestInstance(TestInstance.Lifecycle.PER_METHOD)
class Dcm4CheePacsClientFINDSCU : PacsClientTest {

    fun createClient() = PacsClientDsl {

        PresentationContext {
            abstractSyntax = UID.SecondaryCaptureImageStorage
            transferSyntax(
                UID.ExplicitVRLittleEndian,
                UID.ExplicitVRBigEndianRetired,
                UID.ImplicitVRLittleEndian
            )
            isSCU = true
            isSCP = true
        }

        PresentationContext {
            abstractSyntax = UID.CTImageStorage
            transferSyntax(
                UID.ExplicitVRLittleEndian,
                UID.ExplicitVRBigEndianRetired,
                UID.ImplicitVRLittleEndian
            )
            isSCU = true
            isSCP = true
        }

        PresentationContext {
            abstractSyntax = UID.VerificationSOPClass
            transferSyntax(
                UID.ImplicitVRLittleEndian
            )
            isSCU = true
            isSCP = true
        }

        PACS {
            aeTitle = "DCM4CHEE"
            Connection {
                host = "localhost"
                port = 11112
            }
        }

        Device {
            name = "olegasus"
            threads = 3
        }

        ApplicationEntity {
            aeTitle = "OA"
            Connection {
                host = "0.0.0.0"
                port = 5999
            }
        }
        FindSCU { }
    }

    val logger = LoggerFactory.getLogger(Dcm4CheePacsClientFINDSCU::class.java)

    fun okTestFindLevelStudy() {

    }

    fun okTestFindLevelSeries() = withClient(createClient()) { client ->

        val results = client.findSync(FindSCURequest {
            level(RecordType.SERIES)
            match(Tag.StudyInstanceUID, PacsTestFiller.S1_MUR_UID)
            returnTags(
                Tag.StudyInstanceUID,
                Tag.SeriesInstanceUID, //do not return if missed at series level
                Tag.StudyDate,
                Tag.StudyTime,
                Tag.SeriesDate,
                Tag.SeriesTime,
                Tag.Modality,
                Tag.LargestPixelValueInSeries,
                Tag.SmallestPixelValueInSeries,
                Tag.PatientPosition,
                Tag.BodyPartExamined,
                Tag.ProtocolName
            )
        })
//        results.forEach {
//            logger.info("FIND-RESULT===============================================================")
//            DicomUtil.logAttributes(it, logger, "FIND-RESULT")
//        }
        assert(results.size == 4)
    }

    fun okTestFindLevelImage() {

    }


    fun erTestFindLevelStudy() {

    }

    fun erTestFindLevelSeriesMissedStudyUUID() {

    }

    fun erTestFindLevelImageMissedSeriesUUID() {

    }

}
