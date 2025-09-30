package com.helpunker.modules.helprequest.application.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;

public record CreateHelpRequestRequest(
        @NotBlank @Size(max = 160) String title,
        @NotBlank String details,
        @Size(max = 64) String category,
        @DecimalMin(value = "-90.0", message = "Latitude must be >= -90")
                @DecimalMax(value = "90.0", message = "Latitude must be <= 90")
                BigDecimal locationLat,
        @DecimalMin(value = "-180.0", message = "Longitude must be >= -180")
                @DecimalMax(value = "180.0", message = "Longitude must be <= 180")
                BigDecimal locationLng,
        @Size(max = 255) String address,
        @Valid List<PhotoPayload> photos) {

    public record PhotoPayload(@NotBlank String url, @Size(max = 100) String contentType) {
    }
}
