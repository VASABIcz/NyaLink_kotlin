import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import mu.KotlinLogging
import trackloader.Cache
import trackloader.Track


fun Application.restApi(clients: HashMap<Long, Client>, cache: Cache) {
    val logger = KotlinLogging.logger { }
    logger.info { "rest listening" }

    routing {
        get("/stats") {
            var res = ""

            res += "${clients.size} clients:\n"
            logger.debug(clients.toString())

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
            val player = client.getPlayers()[g] ?: return@get call.respond(HttpStatusCode.NotFound)
            val x = player.current ?: return@get call.respond(HttpStatusCode.NotFound)
            call.respond(x)
        }
        get("queue/{guild_id}") {
            val c = call.request.headers["client"]?.toLongOrNull()
            val g = call.parameters["guild_id"]?.toLongOrNull()

            val a = call.request.queryParameters["amount"]?.toIntOrNull() ?: 10
            val o = call.request.queryParameters["offset"]?.toIntOrNull() ?: 0

            val client = clients[c] ?: return@get call.respond(HttpStatusCode.NotFound)
            val player = client.getPlayers()[g] ?: return@get call.respond(HttpStatusCode.NotFound)

            val queue = Queue(player.que.items.drop(o).take(a), player.que.size)
            call.respond(queue)
        }
        get("cache") {
            val q = call.request.queryParameters["q"] ?: return@get call.respond(HttpStatusCode.NotFound)
            logger.debug("cache query")
            val res = cache.query(q) ?: return@get call.respond(HttpStatusCode.NotFound)
            logger.debug("cache query res $res")
            call.respond(res)
        }

        get("nodes") {
            val c = call.request.headers["client"]?.toLongOrNull()
            val client = clients[c] ?: return@get call.respond(HttpStatusCode.NotFound)
            val nds = client.nodes.values.map {
                it.getNodStatus()
            }
            call.respond(nds)
        }
    }
}

@Serializable
data class Queue(val items: List<Track>, val size: Int)