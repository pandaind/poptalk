package in.pandac.chat.service;

import in.pandac.chat.exception.TooManyRequestsException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Two independent in-memory rate limiters:
 *  1. Per-IP sliding window (registration endpoint)
 *  2. Per-sessionId sliding window (message endpoint — prevents token abuse)
 */
@Service
public class RateLimitService {

    private final int maxRequestsPerMinute;
    private final int maxMessagesPerSessionPerHour;

    // Buckets: key → TokenBucket
    private final Map<String, TokenBucket> ipBuckets      = new ConcurrentHashMap<>();
    private final Map<String, TokenBucket> sessionBuckets = new ConcurrentHashMap<>();

    public RateLimitService(
            @Value("${app.rate-limit.requests-per-minute:5}") int maxRequestsPerMinute,
            @Value("${app.rate-limit.messages-per-session-per-hour:20}") int maxMessagesPerSessionPerHour) {
        this.maxRequestsPerMinute = maxRequestsPerMinute;
        this.maxMessagesPerSessionPerHour = maxMessagesPerSessionPerHour;
    }

    /**
     * Per-IP check for the registration endpoint (sliding 1-minute window).
     */
    public void checkAndConsume(String ip) {
        if (ip == null || ip.isBlank()) return;
        TokenBucket bucket = ipBuckets.computeIfAbsent(ip, k -> new TokenBucket(maxRequestsPerMinute, 60));
        bucket.refill();
        if (!bucket.consume()) {
            throw new TooManyRequestsException("Rate limit exceeded. Please try again later.");
        }
    }

    /**
     * Per-session check for the message endpoint (sliding 1-hour window).
     * Prevents DDoS / abuse even if a token has been stolen.
     */
    public void checkMessageLimit(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) return;
        TokenBucket bucket = sessionBuckets.computeIfAbsent(
                sessionId, k -> new TokenBucket(maxMessagesPerSessionPerHour, 3600));
        bucket.refill();
        if (!bucket.consume()) {
            throw new TooManyRequestsException(
                "Message limit reached for this session. Please try again later.");
        }
    }

    // ── Token Bucket ─────────────────────────────────────────────────────────

    private static class TokenBucket {
        private final int maxTokens;
        private final long windowSeconds;
        private final AtomicInteger tokens;
        private volatile long lastRefill;

        TokenBucket(int maxTokens, long windowSeconds) {
            this.maxTokens     = maxTokens;
            this.windowSeconds = windowSeconds;
            this.tokens        = new AtomicInteger(maxTokens);
            this.lastRefill    = Instant.now().getEpochSecond();
        }

        void refill() {
            long now = Instant.now().getEpochSecond();
            if (now - lastRefill >= windowSeconds) {
                tokens.set(maxTokens);
                lastRefill = now;
            }
        }

        boolean consume() {
            int current = tokens.get();
            return current > 0 && tokens.compareAndSet(current, current - 1);
        }
    }
}
