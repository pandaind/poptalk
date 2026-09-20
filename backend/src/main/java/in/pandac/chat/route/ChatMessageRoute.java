package in.pandac.chat.route;

import in.pandac.chat.dto.ChatMessageRequest;
import in.pandac.chat.dto.ChatMessageResponse;
import in.pandac.chat.entity.ChatMessage;
import in.pandac.chat.entity.ChatSession;
import in.pandac.chat.exception.SessionNotFoundException;
import in.pandac.chat.exception.TooManyRequestsException;
import in.pandac.chat.exception.UnauthorizedException;
import in.pandac.chat.repository.ChatMessageRepository;
import in.pandac.chat.repository.ChatSessionRepository;
import in.pandac.chat.service.AiChatService;
import in.pandac.chat.service.ChatModeService;
import in.pandac.chat.service.Persona;
import in.pandac.chat.service.PersonaService;
import in.pandac.chat.service.RateLimitService;
import in.pandac.chat.util.ClientIpResolver;
import in.pandac.chat.util.TelegramMessageFormatter;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.telegram.TelegramConstants;
import org.apache.camel.component.telegram.model.OutgoingTextMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

@Component
public class ChatMessageRoute extends RouteBuilder {

    @Value("${app.telegram.admin-chat-id}")
    private String adminChatId;

    private final ChatSessionRepository sessionRepo;
    private final ChatMessageRepository messageRepo;
    private final RateLimitService rateLimitService;
    private final ChatModeService chatModeService;
    private final AiChatService aiChatService;
    private final PersonaService personaService;

    public ChatMessageRoute(ChatSessionRepository sessionRepo,
                            ChatMessageRepository messageRepo,
                            RateLimitService rateLimitService,
                            ChatModeService chatModeService,
                            AiChatService aiChatService,
                            PersonaService personaService) {
        this.sessionRepo      = sessionRepo;
        this.messageRepo      = messageRepo;
        this.rateLimitService = rateLimitService;
        this.chatModeService  = chatModeService;
        this.aiChatService    = aiChatService;
        this.personaService   = personaService;
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

        onException(TooManyRequestsException.class)
            .handled(true)
            .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(429))
            .setBody(exceptionMessage());

        rest("/api/v1/chat/message")
            .post()
            .type(ChatMessageRequest.class)
            .to("direct:chat-message");

        from("direct:chat-message")
            .routeId("chat-message")

            // 1. IP-level rate limit (protects against flood even without a token).
            // Proxy headers are used only when present; always fall back to the
            // real TCP socket address which cannot be spoofed by the client.
            .process(exchange -> {
                HttpServletRequest req = exchange.getIn()
                        .getHeader("CamelHttpServletRequest", HttpServletRequest.class);
                String ip = ClientIpResolver.resolve(
                        exchange.getIn().getHeader("X-Real-IP", String.class),
                        exchange.getIn().getHeader("X-Forwarded-For", String.class),
                        req != null ? req.getRemoteAddr() : null);
                rateLimitService.checkAndConsume(ip);
            })

            // 2. Validate JWT — extracts sessionId into header
            .process("jwtValidationProcessor")

            // 3. Per-session message quota (anti-DDoS via stolen token)
            .process(exchange -> {
                String sessionId = exchange.getIn().getHeader("sessionId", String.class);
                rateLimitService.checkMessageLimit(sessionId);
            })

            // 4. Bean validation
            .to("bean-validator:chatMessageRequest")

            // 5. Persist user message + resolve session
            .process(exchange -> {
                String sessionId = exchange.getIn().getHeader("sessionId", String.class);
                ChatMessageRequest req = exchange.getIn().getBody(ChatMessageRequest.class);

                ChatSession session = sessionRepo.findBySessionIdAndActiveTrue(sessionId)
                        .orElseThrow(() -> new SessionNotFoundException(sessionId));

                // Persist user message
                ChatMessage userMsg = new ChatMessage();
                userMsg.setSessionId(sessionId);
                userMsg.setDirection("USER");
                userMsg.setContent(req.getMessage());
                userMsg.setSentAt(LocalDateTime.now());
                userMsg.setRead(true);
                messageRepo.save(userMsg);

                // Stash session + request for subsequent steps
                exchange.getIn().setHeader("chatSession", session);
                exchange.getIn().setBody(req);
            })

