# Configuration Reference

## Backend Environment Variables

| Variable | Default | Required | Description |
|---|---|---|---|
| `OWNER_NAME` | `PandaC` | ✅ | Your name — shown in the chat widget header |
| `CHAT_TITLE` | `Chat with Me` | ✅ | Chat popup title text |
| `CHAT_AVATAR_INITIAL` | `P` | ✅ | Single letter shown in the avatar bubble |
| `TELEGRAM_BOT_TOKEN` | — | ✅ | Bot token from @BotFather |
| `TELEGRAM_ADMIN_CHAT_ID` | — | ✅ | Your Telegram user ID (from @userinfobot) |
| `JWT_SECRET` | — | ✅ | Long random hex string — `openssl rand -hex 64` |
| `H2_PASSWORD` | — | ✅ | **Alphanumeric only** — no special characters |
| `AI_PROVIDER` | `ollama` | — | Default provider: `ollama`, `openai`, `anthropic`, `mistral`, or `deepseek` — see [AI Providers](#ai-providers) |
| `OLLAMA_BASE_URL` | `http://localhost:11434` | — | Ollama API base URL |
| `OLLAMA_MODEL` | `llama3.2` | — | Model name (e.g. `gemma3`, `mistral`) |
| `OLLAMA_TEMPERATURE` | `0.3` | — | Response creativity (0.0 = precise, 1.0 = creative) |
| `OLLAMA_MAX_TOKENS` | `150` | — | Max tokens per response (keep short for chat) |
| `OLLAMA_API_KEY` | _(empty)_ | — | API key for Ollama Cloud; leave empty for local |
| `OPENAI_API_KEY` | _(empty)_ | — | Required to use `AI_PROVIDER=openai` |
| `OPENAI_MODEL` | `gpt-4o-mini` | — | OpenAI model name |
| `OPENAI_TEMPERATURE` | `0.3` | — | Response creativity |
| `ANTHROPIC_API_KEY` | _(empty)_ | — | Required to use `AI_PROVIDER=anthropic` |
| `ANTHROPIC_MODEL` | `claude-sonnet-5` | — | Anthropic model name |
| `ANTHROPIC_TEMPERATURE` | `0.3` | — | Response creativity |
| `ANTHROPIC_MAX_TOKENS` | `1024` | — | Anthropic requires a max-tokens cap on every request |
| `MISTRAL_API_KEY` | _(empty)_ | — | Required to use `AI_PROVIDER=mistral` |
| `MISTRAL_MODEL` | `mistral-small-latest` | — | Mistral model name |
| `MISTRAL_TEMPERATURE` | `0.3` | — | Response creativity |
| `DEEPSEEK_API_KEY` | _(empty)_ | — | Required to use `AI_PROVIDER=deepseek` |
| `DEEPSEEK_MODEL` | `deepseek-chat` | — | DeepSeek model name |
| `DEEPSEEK_TEMPERATURE` | `0.3` | — | Response creativity |
| `MCP_RAG_URL` | _(empty)_ | — | Optional — URL of an external MCP server for RAG/tools, see [RAG / Tools via MCP](../README.md#rag--tools-via-mcp-optional) |
| `MCP_RAG_ENDPOINT` | `/mcp` | — | MCP Streamable HTTP endpoint path on that server |
| `MCP_ENABLED` | `false` | — | Default for whether personas can use MCP tools; override per persona with `mcp=` |
| `CHAT_MODE` | `AI` | — | `AI` — configured provider responds / `MANUAL` — Telegram relay |
| `CORS_ALLOWED_ORIGIN` | `https://pandac.in` | ✅ | Your website's exact origin URL |
| `OWNER_WEBSITE_URL` | _(empty)_ | — | Optional — shown in the AI's fallback reply if it can't respond |
| `HOST_PORT` | `9097` | — | Host port mapped to container's 8080 |

---

## Widget `data-*` Attributes

| Attribute | Default | Description |
|---|---|---|
| `data-api-url` | `http://localhost:8080` | Your backend's URL (no trailing slash) |
| `data-theme` | `dark` | `dark` or `light` |
| `data-accent` | `#6C63FF` | Primary accent color (hex, rgb, hsl — any CSS color) |
| `data-persona` | *(none)* | Persona id to chat with, for multi-persona backends (see "Multiple Personas" in the README). Omit to use the backend's default persona. |
| `data-lang` | *(browser language)* | UI language: `en`, `es`, `fr`, `de`, `pt`, `hi`, `ja`, `zh`. Unsupported codes fall back to English. |

---

## Embed Snippet (CDN via jsDelivr)

```html
<script
  src="https://cdn.jsdelivr.net/gh/pandaind/poptalk@master/widget/dist/poptalk.min.js"
  data-api-url="https://your-backend.com"
  data-accent="#6C63FF"
  data-theme="dark"
  defer>
</script>
```

> **Tip:** For production stability, use a tagged release URL instead of `@master`:
> ```
> https://cdn.jsdelivr.net/gh/pandaind/poptalk@v1.0.0/widget/dist/poptalk.min.js
> ```
> Tag a release with: `git tag v1.0.0 && git push --tags`

---

## Backend API Endpoints

| Method | Path | Auth | Description |
|---|---|---|---|
| `GET` | `/api/config` | None | Returns public branding config. Accepts `?persona=<id>`. |
| `POST` | `/api/v1/chat` | None | Registers a visitor (name/contact/contactType), returns JWT. Accepts `?persona=<id>`. |
| `POST` | `/api/v1/chat/message` | Bearer | Sends a message, returns the AI/manual reply |
| `GET` | `/api/v1/chat/history` | Bearer | Returns message history |
| `GET` | `/api/v1/chat/reply` | Bearer | Polls for a new admin reply in MANUAL mode (204 if none) |
| `POST` | `/api/v1/chat/end` | Bearer | Ends the session |

**Register request / response:**
```json
// POST /api/v1/chat
{ "name": "Jane Visitor", "contact": "jane@example.com", "contactType": "email" }

// Response
{ "token": "eyJ...", "sessionId": "abc123", "message": "Welcome! How can I help you today?" }
```

**Message request / response:**
```json
// POST /api/v1/chat/message
{ "message": "Tell me about your projects" }

// Response
{ "messageId": "...", "timestamp": "...", "response": "I've been working on a few interesting things lately..." }
```

---

## Rate Limits

| Limit | Default | Config key |
|---|---|---|
| Session registrations per IP/minute | 5 | `app.rate-limit.requests-per-minute` |
| Messages per session per hour | 20 | `app.rate-limit.messages-per-session-per-hour` |

---

## H2 Database

The database file is at `./data/pandac-chat.mv.db`, volume-mounted in Docker.

> **Important:** If you change `H2_PASSWORD`, you **must** delete the `.mv.db` file. H2 stores the password hash inside the database file — a changed env var causes an immediate connection failure on startup.
>
> ```bash
> rm -f backend/data/pandac-chat.mv.db
> docker-compose up -d
> ```

Sessions older than 7 days are automatically purged by the `SessionCleanupScheduler`.

---

## AI Persona

The AI is instructed to speak as the persona described in its context file —
in first person if that's an individual, or as "we" if it's a business/brand.
Its identity comes entirely from `backend/data/context.txt`.

The system rules in `application.yml` are generic and work for either voice —
you don't need to edit them. You only need to maintain your `context.txt`.

For serving more than one persona from the same backend, see "Multiple Personas"
in the [README](../README.md#multiple-personas). Relevant `application.yml` keys:

| Key | Default | Description |
|---|---|---|
| `app.ai.context-file` | `data/context.txt` | Single-persona fallback, used when no persona subdirectories exist |
| `app.ai.personas-dir` | `data/personas` | Directory scanned for persona subdirectories |
| `app.ai.default-persona` | `default` | Persona id used when `data-persona` is omitted or unknown |
| `app.ai.default-provider` | `ollama` (via `AI_PROVIDER`) | Provider personas use unless their own `persona.properties` overrides it |

Personas are loaded once at startup — adding or editing one requires a restart.

---

## AI Providers

See "AI Providers" in the [README](../README.md#ai-providers) for the full picture —
each persona can use a different provider (`ollama`, `openai`, `anthropic`, `mistral`,
or `deepseek`), set via `provider=` in its `persona.properties`, with `model=` and
`temperature=` overrides available too. Every provider's `ChatModel` is wired up
regardless of which one is active (see `backend/src/main/java/in/pandac/chat/config/`);
one left with a blank API key just won't be available, and a persona pointed at an
unconfigured provider falls back to the AI's generic "trouble connecting" reply.

---

## RAG / Tools via MCP

See "RAG / Tools via MCP" in the [README](../README.md#rag--tools-via-mcp-optional)
for the full picture. Relevant keys, all optional and blank/off by default:

| Key | Env var | Description |
|---|---|---|
| `app.mcp.rag-url` | `MCP_RAG_URL` | External MCP server URL (Streamable HTTP). Blank = disabled entirely. |
| `app.mcp.rag-endpoint` | `MCP_RAG_ENDPOINT` | Endpoint path on that server (default `/mcp`) |
| `app.ai.default-mcp-enabled` | `MCP_ENABLED` | Default for whether personas use it; override per persona with `mcp=` in `persona.properties` |
| — | `mcp-api-key=` (persona.properties only) | That persona's own API key for the RAG server — the only source of tenant identity; must be unique per persona, not shared |

Wired in `backend/src/main/java/in/pandac/chat/config/McpRagConfig.java` —
one MCP client per `mcp=true` persona (not one shared client), each
authenticated with its own key. A connection attempt happens once at
startup per persona; if the server is unreachable, a warning is logged and
that persona's tools stay unavailable until the backend restarts with the
server reachable — chat itself keeps working either way.
