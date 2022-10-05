package trackloader

import Player
import commands.Play
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import mu.KotlinLogging

// TODO: 23/01/2022 implemnt spotify 
class TrackLoader(private val player: Player) {
    companion object {
        val regex =
            Regex("https?://(www\\.)?[-a-zA-Z0-9@:%._+~#=]{1,256}\\.[a-zA-Z0-9()]{1,6}\\b([-a-zA-Z0-9()@:%_+.~#?&//=]*)")
    }

    private val logger = KotlinLogging.logger { }
    private val cacheClient = player.node.client.cache
    var worker = player.scope.launch { work() }

    var channel = Channel<Play>()
    var isClosed = false


    suspend fun send(track: Play) {
        channel.send(track)
    }

    suspend fun maybeCache(data: String): CacheData? {
        logger.debug("checking cache for $data")
        return cacheClient.get(data)?.let { player.node.client.parse<CacheData>(it) }
    }

    suspend fun cache(key: String, value: CacheData) {
        val s = Json.encodeToString(value)
        logger.debug("caching $key ${value.tracks.size} tracks")
        cacheClient.set(key, s)

        for (track in value.tracks) {
            val k = track.info.title
            val v = CacheData(listOf(track), value.timestamp)
            logger.debug("caching $k $v track")
            cacheClient.set(k, Json.encodeToString(v))
            delay(5)
        }
    }

    suspend fun sendCallback(data: String) {
        // TODO uuid
        logger.debug("sending callback")
        player.node.client.send(data)
    }

    suspend fun fetchSearch(t: Play): CacheData? {
        val res = player.node.client.bestNodeFetch.also { logger.debug("sending work to node $it") }
            ?.limitFetch("ytsearch:${t.name}")

        if (res != null) {
            return if (res.tracks.isEmpty()) {
                logger.debug("no fetch search matches for ${t.name}")
                null
            } else {
                logger.debug("successful search fetched ${t.name} ${res.tracks.size} tracks")
                CacheData(listOf(res.tracks[0]), System.currentTimeMillis())
            }
        }
        return null
    }

    suspend fun fetchUrl(t: Play): CacheData? {
        val res = player.node.client.bestNodeFetch.also { logger.debug("sending work to node $it") }?.limitFetch(t.name)

        if (res != null) {
            return if (res.tracks.isEmpty()) {
                logger.debug("no fetch url matches for ${t.name}")
                null
            } else {
                logger.debug("successful url fetched ${t.name} ${res.tracks.size} tracks")
                CacheData(res.tracks, System.currentTimeMillis())
            }
        }
        return null
    }

    suspend fun process(data: Play): CacheData? {
        val cache = maybeCache(data.name)

        return if (cache == null || !data.cache) {
            logger.debug("fetched ${data.name}")

            if (data.name.matches(regex)) {
                fetchUrl(data).also { logger.debug("fetch_url res $it") }
                    ?.also { player.scope.launch { cache(data.name, it) } }
            } else {
                fetchSearch(data).also { logger.debug("fetch_search res $it") }
                    ?.also { player.scope.launch { cache(data.name, it) } }
            }
        } else {
            logger.debug("used cache to retrieve ${data.name} ${cache.tracks.size} tracks")
            cache
        }
    }

    suspend fun work() {
        while (!isClosed) {
            try {
                val data = channel.receive().also { logger.debug("working on ${it.name}") }

                val result = process(data)
                logger.debug("after processing ${data.name} $result")
                result ?: return

                player.scope.launch { player.doNext() }
                player.que.pushAll(result.tracks)

                player.scope.launch {
                    val callback =
                        TrackCallback("track_result", result.tracks[0], data.requester, data.channel, data.guild)
                    sendCallback(Json.encodeToString(callback))
                }

            } catch (_: Throwable) {
            }
        }
    }

    fun teardown() {
        isClosed = true
        channel.close()
    }

    fun clear() {
        worker.cancel()
        channel = Channel()
        worker = player.scope.launch { work() }
    }
}
