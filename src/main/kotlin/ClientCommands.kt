class ClientCommands(val client: Client) {

    suspend fun add_node(data: AddNode) {
        val node = Node(data, client)
        client.nodes.put(data.password, node)
        node.connect()
    }

    fun remove_node(data: RemoveNode) {
        // TODO: 16/01/2022
    }

    fun destroy_player(data: DestroyPlayer) {
        // TODO: 16/01/2022
    }

    fun clear(data: Clear) {
        // TODO: 16/01/2022
    }

    fun skip(data: Skip) {
        // TODO: 16/01/2022
    }

    fun shuffle(data: Shuffle) {
        // TODO: 16/01/2022
    }

    fun revind(data: Revind) {
        // TODO: 16/01/2022
    }

    fun play(data: Play) {
        // TODO: 16/01/2022
    }

    fun pause(data: Pause) {
        // TODO: 16/01/2022
    }

    fun remove(data: Remove) {
        // TODO: 16/01/2022
    }

    fun seek(data: Seek) {
        // TODO: 16/01/2022
    }

    fun loop(data: Loop) {
        // TODO: 16/01/2022
    }

    fun skip_to(data: Skip) {
        // TODO: 16/01/2022
    }
}