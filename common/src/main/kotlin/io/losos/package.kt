package io.losos

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.databind.node.TreeTraversingParser
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import kotlin.reflect.KClass

typealias DefID = String
typealias PID = String
typealias EventType = String

val started = System.currentTimeMillis()

fun timeFromStarted() = System.currentTimeMillis() - started

object TestUtils {

    object Test {
        val ETCD_URLS = listOf("http://81.29.130.10:2379")
    }

    var loggersEnabled  = mapOf<String, Boolean>()
        private set


    val jsonMapper = jsonMapper()

    fun jsonMapper(): ObjectMapper = jacksonObjectMapper()
}