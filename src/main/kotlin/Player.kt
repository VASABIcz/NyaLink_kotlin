import commands.*
import kotlinx.coroutines.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import lavalink_commands.PlayerUpdate
import mu.KotlinLogging
import responses.TrackEnd
import responses.TrackStart
import to_lavlaink_commands.Destroy
import to_lavlaink_commands.Stop
import to_lavlaink_commands.VoiceUpdate
import trackloader.Track
import trackloader.TrackCallback
import utils.SyncedQue
import java.time.Instant
import java.util.concurrent.atomic.AtomicBoolean
import to_lavlaink_commands.Pause as Pausel
import to_lavlaink_commands.Play as Playl
import to_lavlaink_commands.Seek as Seekl

// TODO: 22/01/2022 small todo FIX THIS FUCKING MESS + TRACK-LOADER MESS :D

@Serializable
data class PlayerStatus(val queueSize: Int, val playing: Boolean)

@Serializable
data class PlayerTeardown(val guild: Long, val op: String)
data class Player(var node: Node, val id: Long) {
    private val waiting: AtomicBoolean = AtomicBoolean(false)

    @set:Synchronized
    private var loaderScope = CoroutineScope(Dispatchers.IO)
    private val logger = KotlinLogging.logger { }
    val que: SyncedQue<Track> = SyncedQue()

    @get:Synchronized
    @set:Synchronized
    private var session: VoiceStateUpdate? = null
    private var event: VoiceServerUpdate? = null
    val isPlaying: Boolean
        get() = session?.channel_id != null && current != null

    @get:Synchronized
    @set:Synchronized
    var current: Track? = null
        private set
    private var lastPosition = 0L
    private var lastUpdate = 0L

    val currentPosition: Long
        // FIXME looks like it works
        get() = lastPosition + (Instant.now().toEpochMilli() - lastUpdate)

    suspend fun teardown() {
        logger.debug("tearing down player")
        // this fixes bug that happens when bot disconnects and is still playing
        // after reconnect it will send play even that will replace old playing song
        // that will trigger onTrackStop and removing new song
        // TODO maybe ignore onTrackStop replaced event
        node.players.remove(id)
        stop()
        // FIXME this doesnt fix lavalink speeding up when moving
        // but it least clears lavalink internal player and recreates it
        // that fixes it
        destroy()
        loaderScope.cancel()
        // scope.newCoroutineContext()
        // loader.teardown()
        node.client.send(Json.encodeToString(PlayerTeardown(id, "playerTeardown")))
    }

    // hopefully it will be destroyed w player
    // it could be possible to create separate context for fetching and close it on clear / teardown
    suspend fun fetchTrack(data: Play) = loaderScope.launch {
        val track = node.client.loader.fetch(data)
            ?: return@launch logger.debug("player state playing: $isPlaying waiting: $waiting current session: $current ${session?.channel_id}")
        que.pushAll(track.tracks)
        doNext()
        logger.debug("player state playing: $isPlaying waiting: $waiting current session: $current ${session?.channel_id}")
    }

    suspend fun updateVoiceState(state: VoiceStateUpdate) {
        logger.info("voice state update $state")
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
        } else {
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
            withTimeout(1000 * 60 * 5) {
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

    suspend fun destroy() {
        node.send(Json.encodeToString(Destroy(guildId = id.toString(), op = "destroy")))
    }

    suspend fun onTrackStop() {
        logger.info("removing track: $current")
        current?.also {
            sendTrackEnd(it)
        }
        current = null
        que.consume()
        doNext()
    }

    suspend fun onTrackStart() {
        current?.also {
            sendTrackStart(it)
        }
    }

    suspend fun sendTrackStart(t: Track) {
        node.client.send(Json.encodeToString(TrackStart(t, id, "trackStart")))
    }

    suspend fun sendTrackEnd(t: Track) {
        node.client.send(Json.encodeToString(TrackEnd(t, id, "trackEnd")))
    }

    suspend fun sendPlay(track: Track, startTime: Long = 0) {
        logger.debug("play start time $startTime ms")
        // FIXME looks like it works
        logger.debug("play invoked $id ${track.info.title}")
        current = track
        lastPosition = 0
        lastUpdate = 0

        val payload = Json.encodeToString(
            Playl(
                id.toString(),
                track.track,
                op = "play",
                noReplace = false,
                startTime = startTime
            )
        )
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
        loaderScope.cancel()
        // FIXME might be source of bug
        loaderScope = CoroutineScope(Dispatchers.IO)
        que.clear()
        stop()
    }

    suspend fun move(identifier: String? = null) {
        // FIXME it works partially clean up
        node.players.remove(id)
        val node = if (identifier != null) {
            node.client.nodes[identifier]
        } else {
            node.client.bestPlayerNode ?: return
        }

        if (node == null) {
            logger.error("failed to move player $id $identifier node is null nodes: ${this.node.client.nodes}")
            return
        }

        this.node = node
        node.players[id] = this
        sendVoice()
        sendPlay(current!!, currentPosition)
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