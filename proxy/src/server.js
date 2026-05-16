import http from "node:http";
import fs from "node:fs/promises";
import path from "node:path";
import { spawn } from "node:child_process";
import { Readable } from "node:stream";
import { fileURLToPath } from "node:url";
import { loadConfig } from "./config.js";
import { decodeToken } from "./tokens.js";
import { isHlsPlaylist, playlistFingerprint, rewritePlaylist } from "./hls.js";

const configPath = process.env.CONFIG_PATH ?? fileURLToPath(new URL("../config/channels.json", import.meta.url));
const port = Number(process.env.PORT ?? 8088);
const requestTimeoutMs = Number(process.env.REQUEST_TIMEOUT_MS ?? 8000);
const failureCooldownMs = Number(process.env.FAILURE_COOLDOWN_MS ?? 120000);
const stuckWindowMs = Number(process.env.STUCK_WINDOW_MS ?? 20000);
const relayStaleMs = Number(process.env.RELAY_STALE_MS ?? 25000);
const relayWarmupMs = Number(process.env.RELAY_WARMUP_MS ?? 15000);
const cacheDir = process.env.CACHE_DIR ?? "/tmp/simple-tv-proxy";
const ffmpegPath = process.env.FFMPEG_PATH ?? "ffmpeg";
const secret = process.env.PROXY_SECRET ?? "change-this-secret-on-your-nas";
const serviceVersion = "0.3.0-relay-watchdog";

const config = await loadConfig(configPath);
const states = new Map();

const server = http.createServer(async (req, res) => {
  try {
    await route(req, res);
  } catch (error) {
    sendText(res, 500, error.stack ?? String(error));
  }
});

server.listen(port, "0.0.0.0", () => {
  console.log(`SimpleTV proxy listening on http://0.0.0.0:${port}`);
});

async function route(req, res) {
  const requestUrl = new URL(req.url ?? "/", `http://${req.headers.host ?? `127.0.0.1:${port}`}`);
  if (requestUrl.pathname === "/health") {
    sendJson(res, 200, {
      ok: true,
      version: serviceVersion,
      mode: "relay",
      channels: config.channels.length,
      ffmpegPath
    });
    return;
  }
  if (requestUrl.pathname === "/status") {
    sendJson(res, 200, buildStatus());
    return;
  }
  if (requestUrl.pathname === "/channels") {
    sendJson(res, 200, buildAppChannelList(requestUrl));
    return;
  }
  const relayMatch = requestUrl.pathname.match(/^\/relay\/([^/]+)\.m3u8$/);
  if (relayMatch) {
    await serveRelayPlaylist(relayMatch[1], requestUrl, res);
    return;
  }
  const relayFileMatch = requestUrl.pathname.match(/^\/relay-files\/([^/]+)\/([^/]+)$/);
  if (relayFileMatch) {
    await serveRelayFile(relayFileMatch[1], relayFileMatch[2], res);
    return;
  }
  const liveMatch = requestUrl.pathname.match(/^\/live\/([^/]+)\.m3u8$/);
  if (liveMatch) {
    await serveLivePlaylist(liveMatch[1], requestUrl, res);
    return;
  }
  const proxyMatch = requestUrl.pathname.match(/^\/proxy\/([^/]+)$/);
  if (proxyMatch) {
    await serveProxy(proxyMatch[1], req, requestUrl, res);
    return;
  }
  sendText(res, 404, "Not found");
}

function buildAppChannelList(requestUrl) {
  const baseUrl = origin(requestUrl);
  return {
    version: config.version ?? Number(new Date().toISOString().slice(0, 10).replaceAll("-", "")),
    updatedAt: config.updatedAt ?? new Date().toISOString().slice(0, 10),
    channels: config.channels.map((channel) => ({
      id: channel.id,
      name: channel.name,
      group: channel.group,
      sources: [{ url: `${baseUrl}/relay/${encodeURIComponent(channel.id)}.m3u8`, label: "NAS转播" }]
    }))
  };
}

