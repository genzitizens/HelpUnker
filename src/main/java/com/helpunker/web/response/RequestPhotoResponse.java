package com.helpunker.web.response;

import java.util.UUID;

public record RequestPhotoResponse(UUID id, String url, String contentType) {
}
