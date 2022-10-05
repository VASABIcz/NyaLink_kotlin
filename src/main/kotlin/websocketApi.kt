import io.ktor.server.application.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import mu.KotlinLogging
import trackloader.Cache

fun Application.websocketApi(clients: HashMap<Long, Client>, cache: Cache) {
    val logger = KotlinLogging.logger { }
    logger.info { "ws listening" }

    routing {
        webSocket("/") {
            val client = call.request.headers["client"]
            val id = client?.toLongOrNull()
            if (clients.containsKey(id)) {
                logger.debug("resuming client $client")
                clients[id]?.resume(this@webSocket)
            } else if (id != null) {
                logger.debug("connecting client $client")
                val clinet = Client(id, cache)
                clients[id] = clinet
                clients[id]?.listen(this@webSocket)
            }
            logger.debug("client $client disconnected")
        }
    }
}