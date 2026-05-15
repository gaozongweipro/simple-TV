package com.simpletv

import android.app.Activity
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.SurfaceView
import android.widget.FrameLayout
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import com.simpletv.channel.Channel
import com.simpletv.channel.ChannelRepository
import com.simpletv.playback.TvPlayerController
import com.simpletv.ui.PlayerChromeView
import kotlin.math.roundToInt
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class MainActivity : Activity() {
    private val mainScope = MainScope()
    private lateinit var repository: ChannelRepository
    private lateinit var controller: TvPlayerController
    private lateinit var chrome: PlayerChromeView
    private var channels: List<Channel> = emptyList()
    private var currentVideoSize: VideoSize = VideoSize.UNKNOWN

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        hideSystemUi()

        repository = ChannelRepository(this, getString(R.string.default_channel_url))
        controller = TvPlayerController(this).apply {
            onChannelFailed = { channel ->
                runOnUiThread {
                    chrome.showChannelFailed(channel.name)
                }
            }
        }
        chrome = PlayerChromeView(this).apply {
            onChannelClick = { channel ->
                repository.saveLastChannelId(channel.id)
                setChannels(channels, channel.id)
                controller.play(channel, clearFailures = true)
            }
            onRefreshClick = { refreshChannelsFromUser() }
            onResetClick = {
                repository.clearCache()
                refreshChannelsFromUser()
            }
            onRetryClick = {
                controller.currentChannel?.let { channel ->
                    controller.play(channel, clearFailures = true)
                }
            }
        }

        val videoHost = FrameLayout(this).apply {
            setBackgroundColor(android.graphics.Color.BLACK)
        }
        val surfaceView = SurfaceView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
                Gravity.CENTER
            )
        }
        videoHost.addView(surfaceView)
        videoHost.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            updateVideoSurfaceBounds(videoHost, surfaceView)
        }
        controller.player.setVideoSurfaceView(surfaceView)
        controller.player.addListener(object : Player.Listener {
            override fun onVideoSizeChanged(videoSize: VideoSize) {
                currentVideoSize = videoSize
                videoHost.post { updateVideoSurfaceBounds(videoHost, surfaceView) }
            }
        })
        setContentView(
            FrameLayout(this).apply {
                addView(
                    videoHost,
                    FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT
                    )
                )
                addView(
                    chrome,
                    FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT
                    )
                )
            }
        )

        val initial = repository.localChannels()
        initial?.let { startFromList(it.channels) } ?: chrome.showStatus("正在刷新频道")
        mainScope.launch {
            repository.refreshChannels()
                .onSuccess {
                    chrome.hideStatus()
                    startFromList(it.channels)
                }
                .onFailure {
                    if (initial == null) {
                        chrome.showStatus("暂无频道清单，长按刷新")
                    }
                }
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            hideSystemUi()
        }
    }

    private fun startFromList(newChannels: List<Channel>) {
        if (newChannels.isEmpty()) return

        channels = newChannels
        val preferred = repository.lastChannelId()
        val channel = channels.firstOrNull { it.id == preferred } ?: channels.first()
        repository.saveLastChannelId(channel.id)
        chrome.setChannels(channels, channel.id)
        controller.play(channel)
    }

    private fun updateVideoSurfaceBounds(videoHost: FrameLayout, surfaceView: SurfaceView) {
        val hostWidth = videoHost.width
        val hostHeight = videoHost.height
        if (hostWidth <= 0 || hostHeight <= 0) return

        val videoWidth = currentVideoSize.width
        val videoHeight = currentVideoSize.height
        val videoRatio = if (videoWidth > 0 && videoHeight > 0) {
            videoWidth * currentVideoSize.pixelWidthHeightRatio / videoHeight
        } else {
            DEFAULT_VIDEO_RATIO
        }
        val hostRatio = hostWidth.toFloat() / hostHeight
        val targetWidth: Int
        val targetHeight: Int
        if (hostRatio > videoRatio) {
            targetHeight = hostHeight
            targetWidth = (targetHeight * videoRatio).roundToInt()
        } else {
            targetWidth = hostWidth
            targetHeight = (targetWidth / videoRatio).roundToInt()
        }

        val current = surfaceView.layoutParams as? FrameLayout.LayoutParams
        if (current?.width == targetWidth && current.height == targetHeight && current.gravity == Gravity.CENTER) {
            return
        }
        surfaceView.layoutParams = FrameLayout.LayoutParams(targetWidth, targetHeight, Gravity.CENTER)
    }

    private fun refreshChannelsFromUser() {
        mainScope.launch {
            chrome.showStatus("正在刷新频道")
            repository.refreshChannels()
                .onSuccess {
                    chrome.hideStatus()
                    startFromList(it.channels)
                }
                .onFailure {
                    val fallback = repository.defaultChannels()
                    if (channels.isEmpty() && fallback != null) {
                        chrome.hideStatus()
                        startFromList(fallback.channels)
                    } else if (channels.isEmpty()) {
                        chrome.showStatus("暂无频道清单，长按刷新")
                    } else {
                        chrome.showStatus("频道刷新失败，继续使用本地缓存")
                    }
                }
        }
    }

    private fun hideSystemUi() {
        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
    }

    override fun onDestroy() {
        controller.release()
        mainScope.cancel()
        super.onDestroy()
    }

    private companion object {
        private const val DEFAULT_VIDEO_RATIO = 16f / 9f
    }
}
