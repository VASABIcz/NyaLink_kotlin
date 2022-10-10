package trackloader

import Client
import commands.Play
import commands.TrackSource
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import mu.KotlinLogging

class TrackLoader(private val client: Client) {
    companion object {
        val urlRegex =
            Regex("https?://(www\\.)?[-a-zA-Z0-9@:%._+~#=]{1,256}\\.[a-zA-Z0-9()]{1,6}\\b([-a-zA-Z0-9()@:%_+.~#?&//=]*)")
    }

    private val logger = KotlinLogging.logger { }
    private val cacheClient = client.cache
    private val semaphore = Semaphore(8)


    suspend fun fetch(track: Play): CacheData? {
        semaphore.withPermit {
            return process(track)
        }
    }

    private suspend fun maybeCache(data: String): CacheData? {
        logger.debug("checking cache for $data")
        return cacheClient.get(data)?.let { client.parse<CacheData>(it) }
    }

    private suspend fun cache(key: String, value: CacheData) {
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

    private suspend fun fetchSearch(t: Play): CacheData? {
        val searchType = when (t.getType) {
            TrackSource.spotify -> "spsearch"
            TrackSource.soundCloud -> "scsearch"
            TrackSource.appleMusic -> "amsearch"
            TrackSource.deezer -> "dzsearch"
            TrackSource.youtube -> "ytsearch"
        }

        val node = client.ableToFetchThis(t.getType)

        if (node == null) {
            logger.error("did not found suitable node to perform fetch ${t.name} ${client.nodes}")
            return null
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

    private suspend fun fetchUrl(url: String): CacheData? {
        // TODO check url to determine source and pick node accordingly
        val node = client.bestNodeFetch

        if (node == null) {
            logger.error("did not found suitable node to perform fetch $url nodes: ${client.nodes}")
            return null
        }

        logger.debug("sending work to node $node")
        val res = node.limitFetch(url)

        if (res == null) {
            logger.debug("fetch $url returned null")
            return null
        }

        if (res.tracks.isEmpty()) {
            logger.debug("no fetch search matches for $url")
            return null
        }

        logger.debug("successful url fetched $url ${res.tracks.size} tracks")
        return CacheData(res.tracks, System.currentTimeMillis())
    }

    private suspend fun process(data: Play): CacheData? {
        val stripedName = data.name.strip()
        val cache = if (data.cache) {
            maybeCache(stripedName)
        } else {
            null
        }

        logger.debug("cache is $cache ${data.cache}")
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
            client.scope.launch {
                cache(stripedName, res)
            }
        }
        return res
    }
}
