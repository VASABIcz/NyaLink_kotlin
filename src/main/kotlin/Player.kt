import commands.*
import kotlinx.coroutines.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import lavalink_commands.PlayerUpdate
import mu.KotlinLogging
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
    private val loader: TrackLoader = TrackLoader(this)
    private val logger = KotlinLogging.logger { }
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
    var lastPosition = 0L
    var lastUpdate = 0L

    fun teardown() {
        logger.debug("tearing down player")
        node.players.remove(id)
        scope.cancel()
        loader.teardown()
    }

    suspend fun fetchTrack(data: Play) {
        loader.send(data)
        logger.debug("player state playing: $isPlaying waiting: $waiting current $current")
        logger.debug("sending to worker queue ${data.name}")
    }

    suspend fun updateVoiceState(state: VoiceStateUpdate) {
        if (session == null) {
            session = state
            sendVoice()
            return
        }

        if (state.channel_id == null) {
            teardown()
            session = state
            sendVoice()
        } else if (state.channel_id != session?.channel_id) {
            session = state
            sendVoice()
        } else if (state.session_id != session?.session_id) {
            session = state
            sendVoice()
        }
    }

    suspend fun updateVoiceServer(state: VoiceServerUpdate) {
        event = state
        sendVoice()
    }

    fun updatePlayerState(data: PlayerUpdate) {
        lastPosition = data.state.position
        lastUpdate = data.state.time
    }

    suspend fun sendVoice() {
        val e = event ?: return
        val s = session ?: return

        val d = VoiceUpdate(guildId = id.toString(), sessionId = s.session_id, event = e, op = "voiceUpdate")
        node.send(Json.encodeToString(d))
    }

    suspend fun doNext() {
        if (waiting.acquire || isPlaying) {
            return
        }
        waiting.set(true)
        logger.debug("doing next")
        try {
            withTimeout(1000 * 30 * 1) {
                val res = que.get()
                logger.debug("got $res from queue")
                sendPlay(res)
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

    suspend fun onTrackStop() {
        current = null
        que.consume()
        doNext()
    }

    suspend fun sendPlay(track: Track) {
        logger.debug("play invoked $id ${track.info.title}")
        current = track
        lastPosition = 0
        lastUpdate = 0

        val payload = Json.encodeToString(Playl(id.toString(), track.track, op = "play", noReplace = false))
        node.send(payload)
    }

    suspend fun sendPause(data: Pause) {
        logger.debug("sending pause ${data.pause} $id")
        val payload = Json.encodeToString(Pausel(op = "pause", guildId = id.toString(), data.pause))
        node.send(payload)
    }

    suspend fun sendSeek(data: Seek) {
        logger.debug("sending seek ${data.time / 1000 / 60}m $id")
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
            node.client.bestNodePlayers?.also { node = it }?.also { it.players[id] = this@Player }
        }
    }

    suspend fun sendCallback(data: NowPlaying) {
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