package in.pandac.chat.service;

import in.pandac.chat.config.McpRagConfig.PersonaMcpToolProviders;
import org.junit.jupiter.api.Test;
import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.deepseek.DeepSeekChatModel;
import org.springframework.ai.mistralai.MistralAiChatModel;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.beans.factory.ObjectProvider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Uses real {@code ChatClient} plumbing over a mocked {@code ChatModel} rather
 * than mocking the fluent request-spec chain directly — the same construction
 * AiChatService itself does in production (ChatClient.builder(model).build()),
 * so these tests exercise the actual per-persona branching (provider lookup,
 * options/tool attachment, fallback on failure) against real Spring AI code.
 */
class AiChatServiceTest {

    @Test
    void returnsTrimmedContentFromTheConfiguredProvider() {
        OllamaChatModel ollama = ollamaReturning("  Hello there!  ");
        PersonaService personaService = personaServiceFor(persona("default", "ollama", null, null, false));
        AiChatService service = new AiChatService(personaService,
                providerOf(ollama), providerOf(null), providerOf(null), providerOf(null), providerOf(null),
                providerOf(null));

        String response = service.chat("session-1", "default", "Visitor", "Hi!");

        assertThat(response).isEqualTo("Hello there!");
    }

    @Test
    void fallsBackWhenThePersonasProviderIsNotWiredUp() {
        PersonaService personaService = personaServiceFor(persona("default", "openai", null, null, false));
        // No providers configured at all.
        AiChatService service = new AiChatService(personaService,
                providerOf(null), providerOf(null), providerOf(null), providerOf(null), providerOf(null),
                providerOf(null));

        String response = service.chat("session-1", "default", "Visitor", "Hi!");

        assertThat(response).contains("having a little trouble");
    }

    @Test
    void fallsBackWithoutPropagatingWhenTheModelCallThrows() {
        OllamaChatModel ollama = ollamaReturning("unused");
        when(ollama.call(any(Prompt.class))).thenThrow(new RuntimeException("connection refused"));
        PersonaService personaService = personaServiceFor(persona("default", "ollama", null, null, false));
        AiChatService service = new AiChatService(personaService,
                providerOf(ollama), providerOf(null), providerOf(null), providerOf(null), providerOf(null),
                providerOf(null));

        String response = service.chat("session-1", "default", "Visitor", "Hi!");

        assertThat(response).contains("having a little trouble");
    }

    @Test
    void aPersonaLevelModelAndTemperatureOverrideDoesNotBreakTheRequest() {
        OllamaChatModel ollama = ollamaReturning("ok");
        PersonaService personaService = personaServiceFor(persona("default", "ollama", "llama3.3", 0.9, false));
        AiChatService service = new AiChatService(personaService,
                providerOf(ollama), providerOf(null), providerOf(null), providerOf(null), providerOf(null),
                providerOf(null));

        assertThatCode(() -> service.chat("session-1", "default", "Visitor", "Hi!")).doesNotThrowAnyException();
    }

    @Test
    void anMcpEnabledPersonaAttachesToolCallbacksWithoutBreakingTheRequest() {
        OllamaChatModel ollama = ollamaReturning("ok");
        PersonaService personaService = personaServiceFor(persona("default", "ollama", null, null, true));
        ToolCallbackProvider toolProvider = mock(ToolCallbackProvider.class);
        when(toolProvider.getToolCallbacks()).thenReturn(new org.springframework.ai.tool.ToolCallback[0]);
        PersonaMcpToolProviders mcpToolProviders = mock(PersonaMcpToolProviders.class);
        when(mcpToolProviders.get("default")).thenReturn(toolProvider);
        AiChatService service = new AiChatService(personaService,
                providerOf(ollama), providerOf(null), providerOf(null), providerOf(null), providerOf(null),
                providerOf(mcpToolProviders));

        assertThatCode(() -> service.chat("session-1", "default", "Visitor", "Hi!")).doesNotThrowAnyException();
    }

    @Test
    void clearMemoryNeverThrowsForAnUnknownSession() {
        PersonaService personaService = personaServiceFor(persona("default", "ollama", null, null, false));
        AiChatService service = new AiChatService(personaService,
                providerOf(null), providerOf(null), providerOf(null), providerOf(null), providerOf(null),
                providerOf(null));

        assertThatCode(() -> service.clearMemory("never-existed")).doesNotThrowAnyException();
    }

    private static Persona persona(String id, String provider, String model, Double temperature, boolean mcpEnabled) {
        return new Persona(id, "Owner", "Chat", "O", null, provider, model, temperature, mcpEnabled,
                mcpEnabled ? "mcp-key" : null, "You are the assistant.");
    }

    private static PersonaService personaServiceFor(Persona persona) {
        PersonaService service = mock(PersonaService.class);
        when(service.getPersona(persona.id())).thenReturn(persona);
        return service;
    }

    /**
     * A bare Mockito mock returns null from getOptions(), and ChatClient's
     * request-building calls options.mutate() on whatever that returns — a
     * real (not mocked) OllamaChatOptions keeps that call working without
     * having to stub Spring AI's internal options-merging machinery too.
     */
    private static OllamaChatModel ollamaReturning(String responseText) {
        OllamaChatModel model = mock(OllamaChatModel.class);
        when(model.getOptions()).thenReturn(OllamaChatOptions.builder().build());
        when(model.call(any(Prompt.class))).thenReturn(cannedResponse(responseText));
        return model;
    }

    private static ChatResponse cannedResponse(String text) {
        return new ChatResponse(java.util.List.of(new Generation(new AssistantMessage(text))));
    }

    @SuppressWarnings("unchecked")
    private static <T> ObjectProvider<T> providerOf(T value) {
        ObjectProvider<T> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(value);
        return provider;
    }
}
