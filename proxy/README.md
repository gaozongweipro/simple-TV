# SimpleTV Proxy

这是给 fnOS/NAS 部署的直播中转服务。手机 App 只播放 NAS 地址，代理服务负责请求上游源、重写 HLS 切片地址、检测卡住的播放列表，并在短时间内跳过坏源。

## 本地运行

```powershell
cd proxy
npm test
npm start
```

访问：

```text
http://127.0.0.1:8088/health
http://127.0.0.1:8088/status
http://127.0.0.1:8088/channels
http://127.0.0.1:8088/relay/zjws.m3u8
```

## fnOS Docker 部署

1. 将 `proxy` 目录上传到 NAS。
2. 在 fnOS 的 Docker / Compose 中导入 `docker-compose.yml`。
3. 把 `PROXY_SECRET` 改成一串随机字符串。
4. 启动容器。
5. 手机和 NAS 在同一局域网时，用下面的地址测试：

```text
http://192.168.31.95:8088/health
http://192.168.31.95:8088/status
http://192.168.31.95:8088/relay/zjws.m3u8
```

## 频道配置

编辑 `config/channels.json`，每个频道可以配置多条上游源：

```json
{
  "id": "zjws",
  "name": "浙江卫视",
  "sources": [
    { "url": "https://example.com/live.m3u8", "label": "线路1" }
  ]
}
```

服务也会输出一个适合 SimpleTV 使用的频道清单：

```text
http://192.168.31.95:8088/channels
```

后续可以把 Android 应用的 `default_channel_url` 设置成这个地址，让 App 自动使用 NAS 代理源。

## 两种播放模式

- `/relay/{channel}.m3u8`：推荐。容器内用 FFmpeg 持续拉上游源，转成 NAS 本地 HLS，手机只播放 NAS 本地切片。它更适合电视直播。
- `/live/{channel}.m3u8`：旧的轻量代理模式，只重写上游 HLS，不主动缓存。上游源卡住时，手机仍可能一起卡。

`/channels` 默认输出 `/relay` 地址。

## 故障判断

如果访问 `/relay/zjws.m3u8` 返回 `503 Relay is warming up`，等 10-20 秒刷新一次，这是 FFmpeg 正在建立本地 HLS。

如果返回 `502 No relay source available`，说明代理服务正常运行，但 NAS 当前网络访问所有上游源都失败。此时需要替换 `config/channels.json` 里的上游源，或者把 NAS 接到更适合访问该源的网络。

如果 `/health` 访问失败，才是容器、端口映射或防火墙问题。

如果播放仍卡，访问 `/status` 查看当前频道正在使用哪条上游源、FFmpeg 是否运行、最近失败原因和本地播放列表是否长时间未推进。
