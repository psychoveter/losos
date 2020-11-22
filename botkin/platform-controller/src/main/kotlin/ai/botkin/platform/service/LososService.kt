package ai.botkin.platform.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import io.losos.platform.LososPlatform
import io.losos.process.engine.ProcessManager
import io.losos.process.model.ProcessDef
import org.springframework.core.io.ResourceLoader
import org.springframework.stereotype.Component

@Component
open class LososService(
    private val platform: LososPlatform,
    private val processManager: ProcessManager,
    private val resourceLoader: ResourceLoader,
    private val objectMapper: ObjectMapper
) {

    fun loadDefaultProcessDef(): ProcessDef {
        val resource = resourceLoader.getResource("classpath:defs/dzm-root-process.json")
        val def =  objectMapper.readValue(resource.inputStream, ProcessDef::class.java)
        return def
    }


    fun runProcess(args: ObjectNode): String {
        //hard code delegating root process
        val def = loadDefaultProcessDef()
        val process = processManager.createProcess(def)
        process.run()

        //fire
        platform.put("${process.startGuard.path()}/start", args)

        return process.pid
    }

}