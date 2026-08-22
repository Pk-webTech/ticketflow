package com.ticketflow.booking.repository;

import com.ticketflow.booking.entity.BookingSeat;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface BookingSeatRepository extends JpaRepository<BookingSeat, UUID> {

    @Query("SELECT bs.seatId FROM BookingSeat bs WHERE bs.showId = :showId AND bs.active = true")
    List<UUID> findActiveSeatIdsForShow(@Param("showId") UUID showId);

    @Query("""
           SELECT COUNT(bs) FROM BookingSeat bs
           WHERE bs.showId = :showId AND bs.categoryId = :categoryId AND bs.active = true
           """)
    long countActiveSeatsInCategory(@Param("showId") UUID showId, @Param("categoryId") UUID categoryId);

    List<BookingSeat> findByShowIdAndActiveTrue(UUID showId);
}
