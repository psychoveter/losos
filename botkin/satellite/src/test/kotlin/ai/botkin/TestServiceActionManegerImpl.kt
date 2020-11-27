package ai.botkin

import ai.botkin.satellite.service.ServiceActionManagerImpl
import ai.botkin.satellite.task.SavePaths
import com.fasterxml.jackson.databind.ObjectMapper
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
    ai.botkin.satellite.config.ObjectMapperConfig::class))
@RunWith(SpringRunner::class)
class TestServiceActionManegerImpl {
    @Autowired
    lateinit var platform: LososPlatform
    @Autowired
    lateinit var restTemplate: RestTemplate
    @Autowired
    lateinit var tracer: Tracer


    @Test
    fun testServiceActionManagerCreation(){
        val serviceActionManagerImpl = ServiceActionManagerImpl(restTemplate, platform, tracer)
        assert(serviceActionManagerImpl != null)
    }

    @Test
    fun invokeServiceMlTask(){
        val serviceActionManagerImpl = ServiceActionManagerImpl(restTemplate, platform, tracer)
        val mapper = ObjectMapper()
        val node = mapper.createObjectNode()
            .put("id", "1")
            .put("dicomPath", "/tmp/dicoms/1.2.392.200036.9116.2.5.1.11341.1409398444.1586329368.638766")
            .put("markupPath", "/tmp/markups/1.2.392.200036.9116.2.5.1.11341.1409398444.1586329368.638766.json")
            .put("target", "/FCT")
        val serviceTask:ServiceTask = ServiceTask(ServiceActionManagerImpl.SERVICE_ML, "", args= node)
        serviceActionManagerImpl.invokeService(serviceTask, "/result")
//        assert(serviceActionManagerImpl)
    }

    @Test
    fun invokeServiceReportTask(){
        val serviceActionManagerImpl = ServiceActionManagerImpl(restTemplate, platform, tracer)
        val mapper = ObjectMapper()
        val node = mapper.createObjectNode()
            .put("id", "1")
            .put("dicomPath", "/tmp/dicoms/1.2.392.200036.9116.2.5.1.11341.1409398444.1586329368.638766")
            .put("markupPath", "/tmp/markups/1.2.392.200036.9116.2.5.1.11341.1409398444.1586329368.638766.json")
            .put("target", "target")
            .putPOJO("savePaths",
                SavePaths("/tmp/markups/1.2.392.200036.9116.2.5.1.11341.1409398444.1586329368.638766/sr.dcm",
                            "/tmp/markups/1.2.392.200036.9116.2.5.1.11341.1409398444.1586329368.638766/sr.dcm",
                            "/tmp/markups/1.2.392.200036.9116.2.5.1.11341.1409398444.1586329368.638766/SC"))
        val serviceTask:ServiceTask = ServiceTask(ServiceActionManagerImpl.SERVICE_REPORTER, "", args= node)
        serviceActionManagerImpl.invokeService(serviceTask, "/result")
//        assert(serviceActionManagerImpl)
    }
}