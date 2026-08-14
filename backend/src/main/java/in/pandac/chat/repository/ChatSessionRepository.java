package in.pandac.chat.repository;

import in.pandac.chat.entity.ChatSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

public interface ChatSessionRepository extends JpaRepository<ChatSession, Long> {

    /** Used on every authenticated request to verify the session is still valid. */
    Optional<ChatSession> findBySessionIdAndActiveTrue(String sessionId);

    /** Hourly cleanup: flip active → false for sessions past their expiry. */
    @Modifying
    @Transactional
    @Query("UPDATE ChatSession s SET s.active = false " +
           "WHERE s.expiresAt < :now AND s.active = true")
    int markExpiredSessions(@Param("now") LocalDateTime now);

    /** Purge session rows that have been inactive for more than 7 days. */
    @Modifying
    @Transactional
    @Query("DELETE FROM ChatSession s " +
           "WHERE s.active = false AND s.expiresAt < :cutoff")
    int deleteOldExpiredSessions(@Param("cutoff") LocalDateTime cutoff);
}
