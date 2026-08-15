package dev.rnett.gradle.mcp.utils

import java.io.InputStream

/**
 * An [InputStream] wrapper that strips a single leading UTF-8 byte-order mark (BOM, the bytes
 * `EF BB BF`) from the underlying stream. Any other leading bytes pass through untouched, and a
 * partial BOM prefix (e.g. a stream that starts with only `EF`, `EF BB`, or `EF BB 00`) is
 * preserved rather than discarded.
 *
 * Detection happens lazily on the first read: up to 3 bytes are read from the delegate once, and
 * are either consumed as an exact BOM or buffered and replayed before any later delegate bytes.
 * All standard read entry points ([read], [read] overloads, [readNBytes], and anything built on
 * top of them) go through this single probe, so the BOM is stripped exactly once regardless of
 * how the consumer reads the stream.
 */
class BomStrippingInputStream(private val delegate: InputStream) : InputStream() {

    private var started = false

    /** Prefix bytes read during BOM probing that must be replayed before any delegate bytes. */
    private var prefix: ByteArray? = null
    private var prefixPos = 0

    private fun ensureStarted() {
        if (started) return
        started = true

        val probe = ByteArray(BOM.size)
        var total = 0
        while (total < probe.size) {
            val n = delegate.read(probe, total, probe.size - total)
            if (n == -1) break
            if (n == 0) continue
            total += n
        }

        val isBom = total == BOM.size &&
            probe[0] == BOM[0] &&
            probe[1] == BOM[1] &&
            probe[2] == BOM[2]

        prefix = if (isBom) null else probe.copyOf(total).takeIf { it.isNotEmpty() }
        prefixPos = 0
    }

    private fun readFromPrefix(): Int {
        val p = prefix ?: return -1
        if (prefixPos >= p.size) return -1
        val b = p[prefixPos].toInt() and 0xFF
        prefixPos++
        return b
    }

    override fun read(): Int {
        ensureStarted()
        val b = readFromPrefix()
        if (b != -1) return b
        return delegate.read()
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        ensureStarted()
        if (len == 0) return 0

        var total = 0
        val p = prefix
        if (p != null && prefixPos < p.size) {
            total = minOf(len, p.size - prefixPos)
            p.copyInto(b, off, prefixPos, prefixPos + total)
            prefixPos += total
            if (total == len) return total
        }

        val dn = delegate.read(b, off + total, len - total)
        if (dn == -1) return if (total > 0) total else -1
        return total + dn
    }

    override fun readNBytes(b: ByteArray, off: Int, len: Int): Int {
        ensureStarted()
        if (len == 0) return 0

        var total = 0
        val p = prefix
        if (p != null && prefixPos < p.size) {
            val n = minOf(len, p.size - prefixPos)
            p.copyInto(b, off, prefixPos, prefixPos + n)
            prefixPos += n
            total = n
            if (total == len) return total
        }

        return total + delegate.readNBytes(b, off + total, len - total)
    }

    override fun available(): Int {
        ensureStarted()
        val p = prefix
        val pending = if (p != null) p.size - prefixPos else 0
        return pending + delegate.available()
    }

    override fun skip(n: Long): Long {
        ensureStarted()
        var remaining = n
        val p = prefix
        if (p != null && prefixPos < p.size) {
            val canSkip = (p.size - prefixPos).toLong()
            val skipped = minOf(remaining, canSkip)
            prefixPos += skipped.toInt()
            remaining -= skipped
            if (remaining <= 0) return n
        }
        return n - remaining + delegate.skip(remaining)
    }

    override fun close() {
        delegate.close()
    }

    private companion object {
        val BOM = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())
    }
}
