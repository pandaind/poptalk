# Configuration Reference

## Backend Environment Variables

| Variable | Default | Required | Description |
|---|---|---|---|
| `OWNER_NAME` | `PandaC` | ✅ | Your name — shown in widget header |
| `CHAT_TITLE` | `Chat with Me` | ✅ | Chat popup title |
| `CHAT_AVATAR_INITIAL` | `P` | ✅ | Single letter for the avatar bubble |
| `TELEGRAM_BOT_TOKEN` | — | ✅ | Bot token from @BotFather |
| `TELEGRAM_ADMIN_CHAT_ID` | — | ✅ | Your Telegram user ID |
| `JWT_SECRET` | — | ✅ | Long random hex string (min 64 chars) |
| `H2_PASSWORD` | — | ✅ | Alphanumeric only — no special chars |
| `OLLAMA_BASE_URL` | `http://localhost:11434` | — | Ollama API base URL |
| `OLLAMA_MODEL` | `llama3.2` | — | Model name |
| `OLLAMA_TEMPERATURE` | `0.3` | — | Response randomness (0.0–1.0) |
| `OLLAMA_MAX_TOKENS` | `150` | — | Max response length |
| `OLLAMA_API_KEY` | _(empty)_ | — | API key for Ollama Cloud |
| `CHAT_MODE` | `AI` | — | `AI` or `MANUAL` |
| `CORS_ALLOWED_ORIGIN` | `https://pandac.in` | ✅ | Your blog's origin URL |
| `HOST_PORT` | `9097` | — | Host port for Docker |

---

## Widget `data-*` Attributes

| Attribute | Default | Description |
|---|---|---|
| `data-api-url` | `http://localhost:8080` | Your backend URL (no trailing slash) |
| `data-theme` | `dark` | `dark` or `light` |
| `data-accent` | `#6C63FF` | Primary accent color |

---

## Backend API Endpoints

| Method | Path | Auth | Description |
|---|---|---|---|
| `GET` | `/api/config` | None | Returns branding config (title, owner, initial) |
| `POST` | `/api/chat/register` | None | Creates a chat session, returns JWT |
| `POST` | `/api/chat/message` | Bearer | Sends a message, returns AI/manual reply |
| `GET` | `/api/chat/history` | Bearer | Returns message history for session |
| `DELETE` | `/api/chat/end` | Bearer | Ends the chat session |

---

## Rate Limits

| Limit | Default | Config Key |
|---|---|---|
| Session registrations per IP/minute | 5 | `app.rate-limit.requests-per-minute` |
| Messages per session per hour | 20 | `app.rate-limit.messages-per-session-per-hour` |

---

## AI Persona Rules

The AI's behavior is controlled by two sources:

1. **`data/personal-context.txt`** — Who you are (written in first person)
2. **`app.ai.rules` in `application.yml`** — How the AI should behave (generic rules, don't need editing)

You only need to edit `personal-context.txt`. The rules in `application.yml` are designed to work with any context file.

---

## H2 Database

The database file lives at `./data/pandac-chat.mv.db`. It's volume-mounted in Docker.

> **Important:** If you ever change `H2_PASSWORD`, you must delete the `.mv.db` file and let H2 recreate it. The password hash is stored inside the file — changing the env var alone will cause a connection failure.

Sessions older than 7 days are automatically purged by the `SessionCleanupScheduler`.
