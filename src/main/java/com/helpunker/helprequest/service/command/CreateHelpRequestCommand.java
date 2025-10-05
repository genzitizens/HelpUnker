package com.helpunker.helprequest.service.command;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record CreateHelpRequestCommand(
        UUID elderlyId,
        String title,
        String details,
        String category,
        BigDecimal locationLat,
        BigDecimal locationLng,
        String address,
        List<Photo> photos) {

    public record Photo(String url, String contentType) {
    }
}
