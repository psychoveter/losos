package io.losos.platform.etcd


import com.fasterxml.jackson.databind.node.ObjectNode
import io.losos.platform.Event
import io.losos.platform.LososPlatform
import io.losos.platform.EventImpl
import io.losos.platform.Subscription
import io.etcd.jetcd.Client
import io.etcd.jetcd.Watch
import io.etcd.jetcd.options.GetOption
import io.etcd.jetcd.options.WatchOption
import io.etcd.jetcd.watch.WatchEvent
import io.etcd.recipes.common.putValue
import io.etcd.recipes.common.putValueWithKeepAlive
import io.etcd.recipes.common.watcher
import io.losos.Framework
import io.losos.common.AgentDescriptor
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.future.await
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import java.nio.charset.Charset
import java.util.*
import kotlin.collections.HashMap

fun WatchEvent.stringify() =
    "type: ${this.eventType}, " +
            "key: ${keyValue.key.toString(Charset.defaultCharset())}, " +
            "value: ${keyValue.value.toString(Charset.defaultCharset())}"

class EtcdLososPlatform(
    val client: Client
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
                        val key = LososPlatform.bytes2string(e.keyValue.key)
                        if (key.startsWith(LososPlatform.PREFIX_AGENT_LEASE)) {
                            val value = Framework.jsonMapper
                                .createObjectNode()
                                .put("action", e.eventType.name)
                            //TODO smells
                            callback(EventImpl(key, value as T))
                        } else {
                            val value = Framework.jsonMapper().readValue(e.keyValue.value.bytes, clazz)
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
                .withPrefix(LososPlatform.fromString(prefix))
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
                                val key = LososPlatform.bytes2string(e.keyValue.key)
                                callback(EventImpl(key, Event.emptyPayload()))
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

    override fun put(path: String, payload: ObjectNode) {
        logger.debug("Emit event ${path}:${payload}")
        client.putValue(path, LososPlatform.fromJson(payload))
    }

    override fun invoke(actionId: String, actionType: String, params: ObjectNode?) {
        logger.debug("Place invocation action $actionId:$actionType:$params")
        TODO("Not yet implemented")
    }


    override fun put(e: Event<*>) {
        val pl: ObjectNode = when (e.payload) {
            is ObjectNode -> e.payload as ObjectNode
            else -> Framework.object2json(e.payload!!)
        }
        put(e.fullPath, pl)
    }

    override fun delete(path: String) {
        client.kvClient.delete(LososPlatform.fromString(path)).get()
    }

    override suspend fun readOne(path: String): ObjectNode? {
        val getResponse = client.kvClient
            .get(LososPlatform.fromString(path), GetOption.DEFAULT)
            .await()

        if (getResponse.count > 1L)
            throw RuntimeException("Expected only one result")

        return if (getResponse.count == 1L)
            LososPlatform.bytes2json(getResponse.kvs.get(0).value)
        else
            null
    }

    override suspend fun readPrefix(prefix: String): Map<String, ObjectNode> {
        val getResponse = client.kvClient
            .get(
                LososPlatform.fromString(prefix),
                GetOption
                    .newBuilder()
                    .withPrefix(LososPlatform.fromString(prefix))
                    .build()
            )
            .await()

        return getResponse.kvs
            .map { LososPlatform.bytes2string(it.key) to LososPlatform.bytes2json(it.value) }
            .toMap()
    }

    override fun runInKeepAlive(key: String, block: () -> Unit) {
        client.putValueWithKeepAlive(key, 0, 5, block)
    }

    override fun register(agentName: String, descriptor: AgentDescriptor) {
        val path = "${LososPlatform.PREFIX_AGENTS}/$agentName"
        logger.debug("Registering agent $agentName at path $path")
        put(path, descriptor.toJson())
    }

    override fun deregister(agentName: String) {
        client.kvClient.delete(LososPlatform.fromString("${LososPlatform.PREFIX_AGENTS}/agentName"))
    }


    override fun history(prefix: String): List<Event<*>> {
        TODO("Not yet implemented")
    }


}