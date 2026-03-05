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
@Description("Pagination parameters. Offset is the zero-based starting index (defaults to 0). Limit is the maximum number of items/lines to return.")
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
    total: Int = items.size,
    isAlreadyPaged: Boolean = false,
    formatter: (T) -> String = { it.toString() }
): String {
    val offset = pagination.offset
    val limit = pagination.limit

    McpToolHelper.logger.debug("Paginating {}: offset={}, limit={}, total={}", itemName, offset, limit, total)

    val paged = if (isAlreadyPaged) items else items.drop(offset).take(limit)
    val end = (offset + paged.size).coerceAtMost(total)

    val content = if (paged.isEmpty() && total > 0 && offset >= total) {
        "No $itemName found at the requested offset. Total $itemName: $total."
    } else if (paged.isEmpty()) {
        "No $itemName found."
    } else {
        paged.joinToString("\n") { formatter(it) }
    }

    val metadata = if (total > 0 && (end < total || offset > 0)) {
        val suffix = if (end >= total) " (End of list)" else ""
        """

---
Pagination: Showing $itemName ${offset + 1} to $end of $total$suffix.
To see more results, use: `offset=${end}`, `limit=$limit`.
---
""".trimIndent()
    } else ""

    return "$content$metadata"
}

/**
 * Standardized pagination utility for raw text.
 *
 * @param text The full text to paginate.
 * @param pagination The [PaginationInput] defining offset and limit.
 * @param unit The [PaginationUnit] (lines or characters) to use for slicing.
 * @return A string containing the paginated text segment and LLM-optimized metadata.
 */
fun McpServerComponent.paginateText(
    text: String,
    pagination: PaginationInput,
    unit: PaginationUnit = PaginationUnit.LINES
): String {
    val offset = pagination.offset
    val limit = pagination.limit

    McpToolHelper.logger.debug("Paginating text by {}: offset={}, limit={}, length={}", unit, offset, limit, text.length)

    return when (unit) {
        PaginationUnit.LINES -> {
            val lines = text.lines()
            paginate(lines, pagination, "lines")
        }

        PaginationUnit.CHARACTERS -> {
            val total = text.length
            val endPos = (offset + limit).coerceAtMost(total)
            val paged = if (offset < total) text.substring(offset, endPos) else ""
            val end = (offset + paged.length).coerceAtMost(total)

            val metadata = if (total > 0 && (end < total || offset > 0)) {
                val suffix = if (end >= total) " (End of text)" else ""
                """

---
Pagination: Showing characters ${offset + 1} to $end of $total$suffix.
To see more results, use: `offset=${end}`, `limit=$limit`.
---
""".trimIndent()
            } else ""

            "$paged$metadata"
        }

        else -> throw IllegalArgumentException("Unsupported pagination unit: $unit")
    }
}
