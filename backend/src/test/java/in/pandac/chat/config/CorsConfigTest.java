package in.pandac.chat.config;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CorsConfigTest {

    @Test
    void aSingleOriginStillWorks() {
        assertThat(CorsConfig.parseOrigins("https://example.com")).containsExactly("https://example.com");
    }

    @Test
    void aCommaSeparatedListIsTrimmedAndEmptyEntriesDropped() {
        assertThat(CorsConfig.parseOrigins(" https://example.com , http://localhost:1313,, "))
                .isEqualTo(List.of("https://example.com", "http://localhost:1313"));
    }

    @Test
    void subdomainPatternsPassThrough() {
        assertThat(CorsConfig.parseOrigins("https://*.example.com")).containsExactly("https://*.example.com");
    }

    @Test
    void aBareWildcardAndAnEmptyValueAreRejected() {
        assertThatThrownBy(() -> CorsConfig.parseOrigins("*")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CorsConfig.parseOrigins("https://a.test, *")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CorsConfig.parseOrigins(" , ")).isInstanceOf(IllegalArgumentException.class);
    }
}
