import crypto from "node:crypto";

export function encodeToken(url, secret) {
  const payload = Buffer.from(JSON.stringify({ url }), "utf8").toString("base64url");
  const signature = sign(payload, secret);
  return `${payload}.${signature}`;
}

export function decodeToken(token, secret) {
  const [payload, signature] = token.split(".");
  if (!payload || !signature || sign(payload, secret) !== signature) {
    throw new Error("Invalid token");
  }
  const decoded = JSON.parse(Buffer.from(payload, "base64url").toString("utf8"));
  if (!decoded.url || !/^https?:\/\//i.test(decoded.url)) {
    throw new Error("Invalid token URL");
  }
  return decoded.url;
}

function sign(payload, secret) {
  return crypto.createHmac("sha256", secret).update(payload).digest("base64url");
}
