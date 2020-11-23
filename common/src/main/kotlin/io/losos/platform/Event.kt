package io.losos.platform


interface Event<T> {
    val fullPath: String
    val payload: T?
}

data class EventImpl<T>(
        override val fullPath: String,
        override val payload: T?
): Event<T>
