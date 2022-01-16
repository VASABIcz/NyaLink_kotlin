class Node(val args: AddNode, val client: Client) {

    var players = HashMap<Int, Player>()
    var available = false
    lateinit var ws: NodeWebsocket

    suspend fun connect() {
        ws = NodeWebsocket(this)
        ws.connect()
    }
    
    fun teardown() {
        client.nodes.remove(args.identifier)
        ws.teardown()

        // TODO: 16/01/2022 add move players on teardown 
    }

}