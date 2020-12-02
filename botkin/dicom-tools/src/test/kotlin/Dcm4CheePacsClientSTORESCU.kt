import ai.botkin.oncore.test.PacsClientTest
import ai.botkin.oncore.dicom.dsl.PacsClientDsl
import ai.botkin.oncore.dicom.service.dto.StoreSCURequest
import org.dcm4che3.data.UID
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.io.File
import java.io.FileInputStream

@TestInstance(TestInstance.Lifecycle.PER_METHOD)
class Dcm4CheePacsClientSTORESCU: PacsClientTest {

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
            aeTitle = "TSTMOCK"
            Connection {
                host = "10.9.2.231"
                port = 30524
            }
        }
//        PACS {
//            aeTitle = "DCM4CHEE"
//            Connection {
//                host = "localhost"
//                port = 11112
//            }
//        }

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
        FindSCU {}

        StoreSCU {}
    }

    fun storeCTSeries() = withClient(createClient()) { client ->

        val url = this.javaClass.getResource("TestSeria20")
        val dir = File(url.toURI())
        for(file in dir.listFiles()) {
            val bytes = FileInputStream(file).readAllBytes()
            println("Storing file")
            client.storeSync(StoreSCURequest(
                data = bytes
            ))
        }

    }

    fun storeDicomSRFiles() {

    }

    fun storeDicomSCFiles() {

    }

}

fun main(args: Array<String>) {
    val test = Dcm4CheePacsClientSTORESCU()
    test.storeCTSeries()
}