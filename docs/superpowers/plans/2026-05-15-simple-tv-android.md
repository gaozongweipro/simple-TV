# SimpleTV Android Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a lightweight Android 8 compatible touchscreen live TV app for 800x400 landscape Xiaomi Xiaoai touch speakers.

**Architecture:** Native Android Kotlin app with a single landscape `MainActivity`, Media3 ExoPlayer for HLS playback, remote JSON channel updates with local cache fallback, and a compact right-side touch drawer. Domain logic is split into small model, repository, playback, and UI units so source switching and cache behavior can be tested outside the Activity.

**Tech Stack:** Android Gradle Plugin, Kotlin, AndroidX AppCompat/Core, Media3 ExoPlayer, OkHttp, kotlinx.serialization, JUnit.

---

## File Structure

- `settings.gradle.kts`: Gradle project registration.
- `build.gradle.kts`: Root Gradle plugin versions.
- `gradle.properties`: Android build options, including local `aapt2` override for this Windows development host.
- `gradlew`, `gradlew.bat`, `gradle/wrapper/gradle-wrapper.jar`, `gradle/wrapper/gradle-wrapper.properties`: Gradle wrapper pinned to a locally cached Gradle 8.x distribution.
- `app/build.gradle.kts`: Android app configuration, minSdk 26, Kotlin, and baseline tests. Media3, OkHttp, and JSON dependencies are added in the tasks that first use them.
- `app/src/main/AndroidManifest.xml`: Landscape `MainActivity`, internet permission, Android 8 compatibility.
- `app/src/main/java/com/simpletv/MainActivity.kt`: Single Activity, wires repository, player controller, and touch UI.
- `app/src/main/java/com/simpletv/channel/ChannelModels.kt`: Serializable channel JSON model and validated domain model.
- `app/src/main/java/com/simpletv/channel/ChannelParser.kt`: Parse and validate remote `channels.json`.
- `app/src/main/java/com/simpletv/channel/ChannelRepository.kt`: Remote fetch, cache read/write, last channel persistence.
- `app/src/main/java/com/simpletv/playback/SourceSelector.kt`: Source fallback and short failure blacklist logic.
- `app/src/main/java/com/simpletv/playback/TvPlayerController.kt`: Media3 player wrapper, channel switching, error callback wiring.
- `app/src/main/java/com/simpletv/ui/PlayerChromeView.kt`: Fullscreen player overlay, compact right-side channel drawer, settings panel.
- `app/src/main/res/values/strings.xml`: App name and default remote channel URL.
- `app/src/main/res/values/colors.xml`: Restrained dark player UI colors.
- `app/src/main/res/values/styles.xml`: Fullscreen no-action-bar app theme.
- `app/src/test/java/com/simpletv/channel/ChannelParserTest.kt`: Channel JSON validation tests.
- `app/src/test/java/com/simpletv/playback/SourceSelectorTest.kt`: Backup source switching tests.
- `sample/channels.json`: Local example channel list for manual testing.

## Task 1: Android Project Skeleton

**Files:**
- Create: `settings.gradle.kts`
- Create: `build.gradle.kts`
- Create: `gradle.properties`
- Create: `gradlew`
- Create: `gradlew.bat`
- Create: `gradle/wrapper/gradle-wrapper.jar`
- Create: `gradle/wrapper/gradle-wrapper.properties`
- Create: `app/build.gradle.kts`
- Create: `app/src/main/AndroidManifest.xml`
- Create: `app/src/main/res/values/strings.xml`
- Create: `app/src/main/res/values/colors.xml`
- Create: `app/src/main/res/values/styles.xml`
- Create: `.gitignore`

- [ ] **Step 1: Create Gradle project files**

`settings.gradle.kts`:

```kotlin
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "SimpleTV"
include(":app")
```

`build.gradle.kts`:

```kotlin
buildscript {
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        classpath("com.android.tools.build:gradle:8.13.1")
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.1.0")
    }
}
```

`app/build.gradle.kts`:

```kotlin
apply(plugin = "com.android.application")
apply(plugin = "org.jetbrains.kotlin.android")

android {
    namespace = "com.simpletv"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.simpletv"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
}
```

- [ ] **Step 2: Add manifest and resources**

`app/src/main/AndroidManifest.xml`:

