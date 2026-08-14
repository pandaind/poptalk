package in.pandac.chat.route;

import in.pandac.chat.entity.ChatMessage;
import in.pandac.chat.exception.UnauthorizedException;
import in.pandac.chat.repository.ChatMessageRepository;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class ChatHistoryRoute extends RouteBuilder {

    private final ChatMessageRepository messageRepo;

    public ChatHistoryRoute(ChatMessageRepository messageRepo) {
        this.messageRepo = messageRepo;
    }

    @Override
    public void configure() {

        onException(UnauthorizedException.class)
            .handled(true)
            .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(401))
            .setBody(exceptionMessage());

        rest("/api/v1/chat/history")
            .get()
            .to("direct:chat-history");

        from("direct:chat-history")
            .routeId("chat-history")
            
            // 1. Verify JWT
            .process("jwtValidationProcessor")
            
            // 2. Fetch full conversation history
            .process(exchange -> {
                String sessionId = exchange.getIn().getHeader("sessionId", String.class);

                List<ChatMessage> messages = messageRepo.findBySessionIdOrderBySentAtAsc(sessionId);

                // Map entities to simple output format expected by chat.js
                List<Map<String, Object>> history = messages.stream()
                    .map(m -> Map.<String, Object>of(
                        "direction", m.getDirection(),
                        "content", m.getContent(),
                        "sentAt", m.getSentAt().toString()
                    ))
                    .collect(Collectors.toList());

                exchange.getIn().setBody(history);
            });
    }
}
