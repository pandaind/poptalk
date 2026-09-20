package in.pandac.chat.service;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

class ChatModeServiceTest {

    private final ChatModeService service = new ChatModeService();

    @Test
    void aiModeIsCaseInsensitive() {
        ReflectionTestUtils.setField(service, "mode", "ai");
        assertThat(service.isAiMode()).isTrue();
        assertThat(service.getMode()).isEqualTo("AI");
    }

    @Test
    void manualModeIsNotAiMode() {
        ReflectionTestUtils.setField(service, "mode", "MANUAL");
        assertThat(service.isAiMode()).isFalse();
        assertThat(service.getMode()).isEqualTo("MANUAL");
    }
}
