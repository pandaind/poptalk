package in.pandac.chat.config;

import in.pandac.chat.config.McpRagConfig.Connection;
import in.pandac.chat.config.McpRagConfig.ReconnectingToolProvider;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class ReconnectingToolProviderTest {

    private static final ToolCallback[] ONE_TOOL = { org.mockito.Mockito.mock(ToolCallback.class) };

    @Test
    void offersNoToolsUntilItConnectsThenOffersThem() {
        AtomicInteger attempts = new AtomicInteger();
        ReconnectingToolProvider provider = new ReconnectingToolProvider("alice", () -> {
            if (attempts.incrementAndGet() < 3) {
                throw new IllegalStateException("not up yet");
            }
            return new Connection(() -> ONE_TOOL, () -> { });
        });

        assertThat(provider.tryConnect()).isFalse();
        assertThat(provider.getToolCallbacks()).isEmpty();
        assertThat(provider.tryConnect()).isFalse();
        assertThat(provider.isConnected()).isFalse();

        assertThat(provider.tryConnect()).isTrue();
        assertThat(provider.isConnected()).isTrue();
        assertThat(provider.getToolCallbacks()).containsExactly(ONE_TOOL);
        assertThat(attempts).hasValue(3);
    }

    @Test
    void doesNotReconnectWhileAlreadyConnected() {
        AtomicInteger attempts = new AtomicInteger();
        ReconnectingToolProvider provider = new ReconnectingToolProvider("alice", () -> {
            attempts.incrementAndGet();
            return new Connection(() -> ONE_TOOL, () -> { });
        });

        provider.tryConnect();
        provider.tryConnect();

        assertThat(attempts).hasValue(1);
    }

    @Test
    void aFailureListingToolsDropsTheConnectionSoItCanBeReplaced() {
        AtomicInteger connects = new AtomicInteger();
        AtomicInteger closed = new AtomicInteger();
        ToolCallbackProvider broken = () -> { throw new IllegalStateException("session expired"); };
        ReconnectingToolProvider provider = new ReconnectingToolProvider("alice", () -> {
            int n = connects.incrementAndGet();
            return n == 1
                    ? new Connection(broken, closed::incrementAndGet)
                    : new Connection(() -> ONE_TOOL, () -> { });
        });
        provider.tryConnect();

        // The chat request must not fail because the knowledge service restarted.
        assertThat(provider.getToolCallbacks()).isEmpty();
        assertThat(provider.isConnected()).isFalse();
        assertThat(closed).hasValue(1);

        assertThat(provider.tryConnect()).isTrue();
        assertThat(provider.getToolCallbacks()).containsExactly(ONE_TOOL);
    }
}
