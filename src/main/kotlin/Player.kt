import commands.*
import kotlinx.coroutines.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import lavalink_commands.PlayerUpdate
import to_lavlaink_commands.Stop
import to_lavlaink_commands.VoiceUpdate
import trackloader.Track
import trackloader.TrackCallback
import trackloader.TrackLoader
import utils.SyncedQue
import java.util.concurrent.atomic.AtomicBoolean
import to_lavlaink_commands.Pause as Pausel
import to_lavlaink_commands.Play as Playl
import to_lavlaink_commands.Seek as Seekl

// TODO: 22/01/2022 small todo FIX THIS FUCKING MESS + TRACK-LOADER MESS :D
class Player(var node: Node, val id: Long) {
    val scope = CoroutineScope(Dispatchers.Default)
    val loader: TrackLoader = TrackLoader(this).also { scope.launch { it.work() } }
    var que: SyncedQue<Track> = SyncedQue()

    var session: VoiceStateUpdate? = null
    var event: VoiceServerUpdate? = null

    var waiting: AtomicBoolean = AtomicBoolean(false)

    val isPlaying: Boolean
        get() {
            if (session?.channel_id != null && current != null) {
                return true
            }
            return false
        }

    var current: Track? = null
    var last_position = 0L
    var last_update = 0L

    fun teardown() {
        println("tearing down player")
        node.players.remove(id)
        scope.cancel()
        loader.teardown()
    }

    suspend fun fetch_track(data: Play) {
        loader.send(data)
        println("player state playing: $isPlaying waiting: $waiting current $current")
        println("sending to worker queue ${data.name}")
    }

    suspend fun update_voice_state(state: VoiceStateUpdate) {
        if (session == null) {
            session = state
            send_voice()
            return
        }

        if (state.channel_id == null) {
            teardown()
            session = state
            send_voice()
        } else if (state.channel_id != session?.channel_id) {
            session = state
            send_voice()
        } else if (state.session_id != session?.session_id) {
            session = state
            send_voice()
        }
    }

    suspend fun update_voice_server(state: VoiceServerUpdate) {
        event = state
        send_voice()
    }

    fun update_player_state(data: PlayerUpdate) {
        last_position = data.state.position
        last_update = data.state.time
    }

    suspend fun send_voice() {
        val e = event ?: return
        val s = session ?: return

        val d = VoiceUpdate(guildId = id.toString(), sessionId = s.session_id, event = e, op = "voiceUpdate")
        node.send(Json.encodeToString(d))
    }

    suspend fun do_next() {
        if (waiting.acquire || isPlaying) {
            return
        }
        waiting.set(true)
        println("doing next")
        try {
            withTimeout(1000 * 30 * 1) {
                val res = que.get()
                println("got $res from queue")
                send_play(res)
            }
        } catch (t: TimeoutCancellationException) {
            teardown()
            return
        }
        waiting.set(false)
    }

    suspend fun stop() {
        node.send(Json.encodeToString(Stop(guildId = id.toString(), op = "stop")))
    }

    suspend fun on_track_stop() {
        current = null
        que.consume()
        do_next()
    }

    suspend fun send_play(track: Track) {
        println("play invoked $id ${track.info.title}")
        current = track
        last_position = 0
        last_update = 0

        val payload = Json.encodeToString(Playl(id.toString(), track.track, op = "play", noReplace = false))
        node.send(payload)
    }

    suspend fun send_pause(data: Pause) {
        println("sending pause ${data.pause} $id")
        val payload = Json.encodeToString(Pausel(op = "pause", guildId = id.toString(), data.pause))
        node.send(payload)
    }

    suspend fun send_seek(data: Seek) {
        println("sending seek ${data.time / 1000 / 60}m $id")
        val payload = Json.encodeToString(Seekl(op = "seek", guildId = id.toString(), data.time))
        node.send(payload)
    }

    suspend fun clear() {
        loader.clear()
        que.clear()
        stop()
    }

    fun move(identifier: String? = null) {
        // TODO: 23/01/2022 not sure if it works 
        node.players.remove(id)
        if (identifier != null) {
            node.client.nodes[identifier]?.also { node = it }?.also { it.players[id] = this@Player }
        } else {
            node.client.best_node_players?.also { node = it }?.also { it.players[id] = this@Player }
        }
    }

    suspend fun send_callback(data: NowPlaying) {
        node.client.send(
            Json.encodeToString(
                TrackCallback(
                    "track_result",
                    current ?: return,
                    data.requester,
                    data.channel,
                    data.guild
                )
            )
        )
    }
}