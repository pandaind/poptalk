package in.pandac.chat.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PersonaServiceTest {

    private static PersonaService newService() {
        PersonaService service = new PersonaService();
        service.setRules(List.of("Stay in character."));
        ReflectionTestUtils.setField(service, "defaultOwnerName", "Default Owner");
        ReflectionTestUtils.setField(service, "defaultChatTitle", "Chat");
        ReflectionTestUtils.setField(service, "defaultAvatarInitial", "D");
        ReflectionTestUtils.setField(service, "defaultWebsiteUrl", "");
        ReflectionTestUtils.setField(service, "defaultProvider", "ollama");
        ReflectionTestUtils.setField(service, "defaultMcpEnabled", false);
        ReflectionTestUtils.setField(service, "defaultMcpApiKey", "");
        return service;
    }

    @Test
    void fallsBackToASinglePersonaWhenNoPersonasDirectoryExists(@TempDir Path tempDir) throws IOException {
        Path context = tempDir.resolve("context.txt");
        Files.writeString(context, "I am Jane, a developer.");
        PersonaService service = newService();
        service.setContextFile(context.toString());
        service.setPersonasDir(tempDir.resolve("no-such-dir").toString());
        service.setDefaultPersona("default");

        service.init();

        assertThat(service.isMultiPersona()).isFalse();
        Persona persona = service.getDefaultPersona();
        assertThat(persona.id()).isEqualTo("default");
        assertThat(persona.ownerName()).isEqualTo("Default Owner");
        assertThat(persona.systemPrompt()).contains("I am Jane, a developer.");
    }

    @Test
    void loadsEveryPersonaSubdirectoryThatHasAContextFile(@TempDir Path tempDir) throws IOException {
        Path personas = tempDir.resolve("personas");
        Files.createDirectories(personas.resolve("alice"));
        Files.writeString(personas.resolve("alice/context.txt"), "I am Alice.");
        Files.writeString(personas.resolve("alice/persona.properties"), """
                ownerName=Alice Smith
                provider=anthropic
                temperature=0.7
                mcp=true
                mcp-api-key=alice-key
                """);

        Files.createDirectories(personas.resolve("bob"));
        Files.writeString(personas.resolve("bob/context.txt"), "I am Bob.");
        // No persona.properties for bob — everything falls back to defaults.

        Files.createDirectories(personas.resolve("empty-dir")); // no context.txt — must be skipped

        PersonaService service = newService();
        service.setPersonasDir(personas.toString());
        service.setDefaultPersona("bob");

        service.init();

        assertThat(service.isMultiPersona()).isTrue();
        assertThat(service.getAllPersonas()).extracting(Persona::id).containsExactlyInAnyOrder("alice", "bob");

        Persona alice = service.getPersona("alice");
        assertThat(alice.ownerName()).isEqualTo("Alice Smith");
        assertThat(alice.provider()).isEqualTo("anthropic");
        assertThat(alice.temperature()).isEqualTo(0.7);
        assertThat(alice.mcpEnabled()).isTrue();
        assertThat(alice.mcpApiKey()).isEqualTo("alice-key");

        Persona bob = service.getPersona("bob");
        assertThat(bob.ownerName()).isEqualTo("Default Owner");
        assertThat(bob.provider()).isEqualTo("ollama");
        assertThat(bob.mcpEnabled()).isFalse();
    }

    @Test
    void anInvalidTemperatureIsIgnoredRatherThanFailingPersonaLoad(@TempDir Path tempDir) throws IOException {
        Path personas = tempDir.resolve("personas");
        Files.createDirectories(personas.resolve("alice"));
        Files.writeString(personas.resolve("alice/context.txt"), "I am Alice.");
        Files.writeString(personas.resolve("alice/persona.properties"), "temperature=not-a-number\n");

        PersonaService service = newService();
        service.setPersonasDir(personas.toString());
        service.setDefaultPersona("alice");

        service.init();

        assertThat(service.getPersona("alice").temperature()).isNull();
    }

    @Test
    void getPersonaFallsBackToTheDefaultForAnUnknownOrBlankId(@TempDir Path tempDir) throws IOException {
        Path personas = tempDir.resolve("personas");
        Files.createDirectories(personas.resolve("alice"));
        Files.writeString(personas.resolve("alice/context.txt"), "I am Alice.");

        PersonaService service = newService();
        service.setPersonasDir(personas.toString());
        service.setDefaultPersona("alice");
        service.init();

        assertThat(service.getPersona("does-not-exist").id()).isEqualTo("alice");
        assertThat(service.getPersona(null).id()).isEqualTo("alice");
        assertThat(service.getPersona("").id()).isEqualTo("alice");
    }
}
