package in.pandac.chat.dto;

/**
 * Public branding config returned by GET /api/config.
 * Safe to expose — no secrets.
 */
public record ConfigResponse(
        String ownerName,
        String chatTitle,
        String avatarInitial
) {}
