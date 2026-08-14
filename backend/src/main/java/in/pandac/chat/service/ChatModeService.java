package in.pandac.chat.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Reads the CHAT_MODE env var once at startup.
 * To switch modes: change CHAT_MODE in .env and restart the service.
 *
 *   CHAT_MODE=AI      → Ollama responds immediately (synchronous)
 *   CHAT_MODE=MANUAL  → message forwarded to Telegram, admin replies with [SID:xxx]
 */
@Service
public class ChatModeService {

    @Value("${app.ai.mode:AI}")
    private String mode;

    /**
     * Returns true when the AI (Ollama) should respond to messages.
     */
    public boolean isAiMode() {
        return "AI".equalsIgnoreCase(mode);
    }

    public String getMode() {
        return mode.toUpperCase();
    }
}
