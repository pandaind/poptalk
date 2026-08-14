package in.pandac.chat.route;

import in.pandac.chat.entity.ChatMessage;
import in.pandac.chat.repository.ChatMessageRepository;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.telegram.model.IncomingMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
public class TelegramConsumerRoute extends RouteBuilder {

    @Value("${app.telegram.admin-chat-id}")
    private String adminChatId;

    private final ChatMessageRepository messageRepo;

    public TelegramConsumerRoute(ChatMessageRepository messageRepo) {
        this.messageRepo = messageRepo;
    }

    @Override
    public void configure() {
        
        // This polls the Telegram API for updates (admin replies)
        from("telegram:bots")
            .routeId("telegram-consumer")
            
            // 1. Log incoming message
            .log("Received Telegram message: ${body}")
            
            // 2. Filter: Only accept messages from the designated admin chat ID
            .filter(exchange -> {
                IncomingMessage msg = exchange.getIn().getBody(IncomingMessage.class);
                if (msg == null || msg.getFrom() == null) return false;
                
                String fromId = String.valueOf(msg.getFrom().getId());
                // The chat ID could be negative for group chats
                String chatId = String.valueOf(msg.getChat().getId());
                
                return adminChatId.equals(fromId) || adminChatId.equals(chatId);
            })
            
            // 3. Parse the [SID:xxx] tag from the message
            .process("telegramReplyParser")
            
            // 4. If we found a valid tag, persist the reply
            .choice()
                .when(header("sessionId").isNotNull())
                    .process(exchange -> {
                        String sessionId = exchange.getIn().getHeader("sessionId", String.class);
                        String replyText = exchange.getIn().getHeader("replyText", String.class);
                        
                        // Save the admin reply to H2
                        // read=false means it hasn't been picked up by the frontend yet
                        ChatMessage adminReply = new ChatMessage();
                        adminReply.setSessionId(sessionId);
                        adminReply.setDirection("ADMIN");
                        adminReply.setContent(replyText);
                        adminReply.setSentAt(LocalDateTime.now());
                        adminReply.setRead(false);
                                
                        messageRepo.save(adminReply);
                    })
                    .log("Saved admin reply for session: ${header.sessionId}")
                .otherwise()
                    .log("Ignored admin message: missing [SID:xxx] tag")
            .end();
    }
}
