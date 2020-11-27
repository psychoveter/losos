package ai.botkin

import ai.botkin.satellite.task.DownloadTask
import ai.botkin.satellite.task.MLTask
import ai.botkin.satellite.task.ReportTask
import ai.botkin.satellite.task.SavePaths
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.junit.Test





class TestObjectNodeToTaskConversion {
    @Test
    fun deserializeMlTask(){
        val mapper = jacksonObjectMapper()
        val node = mapper.createObjectNode()
            .put("id", "1")
            .put("dicomPath", "dicomPath")
            .put("markupPath", "markupPath")
            .put("target", "target")

        val mlTask = mapper.readValue(node.traverse(), MLTask::class.java)
       assert(mlTask.dicomPath == "dicomPath")
       assert(mlTask.markupPath == "markupPath")
       assert(mlTask.target == "target")
    }

    @Test
    fun deserializeReportTask(){
        val mapper = jacksonObjectMapper()
        val node = mapper.createObjectNode()
            .put("id", "1")
            .put("dicomPath", "dicomPath")
            .put("markupPath", "markupPath")
            .put("target", "target")
            .putPOJO("savePaths", SavePaths("sr", "pr", "sc"))

        val reportTask = mapper.readValue(node.traverse(), ReportTask::class.java)
        assert(reportTask.id == "1")
        assert(reportTask.dicomPath == "dicomPath")
        assert(reportTask.markupPath == "markupPath")
        assert(reportTask.target == "target")
        assert(reportTask.savePaths.srPath == "sr")
        assert(reportTask.savePaths.prPath == "pr")
        assert(reportTask.savePaths.scPath == "sc")
    }

    @Test
    fun deserializeDownloadTask(){
        val mapper = jacksonObjectMapper()
        val node = mapper.createObjectNode()
            .put("id", "1")
            .put("studyUID", "123.456")
            .put("downloadPath", "downloadPath")


        val downloadTask = mapper.readValue(node.traverse(), DownloadTask::class.java)
        assert(downloadTask.id == "1")
        assert(downloadTask.studyUID == "123.456")
        assert(downloadTask.downloadPath == "downloadPath")
    }

}

