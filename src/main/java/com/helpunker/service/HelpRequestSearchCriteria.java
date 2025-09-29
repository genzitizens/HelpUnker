package com.helpunker.service;

import com.helpunker.domain.model.RequestStatus;
import java.util.UUID;

public record HelpRequestSearchCriteria(
        RequestStatus status, UUID elderlyId, Double latitude, Double longitude, Double radiusKm) {
}