            // 6. Branch: AI mode or MANUAL mode
            .choice()
                .when(exchange -> chatModeService.isAiMode())
                    .to("direct:chat-message-ai")
                .otherwise()
                    .to("direct:chat-message-manual")
            .end();

        // ── AI Branch ──────────────────────────────────────────────────────────
        from("direct:chat-message-ai")
            .routeId("chat-message-ai")
            .process(exchange -> {
                ChatMessageRequest req     = exchange.getIn().getBody(ChatMessageRequest.class);
                ChatSession        session = exchange.getIn().getHeader("chatSession", ChatSession.class);
                String             sid     = session.getSessionId();
                Persona            persona = personaService.getPersona(session.getPersonaId());

                // Call Ollama via Spring AI
                String aiResponse = aiChatService.chat(sid, persona.id(), session.getName(), req.getMessage());

                // Persist AI response
                ChatMessage aiMsg = new ChatMessage();
                aiMsg.setSessionId(sid);
                aiMsg.setDirection("AI");
                aiMsg.setContent(aiResponse);
                aiMsg.setSentAt(LocalDateTime.now());
                aiMsg.setRead(false);
                messageRepo.save(aiMsg);

                // Notify Telegram with user + AI pair (you still see the conversation)
                String telegramText = TelegramMessageFormatter.aiModeNotification(
                        personaService.isMultiPersona(), persona.ownerName(),
                        session.getName(), req.getMessage(), aiResponse, sid);

                exchange.getIn().setHeader(TelegramConstants.TELEGRAM_CHAT_ID, adminChatId);
                exchange.getIn().setBody(OutgoingTextMessage.builder().text(telegramText).build());
                exchange.getIn().setHeader("aiResponse", aiResponse);
            })
            // Hand off to TelegramNotificationRoute (fire-and-forget) — see
            // its Javadoc for why this isn't a direct .to("telegram:bots").
            .to(TelegramNotificationRoute.ENDPOINT)
            // Return AI response immediately (no polling required)
            .process(exchange -> {
                String aiResponse = exchange.getIn().getHeader("aiResponse", String.class);
                exchange.getIn().setBody(new ChatMessageResponse(
                    UUID.randomUUID().toString(),
                    Instant.now().toString(),
                    aiResponse
                ));
            });

        // ── MANUAL Branch ──────────────────────────────────────────────────────
        from("direct:chat-message-manual")
            .routeId("chat-message-manual")
            .process(exchange -> {
                ChatMessageRequest req     = exchange.getIn().getBody(ChatMessageRequest.class);
                ChatSession        session = exchange.getIn().getHeader("chatSession", ChatSession.class);
                Persona            persona = personaService.getPersona(session.getPersonaId());

                // Format for Telegram — admin replies with [SID:xxx] as before
                String personaTag = personaService.isMultiPersona() ? " (" + persona.ownerName() + ")" : "";
                String telegramText = String.format(
                    "💬%s *%s* says:\n\n%s\n\n`[SID:%s]`",
                    personaTag, session.getName(), req.getMessage(), session.getSessionId());

                exchange.getIn().setHeader(TelegramConstants.TELEGRAM_CHAT_ID, adminChatId);
                exchange.getIn().setBody(OutgoingTextMessage.builder().text(telegramText).build());
            })
            .to(TelegramNotificationRoute.ENDPOINT)
            // Response is null — frontend will poll /api/v1/chat/reply
            .process(exchange -> exchange.getIn().setBody(
                new ChatMessageResponse(UUID.randomUUID().toString(), Instant.now().toString(), null)
            ));
    }
}