async function serveRelayPlaylist(channelId, requestUrl, res) {
  const channel = config.channels.find((item) => item.id === channelId);
  if (!channel) {
    sendText(res, 404, `Unknown channel: ${channelId}`);
    return;
  }

  const state = stateFor(channelId);
  const relay = await ensureRelay(channel, state);
  if (!relay.ok) {
    sendText(res, 502, relay.error);
    return;
  }

  const playlistPath = path.join(relayDir(channelId), "index.m3u8");
  const ready = await waitForFreshFile(playlistPath, relayWarmupMs);
  if (!ready) {
    restartRelay(channel, state, "relay warmup timeout");
    sendText(res, 503, `Relay is warming up for ${channelId}`);
    return;
  }

  const playlist = await fs.readFile(playlistPath, "utf8");
  sendText(res, 200, rewriteRelayPlaylist(channelId, playlist, requestUrl), {
    "content-type": "application/vnd.apple.mpegurl; charset=utf-8",
    "cache-control": "no-store"
  });
}

async function serveRelayFile(channelId, fileName, res) {
  if (!/^[A-Za-z0-9_.-]+$/.test(fileName)) {
    sendText(res, 400, "Invalid file name");
    return;
  }

  const filePath = path.join(relayDir(channelId), fileName);
  const data = await fs.readFile(filePath);
  res.writeHead(200, {
    "content-type": contentTypeForFile(fileName),
    "cache-control": "public, max-age=10"
  });
  res.end(data);
}

async function serveLivePlaylist(channelId, requestUrl, res) {
  const channel = config.channels.find((item) => item.id === channelId);
  if (!channel) {
    sendText(res, 404, `Unknown channel: ${channelId}`);
    return;
  }

  const state = stateFor(channelId);
  const now = Date.now();
  const sources = healthySources(channel, state, now);
  const errors = [];

  for (const source of sources) {
    try {
      const upstream = await fetchText(source.url);
      if (!isHlsPlaylist(upstream.text)) {
        throw new Error("Upstream did not return an HLS playlist.");
      }
      if (isPlaylistStuck(state, source.url, upstream.text, now)) {
        markFailed(state, source.url, now, "playlist stuck");
        errors.push(`${source.label ?? source.url}: playlist stuck`);
        continue;
      }

      state.currentUrl = source.url;
      const rewritten = rewritePlaylist({
        playlist: upstream.text,
        sourceUrl: upstream.finalUrl,
        requestBaseUrl: origin(requestUrl),
        secret
      });
      sendText(res, 200, rewritten, {
        "content-type": "application/vnd.apple.mpegurl; charset=utf-8",
        "cache-control": "no-store"
      });
      return;
    } catch (error) {
      markFailed(state, source.url, now, error.message);
      errors.push(`${source.label ?? source.url}: ${error.message}`);
    }
  }

  sendText(res, 502, `No healthy source for ${channelId}\n${errors.join("\n")}`);
}

async function serveProxy(token, req, requestUrl, res) {
  let upstreamUrl;
  try {
    upstreamUrl = decodeToken(token, secret);
  } catch {
    sendText(res, 403, "Invalid proxy token");
    return;
  }

  const upstream = await fetchStream(upstreamUrl, req.headers.range);
  if (isLikelyPlaylist(upstreamUrl, upstream.headers)) {
    const text = await upstream.text();
    if (isHlsPlaylist(text)) {
      const rewritten = rewritePlaylist({
        playlist: text,
        sourceUrl: upstream.url,
        requestBaseUrl: origin(requestUrl),
        secret
      });
      sendText(res, upstream.status, rewritten, {
        "content-type": "application/vnd.apple.mpegurl; charset=utf-8",
        "cache-control": "no-store"
      });
      return;
    }
  }

  res.writeHead(upstream.status, copyHeaders(upstream.headers));
  Readable.fromWeb(upstream.body).pipe(res);
}

function healthySources(channel, state, now) {
  const available = channel.sources.filter((source) => {
    const failed = state.failures.get(source.url);
    return !failed || now - failed.at > failureCooldownMs;
  });
  return available.length > 0 ? available : channel.sources;
}

function isPlaylistStuck(state, sourceUrl, playlist, now) {
  const fingerprint = playlistFingerprint(playlist);
  const previous = state.playlists.get(sourceUrl);
  state.playlists.set(sourceUrl, { fingerprint, at: now });
  return Boolean(previous && previous.fingerprint === fingerprint && now - previous.at > stuckWindowMs);
}

function markFailed(state, sourceUrl, now, reason) {
  state.failures.set(sourceUrl, { at: now, reason });
  console.warn(`Source failed: ${sourceUrl} (${reason})`);
}

