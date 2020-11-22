package io.losos.actor

import com.fasterxml.jackson.databind.node.ObjectNode
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import org.slf4j.LoggerFactory
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit


abstract class Actor<T> {

    private val logger = LoggerFactory.getLogger(this::class.java)
    private val mailbox = Channel<T>(Channel.UNLIMITED)
    private var job: Job? = null
    /**
     * Launches coroutine which spinlocks over messages in th mailbox.
     * Messages are processed in actor-like manner.
     */
    fun run(): Job {
        this.job = GlobalScope.launch {
            beforeStart()
            try {
                while (isRunning()) {
                    val message = mailbox.receive()
                    process(message)
                }
            } finally {
                afterStop()
            }
        }
        return job!!
    }


    suspend fun joinCoroutine(timeout: Long = Long.MAX_VALUE) = withTimeout(timeout) {
        job?.join()
    }

    fun joinThread(timeout: Long = Long.MAX_VALUE) {
        val latch = CountDownLatch(1)
        GlobalScope.launch {
            joinCoroutine(timeout)
            latch.countDown()
        }
        latch.await(timeout, TimeUnit.MILLISECONDS)
    }

    open suspend fun beforeStart() {}
    open suspend fun afterStop() {}
    abstract suspend fun process(message: T)

    suspend fun send(msg: T) = mailbox.send(msg)

    fun isRunning() = !mailbox.isClosedForSend

    fun isShuttingDown() = mailbox.isClosedForSend && !mailbox.isClosedForReceive

    fun isDown() = mailbox.isClosedForReceive && mailbox.isClosedForSend

    fun close() { mailbox.close() }

}