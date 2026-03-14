package dev.rnett.gradle.mcp.mcp

import dev.rnett.gradle.mcp.fixtures.mcp.BaseMcpServerTest
import dev.rnett.gradle.mcp.tools.PaginationInput
import dev.rnett.gradle.mcp.tools.PaginationUnit
import dev.rnett.gradle.mcp.tools.paginate
import dev.rnett.gradle.mcp.tools.paginateText
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class PaginationTest : BaseMcpServerTest() {

    private val component = object : McpServerComponent("test", "test") {}

    @Test
    fun `paginate slices correctly`() = runTest {
        val items = listOf("a", "b", "c", "d", "e")
        val pagination = PaginationInput(offset = 1, limit = 2)
        val result = component.paginate(items, pagination, "items")

        val lines = result.lines()
        assertEquals("b", lines[0])
        assertEquals("c", lines[1])
        assertTrue(result.contains("Showing items 2 to 3 of 5"))
        assertTrue(result.contains("offset=3"))
    }

    @Test
    fun `paginate handles out of bounds offset`() = runTest {
        val items = listOf("a", "b")
        val pagination = PaginationInput(offset = 5, limit = 2)
        val result = component.paginate(items, pagination, "items")

        assertTrue(result.contains("No items found at the requested offset"))
        assertTrue(result.contains("Total items: 2"))
    }

    @Test
    fun `paginate handles empty list`() = runTest {
        val items = emptyList<String>()
        val pagination = PaginationInput(offset = 0, limit = 2)
        val result = component.paginate(items, pagination, "items")

        assertEquals("No items found.", result)
    }

    @Test
    fun `paginateText by lines`() = runTest {
        val text = "line1\nline2\nline3\nline4"
        val pagination = PaginationInput(offset = 1, limit = 2)
        val result = component.paginateText(text, pagination, PaginationUnit.LINES)

        assertTrue(result.contains("line2"))
        assertTrue(result.contains("line3"))
        assertTrue(result.contains("Showing lines 2 to 3 of 4"))
    }

    @Test
    fun `paginateText by characters`() = runTest {
        val text = "abcdefghij"
        val pagination = PaginationInput(offset = 2, limit = 3)
        val result = component.paginateText(text, pagination, PaginationUnit.CHARACTERS)

        assertTrue(result.startsWith("cde"))
        assertTrue(result.contains("Showing characters 3 to 5 of 10"))
    }

    @Test
    fun `paginate displays end of list suffix`() = runTest {
        val items = listOf("a", "b", "c")
        val pagination = PaginationInput(offset = 1, limit = 5)
        val result = component.paginate(items, pagination, "items")

        assertTrue(result.contains("Showing items 2 to 3 of 3 (End of list)"))
    }

    @Test
    fun `PaginationInput validation`() = runTest {
        assertFailsWith<IllegalArgumentException> {
            PaginationInput(offset = -1)
        }
        assertFailsWith<IllegalArgumentException> {
            PaginationInput(limit = 0)
        }
    }
}
