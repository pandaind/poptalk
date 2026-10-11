package in.pandac.chat.config;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.test.util.ReflectionTestUtils;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;

class OllamaConfigTest {

    private static final String REPLY = "{\"model\":\"m\",\"created_at\":\"2026-01-01T00:00:00Z\","
            + "\"message\":{\"role\":\"assistant\",\"content\":\"hi\"},\"done\":true,\"done_reason\":\"stop\"}";

    private HttpServer server;
    private final List<String> authHeaders = new CopyOnWriteArrayList<>();

    @BeforeEach
    void startServer() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/chat", exchange -> {
            authHeaders.add(String.valueOf(exchange.getRequestHeaders().getFirst("Authorization")));
            exchange.getRequestBody().readAllBytes();
            byte[] body = (REPLY + "\n").getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    private OllamaApi apiWithKey(String key) {
        OllamaConfig config = new OllamaConfig();
        ReflectionTestUtils.setField(config, "apiKey", key);
        ReflectionTestUtils.setField(config, "baseUrl", "http://127.0.0.1:" + server.getAddress().getPort());
        return config.ollamaApi();
    }

    private static OllamaApi.ChatRequest request(boolean stream) {
        return OllamaApi.ChatRequest.builder("m")
                .stream(stream)
                .messages(List.of(OllamaApi.Message.builder(OllamaApi.Message.Role.USER).content("hi").build()))
                .build();
    }

    @Test
    void theApiKeyIsSentOnBlockingCalls() {
        apiWithKey("  secret-key  ").chat(request(false));

        assertThat(authHeaders).containsExactly("Bearer secret-key");
    }

    @Test
    void theApiKeyIsSentOnStreamedCallsToo() {
        apiWithKey("secret-key").streamingChat(request(true)).blockLast();

        assertThat(authHeaders).containsExactly("Bearer secret-key");
    }

    @Test
    void noAuthorizationHeaderIsSentWithoutAKey() {
        OllamaApi api = apiWithKey("");
        api.chat(request(false));
        api.streamingChat(request(true)).blockLast();

        assertThat(authHeaders).containsExactly("null", "null");
    }
}
