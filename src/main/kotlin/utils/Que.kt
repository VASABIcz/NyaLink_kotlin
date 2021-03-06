package utils

import kotlinx.coroutines.Job

// FIXME: 23/01/2022 not working
// FIXME: 23/01/2022 play skips whole queue for some reason
class Que<Any> {
    private var queue = emptyList<Any>().toMutableList()
    private var job = Job()

    var loop = LoopType.None

    val items: List<Any>
        get() = queue.toList()

    val size: Int
        get() = queue.size

    // FIXME: 23/01/2022 erroring here  
    suspend fun get(): Any {
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

    private fun queue_pop(index: Int=0): Any {
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

    private fun raw_push(item: Any) {
        queue.add(item)
    }

    fun push(item: Any) {
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

    fun add(data: Collection<Any>) {
        queue.addAll(data)
        job.complete()
    }

    fun slice(start: Int, end: Int): List<Any> {
        return queue.slice(start..end)
    }
}