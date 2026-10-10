package in.pandac.chat.dto;

/**
 * Public branding config returned by GET /api/config.
 * Safe to expose — no secrets.
 */
public record ConfigResponse(
        String ownerName,
        String chatTitle,
        String avatarInitial,
        /** "AI" or "MANUAL" — lets the widget pick the streaming vs. polling send path. */
        String chatMode,
        /** Optional custom greeting for the chat window; null lets the widget use its default. */
        String welcomeMessage
) {}
