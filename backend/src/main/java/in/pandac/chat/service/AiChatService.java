package in.pandac.chat.service;

import in.pandac.chat.config.McpRagConfig.PersonaMcpToolProviders;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.InMemoryChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.deepseek.DeepSeekChatModel;
import org.springframework.ai.mistralai.MistralAiChatModel;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Chats via whichever AI provider the requested persona is configured for.
 * Each provider's ChatModel is wired independently (see the *Config classes
 * in in.pandac.chat.config) so several can be active at once — a persona
 * picks one by name ("ollama", "openai", "anthropic", "mistral", "deepseek")
 * via its provider property, letting one backend mix, say, a free local
 * Ollama model for one persona and Claude for another.
 *
 * <p>Personas with {@code mcpEnabled} also get tool access to the optional
 * RAG MCP server (see {@link in.pandac.chat.config.McpRagConfig}) — a
 * separate project PopTalk connects to, not something built into this repo.
 */
@Service
public class AiChatService {

    private static final Logger log = LoggerFactory.getLogger(AiChatService.class);

    private static final String CHAT_MEMORY_CONVERSATION_ID_KEY = "chat_memory_conversation_id";
    private static final String CHAT_MEMORY_RETRIEVE_SIZE_KEY = "chat_memory_retrieve_size";

    private final Map<String, ChatClient> chatClientsByProvider = new LinkedHashMap<>();
    private final ChatMemory chatMemory;
    private final PersonaService personaService;
    private final PersonaMcpToolProviders mcpToolProviders;

    @Value("${app.ai.max-history-turns:8}")
    private int maxHistoryTurns;

    public AiChatService(PersonaService personaService,
                         ObjectProvider<OllamaChatModel> ollama,
                         ObjectProvider<OpenAiChatModel> openai,
                         ObjectProvider<AnthropicChatModel> anthropic,
                         ObjectProvider<MistralAiChatModel> mistral,
                         ObjectProvider<DeepSeekChatModel> deepseek,
                         ObjectProvider<PersonaMcpToolProviders> mcpToolProviders) {
        this.personaService = personaService;
        this.mcpToolProviders = mcpToolProviders.getIfAvailable();
        this.chatMemory = MessageWindowChatMemory.builder()
                .chatMemoryRepository(new InMemoryChatMemoryRepository())
                .maxMessages(1000)
                .build();

        // No defaultSystem() here — the system prompt is persona-specific and
        // supplied per call below, since one backend may serve several personas.
        MessageChatMemoryAdvisor memoryAdvisor = MessageChatMemoryAdvisor.builder(chatMemory).build();
        registerProvider("ollama", ollama.getIfAvailable(), memoryAdvisor);
        registerProvider("openai", openai.getIfAvailable(), memoryAdvisor);
        registerProvider("anthropic", anthropic.getIfAvailable(), memoryAdvisor);
        registerProvider("mistral", mistral.getIfAvailable(), memoryAdvisor);
        registerProvider("deepseek", deepseek.getIfAvailable(), memoryAdvisor);

        log.info("AI providers available: {}", chatClientsByProvider.keySet());
        log.info("RAG MCP server: {}", this.mcpToolProviders != null ? "configured" : "not configured");
    }

    private void registerProvider(String key, ChatModel model, MessageChatMemoryAdvisor memoryAdvisor) {
        if (model == null) {
            return;
        }
        chatClientsByProvider.put(key, ChatClient.builder(model).defaultAdvisors(memoryAdvisor).build());
    }

    public String chat(String sessionId, String personaId, String userName, String userMessage) {
        Persona persona = personaService.getPersona(personaId);
        ChatClient chatClient = chatClientsByProvider.get(persona.provider());
        if (chatClient == null) {
            log.error("No AI provider wired for '{}' (persona '{}'); known providers: {}",
                    persona.provider(), persona.id(), chatClientsByProvider.keySet());
            return fallbackResponse(userName, persona);
        }

        try {
            String promptText = String.format("[Visitor: %s] %s", userName, userMessage);

            ChatClient.ChatClientRequestSpec request = chatClient.prompt()
                    .system(persona.systemPrompt())
                    .user(promptText)
                    .advisors(advisor -> advisor
                            .param(CHAT_MEMORY_CONVERSATION_ID_KEY, sessionId)
                            .param(CHAT_MEMORY_RETRIEVE_SIZE_KEY, maxHistoryTurns));

            if (persona.model() != null || persona.temperature() != null) {
                ChatOptions.Builder options = ChatOptions.builder();
                if (persona.model() != null) {
                    options.model(persona.model());
                }
                if (persona.temperature() != null) {
                    options.temperature(persona.temperature());
                }
                // ChatClient's .options() takes a ChatOptions.Builder, not a built
                // instance, as of Spring AI 2.0 — pass the builder itself.
                request = request.options(options);
            }

            if (persona.mcpEnabled() && mcpToolProviders != null) {
                ToolCallbackProvider toolProvider = mcpToolProviders.get(persona.id());
                if (toolProvider != null) {
                    request = request.toolCallbacks(toolProvider);
                }
            }

            String response = request.call().content();

            log.debug("AI response for session {}: {}", sessionId, response);
            return response != null ? response.trim() : fallbackResponse(userName, persona);

        } catch (Exception e) {
            log.error("AI chat error for session {} (provider '{}'): {}", sessionId, persona.provider(), e.getMessage());
            return fallbackResponse(userName, persona);
        }
    }

    public void clearMemory(String sessionId) {
        chatMemory.clear(sessionId);
        log.debug("Cleared AI memory for session {}", sessionId);
    }

    private String fallbackResponse(String userName, Persona persona) {
        String retryHint = persona.websiteUrl() != null
                ? "Please check " + persona.websiteUrl() + " or try again in a moment."
                : "Please try again in a moment.";
        return String.format("Hi %s! I'm having a little trouble connecting right now. %s", userName, retryHint);
    }
}
