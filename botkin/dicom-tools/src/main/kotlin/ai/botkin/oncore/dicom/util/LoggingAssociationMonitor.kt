package ai.botkin.oncore.dicom.util

import org.dcm4che3.net.Association
import org.dcm4che3.net.AssociationMonitor
import org.dcm4che3.net.Device
import org.dcm4che3.net.pdu.AAssociateRJ
import org.slf4j.LoggerFactory

class LoggingAssociationMonitor(val device: Device) : AssociationMonitor {
    private val logger = LoggerFactory.getLogger(this::class.java)

    override fun onAssociationEstablished(association: Association) {
        logger.info("Association $association for device ${device.deviceName} established with ac = ${association.aAssociateAC}, rq = ${association.aAssociateRQ}")
    }

    override fun onAssociationRejected(association: Association, aarj: AAssociateRJ) {
        logger.error("Association $association for device ${device.deviceName}  rejected with ac = ${association.aAssociateAC}, rq = ${association.aAssociateRQ}, aarj = $aarj")
    }

    override fun onAssociationAccepted(association: Association) {
        val accepted = association.aAssociateAC
        val requested = association.aAssociateRQ
        logger.info("Association $association for device ${device.deviceName}  accepted with ac = $accepted, rq = $requested")
        var somethingWrong = false
        accepted.presentationContexts.forEachIndexed { i, pc ->
            if (!pc.isAccepted) {
                val requestedContext = requested.presentationContexts[i]
                somethingWrong = true
                logger.error("Not accepted pc $pc for $requestedContext")
            }
        }
        if (somethingWrong) {
            requested.presentationContexts.forEachIndexed { i, p ->
                logger.error("Requested pc $i is $p")
            }
        }
    }

    override fun onAssociationFailed(association: Association, e: Throwable) {
        logger.error("Association $association for device ${device.deviceName}  rejected with ac = ${association.aAssociateAC}, rq = ${association.aAssociateRQ}, error = $e")
    }

}
