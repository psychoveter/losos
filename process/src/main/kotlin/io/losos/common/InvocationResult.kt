package io.losos.common

import com.fasterxml.jackson.databind.node.ObjectNode

data class InvocationResult (
    val exitCode: InvocationExitCode,
    val data: ObjectNode? = null
)