function stateFor(channelId) {
  if (!states.has(channelId)) {
    states.set(channelId, {
      currentUrl: null,
      relayProcess: null,
      relaySource: null,
      relayPlaylist: null,
      failures: new Map(),
      playlists: new Map()
    });
  }
  return states.get(channelId);
}

async function ensureRelay(channel, state) {
  if (state.relayProcess && state.relaySource && await relayIsHealthy(channel.id, state)) {
    return { ok: true };
  }

  if (state.relayProcess && !await relayIsHealthy(channel.id, state)) {
    restartRelay(channel, state, "relay stale or stuck");
  }

  const now = Date.now();
  const sources = healthySources(channel, state, now);
  for (const source of sources) {
    try {
      await startRelay(channel, state, source);
      return { ok: true };
    } catch (error) {
      markFailed(state, source.url, now, error.message);
    }
  }

  return {
    ok: false,
    error: `No relay source available for ${channel.id}`
  };
}

async function startRelay(channel, state, source) {
  stopRelay(state);
  state.relayPlaylist = null;
  const dir = relayDir(channel.id);
  await fs.rm(dir, { recursive: true, force: true });
  await fs.mkdir(dir, { recursive: true });

  const playlistPath = path.join(dir, "index.m3u8");
  const segmentPath = path.join(dir, "seg_%06d.ts");
  const args = [
    "-hide_banner",
    "-loglevel", "warning",
    "-rw_timeout", String(requestTimeoutMs * 1000),
    "-reconnect", "1",
    "-reconnect_streamed", "1",
    "-reconnect_delay_max", "5",
    "-i", source.url,
    "-map", "0:v:0?",
    "-map", "0:a:0?",
    "-c", "copy",
    "-f", "hls",
    "-hls_time", "4",
    "-hls_list_size", "8",
    "-hls_flags", "delete_segments+omit_endlist+independent_segments",
    "-hls_segment_filename", segmentPath,
    playlistPath
  ];
  const child = spawn(ffmpegPath, args, { stdio: ["ignore", "ignore", "pipe"] });
  state.relayProcess = child;
  state.relaySource = source;
  state.currentUrl = source.url;

  child.stderr.on("data", (chunk) => {
    const line = chunk.toString("utf8").trim();
    if (line) console.warn(`ffmpeg ${channel.id}: ${line}`);
  });
  child.on("error", (error) => {
    if (state.relayProcess === child) {
      markFailed(state, source.url, Date.now(), `ffmpeg error: ${error.message}`);
      state.relayProcess = null;
      state.relaySource = null;
      state.relayPlaylist = null;
    }
  });
  child.on("exit", (code, signal) => {
    if (state.relayProcess === child) {
      markFailed(state, source.url, Date.now(), `ffmpeg exited ${code ?? signal}`);
      state.relayProcess = null;
      state.relaySource = null;
      state.relayPlaylist = null;
    }
  });
}

function restartRelay(channel, state, reason) {
  if (state.relaySource) {
    markFailed(state, state.relaySource.url, Date.now(), reason);
  }
  stopRelay(state);
}

function stopRelay(state) {
  if (state.relayProcess) {
    state.relayProcess.kill("SIGTERM");
    state.relayProcess = null;
    state.relaySource = null;
    state.relayPlaylist = null;
  }
}

async function relayIsHealthy(channelId, state) {
  const playlistPath = path.join(relayDir(channelId), "index.m3u8");
  try {
    const stat = await fs.stat(playlistPath);
    const now = Date.now();
    if (now - stat.mtimeMs > relayStaleMs) return false;

    const playlist = await fs.readFile(playlistPath, "utf8");
    if (!isHlsPlaylist(playlist)) return false;
    const fingerprint = playlistFingerprint(playlist);
    const previous = state.relayPlaylist;
    if (!previous || previous.fingerprint !== fingerprint) {
      state.relayPlaylist = { fingerprint, at: now };
      return true;
    }

    return now - previous.at <= relayStaleMs;
  } catch {
    return false;
  }
}

