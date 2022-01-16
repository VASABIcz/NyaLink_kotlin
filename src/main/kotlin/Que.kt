import kotlinx.coroutines.*

class Que<Any> {
    private var queue = emptyList<Any>().toMutableList()
    private var job = Job()

    var loop = LoopType.None

    val items: List<Any>
        get() = queue.toList()

    val size: Int
        get() = queue.size

    suspend fun get(): Any {
        if (queue.size == 0) {
            await_job()
        }

        return queue[0]
    }

    override fun toString(): String {
        return queue.toString()
    }

    private suspend fun await_job() {
        job.join()
        job = Job()
    }

    private fun raw_consume() {
        queue_pop()
    }

    private fun move_on() {
        val x = queue_pop()
        queue.add(x)
    }

    private fun queue_pop(index: Int=0): Any {
        val x = queue[index]
        queue.removeAt(index)
        return x
    }

    fun consume() {
        when (loop) {
            LoopType.None -> raw_consume()
            LoopType.All -> move_on()
            LoopType.One -> {}
        }
    }

    private fun raw_push(item: Any) {
        queue.add(item)
    }

    fun push(item: Any) {
        raw_push(item)
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

    fun slice(start: Int, end: Int): List<Any> {
        return queue.slice(start..end)
    }

}