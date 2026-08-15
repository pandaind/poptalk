# Getting Started

## Prerequisites

- Docker & Docker Compose
- A running [Ollama](https://ollama.ai) instance (local or cloud) — for AI mode
- A [Telegram bot](https://t.me/BotFather) — for notifications or manual mode

---

## Step 1 — Create Your Telegram Bot

1. Open Telegram and message **@BotFather**
2. Send `/newbot` and follow the prompts
3. Copy the **bot token** (format: `1234567890:ABC...`)
4. Find your **chat ID**: message **@userinfobot** on Telegram — it will reply with your ID

---

## Step 2 — Clone and Configure

```bash
git clone https://github.com/pandaind/pandac-chat.git
cd pandac-chat/backend
cp .env.example .env
```

Open `.env` in your editor and fill in:

```env
OWNER_NAME=Your Name
CHAT_TITLE=Chat with Me
CHAT_AVATAR_INITIAL=Y
TELEGRAM_BOT_TOKEN=your_token_here
TELEGRAM_ADMIN_CHAT_ID=your_chat_id_here
JWT_SECRET=<output of: openssl rand -hex 64>
H2_PASSWORD=SomeAlphanumericPassword123
OLLAMA_BASE_URL=http://localhost:11434
OLLAMA_MODEL=llama3.2
CHAT_MODE=AI
CORS_ALLOWED_ORIGIN=https://yourblog.com
```

> ⚠️ `H2_PASSWORD` must be alphanumeric only. Special characters (`$`, `!`, `@`) break JDBC URL parsing.

---

## Step 3 — Write Your Personal Context

Edit `backend/data/personal-context.txt`. Write about yourself **in first person** — this is what the AI reads to impersonate you.

Example opening:
```
## Who I Am
I'm Jane Doe, a full-stack developer with 8 years of experience...
```

The more detail you provide, the better the AI will represent you.

---

## Step 4 — Deploy

```bash
cd backend
docker-compose up -d
```

Check it's running:
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

A floating chat button will appear in the bottom-right of your site.
The widget automatically fetches your branding from the backend — no hardcoding needed.

---

## Switching to Manual Mode

To answer chats yourself via Telegram instead of AI:

```env
CHAT_MODE=MANUAL
```

Restart the container. Messages will now forward to your Telegram. Reply via your bot.

---

## Updating Your Context Without Rebuilding

The `personal-context.txt` file is volume-mounted into the container:

```bash
# Edit the file
vim backend/data/personal-context.txt

# Restart to reload (Spring reads it at startup)
docker-compose restart backend
```
