package in.pandac.chat.route;

import in.pandac.chat.exception.TooManyRequestsException;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.springframework.stereotype.Component;

/**
 * {@code POST /api/v1/session[?persona=<id>]}: start a chat session without asking the visitor for
 * anything. Takes no body. Returns {@code {token, sessionId, message}}, like the registration
 * endpoint, so the rest of the widget flow is unchanged.
 */
@Component
public class AnonymousSessionRoute extends RouteBuilder {

    @Override
    public void configure() {
        onException(TooManyRequestsException.class)
                .handled(true)
                .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(429))
                .setBody(exceptionMessage());

        onException(Exception.class)
                .handled(true)
                .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(400))
                .setBody(exceptionMessage());

        rest("/api/v1/session")
                .post()
                .to("direct:anonymous-session");

        from("direct:anonymous-session")
                .routeId("anonymous-session")
                .process("anonymousSessionProcessor");
    }
}
