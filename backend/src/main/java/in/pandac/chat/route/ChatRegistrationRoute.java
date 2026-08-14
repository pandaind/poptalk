package in.pandac.chat.route;

import in.pandac.chat.dto.ContactRequest;
import in.pandac.chat.exception.TooManyRequestsException;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.springframework.stereotype.Component;

@Component
public class ChatRegistrationRoute extends RouteBuilder {

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

        rest("/api/v1/chat")
            .post()
            .type(ContactRequest.class)
            .to("direct:chat-registration");

        from("direct:chat-registration")
            .routeId("chat-registration")
            
            // 1. Validate bean constraints (Jakarta Validation)
            .to("bean-validator:contactRequest")
            
            // 2. Persist to H2, check rate limit, generate JWT
            .process("contactRegistrationProcessor")
            
            // 3. Format message for Telegram
            .process("telegramNotificationProcessor")
            
            // 4. Send to Telegram API (native camel component)
            .to("telegram:bots")
            
            // 5. Return the JWT response we prepped in step 2
            .setBody(header("contactResponse"));
    }
}
