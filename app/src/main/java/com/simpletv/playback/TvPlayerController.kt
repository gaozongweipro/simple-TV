package com.simpletv.playback

import android.content.Context
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import com.simpletv.channel.Channel
import com.simpletv.channel.ChannelSource

class TvPlayerController(
    context: Context,
    private val sourceSelector: SourceSelector = SourceSelector(),
    private val nowMillis: () -> Long = { System.currentTimeMillis() }
) {
    val player: ExoPlayer = ExoPlayer.Builder(context)
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
                sourceSelector.markFailed(channel.id, source, nowMillis())
                play(channel, clearFailures = false)
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
            onChannelFailed?.invoke(channel)
            return
        }

        currentSource = source
        val mediaItem = MediaItem.Builder()
            .setUri(source.url)
            .setMimeType(MimeTypes.APPLICATION_M3U8)
            .build()
        player.setMediaItem(mediaItem)
        player.prepare()
        player.playWhenReady = true
    }

    fun release() {
        player.release()
    }

    private companion object {
        private const val MIN_BUFFER_MS = 15_000
        private const val MAX_BUFFER_MS = 45_000
        private const val BUFFER_FOR_PLAYBACK_MS = 2_500
        private const val BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS = 5_000
    }
}
