package in.pandac.chat.processor;

import in.pandac.chat.dto.ContactRequest;
import in.pandac.chat.dto.ContactResponse;
import in.pandac.chat.entity.ChatSession;
import in.pandac.chat.repository.ChatSessionRepository;
import in.pandac.chat.service.JwtService;
import in.pandac.chat.service.RateLimitService;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.UUID;

@Component
public class ContactRegistrationProcessor implements Processor {

    private final ChatSessionRepository sessionRepository;
    private final JwtService jwtService;
    private final RateLimitService rateLimitService;

    public ContactRegistrationProcessor(
            ChatSessionRepository sessionRepository,
            JwtService jwtService,
            RateLimitService rateLimitService) {
        this.sessionRepository = sessionRepository;
        this.jwtService = jwtService;
        this.rateLimitService = rateLimitService;
    }

    @Override
    public void process(Exchange exchange) {
        // Rate limiting based on IP address
        String clientIp = exchange.getIn().getHeader("X-Real-IP", String.class);
        if (clientIp == null) {
            clientIp = exchange.getIn().getHeader("X-Forwarded-For", String.class);
        }
        rateLimitService.checkAndConsume(clientIp);

        // Body was unmarshaled to ContactRequest by Camel REST DSL
        ContactRequest req = exchange.getIn().getBody(ContactRequest.class);

        // Generate an 8-character sessionId for compact Telegram tags: [SID:a1b2c3d4]
        String sessionId = UUID.randomUUID().toString().substring(0, 8);

        // Persist session to H2
        ChatSession session = new ChatSession();
        session.setSessionId(sessionId);
        session.setName(req.getName());
        session.setContact(req.getContact());
        session.setContactType(req.getContactType());
        session.setCreatedAt(LocalDateTime.now());
        session.setExpiresAt(LocalDateTime.now().plusDays(1)); // 24h validity
        session.setActive(true);
        sessionRepository.save(session);

        // Generate JWT
        String token = jwtService.generateToken(sessionId, req.getContact());

        // Prepare response
        ContactResponse response = new ContactResponse(
                token,
                sessionId,
                "Welcome! How can I help you today?"
        );

        // Put response in a header so the route can return it later
        // after sending the Telegram notification
        exchange.getIn().setHeader("contactResponse", response);

        // Pass the original request to the next processor to build the Telegram message
        exchange.getIn().setBody(req);
        exchange.getIn().setHeader("sessionId", sessionId);
    }
}
