package in.pandac.chat.config;

import in.pandac.chat.service.Persona;
import in.pandac.chat.service.PersonaService;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpSchema;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.mcp.SyncMcpToolCallbackProvider;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Optional plug-in point for an external RAG (or any other tool-providing)
 * server speaking MCP over Streamable HTTP — a separate project (poptalk-rag),
 * deployed and versioned independently of PopTalk. Only active when
 * {@code MCP_RAG_URL} is set; unconfigured, this class contributes nothing
 * and personas behave exactly as if MCP didn't exist.
 *
 * <p>One {@link McpSyncClient} per persona that has {@code mcp=true} — not one
 * shared client — because each persona authenticates with its own API key,
 * and that key is how the RAG server derives which tenant's data to search
 * (see poptalk-rag's PersonaApiKeyAuthFilter). A shared client would mean a
 * shared key, which would mean no real tenant isolation. This mirrors the
 * existing {@code Map<String, ChatClient>} pattern in AiChatService.
 *
 * <p>Wired manually rather than through {@code spring-ai-starter-mcp-client}'s
 * own auto-configuration for the same reason as the AI provider configs: that
 * auto-configuration expects its connection's URL property to already be
 * present (even blank) and throws {@code IllegalArgumentException} at
 * startup otherwise — incompatible with "absent by default". Spring AI's own
 * MCP auto-configuration is disabled via {@code spring.ai.mcp.client.enabled:
 * false} in application.yml so it doesn't register extra, empty
 * {@link ToolCallbackProvider} beans alongside these.
 */
@Configuration
@ConditionalOnExpression("'${app.mcp.rag-url:}'.length() > 0")
public class McpRagConfig {

    private static final Logger log = LoggerFactory.getLogger(McpRagConfig.class);

    @Value("${app.mcp.rag-url}")
    private String ragUrl;

    @Value("${app.mcp.rag-endpoint:/mcp}")
    private String ragEndpoint;

    private final PersonaService personaService;
    private final Map<String, McpSyncClient> clientsByPersona = new LinkedHashMap<>();

    public McpRagConfig(PersonaService personaService) {
        this.personaService = personaService;
    }

    /**
     * Wraps the per-persona provider map in its own type rather than exposing
     * a raw {@code Map<String, ToolCallbackProvider>} bean: Spring treats a
     * {@code Map<String, X>} constructor parameter as "collect every bean of
     * type X, keyed by bean name" by default, which would shadow or conflict
     * with a single bean whose own declared type is that same map shape. A
     * dedicated wrapper type sidesteps that ambiguity entirely.
     */
    public static final class PersonaMcpToolProviders {
        private final Map<String, ToolCallbackProvider> byPersona;

        PersonaMcpToolProviders(Map<String, ToolCallbackProvider> byPersona) {
            this.byPersona = byPersona;
        }

        public ToolCallbackProvider get(String personaId) {
            return byPersona.get(personaId);
        }
    }

    @Bean
    public PersonaMcpToolProviders mcpToolCallbackProvidersByPersona() {
        Map<String, ToolCallbackProvider> providers = new LinkedHashMap<>();

        for (Persona persona : personaService.getAllPersonas()) {
            if (!persona.mcpEnabled()) {
                continue;
            }
            if (persona.mcpApiKey() == null) {
                log.warn("Persona '{}' has mcp=true but no mcp-api-key configured — skipping RAG for it", persona.id());
                continue;
            }

            McpSyncClient client = buildClient(persona);
            clientsByPersona.put(persona.id(), client);
            providers.put(persona.id(), new SyncMcpToolCallbackProvider(client));
        }

        log.info("RAG MCP clients configured for persona(s): {}", clientsByPersona.keySet());
        return new PersonaMcpToolProviders(providers);
    }

    private McpSyncClient buildClient(Persona persona) {
        HttpClientStreamableHttpTransport transport = HttpClientStreamableHttpTransport
                .builder(ragUrl)
                .endpoint(ragEndpoint)
                .customizeRequest(builder -> builder.header("Authorization", "Bearer " + persona.mcpApiKey()))
                .build();

        McpSyncClient client = McpClient.sync(transport)
                .clientInfo(new McpSchema.Implementation("poptalk-" + persona.id(), "1.0.0"))
                .build();

        // Connect at startup so failures show up in the logs immediately, but
        // never block boot on it — an unreachable RAG server just means this
        // persona's tools are unavailable until the next restart, same as any
        // other provider misconfiguration in this app.
        try {
            client.initialize();
            log.info("Connected to RAG MCP server at {}{} for persona '{}'", ragUrl, ragEndpoint, persona.id());
        } catch (Exception e) {
            log.warn("Could not connect to RAG MCP server at {}{} for persona '{}': {} — its tools will be "
                    + "unavailable until this is fixed and the backend is restarted.",
                    ragUrl, ragEndpoint, persona.id(), e.getMessage());
        }
        return client;
    }

    @PreDestroy
    public void closeClients() {
        clientsByPersona.values().forEach(McpSyncClient::close);
    }
}
