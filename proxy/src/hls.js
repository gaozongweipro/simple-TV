import { encodeToken } from "./tokens.js";

export function isHlsPlaylist(text) {
  return text.trimStart().startsWith("#EXTM3U");
}

export function rewritePlaylist({ playlist, sourceUrl, requestBaseUrl, secret }) {
  const baseUrl = new URL(sourceUrl);
  const lines = playlist.split(/\r?\n/);
  return lines
    .map((line) => rewriteLine(line, baseUrl, requestBaseUrl, secret))
    .join("\n");
}

function rewriteLine(line, baseUrl, requestBaseUrl, secret) {
  const trimmed = line.trim();
  if (!trimmed) return line;

  if (trimmed.startsWith("#")) {
    return rewriteAttributeUris(line, baseUrl, requestBaseUrl, secret);
  }

  return proxyUrl(new URL(trimmed, baseUrl).toString(), requestBaseUrl, secret);
}

function rewriteAttributeUris(line, baseUrl, requestBaseUrl, secret) {
  return line.replace(/URI="([^"]+)"/g, (_, uri) => {
    const absolute = new URL(uri, baseUrl).toString();
    return `URI="${proxyUrl(absolute, requestBaseUrl, secret)}"`;
  });
}

function proxyUrl(url, requestBaseUrl, secret) {
  const token = encodeToken(url, secret);
  return `${requestBaseUrl}/proxy/${token}`;
}

export function playlistFingerprint(playlist) {
  const meaningful = playlist
    .split(/\r?\n/)
    .map((line) => line.trim())
    .filter((line) => line && !line.startsWith("#EXT-X-PROGRAM-DATE-TIME"));
  const mediaSequence = meaningful.find((line) => line.startsWith("#EXT-X-MEDIA-SEQUENCE:")) ?? "";
  const segments = meaningful.filter((line) => !line.startsWith("#"));
  const tailSegments = segments.slice(-3).join("|");
  return `${mediaSequence}|${tailSegments}`;
}
