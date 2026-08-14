package in.pandac.chat.service;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Loads personal-context.txt and assembles the system prompt from
 * configurable rules (application.yml) + personal context file.
 */
@Service
@ConfigurationProperties(prefix = "app.ai")
public class PersonalContextService {

    private static final Logger log = LoggerFactory.getLogger(PersonalContextService.class);

    private String personalContextFile = "data/personal-context.txt";

    private List<String> rules;

    private String systemPrompt;

    // Getters and Setters for ConfigurationProperties
    public void setPersonalContextFile(String personalContextFile) {
        this.personalContextFile = personalContextFile;
    }

    public void setRules(List<String> rules) {
        this.rules = rules;
    }

    @PostConstruct
    public void init() {
        String context = loadContextFile();
        this.systemPrompt = buildSystemPrompt(context);
        log.info("Personal context loaded. System prompt ready ({} chars).", systemPrompt.length());
    }

    public String getSystemPrompt() {
        return systemPrompt;
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private String loadContextFile() {
        try {
            Path path = Path.of(personalContextFile);
            if (!Files.exists(path)) {
                log.warn("Personal context file not found at '{}'. AI will run without context.", personalContextFile);
                return "(No personal context provided.)";
            }
            String content = Files.readString(path);
            log.info("Loaded personal context from '{}'.", personalContextFile);
            return content;
        } catch (IOException e) {
            log.error("Failed to read personal context file '{}': {}", personalContextFile, e.getMessage());
            return "(Personal context unavailable.)";
        }
    }

    private String buildSystemPrompt(String context) {
        StringBuilder sb = new StringBuilder();

        sb.append("=== RULES (strictly enforced, non-negotiable) ===\n");
        for (int i = 0; i < rules.size(); i++) {
            sb.append(i + 1).append(". ").append(rules.get(i)).append("\n");
        }

        sb.append("\n=== PERSONAL CONTEXT ===\n");
        sb.append(context.trim());
        sb.append("\n========================\n");

        return sb.toString();
    }
}
