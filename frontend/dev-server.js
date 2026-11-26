// Local-only frontend server for Eclipse development. Do not use as the production web server.
const http = require("node:http");
const fs = require("node:fs");
const path = require("node:path");

const ROOT = __dirname;
const BACKEND_HOST = process.env.BACKEND_HOST || "127.0.0.1";
const BACKEND_PORT = Number(process.env.BACKEND_PORT || 8080);
const PORT = Number(process.env.FRONTEND_DEV_PORT || 8088);
const MIME = {
  ".html": "text/html; charset=utf-8",
  ".css": "text/css; charset=utf-8",
  ".js": "text/javascript; charset=utf-8",
  ".json": "application/json; charset=utf-8",
  ".svg": "image/svg+xml",
  ".png": "image/png",
  ".jpg": "image/jpeg",
  ".jpeg": "image/jpeg",
  ".webp": "image/webp",
  ".ico": "image/x-icon"
};

function proxy(request, response) {
  const upstream = http.request({
    hostname: BACKEND_HOST,
    port: BACKEND_PORT,
    path: request.url,
    method: request.method,
    headers: { ...request.headers, host: BACKEND_HOST + ":" + BACKEND_PORT }
  }, upstreamResponse => {
    response.writeHead(upstreamResponse.statusCode || 502, upstreamResponse.headers);
    upstreamResponse.pipe(response);
  });
  upstream.on("error", () => {
    if (!response.headersSent) {
      response.writeHead(502, { "Content-Type": "text/plain; charset=utf-8", "Cache-Control": "no-store" });
    }
    response.end("Backend is not reachable. Start DigitalInvitationApplication in Eclipse on port " + BACKEND_PORT + ".");
  });
  request.pipe(upstream);
}

const server = http.createServer((request, response) => {
  const pathname = new URL(request.url, "http://localhost").pathname;
  if (pathname.startsWith("/api/") || pathname === "/actuator/health") {
    proxy(request, response);
    return;
  }
  if (request.method !== "GET" && request.method !== "HEAD") {
    response.writeHead(405, { Allow: "GET, HEAD" });
    response.end();
    return;
  }

  let decoded;
  try {
    decoded = decodeURIComponent(pathname);
  } catch (_) {
    response.writeHead(400);
    response.end("Invalid URL");
    return;
  }
  if (/^\/i\/[^/]+\/?$/.test(decoded)) decoded = "/invitation.html";
  if (decoded === "/") decoded = "/index.html";

  const file = path.resolve(ROOT, "." + decoded);
  if (file !== ROOT && !file.startsWith(ROOT + path.sep)) {
    response.writeHead(403);
    response.end("Forbidden");
    return;
  }

  fs.stat(file, (statError, stats) => {
    if (statError || !stats.isFile()) {
      response.writeHead(404, { "Content-Type": "text/plain; charset=utf-8" });
      response.end("Not found");
      return;
    }
    response.writeHead(200, {
      "Content-Type": MIME[path.extname(file).toLowerCase()] || "application/octet-stream",
      "X-Content-Type-Options": "nosniff",
      "Referrer-Policy": "strict-origin-when-cross-origin",
      "Cache-Control": file.endsWith(".html") ? "no-store" : "no-cache"
    });
    if (request.method === "HEAD") response.end();
    else fs.createReadStream(file).pipe(response);
  });
});

server.listen(PORT, "127.0.0.1", () => {
  process.stdout.write("DigitalInvitation local frontend: http://localhost:" + PORT + "\n");
  process.stdout.write("Proxying /api and health requests to http://" + BACKEND_HOST + ":" + BACKEND_PORT + "\n");
});
