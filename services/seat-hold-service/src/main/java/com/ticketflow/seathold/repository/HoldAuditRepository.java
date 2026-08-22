package com.ticketflow.seathold.repository;

import com.ticketflow.seathold.entity.HoldAudit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface HoldAuditRepository extends JpaRepository<HoldAudit, UUID> {

    List<HoldAudit> findByCustomerId(UUID customerId);

    List<HoldAudit> findByShowId(UUID showId);
}
