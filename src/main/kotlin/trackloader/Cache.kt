package trackloader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import redis.clients.jedis.JedisPooled


class Cache(private val cache: JedisPooled, val parser: Json) {

    suspend fun get(key: String): String? = withContext(Dispatchers.IO) {
        trytimes {
            cache.get(key)
        }
    }

    suspend fun <T> trytimes(times: Int = 5, d: Long = 100, x: () -> T?): T? {
        repeat(times) {
            try {
                return x()
            } catch (t: Throwable) {
                delay(d)
            }
        }
        return null
    }

    suspend fun get_parsed(keu: String): TrackResult? {
        var res: TrackResult? = null
        get(keu)?.also { res = parse<TrackResult>(it) }

        return res
    }

    suspend fun set(key: String, value: String) = withContext(Dispatchers.IO) {
        trytimes {
            cache.set(key, value)
        }
    }

    suspend fun remove(key: String) {
        trytimes {
            cache.del(key)
        }
    }

    suspend inline fun <reified T> parse(data: String): T? = coroutineScope {
        return@coroutineScope try {
            parser.decodeFromString<T>(data)
        } catch (t: Throwable) {
            println("failed to parse $data")
            null
        }
    }
}