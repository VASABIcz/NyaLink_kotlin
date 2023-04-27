import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.websocket.*

object App {
    val client = HttpClient(CIO) {
        install(WebSockets)
    }
}