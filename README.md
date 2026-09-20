<div align="center">

# 💬 PopTalk

**A self-hostable AI chat widget you can drop into any website — one script tag.**

[![Backend CI](https://github.com/pandaind/poptalk/actions/workflows/backend-ci.yml/badge.svg)](https://github.com/pandaind/poptalk/actions)
[![License: MIT](https://img.shields.io/badge/License-MIT-6C63FF.svg)](LICENSE)
[![Java 21](https://img.shields.io/badge/Java-21-orange.svg)](https://openjdk.org/projects/jdk/21/)
[![Spring Boot 3.3](https://img.shields.io/badge/Spring%20Boot-3.3-green.svg)](https://spring.io/projects/spring-boot)

*Not a generic assistant — it answers as the persona you give it: you, your business, a support bot, whatever fits your site.*

</div>

---

## What is this?

PopTalk is a self-hostable chat widget backed by an LLM that answers using content you provide — on a portfolio, a business site, a SaaS product page, docs, an online store, or anywhere you can drop a `<script>` tag.

You write a `context.txt` file describing what the AI should know — yourself, in first person, or your business, speaking as "we" — deploy the backend, embed one `<script>` tag, and visitors can chat with it directly on your site. One backend can also serve multiple personas at once (see [Multiple Personas](#multiple-personas)) — handy for an agency, a team, or a business running several sites.

**Backend:** Spring Boot + Apache Camel + Spring AI (Ollama, OpenAI, Anthropic, Mistral, or DeepSeek — see [AI Providers](#ai-providers))  
**Widget:** Zero-dependency vanilla JS, Shadow DOM isolated, ~17 kB / ~7 kB gzipped

---

## Quick Start (5 minutes)

### 1. Clone & configure

```bash
git clone https://github.com/pandaind/poptalk.git
cd poptalk/backend
cp .env.example .env
# Edit .env with your values
```

> ⚠️ `H2_PASSWORD` must be **alphanumeric only** — special characters (`$`, `!`, `@`) break JDBC parsing.

### 2. Write your context

Edit `backend/data/context.txt` — describe yourself in first person, or your
business speaking as "we." This is what the AI reads to decide how to answer.

### 3. Deploy

```bash
docker-compose up -d
```

Test it's working:
```bash
curl http://localhost:9097/api/config
# {"ownerName":"Your Name","chatTitle":"Chat with Me","avatarInitial":"Y"}
```

### 4. Embed the widget

Add this before `</body>` on any site — portfolio, business site, docs, product page:

```html
<script
  src="https://cdn.jsdelivr.net/gh/pandaind/poptalk@master/widget/dist/poptalk.min.js"
  data-api-url="https://your-backend-url.com"
  data-accent="#6C63FF"
  defer>
</script>
```

A floating chat button appears in the bottom-right of your site. Done.

---

## Widget Options

| Attribute | Default | Description |
|---|---|---|
| `data-api-url` | `http://localhost:8080` | URL of your deployed backend |
| `data-theme` | `dark` | `dark` or `light` |
| `data-accent` | `#6C63FF` | Primary accent color (any CSS color) |
| `data-persona` | *(none)* | Which persona to chat with, for multi-persona backends. Omit to use the backend's default persona. |
| `data-lang` | *(browser language)* | UI language: `en`, `es`, `fr`, `de`, `pt`, `hi`, `ja`, `zh`. Falls back to English for unsupported codes. |

The widget auto-fetches your name, title, and avatar initial from the backend — no hardcoding needed.

---

## Multiple Personas

One backend can serve several personas — useful for an agency, a team, or a
business running more than one site or brand from a single deployment. A
persona isn't necessarily a person: it's whatever voice its `context.txt`
describes — an individual speaking in first person, or a business speaking as
"we." The shared rules (in `application.yml`) work for either.

By default (no extra setup), the backend runs in single-persona mode using
`backend/data/context.txt` and the `OWNER_NAME` / `CHAT_TITLE` /
`CHAT_AVATAR_INITIAL` env vars, exactly as described above.

To add more personas, create a subdirectory per persona under `backend/data/personas/`:

```
backend/data/personas/
├── alice/
│   ├── context.txt              ← required, same format as the single-file version
│   └── persona.properties       ← optional — overrides branding for this persona
└── acme-support/
    ├── context.txt
    └── persona.properties
```

`persona.properties` supports:

```properties
ownerName=Alice Smith
chatTitle=Chat with Alice
avatarInitial=A
websiteUrl=https://alice.example.com
provider=anthropic
model=claude-opus-5
temperature=0.7
mcp=true
```

Any property left out falls back to the global `app.branding.*` / `app.ai.*` values
(see [AI Providers](#ai-providers) for `provider`/`model`/`temperature`). As soon as
`backend/data/personas/` contains at least one valid subdirectory, multi-persona
mode activates automatically — the directory name becomes the persona id.

Point each embed at its persona with `data-persona`:

```html
<script src="…/poptalk.min.js" data-api-url="https://your-backend.com" data-persona="alice" defer></script>
```

Personas are loaded once at startup — like the single-file setup, adding or
editing a persona requires a backend restart.

---

## AI Providers

PopTalk isn't tied to Ollama — it can talk to Ollama, OpenAI, Anthropic (Claude),
Mistral AI, or DeepSeek, and **different personas can each use a different one**
at the same time (one persona on a free local Ollama model, another on Claude,
for example).

Every provider is wired up regardless of which one you use — just leave the ones
you're not using with a blank API key in `.env`; they simply won't be available.
Pick the default with `AI_PROVIDER` (falls back to `ollama`):

```env
AI_PROVIDER=anthropic
ANTHROPIC_API_KEY=sk-ant-...
ANTHROPIC_MODEL=claude-sonnet-5
```

| Provider | Env var prefix | Needs |
|---|---|---|
| `ollama` | `OLLAMA_*` | Nothing — local install, or an Ollama Cloud key |
| `openai` | `OPENAI_*` | `OPENAI_API_KEY` |
| `anthropic` | `ANTHROPIC_*` | `ANTHROPIC_API_KEY` |
| `mistral` | `MISTRAL_*` | `MISTRAL_API_KEY` |
| `deepseek` | `DEEPSEEK_*` | `DEEPSEEK_API_KEY` |

See `.env.example` for the full list of `*_MODEL` / `*_TEMPERATURE` / `*_BASE_URL`
variables per provider.

For multi-persona deployments, override per persona in its `persona.properties`
(`provider`, `model`, `temperature` — shown above); anything left unset falls
back to `AI_PROVIDER` and that provider's configured defaults.

> Google Gemini isn't wired up yet — Spring AI's only Gemini integration goes
> through Vertex AI, which needs a GCP project rather than a simple API key.
> Open an issue if you want it added.

---

## RAG / Tools via MCP (optional)

PopTalk can plug into an external [MCP](https://modelcontextprotocol.io) server
for retrieval-augmented generation or any other tool — deliberately kept as a
**separate project**, not something built into this repo. RAG (chunking,
embeddings, a vector store) is a different problem from "serve a chat widget,"
so PopTalk connects to whatever RAG/tool server you run, over MCP's Streamable
HTTP transport, rather than owning that logic itself.

[**poptalk-rag**](https://github.com/pandaind/poptalk-rag) is a companion
project that implements exactly this: a Spring Boot MCP server backed by
Postgres/pgvector, with a Camel pipeline that auto-ingests a knowledge base
per persona.

Left unconfigured, nothing changes — no connection is attempted, no dependency
is exercised. To plug one in:

```env
MCP_RAG_URL=https://your-rag-server.example.com
MCP_RAG_ENDPOINT=/mcp        # default
MCP_ENABLED=true             # default for all personas; override per persona below
```

Enable it per persona in `persona.properties` (see [Multiple Personas](#multiple-personas)),
with that persona's own API key for the RAG server:

```properties
mcp=true
mcp-api-key=alice-key-123
```

**Each persona needs its own key, not a shared one.** PopTalk opens one MCP
connection per `mcp=true` persona, authenticated with that persona's key —
the RAG server derives which tenant's data to search from the key itself,
never from a header or a field the model could be prompt-injected into
supplying. A shared key across personas would mean no real isolation between
their knowledge bases.

When enabled, the model can call whatever tools your MCP server exposes as
part of answering — poptalk-rag exposes a `search_knowledge_base` tool the
model calls when it needs more context than `context.txt` gives it. If the
server is unreachable, that persona's tools are simply unavailable (logged as
a warning at startup) — chat still works normally, just without that extra
context, until the backend is restarted with the server reachable again.

---

## Chat Modes

Set `CHAT_MODE` in your `.env`:

| Mode | Behaviour |
|---|---|
| `AI` | The configured AI provider responds automatically using your `context.txt` |
| `MANUAL` | Messages are forwarded to your Telegram. You reply via the Telegram bot. |

Switch by changing the env var and restarting — no rebuild needed.

---

## Project Structure

```
poptalk/
├── backend/          Spring Boot + Camel + Spring AI backend
│   ├── src/
│   ├── data/
│   │   └── context.txt            ← edit this to define your persona
│   ├── Dockerfile
│   ├── docker-compose.yml
│   └── .env.example
├── widget/           Embeddable vanilla JS chat popup
│   ├── src/
│   │   ├── poptalk.js
│   │   ├── styles.css
│   │   └── icons.js
│   ├── dist/
│   │   └── poptalk.min.js         ← built bundle (served via jsDelivr)
│   └── index.html                 ← local dev preview page
├── docs/             Full documentation
├── .github/          CI/CD workflows
├── LICENSE
└── README.md
```

---

## Development

### Backend

```bash
cd backend
cp .env.example .env && vim .env
export $(grep -v '^#' .env | xargs)
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
# Backend running at http://localhost:8080
# H2 console at http://localhost:8080/h2-console
```

### Widget (live reload)

```bash
cd widget
npm install
npm run dev
# Dev server at http://localhost:5173
# Points to backend at http://localhost:8080 by default
```

To produce a minified bundle for distribution:

```bash
npm run build
# Output: dist/poptalk.min.js (~17 kB / ~7 kB gzipped)
```

---

## CI/CD

| Workflow | Triggers when | Action |
|---|---|---|
| **Backend CI** | Push to `backend/` | Maven build + test (Java 21) |
| **Widget Build** | Push to `widget/src/` | Vite build → auto-commits `dist/` |

---

## Documentation

- [Getting Started](docs/getting-started.md)
- [Configuration Reference](docs/configuration.md)
- [Architecture](docs/architecture.md)

---

## License

[MIT](LICENSE) © [Chittaranjan Panda](https://pandac.in)
