package io.losos

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper

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