```xml
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <uses-permission android:name="android.permission.INTERNET" />

    <application
        android:allowBackup="false"
        android:label="@string/app_name"
        android:supportsRtl="true"
        android:theme="@style/AppTheme">
        <activity
            android:name=".MainActivity"
            android:configChanges="keyboardHidden|orientation|screenSize"
            android:exported="true"
            android:screenOrientation="landscape">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>
</manifest>
```

`app/src/main/res/values/strings.xml`:

```xml
<resources>
    <string name="app_name">SimpleTV</string>
    <string name="default_channel_url">https://example.com/simpletv/channels.json</string>
</resources>
```

`app/src/main/res/values/colors.xml`:

```xml
<resources>
    <color name="player_background">#090B0F</color>
    <color name="drawer_background">#E6192028</color>
    <color name="drawer_item_selected">#2F6FED</color>
</resources>
```

`app/src/main/res/values/styles.xml`:

```xml
<resources>
    <style name="AppTheme" parent="@android:style/Theme.Material.NoActionBar">
        <item name="android:windowNoTitle">true</item>
        <item name="android:windowActionBar">false</item>
        <item name="android:windowFullscreen">true</item>
        <item name="android:fontFamily">sans</item>
        <item name="android:colorAccent">@color/drawer_item_selected</item>
    </style>
</resources>
```

`.gitignore`:

```gitignore
.gradle/
build/
app/build/
local.properties
.idea/
*.iml
.superpowers/brainstorm/
```

`gradle.properties`:

```properties
android.aapt2FromMavenOverride=C:\\android\\build-tools\\35.0.1\\aapt2.exe
android.useAndroidX=true
```

- [ ] **Step 3: Add Gradle wrapper**

Create wrapper files by copying a known-good Gradle wrapper script and jar from an existing local Android project, then set `gradle/wrapper/gradle-wrapper.properties` to:

```properties
distributionBase=GRADLE_USER_HOME
distributionPath=wrapper/dists
distributionUrl=https\://services.gradle.org/distributions/gradle-8.13-bin.zip
networkTimeout=10000
validateDistributionUrl=true
zipStoreBase=GRADLE_USER_HOME
zipStorePath=wrapper/dists
```

Rationale: this workspace starts empty and has no system `gradle` command. The wrapper is required for `./gradlew.bat :app:assembleDebug` verification.

- [ ] **Step 4: Verify skeleton**

Run: `./gradlew.bat :app:assembleDebug`

Expected: build completes and creates `app/build/outputs/apk/debug/app-debug.apk`.

- [ ] **Step 5: Commit**

If the workspace has been initialized as a git repository:

```bash
git add .
git commit -m "chore: scaffold simpletv android app"
```

## Task 2: Channel Model and Parser

**Files:**
- Create: `app/src/main/java/com/simpletv/channel/ChannelModels.kt`
- Create: `app/src/main/java/com/simpletv/channel/ChannelParser.kt`
- Create: `app/src/test/java/com/simpletv/channel/ChannelParserTest.kt`
- Create: `sample/channels.json`

- [ ] **Step 1: Write failing parser tests**

`app/src/test/java/com/simpletv/channel/ChannelParserTest.kt`:

```kotlin
package com.simpletv.channel

import org.junit.Assert.assertEquals
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
        assertEquals("cctv13", result.channels.single().id)
        assertEquals(2, result.channels.single().sources.size)
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
}
```

- [ ] **Step 2: Run tests and verify failure**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.simpletv.channel.ChannelParserTest"`

Expected: FAIL because `ChannelParser` and model classes do not exist.

- [ ] **Step 3: Implement models and parser**

`app/src/main/java/com/simpletv/channel/ChannelModels.kt`:

```kotlin
package com.simpletv.channel

import kotlinx.serialization.Serializable

@Serializable
data class ChannelListDto(
    val version: Int,
    val updatedAt: String? = null,
    val channels: List<ChannelDto>
)

@Serializable
data class ChannelDto(
    val id: String,
    val name: String,
    val group: String? = null,
    val sources: List<ChannelSourceDto>
)

@Serializable
data class ChannelSourceDto(
    val url: String,
    val label: String? = null
)

data class ChannelList(
    val version: Int,
    val updatedAt: String?,
    val channels: List<Channel>
)

data class Channel(
    val id: String,
    val name: String,
    val group: String?,
    val sources: List<ChannelSource>
)

data class ChannelSource(
    val url: String,
    val label: String?
)
```

