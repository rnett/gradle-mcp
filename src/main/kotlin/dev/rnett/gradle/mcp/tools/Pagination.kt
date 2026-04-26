package dev.rnett.gradle.mcp.tools

import dev.rnett.gradle.mcp.mcp.McpServerComponent
import dev.rnett.gradle.mcp.mcp.McpToolHelper
import io.github.smiley4.schemakenerator.core.annotations.Description
import kotlinx.serialization.Serializable

/**
 * Standardized pagination input for MCP tools.
 *
 * Use [offset] to specify the number of items to skip and [limit] to control
 * the maximum number of items returned in a single call.
 */
@Serializable
@Description("Pagination. offset = zero-based start index (default 0); limit = max items/lines to return.")
data class PaginationInput(
    val offset: Int = 0,
    val limit: Int = 20
) {
    init {
        require(offset >= 0) { "Offset must be greater than or equal to 0, but was $offset" }
        require(limit >= 1) { "Limit must be greater than or equal to 1, but was $limit" }
    }

    companion object {
        /**
         * Default pagination for collections (limit 20).
         */
        val DEFAULT_ITEMS = PaginationInput(limit = 20)

        /**
         * Default pagination for text lines (limit 100).
         */
        val DEFAULT_LINES = PaginationInput(limit = 100)
    }
}

/**
 * The unit used for text pagination.
 */
@Serializable
enum class PaginationUnit {
    /**
     * Paginate text by line count. Ideal for source code or structured logs.
     */
    LINES,

    /**
     * Paginate text by raw character count. Ideal for large unstructured documentation.
     */
    CHARACTERS
}

/**
 * Standardized pagination utility for collections.
 *
 * @param items The list of items to paginate.
 * @param pagination The [PaginationInput] defining offset and limit.
 * @param itemName The plural name of the items (e.g., "dependencies") used in metadata.
 * @param total The total number of items available (defaults to [items] size).
 * @param isAlreadyPaged If true, the [items] list is assumed to already be a slice, and further slicing is skipped.
 * @param formatter A function to convert each item to a string representation.
 * @return A string containing the paginated items and LLM-optimized metadata.
 */
fun <T> McpServerComponent.paginate(
    items: List<T>,
    pagination: PaginationInput,
    itemName: String = "items",
    total: Int? = null,
    isAlreadyPaged: Boolean = false,
    hasMore: Boolean? = null,
    isTail: Boolean = false,
    formatter: (T) -> String = { it.toString() }
): String = try {
    val offset = pagination.offset
    val limit = pagination.limit

    McpToolHelper.logger.debug("Paginating {}: offset={}, limit={}, total={}, hasMore={}, isTail={}", itemName, offset, limit, total, hasMore, isTail)

    val calculatedTotal = total ?: if (isAlreadyPaged) null else items.size
    val paged = if (isAlreadyPaged) items else items.let {
        if (isTail) it.takeLast(offset + limit).take(limit)
        else it.drop(offset).take(limit)
    }
    val end = offset + paged.size

    val content = if (paged.isEmpty() && calculatedTotal != null && calculatedTotal > 0 && offset >= calculatedTotal) {
        "No $itemName found at the requested offset. Total $itemName: $calculatedTotal."
    } else if (paged.isEmpty()) {
        "No $itemName found."
    } else {
        paged.joinToString("\n") { formatter(it) }
    }

    val actualHasMore = hasMore ?: (calculatedTotal != null && end < calculatedTotal)
    val metadata = if ((actualHasMore || offset > 0 || isTail) && (offset != 0 || limit != Int.MAX_VALUE)) {
        val range = if (isTail && calculatedTotal != null) {
            val tailStart = (calculatedTotal - offset - paged.size).coerceAtLeast(0) + 1
            val tailEnd = (calculatedTotal - offset).coerceAtLeast(0)
            if (offset == 0) "last ${paged.size} $itemName"
            else "$itemName $tailStart to $tailEnd (from end)"
        } else if (isTail) {
            "last ${paged.size} $itemName"
        } else {
            "$itemName ${offset + 1} to $end"
        }
        val suffix = if (!actualHasMore && !isTail) " (End of list)" else ""
        val totalSuffix = if (calculatedTotal != null) " of $calculatedTotal" else ""
        """

---
Pagination: Showing $range$totalSuffix$suffix.
${if (actualHasMore || (isTail && offset + paged.size < (calculatedTotal ?: 0))) "To see more results, use: `offset=${offset + paged.size}`, `limit=$limit`.\n" else ""}---
""".trimIndent()
    } else ""

    "$content$metadata"
} catch (e: Throwable) {
    McpToolHelper.logger.error("Error in paginate: ${e.message}", e)
    throw e
}

/**
 * Standardized pagination utility for raw text.
 *
 * @param text The full text to paginate.
 * @param pagination The [PaginationInput] defining offset and limit.
 * @param unit The [PaginationUnit] (lines or characters) to use for slicing.
 * @param isTail If true, the metadata indicates that the text is the tail of the source.
 * @return A string containing the paginated text segment and LLM-optimized metadata.
 */
fun McpServerComponent.paginateText(
    text: String,
    pagination: PaginationInput,
    unit: PaginationUnit = PaginationUnit.LINES,
    isTail: Boolean = false
): String = try {
    val offset = pagination.offset
    val limit = pagination.limit

    McpToolHelper.logger.debug("Paginating text by {}: offset={}, limit={}, length={}, isTail={}", unit, offset, limit, text.length, isTail)

    when (unit) {
        PaginationUnit.LINES -> {
            val lines = text.lines()
            val total = lines.size
            if (isTail) {
                val start = (total - offset - limit).coerceAtLeast(0)
                val end = (total - offset).coerceAtLeast(0)
                val paged = lines.subList(start, end)
                paginate(paged, pagination, "lines", total = total, isAlreadyPaged = true, isTail = true)
            } else {
                paginate(lines, pagination, "lines")
            }
        }

        PaginationUnit.CHARACTERS -> {
            val total = text.length
            val (start, end) = if (isTail) {
                val start = (total - offset - limit).coerceAtLeast(0)
                val end = (total - offset).coerceAtLeast(0)
                start to end
            } else {
                val start = offset.coerceAtMost(total)
                val end = (offset + limit).coerceAtMost(total)
                start to end
            }

            val paged = if (start < total) text.substring(start, end) else ""
            val range = if (isTail) {
                if (offset == 0) "last ${paged.length} characters"
                else "characters ${start + 1} to $end (from end)"
            } else {
                "characters ${start + 1} to $end"
            }

            val hasMore = if (isTail) start > 0 else end < total
            val metadata = if (total > 0 && (hasMore || offset > 0 || isTail) && (offset != 0 || limit != Int.MAX_VALUE)) {
                val suffix = if (!hasMore && !isTail) " (End of text)" else ""
                """

---
Pagination: Showing $range of $total$suffix.
${if (hasMore) "To see more results, use: `offset=${if (isTail) total - start else end}`, `limit=$limit`.\n" else ""}---
""".trimIndent()
            } else ""

            "$paged$metadata"
        }

        else -> throw IllegalArgumentException("Unsupported pagination unit: $unit")
    }
} catch (e: Throwable) {
    McpToolHelper.logger.error("Error in paginateText: ${e.message}", e)
    throw e
}

