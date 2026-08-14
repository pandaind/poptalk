package in.pandac.chat.route;

import in.pandac.chat.dto.ConfigResponse;
import org.apache.camel.builder.RouteBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * GET /api/config — returns public branding configuration.
 * No authentication required. Safe to call from the embedded widget before session init.
 */
@Component
public class ConfigRoute extends RouteBuilder {

    @Value("${app.branding.owner-name}")
    private String ownerName;

    @Value("${app.branding.chat-title}")
    private String chatTitle;

    @Value("${app.branding.avatar-initial}")
    private String avatarInitial;

    @Override
    public void configure() {
        rest("/api/config")
            .get()
                .produces("application/json")
                .to("direct:getConfig");

        from("direct:getConfig")
            .routeId("config-route")
            .process(exchange -> exchange.getMessage().setBody(
                new ConfigResponse(ownerName, chatTitle, avatarInitial)
            ));
    }
}
