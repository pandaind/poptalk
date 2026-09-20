package in.pandac.chat.processor;

import in.pandac.chat.exception.UnauthorizedException;
import in.pandac.chat.service.JwtService;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JwtValidationProcessorTest {

    private final JwtService jwtService = new JwtService("d".repeat(64), 1);
    private final JwtValidationProcessor processor = new JwtValidationProcessor(jwtService);

    @Test
    void setsSessionIdAndContactHeadersFromAValidToken() {
        String token = jwtService.generateToken("session-1", "visitor@example.com");
        Exchange exchange = exchangeWithAuthHeader("Bearer " + token);

        processor.process(exchange);

        verify(exchange.getIn()).setHeader("sessionId", "session-1");
        verify(exchange.getIn()).setHeader("contact", "visitor@example.com");
    }

    @Test
    void rejectsAMissingAuthorizationHeader() {
        Exchange exchange = exchangeWithAuthHeader(null);

        assertThatThrownBy(() -> processor.process(exchange)).isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void rejectsAHeaderThatIsNotABearerToken() {
        Exchange exchange = exchangeWithAuthHeader("Basic dXNlcjpwYXNz");

        assertThatThrownBy(() -> processor.process(exchange)).isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void rejectsAnInvalidToken() {
        Exchange exchange = exchangeWithAuthHeader("Bearer not-a-real-token");

        assertThatThrownBy(() -> processor.process(exchange)).isInstanceOf(UnauthorizedException.class);
    }

    private static Exchange exchangeWithAuthHeader(String header) {
        Message message = mock(Message.class);
        when(message.getHeader("Authorization", String.class)).thenReturn(header);
        Exchange exchange = mock(Exchange.class);
        when(exchange.getIn()).thenReturn(message);
        return exchange;
    }
}
