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
      if (!section || section.enabled === false) node.remove();
    });

    const locationUrl = String(content.locationUrl || "").trim();
    const qrUrl = String(snapshot?.locationQrDataUrl || snapshot?.locationQrUrl || "").trim();
    document.querySelectorAll("[data-location-qr]").forEach(node => {
      const section = node.closest("[data-location-qr-section]") || node.parentElement;
      if (!locationUrl) { if (section) section.remove(); return; }
      if (!qrUrl) {
        node.remove();
        const placeholder = document.createElement("div");
        placeholder.className = "location-qr-placeholder";
        placeholder.textContent = "Location QR appears here after publishing";
        if (section) section.append(placeholder);
        return;
      }
      if (node.tagName === "IMG") {
        node.setAttribute("src", qrUrl);
        node.setAttribute("alt", "QR code to open the event location");
      } else {
        node.setAttribute("href", qrUrl);
      }
    });
    document.querySelectorAll("[data-location-url]").forEach(node => {
      if (!locationUrl) { node.remove(); return; }
      node.setAttribute("href", locationUrl);
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
      if (!items.length) {
        const wrapper = container.closest("[data-section-key]");
        if (wrapper) wrapper.remove(); else container.hidden = true;
      }
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
        const format = node.dataset.format || "";
        let displayValue = String(value);
        if (format === "date-long" && /^\\d{4}-\\d{2}-\\d{2}$/.test(displayValue)) {
          const [year, month, day] = displayValue.split("-").map(Number);
          displayValue = new Date(year, month - 1, day).toLocaleDateString(document.documentElement.lang || "en", {
            weekday: "long", day: "numeric", month: "long", year: "numeric"
          });
        } else if (format === "time" && /^\\d{1,2}:\\d{2}$/.test(displayValue)) {
          const [hours, minutes] = displayValue.split(":").map(Number);
          displayValue = new Date(2000, 0, 1, hours, minutes).toLocaleTimeString(document.documentElement.lang || "en", {
            hour: "numeric", minute: "2-digit"
          });
        }
        node.textContent = displayValue;
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
    policy.content = "default-src 'none'; img-src 'self' data: blob:; font-src data: blob:; style-src 'unsafe-inline' data:; script-src 'unsafe-inline' data:; connect-src 'none'; form-action 'none'; base-uri 'none'; frame-src 'none'; object-src 'none'; media-src data: blob:";
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


  function initializeBuiltInTemplateBehavior(container, snapshot) {
    if (document.body.dataset.diBehavior !== "wedding-envelope") return;
    const intro = container.querySelector("#intro");
    const envelope = container.querySelector("#envelope");
    const flap = container.querySelector("#flap");
    const seal = container.querySelector("#seal");
    let opened = false;

    function startReveals() {
      const items = container.querySelectorAll(".reveal");
      if (!("IntersectionObserver" in window)) { items.forEach(item => item.classList.add("in")); return; }
      const observer = new IntersectionObserver(entries => entries.forEach(entry => {
        if (entry.isIntersecting) { entry.target.classList.add("in"); observer.unobserve(entry.target); }
      }), { threshold: 0.12, rootMargin: "0px 0px -70px 0px" });
      items.forEach(item => observer.observe(item));
    }

    function startCountdown() {
      const eventSection = (snapshot?.content?.sections || []).find(section => section.key === "event");
      const values = eventSection?.values || {};
      const date = String(values.eventDate || "").trim();
      const time = String(values.eventTime || "00:00").trim();
      if (!/^\\d{4}-\\d{2}-\\d{2}$/.test(date)) return;
      const target = new Date(date + "T" + (/^\\d{1,2}:\\d{2}$/.test(time) ? time : "00:00") + ":00");
      const nodes = { days: container.querySelector("#cd-days"), hours: container.querySelector("#cd-hours"),
        mins: container.querySelector("#cd-mins"), secs: container.querySelector("#cd-secs") };
      const pad = value => String(value).padStart(2, "0");
      const update = () => {
        const diff = Math.max(0, target.getTime() - Date.now());
        if (nodes.days) nodes.days.textContent = pad(Math.floor(diff / 86400000));
        if (nodes.hours) nodes.hours.textContent = pad(Math.floor(diff / 3600000) % 24);
        if (nodes.mins) nodes.mins.textContent = pad(Math.floor(diff / 60000) % 60);
        if (nodes.secs) nodes.secs.textContent = pad(Math.floor(diff / 1000) % 60);
      };
      update(); window.setInterval(update, 1000);
    }

    function startPetals() {
      if (window.matchMedia("(prefers-reduced-motion: reduce)").matches) return;
      const canvas = container.querySelector("#petals"), ctx = canvas?.getContext?.("2d");
      if (!canvas || !ctx) return;
      let width = 0, height = 0;
      const ratio = Math.min(window.devicePixelRatio || 1, 2);
      const colors = ["rgba(217,167,160,1)","rgba(232,196,190,1)","rgba(246,236,224,1)","rgba(216,195,156,1)","rgba(200,190,175,1)"];
      const resize = () => {
        width = window.innerWidth; height = window.innerHeight;
        canvas.width = width * ratio; canvas.height = height * ratio;
        canvas.style.width = width + "px"; canvas.style.height = height + "px";
        ctx.setTransform(ratio, 0, 0, ratio, 0, 0);
      };
      resize(); window.addEventListener("resize", resize);
      const petals = Array.from({length: width < 700 ? 26 : 46}, () => ({
        x: Math.random()*width, y: Math.random()*height, size: 5+Math.random()*9,
        vy: .35+Math.random()*.9, vx: -.35+Math.random()*.7, rot: Math.random()*Math.PI*2,
        vr: (-.6+Math.random()*1.2)*.025, sway: Math.random()*Math.PI*2,
        vs: .008+Math.random()*.016, alpha: .22+Math.random()*.45,
        color: colors[Math.floor(Math.random()*colors.length)]
      }));
      const loop = () => {
        ctx.clearRect(0,0,width,height);
        petals.forEach(p => {
          p.sway += p.vs; p.y += p.vy; p.x += p.vx + Math.sin(p.sway)*.9; p.rot += p.vr;
          if (p.y > height+40 || p.x < -70 || p.x > width+70) { p.x=Math.random()*width; p.y=-30; }
          ctx.save(); ctx.translate(p.x,p.y); ctx.rotate(p.rot); ctx.globalAlpha=p.alpha; ctx.fillStyle=p.color;
          ctx.beginPath(); ctx.ellipse(0,0,p.size,p.size*.52,0,0,Math.PI*2); ctx.fill(); ctx.restore();
        });
        window.requestAnimationFrame(loop);
      };
      loop();
    }

    function openInvitation() {
      if (opened) return;
      opened = true; seal?.classList.add("poof"); envelope?.classList.add("open");
      window.setTimeout(() => { if (flap) flap.style.zIndex = "1"; }, 560);
      window.setTimeout(() => {
        intro?.classList.add("hidden"); document.body.classList.add("ready");
        startPetals(); startReveals();
      }, 1750);
      window.setTimeout(() => { if (intro) intro.style.display = "none"; }, 3000);
    }
    seal?.addEventListener("click", openInvitation);
    envelope?.addEventListener("click", openInvitation);
    document.addEventListener("keydown", event => {
      if (!opened && (event.key === "Enter" || event.key === " ") &&
          (event.target === document.body || event.target === document.documentElement)) {
        event.preventDefault(); openInvitation();
      }
    });
    startCountdown();
    if (!intro) { document.body.classList.add("ready"); startReveals(); }
  }

  // Uploaded scripts remain disabled; only this explicitly selected first-party
  // behavior is initialized for the envelope invitation.
  function renderIntoPage(container, preview, snapshot) {
    if (!container) throw new Error("Invitation render target is missing.");
    if (!preview || typeof preview.html !== "string") {
      throw new Error("The published template package is unavailable.");
    }

    const parser = new DOMParser();
    const source = parser.parseFromString(preview.html, "text/html");
    source.querySelectorAll("base,iframe,object,embed,script,meta[http-equiv]").forEach(node => node.remove());
    bindTemplateData(source, snapshot);

    source.querySelectorAll('link[rel~="stylesheet"]').forEach(node => {
      const href = node.getAttribute("href") || "";
      if (!href.startsWith("data:text/css")) node.remove();
    });

    const rewritten = replaceAssetReferences(source.documentElement.outerHTML, preview.assets);
    const doc = parser.parseFromString(rewritten, "text/html");

    // Prevent uploaded markup from running script or navigating through
    // javascript: URLs when it is mounted in the first-party page.
    doc.querySelectorAll("*").forEach(node => {
      Array.from(node.attributes).forEach(attribute => {
        const name = attribute.name.toLowerCase();
        const value = String(attribute.value || "").trim();
        if (name.startsWith("on") || name === "srcdoc") {
          node.removeAttribute(attribute.name);
          return;
        }
        if (["href", "src", "xlink:href", "action", "formaction", "poster"].includes(name) &&
            /^(?:javascript|vbscript):|^data:text\/html/i.test(value)) {
          node.removeAttribute(attribute.name);
        }
      });
    });

    document.querySelector("#di-template-package-style")?.remove();
    const style = document.createElement("style");
    style.id = "di-template-package-style";
    style.textContent = replaceAssetReferences(preview.css || "", preview.assets);
    document.head.append(style);

    doc.head.querySelectorAll("style").forEach(templateStyle => {
      const styleCopy = document.createElement("style");
      styleCopy.textContent = templateStyle.textContent || "";
      document.head.append(styleCopy);
    });

    container.innerHTML = doc.body.innerHTML;
    if (doc.body.className) document.body.classList.add(...doc.body.className.split(/\s+/).filter(Boolean));
    if (doc.body.getAttribute("style")) {
      document.body.style.cssText += ";" + doc.body.getAttribute("style");
    }
    if (snapshot?.title) document.title = snapshot.title;
    if (doc.body.dataset.diBehavior) document.body.dataset.diBehavior = doc.body.dataset.diBehavior;
    initializeBuiltInTemplateBehavior(container, snapshot);
  }

  function renderIntoFrame(frame, preview, snapshot) {
    if (!frame) throw new Error("Template preview frame is missing.");
    frame.setAttribute("sandbox", "allow-scripts");
    frame.srcdoc = buildSrcdoc(preview, snapshot);
  }

  global.TemplatePackageRenderer = { buildSrcdoc, renderIntoFrame, renderIntoPage };
})(window);
