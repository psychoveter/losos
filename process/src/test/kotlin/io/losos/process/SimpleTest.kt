package io.losos.process

class SimpleTest {
}

fun main(args: Array<String>) {
    val str = "/proc/path/the/one"

    val tokens = str.split("/")
    println(tokens)
}