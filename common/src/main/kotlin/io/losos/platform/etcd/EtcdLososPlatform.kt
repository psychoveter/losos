package io.losos.platform.etcd


import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import io.losos.platform.Event
import io.losos.platform.LososPlatform
import io.losos.platform.EventImpl
import io.losos.platform.Subscription
import io.etcd.jetcd.Client
import io.etcd.jetcd.Watch
import io.etcd.jetcd.kv.GetResponse
import io.etcd.jetcd.options.GetOption
import io.etcd.jetcd.options.WatchOption
import io.etcd.jetcd.watch.WatchEvent
import io.etcd.recipes.common.putValue
import io.etcd.recipes.common.putValueWithKeepAlive
import io.etcd.recipes.common.watcher
import io.losos.common.AgentDescriptor
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.future.await
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import java.lang.IllegalStateException
import java.nio.charset.Charset
import java.util.*
import kotlin.collections.HashMap

fun WatchEvent.stringify() =
    "type: ${this.eventType}, " +
            "key: ${keyValue.key.toString(Charset.defaultCharset())}, " +
            "value: ${keyValue.value.toString(Charset.defaultCharset())}"

class EtcdLososPlatform(
    val client: Client,
    override val jsonMapper: ObjectMapper
) : LososPlatform {

    private val logger = LoggerFactory.getLogger(this::class.java)

    class EtcdSubscription<T>(
        override val id: String,
        override val prefix: String,
        override val callback: suspend (Event<T>) -> Unit,
        val watcher: Watch.Watcher,
        val clazz: Class<T>? = null
    ) : Subscription<T> {
        override fun cancel() = watcher.close()
    }

    private val subscriptions: MutableMap<String, EtcdSubscription<*>> = HashMap()

    override fun subscribe(
        prefix: String,
        callback: suspend (e: Event<ObjectNode>) -> Unit
    ): Subscription<ObjectNode> = subscribe(prefix, ObjectNode::class.java, callback)

    override fun <T> subscribe(
        prefix: String,
        clazz: Class<T>,
        callback: suspend (e: Event<T>) -> Unit
    ): Subscription<T> {

        val action: (e: WatchEvent) -> Unit = { e ->
            logger.debug("[${Thread.currentThread().name}] received event: ${e.stringify()}")
            if (e.eventType == WatchEvent.EventType.PUT) {
                GlobalScope.launch {
                    try {
                        val key = bytes2string(e.keyValue.key)
                        if (key.startsWith(LososPlatform.PREFIX_AGENT_LEASE)) {
                            val value = jsonMapper
                                .createObjectNode()
                                .put("action", e.eventType.name)
                            //TODO smells
                            callback(EventImpl(key, value as T))
                        } else {
                            val value = jsonMapper.readValue(e.keyValue.value.bytes, clazz)
                            callback(EventImpl(key, value))
                        }
                    } catch (exc: Exception) {
                        logger.error("Failed to parse event: e[${e.stringify()}]", e)
                    }
                }
            }

        }

        val watcher = client.watcher(
            keyName = prefix,
            option = WatchOption
                .newBuilder()
                .withPrefix(fromString(prefix))
                .withNoDelete(true)
                .build(),
            block = { response ->
                response.events.forEach {
                    action(it)
                }
            }
        )

        val subs = EtcdSubscription(
            UUID.randomUUID().toString(),
            prefix,
            callback,
            watcher,
            clazz
        )

        subscriptions[subs.id] = subs

        return subs
    }


    override fun subscribeDelete(
        path: String,
        callback: suspend (e: Event<ObjectNode>) -> Unit
    ): Subscription<ObjectNode> {
        val watcher = client.watcher(
            keyName = path,
            block = { response ->
                response.events.forEach { e ->
                    logger.debug("[${Thread.currentThread().name}] received event: ${e.stringify()}")
                    when (e.eventType) {
                        WatchEvent.EventType.DELETE -> GlobalScope.launch {
                            try {
                                val key = bytes2string(e.keyValue.key)
                                callback(EventImpl(key, emptyObject()))
                            } catch (exc: Exception) {
                                logger.error("Failed to parse event: e[${e.stringify()}]", exc)
                            }
                        }
                        else -> {
                        }
                    }
                }
            }
        )
        val subs = EtcdSubscription(
            UUID.randomUUID().toString(),
            path,
            callback,
            watcher,
            ObjectNode::class.java
        )
        subscriptions[subs.id] = subs

        return subs
    }

    override fun put(path: String, payload: Any) {
        logger.debug("Emit event ${path}:${payload}")
        val bytes = when (payload) {
            is ObjectNode -> fromJson(payload)
            else -> fromJson(object2json(payload))
        }
        client.putValue(path, bytes)
    }

    override fun invoke(actionId: String, actionType: String, params: ObjectNode?) {
        logger.debug("Place invocation action $actionId:$actionType:$params")
        TODO("Not yet implemented")
    }


    override fun put(e: Event<*>) {
        val pl: ObjectNode = when (e.payload) {
            is ObjectNode -> e.payload as ObjectNode
            else -> object2json(e.payload!!)
        }
        put(e.fullPath, pl)
    }

    override fun delete(path: String) {
        client.kvClient.delete(fromString(path)).get()
    }

    override fun <T> getOne(path: String, clazz: Class<T>): T? {
        val getResponse = client.kvClient
            .get(fromString(path), GetOption.DEFAULT)
            .get()

        if (getResponse.count > 1L)
            throw RuntimeException("Expected only one result")

        return if (getResponse.count == 1L)
            bytes2object(getResponse.kvs.get(0).value, clazz)
        else
            null
    }

    override fun <T> getPrefix(prefix: String, clazz: Class<T>): Map<String, T> {
        val getResponse = client.kvClient
            .get(
                fromString(prefix),
                GetOption
                    .newBuilder()
                    .withPrefix(fromString(prefix))
                    .build()
            )
            .get()

        return getResponse.kvs
            .map { bytes2string(it.key) to bytes2object(it.value, clazz) }
            .toMap()
    }

    override fun runInKeepAlive(key: String, block: () -> Unit) {
        client.putValueWithKeepAlive(key, 0, 5, block)
    }

    override fun register(agentName: String, descriptor: AgentDescriptor) {
        val path = "${LososPlatform.PREFIX_AGENTS}/$agentName"
        logger.debug("Registering agent $agentName at path $path")
        val json = object2json(descriptor)
        put(path, json)
    }

    override fun deregister(agentName: String) {
        client.kvClient.delete(fromString("${LososPlatform.PREFIX_AGENTS}/agentName"))
    }


    override fun history(prefix: String): List<Event<*>> {
        TODO("Not yet implemented")
    }


}