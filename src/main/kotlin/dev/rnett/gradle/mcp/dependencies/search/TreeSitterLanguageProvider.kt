package dev.rnett.gradle.mcp.dependencies.search

import io.github.treesitter.ktreesitter.Language
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import java.lang.foreign.Arena
import java.lang.foreign.FunctionDescriptor
import java.lang.foreign.Linker
import java.lang.foreign.MemorySegment
import java.lang.foreign.SymbolLookup
import java.lang.foreign.ValueLayout
import java.util.concurrent.ConcurrentHashMap

/**
 * A globally-rooted scope for native Tree-sitter memory.
 * 
 * **CRITICAL LIFECYCLE RATIONALE:**
 * This global object is strictly necessary to prevent `EXCEPTION_ACCESS_VIOLATION` native crashes during highly parallel testing.
 * 
 * 1. **JDK 22 FFM API constraints**: `SymbolLookup.libraryLookup(Path, Arena)` maps a native `.dll`/`.so` library to memory. That library is instantly unmapped and unloaded from the OS the moment its `Arena` is closed.
 * 2. **`ktreesitter` cleanup conflict**: `Parser` and `Query` objects allocate native memory but do NOT expose an explicit `close()` method. Instead, they rely on Java's implicit `Cleaner` (garbage collection finalizer) to eventually invoke their native `delete` functions (`ts_parser_delete`, `ts_query_delete`).
 * 3. **The Race Condition**: If this `Arena` were scoped to the Koin test lifecycle (or closed manually on test teardown), the `.dll` would be instantly unloaded while `Parser`/`Query` objects still float in the JVM heap waiting for GC. When the GC finally triggers their `Cleaner`, the `Cleaner` tries to execute the native `delete` function from an unloaded library, causing a fatal hard crash.
 * 4. **The Solution**: By using `Arena.ofAuto()` inside a static/global `object`, the `Arena` is mathematically guaranteed to only be collected upon full JVM shutdown. This perfectly matches the JDK's intended handling for implicit `Cleaner`-based native resources, ensuring the native library outlives all `Parser`/`Query` instances.
 */
object GlobalTreeSitterNativeScope {
    val arena = Arena.ofAuto()
    val cache = ConcurrentHashMap<String, Language>()
    val mutexes = ConcurrentHashMap<String, Mutex>()
}

/**
 * Manages cached native Tree-sitter [Language] pointers.
 */
class TreeSitterLanguageProvider(
    private val downloader: ParserDownloader
) {
    private val logger = LoggerFactory.getLogger(TreeSitterLanguageProvider::class.java)

    suspend fun getLanguage(name: String): Language {
        GlobalTreeSitterNativeScope.cache[name]?.let { return it }
        return GlobalTreeSitterNativeScope.mutexes.computeIfAbsent(name) { Mutex() }.withLock {
            GlobalTreeSitterNativeScope.cache[name] ?: loadLanguage(name).also { GlobalTreeSitterNativeScope.cache[name] = it }
        }
    }

    private suspend fun loadLanguage(name: String): Language = withContext(Dispatchers.IO) {
        logger.info("Loading native Tree-sitter library for '$name'...")
        val libPath = downloader.ensureLanguage(name)
        val lib = try {
            SymbolLookup.libraryLookup(libPath, GlobalTreeSitterNativeScope.arena)
        } catch (e: Throwable) {
            logger.debug("Failed to load native library at $libPath")
            throw RuntimeException("Failed to load native library for '$name'", e)
        }

        val cSymbol = TreeSitterUtils.getCSymbol(name)
        val funcName = "tree_sitter_$cSymbol"
        val funcSegment: MemorySegment = lib.find(funcName)
            .orElseThrow {
                logger.debug("Symbol $funcName not found in $libPath")
                Exception("Symbol $funcName not found in native library for '$name'")
            }

        val linker = Linker.nativeLinker()
        val methodHandle = linker.downcallHandle(funcSegment, FunctionDescriptor.of(ValueLayout.ADDRESS))
        val languagePointer = try {
            methodHandle.invokeExact() as MemorySegment
        } catch (e: Throwable) {
            logger.debug("Failed to invoke $funcName in $libPath")
            throw RuntimeException("Failed to invoke $funcName in native library for '$name'", e)
        }

        Language(languagePointer.address())
    }
}
