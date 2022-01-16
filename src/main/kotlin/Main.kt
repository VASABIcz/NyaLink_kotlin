import kotlinx.coroutines.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.routing.*
import io.ktor.features.*
import io.ktor.response.*
import io.ktor.application.*
import io.ktor.content.*
import io.ktor.http.cio.websocket.*
import io.ktor.websocket.*



fun main(args: Array<String>) {
    val clients = HashMap<Int, Client>()

    embeddedServer(Netty, port = 8000) {
        install(WebSockets)
        routing {
            webSocket ("/") {
                val client = this.call.request.headers.get("client")
                val id = client?.toIntOrNull()
                println(id)
                if (clients.containsKey(id)){
                    clients[id]?.resume(this@webSocket)
                }
                else if (id != null) {
                    println("succesfull")
                    clients[id] = Client(id, this@webSocket)
                }
                clients[id]?.join()
            }
        }
    }.start(wait = true)
}
