const slug = location.pathname.split("/").filter(Boolean).pop();
const $ = id => document.getElementById(id);
const safe = value => String(value ?? "").replace(/[&<>"']/g, character => ({
  "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;"
}[character]));

const translations = {
  en: {
    eyebrow: "YOU ARE WARMLY INVITED", rsvpTitle: "Will you be joining us?",
    name: "Your name", email: "Email (optional)", response: "Your response",
    yes: "Joyfully accepting", no: "Regretfully declining", maybe: "Not sure yet",
    guests: "Number of guests", message: "Message (optional)", send: "Send RSVP",
    thanks: "Thank you. Your response has been received."
  },
  hi: {
    eyebrow: "सादर आमंत्रित", rsvpTitle: "क्या आप समारोह में शामिल होंगे?",
    name: "आपका नाम", email: "ईमेल (वैकल्पिक)", response: "आपका उत्तर",
    yes: "सहर्ष उपस्थित रहेंगे", no: "उपस्थित नहीं हो पाएंगे", maybe: "अभी निश्चित नहीं",
    guests: "मेहमानों की संख्या", message: "संदेश (वैकल्पिक)", send: "जवाब भेजें",
    thanks: "धन्यवाद। आपका जवाब प्राप्त हो गया है।"
  }
};
let locale = "en";

function t(key) {
  return (translations[locale] || translations.en)[key] || translations.en[key] || key;
}

function applyDesign(design = {}) {
  const palette = design.palette || {};
  const variables = {
    "--invitation-background": palette.background,
    "--invitation-surface": palette.surface,
    "--invitation-text": palette.text,
    "--invitation-accent": palette.accent,
    "--invitation-muted": palette.muted
  };
  Object.entries(variables).forEach(([name, value]) => {
    if (typeof value === "string" && /^#[0-9a-f]{6}$/i.test(value)) {
      document.documentElement.style.setProperty(name, value);
    }
  });

  const allowedStyles = new Set([
    "floral", "royal", "minimal", "romantic", "playful",
    "elegant", "botanical", "bold", "festive", "classic"
  ]);
  const style = allowedStyles.has(design.style) ? design.style : "classic";
  document.body.className = "invitation-page theme-" + style;
  document.documentElement.dataset.invitationFont = design.font === "sans" ? "sans" : "serif";
}

function renderSection(section) {
  const values = Object.entries(section.values || {})
    .map(([label, value]) => '<p><strong>' + safe(label) + '</strong><span>' + safe(value) + '</span></p>')
    .join("");
  const items = (section.items || []).map((item, index) => {
    const title = item.eventName || ("Function " + (index + 1));
    const details = Object.entries(item)
      .filter(([key]) => key !== "eventName")
      .map(([label, value]) => '<p><strong>' + safe(label) + '</strong><span>' + safe(value) + '</span></p>')
      .join("");
    return '<article class="invitation-event"><h3>' + safe(title) + '</h3>' + details + '</article>';
  }).join("");
  return '<section class="section-card"><h2>' + safe(section.label) + '</h2>' + values + items + '</section>';
}

async function load() {
  try {
    const response = await fetch("/api/v1/public/invitations/" + encodeURIComponent(slug), {
      headers: { "Accept": "application/json" },
      credentials: "same-origin"
    });
    if (response.status === 410) {
      $("loading").classList.add("hidden");
      $("expired").classList.remove("hidden");
      return;
    }
    if (response.status === 404) throw new Error("not-found");
    if (!response.ok) throw new Error("not-found");

    const result = await response.json();
    let snapshot = result.data;
    if (typeof snapshot === "string") snapshot = JSON.parse(snapshot);
    const content = snapshot.content || {};
    locale = content.locale === "hi" ? "hi" : "en";
    document.documentElement.lang = locale;
    applyDesign(snapshot.design || {});

    $("inv-title").textContent = snapshot.title || "You are invited";
    $("inv-eyebrow").textContent = t("eyebrow");
    const sections = Array.isArray(content.sections) ? content.sections : [];
    $("inv-content").innerHTML = sections
      .filter(section => section.enabled && (
        Object.keys(section.values || {}).length > 0 ||
        (section.items || []).some(item => Object.keys(item || {}).length > 0)
      ))
      .map(renderSection)
      .join("");

    $("loading").classList.add("hidden");
    $("invitation").classList.remove("hidden");
    document.title = snapshot.title || "Your invitation";

    const configResponse = await fetch("/api/v1/public/config", {
      headers: { "Accept": "application/json" },
      credentials: "same-origin"
    });
    if (configResponse.ok) {
      const config = await configResponse.json();
      if (config.data?.rsvpEnabled) renderRsvp();
    }
  } catch (error) {
    $("loading").classList.add("hidden");
    $("not-found").classList.remove("hidden");
  }
}

function renderRsvp() {
  $("rsvp-area").innerHTML =
    '<section class="section-card"><h2>' + safe(t("rsvpTitle")) + '</h2>' +
    '<form id="rsvp-form">' +
    '<label>' + safe(t("name")) + '<input name="guestName" maxlength="160" required></label>' +
    '<label>' + safe(t("email")) + '<input name="guestEmail" type="email" maxlength="320"></label>' +
    '<label>' + safe(t("response")) + '<select name="attendance">' +
    '<option value="ATTENDING">' + safe(t("yes")) + '</option>' +
    '<option value="NOT_ATTENDING">' + safe(t("no")) + '</option>' +
    '<option value="MAYBE">' + safe(t("maybe")) + '</option></select></label>' +
    '<label>' + safe(t("guests")) + '<input name="guestCount" type="number" min="0" max="20" value="1"></label>' +
    '<label>' + safe(t("message")) + '<textarea name="message" maxlength="1000"></textarea></label>' +
    '<button class="primary" type="submit">' + safe(t("send")) + '</button></form>' +
    '<p id="rsvp-message" class="muted" role="status" aria-live="polite"></p></section>';

  $("rsvp-form").addEventListener("submit", async event => {
    event.preventDefault();
    const data = Object.fromEntries(new FormData(event.target));
    data.guestCount = Number(data.guestCount);
    const message = $("rsvp-message");
    try {
      const response = await fetch("/api/v1/public/invitations/" + encodeURIComponent(slug) + "/rsvp", {
        method: "POST",
        headers: { "Content-Type": "application/json", "Accept": "application/json" },
        credentials: "same-origin",
        body: JSON.stringify(data)
      });
      const result = await response.json().catch(() => ({}));
      if (!response.ok) throw new Error(result.error?.message || "Unable to send RSVP");
      message.textContent = t("thanks");
      $("rsvp-form").classList.add("hidden");
    } catch (error) {
      message.textContent = error.message;
    }
  });
}

load();
