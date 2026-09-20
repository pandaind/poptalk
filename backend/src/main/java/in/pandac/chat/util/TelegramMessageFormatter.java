package in.pandac.chat.util;

/**
 * Formats the admin-facing Telegram notification for an AI-mode exchange —
 * shared between the blocking chat route and the streaming chat controller,
 * since both need the exact same notification once a reply is complete.
 */
public final class TelegramMessageFormatter {

    private TelegramMessageFormatter() {
    }

    public static String aiModeNotification(boolean multiPersona, String personaOwnerName,
                                             String userName, String userMessage, String aiResponse,
                                             String sessionId) {
        String personaTag = multiPersona ? " (" + personaOwnerName + ")" : "";
        return String.format(
                "🤖 *AI Mode*%s | *%s*\n\n👤 User: %s\n🤖 AI:   %s\n\n`[SID:%s]`",
                personaTag, userName, userMessage, aiResponse, sessionId);
    }
}
