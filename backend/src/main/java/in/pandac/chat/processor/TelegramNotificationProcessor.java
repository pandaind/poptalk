package in.pandac.chat.processor;

import in.pandac.chat.dto.ContactRequest;
import in.pandac.chat.service.PersonaService;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.component.telegram.TelegramConstants;
import org.apache.camel.component.telegram.TelegramParseMode;
import org.apache.camel.component.telegram.model.OutgoingTextMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class TelegramNotificationProcessor implements Processor {

    @Value("${app.telegram.admin-chat-id}")
    private String adminChatId;

    private final PersonaService personaService;

    public TelegramNotificationProcessor(PersonaService personaService) {
        this.personaService = personaService;
    }

    @Override
    public void process(Exchange exchange) {
        ContactRequest req = exchange.getIn().getBody(ContactRequest.class);
        String sessionId = exchange.getIn().getHeader("sessionId", String.class);
        String personaOwnerName = exchange.getIn().getHeader("personaOwnerName", String.class);

        // Build Markdown-formatted message for Telegram
        StringBuilder text = new StringBuilder();
        text.append("🔔 *New Chat Visitor*\n\n");
        if (personaService.isMultiPersona() && personaOwnerName != null) {
            text.append("🎭 *Persona*: ").append(personaOwnerName).append("\n");
        }
        text.append("👤 *Name*: ").append(req.getName()).append("\n");
        text.append("📞 *").append(req.getContactType()).append("*: ").append(req.getContact()).append("\n");
        
        if (req.getSource() != null) text.append("🔗 *Source*: ").append(req.getSource()).append("\n");
        if (req.getReferrer() != null) text.append("🌐 *Referrer*: ").append(req.getReferrer()).append("\n");
        
        text.append("\n`[SID:").append(sessionId).append("]`");

        // The camel-telegram component looks for this header to know where to send it
        exchange.getIn().setHeader(TelegramConstants.TELEGRAM_CHAT_ID, adminChatId);

        // Create the OutgoingTextMessage object expected by the telegram component
        OutgoingTextMessage msg = OutgoingTextMessage.builder()
                .text(text.toString())
                .parseMode("Markdown")
                .build();
                
        exchange.getIn().setBody(msg);
    }
}
