# Getting Started

## Prerequisites

- Docker & Docker Compose (for the backend)
- [Ollama](https://ollama.ai) running locally or a cloud Ollama API key — for AI mode
- A [Telegram bot](https://t.me/BotFather) — for Telegram notifications or manual mode

---

## Step 1 — Create Your Telegram Bot

1. Open Telegram, message **@BotFather**, send `/newbot`
2. Copy the **bot token** (e.g. `1234567890:ABC...`)
3. Get your **chat ID**: message **@userinfobot** and it replies with your ID

---

## Step 2 — Clone and Configure

```bash
git clone https://github.com/pandaind/poptalk.git
cd poptalk/backend
cp .env.example .env
```

Open `.env` and fill in at minimum:

```env
OWNER_NAME=Your Name
CHAT_TITLE=Chat with Me
CHAT_AVATAR_INITIAL=Y

TELEGRAM_BOT_TOKEN=your_token_here
TELEGRAM_ADMIN_CHAT_ID=your_chat_id_here

JWT_SECRET=$(openssl rand -hex 64)
H2_PASSWORD=SomeAlphanumericPassword123

OLLAMA_BASE_URL=http://localhost:11434
OLLAMA_MODEL=llama3.2
CHAT_MODE=AI
CORS_ALLOWED_ORIGIN=https://yourwebsite.com
```

> ⚠️ **`H2_PASSWORD` must be alphanumeric only.** Special characters like `$`, `!`, `@`, `#` break H2 JDBC URL parsing and will prevent the app from starting.

---

## Step 3 — Write Your Context

Edit `backend/data/context.txt`. Describe yourself in first person, or your
business speaking as "we" — this is what the AI reads to answer as that persona.

```text
## Who I Am
I'm Jane Doe, a full-stack developer with 8 years of experience...

## My Tech Stack
I primarily work with Go, Python, and React...
```

Or, for a business persona:

```text
## Who We Are
We're Acme Widgets, a small team building browser extensions for developers...

## What We Offer
Our flagship product is Acme Inspector, a DevTools panel for...
```

The more detail you provide, the more accurately the AI represents that persona.

---

## Step 4 — Deploy

```bash
cd backend
docker-compose up -d
```

Verify it's running:

```bash
curl http://localhost:9097/api/config
# {"ownerName":"Your Name","chatTitle":"Chat with Me","avatarInitial":"Y"}
```

---

## Step 5 — Embed the Widget

Add this snippet to your site's HTML before `</body>`:

```html
<script
  src="https://cdn.jsdelivr.net/gh/pandaind/poptalk@master/widget/dist/poptalk.min.js"
  data-api-url="https://your-backend-domain.com"
  data-accent="#6C63FF"
  defer>
</script>
```

The widget:
- Auto-fetches your name, title, and avatar from `GET /api/config`
- Creates a chat session (JWT) on first open — stored in `sessionStorage`
- Reloads conversation history on revisit within the same browser tab

---

## Step 6 — Test Locally (Optional)

Run the backend in dev mode:

```bash
cd backend
export $(grep -v '^#' .env | xargs)
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
# Backend: http://localhost:8080
# H2 Console: http://localhost:8080/h2-console
```

Run the widget dev server:

```bash
cd widget
npm install
npm run dev
# Opens http://localhost:5173 — live reload, points to http://localhost:8080
```

---

## Switching to Manual Mode

To answer chats yourself via Telegram instead of AI:

```env
CHAT_MODE=MANUAL
```

Restart the container. Incoming messages are forwarded to your Telegram. Reply via your bot — the reply is delivered back to the visitor's chat window.

---

## Updating Your Context Without Rebuilding

`context.txt` is volume-mounted into the container at `/app/data/`. You can update it without a full rebuild:

```bash
vim backend/data/context.txt
docker-compose restart backend
```
