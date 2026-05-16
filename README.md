# SimpleTV

SimpleTV 是一个横屏 Android 电视直播播放器，使用 Media3/ExoPlayer 播放 HTTP/HTTPS 视频源。

## 视频源如何设置

当前视频源来自两层配置：

1. 远程频道清单 URL：配置在 `app/src/main/res/values/strings.xml` 的 `default_channel_url`。
2. 内置兜底频道清单：配置在 `app/src/main/assets/default_channels.json`。

应用启动时会先读取本地可用频道：

- 如果之前刷新成功过，会读取缓存的远程频道清单。
- 同时读取 `default_channels.json`。
- 如果内置清单的 `version` 比缓存清单更新，会优先使用内置清单。
- 随后后台请求 `default_channel_url`，成功后写入缓存并切换到最新频道列表。

频道清单格式：

```json
{
  "version": 20260517,
  "updatedAt": "2026-05-15",
  "channels": [
    {
      "id": "cctv13",
      "name": "CCTV-13 新闻",
      "group": "央视",
      "sources": [
        {
          "url": "http://yd-m-l.cztv.com/channels/lantian/channel18/1080p.m3u8",
          "label": "AAC"
        }
      ]
    }
  ]
}
```

每个频道可以设置多个 `sources`。播放时会按顺序选择第一个可用源；如果某个源播放失败，它会在 5 分钟内被临时跳过，自动尝试下一个源。手动重试会清除该频道的失败记录。

播放器会按 URL 后缀识别 HLS/DASH/SmoothStreaming；不确定格式的动态 URL 会交给 Media3 自动探测。针对 IPTV 常见的 MP2 音频，应用已启用 FFmpeg 扩展解码器，避免部分 Android 8 设备只有画面没有声音。

如果频道源是 `yangshipin.cn` 或 `cctv.com` 页面，应用会切换到 WebView 播放网页源。央视网直播页属于这种模式，页面中的动态接口、Cookie、播放鉴权和实际视频地址都由官方网页自己处理，应用不需要把临时 m3u8 固定写死。进入网页源后会隐藏应用频道层，让网页接收点击；按返回键退出网页源并回到普通直播频道。

## 构建

```powershell
.\gradlew.bat assembleDebug
```

Debug APK 输出路径：

```text
app/build/outputs/apk/debug/app-debug.apk
```

## NAS 直播代理

仓库里的 `proxy/` 是一个可部署到 fnOS Docker 的直播中转服务。它会把上游 HLS 源转换成局域网内固定地址，例如：

```text
http://192.168.31.95:8088/relay/zjws.m3u8
```

代理会做三件事：

- 用 FFmpeg 持续拉上游源，生成 NAS 本地 HLS，手机只访问 NAS。
- 检测上游播放列表是否卡住，卡住后临时跳过该源。
- 对同一频道的多条源自动切换，输出统一频道清单：`http://192.168.31.95:8088/channels`。

部署和配置见 `proxy/README.md`。
