package trackloader

import Player
import commands.Play
import commands.PlayCallback
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
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
    var channel = Channel<Play>()
    var closed = false
    val cache_client = player.node.client.cache
    val regex = Regex("https?://(www\\.)?[-a-zA-Z0-9@:%._+~#=]{1,256}\\.[a-zA-Z0-9()]{1,6}\\b([-a-zA-Z0-9()@:%_+.~#?&//=]*)")
    var worker = player.scope.launch { work() }


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

    suspend fun fetch_search(t: Play) {
        val res = player.node.client.best_node_fetch.also { println("sending work to node $it") }?.limit_fetch("ytsearch:${t.name}")

        if (res != null) {
            if (res.loadType == "NO_MATCHES") {
                println("no matches for ${t.name}")
            }
            else {
                res.tracks = listOf(res.tracks[0])
                val result = TrackResult(res, res.tracks[0].info.sourceName)
                cache(t.name, result)
                send_callback(Json.encodeToString(PlayCallback(result, t.requester, t.channel)))
                player.que.push(result.res.tracks[0])
                player.do_next()
            }
        }
    }

    suspend fun fetch_url(t: Play) {
        val res = player.node.client.best_node_fetch.also { println("sending work to node $it") }?.limit_fetch(t.name)

        if (res != null) {
            if (res.loadType == "NO_MATCHES") {
                println("no matches for ${t.name}")
            }
            else {
                val result = TrackResult(res, res.tracks[0].info.sourceName)
                cache(t.name, result)
                send_callback(Json.encodeToString(result))
                if (!player.waiting || !player.playing()) {
                    // TODO: 22/01/2022 maybe bad idk rn
                    player.do_next()
                }
                player.que.add(res.tracks)
            }
        }
    }

    suspend fun fetch_spotify() {

    }

    suspend fun work() {
        while (!closed) {
            try {
                val t = channel.receive().also { println("working on ${it.name}") }
                var res: FetchResult?
                var source: String = "unknown"

                val c = maybe_cache(t.name)

                // TODO: 22/01/2022 this is so bad
                if (c == null) {
                    println("fetched ${t.name}")

                    if (t.name.matches(regex)) {
                        fetch_url(t)
                    }
                    else {
                        fetch_search(t)
                    }
                }
                else {
                    println("cached ${t.name}")
                    if (!player.waiting || !player.playing()) {
                        // TODO: 22/01/2022 maybe bad idk rn
                        player.do_next()
                    }
                    c.res.tracks.forEach {
                        player.que.push(it)
                    }
                    player.do_next()
                }
            }
            catch (_: Throwable) {
            }
        }
    }

    fun teardown() {
        closed = true
        channel.close()
    }

    fun clear() {
        worker.cancel()
        channel = Channel()
        worker = player.scope.launch { work() }
    }
}