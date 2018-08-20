package io.ktor.experimental.client.redis

import kotlinx.coroutines.experimental.*
import kotlinx.coroutines.experimental.channels.*

enum class SortDirection { ASC, DESC }

suspend fun Redis.executeBinary(vararg args: Any?): Any? = execute(*args)
suspend fun Redis.executeText(vararg args: Any?): Any? = execute(*args).byteArraysToString

@Suppress("UNCHECKED_CAST")
suspend fun Redis.commandArrayString(vararg args: Any?): List<String> =
    (executeText(*args) as List<Any?>?)?.filterIsInstance<String>() ?: listOf()

suspend fun Redis.commandArrayAny(vararg args: Any?): List<Any?> =
    (executeText(*args) as List<Any?>?) ?: listOf()

suspend fun Redis.commandArrayStringNull(vararg args: Any?): List<String?> =
    (executeText(*args) as List<Any?>?) as? List<String?> ?: listOf()

@Suppress("UNCHECKED_CAST")
suspend fun Redis.commandArrayLong(vararg args: Any?): List<Long> =
    (executeText(*args) as List<Long>?) ?: listOf()

suspend fun Redis.commandString(vararg args: Any?): String? = executeText(*args)?.toString()
suspend fun Redis.commandByteArray(vararg args: Any?): ByteArray? = execute(*args) as? ByteArray?
suspend fun Redis.commandLong(vararg args: Any?): Long = executeText(*args)?.toString()?.toLongOrNull() ?: 0L
suspend fun Redis.commandInt(vararg args: Any?): Int = executeText(*args)?.toString()?.toIntOrNull() ?: 0
suspend fun Redis.commandDouble(vararg args: Any?): Double = executeText(*args)?.toString()?.toDoubleOrNull() ?: 0.0
suspend fun Redis.commandDoubleOrNull(vararg args: Any?): Double? = executeText(*args)?.toString()?.toDoubleOrNull()
suspend fun Redis.commandUnit(vararg args: Any?): Unit = run { execute(*args) }
suspend fun Redis.commandBool(vararg args: Any?): Boolean = commandLong(*args) != 0L

internal fun <T> List<T>.toListOfPairs(): List<Pair<T, T>> =
    (0 until size / 2).map { this[it * 2 + 0] to this[it * 2 + 1] }

internal fun <T> List<T>.toListOfPairsString(): List<Pair<String, String>> =
    (0 until size / 2).map { ("${this[it * 2 + 0]}") to ("${this[it * 2 + 1]}") }

internal fun List<Any?>.listOfPairsToMap(): Map<String, String> =
    (0 until size / 2).map { ("${this[it * 2 + 0]}") to ("${this[it * 2 + 1]}") }.toMap()

private val UTF8 = Charsets.UTF_8

private val Any?.byteArraysToString: Any?
    get() = when (this) {
        is ByteArray -> this.toString(UTF8)
        is List<*> -> { // @TODO: Copy only on different instances
            this.map { it.byteArraysToString }.toList()
        }
        is Map<*, *> -> { // @TODO: Copy only on different instances
            this.map { it.key.byteArraysToString to it.value.byteArraysToString }.toMap()
        }
        else -> this
    }

internal inline fun <reified T : Any> arrayOfNotNull(vararg items: T?): Array<T> = listOfNotNull(*items).toTypedArray()

data class RedisScanStepResult(val nextCursor: Long, val items: List<String>)

internal suspend fun Redis.scanBaseStep(
    cmd: String,
    key: String?,
    cursor: Long,
    pattern: String? = null,
    count: Int? = null
): RedisScanStepResult {
    val result = commandArrayAny(*arrayListOf<Any?>().apply {
        this += cmd
        if (key != null) {
            this += key
        }
        this += cursor
        if (pattern != null) {
            this += "PATTERN"
            this += pattern
        }
        if (count != null) {
            this += "COUNT"
            this += count
        }
    }.toTypedArray())
    return RedisScanStepResult(result[0].toString().toLong(), result[1] as List<String>)
}

internal suspend fun Redis.scanBase(
    cmd: String, key: String?,
    pattern: String? = null, count: Int? = null,
    pairs: Boolean = false
): ReceiveChannel<Any> {
    val channel = Channel<Any>((count ?: 10) * 2)
    launch {
        try {
            var cursor = 0L
            do {
                val result = scanBaseStep(cmd, key, cursor, pattern, count)
                cursor = result.nextCursor
                val items = result.items
                if (pairs) {
                    for (n in 0 until items.size step 2) {
                        channel.send(items[n + 0] to items[n + 1])
                    }
                } else {
                    for (item in items) {
                        channel.send(item)
                    }
                }
            } while (cursor > 0L)
        } finally {
            channel.close()
        }
    }
    return channel
}

internal suspend fun Redis.scanBaseString(
    cmd: String, key: String?, pattern: String? = null, count: Int? = null
): ReceiveChannel<String> = scanBase(cmd, key, pattern, count, pairs = false) as Channel<String>

internal suspend fun Redis.scanBasePairs(
    cmd: String, key: String?, pattern: String? = null, count: Int? = null
): ReceiveChannel<Pair<String, String>> = scanBase(cmd, key, pattern, count, pairs = true) as Channel<Pair<String, String>>
