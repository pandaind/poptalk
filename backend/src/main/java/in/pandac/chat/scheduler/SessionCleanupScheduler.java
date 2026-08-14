package in.pandac.chat.scheduler;

import in.pandac.chat.repository.ChatMessageRepository;
import in.pandac.chat.repository.ChatSessionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
public class SessionCleanupScheduler {

    private static final Logger log = LoggerFactory.getLogger(SessionCleanupScheduler.class);

    private final ChatSessionRepository sessionRepository;
    private final ChatMessageRepository messageRepository;

    public SessionCleanupScheduler(
            ChatSessionRepository sessionRepository,
            ChatMessageRepository messageRepository) {
        this.sessionRepository = sessionRepository;
        this.messageRepository = messageRepository;
    }

    /**
     * Runs every hour at minute 0.
     * 1. Marks sessions past their expiresAt date as active = false.
     * 2. Purges old sessions (> 7 days expired) and their messages from H2.
     */
    @Scheduled(cron = "0 0 * * * *")
    public void cleanupExpiredSessions() {
        log.info("Starting scheduled session cleanup...");
        LocalDateTime now = LocalDateTime.now();

        // 1. Mark expired
        int expiredCount = sessionRepository.markExpiredSessions(now);
        if (expiredCount > 0) {
            log.info("Marked {} sessions as inactive (expired).", expiredCount);
        }

        // 2. Purge old sessions (> 7 days inactive)
        LocalDateTime cutoff = now.minusDays(7);
        
        // Cascade delete: first messages, then sessions
        int deletedMessages = messageRepository.deleteMessagesForExpiredSessions(cutoff);
        int deletedSessions = sessionRepository.deleteOldExpiredSessions(cutoff);
        
        if (deletedSessions > 0 || deletedMessages > 0) {
            log.info("Purged {} old sessions and {} old messages from database.", 
                     deletedSessions, deletedMessages);
        }
    }
}
