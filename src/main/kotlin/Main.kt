import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.routing.*
import io.ktor.application.*
import io.ktor.websocket.*


fun main(args: Array<String>) {
    val clients = HashMap<Int, Client>()

    embeddedServer(Netty, port = 8000) {
        install(WebSockets)
        routing {
            webSocket ("/") {
                val client = this.call.request.headers["client"]
                val id = client?.toIntOrNull()
                if (clients.containsKey(id)){
                    clients[id]?.resume(this@webSocket)
                }
                else if (id != null) {
                    println("succesfull")
                    val clinet =  Client(id, this@webSocket)
                    clients[id] = clinet
                    clients[id]?.listen()
                }
            }
        }
    }.start(wait = true)
}
