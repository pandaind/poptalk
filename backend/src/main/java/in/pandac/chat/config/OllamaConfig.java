package in.pandac.chat.config;

import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.web.client.RestClient;

@Configuration
public class OllamaConfig {

    @Value("${app.ai.api-key:}")
    private String apiKey;

    @Value("${spring.ai.ollama.base-url:http://localhost:11434}")
    private String baseUrl;

    @Value("${spring.ai.ollama.chat.model:llama3.2}")
    private String modelName;

    @Value("${spring.ai.ollama.chat.temperature:0.3}")
    private Double temperature;

    @Bean
    public OllamaApi ollamaApi() {
        var restClientBuilder = RestClient.builder();
        if (apiKey != null && !apiKey.trim().isEmpty()) {
            restClientBuilder.defaultHeader("Authorization", "Bearer " + apiKey.trim());
        }
        
        return OllamaApi.builder()
            .baseUrl(baseUrl)
            .restClientBuilder(restClientBuilder)
            .build();
    }

    @Bean
    @Primary
    public OllamaChatModel ollamaChatModel(OllamaApi ollamaApi) {
        return OllamaChatModel.builder()
            .ollamaApi(ollamaApi)
            .options(OllamaChatOptions.builder()
                .model(modelName)
                .temperature(temperature)
                .build())
            .build();
    }
}
