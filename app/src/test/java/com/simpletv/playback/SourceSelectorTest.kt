package com.simpletv.playback

import com.simpletv.channel.ChannelSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SourceSelectorTest {
    @Test
    fun choosesFirstHealthySource() {
        val selector = SourceSelector(failureWindowMillis = 300_000)
        val sources = listOf(
            ChannelSource("https://example.com/a.m3u8", "A"),
            ChannelSource("https://example.com/b.m3u8", "B")
        )

        assertEquals("https://example.com/a.m3u8", selector.nextSource("cctv13", sources, nowMillis = 1_000)?.url)
    }

    @Test
    fun skipsRecentlyFailedSource() {
        val selector = SourceSelector(failureWindowMillis = 300_000)
        val sources = listOf(
            ChannelSource("https://example.com/a.m3u8", "A"),
            ChannelSource("https://example.com/b.m3u8", "B")
        )

        selector.markFailed("cctv13", sources[0], nowMillis = 1_000)

        assertEquals("https://example.com/b.m3u8", selector.nextSource("cctv13", sources, nowMillis = 2_000)?.url)
    }

    @Test
    fun returnsNullWhenAllSourcesRecentlyFailed() {
        val selector = SourceSelector(failureWindowMillis = 300_000)
        val sources = listOf(
            ChannelSource("https://example.com/a.m3u8", "A"),
            ChannelSource("https://example.com/b.m3u8", "B")
        )

        selector.markFailed("cctv13", sources[0], nowMillis = 1_000)
        selector.markFailed("cctv13", sources[1], nowMillis = 1_000)

        assertNull(selector.nextSource("cctv13", sources, nowMillis = 2_000))
    }

    @Test
    fun retryClearsChannelFailures() {
        val selector = SourceSelector(failureWindowMillis = 300_000)
        val sources = listOf(ChannelSource("https://example.com/a.m3u8", "A"))
        selector.markFailed("cctv13", sources[0], nowMillis = 1_000)
        selector.clearChannelFailures("cctv13")

        assertEquals("https://example.com/a.m3u8", selector.nextSource("cctv13", sources, nowMillis = 2_000)?.url)
    }

    @Test
    fun sourceBecomesEligibleAfterFailureWindow() {
        val selector = SourceSelector(failureWindowMillis = 300_000)
        val sources = listOf(ChannelSource("https://example.com/a.m3u8", "A"))
        selector.markFailed("cctv13", sources[0], nowMillis = 1_000)

        assertEquals("https://example.com/a.m3u8", selector.nextSource("cctv13", sources, nowMillis = 301_001)?.url)
    }

    @Test
    fun failuresAreScopedByChannel() {
        val selector = SourceSelector(failureWindowMillis = 300_000)
        val shared = ChannelSource("https://example.com/a.m3u8", "A")
        selector.markFailed("cctv13", shared, nowMillis = 1_000)

        assertNull(selector.nextSource("cctv13", listOf(shared), nowMillis = 2_000))
        assertEquals("https://example.com/a.m3u8", selector.nextSource("cctv1", listOf(shared), nowMillis = 2_000)?.url)
    }

    @Test
    fun failureKeyDoesNotCollideWhenValuesContainDelimiter() {
        val selector = SourceSelector(failureWindowMillis = 300_000)
        val failedSource = ChannelSource("b|c", "A")
        val otherSource = ChannelSource("c", "B")

        selector.markFailed("a", failedSource, nowMillis = 1_000)

        assertEquals("c", selector.nextSource("a|b", listOf(otherSource), nowMillis = 2_000)?.url)
    }

    @Test
    fun clearChannelFailuresDoesNotClearPrefixedChannelIds() {
        val selector = SourceSelector(failureWindowMillis = 300_000)
        val source = ChannelSource("https://example.com/a.m3u8", "A")
        selector.markFailed("a", source, nowMillis = 1_000)
        selector.markFailed("a|b", source, nowMillis = 1_000)

        selector.clearChannelFailures("a")

        assertEquals("https://example.com/a.m3u8", selector.nextSource("a", listOf(source), nowMillis = 2_000)?.url)
        assertNull(selector.nextSource("a|b", listOf(source), nowMillis = 2_000))
    }

    @Test
    fun sourceRemainsUnavailableAtExactFailureWindowBoundary() {
        val selector = SourceSelector(failureWindowMillis = 300_000)
        val source = ChannelSource("https://example.com/a.m3u8", "A")
        selector.markFailed("cctv13", source, nowMillis = 1_000)

        assertNull(selector.nextSource("cctv13", listOf(source), nowMillis = 301_000))
    }

    @Test
    fun returnsNullForEmptySourceList() {
        val selector = SourceSelector(failureWindowMillis = 300_000)

        assertNull(selector.nextSource("cctv13", emptyList(), nowMillis = 1_000))
    }
}