`app/src/main/java/com/simpletv/channel/ChannelParser.kt`:

```kotlin
package com.simpletv.channel

import kotlinx.serialization.json.Json

class ChannelParser(
    private val json: Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }
) {
    fun parse(rawJson: String): ChannelList {
        val dto = json.decodeFromString<ChannelListDto>(rawJson)
        require(dto.channels.isNotEmpty()) { "Channel list must contain at least one channel." }

        val channels = dto.channels.map { channel ->
            val id = channel.id.trim()
            require(id.isNotEmpty()) { "Channel id must not be empty." }
            require(channel.name.trim().isNotEmpty()) { "Channel $id name must not be empty." }
            require(channel.sources.isNotEmpty()) { "Channel $id must contain at least one source." }

            val sources = channel.sources.map { source ->
                val url = source.url.trim()
                require(url.startsWith("http://") || url.startsWith("https://")) {
                    "Source url for channel $id must be http or https."
                }
                ChannelSource(url = url, label = source.label?.trim()?.ifEmpty { null })
            }

            Channel(
                id = id,
                name = channel.name.trim(),
                group = channel.group?.trim()?.ifEmpty { null },
                sources = sources
            )
        }

        return ChannelList(version = dto.version, updatedAt = dto.updatedAt, channels = channels)
    }
}
```

`sample/channels.json`:

```json
{
  "version": 1,
  "updatedAt": "2026-05-15T00:00:00+08:00",
  "channels": [
    {
      "id": "cctv13",
      "name": "CCTV-13 新闻",
      "group": "央视",
      "sources": [
        {
          "url": "https://example.com/cctv13.m3u8",
          "label": "主源"
        }
      ]
    }
  ]
}
```

- [ ] **Step 4: Verify parser tests pass**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.simpletv.channel.ChannelParserTest"`

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/simpletv/channel app/src/test/java/com/simpletv/channel sample/channels.json
git commit -m "feat: add channel list parser"
```

## Task 3: Source Fallback Logic

**Files:**
- Create: `app/src/main/java/com/simpletv/playback/SourceSelector.kt`
- Create: `app/src/test/java/com/simpletv/playback/SourceSelectorTest.kt`

- [ ] **Step 1: Write failing tests**

`app/src/test/java/com/simpletv/playback/SourceSelectorTest.kt`:

```kotlin
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
}
```

- [ ] **Step 2: Run tests and verify failure**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.simpletv.playback.SourceSelectorTest"`

Expected: FAIL because `SourceSelector` does not exist.

- [ ] **Step 3: Implement source selector**

`app/src/main/java/com/simpletv/playback/SourceSelector.kt`:

```kotlin
package com.simpletv.playback

import com.simpletv.channel.ChannelSource

class SourceSelector(
    private val failureWindowMillis: Long = 5 * 60 * 1000L
) {
    private val failures = mutableMapOf<String, Long>()

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
        failures.keys.removeAll { it.startsWith("$channelId|") }
    }

    private fun key(channelId: String, sourceUrl: String): String = "$channelId|$sourceUrl"
}
```

- [ ] **Step 4: Verify source selector tests pass**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.simpletv.playback.SourceSelectorTest"`

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/simpletv/playback/SourceSelector.kt app/src/test/java/com/simpletv/playback/SourceSelectorTest.kt
git commit -m "feat: add playback source fallback logic"
```

## Task 4: Channel Repository Cache and Remote Update

**Files:**
- Create: `app/src/main/java/com/simpletv/channel/ChannelRepository.kt`
- Modify: `app/src/main/java/com/simpletv/MainActivity.kt`

- [ ] **Step 1: Implement repository**

`app/src/main/java/com/simpletv/channel/ChannelRepository.kt`:

```kotlin
package com.simpletv.channel

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

