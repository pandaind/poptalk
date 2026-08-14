package in.pandac.chat.repository;

import in.pandac.chat.entity.ChatMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    /** Full conversation history for a session, oldest first. */
    List<ChatMessage> findBySessionIdOrderBySentAtAsc(String sessionId);

    /**
     * Oldest unread admin reply — used by GET /api/v1/chat/reply.
     * After fetching, the route marks it read=true (one-shot delivery).
     */
    Optional<ChatMessage> findFirstBySessionIdAndDirectionAndReadFalseOrderBySentAtAsc(
            String sessionId, String direction);

    /** Cascade delete messages when their parent session is purged. */
    @Modifying
    @Transactional
    @Query("DELETE FROM ChatMessage m WHERE m.sessionId IN " +
           "(SELECT s.sessionId FROM ChatSession s " +
           " WHERE s.active = false AND s.expiresAt < :cutoff)")
    int deleteMessagesForExpiredSessions(@Param("cutoff") LocalDateTime cutoff);
}
