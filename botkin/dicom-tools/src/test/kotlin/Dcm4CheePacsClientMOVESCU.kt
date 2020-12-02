import ai.botkin.oncore.test.PacsClientTest
import ai.botkin.oncore.dicom.dsl.PacsClientDsl
import org.dcm4che3.data.UID
import org.junit.jupiter.api.TestInstance

@TestInstance(TestInstance.Lifecycle.PER_METHOD)
class Dcm4CheePacsClientMOVESCU: PacsClientTest {

    fun createMoveSingleNodeClient() = PacsClientDsl {

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

        ApplicationEntity {
            aeTitle = "OA"
            Connection {
                host = "0.0.0.0"
                port = 5999
            }
        }

        MoveSCU {

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
        }
    }

    fun createMoveCoordinatorClient() {

    }

    fun createStoreSCPMinion() {

    }



}