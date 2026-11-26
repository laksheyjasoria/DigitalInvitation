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

  function bindTemplateData(document, snapshot) {
    const content = snapshot?.content || snapshot?.sampleContent || {};
    const sections = Array.isArray(content.sections) ? content.sections : [];
    const sectionByKey = key => sections.find(section => section.key === key);

    document.querySelectorAll("[data-section-key]").forEach(node => {
      const section = sectionByKey(node.dataset.sectionKey);
      if (section && section.enabled === false) node.remove();
    });

    document.querySelectorAll("[data-repeat]").forEach(container => {
      const sectionKey = container.dataset.repeat;
      const section = sectionByKey(sectionKey);
      const items = Array.isArray(section?.items) ? section.items.filter(entry =>
        entry && Object.values(entry).some(value => value !== null && value !== undefined && String(value).trim().length)) : [];
      const templateNode = container.querySelector(":scope > template");
      const prototype = templateNode
        ? templateNode.content.cloneNode(true)
        : (container.firstElementChild ? container.firstElementChild.cloneNode(true) : null);
      if (!prototype) return;
      container.replaceChildren();
      if (!section || section.enabled === false) {
        container.hidden = true;
        return;
      }
      items.forEach(item => {
        const clone = prototype.cloneNode(true);
        applyFields(clone, sections, sectionKey, item);
        container.append(clone);
      });
      if (!items.length) {\n        const wrapper = container.closest("[data-section-key]");\n        if (wrapper) wrapper.remove(); else container.hidden = true;\n      }
    });

    applyFields(document, sections, null, null);
  }

  function applyFields(root, sections, scopedSectionKey, item) {
    root.querySelectorAll("[data-field]").forEach(node => {
      if (!item && node.closest("[data-repeat]")) return;
      const key = node.dataset.field;
      const sectionKey = node.dataset.section || scopedSectionKey;
      let value;
      if (item && (!sectionKey || sectionKey === scopedSectionKey)) {
        value = item[key];
      } else if (sectionKey) {
        const section = sections.find(candidate => candidate.key === sectionKey);
        value = section?.values?.[key];
        if (value === undefined && Array.isArray(section?.items)) value = section.items[0]?.[key];
      } else {
        for (const section of sections) {
          value = section.values?.[key];
          if (value === undefined && Array.isArray(section.items)) value = section.items[0]?.[key];
          if (value !== undefined) break;
        }
      }
      if (value !== undefined && value !== null && String(value).trim().length) {
        node.textContent = String(value);
      } else {
        // Do not leak a designer's sample placeholder when a customer leaves
        // an optional field blank or explicitly hides it.
        node.remove();
      }
    });
  }

  function buildSrcdoc(preview, snapshot) {
    if (!preview || typeof preview.html !== "string") throw new Error("The published template package is unavailable.");
    const parser = new DOMParser();
    const source = parser.parseFromString(preview.html, "text/html");
    source.querySelectorAll("base,iframe,object,embed,meta[http-equiv]").forEach(node => node.remove());
    bindTemplateData(source, snapshot);
    source.querySelectorAll('link[rel~="stylesheet"]').forEach(node => { const href=node.getAttribute("href")||""; if(!href.startsWith("data:text/css"))node.remove(); });
    source.querySelectorAll("script[src]").forEach(node => {
      const src = node.getAttribute("src") || "";
      if (src.replace(/^\.\//, "").replace(/^\//, "") === "script.js") node.remove();
    });

    const html = replaceAssetReferences(source.documentElement.outerHTML, preview.assets);
    const doc = parser.parseFromString(html, "text/html");
    const policy = doc.createElement("meta");
    policy.httpEquiv = "Content-Security-Policy";
    policy.content = "default-src 'none'; img-src data: blob:; font-src data: blob:; style-src 'unsafe-inline' data:; script-src 'unsafe-inline' data:; connect-src 'none'; form-action 'none'; base-uri 'none'; frame-src 'none'; object-src 'none'; media-src data: blob:";
    doc.head.prepend(policy);

    const bootstrap = doc.createElement("script");
    bootstrap.textContent = "window.digitalInvitationPreviewData=" + scriptSafeJson(snapshot) + ";";
    policy.after(bootstrap);

    const style = doc.createElement("style");
    style.textContent = replaceAssetReferences(preview.css || "", preview.assets).replace(/<\/style/gi, "<\\/style");
    bootstrap.after(style);

    if (preview.js) {
      const script = doc.createElement("script");
      script.textContent = String(preview.js).replace(/<\/script/gi, "<\\/script");
      doc.body.append(script);
    }
    const resize = doc.createElement("script");
    resize.textContent = "(()=>{const send=()=>parent.postMessage({type:'di-template-resize',height:Math.max(document.documentElement.scrollHeight,document.body.scrollHeight)},'*');new ResizeObserver(send).observe(document.documentElement);window.addEventListener('load',()=>{send();parent.postMessage({type:'di-preview-rendered'},'*');},{once:true});send();})();";
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
