package io.losos.common

data class NodeInfo (
    val name: String,
    val host: String,
    val processDefNames: Collection<String> = listOf()
)