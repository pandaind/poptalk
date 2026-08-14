package in.pandac.chat.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.InMemoryChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Spring AI 2 chat service backed by a locally running Ollama model.
 */
@Service
public class AiChatService {

    private static final Logger log = LoggerFactory.getLogger(AiChatService.class);

    private static final String CHAT_MEMORY_CONVERSATION_ID_KEY = "chat_memory_conversation_id";
    private static final String CHAT_MEMORY_RETRIEVE_SIZE_KEY = "chat_memory_retrieve_size";

    private final ChatClient chatClient;
    private final ChatMemory chatMemory;

    @Value("${app.ai.max-history-turns:8}")
    private int maxHistoryTurns;

    public AiChatService(ChatClient.Builder chatClientBuilder,
                         PersonalContextService contextService) {
        this.chatMemory = MessageWindowChatMemory.builder()
                .chatMemoryRepository(new InMemoryChatMemoryRepository())
                .maxMessages(1000)
                .build();
                
        this.chatClient = chatClientBuilder
                .defaultSystem(contextService.getSystemPrompt())
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
                .build();
    }

    public String chat(String sessionId, String userName, String userMessage) {
        try {
            String promptText = String.format("[Visitor: %s] %s", userName, userMessage);

            String response = chatClient.prompt()
                    .user(promptText)
                    .advisors(advisor -> advisor
                            .param(CHAT_MEMORY_CONVERSATION_ID_KEY, sessionId)
                            .param(CHAT_MEMORY_RETRIEVE_SIZE_KEY, maxHistoryTurns))
                    .call()
                    .content();

            log.debug("AI response for session {}: {}", sessionId, response);
            return response != null ? response.trim() : fallbackResponse(userName);

        } catch (Exception e) {
            log.error("AI chat error for session {}: {}", sessionId, e.getMessage());
            return fallbackResponse(userName);
        }
    }

    public void clearMemory(String sessionId) {
        chatMemory.clear(sessionId);
        log.debug("Cleared AI memory for session {}", sessionId);
    }

    private String fallbackResponse(String userName) {
        return String.format(
            "Hi %s! I'm having a little trouble connecting right now. " +
            "Please check pandac.in or try again in a moment.", userName);
    }
}

