/**
 * Smart Expense AI server: the app's chat calls come here and go on to the AI company with the owner's key, which is
 * kept here as a secret and never on a phone. One path per company (/claude, /chatgpt, /gemini, /groq, /openrouter);
 * a company with no key here answers 401, and the app tries the next one.
 */
const UP = {
  claude: { url: "https://api.anthropic.com/v1/messages", secret: "ANTHROPIC_API_KEY", auth: (k) => ({ "x-api-key": k }) },
  chatgpt: { url: "https://api.openai.com/v1/chat/completions", secret: "OPENAI_API_KEY", auth: (k) => ({ Authorization: "Bearer " + k }) },
  gemini: { url: "https://generativelanguage.googleapis.com/v1beta/openai/chat/completions", secret: "GEMINI_API_KEY", auth: (k) => ({ Authorization: "Bearer " + k }) },
  groq: { url: "https://api.groq.com/openai/v1/chat/completions", secret: "GROQ_API_KEY", auth: (k) => ({ Authorization: "Bearer " + k }) },
  openrouter: { url: "https://openrouter.ai/api/v1/chat/completions", secret: "OPENROUTER_API_KEY", auth: (k) => ({ Authorization: "Bearer " + k }) },
};

/** Headers the app may pass on (Claude's version and beta features); everything else is set here. */
const PASS = ["anthropic-version", "anthropic-beta"];

const answer = (status, message) =>
  new Response(JSON.stringify({ error: { message } }), { status, headers: { "Content-Type": "application/json" } });

export default {
  async fetch(request, env) {
    const up = UP[new URL(request.url).pathname.replace(/^\/+|\/+$/g, "")];
    if (!up || request.method !== "POST") return answer(404, "not found");
    if (env.APP_TOKEN && request.headers.get("x-app") !== env.APP_TOKEN) return answer(403, "not the app");
    const key = env[up.secret];
    if (!key) return answer(401, "no key on the server");
    const headers = { "Content-Type": "application/json", ...up.auth(key) };
    for (const h of PASS) {
      const v = request.headers.get(h);
      if (v) headers[h] = v;
    }
    const reply = await fetch(up.url, { method: "POST", headers, body: await request.text() });
    return new Response(reply.body, { status: reply.status, headers: { "Content-Type": "application/json" } });
  },
};
