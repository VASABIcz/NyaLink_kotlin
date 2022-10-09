package trackloader

import Player
import commands.Play
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.Channel.Factory.UNLIMITED
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import mu.KotlinLogging

class TrackLoader(private val player: Player) {
    companion object {
        val urlRegex =
            Regex("https?://(www\\.)?[-a-zA-Z0-9@:%._+~#=]{1,256}\\.[a-zA-Z0-9()]{1,6}\\b([-a-zA-Z0-9()@:%_+.~#?&//=]*)")
    }

    private val logger = KotlinLogging.logger { }
    private val cacheClient = player.node.client.cache
    var worker = player.scope.launch { work() }

    var channel = Channel<Play>(capacity = UNLIMITED)
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
            // logger.debug("caching $k $v track")
            cacheClient.set(k, Json.encodeToString(v))
            cacheClient.set(track.info.uri, Json.encodeToString(v))
            //delay(5)
        }
    }

    /*
    suspend fun sendCallback(data: String) {
        // TODO uuid
        logger.debug("sending callback")
        player.node.client.send(data)
    }

     */

    suspend fun fetchSearch(t: Play): CacheData? {
        val node = player.node.client.bestNodeFetch

        if (node == null) {
            println("did not found suitable node to perform fetch ${t.name}")
            return null
        }

        val searchType = when (t.searchType) {
            "spotify" -> "spsearch"
            "soundCloud" -> "scsearch"
            "appleMusic" -> "amsearch"
            "deezer" -> "dzsearch"
            "youtube" -> "ytsearch"
            else -> "ytsearch"
        }

        val searchString = "$searchType:${t.name}"

        logger.debug("sending work to node $node")
        val res = node.limitFetch(searchString)

        if (res == null) {
            logger.debug("fetch $searchString returned null")
            return null
        }

        if (res.tracks.isEmpty()) {
            logger.debug("no fetch search matches for $searchString")
            return null
        }

        logger.debug("successful search fetched $searchString ${res.tracks.size} tracks")
        return CacheData(listOf(res.tracks[0]), System.currentTimeMillis())
    }

    suspend fun fetchUrl(url: String): CacheData? {
        val res = player.node.client.bestNodeFetch.also { logger.debug("sending work to node $it") }?.limitFetch(url)

        if (res == null || res.tracks.isEmpty()) {
            logger.debug("no fetch url matches for $url")
            return null
        }

        logger.debug("successful url fetched $url ${res.tracks.size} tracks")
        return CacheData(res.tracks, System.currentTimeMillis())
    }

    suspend fun process(data: Play): CacheData? {
        val stripedName = data.name.strip()
        val cache = if (data.cache) {
            maybeCache(stripedName)
        } else {
            null
        }

        println("cache is $cache ${data.cache}")
        if (cache != null) {
            logger.debug("used cache to retrieve $stripedName ${cache.tracks.size} tracks")
            return cache
        }

        val res = if (stripedName.matches(urlRegex)) {
            fetchUrl(stripedName)
        } else {
            fetchSearch(data)
        }
        if (res != null) {
            player.scope.launch {
                cache(stripedName, res)
            }
        }
        return res
    }

    suspend fun work() {
        while (!isClosed) {
            try {
                println("waiting for work")
                val data = channel.receive().also { logger.debug("working on ${it.name}") }

                val result = process(data)
                logger.debug("after processing ${data.name} $result")
                result ?: return

                player.scope.launch { player.doNext() }
                player.que.pushAll(result.tracks)
                /*
                player.scope.launch {
                    val callback =
                        TrackCallback("track_result", result.tracks[0], data.requester, data.channel, data.guild)
                    // sendCallback(Json.encodeToString(callback))
                }

                 */

            } catch (t: Throwable) {
                t.printStackTrace()
                isClosed = true
            }
        }
    }

    fun teardown() {
        logger.debug("teardown track loader")
        isClosed = true
        channel.close()
    }

    fun clear() {
        return
        // FIXME
        // causes kotlinx.coroutines.JobCancellationException: StandaloneCoroutine was cancelled; job=StandaloneCoroutine{Cancelling}@24332135
        // which then causes player not responding
        // TODO maybe single track loader for whole nyalink
        worker.cancel()
        channel = Channel()
        worker = player.scope.launch { work() }
    }
}
