<div align="center">

# 💬 pandac-chat

**An AI-powered personal chat widget you can embed on any blog in one line.**

[![Backend CI](https://github.com/pandaind/pandac-chat/actions/workflows/backend-ci.yml/badge.svg)](https://github.com/pandaind/pandac-chat/actions)
[![License: MIT](https://img.shields.io/badge/License-MIT-6C63FF.svg)](LICENSE)
[![Java](https://img.shields.io/badge/Java-21-orange.svg)](https://openjdk.org/projects/jdk/21/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.3-green.svg)](https://spring.io/projects/spring-boot)

*The AI doesn't answer as a generic assistant — it answers **as you**.*

</div>

---

## What is this?

`pandac-chat` is a self-hostable chat system where an AI model impersonates **you** on your blog.
You write a `personal-context.txt` describing yourself, deploy the backend, embed one `<script>` tag,
and visitors can have a conversation with your AI persona.

Built with **Spring Boot + Apache Camel + Spring AI (Ollama)** on the backend,
and a **zero-dependency, Shadow DOM-isolated vanilla JS widget** on the frontend.

---

## Architecture

```
┌──────────────────────────────────┐
│  Blog / Static Site              │
│  <script src="pandac-chat.min.js"│──── one line embed
│         data-api-url="...">      │
└─────────────┬────────────────────┘
              │  REST (HTTPS)
              ▼
┌──────────────────────────────────┐
│  pandac-chat Backend             │
│  Spring Boot + Apache Camel      │
│  ┌──────────┐  ┌──────────────┐  │
│  │ JWT Auth │  │ Rate Limiter │  │
│  └──────────┘  └──────────────┘  │
│  ┌───────────────────────────┐   │
│  │ AI Mode  → Ollama / Cloud │   │
│  │ Manual → Telegram Bot     │   │
│  └───────────────────────────┘   │
│  H2 file DB (sessions + history) │
└──────────────────────────────────┘
```

---

## Quick Start (5 minutes)

### 1. Clone & configure

```bash
git clone https://github.com/pandaind/pandac-chat.git
cd pandac-chat/backend
cp .env.example .env
```

Edit `.env` with your values. At minimum:
- `OWNER_NAME`, `CHAT_TITLE`, `CHAT_AVATAR_INITIAL` — your branding
- `H2_PASSWORD` — alphanumeric only (special chars break H2)
- `JWT_SECRET` — `openssl rand -hex 64`
- `TELEGRAM_BOT_TOKEN` + `TELEGRAM_ADMIN_CHAT_ID` — from @BotFather

### 2. Write your context

Edit `backend/data/personal-context.txt` — write about yourself in first person.
This is what the AI reads to know how to speak as you.

### 3. Deploy

```bash
docker-compose up -d
```

Backend is now running on port `9097` (configurable via `HOST_PORT`).

### 4. Embed the widget

Add this to your blog's HTML, before `</body>`:

```html
<script
  src="https://cdn.jsdelivr.net/gh/pandaind/pandac-chat@master/widget/dist/pandac-chat.min.js"
  data-api-url="https://your-backend-url.com"
  data-accent="#6C63FF"
  data-theme="dark"
  defer>
</script>
```

That's it. A floating chat button appears in the bottom-right corner of your site.

---

## Widget Options

| Attribute | Default | Description |
|---|---|---|
| `data-api-url` | `http://localhost:8080` | URL of your deployed backend |
| `data-theme` | `dark` | `dark` or `light` |
| `data-accent` | `#6C63FF` | Accent color (hex or CSS color) |

---

## Chat Modes

Set `CHAT_MODE` in your `.env`:

| Mode | Behaviour |
|---|---|
| `AI` | Ollama model responds automatically using your personal context |
| `MANUAL` | Messages are forwarded to your Telegram. You reply via Telegram bot. |

Switch modes by changing the env var and restarting — no rebuild needed.

---

## Project Structure

```
pandac-chat/
├── backend/      Spring Boot + Camel + Spring AI backend
├── widget/       Embeddable vanilla JS chat popup
├── docs/         Getting started & configuration guides
└── .github/      CI/CD workflows
```

---

## Development

### Backend
```bash
cd backend
cp .env.example .env && vim .env
export $(cat .env | xargs)
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

### Widget (with live reload)
```bash
cd widget
npm install
npm run dev      # Vite dev server at http://localhost:5173
```

---

## Documentation

- [Getting Started](docs/getting-started.md)
- [Configuration Reference](docs/configuration.md)

---

## License

[MIT](LICENSE) © [Chittaranjan Panda](https://pandac.in)
