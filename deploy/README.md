# Deploying PopTalk with its knowledge base

One Docker Compose stack: the chat backend, the knowledge service
([poptalk-rag](https://github.com/pandaind/poptalk-rag)) and the Postgres/pgvector database it
needs. Models run in your own Ollama (optional profile) or in a hosted provider.

```bash
cd deploy
cp .env.example .env              # fill in the secrets; see the comments in the file
mkdir -p data/personas/default knowledge/default
echo "You are the assistant for <your site>..." > data/personas/default/context.txt
docker compose up -d --build      # first build takes a few minutes
```

Check it:

```bash
curl http://127.0.0.1:9097/api/config
docker compose ps                 # backend, rag and db should all say healthy
```

## What is exposed

Only the backend, on `127.0.0.1:${BACKEND_PORT}`. The knowledge service and the database have no
published ports and are reachable only from the backend on the stack's own network. Put a TLS
reverse proxy in front for the public URL, for example Caddy:

```
chat.example.com {
    reverse_proxy 127.0.0.1:9097
}
```

Use `https://chat.example.com` as the widget's `api_url`, and set `CORS_ALLOWED_ORIGIN` to your
site's origin.

## Models

- **Hosted:** set `AI_PROVIDER` and the provider's key, and for embeddings set
  `EMBEDDING_PROVIDER=openai` with `EMBEDDING_DIMENSIONS=1536` and `OPENAI_API_KEY`. Set
  `RERANK_PROVIDER` to match (or `RERANK_ENABLED=false`).
- **Local:** `docker compose --profile ollama up -d`, then pull what you use:

  ```bash
  docker compose exec ollama ollama pull nomic-embed-text
  docker compose exec ollama ollama pull llama3.2
  ```

Changing the embedding model changes the vector size. Start with an empty database
(`docker compose down -v` removes the `pgdata` volume) when you do.

## Knowledge

- Put Markdown, text, HTML or PDF files in `knowledge/<persona id>/`; they are picked up within
  a minute. Files in `knowledge/_shared/` are visible to every persona.
- Or let a static site publish itself: set `KNOWLEDGE_SITE_SOURCES=default=https://your.site/knowledge.json`.
  [Meteorite](https://github.com/pandaind/meteorite) writes that file with `chat.knowledge.publish: true`.
- `PERSONA_API_KEYS` is `personaId:key` (colon), comma separated. The same key must be the
  persona's `mcp-api-key=` in `data/personas/<id>/persona.properties`, or `MCP_API_KEY` for a
  single persona.

## Things to know

- `./data` is mounted over the backend's `/app/data`, so the context file the image ships with is
  hidden. Put your own at `data/personas/<id>/context.txt` (or `data/context.txt`).
- The backend keeps trying to reach the knowledge service: if it is down at startup, or restarts
  later, chat keeps working without knowledge-base tools and they come back within about 15
  seconds of the service being reachable. The compose file also starts the backend after the
  knowledge service is healthy.
- Back up `./data` (sessions database, personas) and the `pgdata` volume.
- Update with `git pull && docker compose up -d --build`.
