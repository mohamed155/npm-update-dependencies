package com.github.warningimhack3r.npmupdatedependencies.backend.extensions

import com.intellij.json.psi.JsonValue
import kotlinx.coroutines.*
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

fun <T> safeConversion(block: () -> T): T? = try {
    block()
} catch (_: IllegalArgumentException) {
    null
}

val JsonElement.asJsonObject
    get() = safeConversion { jsonObject }

val JsonElement.asJsonArray
    get() = safeConversion { jsonArray }

val JsonElement.asString
    get() = jsonPrimitive.contentOrNull

val JsonElement.asBoolean
    get() = jsonPrimitive.booleanOrNull

fun JsonValue.stringValue(): String = text.replace("\"", "")

// Credit: https://jivimberg.io/blog/2018/05/04/parallel-map-in-kotlin/
fun <T, R> Iterable<T>.parallelMap(mapper: suspend (T) -> R) = runBlocking(SupervisorJob() + Dispatchers.Default) {
    coroutineScope { map { async { mapper(it) } }.awaitAll() }
}

fun String.isBlankOrEmpty() = isBlank() || isEmpty()
