package io.losos.common

import com.fasterxml.jackson.databind.node.ObjectNode


data class AgentTask (
        val id: String,
        val type: String,
        val successEventPath: String,
        val retryEventPath: String,
        val failureEventPath: String,
        val payload: ObjectNode
)