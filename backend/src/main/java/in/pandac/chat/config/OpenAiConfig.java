package in.pandac.chat.config;

import com.openai.client.OpenAIClient;
import io.micrometer.observation.ObservationRegistry;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.setup.OpenAiSetup;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.List;

/**
 * Manually wires the OpenAI chat model (rather than relying on Spring AI's own
 * conditional auto-configuration) so it can coexist with every other provider's
 * ChatModel bean at once — personas each pick their own provider at runtime
 * (see AiChatService). Spring AI's built-in auto-selection is disabled via
 * spring.ai.model.chat=none in application.yml for exactly this reason.
 *
 * <p>Only active when an API key is actually configured — some providers'
 * client setup (e.g. Mistral's) throws on construction with a blank key, so
 * this is a hard requirement, not just tidiness.
 *
 * <p>Spring AI 2.0 removed {@code OpenAiApi} entirely — {@code OpenAiChatModel}
 * now wraps the official {@code com.openai:openai-java} SDK's {@link OpenAIClient}
 * directly. {@link OpenAiSetup#setupSyncClient} is the same factory Spring AI's
 * own auto-configuration uses to build that client from plain baseUrl/apiKey.
 */
@Configuration
@ConditionalOnExpression("'${spring.ai.openai.api-key:}'.length() > 0")
public class OpenAiConfig {

    @Value("${spring.ai.openai.api-key:}")
    private String apiKey;

    @Value("${spring.ai.openai.base-url:https://api.openai.com}")
    private String baseUrl;

    @Value("${spring.ai.openai.chat.model:gpt-4o-mini}")
    private String modelName;

    @Value("${spring.ai.openai.chat.temperature:0.3}")
    private Double temperature;

    @Bean
    public OpenAIClient openAiClient() {
        return OpenAiSetup.setupSyncClient(baseUrl, apiKey, null, null, null, null, false, false, modelName,
                Duration.ofSeconds(60), 3, null, null, ObservationRegistry.NOOP, null, List.of());
    }

    @Bean
    public OpenAiChatModel openAiChatModel(OpenAIClient openAiClient) {
        return OpenAiChatModel.builder()
                .openAiClient(openAiClient)
                .options(OpenAiChatOptions.builder()
                        .model(modelName)
                        .temperature(temperature)
                        .build())
                .build();
    }
}
