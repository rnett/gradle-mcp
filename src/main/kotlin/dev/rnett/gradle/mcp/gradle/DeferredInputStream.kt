package dev.rnett.gradle.mcp.gradle

import kotlinx.coroutines.Deferred
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.slf4j.LoggerFactory
import java.io.InputStream
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration

class DeferredInputStream(val timeout: Duration, val stream: Deferred<InputStream>) : InputStream() {
    private val LOGGER = LoggerFactory.getLogger(DeferredInputStream::class.java)

    private var awaited: InputStream? = null

    @OptIn(ExperimentalAtomicApi::class)
    @Synchronized
    private fun awaitStream(): InputStream? {
        if (awaited != null) {
            return awaited
        }
        return runBlocking {
            withTimeoutOrNull(timeout) {
                try {
                    stream.await().also {
                        awaited = it
                    }
                } catch (e: Throwable) {
                    if (e is CancellationException)
                        LOGGER.debug("Deferred stream's deferred source was cancelled")
                    else
                        LOGGER.error("Failed to await deferred stream: ${e.message}", e)
                    null
                }
            }
        }
    }

    override fun available(): Int {
        return awaited?.available() ?: 1
    }

    override fun read(): Int {
        return awaitStream()?.read() ?: -1
    }

    override fun read(b: ByteArray?, off: Int, len: Int): Int {
        return awaitStream()?.read(b, off, len) ?: -1
    }

    override fun readNBytes(b: ByteArray?, off: Int, len: Int): Int {
        return awaitStream()?.readNBytes(b, off, len) ?: -1
    }
}