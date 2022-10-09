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

// TODO: 23/01/2022 for some reason multiple clients are buggy

data class NyaLinkConfig(var port: Int? = null, var redisHost: String? = null, var redisPort: Int? = null)

class ArgumentNotSpecified(name: String) : Throwable("argument $name was not specified or was invalid")

fun getEnvConfig(): NyaLinkConfig {
    val cfg = NyaLinkConfig()
    val splited = System.getenv("REDIS").split(":")
    cfg.redisHost = splited.getOrNull(0)
    cfg.redisPort = splited.getOrNull(1)?.toIntOrNull()
    cfg.port = System.getenv("PORT")?.toInt()
    return cfg
}

fun main() {
    val cfg = getEnvConfig()
    val redisPort = cfg.redisPort ?: throw ArgumentNotSpecified("redis port")
    val redisUrl = cfg.redisHost ?: throw ArgumentNotSpecified("redis host")
    val port = cfg.port ?: 8000

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
