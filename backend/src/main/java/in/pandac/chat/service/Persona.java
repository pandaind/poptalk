package in.pandac.chat.service;

/**
 * A resolved persona: branding shown in the widget plus the fully assembled
 * system prompt (shared rules + this persona's personal context) sent to the AI.
 */
public record Persona(
        String id,
        String ownerName,
        String chatTitle,
        String avatarInitial,
        String websiteUrl,
        /** Which AI provider this persona talks to: "ollama", "openai", "anthropic", "mistral", or "deepseek". */
        String provider,
        /** Optional per-persona model override; null means use that provider's configured default model. */
        String model,
        /** Optional per-persona temperature override; null means use that provider's configured default. */
        Double temperature,
        /** Whether this persona can call tools from the optional RAG MCP server (see McpRagConfig). */
        boolean mcpEnabled,
        /** This persona's own API key for the RAG MCP server — the server derives which
         *  tenant's data to search from this key, so it must be unique per persona. Only
         *  meaningful when mcpEnabled is true. */
        String mcpApiKey,
        String systemPrompt
) {}
