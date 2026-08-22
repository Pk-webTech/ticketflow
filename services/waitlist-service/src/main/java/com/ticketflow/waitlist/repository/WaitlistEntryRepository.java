package com.ticketflow.waitlist.repository;

import com.ticketflow.waitlist.entity.WaitlistEntry;
import com.ticketflow.waitlist.entity.WaitlistStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WaitlistEntryRepository extends JpaRepository<WaitlistEntry, UUID> {

    /**
     * "Who is next in this category's queue?"
     *
     * <p>{@code PESSIMISTIC_WRITE} + {@code SKIP LOCKED} is what makes
     * auto-assignment safe when several cancellations for the same show land
     * on different service instances at the same moment. Each consumer locks
     * the head of the queue; a competing consumer skips the locked row and
     * takes the next one, so two customers can never be offered the same
     * seat, and no consumer blocks waiting on another.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@jakarta.persistence.QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2"))
    @Query("""
           SELECT w FROM WaitlistEntry w
           WHERE w.showId = :showId
             AND w.categoryId = :categoryId
             AND w.status = 'ACTIVE'
           ORDER BY w.createdAt ASC, w.id ASC
           """)
    List<WaitlistEntry> lockNextInQueue(@Param("showId") UUID showId,
                                        @Param("categoryId") UUID categoryId,
                                        Pageable pageable);

    List<WaitlistEntry> findByCustomerIdOrderByCreatedAtDesc(UUID customerId);

    Optional<WaitlistEntry> findByShowIdAndCategoryIdAndCustomerIdAndStatusIn(
            UUID showId, UUID categoryId, UUID customerId, List<WaitlistStatus> statuses);

    long countByShowIdAndCategoryIdAndStatus(UUID showId, UUID categoryId, WaitlistStatus status);

    @Query("""
           SELECT COUNT(w) FROM WaitlistEntry w
           WHERE w.showId = :showId AND w.categoryId = :categoryId
             AND w.status = 'ACTIVE' AND w.createdAt < :createdAt
           """)
    long countAhead(@Param("showId") UUID showId,
                    @Param("categoryId") UUID categoryId,
                    @Param("createdAt") java.time.Instant createdAt);
}
