package trackloader

import Player
import commands.Play
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

// TODO: 23/01/2022 implemnt spotify 
class TrackLoader(val player: Player) {
    var channel = Channel<Play>()
    var closed = false
    val cache_client = player.node.client.cache
    val regex = Regex("https?://(www\\.)?[-a-zA-Z0-9@:%._+~#=]{1,256}\\.[a-zA-Z0-9()]{1,6}\\b([-a-zA-Z0-9()@:%_+.~#?&//=]*)")
    var worker = player.scope.launch { work() }


    suspend fun send(track: Play) {
        channel.send(track)
    }

    suspend fun maybe_cache(data: String): CacheData? {
        println("checking cache for $data")
        return cache_client.get(data)?.let { player.node.client.parse<CacheData>(it) }
    }

    suspend fun cache(key: String, value: CacheData) {
        val s = Json.encodeToString(value)
        println("caching $key")
        cache_client.set(key, s)
    }

    suspend fun send_callback(data: String) {
        println("sending callback")
        player.node.client.send(data)
    }

    suspend fun fetch_search(t: Play): CacheData? {
        val res = player.node.client.best_node_fetch.also { println("sending work to node $it") }
            ?.limit_fetch("ytsearch:${t.name}")

        if (res != null) {
            return if (res.loadType == "NO_MATCHES") {
                println("no matches for ${t.name}")
                null
            } else {
                println("parsing to cache format ${t.name}")
                CacheData(listOf(res.tracks[0]), System.currentTimeMillis())//.also { cache(t.name, it) }
            }
        }
        return null
    }

    suspend fun fetch_url(t: Play): CacheData? {
        val res = player.node.client.best_node_fetch.also { println("sending work to node $it") }?.limit_fetch(t.name)

        if (res != null) {
            if (res.loadType == "NO_MATCHES") {
                println("no matches for ${t.name}")
                return null
            } else {
                println("parsing to cache format ${t.name}")
                return CacheData(res.tracks, System.currentTimeMillis())//.also { cache(t.name, it) }
            }
        }
        return null
    }

    suspend fun process(data: Play): CacheData? {
        val cache = maybe_cache(data.name)

        return if (cache == null || !data.cache) {
            println("fetched ${data.name}")

            if (data.name.matches(regex)) {
                fetch_url(data).also { println("fetch_url res $it") }
                    ?.also { player.scope.launch { cache(data.name, it) } }
            } else {
                fetch_search(data).also { println("fetch_search res $it") }
                    ?.also { player.scope.launch { cache(data.name, it) } }
            }
        } else {
            println("cached ${data.cache}")
            cache
        }
    }

    suspend fun work() {
        while (!closed) {
            try {
                val data = channel.receive().also { println("working on ${it.name}") }

                val result = process(data)
                println("after processing ${data.name} $result")
                result ?: return

                player.scope.launch { player.do_next() }
                player.que.add(result.tracks)

                player.scope.launch {
                    val callback =
                        TrackCallback("track_result", result.tracks[0], data.requester, data.channel, data.guild)
                    send_callback(Json.encodeToString(callback))
                }

            } catch (_: Throwable) {
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
