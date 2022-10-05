package trackloader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import redis.clients.jedis.JedisPooled


class Cache(private val cache: JedisPooled) {

    suspend fun get(key: String): String? = withContext(Dispatchers.IO) {
        tryTimes {
            cache.get(key)
        }
    }

    private suspend fun <T> tryTimes(times: Int = 5, d: Long = 100, x: () -> T?): T? {
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
        tryTimes {
            cache.set(key, value)
        }
    }

    suspend fun remove(key: String) {
        tryTimes {
            cache.del(key)
        }
    }

    suspend fun query(query: String): List<String>? = withContext(Dispatchers.IO) {
        return@withContext tryTimes {
            /*
            val scanParams = ScanParams().count(10).match("$query*")
            val cur = SCAN_POINTER_START

            val scanResult = cache.scan(cur, scanParams)

            return@trytimes scanResult.result.toList()

             */
            return@tryTimes cache.keys("$query*").toList()
        }
    }
}