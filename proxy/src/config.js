import fs from "node:fs/promises";

export async function loadConfig(configPath) {
  const raw = await fs.readFile(configPath, "utf8");
  const parsed = JSON.parse(raw);
  const channels = Array.isArray(parsed.channels) ? parsed.channels : [];
  if (channels.length === 0) {
    throw new Error("Config must contain at least one channel.");
  }
  for (const channel of channels) {
    if (!channel.id || !Array.isArray(channel.sources) || channel.sources.length === 0) {
      throw new Error(`Invalid channel config: ${JSON.stringify(channel)}`);
    }
  }
  return { ...parsed, channels };
}
