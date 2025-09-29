package dev.rnett.gradle.mcp

import kotlin.io.path.Path
import kotlin.io.path.pathString
import kotlin.test.Test
import kotlin.test.assertEquals

class NewRootPathConverterTest {

    private val root = Path("/test-root")
    private val converter = NewRootPathConverter(root)

    @Test
    fun `converts Windows absolute paths under root with drive letter`() {
        val input = "C:\\Users\\me\\projects\\app"

        val converted = converter.convertPath(input)

        assertEquals("\\test-root\\c\\Users\\me\\projects\\app", converted.pathString)
    }

    // can't test linux paths because they don't show as absolute on windows

    @Test
    fun `leaves relative paths unchanged`() {
        val input = "some\\relative/path.txt" // mixed separators should still be treated as relative

        val converted = converter.convertPath(input)
        val expected = Path(input)

        assertEquals(expected.pathString, converted.pathString)
    }
}
