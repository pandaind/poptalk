package in.pandac.chat.config;

import org.springframework.ai.deepseek.DeepSeekChatModel;
import org.springframework.ai.deepseek.DeepSeekChatOptions;
import org.springframework.ai.deepseek.api.DeepSeekApi;
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
@ConditionalOnExpression("'${spring.ai.deepseek.api-key:}'.length() > 0")
public class DeepSeekConfig {

    @Value("${spring.ai.deepseek.api-key:}")
    private String apiKey;

    @Value("${spring.ai.deepseek.base-url:https://api.deepseek.com}")
    private String baseUrl;

    @Value("${spring.ai.deepseek.chat.model:deepseek-chat}")
    private String modelName;

    @Value("${spring.ai.deepseek.chat.temperature:0.3}")
    private Double temperature;

    @Bean
    public DeepSeekApi deepSeekApi() {
        return DeepSeekApi.builder()
                .baseUrl(baseUrl)
                .apiKey(apiKey)
                .build();
    }

    @Bean
    public DeepSeekChatModel deepSeekChatModel(DeepSeekApi deepSeekApi) {
        return DeepSeekChatModel.builder()
                .deepSeekApi(deepSeekApi)
                .options(DeepSeekChatOptions.builder()
                        .model(modelName)
                        .temperature(temperature)
                        .build())
                .build();
    }
}
