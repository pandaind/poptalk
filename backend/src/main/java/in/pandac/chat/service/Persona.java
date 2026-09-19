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
        String systemPrompt
) {}
