package rsexperiments

import java.lang.RuntimeException
import java.util.*
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.Flow
import java.util.concurrent.SubmissionPublisher
import java.util.concurrent.atomic.AtomicInteger

class EndSubscriber<T>(val name: String): Flow.Subscriber<T> {

    lateinit var subscribtion: Flow.Subscription
    private val events = mutableListOf<T>()

    override fun onComplete() {
        println("[ES $name]: completed")
    }

    override fun onSubscribe(p0: Flow.Subscription?) {
        println("[ES $name]: subscribed")
        subscribtion = p0!!
        subscribtion.request(1)
    }

    override fun onNext(p0: T) {
        println("[ES $name]: received $p0")
        subscribtion.request(1)
    }


    override fun onError(p0: Throwable?) {
        println("[ES $name]: error: ${p0?.message}")
    }

}

class Transformer<T, R>:  SubmissionPublisher<R>, Flow.Processor<T,R> {

    private val block: (T) -> R

    constructor(block: (T) -> R): super() {
        this.block = block
    }

    lateinit var subs: Flow.Subscription

    override fun onComplete() {
        println("[TS]: completed")
        close()
    }

    override fun onSubscribe(p0: Flow.Subscription?) {
        println("[TS]: subscribed")
        subs = p0!!
        subs.request(1)
    }

    private val counter = AtomicInteger(0)

    override fun onNext(p0: T) {
        println("[TS]: submitting")
//        if(counter.get() == 5)
//            throw RuntimeException("Big error")
        counter.incrementAndGet()

        submit(block(p0))
        subs.request(1)
    }

    override fun onError(p0: Throwable?) {
        println("[TS]: error: ${p0?.message}")
        subscribers.forEach { it.onError(p0) }
    }

}

fun main(args: Array<String>) {

    val publisher = SubmissionPublisher<String>()
    val trans = Transformer { it: String ->
        it.substring(0, 8)
    }


    publisher.subscribe(trans)

    val s1 = EndSubscriber<String>("first")
    trans.subscribe(s1)

//    val s2 = EndSubscriber<String>("second")
//    trans.subscribe(s2)

    Thread.sleep(10)

    println("Publishing...")
    for(i in 0..10) {
        println("Submitting $i")
        publisher.submit("$i-${UUID.randomUUID()}")
    }

    //wait while the pipeline will be processed
    var timeout = 100
    while(publisher.estimateMaximumLag() > 0 && timeout > 0) {
        timeout -= 10
        Thread.sleep(10)
    }
    println(timeout)

    publisher.closeExceptionally(RuntimeException("FAIL MEE"))

    Thread.sleep(1000)
}