package trackloader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import redis.clients.jedis.JedisPooled


// TODO: 19/01/2022 make async
class Cache(private val cache: JedisPooled, val parser: Json) {

    suspend fun get(key: String): String? = withContext(Dispatchers.IO) {
        val res = cache.get(key)
        return@withContext res
    }

    suspend fun get_parsed(keu: String): TrackResult? {
        var res: TrackResult? = null
        get(keu)?.also { res = parse<TrackResult>(it) }

        return res
    }

    suspend fun set(key: String, value: String) = withContext(Dispatchers.IO) {
        cache.set(key, value)
    }

    fun remove(key: String) {
        cache.del(key)
    }

    inline fun <reified T> parse(data: String): T? {
        return try {
            parser.decodeFromString<T>(data)
        } catch (t: Throwable) {
            println("failed to parse $data")
            null
        }
    }
}