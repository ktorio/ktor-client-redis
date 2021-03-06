package io.ktor.experimental.client.util

import io.ktor.utils.io.core.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import java.util.concurrent.atomic.*
import kotlin.coroutines.*

class PipelineElement<TRequest : Any, TResponse : Any>(
    val request: TRequest,
    val response: CompletableDeferred<TResponse>
)

class RequestContext<TResponse : Any>(val response: CompletableDeferred<TResponse>, val callContext: CoroutineScope)

abstract class ConnectionPipeline<TRequest : Any, TResponse : Any>(
    source: ReceiveChannel<PipelineElement<TRequest, TResponse>>,
    pipelineSize: Int = 10,
    override val coroutineContext: CoroutineContext
) : CoroutineScope, Closeable {
    private val started = AtomicBoolean(false)

    private val writer: Job = launch {
        try {
            onStart()

            source.consumeEach { element ->
                val callContext = createCallContext()
                try {
                    reader.send(RequestContext(element.response, callContext))
                } catch (cause: Throwable) {
                    element.response.completeExceptionally(cause)
                    throw cause
                }

                send(callContext, element.request)
            }
        } catch (cause: Throwable) {
            reader.close(cause)
            onError(cause)
        } finally {
            reader.close()
            silent { onDone() }
        }
    }

    private val reader: SendChannel<RequestContext<TResponse>> = actor(
        capacity = pipelineSize,
        start = CoroutineStart.LAZY
    ) {
        for (element in channel) {
            element.response.completeWith {
                this@ConnectionPipeline.receive(element.callContext)
            }

            (element.callContext.coroutineContext[Job] as CompletableDeferred<Unit>).apply {
                complete(Unit)
                join()
            }
        }
    }

    private fun createCallContext(): CoroutineScope =
        CoroutineScope(coroutineContext + CompletableDeferred<Unit>(coroutineContext[Job]))

    protected abstract suspend fun send(callScope: CoroutineScope, request: TRequest)

    protected abstract suspend fun receive(callScope: CoroutineScope): TResponse

    protected open suspend fun onStart() {}
    protected open fun onDone() {}
    protected open fun onError(cause: Throwable) {}
}

class PipelineException(val request: Any, override val cause: Throwable) : RuntimeException() {
    override val message: String = "Fail to execute: $request"
}

private inline fun silent(block: () -> Unit) = try { block() } catch (_: Throwable) {}
