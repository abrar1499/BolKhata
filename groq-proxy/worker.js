// Cloudflare Worker: lets every Smart Expense user use Groq without their own key.
// The owner's Groq key lives only in this Worker as the secret GROQ_KEY (never in the app or this repo).
// It forwards just the two calls the app makes, with the app's own models, and turns away anything else.

const ROUTES = {
  "/chat": "https://api.groq.com/openai/v1/chat/completions",
  "/transcribe": "https://api.groq.com/openai/v1/audio/transcriptions",
};
const MODELS = ["openai/gpt-oss-120b", "whisper-large-v3"];
const MAX_BYTES = 6 * 1024 * 1024; // a voice clip of about a minute
const PER_MINUTE = 30; // requests per phone address per minute (per Worker instance)
const seen = new Map();

function tooMany(ip) {
  const now = Date.now();
  const hits = (seen.get(ip) || []).filter((t) => now - t < 60_000);
  hits.push(now);
  seen.set(ip, hits);
  if (seen.size > 5000) seen.clear();
  return hits.length > PER_MINUTE;
}

export default {
  async fetch(request, env) {
    const url = new URL(request.url);
    const target = ROUTES[url.pathname];
    if (request.method !== "POST" || !target) return new Response("Not found", { status: 404 });
    if (request.headers.get("X-App") !== "smart-expense") return new Response("Forbidden", { status: 403 });
    if (tooMany(request.headers.get("CF-Connecting-IP") || "?")) return new Response("Slow down", { status: 429 });

    const body = await request.arrayBuffer();
    if (body.byteLength > MAX_BYTES) return new Response("Too large", { status: 413 });
    // Only the app's models: a chat is small JSON; a voice clip names its model in the first form field.
    const json = (request.headers.get("Content-Type") || "").includes("json");
    const head = new TextDecoder().decode(json ? body : body.slice(0, 4096));
    if (!MODELS.some((m) => head.includes(m))) return new Response("Model not allowed", { status: 400 });

    const reply = await fetch(target, {
      method: "POST",
      headers: {
        Authorization: "Bearer " + env.GROQ_KEY,
        "Content-Type": request.headers.get("Content-Type") || "application/json",
      },
      body,
    });
    return new Response(reply.body, {
      status: reply.status,
      headers: { "Content-Type": reply.headers.get("Content-Type") || "application/json" },
    });
  },
};
