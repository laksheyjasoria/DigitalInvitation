const slug = location.pathname.split("/").filter(Boolean).pop();
const $ = id => document.getElementById(id);
const safe = value => String(value ?? "").replace(/[&<>"']/g, character => ({
  "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;"
}[character]));

window.addEventListener("message", event => {
  const frame = $("invitation-render")?.querySelector("iframe");
  if (!frame || event.source !== frame.contentWindow || event.data?.type !== "di-template-resize") return;
  frame.style.height = Math.max(520, Math.min(5000, Number(event.data.height) || 760)) + "px";
});

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
    if (snapshot.templatePackagePreview) {
      const frame = document.createElement("iframe");
      frame.title = snapshot.title || "Invitation";
      frame.setAttribute("sandbox", "allow-scripts");
      frame.style.width = "100%";
      frame.style.minHeight = "760px";
      frame.style.border = "0";
      frame.style.display = "block";
      $("invitation-render").replaceChildren(frame);
      window.TemplatePackageRenderer.renderIntoFrame(frame, snapshot.templatePackagePreview, snapshot);
    } else {
      window.InvitationRenderer.render($("invitation-render"), snapshot);
    }
    document.body.classList.add("invitation-page");
    document.documentElement.dataset.invitationFont = snapshot.design?.font === "sans" ? "sans" : "serif";

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
