package ai.botkin.oncore.dicom.dsl

import ai.botkin.oncore.dicom.util.DicomUtil
import ai.botkin.oncore.dicom.util.LoggingAssociationMonitor
import ai.botkin.oncore.dicom.PacsClient
import ai.botkin.oncore.dicom.client.PacsClientImpl
import ai.botkin.oncore.dicom.client.PacsClientOnlyStoreSCPImpl
import ai.botkin.oncore.dicom.depers.DeperService
import ai.botkin.oncore.dicom.service.*
import org.dcm4che3.data.UID
import org.dcm4che3.net.SSLManagerFactory
import org.dcm4che3.net.pdu.AAssociateRQ
import org.dcm4che3.net.pdu.RoleSelection
import org.slf4j.LoggerFactory
import java.lang.IllegalStateException

class DicomFactory(private val dsl: PacsClientContextDsl) {

    private val logger = LoggerFactory.getLogger(DicomFactory::class.java)

    fun buildClient(): PacsClient {
        val device = dsl.device.build()
        device.associationMonitor = LoggingAssociationMonitor(device)

        lateinit var clientImpl: PacsClient

        // Configure Store SCP which may be used in both types of client
        var storeScp: StoreSCPService? = null
        if (dsl.storeScp != null) {
            // For StoreSCP another AE is created
            val conn = dsl.storeScp!!.ae.conn.build()
            val ae = dsl.storeScp!!.ae.build(device, conn)

            val service = StoreSCPService(ae, dsl.storeScp!!.storeFolder)
            ae.isAssociationAcceptor = true
            ae.dimseRQHandler = service

            DicomUtil.configureStoreTransferCapability(ae)

            storeScp = service
        }

        if (dsl.ae == null) {

            if (storeScp == null)
                throw IllegalStateException("Neither top level AE nor StoreSCP have been configured")

            clientImpl = PacsClientOnlyStoreSCPImpl(storeScp)

        } else {
            // Configure SCU client
            var findScu: FindSCUService? = null
            var getScu: GetSCUService? = null
            var moveScu: MoveSCUService? = null
            var storeScu: StoreSCUService? = null


            val myConnection = dsl.ae!!.conn.build()
            val applicationEntity = dsl.ae!!.build(device, myConnection)

            if (dsl.findScu != null || dsl.getScu != null || dsl.moveScu != null || dsl.storeScu != null) {
                applicationEntity.isAssociationInitiator = true
            }

            val pacsConnection = dsl.targetPacs!!.conn.build()

            if (dsl.ssl != null) {
                myConnection.setTlsCipherSuites(*dsl.ssl!!.tlsCipherSuites)
                myConnection.setTlsProtocols(*dsl.ssl!!.tlsProtocol)
                pacsConnection.setTlsCipherSuites(*dsl.ssl!!.tlsCipherSuites)
                pacsConnection.setTlsProtocols(*dsl.ssl!!.tlsProtocol)

                device.keyManager = SSLManagerFactory.createKeyManager(
                    dsl.ssl!!.keyType, dsl.ssl!!.keyStoreFile, dsl.ssl!!.keyStorePassword, dsl.ssl!!.keyStoreSecret)
                device.trustManager = SSLManagerFactory.createTrustManager(
                    dsl.ssl!!.trustType, dsl.ssl!!.trustStoreFile, dsl.ssl!!.trustStorePassword)
            }

            val depers: DeperService? = if (dsl.depers != null) {
                DeperService(dsl.depers!!.getTags())
            } else null


            if (dsl.findScu != null) {
                findScu = FindSCUService()
            }

            if (dsl.getScu != null) {
                val syntaxes = dsl.getPresentationContexts().map { it.abstractSyntax }

                getScu = GetSCUService(syntaxes)
                applicationEntity.dimseRQHandler = getScu
            }

            if (dsl.moveScu != null) {
                moveScu = dsl.moveScu!!.build()
            }

            if (dsl.storeScu != null) {
                storeScu = StoreSCUService()
            }

            clientImpl = PacsClientImpl(
                device = device,
                applicationEntity = applicationEntity,
                myConnection = myConnection,
                pacsConnection = pacsConnection,
                dicomFactory = this,
                getSCUService = getScu,
                moveSCUService =  moveScu,
                findSCUService = findScu,
                storeSCUService = storeScu,
                storeSCPService = storeScp,
                depers = depers
            )
        }

        return clientImpl
    }


    fun buildAssociationRequest(): AAssociateRQ {
        if (dsl.ae == null)
            throw IllegalStateException("Cannot construct AAssociateRQ: ApplicationEntity has not been configured")
        if (dsl.targetPacs == null)
            throw IllegalStateException("Cannot construct AAssociateRQ: Target PACS has not been configured")

        logger.info("Building new AAssociationRQ")

        val rq = AAssociateRQ()
        rq.calledAET = dsl.targetPacs!!.aeTitle

        if (dsl.findScu != null) {
            rq.addRoleSelection(RoleSelection(UID.StudyRootQueryRetrieveInformationModelFIND, true, false))
            DicomUtil.addPresentationContext(
                rq,
                UID.StudyRootQueryRetrieveInformationModelFIND,
                UID.ExplicitVRLittleEndian,
                UID.ExplicitVRBigEndianRetired,
                UID.ImplicitVRLittleEndian
            )
        }

        if (dsl.getScu != null) {
            rq.addRoleSelection(RoleSelection(UID.StudyRootQueryRetrieveInformationModelGET, true, false))
            DicomUtil.addPresentationContext(
                rq,
                UID.StudyRootQueryRetrieveInformationModelGET,
                UID.ExplicitVRLittleEndian,
                UID.ExplicitVRBigEndianRetired,
                UID.ImplicitVRLittleEndian
            )
        }

        if (dsl.moveScu != null) {
            rq.addRoleSelection(RoleSelection(UID.StudyRootQueryRetrieveInformationModelMOVE, true, true))
            DicomUtil.addPresentationContext(
                rq,
                UID.StudyRootQueryRetrieveInformationModelMOVE,
                UID.ImplicitVRLittleEndian,
                UID.ExplicitVRLittleEndian,
                UID.ExplicitVRBigEndianRetired
            )
        }

        if (dsl.storeScu != null) {
            logger.warn("Store SCU is not configured")
        }

        for (pc in dsl.getPresentationContexts()) {
            DicomUtil.addPresentationContext(rq, pc.abstractSyntax, *pc._transferSyntax.toTypedArray())
            rq.addRoleSelection(RoleSelection(pc.abstractSyntax, pc.isSCU, pc.isSCP))
        }

        return rq
    }


}