async function waitForFreshFile(filePath, timeoutMs) {
  const deadline = Date.now() + timeoutMs;
  while (Date.now() < deadline) {
    try {
      const stat = await fs.stat(filePath);
      if (stat.size > 0 && Date.now() - stat.mtimeMs <= relayStaleMs) return true;
    } catch {
      // Keep waiting while FFmpeg warms up.
    }
    await new Promise((resolve) => setTimeout(resolve, 500));
  }
  return false;
}

function rewriteRelayPlaylist(channelId, playlist, requestUrl) {
  const baseUrl = origin(requestUrl);
  return playlist
    .split(/\r?\n/)
    .map((line) => {
      const trimmed = line.trim();
      if (!trimmed || trimmed.startsWith("#")) return line;
      const fileName = path.basename(trimmed);
      return `${baseUrl}/relay-files/${encodeURIComponent(channelId)}/${encodeURIComponent(fileName)}`;
    })
    .join("\n");
}

function relayDir(channelId) {
  return path.join(cacheDir, safeChannelId(channelId));
}

function safeChannelId(channelId) {
  return channelId.replace(/[^A-Za-z0-9_-]/g, "_");
}

async function fetchText(url) {
  const response = await fetchWithTimeout(url);
  if (!response.ok) throw new Error(`HTTP ${response.status}`);
  return { text: await response.text(), finalUrl: response.url };
}

async function fetchStream(url, range) {
  const headers = {};
  if (range) headers.range = range;
  const response = await fetchWithTimeout(url, { headers });
  if (!response.ok && response.status !== 206) {
    throw new Error(`Proxy upstream failed: HTTP ${response.status}`);
  }
  return response;
}

async function fetchWithTimeout(url, options = {}) {
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), requestTimeoutMs);
  try {
    return await fetch(url, {
      ...options,
      signal: controller.signal,
      headers: {
        "user-agent": "SimpleTVProxy/0.1",
        ...(options.headers ?? {})
      },
      redirect: "follow"
    });
  } catch (error) {
    if (error.name === "AbortError") throw new Error("request timeout");
    throw error;
  } finally {
    clearTimeout(timer);
  }
}

function copyHeaders(headers) {
  const copied = {};
  for (const [key, value] of headers.entries()) {
    const lower = key.toLowerCase();
    if (["connection", "content-encoding", "transfer-encoding"].includes(lower)) continue;
    copied[key] = value;
  }
  copied["cache-control"] = "public, max-age=30";
  return copied;
}

function isLikelyPlaylist(url, headers) {
  const contentType = headers.get("content-type") ?? "";
  return /\.m3u8($|\?)/i.test(url) ||
    contentType.includes("application/vnd.apple.mpegurl") ||
    contentType.includes("application/x-mpegURL");
}

function contentTypeForFile(fileName) {
  const lower = fileName.toLowerCase();
  if (lower.endsWith(".m3u8")) return "application/vnd.apple.mpegurl";
  if (lower.endsWith(".ts")) return "video/mp2t";
  if (lower.endsWith(".m4s")) return "video/iso.segment";
  if (lower.endsWith(".aac")) return "audio/aac";
  if (lower.endsWith(".key")) return "application/octet-stream";
  return "application/octet-stream";
}

function buildStatus() {
  const now = Date.now();
  return {
    ok: true,
    version: serviceVersion,
    mode: "relay",
    channels: config.channels.map((channel) => {
      const state = states.get(channel.id);
      return {
        id: channel.id,
        name: channel.name,
        relaySource: state?.relaySource?.label ?? null,
        relayUrl: state?.relaySource?.url ?? null,
        relayRunning: Boolean(state?.relayProcess),
        relayPlaylistAgeSeconds: state?.relayPlaylist
          ? Math.round((now - state.relayPlaylist.at) / 1000)
          : null,
        failures: state ? [...state.failures.entries()].map(([url, value]) => ({
          url,
          reason: value.reason,
          ageSeconds: Math.round((now - value.at) / 1000)
        })) : []
      };
    })
  };
}

function origin(requestUrl) {
  return `${requestUrl.protocol}//${requestUrl.host}`;
}

function sendJson(res, status, body) {
  sendText(res, status, JSON.stringify(body, null, 2), {
    "content-type": "application/json; charset=utf-8",
    "cache-control": "no-store"
  });
}

function sendText(res, status, body, headers = {}) {
  res.writeHead(status, {
    "content-type": "text/plain; charset=utf-8",
    ...headers
  });
  res.end(body);
}
