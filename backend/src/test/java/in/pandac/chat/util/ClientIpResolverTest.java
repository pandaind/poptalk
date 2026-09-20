package in.pandac.chat.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ClientIpResolverTest {

    @Test
    void prefersXRealIpOverEverythingElse() {
        assertThat(ClientIpResolver.resolve("1.1.1.1", "2.2.2.2", "3.3.3.3")).isEqualTo("1.1.1.1");
    }

    @Test
    void fallsBackToForwardedForWhenRealIpIsAbsent() {
        assertThat(ClientIpResolver.resolve(null, "2.2.2.2", "3.3.3.3")).isEqualTo("2.2.2.2");
        assertThat(ClientIpResolver.resolve("", "2.2.2.2", "3.3.3.3")).isEqualTo("2.2.2.2");
        assertThat(ClientIpResolver.resolve("  ", "2.2.2.2", "3.3.3.3")).isEqualTo("2.2.2.2");
    }

    @Test
    void fallsBackToTheServletRemoteAddressWhenNoProxyHeadersArePresent() {
        assertThat(ClientIpResolver.resolve(null, null, "3.3.3.3")).isEqualTo("3.3.3.3");
        assertThat(ClientIpResolver.resolve("", "", "3.3.3.3")).isEqualTo("3.3.3.3");
    }

    @Test
    void returnsNullWhenNothingIsAvailable() {
        assertThat(ClientIpResolver.resolve(null, null, null)).isNull();
    }
}
