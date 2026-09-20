package in.pandac.chat.service;

import in.pandac.chat.exception.TooManyRequestsException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RateLimitServiceTest {

    private final RateLimitService service = new RateLimitService(3, 2);

    @Test
    void allowsUpToTheConfiguredLimitThenBlocks() {
        assertThatCode(() -> {
            service.checkAndConsume("1.2.3.4");
            service.checkAndConsume("1.2.3.4");
            service.checkAndConsume("1.2.3.4");
        }).doesNotThrowAnyException();

        assertThatThrownBy(() -> service.checkAndConsume("1.2.3.4"))
                .isInstanceOf(TooManyRequestsException.class);
    }

    @Test
    void tracksEachIpInItsOwnBucket() {
        service.checkAndConsume("1.1.1.1");
        service.checkAndConsume("1.1.1.1");
        service.checkAndConsume("1.1.1.1");

        assertThatCode(() -> service.checkAndConsume("2.2.2.2")).doesNotThrowAnyException();
    }

    @Test
    void aBlankOrNullIpIsANoOpRatherThanConsumingAnyBucket() {
        assertThatCode(() -> {
            service.checkAndConsume(null);
            service.checkAndConsume("");
            service.checkAndConsume("  ");
        }).doesNotThrowAnyException();
    }

    @Test
    void messageLimitIsIndependentPerSessionAndFromTheIpLimit() {
        service.checkMessageLimit("session-1");
        service.checkMessageLimit("session-1");

        assertThatThrownBy(() -> service.checkMessageLimit("session-1"))
                .isInstanceOf(TooManyRequestsException.class);
        // A different session's bucket is untouched.
        assertThatCode(() -> service.checkMessageLimit("session-2")).doesNotThrowAnyException();
    }
}
