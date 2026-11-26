const fs = require("node:fs");
const paths = [
  "frontend/template-package-renderer.js",
  "backend/src/main/resources/static/template-package-renderer.js"
];
const sources = paths.map(path => fs.readFileSync(path, "utf8"));
if (sources[0] !== sources[1]) throw new Error("Frontend and backend template renderers must remain identical.");
for (const source of sources) {
  const bootstrapDeclaration = source.indexOf('const bootstrap = doc.createElement("script")');
  const bootstrapUse = source.indexOf("bootstrap.after(style)");
  const styleDeclaration = source.indexOf('const style = doc.createElement("style")');
  if (bootstrapDeclaration < 0 || styleDeclaration < 0 || bootstrapUse < 0
      || bootstrapDeclaration > bootstrapUse || styleDeclaration > bootstrapUse) {
    throw new Error("Template preview bootstrap/style ordering is invalid.");
  }
  if (!source.includes('frame.setAttribute("sandbox", "allow-scripts")')
      || !source.includes("connect-src 'none'") || !source.includes("form-action 'none'")) {
    throw new Error("Template preview must remain isolated by its sandbox and restrictive CSP.");
  }
}
process.stdout.write("Template package renderer ordering and sandbox checks passed.\n");
