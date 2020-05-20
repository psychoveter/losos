package io.losos.etcd

import io.losos.JsonObj
import io.losos.eventbus.Event
import io.losos.eventbus.EventBus
import io.losos.eventbus.EventImpl
import io.losos.eventbus.Subscription
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
import kotlinx.coroutines.future.await
import kotlinx.coroutines.launch
import java.nio.charset.Charset
import java.util.*
import kotlin.collections.HashMap

fun WatchEvent.stringify() =
        "type: ${this.eventType}, " +
        "key: ${keyValue.key.toString(Charset.defaultCharset())}, " +
        "value: ${keyValue.value.toString(Charset.defaultCharset())}"

class EtcdEventBus(
        val client: Client
): EventBus {

    val log = io.losos.logger("etcdbus")

    class EtcdSubscription(
        override val id: String,
        override val prefix: String,
        override val callback: suspend (Event) -> Unit,
        val watcher: Watch.Watcher
    ): Subscription {
        override fun cancel() = watcher.close()
    }

    private val subscriptions: MutableMap<String, EtcdSubscription> = HashMap()

    override fun subscribe(prefix: String, callback: suspend (e: Event) -> Unit): Subscription {
        val watcher = client.watcher(
                keyName = prefix,
                option = WatchOption
                           .newBuilder()
                           .withPrefix(EventBus.fromString(prefix))
                        .build(),
                block = { response ->
                    response.events.forEach { e -> GlobalScope.launch {
                            log("[${Thread.currentThread().name}] received event: ${e.stringify()}")
                            try {
                                val key = EventBus.bytes2string(e.keyValue.key)
                                if ( key.startsWith(EventBus.PREFIX_AGENT_LEASE) ) {
                                    val value = Framework.jsonMapper
                                            .createObjectNode()
                                            .put("action", e.eventType.name)
                                    callback(EventImpl(key, value))
                                } else {
                                    val value = EventBus.bytes2json(e.keyValue.value)
                                    callback(EventImpl(key, value))
                                }
                            } catch (exc: Exception) {
                                log("Failed to parse event: e[${e.stringify()}], exc: ${exc.toString()}")
                                exc.printStackTrace()
                            }
                        }
                    }
                }
        )
        val subs = EtcdSubscription(UUID.randomUUID().toString(), prefix, callback, watcher)
        subscriptions[subs.id] = subs

        return subs
    }

    override fun subscribeDelete(path: String,  callback: suspend (e: Event) -> Unit): Subscription {
        val watcher = client.watcher(
                keyName = path,
                block = { response ->
                    response.events.forEach { e ->
                        //log("[${Thread.currentThread().name}] received event: ${e.stringify()}")
                        when (e.eventType) {
                            WatchEvent.EventType.DELETE-> GlobalScope.launch {
                                try {
                                    val key   = EventBus.bytes2string(e.keyValue.key)
                                    callback(EventImpl(key, Event.emptyPayload()))
                                } catch (exc: Exception) {
                                    log("Failed to parse event: e[${e.stringify()}], exc: ${exc.toString()}")
                                }
                            }
                            else -> {}
                        }
                    }
                }
        )
        val subs = EtcdSubscription(UUID.randomUUID().toString(), path, callback, watcher)
        subscriptions[subs.id] = subs

        return subs
    }

    override fun emit(path: String, payload: JsonObj) {
        log("Emit event ${path}:${payload}")
        client.putValue(path, EventBus.fromJson(payload))
    }

    override fun emit(e: Event) {
        emit(e.fullPath, e.payload)
    }

    override suspend fun readOne(path: String): JsonObj? {
        val getResponse = client.kvClient
                .get(EventBus.fromString(path), GetOption.DEFAULT)
                .await()

        if (getResponse.count > 1L)
            throw RuntimeException("Expected only one result")

        return if (getResponse.count == 1L)
            EventBus.bytes2json(getResponse.kvs.get(0).value)
        else
            null
    }

    override suspend fun readPrefix(prefix: String): Map<String, JsonObj> {
        val getResponse = client.kvClient
                .get(EventBus.fromString(prefix),
                     GetOption
                             .newBuilder()
                             .withPrefix(EventBus.fromString(prefix))
                             .build()
                )
                .await()

        return getResponse.kvs
                .map { EventBus.bytes2string(it.key) to EventBus.bytes2json(it.value) }
                .toMap()
    }

    override fun runInKeepAlive(key: String, block: () -> Unit) {
        client.putValueWithKeepAlive(key, 0 , 5, block)
    }

    override fun register(agentName: String, descriptor: AgentDescriptor) {
        val path = "${EventBus.PREFIX_AGENTS}/$agentName"
        log("Registering agent $agentName at path $path")
        emit(path, descriptor.toJson())
    }

    override fun deregister(agentName: String) {
        client.kvClient.delete(EventBus.fromString("${EventBus.PREFIX_AGENTS}/agentName"))
    }


    override fun history(prefix: String): List<Event> {
        TODO("Not yet implemented")
    }

}