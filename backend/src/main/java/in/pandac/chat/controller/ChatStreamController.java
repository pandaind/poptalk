package in.pandac.chat.controller;

import in.pandac.chat.dto.ChatMessageRequest;
import in.pandac.chat.entity.ChatMessage;
import in.pandac.chat.entity.ChatSession;
import in.pandac.chat.exception.SessionNotFoundException;
import in.pandac.chat.exception.TooManyRequestsException;
import in.pandac.chat.exception.UnauthorizedException;
import in.pandac.chat.repository.ChatMessageRepository;
import in.pandac.chat.repository.ChatSessionRepository;
import in.pandac.chat.route.TelegramNotificationRoute;
import in.pandac.chat.service.AiChatService;
import in.pandac.chat.service.ChatModeService;
import in.pandac.chat.service.JwtService;
import in.pandac.chat.service.Persona;
import in.pandac.chat.service.PersonaService;
import in.pandac.chat.service.RateLimitService;
import in.pandac.chat.util.ClientIpResolver;
import in.pandac.chat.util.TelegramMessageFormatter;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.component.telegram.TelegramConstants;
import org.apache.camel.component.telegram.model.OutgoingTextMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.LocalDateTime;

/**
 * Streams the AI reply as it's generated instead of waiting for the whole
 * thing (see {@code /api/v1/chat/message}, which stays as the blocking
 * alternative — MANUAL mode has nothing to stream, so it keeps using that
 * one). Built as a plain Spring MVC controller rather than a Camel route:
 * Camel's REST DSL is one Exchange in, one response out, which doesn't map
 * onto progressively flushing a single HTTP response the way an
 * {@link SseEmitter} does.
 *
 * <p>Performs the same steps as {@code ChatMessageRoute}'s AI branch, in the
 * same order, reusing the same services directly as plain beans.
 */
@RestController
@RequestMapping("/api/v1/chat")
public class ChatStreamController {

    private static final Logger log = LoggerFactory.getLogger(ChatStreamController.class);

    private final ChatSessionRepository sessionRepository;
    private final ChatMessageRepository messageRepository;
    private final JwtService jwtService;
    private final RateLimitService rateLimitService;
    private final ChatModeService chatModeService;
    private final AiChatService aiChatService;
    private final PersonaService personaService;
    private final ProducerTemplate producerTemplate;

    @Value("${app.telegram.admin-chat-id}")
    private String adminChatId;

    @Value("${app.ai.stream-timeout-ms:120000}")
    private long streamTimeoutMs;

