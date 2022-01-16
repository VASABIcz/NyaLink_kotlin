import io.ktor.http.cio.websocket.*
import kotlinx.coroutines.*
import kotlinx.serialization.*
import kotlinx.serialization.json.*



@OptIn(DelicateCoroutinesApi::class)
class Client(var id: Int, var ws: DefaultWebSocketSession) {
    var nodes = HashMap<String, Node>()
    var closed = false
    var listenner: Job? = null
    private var done = Job()

    private var counter = 0

    init {
        GlobalScope.launch { create_listener() }
    }

    suspend fun handle(frame: Frame.Text) {
        val data = frame.readText().trim().also { println(it) }
        Json { ignoreUnknownKeys=true }
        val d =Json.decodeFromString<Command>(data)
        when (d.op) {
            // debug
            "SUS" -> send(counter).also { counter += 1 }
            // nodes
            "add_node" -> parse<AddNode>(data)?.also { add_node(it) }
            "remove_node" -> parse<AddNode>(data)?.also {} // TODO: 16/01/2022
            // player
            "destroy" -> parse<DestroyPlayer>(data)?.also {} // TODO: 16/01/2022
            "clear" -> parse<Clear>(data)?.also {} // TODO: 16/01/2022
            "skip" -> parse<Skip>(data)?.also {} // TODO: 16/01/2022
            "shuffle" -> parse<Shuffle>(data)?.also {} // TODO: 16/01/2022
            "revind" -> parse<Revind>(data)?.also {} // TODO: 16/01/2022
            "play" -> parse<Play>(data)?.also {} // TODO: 16/01/2022
            "pause" -> parse<Pause>(data)?.also {} // TODO: 16/01/2022
            "remove" -> parse<Remove>(data)?.also {} // TODO: 16/01/2022
            "seek" -> parse<Seek>(data)?.also {} // TODO: 16/01/2022
            "loop" -> parse<Loop>(data)?.also {} // TODO: 16/01/2022
            "skip_to" -> parse<SkiTo>(data)?.also {} // TODO: 16/01/2022
        }
        println(d)
    }

    suspend fun loop() {
        for (x in ws.incoming) {
            when (x) {
                is Frame.Text -> coroutineScope { launch { handle(x) } }
                is Frame.Close -> done.complete()
            }
        }
    }

    suspend fun create_listener() {
        coroutineScope {
            listenner = launch {
                loop()
            }
        }
    }

    suspend fun send(text: Any) {
        println("sending $text")
        ws.send(text.toString())
    }
    
    suspend fun resume(ws: DefaultWebSocketSession) {
        done = Job()
        this.ws = ws
        create_listener()
    }

    suspend fun join() {
        done.join()
    }

    inline fun <reified T> parse(data: String): T?{
        return try {
            Json.decodeFromString<T>(data)
        } catch (t: Throwable) {
            println("failed to parse $data")
            null
        }
    }

    suspend fun add_node(data: AddNode) {
        val node = Node(data, this)
        nodes.put(data.password, node)
        node.connect()
    }

}