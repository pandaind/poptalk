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
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

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
 *
 * <p>The connection is allowed to come up late and to drop: if the RAG server is not reachable
 * at startup, or restarts later, the persona simply has no tools until a background task
 * reconnects (every {@value #RETRY_SECONDS}s). Chat keeps working without tools in the meantime.
 */
@Configuration
@ConditionalOnExpression("'${app.mcp.rag-url:}'.length() > 0")
public class McpRagConfig {

    private static final Logger log = LoggerFactory.getLogger(McpRagConfig.class);

    /** How often a persona whose RAG connection is down tries to connect again. */
    static final int RETRY_SECONDS = 15;

    @Value("${app.mcp.rag-url}")
    private String ragUrl;

    @Value("${app.mcp.rag-endpoint:/mcp}")
    private String ragEndpoint;

    private final PersonaService personaService;
    private final Map<String, ReconnectingToolProvider> providersByPersona = new LinkedHashMap<>();
    private ScheduledExecutorService retryScheduler;

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

        /**
         * True when the persona has a live connection to the knowledge server right now. Cheap
         * (no network call), so a request can tell the model about its tools only when it has them.
         */
        public boolean hasTools(String personaId) {
            return byPersona.get(personaId) instanceof ReconnectingToolProvider provider
                    && provider.isConnected();
        }
    }

    /** A live MCP connection: its tools, and how to close it. */
    record Connection(ToolCallbackProvider tools, AutoCloseable closer) {}

    /**
     * Tool provider for one persona that connects lazily and heals itself. While there is no
     * connection it offers no tools (so chat still works); a background task calls
     * {@link #tryConnect()} until it succeeds, and a failure while listing tools drops the
     * connection so the same task replaces it.
     */
    static final class ReconnectingToolProvider implements ToolCallbackProvider {
        private final String personaId;
        private final Callable<Connection> connector;
        private volatile Connection connection;

        ReconnectingToolProvider(String personaId, Callable<Connection> connector) {
            this.personaId = personaId;
            this.connector = connector;
        }

        boolean isConnected() {
            return connection != null;
        }

        /** Returns true when connected (already, or just now). */
        synchronized boolean tryConnect() {
            if (connection != null) {
                return true;
            }
            try {
                connection = connector.call();
                log.info("Connected to the RAG MCP server for persona '{}'", personaId);
                return true;
            } catch (Exception e) {
                log.debug("RAG MCP server not available for persona '{}': {}", personaId, e.getMessage());
                return false;
            }
        }

        @Override
        public ToolCallback[] getToolCallbacks() {
            Connection current = connection;
            if (current == null) {
                return new ToolCallback[0];
            }
            try {
                return current.tools().getToolCallbacks();
            } catch (Exception e) {
                log.warn("Lost the RAG MCP connection for persona '{}': {} — reconnecting in the background",
                        personaId, e.getMessage());
                drop(current);
                return new ToolCallback[0];
            }
        }

        private synchronized void drop(Connection lost) {
            if (connection == lost) {
                connection = null;
                closeQuietly(lost);
            }
        }

        void close() {
            Connection current = connection;
            connection = null;
            if (current != null) {
                closeQuietly(current);
            }
        }

        private static void closeQuietly(Connection c) {
            try {
                c.closer().close();
            } catch (Exception ignored) {
                // closing a connection that is already broken
            }
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

            ReconnectingToolProvider provider =
                    new ReconnectingToolProvider(persona.id(), () -> connect(persona));
            if (!provider.tryConnect()) {
                log.warn("Could not connect to the RAG MCP server at {}{} for persona '{}' — it will have no "
                        + "knowledge-base tools until it is reachable; retrying every {}s.",
                        ragUrl, ragEndpoint, persona.id(), RETRY_SECONDS);
            }
            providersByPersona.put(persona.id(), provider);
            providers.put(persona.id(), provider);
        }

        log.info("RAG MCP configured for persona(s): {}", providersByPersona.keySet());
        if (!providersByPersona.isEmpty()) {
            startRetryTask();
        }
        return new PersonaMcpToolProviders(providers);
    }

    private void startRetryTask() {
        retryScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "rag-mcp-reconnect");
            t.setDaemon(true);
            return t;
        });
        retryScheduler.scheduleWithFixedDelay(() -> {
            try {
                providersByPersona.values().stream()
                        .filter(p -> !p.isConnected())
                        .forEach(ReconnectingToolProvider::tryConnect);
            } catch (RuntimeException e) {
                log.warn("RAG MCP reconnect pass failed: {}", e.getMessage());
            }
        }, RETRY_SECONDS, RETRY_SECONDS, TimeUnit.SECONDS);
    }

    /** Builds and initializes a client for the persona; throws if the server is not reachable. */
    private Connection connect(Persona persona) {
        // .customizeRequest(Consumer<HttpRequest.Builder>) was renamed/reshaped to
        // .httpRequestCustomizer(...) in MCP SDK 2.0.0 (bumped alongside Spring AI
        // 2.0's own major version) — the customizer callback now also receives the
        // method/URI/body/transport-context, none of which we need here.
        HttpClientStreamableHttpTransport transport = HttpClientStreamableHttpTransport
                .builder(ragUrl)
                .endpoint(ragEndpoint)
                .httpRequestCustomizer((builder, method, uri, body, context) ->
                        builder.header("Authorization", "Bearer " + persona.mcpApiKey()))
                .build();

        McpSyncClient client = McpClient.sync(transport)
                .clientInfo(new McpSchema.Implementation("poptalk-" + persona.id(), "1.0.0"))
                .build();
        try {
            client.initialize();
        } catch (RuntimeException e) {
            client.close();
            throw e;
        }
        return new Connection(new SyncMcpToolCallbackProvider(client), client::close);
    }

    @PreDestroy
    public void closeClients() {
        if (retryScheduler != null) {
            retryScheduler.shutdownNow();
        }
        providersByPersona.values().forEach(ReconnectingToolProvider::close);
    }
}