class ChannelRepository(
    context: Context,
    private val remoteUrl: String,
    private val parser: ChannelParser = ChannelParser(),
    private val client: OkHttpClient = OkHttpClient()
) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("simpletv_channels", Context.MODE_PRIVATE)

    fun cachedChannels(): ChannelList? {
        val raw = prefs.getString(KEY_CHANNEL_JSON, null) ?: return null
        return runCatching { parser.parse(raw) }.getOrNull()
    }

    suspend fun refreshChannels(): Result<ChannelList> = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder().url(remoteUrl).get().build()
            val response = client.newCall(request).execute()
            response.use {
                require(it.isSuccessful) { "Channel request failed: HTTP ${it.code}" }
                val body = requireNotNull(it.body?.string()) { "Channel response body is empty." }
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
        private const val KEY_CHANNEL_JSON = "channel_json"
        private const val KEY_CHANNEL_VERSION = "channel_version"
        private const val KEY_LAST_CHANNEL_ID = "last_channel_id"
    }
}
```

- [ ] **Step 2: Verify build**

Run: `./gradlew.bat :app:assembleDebug`

Expected: PASS.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/simpletv/channel/ChannelRepository.kt
git commit -m "feat: add remote channel repository"
```

## Task 5: Main Activity, Player Controller, and Basic Playback

**Files:**
- Create: `app/src/main/java/com/simpletv/MainActivity.kt`
- Create: `app/src/main/java/com/simpletv/playback/TvPlayerController.kt`

- [ ] **Step 1: Implement player controller**

`app/src/main/java/com/simpletv/playback/TvPlayerController.kt`:

```kotlin
package com.simpletv.playback

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.simpletv.channel.Channel
import com.simpletv.channel.ChannelSource

class TvPlayerController(
    context: Context,
    private val sourceSelector: SourceSelector = SourceSelector(),
    private val nowMillis: () -> Long = { System.currentTimeMillis() }
) {
    val player: ExoPlayer = ExoPlayer.Builder(context).build()
    var currentChannel: Channel? = null
        private set
    private var currentSource: ChannelSource? = null
    var onChannelFailed: ((Channel) -> Unit)? = null

    init {
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
        player.setMediaItem(MediaItem.fromUri(source.url))
        player.prepare()
        player.playWhenReady = true
    }

    fun release() {
        player.release()
    }
}
```

- [ ] **Step 2: Implement temporary Activity with fullscreen player**

`app/src/main/java/com/simpletv/MainActivity.kt`:

```kotlin
package com.simpletv

import android.os.Bundle
import android.view.View
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.media3.ui.PlayerView
import com.simpletv.channel.Channel
import com.simpletv.channel.ChannelRepository
import com.simpletv.playback.TvPlayerController
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    private lateinit var repository: ChannelRepository
    private lateinit var controller: TvPlayerController
    private var channels: List<Channel> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY

        repository = ChannelRepository(this, getString(R.string.default_channel_url))
        controller = TvPlayerController(this)

        val playerView = PlayerView(this).apply {
            useController = false
            player = controller.player
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }
        setContentView(FrameLayout(this).apply { addView(playerView) })

        repository.cachedChannels()?.let { startFromList(it.channels) }
        lifecycleScope.launch {
            repository.refreshChannels()
                .onSuccess { startFromList(it.channels) }
        }
    }

    private fun startFromList(newChannels: List<Channel>) {
        channels = newChannels
        val preferred = repository.lastChannelId()
        val channel = channels.firstOrNull { it.id == preferred } ?: channels.first()
        repository.saveLastChannelId(channel.id)
        controller.play(channel)
    }

    override fun onDestroy() {
        controller.release()
        super.onDestroy()
    }
}
```

- [ ] **Step 3: Verify build**

Run: `./gradlew.bat :app:assembleDebug`

Expected: PASS.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/simpletv/MainActivity.kt app/src/main/java/com/simpletv/playback/TvPlayerController.kt
git commit -m "feat: wire media3 fullscreen playback"
```

## Task 6: Compact Touch Drawer UI

**Files:**
- Create: `app/src/main/java/com/simpletv/ui/PlayerChromeView.kt`
- Modify: `app/src/main/java/com/simpletv/MainActivity.kt`

- [ ] **Step 1: Implement compact touch chrome**

`app/src/main/java/com/simpletv/ui/PlayerChromeView.kt`:

```kotlin
package com.simpletv.ui

import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.simpletv.channel.Channel

class PlayerChromeView(context: Context) : FrameLayout(context) {
    var onChannelClick: ((Channel) -> Unit)? = null
    var onRefreshClick: (() -> Unit)? = null
    var onResetClick: (() -> Unit)? = null

