import io.ktor.application.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.websocket.*
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
        install(WebSockets)
        routing {
            webSocket("/") {

                val client = this.call.request.headers["client"]
                val id = client?.toLongOrNull()
                if (clients.containsKey(id)) {
                    clients[id]?.resume(this@webSocket)
                } else if (id != null) {
                    println("succesfull")

                    val clinet =  Client(id, this@webSocket, parser, cache)
                    clients[id] = clinet
                    clients[id]?.listen()
                }
            }
            get ("/stats") {
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
        }
    }.start(wait = true)
}
