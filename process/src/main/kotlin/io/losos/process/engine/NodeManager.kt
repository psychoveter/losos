package io.losos.process.engine

import io.losos.platform.LososPlatform
import io.losos.process.engine.action.AsyncActionManager
import io.losos.process.engine.action.ServiceActionManager
import java.util.*


interface IDGenerator {
    fun newUniquePID(): String
}

object IDGenUUID: IDGenerator {
    override fun newUniquePID(): String = UUID.randomUUID().toString()
}


class NodeManager(
    val platform: LososPlatform,
    val host: String = "localhost",
    val idGen: IDGenerator = IDGenUUID
) {
    val processManager = ProcessManager(this)
    val asyncActionManager = AsyncActionManager(this)
    val serviceActionManager = ServiceActionManager(this)


    fun start() {
        processManager.startBrokering()
    }
}