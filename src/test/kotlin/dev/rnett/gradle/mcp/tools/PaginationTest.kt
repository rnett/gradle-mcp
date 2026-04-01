package dev.rnett.gradle.mcp.tools

import dev.rnett.gradle.mcp.mcp.McpServerComponent
import io.mockk.mockk
import org.junit.jupiter.api.Test
import kotlin.test.assertContains

class PaginationTest {

    private val component = mockk<McpServerComponent>()

    @Test
    fun `test paginate items from start`() {
        val items = listOf("a", "b", "c", "d", "e")
        val pagination = PaginationInput(offset = 1, limit = 2)

        val output = component.paginate(items, pagination, itemName = "letters")

        assertContains(output, "b")
        assertContains(output, "c")
        assertContains(output, "letters 2 to 3 of 5")
        assertContains(output, "To see more results, use: `offset=3`, `limit=2`")
    }

    @Test
    fun `test paginate items from end already paged`() {
        val items = listOf("d", "e") // last 2 of 5
        val pagination = PaginationInput(offset = 0, limit = 2)

        val output = component.paginate(items, pagination, itemName = "letters", total = 5, isAlreadyPaged = true, isTail = true)

        assertContains(output, "d")
        assertContains(output, "e")
        assertContains(output, "Showing last 2 letters of 5")
        assertContains(output, "To see more results, use: `offset=2`, `limit=2`")
    }

    @Test
    fun `test paginate items from end not already paged`() {
        val items = listOf("a", "b", "c", "d", "e")
        val pagination = PaginationInput(offset = 1, limit = 2)

        // total 5, offset 1 from end -> skip 'e'. limit 2 -> 'c', 'd'
        val output = component.paginate(items, pagination, itemName = "letters", isTail = true)

        assertContains(output, "c")
        assertContains(output, "d")
        assertContains(output, "letters 3 to 4 (from end) of 5")
        assertContains(output, "To see more results, use: `offset=3`, `limit=2`")
    }

    @Test
    fun `test paginate text by lines from start`() {
        val text = "line1\nline2\nline3\nline4\nline5"
        val pagination = PaginationInput(offset = 1, limit = 2)

        val output = component.paginateText(text, pagination, unit = PaginationUnit.LINES)

        assertContains(output, "line2")
        assertContains(output, "line3")
        assertContains(output, "lines 2 to 3 of 5")
    }

    @Test
    fun `test paginate text by lines from end`() {
        val text = "line1\nline2\nline3\nline4\nline5"
        val pagination = PaginationInput(offset = 1, limit = 2)

        // total 5, offset 1 from end -> skip 'line5'. limit 2 -> 'line3', 'line4'
        val output = component.paginateText(text, pagination, unit = PaginationUnit.LINES, isTail = true)

        assertContains(output, "line3")
        assertContains(output, "line4")
        assertContains(output, "lines 3 to 4 (from end) of 5")
        assertContains(output, "To see more results, use: `offset=3`, `limit=2`")
    }

    @Test
    fun `test paginate text by characters from start`() {
        val text = "abcdefghij"
        val pagination = PaginationInput(offset = 2, limit = 3)

        val output = component.paginateText(text, pagination, unit = PaginationUnit.CHARACTERS)

        assertContains(output, "cde")
        assertContains(output, "characters 3 to 5 of 10")
    }

    @Test
    fun `test paginate text by characters from end`() {
        val text = "abcdefghij"
        val pagination = PaginationInput(offset = 1, limit = 3)

        // total 10, offset 1 from end -> skip 'j'. limit 3 -> 'g', 'h', 'i'
        val output = component.paginateText(text, pagination, unit = PaginationUnit.CHARACTERS, isTail = true)

        assertContains(output, "ghi")
        assertContains(output, "characters 7 to 9 (from end) of 10")
        assertContains(output, "To see more results, use: `offset=4`, `limit=3`")
    }
}
