package dev.rnett.gradle.mcp

import kotlin.test.Test

class TestTest {
    @Test
    fun success() {
        println("test")

    }

    @Test
    fun fail() {
        println("test failure")
        assert(2 == 4)
    }
}