import commands.Command
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.features.websocket.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.http.cio.websocket.*
import kotlinx.coroutines.*
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import lavalink_commands.Stats


class NodeWebsocket(val node: Node) {
    val connected: Boolean
        get() = ws?.isActive?: false
    var closed = false

    var ws: DefaultWebSocketSession? = null
    var pars = Json { ignoreUnknownKeys=true }

    fun handle(frame: Frame.Text) {
        val data = frame.readText().also { println(it) }
        println("parsing lavlaink $data")
        val d = parse<Command>(data)
        // TODO: 16/01/2022
        when (d?.op) {
            "stats" -> parse<Stats>(data).also { println(it) }.also { node.stats = it }
            else -> println("unhandled lavalink command $data")
        }
    }

    suspend fun listener() {
        for (x in ws?.incoming!!) {
            println("recived something")
            when (x) {
                is Frame.Text -> handle(x)
            }
        }
    }

    suspend fun connect() {
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
                    return
                }
                delay(1000)
                repeats += 1
            }
        }
    }

    suspend fun teardown() {
        closed = true
        ws?.close()
        println("teardown ws ${node.args.identifier}")
    }

    suspend fun send(text: Any) {
        println("sending $text")
        ws?.send(text.toString())
    }

    inline fun <reified T> parse(data: String): T? {
        return try {
            pars.decodeFromString<T>(data)
        } catch (t: Throwable) {
            println("failed to parse $data, e: $t")
            null
        }
    }
}