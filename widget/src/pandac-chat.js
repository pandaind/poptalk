import { ICON_CHAT, ICON_CLOSE, ICON_SEND } from './icons.js';
import rawCSS from './styles.css?inline';

(function () {
  'use strict';

  // ── Read config from script tag data-* attributes ─────────────────────────
  const script = document.currentScript ||
    document.querySelector('script[data-api-url]');

  const cfg = {
    apiUrl: (script?.dataset.apiUrl || 'http://localhost:8080').replace(/\/$/, ''),
    theme:  script?.dataset.theme  || 'dark',
    accent: script?.dataset.accent || '#6C63FF',
  };

  // ── State ─────────────────────────────────────────────────────────────────
  let token     = sessionStorage.getItem('pc_token')     || null;
  let sessionId = sessionStorage.getItem('pc_session_id')|| null;
  let isOpen    = false;
  let isWaiting = false;

  // ── Shadow DOM host ───────────────────────────────────────────────────────
  const host = document.createElement('div');
  host.id = 'pandac-chat-host';
  const shadow = host.attachShadow({ mode: 'closed' });
  document.body.appendChild(host);

  // ── Inject styles ─────────────────────────────────────────────────────────
  const sheet = new CSSStyleSheet();
  sheet.replaceSync(rawCSS);
  shadow.adoptedStyleSheets = [sheet];

  // ── Inject Google Font ────────────────────────────────────────────────────
  if (!document.querySelector('#pc-inter-font')) {
    const link = document.createElement('link');
    link.id   = 'pc-inter-font';
    link.rel  = 'stylesheet';
    link.href = 'https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600;700&display=swap';
    document.head.appendChild(link);
  }

  // ── Build DOM ─────────────────────────────────────────────────────────────
  shadow.innerHTML = `
    <button id="pc-toggle" aria-label="Open chat" title="Chat with me">
      <span class="pc-icon pc-icon-chat">${ICON_CHAT}</span>
      <span class="pc-icon pc-icon-close">${ICON_CLOSE}</span>
    </button>

    <div id="pc-popup" role="dialog" aria-modal="true" aria-label="Chat popup">
      <div id="pc-header">
        <div class="pc-avatar" id="pc-avatar">P</div>
        <div class="pc-header-text">
          <div class="pc-title" id="pc-title">Chat with Me</div>
          <div class="pc-status">Online</div>
        </div>
      </div>

      <div id="pc-messages" role="log" aria-live="polite">
        <p class="pc-welcome">👋 Ask me anything about my work, projects, or background!</p>
      </div>

      <div id="pc-input-area">
        <textarea
          id="pc-input"
          rows="1"
          placeholder="Type a message…"
          aria-label="Chat message"
          maxlength="500"
        ></textarea>
        <button id="pc-send" aria-label="Send message" disabled>${ICON_SEND}</button>
      </div>

      <div id="pc-footer">
        Powered by <a href="https://github.com/pandaind/pandac-chat" target="_blank" rel="noopener">pandac-chat</a>
      </div>
    </div>
  `;

  // ── Apply accent color ────────────────────────────────────────────────────
  const toggleBtn = shadow.getElementById('pc-toggle');
  const popup     = shadow.getElementById('pc-popup');
  const messages  = shadow.getElementById('pc-messages');
  const input     = shadow.getElementById('pc-input');
  const sendBtn   = shadow.getElementById('pc-send');
  const titleEl   = shadow.getElementById('pc-title');
  const avatarEl  = shadow.getElementById('pc-avatar');

  // Set CSS custom property for accent
  host.style.setProperty('--pc-accent', cfg.accent);
  shadow.host.style.setProperty('--pc-accent', cfg.accent);

  // ── Fetch branding from backend ───────────────────────────────────────────
  async function loadConfig() {
    try {
      const res = await fetch(`${cfg.apiUrl}/api/config`);
      if (res.ok) {
        const data = await res.json();
        if (data.chatTitle)     titleEl.textContent  = data.chatTitle;
        if (data.avatarInitial) avatarEl.textContent = data.avatarInitial.charAt(0).toUpperCase();
      }
    } catch (_) { /* use defaults */ }
  }

  // ── Session management ────────────────────────────────────────────────────
  async function ensureSession() {
    if (token && sessionId) return true;
    try {
      const res  = await fetch(`${cfg.apiUrl}/api/chat/register`, { method: 'POST' });
      if (!res.ok) throw new Error(`Register failed: ${res.status}`);
      const data = await res.json();
      token     = data.token;
      sessionId = data.sessionId;
      sessionStorage.setItem('pc_token',      token);
      sessionStorage.setItem('pc_session_id', sessionId);
      return true;
    } catch (err) {
      appendError('Could not connect. Please try again later.');
      console.error('[pandac-chat] session error:', err);
      return false;
    }
  }

  async function loadHistory() {
    if (!token) return;
    try {
      const res = await fetch(`${cfg.apiUrl}/api/chat/history`, {
        headers: { Authorization: `Bearer ${token}` }
      });
      if (!res.ok) return;
      const history = await res.json();
      if (history.length === 0) return;
      // Remove welcome message
      const welcome = messages.querySelector('.pc-welcome');
      if (welcome) welcome.remove();
      history.forEach(m => appendBubble(m.role === 'USER' ? 'user' : 'ai', m.content));
      scrollToBottom();
    } catch (_) { /* silent — fresh start */ }
  }

  // ── Message rendering ─────────────────────────────────────────────────────
  function appendBubble(role, text) {
    const div = document.createElement('div');
    div.className = `pc-msg ${role === 'user' ? 'pc-msg-user' : 'pc-msg-ai'}`;
    div.textContent = text;
    messages.appendChild(div);
    return div;
  }

  function appendError(text) {
    const div = appendBubble('ai', `⚠️ ${text}`);
    div.classList.add('pc-msg-error');
    scrollToBottom();
  }

  function showTyping() {
    const t = document.createElement('div');
    t.className = 'pc-typing';
    t.id = 'pc-typing';
    t.innerHTML = '<span></span><span></span><span></span>';
    messages.appendChild(t);
    scrollToBottom();
    return t;
  }

  function removeTyping() {
    shadow.getElementById('pc-typing')?.remove();
  }

  function scrollToBottom() {
    messages.scrollTop = messages.scrollHeight;
  }

  // ── Send message ──────────────────────────────────────────────────────────
  async function sendMessage() {
    const text = input.value.trim();
    if (!text || isWaiting) return;

    // Remove welcome message on first send
    messages.querySelector('.pc-welcome')?.remove();

    appendBubble('user', text);
    input.value = '';
    autoResize();
    scrollToBottom();

    isWaiting = true;
    sendBtn.disabled = true;
    const typing = showTyping();

    if (!(await ensureSession())) {
      removeTyping();
      isWaiting = false;
      sendBtn.disabled = false;
      return;
    }

    try {
      const res = await fetch(`${cfg.apiUrl}/api/chat/message`, {
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
        sessionStorage.removeItem('pc_token');
        sessionStorage.removeItem('pc_session_id');
        appendError('Session expired. Please send your message again.');
      } else if (res.status === 429) {
        appendError('Too many messages — slow down a bit!');
      } else if (!res.ok) {
        appendError('Something went wrong. Try again in a moment.');
      } else {
        const data = await res.json();
        appendBubble('ai', data.reply);
      }
    } catch (err) {
      removeTyping();
      appendError('Network error. Check your connection.');
      console.error('[pandac-chat] send error:', err);
    } finally {
      isWaiting = false;
      sendBtn.disabled = input.value.trim().length === 0;
      scrollToBottom();
    }
  }

  // ── Toggle popup ──────────────────────────────────────────────────────────
  function openPopup() {
    isOpen = true;
    popup.classList.add('visible');
    toggleBtn.classList.add('open');
    toggleBtn.setAttribute('aria-label', 'Close chat');
    input.focus();
    scrollToBottom();
  }

  function closePopup() {
    isOpen = false;
    popup.classList.remove('visible');
    toggleBtn.classList.remove('open');
    toggleBtn.setAttribute('aria-label', 'Open chat');
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
