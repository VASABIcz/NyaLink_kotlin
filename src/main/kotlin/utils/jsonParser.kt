package utils

import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

suspend inline fun <reified T> parse(data: String, parser: Json = Json): T? = coroutineScope {
    return@coroutineScope try {
        parser.decodeFromString<T>(data)
    } catch (t: Throwable) {
        println("failed to parse: $data\nerror: $t")
        null
    }
}