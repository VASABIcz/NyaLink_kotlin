import commands.VoiceServerUpdate
import commands.VoiceStateUpdate
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import lavalink_commands.PlayerUpdate
import to_lavlaink_commands.Play as Playc
import commands.Play
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
        node.players.remove(id)
        loader.teardown()
    }

    suspend fun fetch_track(data: Play) {
        loader.send(data)
        println("sending to worker queue")
        do_next()
        println("doing next")
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
        println("send voice invoke $event $session")
        val e = event ?: return
        val s = session ?: return

        println("sending voice")

        val d = VoiceUpdate(guildId = id.toString(), sessionId = s.session_id, event = e, op = "voiceUpdate")
        node.ws.send(Json.encodeToString(d))
    }

    suspend fun do_next() {
        if (waiting || playing()) {
            return
        }
        waiting = true
        println("doing next")
        val res = que.get()
        que.consume()
        println("got $res from queue")
        play(res)

        waiting = false
    }

    suspend fun play(track: Track) {
        println("pay invoked $id")
        current = track
        last_position = 0
        last_update = 0

        val payload = Json.encodeToString(Playc(id.toString(), track.track, op = "play", noReplace = false))
        println("payload $payload")
        node.send(payload)
    }

    suspend fun stop() {
        node.send(Json.encodeToString(Stop(guildId = id, op = "skip")))
    }

    suspend fun on_track_stop() {
        current = null
        do_next()
    }
}