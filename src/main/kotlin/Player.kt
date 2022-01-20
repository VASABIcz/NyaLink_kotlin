import commands.VoiceServerUpdate
import commands.VoiceStateUpdate
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import lavalink_commands.PlayerUpdate
import to_lavlaink_commands.Play as Playc
import commands.Play
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import to_lavlaink_commands.Stop
import to_lavlaink_commands.VoiceUpdate
import trackloader.Track
import trackloader.TrackLoader
import utils.Que

class Player(val node: Node, val id: Long) {
    val loader: TrackLoader = TrackLoader(this).also { GlobalScope.launch { it.work() } }
    var que: Que<Track> = Que()

    var session: VoiceStateUpdate? = null
    var event: VoiceServerUpdate? = null

    var waiting = false

    fun playing(): Boolean {
        if (session?.channel_id != null && current != null) {
            return true
        }
        return false

    }

    var current: Track? = null
    var last_position = 0
    var last_update = 0

    suspend fun teardown() {
        println("tearing down player")
        node.players.remove(id)
        loader.teardown()
    }

    suspend fun fetch_track(data: Play) {
        loader.send(data)
        println("player state playing: ${playing()} waiting: $waiting current $current")
        println("sending to worker queue ${data.name}")
        do_next()
    }

    suspend fun update_voice_state(state: VoiceStateUpdate) {
        session = state
        // TODO: 19/01/2022 add better handling
        send_voice()
    }

    suspend fun update_voice_server(state: VoiceServerUpdate) {
        event = state
        send_voice()
    }

    suspend fun update_player_state(data: PlayerUpdate) {

    }

    suspend fun send_voice() {
        val e = event ?: return
        val s = session ?: return

        println("sending voice $event $session")

        val d = VoiceUpdate(guildId = id.toString(), sessionId = s.session_id, event = e, op = "voiceUpdate")
        node.send(Json.encodeToString(d))
    }

    suspend fun do_next() {
        if (waiting || playing()) {
            return
        }
        waiting = true
        println("doing next")
        try {
            withTimeout(1000*30*1) {
                val res = que.get()
                que.consume()
                println("got $res from queue")
                play(res)
            }
        }
        catch (t: TimeoutCancellationException) {
            teardown()
            return
        }
        waiting = false
    }

    suspend fun play(track: Track) {
        println("play invoked $id ${track.info.title}")
        current = track
        last_position = 0
        last_update = 0

        val payload = Json.encodeToString(Playc(id.toString(), track.track, op = "play", noReplace = false))
        node.send(payload)
    }

    suspend fun stop() {
        node.send(Json.encodeToString(Stop(guildId = id.toString(), op = "stop")))
    }

    suspend fun on_track_stop() {
        current = null
        do_next()
    }
}