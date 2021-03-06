package io.ktor.experimental.client.redis

import io.ktor.experimental.client.redis.protocol.*
import io.ktor.experimental.client.redis.utils.*
import io.ktor.network.sockets.*
import io.ktor.utils.io.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import java.io.*
import java.nio.charset.*
import kotlin.coroutines.cancellation.CancellationException

internal class RedisRequest(val args: Any?, val result: CompletableDeferred<Any?>?)

private const val DEFAULT_PIPELINE_SIZE = 10

/**
 * Redis connection pipeline
 * https://redis.io/topics/pipelining
 */
internal class ConnectionPipeline(
    socket: Socket,
    private val requestQueue: Channel<RedisRequest>,
    private val password: String?,
    private val charset: Charset,
    pipelineSize: Int = DEFAULT_PIPELINE_SIZE,
    dispatcher: CoroutineDispatcher = Dispatchers.Default
) : Closeable {
    private val input = socket.openReadChannel()
    private val output = socket.openWriteChannel()

    val context: Job = GlobalScope.launch {

        try {
            password?.let { auth(it) }
        } catch (cause: Throwable) {

            requestQueue.consumeEach {
                it.result?.completeExceptionally(cause)
            }

            requestQueue.cancel(CancellationException(cause))
            throw cause
        }

        requestQueue.consumeEach { request ->
            if (request.result != null) {
                receiver.send(request.result)
            }

            if (request.args != null) {
                output.writePacket {
                    writeRedisValue(request.args, charset = charset)
                }
                output.flush()
            }
        }
    }

    private val receiver = GlobalScope.actor<CompletableDeferred<Any?>>(
        dispatcher + context, capacity = pipelineSize
    ) {
        val decoder = charset.newDecoder()!!

        consumeEach { result ->
            completeWith(result) {
                input.readRedisMessage(decoder)
            }
        }

        output.close()
        socket.close()
    }

    override fun close() {
        context.cancel()
    }

    private suspend fun auth(password: String) {
        output.writePacket {
            writeRedisValue(listOf("AUTH", password), charset = charset)
        }
        output.flush()

        input.readRedisMessage(charset.newDecoder())
    }
}
