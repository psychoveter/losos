package io.losos.eventbus

import io.losos.Framework
import io.losos.JsonObj

interface Event {
    val fullPath: String
    val payload: JsonObj

    companion object {
        fun emptyPayload() = Framework.jsonMapper.createObjectNode()
        fun createEmpty(path: String) = EventImpl(path, emptyPayload())
    }
}

data class EventImpl(
        override val fullPath: String,
        override val payload: JsonObj
): Event
