package in.pandac.chat.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import java.util.List;

/**
 * Restricts cross-origin access to the configured origins only.
 *
 * <p>{@code app.cors.allowed-origin} takes one origin or a comma-separated list, so a production
 * site and a local dev server can share one backend, for example
 * {@code https://example.com,http://localhost:1313}. Subdomain patterns such as
 * {@code https://*.example.com} are accepted too. A bare {@code *} is rejected: the widget
 * sends no credentials, but an open backend would still let any page spend your LLM quota.
 */
@Configuration
public class CorsConfig {

    @Value("${app.cors.allowed-origin}")
    private String allowedOrigin;

    /** Splits the configured value into trimmed, non-empty origins; refuses a bare wildcard. */
    static List<String> parseOrigins(String configured) {
        List<String> origins = java.util.Arrays.stream(configured.split(","))
                .map(String::trim)
                .filter(o -> !o.isEmpty())
                .toList();
        if (origins.isEmpty()) {
            throw new IllegalArgumentException("app.cors.allowed-origin must name at least one origin");
        }
        if (origins.contains("*")) {
            throw new IllegalArgumentException(
                    "app.cors.allowed-origin must not be '*'; list your site's origin(s) explicitly");
        }
        return origins;
    }

    @Bean
    public CorsFilter corsFilter() {
        CorsConfiguration config = new CorsConfiguration();
        // Patterns accept exact origins as well as https://*.example.com.
        config.setAllowedOriginPatterns(parseOrigins(allowedOrigin));
        config.setAllowedMethods(List.of("GET", "POST", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept"));
        config.setAllowCredentials(false);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return new CorsFilter(source);
    }
}
