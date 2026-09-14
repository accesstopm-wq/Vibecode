export default {
  async fetch(request, env) {
    const cors = {
      "Access-Control-Allow-Origin": "*",
      "Access-Control-Allow-Methods": "GET,HEAD,POST,OPTIONS",
      "Access-Control-Allow-Headers": "Authorization,Content-Type",
      "Cache-Control": "no-store",
    };

    const url = new URL(request.url);

    if (request.method === "OPTIONS") {
      return new Response(null, { status: 204, headers: cors });
    }

    if (url.pathname === "/config" && request.method === "POST") {
      const auth = request.headers.get("Authorization") || "";
      if (auth !== `Bearer ${env.UPDATE_TOKEN}`) {
        return new Response("Unauthorized", { status: 401, headers: cors });
      }

      let body;
      try {
        body = await request.json();
      } catch {
        return new Response("Invalid JSON", { status: 400, headers: cors });
      }

      const origin = String(body?.origin || "").replace(/\/$/, "");
      if (!/^https:\/\/[A-Za-z0-9.-]+\.trycloudflare\.com$/.test(origin)) {
        return new Response("Invalid tunnel URL", { status: 400, headers: cors });
      }

      await env.TUNNEL.put("origin", origin);
      return new Response(JSON.stringify({ ok: true, origin }), {
        status: 200,
        headers: { ...cors, "Content-Type": "application/json" },
      });
    }

    if (url.pathname === "/health") {
      return new Response(JSON.stringify({ ok: true }), {
        headers: { ...cors, "Content-Type": "application/json" },
      });
    }

    const origin = await env.TUNNEL.get("origin", { cacheTtl: 30 });
    if (!origin) {
      return new Response("Tunnel URL is not configured", { status: 503, headers: cors });
    }

    const target = new URL(url.pathname + url.search, origin);
    const headers = new Headers(request.headers);
    headers.delete("host");
    headers.set("Origin", origin);

    const upstreamRequest = new Request(target, {
      method: request.method,
      headers,
      body: request.method === "GET" || request.method === "HEAD" ? undefined : request.body,
      redirect: "follow",
    });

    try {
      const response = await fetch(upstreamRequest, { cache: "no-store" });
      const out = new Response(response.body, response);
      out.headers.set("Access-Control-Allow-Origin", "*");
      out.headers.set("Cache-Control", "no-store");
      return out;
    } catch (error) {
      return new Response(`Upstream error: ${error}`, { status: 502, headers: cors });
    }
  },
};
