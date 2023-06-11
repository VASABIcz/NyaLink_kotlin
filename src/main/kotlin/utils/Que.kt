package utils

import kotlinx.coroutines.Job
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import mu.KotlinLogging

// FIXME: 23/01/2022 not working
// FIXME: 23/01/2022 play skips whole queue for some reason
/*
class Que<T> {
    private var queue = emptyList<T>().toMutableList()
    private var job = Job()

    var loop = LoopType.None

    val items: List<T>
        get() = queue.toList()

    val size: Int
        get() = queue.size

    // FIXME: 23/01/2022 erroring here
    suspend fun get(): T {
        if (queue.size == 0) {
            await_job()
            println(queue)
        }

        return queue[0]
    }

    override fun toString(): String {
        return queue.toString()
    }

    private suspend fun await_job() {
        if (!job.isActive) {
            job = Job()
        }
        job.join()
        job = Job()
    }

    private fun raw_consume() {
        queue_pop()
    }

    private fun move_on() {
        queue_pop().also { queue.add(it) }
    }

    private fun queue_pop(index: Int = 0): T {
        val x = queue[index]
        queue.removeAt(index)
        return x
    }

    fun remove(index: Int) {
        if (index in 1..size) {
            queue.removeAt(index)
        }
    }

    fun consume() {
        println("consuming $loop $queue")
        if (queue.size > 0) {
            when (loop) {
                LoopType.None -> raw_consume()
                LoopType.All -> move_on()
                LoopType.One -> {}
            }
        }
    }

    private fun raw_push(item: T) {
        queue.add(item)
    }

    fun push(item: T) {
        println("que ${this.queue} $job $")
        raw_push(item)
        println("pushed $item")
        job.complete()
    }

    fun clear() {
        queue.clear()
    }

    fun shuffle() {
        queue.shuffle()
    }

    fun skip_to(index: Int) {
        repeat(index) {
            val x = queue_pop()
            queue.add(x)
        }
    }

    fun add(data: Collection<T>) {
        queue.addAll(data)
        job.complete()
    }

    fun slice(start: Int, end: Int): List<T> {
        return queue.slice(start..end)
    }
}

 */

class SyncedQue<T> {
    private var queue = emptyList<T>().toMutableList()
    private var lock = Mutex()
    private var job = Job()
    private val logger = KotlinLogging.logger { }
    private var loop = LoopType.None

    val items: List<T>
        get() = queue.toList()

    val size: Int
        get() = queue.size

    // FIXME: 23/01/2022 erroring here
    suspend fun get(): T {
        if (queue.size == 0) {
            awaitJob()
            logger.debug(queue.toString())
        }

        return queue[0]
    }

    override fun toString(): String {
        return queue.toString()
    }

    suspend fun setLoop(loopType: LoopType) {
        lock.withLock {
            this.loop = loopType
        }
    }

    private suspend fun awaitJob() {
        if (!job.isActive) {
            job = Job()
        }
        job.join()
        job = Job()
    }

    private fun rawConsume() {
        queuePop()
    }

    private fun moveOn() {
        queuePop().also { queue.add(it) }
    }

    private fun queuePop(index: Int = 0): T {
        val x = queue[index]
        queue.removeAt(index)
        return x
    }

    suspend fun remove(index: Int) {
        lock.withLock {
            if (index in 1..size) {
                queue.removeAt(index)
            }
        }
    }

    suspend fun consume() {
        lock.withLock {
            logger.debug("consuming $loop $queue")
            if (queue.size > 0) {
                when (loop) {
                    LoopType.None -> rawConsume()
                    LoopType.All -> moveOn()
                    LoopType.One -> {}
                }
            }
        }
    }

    private fun rawPush(item: T) {
        queue.add(item)
    }

    suspend fun push(item: T) {
        lock.withLock {
            logger.debug("que ${this.queue} $job $")
            rawPush(item)
            logger.debug("pushed $item")
            job.complete()
        }
    }

    suspend fun clear() {
        lock.withLock {
            queue.clear()
        }
    }

    suspend fun shuffle() {
        lock.withLock {
            queue.shuffle()
        }
    }

    suspend fun skipTo(index: Int) {
        lock.withLock {
            repeat(index) {
                val x = queuePop()
                queue.add(x)
            }
        }
    }

    suspend fun pushAll(data: Collection<T>) {
        lock.withLock {
            queue.addAll(data)
            job.complete()
        }
    }

    suspend fun slice(start: Int, end: Int): List<T> {
        return lock.withLock {
            queue.slice(start..end)
        }
    }
}