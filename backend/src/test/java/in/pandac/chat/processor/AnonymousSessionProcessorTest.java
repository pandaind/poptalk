package in.pandac.chat.processor;

import in.pandac.chat.dto.ContactResponse;
import in.pandac.chat.entity.ChatSession;
import in.pandac.chat.exception.TooManyRequestsException;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AnonymousSessionProcessorTest {

    private final ChatSessionRepository sessionRepository = mock(ChatSessionRepository.class);
    private final JwtService jwtService = mock(JwtService.class);
    private final RateLimitService rateLimitService = mock(RateLimitService.class);
    private final PersonaService personaService = mock(PersonaService.class);
    private final AnonymousSessionProcessor processor =
            new AnonymousSessionProcessor(sessionRepository, jwtService, rateLimitService, personaService);

    @Test
    void createsASessionWithNeutralPlaceholdersAndNoVisitorDetails() {
        when(personaService.getPersona("alice")).thenReturn(persona("alice"));
        when(jwtService.generateToken(anyString(), eq(""))).thenReturn("token-abc");
        Exchange exchange = exchange("1.2.3.4", "alice");

        processor.process(exchange);

        ArgumentCaptor<ChatSession> saved = ArgumentCaptor.forClass(ChatSession.class);
        verify(sessionRepository).save(saved.capture());
        ChatSession s = saved.getValue();
        assertThat(s.getName()).isEqualTo("Anonymous");
        assertThat(s.getContact()).isEmpty();
        assertThat(s.getContactType()).isEqualTo("none").hasSizeLessThanOrEqualTo(10);
        assertThat(s.getPersonaId()).isEqualTo("alice");
        assertThat(s.isActive()).isTrue();
        assertThat(s.getSessionId()).hasSize(36); // a full UUID, not an enumerable prefix
        assertThat(s.getExpiresAt()).isAfter(s.getCreatedAt());
    }

    @Test
    void returnsATokenWhoseSubjectIsTheNewSessionAndCarriesNoContact() {
        when(personaService.getPersona(any())).thenReturn(persona("default"));
        when(jwtService.generateToken(anyString(), eq(""))).thenReturn("token-abc");
        Exchange exchange = exchange("1.2.3.4", null);

        processor.process(exchange);

        ArgumentCaptor<Object> body = ArgumentCaptor.forClass(Object.class);
        verify(exchange.getIn()).setBody(body.capture());
        ContactResponse response = (ContactResponse) body.getValue();
        assertThat(response.getToken()).isEqualTo("token-abc");

        ArgumentCaptor<String> sid = ArgumentCaptor.forClass(String.class);
        verify(jwtService).generateToken(sid.capture(), eq(""));
        assertThat(response.getSessionId()).isEqualTo(sid.getValue());
    }

    @Test
    void isRateLimitedByClientIpBeforeAnythingIsCreated() {
        doThrow(new TooManyRequestsException("slow down")).when(rateLimitService).checkAndConsume("9.9.9.9");
        Exchange exchange = exchange("9.9.9.9", null);

        assertThatThrownBy(() -> processor.process(exchange)).isInstanceOf(TooManyRequestsException.class);

        verify(sessionRepository, never()).save(any());
        verify(jwtService, never()).generateToken(anyString(), anyString());
    }

    private static Persona persona(String id) {
        return new Persona(id, "Owner", "Chat", "O", null, "ollama", null, null, false, null, "prompt");
    }

    private static Exchange exchange(String realIp, String requestedPersona) {
        HttpServletRequest servletRequest = mock(HttpServletRequest.class);
        when(servletRequest.getRemoteAddr()).thenReturn("203.0.113.9");

        Message message = mock(Message.class);
        when(message.getHeader("X-Real-IP", String.class)).thenReturn(realIp);
        when(message.getHeader("X-Forwarded-For", String.class)).thenReturn(null);
        when(message.getHeader("CamelHttpServletRequest", HttpServletRequest.class)).thenReturn(servletRequest);
        when(message.getHeader("persona", String.class)).thenReturn(requestedPersona);

        Exchange exchange = mock(Exchange.class);
        when(exchange.getIn()).thenReturn(message);
        return exchange;
    }
}
