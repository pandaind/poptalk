package in.pandac.chat.processor;

import in.pandac.chat.exception.UnauthorizedException;
import in.pandac.chat.service.JwtService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.springframework.stereotype.Component;

@Component
public class JwtValidationProcessor implements Processor {

    private final JwtService jwtService;

    public JwtValidationProcessor(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    public void process(Exchange exchange) {
        String authHeader = exchange.getIn().getHeader("Authorization", String.class);
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new UnauthorizedException("Missing or invalid Authorization header");
        }

        String token = authHeader.substring(7);
        try {
            Claims claims = jwtService.validateToken(token);
            // Extract the sessionId (subject) and put it in a header for subsequent routing
            String sessionId = claims.getSubject();
            exchange.getIn().setHeader("sessionId", sessionId);
            
            // Also store contact info in case it's needed
            exchange.getIn().setHeader("contact", claims.get("contact", String.class));
        } catch (JwtException e) {
            throw new UnauthorizedException("Invalid or expired token");
        }
    }
}
