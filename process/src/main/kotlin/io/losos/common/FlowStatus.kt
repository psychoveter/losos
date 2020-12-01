package io.losos.common

import com.fasterxml.jackson.databind.node.ObjectNode

enum class FlowStatus {
    OK, FAILED
}

data class InvocationResult (
    val data: ObjectNode? = null,
    val status: FlowStatus = FlowStatus.OK
) {
    companion object {
        fun fail(data: ObjectNode?) = InvocationResult(
            data, FlowStatus.FAILED
        )
    }
}