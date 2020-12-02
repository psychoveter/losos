import ai.botkin.oncore.test.PacsClientTest
import ai.botkin.oncore.dicom.dsl.PacsClientDsl
import org.dcm4che3.data.UID
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

@TestInstance(TestInstance.Lifecycle.PER_METHOD)
class Dcm4ChePacsClientSTORESCP: PacsClientTest {

    fun createSCU() = PacsClientDsl {

        PresentationContext {
            abstractSyntax = UID.VerificationSOPClass
            transferSyntax(
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
            isSCP = false
        }

        PACS {
            aeTitle = "TSTMOCK"
            Connection {
                host = "10.9.2.231"
                port = 30524
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

        StoreSCU {

        }
    }

    fun createSCP() = PacsClientDsl {

        PresentationContext {
            abstractSyntax = UID.CTImageStorage
            transferSyntax(
                UID.ExplicitVRLittleEndian,
                UID.ExplicitVRBigEndianRetired,
                UID.ImplicitVRLittleEndian
            )
            isSCU = false
            isSCP = true
        }

        Device {
            name = "olegasus"
            threads = 3
        }


        StoreSCP {
            /**
             * StoreSCP creates it's own AE and accepts RQ on it
             */
            ApplicationEntity {
                aeTitle = "OASTORE"
                Connection {
                    host = "0.0.0.0"
                    port = 6000
                }
            }
            storeFolder = "/tmp/dicoms"
        }
    }


    fun testSCP() = withClient(createSCP()) { client ->
        Thread.sleep(1000 * 60 * 10)
    }
}

fun main(args: Array<String>) {
    val test = Dcm4ChePacsClientSTORESCP()
    test.testSCP()
}
