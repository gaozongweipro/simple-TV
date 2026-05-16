package com.simpletv.playback

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.BehindLiveWindowException
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import com.simpletv.channel.Channel
import com.simpletv.channel.ChannelSource

class TvPlayerController(
    context: Context,
    private val sourceSelector: SourceSelector = SourceSelector(),
    private val nowMillis: () -> Long = { System.currentTimeMillis() }
) {
    private data class LiveWindowRetryKey(val channelId: String, val sourceUrl: String)

    private val trackSelector = DefaultTrackSelector(context).apply {
        parameters = buildUponParameters()
            .setMaxVideoSize(MAX_VIDEO_WIDTH, MAX_VIDEO_HEIGHT)
            .setMaxVideoBitrate(MAX_VIDEO_BITRATE)
            .build()
    }
    private val httpDataSourceFactory = DefaultHttpDataSource.Factory()
        .setUserAgent(HTTP_USER_AGENT)
        .setDefaultRequestProperties(
            mapOf(
                "Accept" to "*/*",
                "Connection" to "keep-alive"
            )
        )
    private val mediaSourceFactory = DefaultMediaSourceFactory(httpDataSourceFactory)
        .setLiveTargetOffsetMs(LIVE_TARGET_OFFSET_MS)
        .setLiveMinOffsetMs(LIVE_MIN_OFFSET_MS)
        .setLiveMaxOffsetMs(LIVE_MAX_OFFSET_MS)
        .setLiveMinSpeed(LIVE_MIN_SPEED)
        .setLiveMaxSpeed(LIVE_MAX_SPEED)
    private val renderersFactory = DefaultRenderersFactory(context)
        .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)
        .setEnableDecoderFallback(true)

    val player: ExoPlayer = ExoPlayer.Builder(context)
        .setRenderersFactory(renderersFactory)
        .setTrackSelector(trackSelector)
        .setMediaSourceFactory(mediaSourceFactory)
        .setLoadControl(
            DefaultLoadControl.Builder()
                .setBufferDurationsMs(
                    MIN_BUFFER_MS,
                    MAX_BUFFER_MS,
                    BUFFER_FOR_PLAYBACK_MS,
                    BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS
                )
                .build()
        )
        .build()
    var currentChannel: Channel? = null
        private set
    private var currentSource: ChannelSource? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private val liveWindowRetries = mutableMapOf<LiveWindowRetryKey, Long>()
    private val stallRetries = mutableMapOf<LiveWindowRetryKey, Long>()
    private val bufferingTimeout = Runnable { recoverFromStall() }
    var onChannelFailed: ((Channel) -> Unit)? = null

    init {
        player.setAudioAttributes(
            AudioAttributes.Builder()
                .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                .setUsage(C.USAGE_MEDIA)
                .build(),
            true
        )
        player.volume = 1f
        player.addListener(object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                val channel = currentChannel ?: return
                val source = currentSource ?: return
                if (error.cause is BehindLiveWindowException) {
                    Log.w(TAG, "Behind live window on ${channel.id}: ${source.url}")
                    recoverFromBehindLiveWindow(channel, source)
                    return
                }
                Log.w(TAG, "Playback error on ${channel.id}: ${source.url}", error)
                failCurrentSourceAndPlayNext(channel, source)
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_BUFFERING) {
                    mainHandler.removeCallbacks(bufferingTimeout)
                    mainHandler.postDelayed(bufferingTimeout, BUFFERING_TIMEOUT_MS)
                } else {
                    mainHandler.removeCallbacks(bufferingTimeout)
                }
            }
        })
    }

    fun play(channel: Channel, clearFailures: Boolean = false) {
        if (clearFailures) {
            sourceSelector.clearChannelFailures(channel.id)
        }
        currentChannel = channel
        val source = sourceSelector.nextSource(channel.id, channel.sources, nowMillis())
        if (source == null) {
            Log.w(TAG, "All sources failed for ${channel.id}")
            onChannelFailed?.invoke(channel)
            return
        }

        currentSource = source
        Log.i(TAG, "Playing ${channel.id}: ${source.label ?: source.url}")
        val mediaItem = MediaItem.Builder()
            .setUri(source.url)
            .setMimeType(contentMimeTypeForUrl(source.url))
            .build()
        player.setMediaItem(mediaItem)
        player.prepare()
        player.playWhenReady = true
    }

    fun release() {
        mainHandler.removeCallbacksAndMessages(null)
        player.release()
    }

    private fun recoverFromBehindLiveWindow(channel: Channel, source: ChannelSource) {
        if (channel.sources.size > 1) {
            Log.w(TAG, "Live window expired, switching source for ${channel.id}: ${source.url}")
            failCurrentSourceAndPlayNext(channel, source)
            return
        }

        val now = nowMillis()
        val key = LiveWindowRetryKey(channel.id, source.url)
        val lastRetryAt = liveWindowRetries[key]
        if (lastRetryAt == null || now - lastRetryAt > LIVE_WINDOW_RETRY_WINDOW_MS) {
            liveWindowRetries[key] = now
            Log.i(TAG, "Refreshing live position for ${channel.id}: ${source.url}")
            player.seekToDefaultPosition()
            player.prepare()
            player.playWhenReady = true
            return
        }

        Log.w(TAG, "Live window failed again, switching source for ${channel.id}: ${source.url}")
        liveWindowRetries.remove(key)
        failCurrentSourceAndPlayNext(channel, source)
    }

    private fun recoverFromStall() {
        if (player.playbackState != Player.STATE_BUFFERING) return
        val channel = currentChannel ?: return
        val source = currentSource ?: return
        val now = nowMillis()
        val key = LiveWindowRetryKey(channel.id, source.url)
        val lastRetryAt = stallRetries[key]
        if (lastRetryAt == null || now - lastRetryAt > LIVE_WINDOW_RETRY_WINDOW_MS) {
            stallRetries[key] = now
            Log.w(TAG, "Buffering timeout, refreshing live position for ${channel.id}: ${source.url}")
            player.seekToDefaultPosition()
            player.prepare()
            player.playWhenReady = true
            return
        }

        stallRetries.remove(key)
        if (channel.sources.size > 1) {
            Log.w(TAG, "Repeated buffering timeout, switching source for ${channel.id}: ${source.url}")
            failCurrentSourceAndPlayNext(channel, source)
            return
        }

        Log.w(TAG, "Repeated buffering timeout, refreshing single source for ${channel.id}: ${source.url}")
        player.seekToDefaultPosition()
        player.prepare()
        player.playWhenReady = true
    }

    private fun failCurrentSourceAndPlayNext(channel: Channel, source: ChannelSource) {
        mainHandler.removeCallbacks(bufferingTimeout)
        sourceSelector.markFailed(channel.id, source, nowMillis())
        Log.i(TAG, "Marked source failed for ${channel.id}: ${source.url}")
        play(channel, clearFailures = false)
    }

    private companion object {
        private const val TAG = "SimpleTvPlayer"
        private const val MIN_BUFFER_MS = 10_000
        private const val MAX_BUFFER_MS = 30_000
        private const val BUFFER_FOR_PLAYBACK_MS = 1_500
        private const val BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS = 3_000
        private const val LIVE_TARGET_OFFSET_MS = 12_000L
        private const val LIVE_MIN_OFFSET_MS = 8_000L
        private const val LIVE_MAX_OFFSET_MS = 20_000L
        private const val LIVE_MIN_SPEED = 0.97f
        private const val LIVE_MAX_SPEED = 1.03f
        private const val BUFFERING_TIMEOUT_MS = 15_000L
        private const val LIVE_WINDOW_RETRY_WINDOW_MS = 60_000L
        private const val MAX_VIDEO_WIDTH = 1280
        private const val MAX_VIDEO_HEIGHT = 720
        private const val MAX_VIDEO_BITRATE = 2_500_000
        private const val HTTP_USER_AGENT =
            "Mozilla/5.0 (Linux; Android 8.1; SimpleTV) AppleWebKit/537.36 Chrome/120 Mobile Safari/537.36"
    }
}

internal fun contentMimeTypeForUrl(url: String): String? {
    val normalized = url.substringBefore('#').substringBefore('?').lowercase()
    return when {
        normalized.endsWith(".m3u8") -> MimeTypes.APPLICATION_M3U8
        normalized.endsWith(".mpd") -> MimeTypes.APPLICATION_MPD
        normalized.endsWith(".ism") || normalized.endsWith(".isml") -> MimeTypes.APPLICATION_SS
        else -> null
    }
}
