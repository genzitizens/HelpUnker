package com.helpunker.modules.helprequest.application.dto.response;

import com.helpunker.modules.helprequest.domain.model.RequestStatus;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record HelpRequestResponse(
        UUID id,
        String title,
        String details,
        RequestStatus status,
        String category,
        BigDecimal locationLat,
        BigDecimal locationLng,
        String address,
        UUID elderlyId,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        List<RequestPhotoResponse> photos) {
}
