package com.simpletv.playback

import com.simpletv.channel.ChannelSource

class SourceSelector(
    private val failureWindowMillis: Long = 5 * 60 * 1000L
) {
    private data class FailureKey(val channelId: String, val sourceUrl: String)

    private val failures = mutableMapOf<FailureKey, Long>()

    fun nextSource(channelId: String, sources: List<ChannelSource>, nowMillis: Long): ChannelSource? {
        return sources.firstOrNull { source ->
            val failedAt = failures[key(channelId, source.url)]
            failedAt == null || nowMillis - failedAt > failureWindowMillis
        }
    }

    fun markFailed(channelId: String, source: ChannelSource, nowMillis: Long) {
        failures[key(channelId, source.url)] = nowMillis
    }

    fun clearChannelFailures(channelId: String) {
        failures.keys.removeAll { it.channelId == channelId }
    }

    private fun key(channelId: String, sourceUrl: String): FailureKey = FailureKey(channelId, sourceUrl)
}
