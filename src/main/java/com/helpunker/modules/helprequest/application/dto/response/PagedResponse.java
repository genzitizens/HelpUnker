package com.helpunker.modules.helprequest.application.dto.response;

import java.util.List;

public record PagedResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages) {
}
