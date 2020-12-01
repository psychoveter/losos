package ai.botkin

import ai.botkin.satellite.service.ServiceActionManagerImpl
import ai.botkin.satellite.task.*
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import io.losos.platform.LososPlatform
import io.losos.process.planner.ServiceTask
import io.opentracing.Tracer
import org.junit.Test
import org.junit.runner.RunWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.junit4.SpringRunner
import org.springframework.web.client.RestTemplate

@SpringBootTest(classes = arrayOf(ai.botkin.satellite.config.LososConfig::class,
    ai.botkin.satellite.config.ObjectMapperConfig::class), webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@RunWith(SpringRunner::class)
class TestServiceActionManegerImpl {
    @Autowired
    lateinit var platform: LososPlatform

    @Autowired
    lateinit var restTemplate: RestTemplate

    @Autowired
    lateinit var tracer: Tracer

    val mapper = ObjectMapper()

    @Test
    fun testServiceActionManagerCreation() {
        val serviceActionManagerImpl = ServiceActionManagerImpl(restTemplate, platform, tracer)
        assert(serviceActionManagerImpl != null)
    }

    @Test
    fun testInvokeServiceMlTask() {
        val serviceActionManagerImpl = ServiceActionManagerImpl(restTemplate, platform, tracer)
        val node = getObjectNode("ml")
        val serviceTask: ServiceTask = ServiceTask(ServiceActionManagerImpl.SERVICE_ML, "", args = node)
        serviceActionManagerImpl.invokeService(serviceTask, "/result")
    }

    @Test
    fun testInvokeServiceReportTask() {
        val serviceActionManagerImpl = ServiceActionManagerImpl(restTemplate, platform, tracer)

        val node = getObjectNode("report")
        val serviceTask: ServiceTask = ServiceTask(ServiceActionManagerImpl.SERVICE_REPORTER, "", args = node)
        serviceActionManagerImpl.invokeService(serviceTask, "/result")
    }

    @Test
    fun testAssembleRequestForMlService() {
        val serviceActionManagerImpl = ServiceActionManagerImpl(restTemplate, platform, tracer)
        val mlRequest: Request<MLTask> = serviceActionManagerImpl.assembleRequestML(getObjectNode("ml"))
        val mlTask = mlRequest.tasks[0]
        assert(mlTask.id == "1")
        assert(mlTask.target == "FCT")

    }

    @Test
    fun testAssembleRequestForReportService() {
        val serviceActionManagerImpl = ServiceActionManagerImpl(restTemplate, platform, tracer)
        val reportRequest: Request<ReportTask> =
            serviceActionManagerImpl.assembleRequestReport(getObjectNode("report"))
        val reportTask = reportRequest.tasks[0]
        assert(reportTask.id == "1")
        assert(reportTask.target == "FCT")
    }

    @Test
    fun testPostTaskOnMLService(){
        val serviceActionManagerImpl = ServiceActionManagerImpl(restTemplate, platform, tracer)
        serviceActionManagerImpl.start()
    }


    fun getObjectNode(workerType: String): ObjectNode {
        when (workerType) {
            "ml" -> return mapper.createObjectNode()
                .put("id", "1")
                .put("dicomPath", "/tmp/dicoms/1.2.392.200036.9116.2.5.1.11341.1409398444.1586329368.638766")
                .put("markupPath", "/tmp/markups/1.2.392.200036.9116.2.5.1.11341.1409398444.1586329368.638766.json")
                .put("target", "FCT")

            "report" -> return mapper.createObjectNode()
                    .put("id", "1")
                    .put("dicomPath", "/tmp/dicoms/1.2.392.200036.9116.2.5.1.11341.1409398444.1586329368.638766")
                    .put("markupPath", "/tmp/markups/1.2.392.200036.9116.2.5.1.11341.1409398444.1586329368.638766.json")
                    .put("target", "FCT")
                    .putPOJO(
                        "savePaths",
                        SavePaths(
                            "/tmp/markups/1.2.392.200036.9116.2.5.1.11341.1409398444.1586329368.638766/sr.dcm",
                            "/tmp/markups/1.2.392.200036.9116.2.5.1.11341.1409398444.1586329368.638766/sr.dcm",
                            "/tmp/markups/1.2.392.200036.9116.2.5.1.11341.1409398444.1586329368.638766/SC"
                        )
                    )

        }
        throw RuntimeException("Unsupported worker type: $workerType")
    }
}