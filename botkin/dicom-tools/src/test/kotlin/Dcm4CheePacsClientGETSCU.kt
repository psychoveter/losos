import ai.botkin.oncore.test.PacsClientTest
import ai.botkin.oncore.dicom.util.DicomUtil
import ai.botkin.oncore.dicom.dsl.PacsClientDsl
import ai.botkin.oncore.dicom.pipeline.PacsSubscriber
import ai.botkin.oncore.dicom.pipeline.TransferState
import ai.botkin.oncore.dicom.pipeline.dto.ImageDto
import ai.botkin.oncore.dicom.service.dto.GetSCURequest
import org.dcm4che3.data.Tag
import org.dcm4che3.data.UID
import org.dcm4che3.media.RecordType
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.slf4j.LoggerFactory
import java.util.concurrent.CancellationException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@TestInstance(TestInstance.Lifecycle.PER_METHOD)
class Dcm4CheePacsClientGETSCU : PacsClientTest {

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

// dzm test
//        PACS {
//            aeTitle = "SSLAIBUSSCP"
//            Connection {
//                host = "178.208.149.80"
//                port = 21113
//            }
//        }


// dzm prod
//        PACS {
//            aeTitle = "SSLAIBUSSCP"
//            Connection {
//                host = "178.208.149.75"
//                port = 21113
//            }
//        }

// local DCM4CHEE
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
//            aeTitle = "CALLER_AE"
            Connection {
                host = "0.0.0.0"
                port = 61386
            }

        }

        GetSCU { }

        Depersonalization {
            strategy = "Random" // | Delete | Persistent | None
            tags(
                Tag.PerformingPhysicianName,
                Tag.ScheduledPerformingPhysicianName,
                Tag.InstitutionAddress,
                Tag.ReferringPhysicianName,
                Tag.OperatorsName,
                Tag.OtherPatientNames,
                Tag.ReferringPhysicianAddress,
                Tag.PatientBirthName,
                Tag.RequestingPhysician,
                Tag.PatientAddress,
                Tag.PatientMotherBirthName,
                Tag.CurrentPatientLocation,
                Tag.PatientName,
                Tag.ReferringPhysicianTelephoneNumbers,
                Tag.PatientTelephoneNumbers,
                Tag.ConsultingPhysicianName
            )
        }
    }

    val logger = LoggerFactory.getLogger(Dcm4CheePacsClientGETSCU::class.java)

    fun okTestGetLevelStudy() = withClient(createClient()) { client ->

        val handle = client.get(GetSCURequest(
                studyInstanceUID = "1.2.392.200036.9116.2.5.1.37.2418211266.1600912062.297494",//PacsTestFiller.S1_MUR_UID,
                level = RecordType.STUDY
            ))
            .accumulate()
            .start()

        val res = handle.future
            .orTimeout(10, TimeUnit.SECONDS)
            .join()

        handle.clear()

        assert(res.size  == 500)
    }

    fun okTestGetLevelSeries() = withClient(createClient()) { client ->
        val handle = client.get(GetSCURequest(
                studyInstanceUID = PacsTestFiller.S1_MUR_UID,
                seriesInstanceUID = PacsTestFiller.S12_AXIAL_MUR_UID,
                level = RecordType.SERIES
            ))
            .accumulate()
            .start()

        val res = handle.future
            .orTimeout(10, TimeUnit.SECONDS)
            .join()

        handle.clear()

        assert(res.size == 187)
        assert(!res[0].bulkDataFiles!![0].exists())
    }

    fun okTestGetLevelImageSync() = withClient(createClient()) { client ->

        val images = client.getSync(GetSCURequest(
            studyInstanceUID = PacsTestFiller.S1_MUR_UID,
            seriesInstanceUID = PacsTestFiller.S12_AXIAL_MUR_UID,
            sopInstanceUID = PacsTestFiller.S121_IMG_MUR_UID,
            level = RecordType.IMAGE
        ))

        for (i in images) {
            if (i.dataset != null)
                DicomUtil.logAttributes(i.dataset!!, logger, "TEST-IMG")
        }

        logger.info("Got ${images.size} images")
        assert(images.size == 1)
        assert(images[0].sopInstanceUID == PacsTestFiller.S121_IMG_MUR_UID)

    }

    fun testGetLevelSeriesCancel() = withClient(createClient()) { client ->

        val size = 10
        var counter = 0
        val latch = CountDownLatch(1)
        val received = mutableListOf<ImageDto>()

        val handle = client.get(GetSCURequest(
                studyInstanceUID = PacsTestFiller.S1_MUR_UID,
                seriesInstanceUID = PacsTestFiller.S12_AXIAL_MUR_UID,
                level = RecordType.SERIES
            ))
            .onEntry {
                counter++
                if (counter > size)
                    latch.countDown()
                received.add(it)
            }
            .start()


        var exception: Throwable? = null
        handle.future.whenComplete { res, exc ->
            exception = exc
        }

        latch.await()

        val cancelled = handle
            .future
            .cancel(true)

        handle.clear()

        assert(cancelled)
        assert(received.size >= size)
        assert(!received[0].bulkDataFiles!![0].exists())
        assert(handle.future.isCancelled)
        assert(exception is CancellationException)
    }

}
