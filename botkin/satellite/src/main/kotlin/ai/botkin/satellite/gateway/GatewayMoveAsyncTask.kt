package ai.botkin.satellite.gateway

import com.fasterxml.jackson.databind.node.ObjectNode
import io.losos.common.AbstractAsyncTask
import io.losos.platform.LososPlatform
import io.opentracing.Tracer
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

    override fun doWork(args: ObjectNode?, settings: GatewayTaskSettings?): Any? {
        if (args == null)
            throw RuntimeException("Arguments shouldn't be null")

        if (settings == null)
            throw RuntimeException("Settings shouldn't be null")

        logger.info("Tracer initialized ${this::tracer.isInitialized}")

        //do download magic
        Thread.sleep(1000)

        return args
    }

}