package in.pandac.chat.route;

import in.pandac.chat.entity.ChatSession;
import in.pandac.chat.exception.SessionNotFoundException;
import in.pandac.chat.exception.UnauthorizedException;
import in.pandac.chat.repository.ChatSessionRepository;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.telegram.TelegramConstants;
import org.apache.camel.component.telegram.model.OutgoingTextMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class ChatEndRoute extends RouteBuilder {

    @Value("${app.telegram.admin-chat-id}")
    private String adminChatId;

    private final ChatSessionRepository sessionRepo;

    public ChatEndRoute(ChatSessionRepository sessionRepo) {
        this.sessionRepo = sessionRepo;
    }

    @Override
    public void configure() {

        onException(UnauthorizedException.class)
            .handled(true)
            .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(401))
            .setBody(exceptionMessage());

        onException(SessionNotFoundException.class)
            .handled(true)
            .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(404))
            .setBody(exceptionMessage());

        rest("/api/v1/chat/end")
            .post()
            .to("direct:chat-end");

        from("direct:chat-end")
            .routeId("chat-end")
            
            // 1. Verify JWT
            .process("jwtValidationProcessor")
            
            // 2. Mark session inactive and prepare Telegram payload
            .process(exchange -> {
                String sessionId = exchange.getIn().getHeader("sessionId", String.class);

                ChatSession session = sessionRepo.findBySessionIdAndActiveTrue(sessionId)
                        .orElseThrow(() -> new SessionNotFoundException(sessionId));

                // Mark session as inactive
                session.setActive(false);
                sessionRepo.save(session);

                // Format notification for Telegram
                String telegramText = String.format("🚫 *%s* has ended the chat session.\n\n`[SID:%s]`",
                        session.getName(), sessionId);

                exchange.getIn().setHeader(TelegramConstants.TELEGRAM_CHAT_ID, adminChatId);
                OutgoingTextMessage outMsg = OutgoingTextMessage.builder()
                        .text(telegramText)
                        .build();

                exchange.getIn().setBody(outMsg);
            })
            
            // 3. Send notification to Telegram
            .to("telegram:bots")
            
            // 4. Return success
            .process(exchange -> {
                exchange.getIn().setBody(Map.of("message", "Session ended successfully"));
                exchange.getIn().setHeader(Exchange.HTTP_RESPONSE_CODE, 200);
            });
    }
}
