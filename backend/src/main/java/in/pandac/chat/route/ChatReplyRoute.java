package in.pandac.chat.route;

import in.pandac.chat.dto.ChatMessageResponse;
import in.pandac.chat.entity.ChatMessage;
import in.pandac.chat.exception.UnauthorizedException;
import in.pandac.chat.repository.ChatMessageRepository;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Component
public class ChatReplyRoute extends RouteBuilder {

    private final ChatMessageRepository messageRepo;

    public ChatReplyRoute(ChatMessageRepository messageRepo) {
        this.messageRepo = messageRepo;
    }

    @Override
    public void configure() {

        onException(UnauthorizedException.class)
            .handled(true)
            .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(401))
            .setBody(exceptionMessage());

        rest("/api/v1/chat/reply")
            .get()
            .to("direct:chat-reply");

        from("direct:chat-reply")
            .routeId("chat-reply")
            
            // 1. Verify JWT
            .process("jwtValidationProcessor")
            
            // 2. Query H2 for unread admin replies
            .process(exchange -> {
                String sessionId = exchange.getIn().getHeader("sessionId", String.class);

                Optional<ChatMessage> replyOpt = messageRepo
                        .findFirstBySessionIdAndDirectionAndReadFalseOrderBySentAtAsc(sessionId, "ADMIN");

                if (replyOpt.isPresent()) {
                    ChatMessage reply = replyOpt.get();
                    
                    // Mark as delivered
                    reply.setRead(true);
                    messageRepo.save(reply);

                    ChatMessageResponse res = new ChatMessageResponse(
                            UUID.randomUUID().toString(),
                            reply.getSentAt().toString(),
                            reply.getContent()
                    );

                    exchange.getIn().setBody(res);
                    exchange.getIn().setHeader(Exchange.HTTP_RESPONSE_CODE, 200);
                } else {
                    // No new replies
                    exchange.getIn().setBody(null);
                    exchange.getIn().setHeader(Exchange.HTTP_RESPONSE_CODE, 204);
                }
            });
    }
}
