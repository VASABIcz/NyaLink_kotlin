import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.features.websocket.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.http.cio.websocket.*
import kotlinx.coroutines.*
import lavalink_commands.*


class NodeWebsocket(val node: Node) {
    val connected: Boolean
        get() = ws?.isActive?: false
    var closed = false

    var ws: DefaultWebSocketSession? = null

    suspend fun handle(frame: Frame.Text) {
        val data = frame.readText()
        val d = node.client.parse<Event>(data)
        d?.op?.also { if (it != "playerUpdate") println("recived lavalink $it") }
        // TODO: 16/01/2022
        when (d?.op) {
            "stats" -> node.client.parse<Stats>(data)?.also { println(it) }.also { node.stats = it }
            "event" -> process_event(d, data)
            "playerUpdate" -> node.client.parse<PlayerUpdate>(data)?.also { node.players[it.guildId]?.update_player_state(it) }

            else -> println("unhandled lavalink command $data")
        }
    }

    suspend fun listener() {
        for (x in ws?.incoming!!) {
            when (x) {
                is Frame.Text -> handle(x)
            }
        }
    }

    suspend fun connect() {
        node.scope.launch {
            var repeats = 0
            while (!connected and !closed) {
                println("connecting to ${node.args.identifier} ${node.args}")
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
                        println("closing lavalink ws ${node.args.identifier}")
                        return@webSocket
                    }
                }
                catch (e: Throwable) {
                    println("ws exception ${node.args.identifier} $e")
                    if (repeats > 5) {
                        println("failed to connect/reconnect ${node.args.identifier}")
                        return@launch
                    }
                    delay(1000)
                    repeats += 1
                }
            }
        }
    }

    suspend fun teardown() {
        closed = true
        ws?.close()
        println("teardown ws ${node.args.identifier}")
    }

    suspend fun send(text: Any) {
        println("sending lavalink $text")
        ws?.send(text.toString())
    }

    suspend fun process_event(event: Event, data: String) {
        println(event)
        when (event.type) {
            "WebSocketClosedEvent" -> node.client.parse<WebSocketClosedEvent>(data).also { }
            "TrackStuckEvent" -> node.client.parse<TrackStuckEvent>(data)?.also { node.players[it.guildId]?.on_track_stop() }
            "TrackStartEvent" -> node.client.parse<TrackStartEvent>(data)?.also { }
            "TrackEndEvent" -> node.client.parse<TrackEndEvent>(data)?.also { node.players[it.guildId]?.on_track_stop() }
        }
    }
 }