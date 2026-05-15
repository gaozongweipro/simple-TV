package com.simpletv.channel

import android.content.SharedPreferences
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChannelRepositoryTest {
    private val prefs = FakeSharedPreferences()

    @Test
    fun cachedChannelsReturnsNullWhenCacheIsMissing() {
        val repository = repository()

        assertNull(repository.cachedChannels())
    }

    @Test
    fun cachedChannelsParsesStoredChannelJson() {
        prefs.edit()
            .putString(
                "channel_json",
                """{"version":3,"channels":[{"id":"cctv13","name":"CCTV-13","sources":[{"url":"https://example.com/cctv13.m3u8"}]}]}"""
            )
            .apply()
        val repository = repository()

        val cached = repository.cachedChannels()

        assertEquals(3, cached?.version)
        assertEquals("cctv13", cached?.channels?.single()?.id)
    }

    @Test
    fun cachedChannelsReturnsNullWhenStoredJsonIsInvalid() {
        prefs.edit().putString("channel_json", """{"version":1,"channels":[]}""").apply()
        val repository = repository()

        assertNull(repository.cachedChannels())
    }

    @Test
    fun defaultChannelsParsesBundledJson() {
        val repository = ChannelRepository(
            prefs = prefs,
            remoteUrl = "https://example.com/channels.json",
            defaultJsonProvider = {
                """{"version":9,"channels":[{"id":"cctv13","name":"CCTV-13","sources":[{"url":"https://example.com/cctv13.m3u8"}]}]}"""
            }
        )

        val defaults = repository.defaultChannels()

        assertEquals(9, defaults?.version)
        assertEquals("cctv13", defaults?.channels?.single()?.id)
    }

    @Test
    fun defaultChannelsReturnsNullWhenBundledJsonIsInvalid() {
        val repository = ChannelRepository(
            prefs = prefs,
            remoteUrl = "https://example.com/channels.json",
            defaultJsonProvider = { """{"version":1,"channels":[]}""" }
        )

        assertNull(repository.defaultChannels())
    }

    @Test
    fun localChannelsPrefersBundledDefaultsWhenTheyAreNewerThanCache() {
        prefs.edit()
            .putString(
                "channel_json",
                """{"version":1,"channels":[{"id":"old","name":"Old","sources":[{"url":"https://example.com/old.m3u8"}]}]}"""
            )
            .apply()
        val repository = ChannelRepository(
            prefs = prefs,
            remoteUrl = "https://example.com/channels.json",
            defaultJsonProvider = {
                """{"version":2,"channels":[{"id":"new","name":"New","sources":[{"url":"https://example.com/new.m3u8"}]}]}"""
            }
        )

        assertEquals("new", repository.localChannels()?.channels?.single()?.id)
    }

    @Test
    fun localChannelsKeepsCacheWhenCacheIsNewerThanBundledDefaults() {
        prefs.edit()
            .putString(
                "channel_json",
                """{"version":3,"channels":[{"id":"cached","name":"Cached","sources":[{"url":"https://example.com/cached.m3u8"}]}]}"""
            )
            .apply()
        val repository = ChannelRepository(
            prefs = prefs,
            remoteUrl = "https://example.com/channels.json",
            defaultJsonProvider = {
                """{"version":2,"channels":[{"id":"default","name":"Default","sources":[{"url":"https://example.com/default.m3u8"}]}]}"""
            }
        )

        assertEquals("cached", repository.localChannels()?.channels?.single()?.id)
    }

    @Test
    fun refreshChannelsStoresRawJsonAndVersion() = runBlocking {
        val body = """{"version":4,"channels":[{"id":"cctv1","name":"CCTV-1","sources":[{"url":"https://example.com/cctv1.m3u8"}]}]}"""
        val repository = ChannelRepository(
            prefs = prefs,
            remoteUrl = "https://example.com/channels.json",
            client = clientReturning(code = 200, body = body)
        )

        val result = repository.refreshChannels()

        assertTrue(result.isSuccess)
        assertEquals(4, result.getOrThrow().version)
        assertEquals(body, prefs.getString("channel_json", null))
        assertEquals(4, prefs.getInt("channel_version", -1))
    }

    @Test
    fun refreshChannelsReturnsFailureForHttpErrorAndKeepsCache() = runBlocking {
        prefs.edit()
            .putString(
                "channel_json",
                """{"version":1,"channels":[{"id":"old","name":"Old","sources":[{"url":"https://example.com/old.m3u8"}]}]}"""
            )
            .putInt("channel_version", 1)
            .apply()
        val repository = ChannelRepository(
            prefs = prefs,
            remoteUrl = "https://example.com/channels.json",
            client = clientReturning(code = 500, body = "server error")
        )

        val result = repository.refreshChannels()

        assertTrue(result.isFailure)
        assertEquals(1, repository.cachedChannels()?.version)
        assertEquals(1, prefs.getInt("channel_version", -1))
    }

    @Test
    fun refreshChannelsReturnsFailureForBlankBody() = runBlocking {
        val repository = ChannelRepository(
            prefs = prefs,
            remoteUrl = "https://example.com/channels.json",
            client = clientReturning(code = 200, body = " ")
        )

        val result = repository.refreshChannels()

        assertTrue(result.isFailure)
        assertNull(repository.cachedChannels())
    }

    @Test
    fun persistsLastChannelId() {
        val repository = repository()

        repository.saveLastChannelId("cctv1")

        assertEquals("cctv1", repository.lastChannelId())
    }

    @Test
    fun clearCacheClearsStoredChannelsAndLastChannelId() {
        val repository = repository()
        prefs.edit()
            .putString(
                "channel_json",
                """{"version":1,"channels":[{"id":"cctv1","name":"CCTV-1","sources":[{"url":"https://example.com/cctv1.m3u8"}]}]}"""
            )
            .putString("last_channel_id", "cctv1")
            .apply()

        repository.clearCache()

        assertNull(repository.cachedChannels())
        assertNull(repository.lastChannelId())
    }

    private fun repository(): ChannelRepository {
        return ChannelRepository(prefs = prefs, remoteUrl = "https://example.com/channels.json")
    }

    private fun clientReturning(code: Int, body: String): OkHttpClient {
        return OkHttpClient.Builder()
            .addInterceptor(Interceptor { chain ->
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(code)
                    .message(if (code in 200..299) "OK" else "Error")
                    .body(body.toResponseBody("application/json".toMediaType()))
                    .build()
            })
            .build()
    }

    private class FakeSharedPreferences : SharedPreferences {
        private val values = mutableMapOf<String, Any?>()

        override fun getString(key: String, defValue: String?): String? = values[key] as? String ?: defValue

        override fun edit(): SharedPreferences.Editor = FakeEditor(values)

        override fun contains(key: String): Boolean = values.containsKey(key)

        override fun getAll(): MutableMap<String, *> = values.toMutableMap()

        override fun getInt(key: String, defValue: Int): Int = values[key] as? Int ?: defValue

        override fun getLong(key: String, defValue: Long): Long = values[key] as? Long ?: defValue

        override fun getFloat(key: String, defValue: Float): Float = values[key] as? Float ?: defValue

        override fun getBoolean(key: String, defValue: Boolean): Boolean = values[key] as? Boolean ?: defValue

        override fun getStringSet(key: String, defValues: MutableSet<String>?): MutableSet<String>? {
            @Suppress("UNCHECKED_CAST")
            return values[key] as? MutableSet<String> ?: defValues
        }

        override fun registerOnSharedPreferenceChangeListener(
            listener: SharedPreferences.OnSharedPreferenceChangeListener?
        ) = Unit

        override fun unregisterOnSharedPreferenceChangeListener(
            listener: SharedPreferences.OnSharedPreferenceChangeListener?
        ) = Unit
    }

    private class FakeEditor(private val values: MutableMap<String, Any?>) : SharedPreferences.Editor {
        private val pending = mutableMapOf<String, Any?>()
        private var clear = false

        override fun putString(key: String, value: String?): SharedPreferences.Editor = apply {
            pending[key] = value
        }

        override fun putInt(key: String, value: Int): SharedPreferences.Editor = apply {
            pending[key] = value
        }

        override fun clear(): SharedPreferences.Editor = apply {
            clear = true
        }

        override fun apply() {
            commit()
        }

        override fun commit(): Boolean {
            if (clear) values.clear()
            pending.forEach { (key, value) ->
                if (value == null) {
                    values.remove(key)
                } else {
                    values[key] = value
                }
            }
            return true
        }

        override fun remove(key: String): SharedPreferences.Editor = apply {
            pending[key] = null
        }

        override fun putLong(key: String, value: Long): SharedPreferences.Editor = apply {
            pending[key] = value
        }

        override fun putFloat(key: String, value: Float): SharedPreferences.Editor = apply {
            pending[key] = value
        }

        override fun putBoolean(key: String, value: Boolean): SharedPreferences.Editor = apply {
            pending[key] = value
        }

        override fun putStringSet(key: String, values: MutableSet<String>?): SharedPreferences.Editor = apply {
            pending[key] = values
        }
    }
}
