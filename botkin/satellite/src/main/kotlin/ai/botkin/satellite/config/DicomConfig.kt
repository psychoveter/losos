package ai.botkin.satellite.config

import ai.botkin.oncore.dicom.PacsClient
import ai.botkin.oncore.dicom.dsl.PacsClientDsl
import org.dcm4che3.data.UID
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration


@Configuration
open class DicomConfig(
    @Value("\${satellite.dicom.store-scp.folder-store:/tmp/data/dicom-store}")
    val dicomStoreFolder: String,

    @Value("\${satellite.dicom.store-scp.folder-report:/tmp/data/dicom-report}")
    val dicomReportFolder: String,

    @Value("\${satellite.dicom.store-scp.port:/tmp/data/dicom-report}")
    val localStorePort: Int
) {


    @Bean
    fun pacsClient(): PacsClient = PacsClientDsl {

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
            transferSyntax (
                UID.ExplicitVRLittleEndian,
                UID.ExplicitVRBigEndianRetired,
                UID.ImplicitVRLittleEndian
            )
            isSCU = true
            isSCP = true
        }

        PresentationContext {
            abstractSyntax = UID.VerificationSOPClass
            transferSyntax (
                UID.ImplicitVRLittleEndian
            )
            isSCU = true
            isSCP = true
        }

        PACS {
            aeTitle = "TSTMOCK"
            Connection {
                host = "10.10.101.52"
                port = 11114
            }
        }
//        PACS {
//            aeTitle = "TSTMOCK"
//            Connection {
//                host = "10.9.2.231"
//                port = 30524
//            }
//        }
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

        StoreSCP {
            storeFolder = dicomStoreFolder
            ApplicationEntity {
                aeTitle = "S1"
                Connection {
                    host = "0.0.0.0"
                    port = localStorePort
                }
            }
        }
    }

}