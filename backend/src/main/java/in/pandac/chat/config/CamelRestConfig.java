package in.pandac.chat.config;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.rest.RestBindingMode;
import org.springframework.stereotype.Component;

/**
 * Configures Camel REST DSL to use the platform-http component, which
 * hooks into Spring Boot's embedded Tomcat — no separate HTTP server needed.
 *
 * bindingMode(json) means:
 *  - Incoming: JSON body → auto-unmarshaled to the DTO class declared in rest().type()
 *  - Outgoing: any Java object body → auto-marshaled to JSON
 */
@Component
public class CamelRestConfig extends RouteBuilder {

    @Override
    public void configure() {
        restConfiguration()
            .component("platform-http")
            .bindingMode(RestBindingMode.json)
            .dataFormatProperty("prettyPrint", "false")
            .enableCORS(false);   // CORS is handled by Spring CorsFilter
    }
}
