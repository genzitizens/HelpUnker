package com.helpunker.domain.repository;

import com.helpunker.domain.model.DeviceToken;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeviceTokenRepository extends JpaRepository<DeviceToken, UUID> {

    List<DeviceToken> findByUserId(UUID userId);
}
