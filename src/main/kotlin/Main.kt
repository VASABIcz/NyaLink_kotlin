import io.ktor.serialization.kotlinx.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.websocket.*
import kotlinx.serialization.json.Json
import redis.clients.jedis.JedisPooled
import trackloader.Cache
import kotlin.system.exitProcess

// TODO: 23/01/2022 for some reason multiple clients are buggy
fun main() {
    val redisUrl = System.getenv("REDIS_URL") ?: System.err.println("REDIS_URL not provided").let { exitProcess(1) }
    val redisPort =
        System.getenv("REDIS_PORT")?.toInt() ?: System.err.println("REDIS_PORT not provided").let { exitProcess(1) }
    val port = System.getenv("PORT")?.toInt() ?: System.err.println("PORT not provided").let { exitProcess(1) }

    val clients = HashMap<Long, Client>()
    val redis = JedisPooled(redisUrl, redisPort)
    val cache = Cache(redis)

    embeddedServer(Netty, port = port) {
        install(WebSockets) {
            contentConverter = KotlinxWebsocketSerializationConverter(Json)
        }
        install(ContentNegotiation) {
            json()
        }

        websocketApi(clients, cache)
        restApi(clients, cache)
    }.start(wait = true)
}
