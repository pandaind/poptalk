package in.pandac.chat.config;

import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.anthropic.api.AnthropicApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * See OpenAiConfig for why this is wired manually instead of relying on
 * Spring AI's own conditional auto-configuration, and why it's only active
 * when an API key is configured.
 */
@Configuration
@ConditionalOnExpression("'${spring.ai.anthropic.api-key:}'.length() > 0")
public class AnthropicConfig {

    @Value("${spring.ai.anthropic.api-key:}")
    private String apiKey;

    @Value("${spring.ai.anthropic.base-url:https://api.anthropic.com}")
    private String baseUrl;

    @Value("${spring.ai.anthropic.chat.options.model:claude-sonnet-5}")
    private String modelName;

    @Value("${spring.ai.anthropic.chat.options.temperature:0.3}")
    private Double temperature;

    @Value("${spring.ai.anthropic.chat.options.max-tokens:1024}")
    private Integer maxTokens;

    @Bean
    public AnthropicApi anthropicApi() {
        return AnthropicApi.builder()
                .baseUrl(baseUrl)
                .apiKey(apiKey)
                .build();
    }

    @Bean
    public AnthropicChatModel anthropicChatModel(AnthropicApi anthropicApi) {
        return AnthropicChatModel.builder()
                .anthropicApi(anthropicApi)
                .defaultOptions(AnthropicChatOptions.builder()
                        .model(modelName)
                        .temperature(temperature)
                        .maxTokens(maxTokens)
                        .build())
                .build();
    }
}
