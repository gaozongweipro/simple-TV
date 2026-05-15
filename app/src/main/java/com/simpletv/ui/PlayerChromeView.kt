package com.simpletv.ui

import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.simpletv.channel.Channel

class PlayerChromeView(context: Context) : FrameLayout(context) {
    var onChannelClick: ((Channel) -> Unit)? = null
    var onRefreshClick: (() -> Unit)? = null
    var onResetClick: (() -> Unit)? = null
    var onRetryClick: (() -> Unit)? = null

    private var channels: List<Channel> = emptyList()
    private var selectedChannelId: String? = null

    private val drawerContent = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(10), dp(10), dp(10), dp(10))
    }

    private val drawer = ScrollView(context).apply {
        setBackgroundColor(Color.parseColor("#E6192028"))
        visibility = GONE
        isClickable = true
        isFillViewport = true
        addView(
            drawerContent,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )
    }

    private val status = TextView(context).apply {
        setTextColor(Color.WHITE)
        textSize = 14f
        setBackgroundColor(Color.parseColor("#99000000"))
        setPadding(dp(12), dp(8), dp(12), dp(8))
        includeFontPadding = false
        visibility = GONE
    }

    init {
        setBackgroundColor(Color.TRANSPARENT)
        isClickable = true
        setOnClickListener { toggleDrawer() }
        setOnLongClickListener {
            showSettings()
            true
        }
        addView(
            drawer,
            LayoutParams(drawerWidthPx(), LayoutParams.MATCH_PARENT, Gravity.END)
        )
        addView(
            status,
            LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.START or Gravity.BOTTOM).apply {
                leftMargin = dp(14)
                bottomMargin = dp(14)
            }
        )
    }

    fun setChannels(channels: List<Channel>, selectedChannelId: String?) {
        this.channels = channels
        this.selectedChannelId = selectedChannelId
        renderChannels()
    }

    fun toggleDrawer() {
        if (drawer.visibility == VISIBLE) {
            hideDrawer()
        } else {
            renderChannels()
            drawer.visibility = VISIBLE
        }
    }

    fun hideDrawer() {
        drawer.visibility = GONE
    }

    fun showStatus(message: String) {
        status.text = message
        status.visibility = VISIBLE
    }

    fun hideStatus() {
        status.visibility = GONE
    }

    fun showChannelFailed(channelName: String) {
        drawerContent.removeAllViews()
        drawerContent.addView(header(channelName))
        drawerContent.addView(labelRow("播放失败"))
        drawerContent.addView(actionRow("重试") { onRetryClick?.invoke() })
        drawerContent.addView(actionRow("切换频道") { renderChannels() })
        drawer.visibility = VISIBLE
    }

    private fun renderChannels() {
        drawerContent.removeAllViews()
        drawerContent.addView(header("频道"))
        channels.forEach { channel ->
            drawerContent.addView(channelRow(channel))
        }
        drawer.scrollTo(0, 0)
    }

    private fun showSettings() {
        drawerContent.removeAllViews()
        drawerContent.addView(header("设置"))
        drawerContent.addView(actionRow("刷新频道") { onRefreshClick?.invoke() })
        drawerContent.addView(actionRow("恢复默认") { onResetClick?.invoke() })
        drawer.visibility = VISIBLE
    }

    private fun header(text: String): TextView {
        return TextView(context).apply {
            this.text = text
            setTextColor(Color.WHITE)
            textSize = 15f
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, dp(8))
            includeFontPadding = false
            minHeight = dp(32)
        }
    }

    private fun channelRow(channel: Channel): TextView {
        return row(channel.name).apply {
            setBackgroundColor(
                if (channel.id == selectedChannelId) Color.parseColor("#2F6FED") else Color.TRANSPARENT
            )
            setOnClickListener {
                selectedChannelId = channel.id
                onChannelClick?.invoke(channel)
                hideDrawer()
            }
        }
    }

    private fun actionRow(text: String, action: () -> Unit): TextView {
        return row(text).apply {
            setOnClickListener {
                action()
            }
        }
    }

    private fun labelRow(text: String): TextView {
        return row(text).apply {
            alpha = 0.76f
            isEnabled = false
        }
    }

    private fun row(text: String): TextView {
        return TextView(context).apply {
            this.text = text
            textSize = 14f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER_VERTICAL
            minHeight = dp(44)
            includeFontPadding = false
            setPadding(dp(10), 0, dp(10), 0)
            layoutParams = LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, dp(44)).apply {
                bottomMargin = dp(4)
            }
        }
    }

    private fun drawerWidthPx(): Int {
        val width = resources.displayMetrics.widthPixels
        return if (width > 0) width / 3 else dp(266)
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
