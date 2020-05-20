package io.losos

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.databind.node.TreeTraversingParser
import com.fasterxml.jackson.module.kotlin.KotlinModule
import kotlin.reflect.KClass

typealias DefID = String
typealias PID = String
typealias EventType = String
typealias JsonObj = ObjectNode

val started = System.currentTimeMillis()

fun timeFromStarted() = System.currentTimeMillis() - started

object Framework {

    object Test {
        val ETCD_URLS = listOf("http://81.29.130.10:2379")
    }

    var loggersEnabled  = mapOf<String, Boolean>()
        private set


    fun init(loggersMap: Map<String, Boolean> = loggersEnabled) { loggersEnabled = loggersMap }

    fun loggerEnabled(name: String): Boolean = loggersEnabled[name] ?: true

    //--json------------------------------------------------------------------------------------------------------------

    val jsonMapper = jsonMapper()

    fun jsonMapper(): ObjectMapper {
        val mapper = ObjectMapper()
        mapper.registerModule(KotlinModule())
        return mapper
    }

    fun <T> json2object(json: JsonObj, clazz: Class<T>): T = Framework
                                                                .jsonMapper
                                                                .readValue(TreeTraversingParser(json), clazz)

    fun object2json(obj: Any): JsonObj = Framework
                                            .jsonMapper
                                            .convertValue(obj, ObjectNode::class.java)

    inline fun <reified T> readJson(path: String): T {
        val url = T::class.java.getResource(path)
        return jsonMapper.readValue(url, T::class.java)
    }
}



//--logging-utilities---------------------------------------------------------------------------------------------------
fun logger(name: String): (String) -> Unit = if (Framework.loggerEnabled(name))
                                                 { it: String -> log("[$name]: $it") }
                                             else
                                                 { it: String -> {} }

fun log(msg: String) {
    println("[${Thread.currentThread().name}:${timeFromStarted()}] $msg")
}



//fun readResourcesGAN(path: String): GANDef = EventBus
//        .jsonMapper
//        .readValue(File("src/test/resources/cases/$path"), GANDef::class.java)