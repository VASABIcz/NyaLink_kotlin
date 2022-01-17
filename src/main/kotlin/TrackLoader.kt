import kotlinx.coroutines.channels.Channel

/*
create source based executors
spotify

semaphore based node extraction

response message found spotify album/track/...
class TrackResult
    message: source based
    tracks: [Track]

class Track
    generic track data
    generic track message

class Que
    queue: [Track]

1. recive track
2. check if track is in cache
    1. add it to cache executor or create task idk based on lib i will use
    2. add it to rest queue
3. is not null, chack if message should be sent poll message, add to cache, add to player
 */

data class TrackJob(val name: String, val player: Player)

data class Track(val name: String) // TODO: 17/01/2022

class TrackLoader(val client: Client) {
    val channel = Channel<TrackJob>()
    var closed = false


    suspend fun send(track: TrackJob) {
        channel.send(track)
    }

    fun restLoader(item: TrackJob) {
        // TODO: 16/01/2022
        /*
        get best node
        get track
        put it to desired class
        send it to player
         */
    }

    suspend fun work() {
        while (!closed) {
            try {
                channel.receive().also {  }
            }
            catch (_: Throwable) {
                closed = true
            }
        }
    }

    fun teardown() {
        closed = true
    }
}