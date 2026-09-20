package in.pandac.chat.route;

import in.pandac.chat.dto.ConfigResponse;
import in.pandac.chat.service.ChatModeService;
import in.pandac.chat.service.Persona;
import in.pandac.chat.service.PersonaService;
import org.apache.camel.builder.RouteBuilder;
import org.springframework.stereotype.Component;

/**
 * GET /api/config — returns public branding configuration.
 * No authentication required. Safe to call from the embedded widget before session init.
 * Accepts an optional ?persona=<id> query param for multi-persona deployments;
 * falls back to the default persona when absent or unknown.
 */
@Component
public class ConfigRoute extends RouteBuilder {

    private final PersonaService personaService;
    private final ChatModeService chatModeService;

    public ConfigRoute(PersonaService personaService, ChatModeService chatModeService) {
        this.personaService = personaService;
        this.chatModeService = chatModeService;
    }

    @Override
    public void configure() {
        rest("/api/config")
            .get()
                .produces("application/json")
                .to("direct:getConfig");

        from("direct:getConfig")
            .routeId("config-route")
            .process(exchange -> {
                String requestedPersona = exchange.getIn().getHeader("persona", String.class);
                Persona persona = personaService.getPersona(requestedPersona);
                exchange.getMessage().setBody(
                    new ConfigResponse(persona.ownerName(), persona.chatTitle(), persona.avatarInitial(),
                            chatModeService.getMode()));
            });
    }
}
