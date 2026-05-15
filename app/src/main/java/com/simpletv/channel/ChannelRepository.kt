package com.simpletv.channel

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

class ChannelRepository {
    private val prefs: SharedPreferences
    private val remoteUrl: String
    private val parser: ChannelParser
    private val client: OkHttpClient
    private val defaultJsonProvider: (() -> String?)?

    constructor(
        context: Context,
        remoteUrl: String,
        parser: ChannelParser = ChannelParser(),
        client: OkHttpClient = OkHttpClient()
    ) : this(
        prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE),
        remoteUrl = remoteUrl,
        parser = parser,
        client = client,
        defaultJsonProvider = {
            context.applicationContext.assets.open(DEFAULT_CHANNELS_ASSET)
                .bufferedReader()
                .use { it.readText() }
        }
    )

    internal constructor(
        prefs: SharedPreferences,
        remoteUrl: String,
        parser: ChannelParser = ChannelParser(),
        client: OkHttpClient = OkHttpClient(),
        defaultJsonProvider: (() -> String?)? = null
    ) {
        this.prefs = prefs
        this.remoteUrl = remoteUrl
        this.parser = parser
        this.client = client
        this.defaultJsonProvider = defaultJsonProvider
    }

    fun cachedChannels(): ChannelList? {
        val raw = prefs.getString(KEY_CHANNEL_JSON, null) ?: return null
        return runCatching { parser.parse(raw) }.getOrNull()
    }

    fun defaultChannels(): ChannelList? {
        val raw = defaultJsonProvider?.invoke() ?: return null
        return runCatching { parser.parse(raw) }.getOrNull()
    }

    fun localChannels(): ChannelList? {
        val cached = cachedChannels()
        val defaults = defaultChannels()
        return when {
            cached == null -> defaults
            defaults == null -> cached
            defaults.version > cached.version -> defaults
            else -> cached
        }
    }

    suspend fun refreshChannels(): Result<ChannelList> = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder().url(remoteUrl).get().build()
            val response = client.newCall(request).execute()
            response.use {
                require(it.isSuccessful) { "Channel request failed: HTTP ${it.code}" }
                val body = requireNotNull(it.body?.string()) { "Channel response body is empty." }
                require(body.isNotBlank()) { "Channel response body is empty." }
                val parsed = parser.parse(body)
                prefs.edit()
                    .putString(KEY_CHANNEL_JSON, body)
                    .putInt(KEY_CHANNEL_VERSION, parsed.version)
                    .apply()
                parsed
            }
        }
    }

    fun lastChannelId(): String? = prefs.getString(KEY_LAST_CHANNEL_ID, null)

    fun saveLastChannelId(channelId: String) {
        prefs.edit().putString(KEY_LAST_CHANNEL_ID, channelId).apply()
    }

    fun clearCache() {
        prefs.edit().clear().apply()
    }

    companion object {
        private const val PREFS_NAME = "simpletv_channels"
        private const val KEY_CHANNEL_JSON = "channel_json"
        private const val KEY_CHANNEL_VERSION = "channel_version"
        private const val KEY_LAST_CHANNEL_ID = "last_channel_id"
        private const val DEFAULT_CHANNELS_ASSET = "default_channels.json"
    }
}
