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

    suspend fun query(query: String): List<String>? = withContext(Dispatchers.IO) {
        return@withContext trytimes {
            /*
            val scanParams = ScanParams().count(10).match("$query*")
            val cur = SCAN_POINTER_START

            val scanResult = cache.scan(cur, scanParams)

            return@trytimes scanResult.result.toList()

             */
            return@trytimes cache.keys("$query*").toList()
        }
    }

    suspend inline fun <reified T> parse(data: String): T? = coroutineScope {
        return@coroutineScope try {
            parser.decodeFromString<T>(data)
        } catch (t: Throwable) {
            println("failed to parse: $data\nerror: $t")
            null
        }
    }
}