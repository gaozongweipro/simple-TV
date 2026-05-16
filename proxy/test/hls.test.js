import assert from "node:assert/strict";
import test from "node:test";
import { decodeToken } from "../src/tokens.js";
import { playlistFingerprint, rewritePlaylist } from "../src/hls.js";

test("rewrites segment and attribute URIs through signed proxy URLs", () => {
  const rewritten = rewritePlaylist({
    playlist: [
      "#EXTM3U",
      '#EXT-X-KEY:METHOD=AES-128,URI="key.bin"',
      "#EXTINF:4,",
      "segment-1.ts"
    ].join("\n"),
    sourceUrl: "https://example.com/live/index.m3u8",
    requestBaseUrl: "http://nas:8088",
    secret: "secret"
  });

  const tokens = [...rewritten.matchAll(/\/proxy\/([A-Za-z0-9_-]+\.[A-Za-z0-9_-]+)/g)].map((match) => match[1]);

  assert.equal(tokens.length, 2);
  assert.equal(decodeToken(tokens[0], "secret"), "https://example.com/live/key.bin");
  assert.equal(decodeToken(tokens[1], "secret"), "https://example.com/live/segment-1.ts");
});

test("playlist fingerprint tracks media sequence and tail segments", () => {
  const a = playlistFingerprint("#EXTM3U\n#EXT-X-MEDIA-SEQUENCE:10\n#EXTINF:4,\na.ts\n#EXTINF:4,\nb.ts");
  const b = playlistFingerprint("#EXTM3U\n#EXT-X-MEDIA-SEQUENCE:11\n#EXTINF:4,\nb.ts\n#EXTINF:4,\nc.ts");

  assert.notEqual(a, b);
});
