import commands.Play
import kotlinx.coroutines.channels.Channel
import trackloader.TrackResult

/*
https://github.com/rrva/coredis


structure

request:
    name: nya nya
    channel: 123

Track
    generic track data (id, name, ...)

Result
    tracks [Track]
    source: spot/sc/yt
    type track/playlist
    image
    title

incoming request:

parse it to class
add to player fetch queue
queue picks it
    check if in cache or should be fetched
        read cahche
    else
        check if its any of spuported urls else just ytsearch:
        find the best nody by available permits
        aquire node semaphore permit
        await fetch
    if not null
        parse
        send response to cahnnel
        add to queue
*/

class TrackLoader(val player: Player) {
    val channel = Channel<Play>()
    var closed = false


    suspend fun send(track: Play) {
        channel.send(track)
    }

    suspend fun maybe_cahce(data: String): Boolean {
        // should return Result or null
        return false
    }

    suspend fun cache(key: String, value: TrackResult) {

    }

    suspend fun work() {
        while (!closed) {
            try {
                val t = channel.receive().also { println("working on ${it.name}") }
                if (maybe_cahce(t.name)) {

                    var source: String = "unknown"
                    // add more fu
                    val res = when (t.name) {
                        else -> player.node.client.best_node_fetch?.fetch_track(t.name)?.also { source = "idk" }
                    }
                    if (res != null) {
                        val result = TrackResult(res, source)
                        cache(t.name, result)
                        result.res.tracks.forEach() {
                            player.que.push(it)
                        }
                    }

                }
            }
            catch (_: Throwable) {
                closed = true
            }
        }
    }

    fun teardown() {
        closed = true
    }
}