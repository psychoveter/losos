package io.losos.platform

import com.fasterxml.jackson.databind.node.ObjectNode
import io.losos.Framework


interface Event<T> {
    val fullPath: String
    val payload: T

    companion object {
        fun emptyPayload() = Framework.jsonMapper.createObjectNode()
        fun createEmpty(path: String) = EventImpl(path, emptyPayload())
    }
}

data class EventImpl<T>(
        override val fullPath: String,
        override val payload: T
): Event<T>
