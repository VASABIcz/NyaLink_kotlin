import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.websocket.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import lavalink_commands.*
import mu.KotlinLogging


class NodeWebsocket(private val node: Node) {
    private val logger = KotlinLogging.logger { }
    val isConnected: Boolean
        get() = ws?.isActive ?: false
    var isClosed = false

    var ws: DefaultWebSocketSession? = null

    private suspend fun handle(frame: Frame.Text) {
        val data = frame.readText()

        logger.debug("node ws ${node.args.identifier} $data")
        val d = node.client.parse<Event>(data)
        d?.op?.also { if (it != "playerUpdate") logger.debug("received lavalink $it") }
        when (d?.op) {
            "stats" -> node.client.parse<Stats>(data)?.also { logger.debug(it.toString()) }
                .also { node.statistics = it }

            "event" -> processEvent(d, data)
            "playerUpdate" -> node.client.parse<PlayerUpdate>(data)
                ?.also { node.players[it.guildId]?.updatePlayerState(it) }

            else -> logger.debug("unhandled lavalink command $data")
        }
    }

    private suspend fun listener() {
        for (x in ws?.incoming!!) {
            when (x) {
                is Frame.Text -> handle(x)
            }
        }
    }

    suspend fun connect() {
        // todo better reconnect logic
        node.scope.launch {
            if (!node.scope.isActive) {
                return@launch
            }
            logger.debug("connecting to ${node.args.identifier} ${node.args}")
            while (!isConnected and !isClosed) {
                val client = HttpClient(CIO) {
                    install(WebSockets)
                }
                try {
                    client.webSocket(method = HttpMethod.Get, host = node.args.host, port = node.args.port, request = {
                        header("Authorization", node.args.password)
                        header("User-Id", node.client.id.toString())
                        header("Client-Name", "NyaLink_kotlin")
                    }) {
                        ws = this@webSocket
                        withContext(Dispatchers.Default) { listener() }
                        logger.debug("closing lavalink ws ${node.args.identifier}")
                        return@webSocket
                    }
                }
                catch (e: Throwable) {
                    logger.debug("ws exception ${node.args.identifier} $e")
                    delay(1000)
                }
            }
        }
    }

    suspend fun teardown() {
        isClosed = true
        ws?.close()
        logger.debug("teardown ws ${node.args.identifier}")
    }

    suspend fun send(text: Any) {
        logger.debug("sending lavalink $text")
        ws?.send(text.toString())
    }

    private suspend fun processEvent(event: Event, data: String) {
        logger.debug(event.toString())
        when (event.type) {
            "WebSocketClosedEvent" -> node.client.parse<WebSocketClosedEvent>(data).also {
                // todo
            }

            "TrackStuckEvent" -> node.client.parse<TrackStuckEvent>(data)
                ?.also { node.players[it.guildId]?.onTrackStop() }

            "TrackStartEvent" -> node.client.parse<TrackStartEvent>(data)?.also {
                // todo
            }

            "TrackEndEvent" -> node.client.parse<TrackEndEvent>(data)?.also { node.players[it.guildId]?.onTrackStop() }
        }
    }
 }