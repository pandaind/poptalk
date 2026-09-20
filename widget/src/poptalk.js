import { ICON_CHAT, ICON_CLOSE, ICON_SEND } from './icons.js';
import { resolveTranslations } from './i18n.js';
import rawCSS from './styles.css?inline';

(function () {
  'use strict';

  // ── Read config from script tag data-* attributes ─────────────────────────
  const script = document.currentScript ||
    document.querySelector('script[data-api-url]');

  const cfg = {
    apiUrl:  (script?.dataset.apiUrl || 'http://localhost:8080').replace(/\/$/, ''),
    theme:   script?.dataset.theme   || 'dark',
    accent:  script?.dataset.accent  || '#6C63FF',
    // Which backend persona to talk to (multi-persona deployments only).
    // Left blank, the backend uses its configured default persona.
    persona: script?.dataset.persona || '',
    lang:    script?.dataset.lang    || '',
  };

  const t = resolveTranslations(cfg.lang);

  // Appends ?persona=<id> to a URL when one was configured via data-persona.
  function withPersona(url) {
    if (!cfg.persona) return url;
    return `${url}${url.includes('?') ? '&' : '?'}persona=${encodeURIComponent(cfg.persona)}`;
  }

  // ── State ─────────────────────────────────────────────────────────────────
  let token     = sessionStorage.getItem('pt_token')     || null;
  let sessionId = sessionStorage.getItem('pt_session_id')|| null;
  let isOpen    = false;
  let isWaiting = false;
  let chatMode  = 'AI'; // from GET /api/config — "AI" streams, "MANUAL" polls for a reply

  // ── Shadow DOM host ───────────────────────────────────────────────────────
  const host = document.createElement('div');
  host.id = 'poptalk-host';
  const shadow = host.attachShadow({ mode: 'closed' });
  document.body.appendChild(host);

  // ── Inject styles ─────────────────────────────────────────────────────────
  const sheet = new CSSStyleSheet();
  sheet.replaceSync(rawCSS);
  shadow.adoptedStyleSheets = [sheet];

  // ── Inject Google Font ────────────────────────────────────────────────────
  if (!document.querySelector('#pt-inter-font')) {
    const link = document.createElement('link');
    link.id   = 'pt-inter-font';
    link.rel  = 'stylesheet';
    link.href = 'https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600;700&display=swap';
    document.head.appendChild(link);
  }

  // ── Build DOM ─────────────────────────────────────────────────────────────
  shadow.innerHTML = `
    <button id="pt-toggle" aria-label="${t.openChat}" title="${t.chatTooltip}">
      <span class="pt-icon pt-icon-chat">${ICON_CHAT}</span>
      <span class="pt-icon pt-icon-close">${ICON_CLOSE}</span>
    </button>

    <div id="pt-popup" role="dialog" aria-modal="true" aria-label="${t.dialogLabel}">
      <div id="pt-header">
        <div class="pt-avatar" id="pt-avatar">P</div>
        <div class="pt-header-text">
          <div class="pt-title" id="pt-title">${t.chatTooltip}</div>
          <div class="pt-status">${t.online}</div>
        </div>
      </div>

      <div id="pt-messages" role="log" aria-live="polite">
        <p class="pt-welcome">${t.welcome}</p>
      </div>

      <div id="pt-input-area">
        <textarea
          id="pt-input"
          rows="1"
          placeholder="${t.inputPlaceholder}"
          aria-label="${t.inputLabel}"
          maxlength="500"
        ></textarea>
        <button id="pt-send" aria-label="${t.sendLabel}" disabled>${ICON_SEND}</button>
      </div>

      <div id="pt-footer">
        ${t.poweredBy} <a href="https://github.com/pandaind/poptalk" target="_blank" rel="noopener">PopTalk</a>
      </div>
    </div>
  `;

  // ── Apply accent color ────────────────────────────────────────────────────
  const toggleBtn = shadow.getElementById('pt-toggle');
  const popup     = shadow.getElementById('pt-popup');
  const messages  = shadow.getElementById('pt-messages');
  const input     = shadow.getElementById('pt-input');
  const sendBtn   = shadow.getElementById('pt-send');
  const titleEl   = shadow.getElementById('pt-title');
  const avatarEl  = shadow.getElementById('pt-avatar');

  // Set CSS custom property for accent
  host.style.setProperty('--pt-accent', cfg.accent);
  shadow.host.style.setProperty('--pt-accent', cfg.accent);

  // ── Fetch branding from backend ───────────────────────────────────────────
  async function loadConfig() {
    try {
      const res = await fetch(withPersona(`${cfg.apiUrl}/api/config`));
      if (res.ok) {
        const data = await res.json();
        if (data.chatTitle)     titleEl.textContent  = data.chatTitle;
        if (data.avatarInitial) avatarEl.textContent = data.avatarInitial.charAt(0).toUpperCase();
        if (data.chatMode)      chatMode = data.chatMode;
      }
    } catch (_) { /* use defaults */ }
  }

  // ── Session management ────────────────────────────────────────────────────
  async function ensureSession() {
    if (token && sessionId) return true;
    try {
      // The backend gates chat behind its contact-registration endpoint (name +
      // contact + contactType). The widget itself doesn't collect those, so we
      // register with an anonymous placeholder — good enough to obtain a session.
      const visitorId = (typeof crypto !== 'undefined' && crypto.randomUUID)
        ? crypto.randomUUID()
        : `${Date.now()}-${Math.random().toString(36).slice(2)}`;

      const res = await fetch(withPersona(`${cfg.apiUrl}/api/v1/chat`), {
        method:  'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          name:        'Website Visitor',
          contact:     `visitor-${visitorId}@widget.local`,
          contactType: 'email',
          source:      location.hostname,
          referrer:    document.referrer || undefined,
        }),
      });
      if (!res.ok) throw new Error(`Register failed: ${res.status}`);
      const data = await res.json();
      token     = data.token;
      sessionId = data.sessionId;
      sessionStorage.setItem('pt_token',      token);
      sessionStorage.setItem('pt_session_id', sessionId);
      return true;
    } catch (err) {
      appendError(t.errorConnect);
      console.error('[poptalk] session error:', err);
      return false;
    }
  }

  async function loadHistory() {
    if (!token) return;
    try {
      const res = await fetch(`${cfg.apiUrl}/api/v1/chat/history`, {
        headers: { Authorization: `Bearer ${token}` }
      });
      if (!res.ok) return;
      const history = await res.json();
      if (history.length === 0) return;
      // Remove welcome message
      const welcome = messages.querySelector('.pt-welcome');
      if (welcome) welcome.remove();
      history.forEach(m => appendBubble(m.direction === 'USER' ? 'user' : 'ai', m.content));
      scrollToBottom();
    } catch (_) { /* silent — fresh start */ }
  }

  // ── Message rendering ─────────────────────────────────────────────────────
  function appendBubble(role, text) {
    const div = document.createElement('div');
    div.className = `pt-msg ${role === 'user' ? 'pt-msg-user' : 'pt-msg-ai'}`;
    div.textContent = text;
    messages.appendChild(div);
    return div;
  }

  function appendError(text) {
    const div = appendBubble('ai', `⚠️ ${text}`);
    div.classList.add('pt-msg-error');
    scrollToBottom();
  }

  function showTyping() {
    const el = document.createElement('div');
    el.className = 'pt-typing';
    el.id = 'pt-typing';
    el.innerHTML = '<span></span><span></span><span></span>';
    messages.appendChild(el);
    scrollToBottom();
    return el;
  }

  function removeTyping() {
    shadow.getElementById('pt-typing')?.remove();
  }

  function scrollToBottom() {
    messages.scrollTop = messages.scrollHeight;
  }

  // ── Send message ──────────────────────────────────────────────────────────
  async function sendMessage() {
    const text = input.value.trim();
    if (!text || isWaiting) return;

    // Remove welcome message on first send
    messages.querySelector('.pt-welcome')?.remove();

    appendBubble('user', text);
    input.value = '';
    autoResize();
    scrollToBottom();

    isWaiting = true;
    sendBtn.disabled = true;
    showTyping();

    if (!(await ensureSession())) {
      removeTyping();
      isWaiting = false;
      sendBtn.disabled = false;
      return;
    }

    if (chatMode === 'AI') {
      await sendMessageStreaming(text);
    } else {
      await sendMessageBlocking(text);
    }

    isWaiting = false;
    sendBtn.disabled = input.value.trim().length === 0;
    scrollToBottom();
  }

  // MANUAL mode: unchanged request/response round trip — there's no AI reply
  // to stream, the frontend polls /api/v1/chat/reply separately for it.
  async function sendMessageBlocking(text) {
    try {
      const res = await fetch(`${cfg.apiUrl}/api/v1/chat/message`, {
        method:  'POST',
        headers: {
          'Content-Type':  'application/json',
          'Authorization': `Bearer ${token}`,
        },
        body: JSON.stringify({ message: text }),
      });

      removeTyping();

      if (res.status === 401) {
        // Token expired — clear and retry once
        token = null; sessionId = null;
        sessionStorage.removeItem('pt_token');
        sessionStorage.removeItem('pt_session_id');
        appendError(t.errorSessionExpired);
      } else if (res.status === 429) {
        appendError(t.errorRateLimited);
      } else if (!res.ok) {
        appendError(t.errorGeneric);
      } else {
        const data = await res.json();
        appendBubble('ai', data.response);
      }
    } catch (err) {
      removeTyping();
      appendError(t.errorNetwork);
      console.error('[poptalk] send error:', err);
    }
  }

  // AI mode: reads the reply as Server-Sent Events and appends each chunk to
  // a live bubble as it arrives. Uses fetch + a manual reader rather than
  // EventSource, since EventSource can't send the Authorization header this
  // endpoint requires.
  async function sendMessageStreaming(text) {
    let res;
    try {
      res = await fetch(`${cfg.apiUrl}/api/v1/chat/stream`, {
        method:  'POST',
        headers: {
          'Content-Type':  'application/json',
          'Authorization': `Bearer ${token}`,
        },
        body: JSON.stringify({ message: text }),
      });
    } catch (err) {
      removeTyping();
      appendError(t.errorNetwork);
      console.error('[poptalk] stream error:', err);
      return;
    }

    removeTyping();

    if (res.status === 401) {
      token = null; sessionId = null;
      sessionStorage.removeItem('pt_token');
      sessionStorage.removeItem('pt_session_id');
      appendError(t.errorSessionExpired);
      return;
    }
    if (res.status === 429) {
      appendError(t.errorRateLimited);
      return;
    }
    if (!res.ok || !res.body) {
      appendError(t.errorGeneric);
      return;
    }

    const bubble = appendBubble('ai', '');
    let gotAnyText = false;

    try {
      const reader = res.body.getReader();
      const decoder = new TextDecoder();
      let buffer = '';

      while (true) {
        const { done, value } = await reader.read();
        if (done) break;
        buffer += decoder.decode(value, { stream: true });

        let boundary;
        while ((boundary = buffer.indexOf('\n\n')) !== -1) {
          const rawEvent = buffer.slice(0, boundary);
          buffer = buffer.slice(boundary + 2);
          const { event, data } = parseSseEvent(rawEvent);

          if (event === 'error') {
            if (!gotAnyText) bubble.remove();
            appendError(t.errorGeneric);
            return;
          }
          if (data != null && event !== 'done') {
            bubble.textContent += data;
            gotAnyText = true;
            scrollToBottom();
          }
        }
      }
    } catch (err) {
      console.error('[poptalk] stream read error:', err);
      if (!gotAnyText) {
        bubble.remove();
        appendError(t.errorNetwork);
      }
    }
  }

  function parseSseEvent(rawEvent) {
    let event = 'message';
    const dataLines = [];
    for (const line of rawEvent.split('\n')) {
      if (line.startsWith('event:')) {
        event = line.slice(6).trim();
      } else if (line.startsWith('data:')) {
        dataLines.push(line.slice(5).replace(/^ /, ''));
      }
    }
    return { event, data: dataLines.length ? dataLines.join('\n') : null };
  }

  // ── Toggle popup ──────────────────────────────────────────────────────────
  function openPopup() {
    isOpen = true;
    popup.classList.add('visible');
    toggleBtn.classList.add('open');
    toggleBtn.setAttribute('aria-label', t.closeChat);
    input.focus();
    scrollToBottom();
  }

  function closePopup() {
    isOpen = false;
    popup.classList.remove('visible');
    toggleBtn.classList.remove('open');
    toggleBtn.setAttribute('aria-label', t.openChat);
  }

  // ── Auto-resize textarea ──────────────────────────────────────────────────
  function autoResize() {
    input.style.height = 'auto';
    input.style.height = Math.min(input.scrollHeight, 100) + 'px';
  }

  // ── Event listeners ───────────────────────────────────────────────────────
  toggleBtn.addEventListener('click', () => {
    if (!isOpen) {
      openPopup();
      // Load session + history on first open
      if (!token) {
        ensureSession().then(() => loadHistory());
      } else {
        loadHistory();
      }
    } else {
      closePopup();
    }
  });

  input.addEventListener('input', () => {
    autoResize();
    sendBtn.disabled = input.value.trim().length === 0 || isWaiting;
  });

  input.addEventListener('keydown', (e) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault();
      sendMessage();
    }
  });

  sendBtn.addEventListener('click', sendMessage);

  // Close popup on Escape
  document.addEventListener('keydown', (e) => {
    if (e.key === 'Escape' && isOpen) closePopup();
  });

  // ── Init ──────────────────────────────────────────────────────────────────
  loadConfig();

})();
