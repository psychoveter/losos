package ai.botkin.satellite.gateway

import ai.botkin.oncore.dicom.PacsClient
import ai.botkin.oncore.dicom.service.dto.MoveSCURequest
import com.fasterxml.jackson.databind.node.ObjectNode
import io.losos.common.AbstractAsyncTask
import io.losos.platform.LososPlatform
import io.opentracing.Tracer
import org.dcm4che3.media.RecordType
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import java.lang.RuntimeException


data class GatewayTaskSettings (
    val targetAETitle: String
)

class GatewayMoveAsyncTask(platform: LososPlatform): AbstractAsyncTask<ObjectNode, GatewayTaskSettings>(
    argsType = ObjectNode::class.java,
    settingsType = GatewayTaskSettings::class.java,
    platform = platform
) {

    private val logger = LoggerFactory.getLogger(GatewayMoveAsyncTask::class.java)

    @Autowired
    private lateinit var tracer: Tracer

    @Autowired
    private lateinit var pacsClient: PacsClient

    override fun doWork(args: ObjectNode?, settings: GatewayTaskSettings?): Any? {
        if (args == null)
            throw RuntimeException("Arguments shouldn't be null")

        if (settings == null)
            throw RuntimeException("Settings shouldn't be null")

        logger.info("Tracer initialized ${this::tracer.isInitialized}")

        val studyUid = args.get("studyUid").textValue()

        return args
    }

    private fun moveStudy(
        studyUid: String,
        destination: String
    ) {
        val moveRequest = MoveSCURequest(
            destination = destination,
            studyInstanceUID = studyUid,
            level = RecordType.STUDY
        )

        //call blocks until processing is done
        pacsClient.move(moveRequest)

        //at this point we have to get list of files
        //how to get list of files?
    }

}