    private val drawer = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundColor(Color.parseColor("#E6192028"))
        visibility = GONE
        setPadding(dp(10), dp(10), dp(10), dp(10))
    }

    private val title = TextView(context).apply {
        text = "频道"
        setTextColor(Color.WHITE)
        textSize = 15f
        setPadding(0, 0, 0, dp(8))
    }

    init {
        setBackgroundColor(Color.TRANSPARENT)
        addView(drawer, LayoutParams(dp(266), LayoutParams.MATCH_PARENT, Gravity.END))
        drawer.addView(title)
        setOnClickListener { hideDrawer() }
        setOnLongClickListener {
            showSettings()
            true
        }
        drawer.setOnClickListener { }
    }

    fun setChannels(channels: List<Channel>, selectedChannelId: String?) {
        drawer.removeAllViews()
        drawer.addView(title)
        channels.forEach { channel ->
            val row = TextView(context).apply {
                text = channel.name
                textSize = 14f
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER_VERTICAL
                minHeight = dp(44)
                setPadding(dp(10), 0, dp(10), 0)
                setBackgroundColor(
                    if (channel.id == selectedChannelId) Color.parseColor("#2F6FED")
                    else Color.TRANSPARENT
                )
                setOnClickListener {
                    onChannelClick?.invoke(channel)
                    hideDrawer()
                }
            }
            drawer.addView(row, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, dp(44)))
        }
    }

    fun toggleDrawer() {
        drawer.visibility = if (drawer.visibility == VISIBLE) GONE else VISIBLE
    }

    fun hideDrawer() {
        drawer.visibility = GONE
    }

    private fun showSettings() {
        drawer.removeAllViews()
        drawer.visibility = VISIBLE
        drawer.addView(settingRow("刷新频道") { onRefreshClick?.invoke() })
        drawer.addView(settingRow("恢复默认") { onResetClick?.invoke() })
    }

    private fun settingRow(text: String, click: () -> Unit): TextView {
        return TextView(context).apply {
            this.text = text
            textSize = 14f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER_VERTICAL
            minHeight = dp(44)
            setPadding(dp(10), 0, dp(10), 0)
            setOnClickListener { click() }
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
```

- [ ] **Step 2: Wire chrome into Activity**

Modify `MainActivity` to use a root `FrameLayout` with `PlayerView` and `PlayerChromeView`:

```kotlin
private lateinit var chrome: PlayerChromeView

// inside onCreate after controller creation
chrome = PlayerChromeView(this)
chrome.onChannelClick = { channel ->
    repository.saveLastChannelId(channel.id)
    chrome.setChannels(channels, channel.id)
    controller.play(channel, clearFailures = true)
}
chrome.onRefreshClick = { refreshChannelsFromUser() }
chrome.onResetClick = {
    repository.clearCache()
    refreshChannelsFromUser()
}

val root = FrameLayout(this)
root.addView(playerView)
root.addView(chrome, FrameLayout.LayoutParams(
    FrameLayout.LayoutParams.MATCH_PARENT,
    FrameLayout.LayoutParams.MATCH_PARENT
))
chrome.setOnClickListener { chrome.toggleDrawer() }
setContentView(root)
```

Add method:

```kotlin
private fun refreshChannelsFromUser() {
    lifecycleScope.launch {
        repository.refreshChannels()
            .onSuccess { startFromList(it.channels) }
    }
}
```

Update `startFromList` to call:

```kotlin
chrome.setChannels(channels, channel.id)
```

- [ ] **Step 3: Verify build**

Run: `./gradlew.bat :app:assembleDebug`

Expected: PASS.

- [ ] **Step 4: Manual 800x400 layout check**

Run the debug APK on an Android 8 compatible emulator or device. Set the display to landscape 800x400 if possible.

Expected:
- Player fills the screen.
- Tap toggles right drawer.
- Drawer width is about one third of the screen.
- At least five channel rows fit without oversized UI.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/simpletv/MainActivity.kt app/src/main/java/com/simpletv/ui/PlayerChromeView.kt
git commit -m "feat: add compact touch channel drawer"
```

## Task 7: Empty, Loading, and Error States

**Files:**
- Modify: `app/src/main/java/com/simpletv/ui/PlayerChromeView.kt`
- Modify: `app/src/main/java/com/simpletv/MainActivity.kt`

- [ ] **Step 1: Add status and retry UI methods**

Add to `PlayerChromeView`:

```kotlin
var onRetryClick: (() -> Unit)? = null

private val status = TextView(context).apply {
    setTextColor(Color.WHITE)
    textSize = 14f
    setBackgroundColor(Color.parseColor("#99000000"))
    setPadding(dp(12), dp(8), dp(12), dp(8))
    visibility = GONE
}

// in init, before drawer
addView(status, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.START or Gravity.BOTTOM))

fun showStatus(message: String) {
    status.text = message
    status.visibility = VISIBLE
}

fun hideStatus() {
    status.visibility = GONE
}

fun showChannelFailed(channelName: String) {
    drawer.removeAllViews()
    drawer.visibility = VISIBLE
    drawer.addView(settingRow("$channelName 播放失败") { })
    drawer.addView(settingRow("重试") { onRetryClick?.invoke() })
    drawer.addView(settingRow("切换频道") { setChannels(emptyList(), null) })
}
```

- [ ] **Step 2: Wire player failure to UI**

In `MainActivity.onCreate`, after `controller` is created:

```kotlin
controller.onChannelFailed = { channel ->
    runOnUiThread {
        chrome.showChannelFailed(channel.name)
    }
}
```

Set retry callback after chrome creation:

```kotlin
chrome.onRetryClick = {
    controller.currentChannel?.let { channel ->
        controller.play(channel, clearFailures = true)
    }
}
```

In `refreshChannelsFromUser`, show status on failure:

```kotlin
private fun refreshChannelsFromUser() {
    lifecycleScope.launch {
        chrome.showStatus("正在刷新频道")
        repository.refreshChannels()
            .onSuccess {
                chrome.hideStatus()
                startFromList(it.channels)
            }
            .onFailure {
                chrome.showStatus("频道刷新失败，继续使用本地缓存")
            }
    }
}
```

- [ ] **Step 3: Verify build**

Run: `./gradlew.bat :app:assembleDebug`

Expected: PASS.

- [ ] **Step 4: Manual failure check**

Use `sample/channels.json` with an invalid first source and a valid second source.

Expected:
- Invalid source fails.
- Player automatically attempts the second source.
- If all sources are invalid, lightweight failed state appears with retry.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/simpletv/MainActivity.kt app/src/main/java/com/simpletv/ui/PlayerChromeView.kt
git commit -m "feat: add lightweight playback error states"
```

## Task 8: Final Android 8 and 800x400 Verification

**Files:**
- Modify only if verification finds issues.

- [ ] **Step 1: Run unit tests**

Run: `./gradlew.bat :app:testDebugUnitTest`

Expected: PASS.

- [ ] **Step 2: Build debug APK**

Run: `./gradlew.bat :app:assembleDebug`

Expected: PASS and APK exists at `app/build/outputs/apk/debug/app-debug.apk`.

- [ ] **Step 3: Install on Android 8 target**

Run, if a device or emulator is connected:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Expected: `Success`.

- [ ] **Step 4: Validate runtime behavior**

Manual checklist:

- App launches landscape.
- No action bar, no navigation overlay staying visible.
- At 800x400, text does not overlap and the page does not scroll as a whole.
- Tap player area toggles the right drawer.
- Channel rows are compact but easy to tap.
- Remote JSON failure does not crash the app.
- Cached channel list still starts playback.
- Playback source failure attempts the next source.

- [ ] **Step 5: Commit final fixes**

```bash
git add .
git commit -m "fix: polish android 8 small screen verification"
```

## Self-Review

Spec coverage:

- Android 8/API 26 is covered in Task 1 and Task 8.
- 800x400 landscape is covered in Task 1, Task 6, and Task 8.
- Native Android + Media3 is covered in Task 1 and Task 5.
- Fixed remote JSON and local cache are covered in Task 4.
- Backup source switching is covered in Task 3 and Task 5.
- Touch-only right drawer is covered in Task 6.
- No EPG, no remote focus navigation, and compact UI are preserved by omission and Task 6 layout constraints.
- Failure states and retry are covered in Task 7.

Placeholder scan:

- No placeholder markers or unspecified implementation steps remain.
- Commit steps are included, but should only be run after the workspace is initialized as git.

Type consistency:

- `Channel`, `ChannelSource`, `ChannelParser`, `ChannelRepository`, `SourceSelector`, `TvPlayerController`, and `PlayerChromeView` names are consistent across tasks.
- The `TvPlayerController.currentChannel` property is public read-only, matching Task 7 retry usage.
