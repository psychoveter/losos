package ai.botkin.platform.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import io.losos.process.engine.NodeManager
import io.losos.process.model.ProcessDef
import org.springframework.core.io.ResourceLoader
import org.springframework.stereotype.Component

@Component
open class LososService(
    private val nodeManager: NodeManager,
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
        val process = nodeManager.processManager.createProcess(def, args = args)
        return process.pid
    }

}