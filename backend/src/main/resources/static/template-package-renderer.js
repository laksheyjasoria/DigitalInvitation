(function(global) {
  "use strict";

  function replaceAssetReferences(value, assets) {
    let result = String(value || "");
    const entries = Object.entries(assets || {}).sort((a, b) => b[0].length - a[0].length);
    for (const [path, dataUrl] of entries) {
      const escaped = path.replace(/[.*+?^${}()|[\]\\]/g, "\\$&").replace(/\//g, "\\/");
      const reference = "(?:\\./|/)?"+escaped;
      result = result.replace(new RegExp("([\\x22\\x27])("+reference+")([\\x22\\x27])", "g"), "$1"+dataUrl+"$3");
      result = result.replace(new RegExp("url\\(([\\x22\\x27]?)(?:"+reference+")\\1\\)", "g"), "url("+JSON.stringify(dataUrl)+")");
    }
    return result;
  }

  function scriptSafeJson(value) {
    return JSON.stringify(value || {}).replace(/</g, "\\u003c").replace(/\u2028/g, "\\u2028").replace(/\u2029/g, "\\u2029");
  }

  function buildSrcdoc(preview, snapshot) {
    if (!preview || typeof preview.html !== "string") throw new Error("The published template package is unavailable.");
    const parser = new DOMParser();
    const source = parser.parseFromString(preview.html, "text/html");
    source.querySelectorAll("base,iframe,object,embed,meta[http-equiv]").forEach(node => node.remove());
    source.querySelectorAll('link[rel~="stylesheet"]').forEach(node => { const href=node.getAttribute("href")||""; if(!href.startsWith("data:text/css"))node.remove(); });
    source.querySelectorAll("script[src]").forEach(node => {
      const src = node.getAttribute("src") || "";
      if (/(?:^|\/)script\.js(?:[?#].*)?$/i.test(src)) node.remove();
    });

    const html = replaceAssetReferences(source.documentElement.outerHTML, preview.assets);
    const doc = parser.parseFromString(html, "text/html");
    const policy = doc.createElement("meta");
    policy.httpEquiv = "Content-Security-Policy";
    policy.content = "default-src 'none'; img-src data: blob:; font-src data: blob:; style-src 'unsafe-inline' data:; script-src 'unsafe-inline' data:; connect-src 'none'; form-action 'none'; base-uri 'none'; frame-src 'none'; object-src 'none'; media-src data: blob:";
    doc.head.prepend(policy);

    const style = doc.createElement("style");
    style.textContent = replaceAssetReferences(preview.css || "", preview.assets).replace(/<\/style/gi, "<\\/style");
    doc.head.append(style);

    const bootstrap = doc.createElement("script");
    bootstrap.textContent = "window.digitalInvitationPreviewData=" + scriptSafeJson(snapshot) + ";";
    doc.body.append(bootstrap);

    if (preview.js) {
      const script = doc.createElement("script");
      script.textContent = String(preview.js).replace(/<\/script/gi, "<\\/script");
      doc.body.append(script);
    }
    const resize = doc.createElement("script");
    resize.textContent = "(()=>{const send=()=>parent.postMessage({type:'di-template-resize',height:Math.max(document.documentElement.scrollHeight,document.body.scrollHeight)},'*');new ResizeObserver(send).observe(document.documentElement);window.addEventListener('load',send);send();})();";
    doc.body.append(resize);
    return "<!doctype html>" + doc.documentElement.outerHTML;
  }

  function renderIntoFrame(frame, preview, snapshot) {
    if (!frame) throw new Error("Template preview frame is missing.");
    frame.setAttribute("sandbox", "allow-scripts");
    frame.srcdoc = buildSrcdoc(preview, snapshot);
  }

  global.TemplatePackageRenderer = { buildSrcdoc, renderIntoFrame };
})(window);
