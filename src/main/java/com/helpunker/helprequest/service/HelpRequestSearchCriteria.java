package com.helpunker.helprequest.service;

import com.helpunker.helprequest.entity.RequestStatus;
import java.util.UUID;

public record HelpRequestSearchCriteria(
        RequestStatus status, UUID elderlyId, Double latitude, Double longitude, Double radiusKm) {
}
