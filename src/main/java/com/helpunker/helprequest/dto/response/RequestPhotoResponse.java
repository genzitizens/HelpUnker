package com.helpunker.helprequest.dto.response;

import java.util.UUID;

public record RequestPhotoResponse(UUID id, String url, String contentType) {
}
