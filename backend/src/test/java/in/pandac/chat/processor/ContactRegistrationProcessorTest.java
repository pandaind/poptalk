package in.pandac.chat.processor;

import in.pandac.chat.dto.ContactRequest;
import in.pandac.chat.dto.ContactResponse;
import in.pandac.chat.entity.ChatSession;
import in.pandac.chat.repository.ChatSessionRepository;
import in.pandac.chat.service.JwtService;
import in.pandac.chat.service.Persona;
import in.pandac.chat.service.PersonaService;
import in.pandac.chat.service.RateLimitService;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ContactRegistrationProcessorTest {

    private final ChatSessionRepository sessionRepository = mock(ChatSessionRepository.class);
    private final JwtService jwtService = mock(JwtService.class);
    private final RateLimitService rateLimitService = mock(RateLimitService.class);
    private final PersonaService personaService = mock(PersonaService.class);
    private final ContactRegistrationProcessor processor =
            new ContactRegistrationProcessor(sessionRepository, jwtService, rateLimitService, personaService);

    @Test
    void prefersXRealIpOverEveryOtherAddressSource() {
        Persona alice = persona("alice");
        when(personaService.getPersona(any())).thenReturn(alice);
        when(jwtService.generateToken(any(), any())).thenReturn("token-123");
        Exchange exchange = exchangeWith("1.1.1.1", "2.2.2.2", "3.3.3.3", "alice");

        processor.process(exchange);

        verify(rateLimitService).checkAndConsume("1.1.1.1");
    }

    @Test
    void fallsBackToForwardedForWhenRealIpIsAbsent() {
        when(personaService.getPersona(any())).thenReturn(persona("alice"));
        when(jwtService.generateToken(any(), any())).thenReturn("token-123");
        Exchange exchange = exchangeWith(null, "2.2.2.2", "3.3.3.3", "alice");

        processor.process(exchange);

        verify(rateLimitService).checkAndConsume("2.2.2.2");
    }

    @Test
    void fallsBackToTheServletRemoteAddressWhenNoProxyHeadersArePresent() {
        when(personaService.getPersona(any())).thenReturn(persona("alice"));
        when(jwtService.generateToken(any(), any())).thenReturn("token-123");
        Exchange exchange = exchangeWith(null, null, "3.3.3.3", "alice");

        processor.process(exchange);

        verify(rateLimitService).checkAndConsume("3.3.3.3");
    }

    @Test
    void persistsTheSessionAndPreparesTheResponse() {
        Persona alice = persona("alice");
        when(personaService.getPersona("alice")).thenReturn(alice);
        when(jwtService.generateToken(any(), eq("visitor@example.com"))).thenReturn("token-123");
        Exchange exchange = exchangeWith("9.9.9.9", null, null, "alice");

        processor.process(exchange);

        ArgumentCaptor<ChatSession> sessionCaptor = ArgumentCaptor.forClass(ChatSession.class);
        verify(sessionRepository).save(sessionCaptor.capture());
        ChatSession saved = sessionCaptor.getValue();
        assertThat(saved.getName()).isEqualTo("Visitor Name");
        assertThat(saved.getContact()).isEqualTo("visitor@example.com");
        assertThat(saved.getPersonaId()).isEqualTo("alice");
        assertThat(saved.isActive()).isTrue();

        verify(exchange.getIn()).setHeader(eq("contactResponse"), any(ContactResponse.class));
        verify(exchange.getIn()).setHeader("personaOwnerName", "Alice Owner");
    }

    private static Persona persona(String id) {
        return new Persona(id, "Alice Owner", "Chat", "A", null, "ollama", null, null, false, null, "prompt");
    }

    private static Exchange exchangeWith(String realIp, String forwardedFor, String servletRemoteAddr, String requestedPersona) {
        ContactRequest request = new ContactRequest();
        request.setName("Visitor Name");
        request.setContact("visitor@example.com");
        request.setContactType("email");

        HttpServletRequest servletRequest = mock(HttpServletRequest.class);
        when(servletRequest.getRemoteAddr()).thenReturn(servletRemoteAddr);

        Message message = mock(Message.class);
        when(message.getHeader("X-Real-IP", String.class)).thenReturn(realIp);
        when(message.getHeader("X-Forwarded-For", String.class)).thenReturn(forwardedFor);
        when(message.getHeader("CamelHttpServletRequest", HttpServletRequest.class)).thenReturn(servletRequest);
        when(message.getHeader("persona", String.class)).thenReturn(requestedPersona);
        when(message.getBody(ContactRequest.class)).thenReturn(request);

        Exchange exchange = mock(Exchange.class);
        when(exchange.getIn()).thenReturn(message);
        return exchange;
    }
}
