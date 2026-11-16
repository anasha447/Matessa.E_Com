package com.matessa.order.repository;

import com.matessa.order.entity.OutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OutboxRepository extends JpaRepository<OutboxEvent, Long> {

    /**
     * Claims up to {@code limit} unpublished rows for this transaction. SKIP LOCKED: rows another
     * replica is already publishing are skipped instead of waited on or published twice.
     */
    @org.springframework.data.jpa.repository.Query(value = "select * from outbox where published_at is null "
            + "order by id limit :limit for update skip locked", nativeQuery = true)
    List<OutboxEvent> claimUnpublished(@org.springframework.data.repository.query.Param("limit") int limit);
}
