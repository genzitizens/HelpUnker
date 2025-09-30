package com.helpunker.modules.outbox.domain.repository;

import com.helpunker.modules.outbox.domain.model.OutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {
}
