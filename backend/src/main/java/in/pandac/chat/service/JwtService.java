package in.pandac.chat.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Service
public class JwtService {

    private final SecretKey key;
    private final long expiryMillis;

    public JwtService(
            @Value("${app.jwt.secret}") String secret,
            @Value("${app.jwt.expiry-hours}") long expiryHours) {
        // Fallback for missing secret during local dev to prevent startup crash
        if (secret == null || secret.isBlank() || secret.equals("${JWT_SECRET}")) {
            secret = "ThisIsADefaultSecretForLocalDevOnlyChangeInProd123!";
        }
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expiryMillis = expiryHours * 3600 * 1000;
    }

    public String generateToken(String sessionId, String contact) {
        long nowMillis = System.currentTimeMillis();
        Date now = new Date(nowMillis);
        Date expiry = new Date(nowMillis + expiryMillis);

        return Jwts.builder()
                .subject(sessionId)
                .claim("contact", contact)
                .issuedAt(now)
                .expiration(expiry)
                .signWith(key)
                .compact();
    }

    /**
     * Validates the token and returns the Claims.
     * Throws JwtException if the token is invalid or expired.
     */
    public Claims validateToken(String token) throws JwtException {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
