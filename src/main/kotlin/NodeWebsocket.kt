import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.features.websocket.*
import io.ktor.client.utils.*
import io.ktor.http.HttpMethod
import io.ktor.http.cio.websocket.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class NodeWebsocket(val node: Node) {
    var connected = false
    var listenner: Job? = null
    var done = Job()
    lateinit var ws: DefaultWebSocketSession

    fun complete() {
        done.complete()
        done = Job()
    }

    suspend fun listener() {
        for (x in ws.incoming) {
            when (x) {
                is Frame.Text -> println("recived ${x.readText()}")
                is Frame.Close -> {
                    connected = false
                    complete()
                }
            }
        }
    }

    suspend fun connect() {
        val client = HttpClient(CIO) {
            install(WebSockets)
            buildHeaders {
                append("Authorization", node.args.password)
                append("User-Id", node.client.id.toString())
                append("Client-Name", "NyaLink_kotlin")
            }
        }
        connected = true
        client.webSocket(method = HttpMethod.Get, host = node.args.host, port = node.args.port) {
            ws = this@webSocket
            launch { listener() }
            done.join()
        }
    }

    fun teardown() {
        connected = false
        done.complete()
    }
}