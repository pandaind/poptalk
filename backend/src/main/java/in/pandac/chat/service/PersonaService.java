package in.pandac.chat.service;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * Loads one or more personas and assembles each one's system prompt from the
 * shared rules (application.yml) plus that persona's own context file. A
 * persona can be a person speaking in first person, or a business/brand
 * speaking as itself — PopTalk doesn't assume either; the context file's own
 * content and the shared rules steer which voice the model takes.
 *
 * <p>Single-persona mode (the default): reads {@code contextFile} and uses
 * the global {@code app.branding.*} values — identical to the original
 * single-file behaviour.
 *
 * <p>Multi-persona mode: activated automatically when {@code personasDir}
 * exists and contains at least one subdirectory with a {@code context.txt}
 * inside. Each subdirectory name becomes the persona id (used as the
 * widget's {@code data-persona} value). An optional {@code persona.properties}
 * file inside it (keys: {@code ownerName}, {@code chatTitle},
 * {@code avatarInitial}, {@code websiteUrl}, {@code provider}, {@code model},
 * {@code temperature}, {@code mcp}) overrides the global branding and AI
 * provider defaults for that persona — each persona can talk to a different
 * AI provider (see AiChatService) and independently opt into the optional
 * RAG MCP server (see McpRagConfig).
 *
 * <p>Personas are loaded once at startup — editing a context file or adding a
 * new persona requires a restart, same as the original single-persona setup.
 */
@Service
@ConfigurationProperties(prefix = "app.ai")
public class PersonaService {

    private static final Logger log = LoggerFactory.getLogger(PersonaService.class);

    private String contextFile = "data/context.txt";
    private String personasDir = "data/personas";
    private String defaultPersona = "default";
    private List<String> rules;

    @Value("${app.branding.owner-name}")
    private String defaultOwnerName;

    @Value("${app.branding.chat-title}")
    private String defaultChatTitle;

    @Value("${app.branding.avatar-initial}")
    private String defaultAvatarInitial;

    @Value("${app.branding.website-url:}")
    private String defaultWebsiteUrl;

    @Value("${app.ai.default-provider:ollama}")
    private String defaultProvider;

    @Value("${app.ai.default-mcp-enabled:false}")
    private boolean defaultMcpEnabled;

    private final Map<String, Persona> personas = new LinkedHashMap<>();
    private String defaultPersonaId;

    // Getters and Setters for ConfigurationProperties
    public void setContextFile(String contextFile) {
        this.contextFile = contextFile;
    }

    public void setPersonasDir(String personasDir) {
        this.personasDir = personasDir;
    }

    public void setDefaultPersona(String defaultPersona) {
        this.defaultPersona = defaultPersona;
    }

    public void setRules(List<String> rules) {
        this.rules = rules;
    }

    @PostConstruct
    public void init() {
        Path dir = Path.of(personasDir);
        if (Files.isDirectory(dir)) {
            loadPersonasFrom(dir);
        }

        if (personas.isEmpty()) {
            // Single-persona fallback — identical to the original behaviour.
            String context = loadContextFile(Path.of(contextFile));
            personas.put(defaultPersona, new Persona(
                    defaultPersona, defaultOwnerName, defaultChatTitle, defaultAvatarInitial,
                    blankToNull(defaultWebsiteUrl), defaultProvider, null, null, defaultMcpEnabled,
                    buildSystemPrompt(context)));
        }

        defaultPersonaId = personas.containsKey(defaultPersona)
                ? defaultPersona
                : personas.keySet().iterator().next();

        log.info("Loaded {} persona(s): {} (default: '{}')",
                personas.size(), personas.keySet(), defaultPersonaId);
    }

    /** Returns the persona for the given id, falling back to the default persona if unknown or blank. */
    public Persona getPersona(String id) {
        if (id == null || id.isBlank()) {
            return getDefaultPersona();
        }
        Persona persona = personas.get(id);
        if (persona == null) {
            log.debug("Unknown persona '{}' requested — falling back to default '{}'", id, defaultPersonaId);
            return getDefaultPersona();
        }
        return persona;
    }

    public Persona getDefaultPersona() {
        return personas.get(defaultPersonaId);
    }

    /** True when more than one persona is configured — used to decide whether Telegram/log output should be tagged. */
    public boolean isMultiPersona() {
        return personas.size() > 1;
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private void loadPersonasFrom(Path dir) {
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, Files::isDirectory)) {
            for (Path personaDir : stream) {
                String id = personaDir.getFileName().toString();
                Path contextPath = personaDir.resolve("context.txt");
                if (!Files.exists(contextPath)) {
                    log.warn("Skipping persona '{}': no context.txt found in {}", id, personaDir);
                    continue;
                }

                Properties props = loadPersonaProperties(personaDir.resolve("persona.properties"));
                String ownerName = props.getProperty("ownerName", defaultOwnerName);
                String chatTitle = props.getProperty("chatTitle", defaultChatTitle);
                String avatarInitial = props.getProperty("avatarInitial",
                        ownerName != null && !ownerName.isBlank()
                                ? ownerName.substring(0, 1).toUpperCase()
                                : defaultAvatarInitial);
                String websiteUrl = blankToNull(props.getProperty("websiteUrl", defaultWebsiteUrl));
                String provider = props.getProperty("provider", defaultProvider);
                String model = blankToNull(props.getProperty("model"));
                Double temperature = parseDoubleOrNull(props.getProperty("temperature"), id);
                boolean mcpEnabled = Boolean.parseBoolean(
                        props.getProperty("mcp", Boolean.toString(defaultMcpEnabled)));

                String context = loadContextFile(contextPath);
                personas.put(id, new Persona(id, ownerName, chatTitle, avatarInitial, websiteUrl,
                        provider, model, temperature, mcpEnabled, buildSystemPrompt(context)));
            }
        } catch (IOException e) {
            log.error("Failed to scan personas directory '{}': {}", dir, e.getMessage());
        }
    }

    private static String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value;
    }

    private Double parseDoubleOrNull(String value, String personaId) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Double.parseDouble(value.trim());
        } catch (NumberFormatException e) {
            log.warn("Ignoring invalid temperature '{}' for persona '{}'", value, personaId);
            return null;
        }
    }

    private Properties loadPersonaProperties(Path path) {
        Properties props = new Properties();
        if (Files.exists(path)) {
            try (InputStream in = Files.newInputStream(path)) {
                props.load(in);
            } catch (IOException e) {
                log.warn("Failed to read persona.properties at '{}': {}", path, e.getMessage());
            }
        }
        return props;
    }

    private String loadContextFile(Path path) {
        try {
            if (!Files.exists(path)) {
                log.warn("Context file not found at '{}'. AI will run without context.", path);
                return "(No context provided.)";
            }
            String content = Files.readString(path);
            log.info("Loaded context from '{}'.", path);
            return content;
        } catch (IOException e) {
            log.error("Failed to read context file '{}': {}", path, e.getMessage());
            return "(Context unavailable.)";
        }
    }

    private String buildSystemPrompt(String context) {
        StringBuilder sb = new StringBuilder();

        sb.append("=== RULES (strictly enforced, non-negotiable) ===\n");
        for (int i = 0; i < rules.size(); i++) {
            sb.append(i + 1).append(". ").append(rules.get(i)).append("\n");
        }

        sb.append("\n=== CONTEXT ===\n");
        sb.append(context.trim());
        sb.append("\n========================\n");

        return sb.toString();
    }
}
