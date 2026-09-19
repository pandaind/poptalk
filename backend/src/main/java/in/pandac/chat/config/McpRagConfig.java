package in.pandac.chat.config;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpSchema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.mcp.SyncMcpToolCallbackProvider;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Optional plug-in point for an external RAG (or any other tool-providing)
 * server speaking MCP over Streamable HTTP — a separate project, deployed and
 * versioned independently of PopTalk. Only active when {@code MCP_RAG_URL} is
 * set; unconfigured, this class contributes nothing and personas behave
 * exactly as if MCP didn't exist.
 *
 * <p>Wired manually rather than through {@code spring-ai-starter-mcp-client}'s
 * own auto-configuration for the same reason as the AI provider configs: that
 * auto-configuration expects its connection's URL property to already be
 * present (even blank) and throws {@code IllegalArgumentException} at
 * startup otherwise — incompatible with "absent by default". Spring AI's own
 * MCP auto-configuration is disabled via {@code spring.ai.mcp.client.enabled:
 * false} in application.yml so it doesn't register a second, empty
 * {@link ToolCallbackProvider} bean alongside this one.
 *
 * <p>A persona opts in via {@code mcp=true} in its {@code persona.properties}
 * (or the {@code MCP_ENABLED} env var as the default for all personas) — see
 * {@link in.pandac.chat.service.PersonaService} and
 * {@link in.pandac.chat.service.AiChatService}.
 */
@Configuration
@ConditionalOnExpression("'${app.mcp.rag-url:}'.length() > 0")
public class McpRagConfig {

    private static final Logger log = LoggerFactory.getLogger(McpRagConfig.class);

    @Value("${app.mcp.rag-url}")
    private String ragUrl;

    @Value("${app.mcp.rag-endpoint:/mcp}")
    private String ragEndpoint;

    @Bean(destroyMethod = "close")
    public McpSyncClient ragMcpClient() {
        HttpClientStreamableHttpTransport transport = HttpClientStreamableHttpTransport
                .builder(ragUrl)
                .endpoint(ragEndpoint)
                .build();

        McpSyncClient client = McpClient.sync(transport)
                .clientInfo(new McpSchema.Implementation("poptalk", "1.0.0"))
                .build();

        // Connect at startup so failures show up in the logs immediately, but
        // never block boot on it — an unreachable RAG server just means its
        // tools are unavailable until the next restart, same as any other
        // provider misconfiguration in this app.
        try {
            client.initialize();
            log.info("Connected to RAG MCP server at {}{}", ragUrl, ragEndpoint);
        } catch (Exception e) {
            log.warn("Could not connect to RAG MCP server at {}{}: {} — its tools will be "
                    + "unavailable until this is fixed and the backend is restarted.",
                    ragUrl, ragEndpoint, e.getMessage());
        }
        return client;
    }

    @Bean
    public ToolCallbackProvider ragToolCallbackProvider(McpSyncClient ragMcpClient) {
        return new SyncMcpToolCallbackProvider(ragMcpClient);
    }
}
