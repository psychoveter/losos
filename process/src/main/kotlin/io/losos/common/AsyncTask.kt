package io.losos.common


import com.fasterxml.jackson.databind.node.ObjectNode
import io.losos.platform.LososPlatform
import org.slf4j.LoggerFactory

interface AsyncTask {

    fun execute(args: ObjectNode?, settings: ObjectNode?): Any?

}

abstract class AbstractAsyncTask<Args, Settings>(
    val platform: LososPlatform,
    val argsType: Class<Args>,
    val settingsType: Class<Settings>
): AsyncTask {

    override fun execute(argsJson: ObjectNode?, settingsJson: ObjectNode?): Any? {
        val args = if (argsJson != null) platform.json2object(argsJson, argsType) else null
        val settings = if (settingsJson != null) platform.json2object(settingsJson, settingsType) else null
        return doWork(args, settings)
    }

    abstract fun doWork(args: Args?, settings: Settings?): Any?

}


data class DummySettings(
    val s1: String,
    val i1: Int
)

class DummyAsyncTask(platform: LososPlatform): AbstractAsyncTask<ObjectNode, DummySettings> (
    platform,
    ObjectNode::class.java,
    DummySettings::class.java
) {
    private val logger = LoggerFactory.getLogger(DummyAsyncTask::class.java)

    @Volatile
    var counter = 0
    override fun doWork(args: ObjectNode?, settings: DummySettings?): Any? {
        if (args != null)
            logger.info("Args received: $args")
        if (settings != null)
            logger.info("Settings received: $settings")

        if (counter == 0) {
            counter++
            throw RuntimeException("Fail once...")
        }

        return args
    }
}