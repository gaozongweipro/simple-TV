package com.simpletv.playback

import androidx.media3.common.MimeTypes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlaybackMediaItemTest {
    @Test
    fun detectsHlsUrlsWithQueryParameters() {
        assertEquals(
            MimeTypes.APPLICATION_M3U8,
            contentMimeTypeForUrl("https://example.com/live/index.m3u8?token=abc")
        )
    }

    @Test
    fun detectsDashUrls() {
        assertEquals(
            MimeTypes.APPLICATION_MPD,
            contentMimeTypeForUrl("https://example.com/live/manifest.mpd")
        )
    }

    @Test
    fun leavesDynamicUrlsForPlayerSniffing() {
        assertNull(contentMimeTypeForUrl("http://p.ytelc.com/169l/0/cctv.php?id=cctv9"))
    }
}
