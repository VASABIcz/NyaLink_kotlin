import commands.AddNode
import commands.RemoveNode
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import lavalink_commands.Stats
import trackloader.FetchResult

class Node(val args: AddNode, val client: Client) {
    val scope = CoroutineScope(Dispatchers.Default)
    var players = HashMap<Long, Player>()
    lateinit var ws: NodeWebsocket
    val available: Boolean
        get() = ws.connected
    var stats: Stats? = null
    val semaphore = Semaphore(4)

    suspend fun connect() {
        ws = NodeWebsocket(this)
        ws.connect()
    }

    suspend fun teardown(data: RemoveNode) {
        client.nodes.remove(data.identifier)
        ws.teardown() // TODO: 16/01/2022 add move players on teardown
        players.values.forEach {
            it.teardown()
        }
        scope.cancel()
        println("teardown node ${args.identifier}")
    }

    suspend fun send(data: String) {
        ws.send(data)
    }

    suspend fun fetch_track(track: String): FetchResult? {
        val type = if (args.secure) "https" else "http"

        repeat(3) {
            val response: HttpStatement = client.ktor_client.request("${type}://${args.host}:${args.port}/loadtracks") {
                method = HttpMethod.Get
                parameter("identifier", track)
                header("Authorization", args.password)
                header("User-Id", client.id.toString())
                header("Client-Name", "NyaLink_kotlin")
            }
            val res = response.execute()

            if (res.status.value != 200) {
                println("track load returned $track ${res.status.value}")
                return@repeat
            }
            val data = res.readText()
            val parsed = client.parse<FetchResult>(data)
            println("parsed track respnse $parsed")
            return parsed
        }
        return null
    }

    suspend fun limit_fetch(track: String): FetchResult? {
        semaphore.withPermit {
            return fetch_track(track)
        }
    }

    fun create_player(id: Long): Player {
        val player = Player(this, id)
        players.put(id, player)
        return player
    }
}