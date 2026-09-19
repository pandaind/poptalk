package in.pandac.chat.config;

import org.springframework.ai.mistralai.MistralAiChatModel;
import org.springframework.ai.mistralai.MistralAiChatOptions;
import org.springframework.ai.mistralai.api.MistralAiApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * See OpenAiConfig for why this is wired manually instead of relying on
 * Spring AI's own conditional auto-configuration. The non-blank-key guard
 * here is a hard requirement, not just tidiness: MistralAiApi's builder
 * throws {@code IllegalArgumentException} on construction with a blank key.
 */
@Configuration
@ConditionalOnExpression("'${spring.ai.mistralai.api-key:}'.length() > 0")
public class MistralAiConfig {

    @Value("${spring.ai.mistralai.api-key:}")
    private String apiKey;

    @Value("${spring.ai.mistralai.base-url:https://api.mistral.ai}")
    private String baseUrl;

    @Value("${spring.ai.mistralai.chat.model:mistral-small-latest}")
    private String modelName;

    @Value("${spring.ai.mistralai.chat.temperature:0.3}")
    private Double temperature;

    @Bean
    public MistralAiApi mistralAiApi() {
        return MistralAiApi.builder()
                .baseUrl(baseUrl)
                .apiKey(apiKey)
                .build();
    }

    @Bean
    public MistralAiChatModel mistralAiChatModel(MistralAiApi mistralAiApi) {
        return MistralAiChatModel.builder()
                .mistralAiApi(mistralAiApi)
                .options(MistralAiChatOptions.builder()
                        .model(modelName)
                        .temperature(temperature)
                        .build())
                .build();
    }
}
