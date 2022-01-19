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
        cache_client.set(key, Json.encodeToString(value))
    }

    suspend fun send_callback(data: String) {
        player.node.client.send(data)
    }

    suspend fun work() {
        println("worker setuped for $player.id")
        while (!closed) {
            try {
                val t = channel.receive().also { println("working on ${it.name}") }
                val c = maybe_cache(t.name)
                if (c == null) {
                    var source: String = "unknown"
                    println("not in cache")
                    // add more fun
                    val res = when (t.name) {
                        else -> player.node.client.best_node_fetch.also { println("sending work to node $it") }?.limit_fetch("ytsearch:${t.name}")?.also { source = "idk" }
                    }
                    println("result $res")
                    if (res != null) {
                        if (res.loadType == "NO_MATCHES") {
                            println("no matches for ${t.name}")
                        }
                        else {
                            val result = TrackResult(res, source)
                            cache(t.name, result)
                            println("caching ${t.name}: ${Json.encodeToString(result)}")
                            send_callback(Json.encodeToString(TrackResult))
                            result.res.tracks.forEach() {
                                player.que.push(it)
                            }
                        }
                    }
                }
                else {
                    c.res.tracks.forEach() {
                        player.que.push(it)
                    }
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