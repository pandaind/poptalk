package in.pandac.chat.config;

import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Manually wires the OpenAI chat model (rather than relying on Spring AI's own
 * conditional auto-configuration) so it can coexist with every other provider's
 * ChatModel bean at once — personas each pick their own provider at runtime
 * (see AiChatService). Spring AI's built-in auto-selection is disabled via
 * spring.ai.model.chat=none in application.yml for exactly this reason.
 *
 * <p>Only active when an API key is actually configured — some providers'
 * Api builders (e.g. Mistral's) throw on construction with a blank key, so
 * this is a hard requirement, not just tidiness.
 */
@Configuration
@ConditionalOnExpression("'${spring.ai.openai.api-key:}'.length() > 0")
public class OpenAiConfig {

    @Value("${spring.ai.openai.api-key:}")
    private String apiKey;

    @Value("${spring.ai.openai.base-url:https://api.openai.com}")
    private String baseUrl;

    @Value("${spring.ai.openai.chat.options.model:gpt-4o-mini}")
    private String modelName;

    @Value("${spring.ai.openai.chat.options.temperature:0.3}")
    private Double temperature;

    @Bean
    public OpenAiApi openAiApi() {
        return OpenAiApi.builder()
                .baseUrl(baseUrl)
                .apiKey(apiKey)
                .build();
    }

    @Bean
    public OpenAiChatModel openAiChatModel(OpenAiApi openAiApi) {
        return OpenAiChatModel.builder()
                .openAiApi(openAiApi)
                .defaultOptions(OpenAiChatOptions.builder()
                        .model(modelName)
                        .temperature(temperature)
                        .build())
                .build();
    }
}
