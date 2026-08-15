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
git clone https://github.com/pandaind/pandac-chat.git
cd pandac-chat/backend
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
CORS_ALLOWED_ORIGIN=https://yourblog.com
```

> ⚠️ **`H2_PASSWORD` must be alphanumeric only.** Special characters like `$`, `!`, `@`, `#` break H2 JDBC URL parsing and will prevent the app from starting.

---

## Step 3 — Write Your Personal Context

Edit `backend/data/personal-context.txt`. Write about yourself **in first person** — this is what the AI reads to impersonate you.

```text
## Who I Am
I'm Jane Doe, a full-stack developer with 8 years of experience...

## My Tech Stack
I primarily work with Go, Python, and React...
```

The more detail you provide, the more accurately the AI represents you.

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

Add this snippet to your blog's HTML before `</body>`:

```html
<script
  src="https://cdn.jsdelivr.net/gh/pandaind/pandac-chat@master/widget/dist/pandac-chat.min.js"
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

`personal-context.txt` is volume-mounted into the container at `/app/data/`. You can update it without a full rebuild:

```bash
vim backend/data/personal-context.txt
docker-compose restart backend
```
