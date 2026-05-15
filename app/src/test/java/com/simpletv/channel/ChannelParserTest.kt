package com.simpletv.channel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChannelParserTest {
    private val parser = ChannelParser()

    @Test
    fun parsesValidChannelList() {
        val result = parser.parse(
            """
            {
              "version": 1,
              "updatedAt": "2026-05-15T00:00:00+08:00",
              "channels": [
                {
                  "id": "cctv13",
                  "name": "CCTV-13 新闻",
                  "group": "央视",
                  "sources": [
                    { "url": "https://example.com/cctv13.m3u8", "label": "主源" },
                    { "url": "https://example.com/cctv13-bak.m3u8", "label": "备用源" }
                  ]
                }
              ]
            }
            """.trimIndent()
        )

        assertEquals(1, result.version)
        assertEquals("2026-05-15T00:00:00+08:00", result.updatedAt)
        assertEquals("cctv13", result.channels.single().id)
        assertEquals("CCTV-13 新闻", result.channels.single().name)
        assertEquals("央视", result.channels.single().group)
        assertEquals(2, result.channels.single().sources.size)
        assertEquals("主源", result.channels.single().sources.first().label)
    }

    @Test
    fun ignoresUnknownFields() {
        val result = parser.parse(
            """
            {
              "version": 2,
              "unknown": "ignored",
              "channels": [
                {
                  "id": "cctv1",
                  "name": "CCTV-1 综合",
                  "extra": true,
                  "sources": [{ "url": "https://example.com/cctv1.m3u8" }]
                }
              ]
            }
            """.trimIndent()
        )

        assertEquals(2, result.version)
        assertEquals("cctv1", result.channels.single().id)
        assertNull(result.channels.single().sources.single().label)
    }

    @Test
    fun rejectsEmptyChannelList() {
        val error = runCatching {
            parser.parse("""{"version":1,"channels":[]}""")
        }.exceptionOrNull()

        assertTrue(error is IllegalArgumentException)
        assertEquals("Channel list must contain at least one channel.", error!!.message)
    }

    @Test
    fun rejectsChannelWithoutSources() {
        val error = runCatching {
            parser.parse("""{"version":1,"channels":[{"id":"cctv13","name":"CCTV-13","sources":[]}]}""")
        }.exceptionOrNull()

        assertTrue(error is IllegalArgumentException)
        assertEquals("Channel cctv13 must contain at least one source.", error!!.message)
    }

    @Test
    fun rejectsNonHttpSourceUrl() {
        val error = runCatching {
            parser.parse("""{"version":1,"channels":[{"id":"cctv13","name":"CCTV-13","sources":[{"url":"file:///tmp/a.m3u8"}]}]}""")
        }.exceptionOrNull()

        assertTrue(error is IllegalArgumentException)
        assertEquals("Source url for channel cctv13 must be http or https.", error!!.message)
    }

    @Test
    fun rejectsBlankId() {
        val error = runCatching {
            parser.parse("""{"version":1,"channels":[{"id":"  ","name":"CCTV-13","sources":[{"url":"https://example.com/cctv13.m3u8"}]}]}""")
        }.exceptionOrNull()

        assertTrue(error is IllegalArgumentException)
        assertEquals("Channel id must not be empty.", error!!.message)
    }

    @Test
    fun rejectsBlankName() {
        val error = runCatching {
            parser.parse("""{"version":1,"channels":[{"id":"cctv13","name":"  ","sources":[{"url":"https://example.com/cctv13.m3u8"}]}]}""")
        }.exceptionOrNull()

        assertTrue(error is IllegalArgumentException)
        assertEquals("Channel cctv13 name must not be empty.", error!!.message)
    }

    @Test
    fun trimsRequiredStrings() {
        val result = parser.parse(
            """{"version":1,"channels":[{"id":" cctv13 ","name":" CCTV-13 ","sources":[{"url":" https://example.com/cctv13.m3u8 "}]}]}"""
        )

        assertEquals("cctv13", result.channels.single().id)
        assertEquals("CCTV-13", result.channels.single().name)
        assertEquals("https://example.com/cctv13.m3u8", result.channels.single().sources.single().url)
    }

    @Test
    fun optionalBlankGroupAndLabelBecomeNull() {
        val result = parser.parse(
            """{"version":1,"channels":[{"id":"cctv13","name":"CCTV-13","group":"  ","sources":[{"url":"https://example.com/cctv13.m3u8","label":"  "}]}]}"""
        )

        assertNull(result.channels.single().group)
        assertNull(result.channels.single().sources.single().label)
    }

    @Test
    fun rejectsNonStringId() {
        val error = runCatching {
            parser.parse("""{"version":1,"channels":[{"id":13,"name":"CCTV-13","sources":[{"url":"https://example.com/cctv13.m3u8"}]}]}""")
        }.exceptionOrNull()

        assertTrue(error is IllegalArgumentException)
        assertEquals("Field id must be a string.", error!!.message)
    }

    @Test
    fun rejectsNonStringOptionalField() {
        val error = runCatching {
            parser.parse("""{"version":1,"channels":[{"id":"cctv13","name":"CCTV-13","group":13,"sources":[{"url":"https://example.com/cctv13.m3u8"}]}]}""")
        }.exceptionOrNull()

        assertTrue(error is IllegalArgumentException)
        assertEquals("Field group must be a string.", error!!.message)
    }

    @Test
    fun rejectsNonObjectRoot() {
        val error = runCatching {
            parser.parse("""[]""")
        }.exceptionOrNull()

        assertTrue(error is IllegalArgumentException)
        assertEquals("Channel list root must be a JSON object.", error!!.message)
    }

    @Test
    fun rejectsNonObjectSourceItem() {
        val error = runCatching {
            parser.parse("""{"version":1,"channels":[{"id":"cctv13","name":"CCTV-13","sources":["https://example.com/cctv13.m3u8"]}]}""")
        }.exceptionOrNull()

        assertTrue(error is IllegalArgumentException)
        assertEquals("Source item for channel cctv13 must be a JSON object.", error!!.message)
    }

    @Test
    fun rejectsNonObjectChannelItem() {
        val error = runCatching {
            parser.parse("""{"version":1,"channels":["cctv13"]}""")
        }.exceptionOrNull()

        assertTrue(error is IllegalArgumentException)
        assertEquals("Channel item must be a JSON object.", error!!.message)
    }

    @Test
    fun rejectsNonIntegerVersion() {
        val error = runCatching {
            parser.parse("""{"version":"1","channels":[{"id":"cctv13","name":"CCTV-13","sources":[{"url":"https://example.com/cctv13.m3u8"}]}]}""")
        }.exceptionOrNull()

        assertTrue(error is IllegalArgumentException)
        assertEquals("Field version must be an integer.", error!!.message)
    }

    @Test
    fun rejectsMissingVersion() {
        val error = runCatching {
            parser.parse("""{"channels":[{"id":"cctv13","name":"CCTV-13","sources":[{"url":"https://example.com/cctv13.m3u8"}]}]}""")
        }.exceptionOrNull()

        assertTrue(error is IllegalArgumentException)
        assertEquals("Missing required field: version.", error!!.message)
    }

    @Test
    fun rejectsMissingRequiredField() {
        val error = runCatching {
            parser.parse("""{"version":1,"channels":[{"id":"cctv13","sources":[{"url":"https://example.com/cctv13.m3u8"}]}]}""")
        }.exceptionOrNull()

        assertTrue(error is IllegalArgumentException)
        assertEquals("Missing required field: name.", error!!.message)
    }
}
