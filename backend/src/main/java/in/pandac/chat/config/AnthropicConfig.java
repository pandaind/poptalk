package in.pandac.chat.config;

import com.anthropic.client.AnthropicClient;
import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.anthropic.AnthropicSetup;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * See OpenAiConfig for why this is wired manually instead of relying on
 * Spring AI's own conditional auto-configuration, and why it's only active
 * when an API key is configured.
 *
 * <p>Spring AI 2.0 removed {@code AnthropicApi} entirely — {@code AnthropicChatModel}
 * now wraps the official {@code com.anthropic:anthropic-java} SDK's
 * {@link AnthropicClient} directly. {@link AnthropicSetup#setupSyncClient} is the
 * same factory Spring AI's own auto-configuration uses to build that client from
 * plain baseUrl/apiKey.
 */
@Configuration
@ConditionalOnExpression("'${spring.ai.anthropic.api-key:}'.length() > 0")
public class AnthropicConfig {

    @Value("${spring.ai.anthropic.api-key:}")
    private String apiKey;

    @Value("${spring.ai.anthropic.base-url:https://api.anthropic.com}")
    private String baseUrl;

    @Value("${spring.ai.anthropic.chat.model:claude-sonnet-5}")
    private String modelName;

    @Value("${spring.ai.anthropic.chat.temperature:0.3}")
    private Double temperature;

    @Value("${spring.ai.anthropic.chat.max-tokens:1024}")
    private Integer maxTokens;

    @Bean
    public AnthropicClient anthropicClient() {
        return AnthropicSetup.setupSyncClient(baseUrl, apiKey, null, null, null, null);
    }

    @Bean
    public AnthropicChatModel anthropicChatModel(AnthropicClient anthropicClient) {
        return AnthropicChatModel.builder()
                .anthropicClient(anthropicClient)
                .options(AnthropicChatOptions.builder()
                        .model(modelName)
                        .temperature(temperature)
                        .maxTokens(maxTokens)
                        .build())
                .build();
    }
}
