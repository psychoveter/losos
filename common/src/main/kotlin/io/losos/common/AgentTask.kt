package io.losos.common

import io.losos.JsonObj

data class AgentTask (
        val id: String,
        val type: String,
        val successEventPath: String,
        val retryEventPath: String,
        val failureEventPath: String,
        val payload: JsonObj
)