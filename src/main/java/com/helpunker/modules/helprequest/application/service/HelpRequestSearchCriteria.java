package com.helpunker.modules.helprequest.application.service;

import com.helpunker.modules.helprequest.domain.model.RequestStatus;
import java.util.UUID;

public record HelpRequestSearchCriteria(
        RequestStatus status, UUID elderlyId, Double latitude, Double longitude, Double radiusKm) {
}