    public ChatStreamController(ChatSessionRepository sessionRepository,
                                 ChatMessageRepository messageRepository,
                                 JwtService jwtService,
                                 RateLimitService rateLimitService,
                                 ChatModeService chatModeService,
                                 AiChatService aiChatService,
                                 PersonaService personaService,
                                 ProducerTemplate producerTemplate) {
        this.sessionRepository = sessionRepository;
        this.messageRepository = messageRepository;
        this.jwtService = jwtService;
        this.rateLimitService = rateLimitService;
        this.chatModeService = chatModeService;
        this.aiChatService = aiChatService;
        this.personaService = personaService;
        this.producerTemplate = producerTemplate;
    }

    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@RequestHeader(value = "X-Real-IP", required = false) String realIp,
                              @RequestHeader(value = "X-Forwarded-For", required = false) String forwardedFor,
                              @RequestHeader("Authorization") String authorizationHeader,
                              @Valid @RequestBody ChatMessageRequest request,
                              HttpServletRequest servletRequest) {

        // 1. IP-level rate limit — same fallback chain as every other entry point.
        String clientIp = ClientIpResolver.resolve(realIp, forwardedFor, servletRequest.getRemoteAddr());
        rateLimitService.checkAndConsume(clientIp);

        // 2. JWT validation.
        String sessionId = validateAndExtractSessionId(authorizationHeader);

        // 3. Per-session message quota.
        rateLimitService.checkMessageLimit(sessionId);

        // 4. Resolve the session (bean validation on `request` already ran via @Valid).
        ChatSession session = sessionRepository.findBySessionIdAndActiveTrue(sessionId)
                .orElseThrow(() -> new SessionNotFoundException(sessionId));

        if (!chatModeService.isAiMode()) {
            // MANUAL mode has no AI response to stream — the widget only calls
            // this endpoint when chatMode is "AI" (see GET /api/config), so
            // this is a defensive guard, not the normal path.
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Streaming is only available in AI mode");
        }

        // 5. Persist the user message.
        ChatMessage userMsg = new ChatMessage();
        userMsg.setSessionId(sessionId);
        userMsg.setDirection("USER");
        userMsg.setContent(request.getMessage());
        userMsg.setSentAt(LocalDateTime.now());
        userMsg.setRead(true);
        messageRepository.save(userMsg);

        Persona persona = personaService.getPersona(session.getPersonaId());
        SseEmitter emitter = new SseEmitter(streamTimeoutMs);
        StringBuilder fullResponse = new StringBuilder();

        aiChatService.chatStream(sessionId, persona.id(), session.getName(), request.getMessage())
                .subscribe(
                        chunk -> sendChunk(emitter, fullResponse, chunk),
                        error -> handleStreamError(emitter, sessionId, error),
                        () -> handleStreamComplete(emitter, session, persona, request.getMessage(), fullResponse.toString()));

        return emitter;
    }

    private void sendChunk(SseEmitter emitter, StringBuilder fullResponse, String chunk) {
        fullResponse.append(chunk);
        try {
            emitter.send(SseEmitter.event().data(chunk));
        } catch (IOException e) {
            // The visitor navigated away or the connection dropped — nothing
            // more to send; the subscription will be cancelled on its own.
            log.debug("Could not write a chunk (client likely disconnected): {}", e.getMessage());
        }
    }

    private void handleStreamError(SseEmitter emitter, String sessionId, Throwable error) {
        log.error("AI stream error for session {}: {}", sessionId, error.getMessage());
        try {
            emitter.send(SseEmitter.event().name("error").data("stream failed"));
        } catch (IOException ignored) {
            // Connection already gone — nothing to notify.
        }
        emitter.completeWithError(error);
    }

    private void handleStreamComplete(SseEmitter emitter, ChatSession session, Persona persona,
                                       String userMessage, String aiResponse) {
        ChatMessage aiMsg = new ChatMessage();
        aiMsg.setSessionId(session.getSessionId());
        aiMsg.setDirection("AI");
        aiMsg.setContent(aiResponse);
        aiMsg.setSentAt(LocalDateTime.now());
        aiMsg.setRead(false);
        messageRepository.save(aiMsg);

        // Fire-and-forget via TelegramNotificationRoute (see its Javadoc) —
        // the visitor has already received the full streamed reply by this
        // point, so nothing about Telegram's timing or success may affect
        // the "done" event below.
        try {
            String telegramText = TelegramMessageFormatter.aiModeNotification(
                    personaService.isMultiPersona(), persona.ownerName(),
                    session.getName(), userMessage, aiResponse, session.getSessionId());
            producerTemplate.sendBodyAndHeader(TelegramNotificationRoute.ENDPOINT,
                    OutgoingTextMessage.builder().text(telegramText).build(),
                    TelegramConstants.TELEGRAM_CHAT_ID, adminChatId);
        } catch (Exception e) {
            log.warn("Failed to send Telegram AI-mode notification for session {}: {}",
                    session.getSessionId(), e.getMessage());
        }

        try {
            emitter.send(SseEmitter.event().name("done").data(session.getSessionId()));
        } catch (IOException ignored) {
            // Connection already gone — the message is already persisted either way.
        }
        emitter.complete();
    }

    private String validateAndExtractSessionId(String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
            throw new UnauthorizedException("Missing or invalid Authorization header");
        }
        try {
            Claims claims = jwtService.validateToken(authorizationHeader.substring(7));
            return claims.getSubject();
        } catch (JwtException e) {
            throw new UnauthorizedException("Invalid or expired token");
        }
    }

    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<String> handleUnauthorized(UnauthorizedException e) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(e.getMessage());
    }

    @ExceptionHandler(SessionNotFoundException.class)
    public ResponseEntity<String> handleSessionNotFound(SessionNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
    }

    @ExceptionHandler(TooManyRequestsException.class)
    public ResponseEntity<String> handleTooManyRequests(TooManyRequestsException e) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<String> handleValidation(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().isEmpty()
                ? "Invalid request"
                : e.getBindingResult().getFieldErrors().get(0).getDefaultMessage();
        return ResponseEntity.badRequest().body(message);
    }
}
