package com.ticketflow.venueevent.repository;

import com.ticketflow.venueevent.entity.ShowPricing;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ShowPricingRepository extends JpaRepository<ShowPricing, UUID> {

    List<ShowPricing> findByShowId(UUID showId);

    void deleteByShowId(UUID showId);
}
