import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.routing.*
import io.ktor.application.*
import io.ktor.websocket.*
import kotlinx.serialization.json.Json
import redis.clients.jedis.*
import trackloader.Cache

fun main(args: Array<String>) {
    val clients = HashMap<Long, Client>()
    val redis = JedisPooled("127.0.0.1", 6379);
    val parser = Json { ignoreUnknownKeys=true }
    val cache = Cache(redis, parser)

    embeddedServer(Netty, port = 8000) {
        install(WebSockets)
        routing {
            webSocket ("/") {

                val client = this.call.request.headers["client"]
                val id = client?.toLongOrNull()
                if (clients.containsKey(id)){
                    clients[id]?.resume(this@webSocket)
                }
                else if (id != null) {
                    println("succesfull")

                    val clinet =  Client(id, this@webSocket, parser, cache)
                    clients[id] = clinet
                    clients[id]?.listen()
                }
            }
        }
    }.start(wait = true)
}
