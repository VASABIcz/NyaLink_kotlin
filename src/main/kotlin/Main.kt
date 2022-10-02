import io.ktor.http.*
import io.ktor.serialization.kotlinx.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import kotlinx.serialization.json.Json
import redis.clients.jedis.JedisPooled
import trackloader.Cache
import kotlin.system.exitProcess

// TODO: 23/01/2022 for some reason multiple clients are buggy
fun main() {
    val redis_url = System.getenv("REDIS_URL") ?: System.err.println("REDIS_URL not provided").let { exitProcess(1) }
    val redis_port =
        System.getenv("REDIS_PORT")?.toInt() ?: System.err.println("REDIS_PORT not provided").let { exitProcess(1) }
    val port = System.getenv("PORT")?.toInt() ?: System.err.println("PORT not provided").let { exitProcess(1) }

    val clients = HashMap<Long, Client>()
    val redis = JedisPooled(redis_url, redis_port)
    val parser = Json { ignoreUnknownKeys = true }
    val cache = Cache(redis, parser)

    embeddedServer(Netty, port = port) {
        install(WebSockets) {
            contentConverter = KotlinxWebsocketSerializationConverter(Json)
        }
        install(ContentNegotiation) {
            json()
        }
        routing {
            webSocket("/") {
                val client = call.request.headers["client"]
                val id = client?.toLongOrNull()
                if (clients.containsKey(id)) {
                    clients[id]?.resume(this@webSocket)
                } else if (id != null) {
                    println("succesfull")

                    val clinet = Client(id, this@webSocket, parser, cache)
                    clients[id] = clinet
                    clients[id]?.listen()
                }
            }
            get("/stats") {
                var res = ""

                res += "${clients.size} clients:\n"
                println(clients)

                clients.forEach {
                    res += "  ${it.key}  ${it.value.nodes.size} nodes:\n"
                    it.value.nodes.forEach {
                        res += "    ${it.key}  ${it.value.players.size} players:\n"
                        it.value.players.forEach {
                            res += "      ${it.key}  ${it.value.que.size} songs\n"
                        }
                    }
                }
                call.respondText(res)
            }
            get("now_playing/{guild_id}") {
                val c = call.request.headers["client"]?.toLongOrNull()
                val g = call.parameters["guild_id"]?.toLongOrNull()
                val client = clients[c] ?: return@get call.respond(HttpStatusCode.NotFound)
                val player = client.players()[g] ?: return@get call.respond(HttpStatusCode.NotFound)
                val x = player.current ?: return@get call.respond(HttpStatusCode.NotFound)
                call.respond(x)
            }
            get("queue/{guild_id}") {
                val c = call.request.headers["client"]?.toLongOrNull()
                val g = call.parameters["guild_id"]?.toLongOrNull()

                val a = call.request.queryParameters["amount"]?.toIntOrNull() ?: 10
                val o = call.request.queryParameters["offset"]?.toIntOrNull() ?: 0

                val client = clients[c] ?: return@get call.respond(HttpStatusCode.NotFound)
                val player = client.players()[g] ?: return@get call.respond(HttpStatusCode.NotFound)

                val x = player.que.items.drop(o).take(a)
                call.respond(x)
            }
            get("cache") {
                val q = call.request.queryParameters["q"] ?: return@get call.respond(HttpStatusCode.NotFound)
                println("cache query")
                val res = cache.query(q) ?: return@get call.respond(HttpStatusCode.NotFound)
                println("cache query res $res")
                call.respond(res)
            }
        }
    }.start(wait = true)
}
