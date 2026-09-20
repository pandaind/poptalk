package in.pandac.chat.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

    private final JwtService jwtService = new JwtService("a".repeat(64), 1);

    @Test
    void generatesATokenThatValidatesBackToTheSameSessionAndContact() {
        String token = jwtService.generateToken("session-123", "visitor@example.com");

        Claims claims = jwtService.validateToken(token);

        assertThat(claims.getSubject()).isEqualTo("session-123");
        assertThat(claims.get("contact", String.class)).isEqualTo("visitor@example.com");
    }

    @Test
    void rejectsATokenSignedWithADifferentSecret() {
        JwtService other = new JwtService("b".repeat(64), 1);
        String token = other.generateToken("session-123", "visitor@example.com");

        assertThatThrownBy(() -> jwtService.validateToken(token)).isInstanceOf(JwtException.class);
    }

    @Test
    void rejectsAnObviouslyMalformedToken() {
        assertThatThrownBy(() -> jwtService.validateToken("not-a-jwt")).isInstanceOf(JwtException.class);
    }

    @Test
    void rejectsAnExpiredToken() throws InterruptedException {
        // expiry-hours accepts only whole hours, so exercise expiry via a token
        // signed with an already-past expiry instead of waiting an hour.
        JwtService alreadyExpired = new JwtService("c".repeat(64), 0);
        String token = alreadyExpired.generateToken("session-123", "visitor@example.com");

        // 0 hours means expiry == issuedAt; give the clock a moment to move past it.
        Thread.sleep(5);

        assertThatThrownBy(() -> alreadyExpired.validateToken(token)).isInstanceOf(JwtException.class);
    }

    @Test
    void refusesToConstructWithABlankSecret() {
        assertThatThrownBy(() -> new JwtService("", 1)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void refusesToConstructWithTheUnresolvedPlaceholderSecret() {
        assertThatThrownBy(() -> new JwtService("${JWT_SECRET}", 1)).isInstanceOf(IllegalStateException.class);
    }
}
