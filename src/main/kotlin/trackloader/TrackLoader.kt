package trackloader

import Player
import commands.Play
import kotlinx.coroutines.channels.Channel
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

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
    val cache_client = player.node.client.cache


    suspend fun send(track: Play) {
        channel.send(track)
    }

    suspend fun maybe_cache(data: String): TrackResult? {
        println("checking cache for $data")
        return cache_client.get(data)?.also { send_callback(it) }?.let { player.node.client.parse<TrackResult>(it) }
    }

    suspend fun cache(key: String, value: TrackResult) {
        val s = Json.encodeToString(value)
        println("caching ${key}")
        cache_client.set(key, s)
    }

    suspend fun send_callback(data: String) {
        println("sending callback")
        player.node.client.send(data)
    }

    suspend fun work() {
        while (!closed) {
            try {
                val t = channel.receive().also { println("working on ${it.name}") }
                val c = maybe_cache(t.name)
                if (c == null) {
                    var source: String = "unknown"
                    println("fetched ${t.name}")
                    // add more fun
                    val res = when (t.name) {
                        else -> player.node.client.best_node_fetch.also { println("sending work to node $it") }?.limit_fetch("ytsearch:${t.name}")?.also { source = "idk" }
                    }
                    if (res != null) {
                        if (res.loadType == "NO_MATCHES") {
                            println("no matches for ${t.name}")
                        }
                        else {
                            val result = TrackResult(res, source)
                            cache(t.name, result)
                            send_callback(Json.encodeToString(result))
                            player.que.push(result.res.tracks[0])
                            player.do_next()
                        }
                    }
                }
                else {
                    println("cached ${t.name}")
                    player.que.push(c.res.tracks[0])
                    player.do_next()
                }
            }
            catch (_: Throwable) {
                closed = true
            }
        }
    println("cache nya nya result: ${cache_client.get("nya nya")}")
    }

    fun teardown() {
        closed = true
    }
}