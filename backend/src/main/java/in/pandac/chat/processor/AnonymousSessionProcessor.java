package in.pandac.chat.processor;

import in.pandac.chat.dto.ContactResponse;
import in.pandac.chat.entity.ChatSession;
import in.pandac.chat.repository.ChatSessionRepository;
import in.pandac.chat.service.JwtService;
import in.pandac.chat.service.Persona;
import in.pandac.chat.service.PersonaService;
import in.pandac.chat.service.RateLimitService;
import in.pandac.chat.util.ClientIpResolver;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Starts a chat session for a visitor who has not been asked for any details.
 *
 * <p>Most visitors of a docs or marketing site just want an answer; requiring a name and an email
 * first costs most of them. The widget therefore asks for a session with no body at all, and the
 * visitor stays anonymous: a random session id and a signed token, nothing else. The session row
 * keeps neutral placeholders (the columns are mandatory) so the AI prompt and history still work.
 *
 * <p>Unlike {@link ContactRegistrationProcessor} this sends no "new visitor" notification: there
 * is nothing to tell the owner yet. In manual mode the owner hears about the visitor when a
 * message arrives, which is the first moment there is something to answer.
 */
@Component
public class AnonymousSessionProcessor implements Processor {

    /** Stored as the session's name; also what the AI prompt calls the visitor. */
    public static final String ANONYMOUS_NAME = "Anonymous";
    /** Stored as the session's contact type; fits the 10 character column. */
    public static final String ANONYMOUS_CONTACT_TYPE = "none";

    private final ChatSessionRepository sessionRepository;
    private final JwtService jwtService;
    private final RateLimitService rateLimitService;
    private final PersonaService personaService;

    public AnonymousSessionProcessor(
            ChatSessionRepository sessionRepository,
            JwtService jwtService,
            RateLimitService rateLimitService,
            PersonaService personaService) {
        this.sessionRepository = sessionRepository;
        this.jwtService = jwtService;
        this.rateLimitService = rateLimitService;
        this.personaService = personaService;
    }

    @Override
    public void process(Exchange exchange) {
        // Same IP-based limit as registration: starting sessions is the cheap way to probe the
        // backend, so it is limited exactly like the endpoint it replaces.
        HttpServletRequest servletRequest = exchange.getIn()
                .getHeader("CamelHttpServletRequest", HttpServletRequest.class);
        String clientIp = ClientIpResolver.resolve(
                exchange.getIn().getHeader("X-Real-IP", String.class),
                exchange.getIn().getHeader("X-Forwarded-For", String.class),
                servletRequest != null ? servletRequest.getRemoteAddr() : null);
        rateLimitService.checkAndConsume(clientIp);

        Persona persona = personaService.getPersona(exchange.getIn().getHeader("persona", String.class));

        String sessionId = UUID.randomUUID().toString();
        LocalDateTime now = LocalDateTime.now();

        ChatSession session = new ChatSession();
        session.setSessionId(sessionId);
        session.setName(ANONYMOUS_NAME);
        session.setContact("");
        session.setContactType(ANONYMOUS_CONTACT_TYPE);
        session.setPersonaId(persona.id());
        session.setCreatedAt(now);
        session.setExpiresAt(now.plusDays(1));
        session.setActive(true);
        sessionRepository.save(session);

        String token = jwtService.generateToken(sessionId, "");
        exchange.getIn().setBody(new ContactResponse(token, sessionId, "Welcome! How can I help you today?"));
    }
}
