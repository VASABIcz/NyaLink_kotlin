import commands.AddNode
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import lavalink_commands.Stats
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import trackloader.FetchResult

class Node(val args: AddNode, val client: Client) {
    var players = HashMap<Int, Player>()
    lateinit var ws: NodeWebsocket
    val available: Boolean
        get() = ws.connected
    var stats: Stats? = null
    val semaphore = Semaphore(4)

    suspend fun connect() {
        ws = NodeWebsocket(this)
        coroutineScope { launch { ws.connect() } }
    }
    
    suspend fun teardown() {
        ws.teardown() // TODO: 16/01/2022 add move players on teardown
        println("teardown node ${args.identifier}")
    }

    suspend fun send(data: String) {
        ws.send(data)
    }

    suspend fun fetch_track(track: String): FetchResult? {
        val type = if (args.secure) "https" else "http"

        repeat(3) {
            val response: HttpStatement = client.ktor_client.request("${type}://${args.host}:${args.port}/") {
                method = HttpMethod.Get
                parameter("identifier", track)
                header("Authorization", args.password)
                header("User-Id", client.id.toString())
                header("Client-Name", "NyaLink_kotlin")
            }
            val res = response.execute()

            if (res.status.value != 200) {
                println("returning repeat idk")
                return@repeat
            }

            val data = res.readText()
            client.parse<FetchResult>(data)?.also { return it }
        }
        return null
    }

    suspend fun limit_fetch(track: String): FetchResult? {
        semaphore.withPermit {
            return fetch_track(track)
        }
    }
}