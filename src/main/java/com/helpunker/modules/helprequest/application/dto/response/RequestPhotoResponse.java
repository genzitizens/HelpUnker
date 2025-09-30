package com.helpunker.modules.helprequest.application.dto.response;

import java.util.UUID;

public record RequestPhotoResponse(UUID id, String url, String contentType) {
}
