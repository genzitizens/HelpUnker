package com.helpunker.helprequest.service.mapper;

import com.helpunker.helprequest.dto.response.HelpRequestResponse;
import com.helpunker.helprequest.dto.response.RequestPhotoResponse;
import com.helpunker.helprequest.domain.HelpRequest;
import com.helpunker.helprequest.domain.RequestPhoto;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class HelpRequestMapper {

    public HelpRequestResponse toResponse(HelpRequest request) {
        List<RequestPhotoResponse> photos = request.getPhotos() == null
                ? List.of()
                : request.getPhotos().stream().map(this::toPhotoResponse).toList();
        return new HelpRequestResponse(
                request.getId(),
                request.getTitle(),
                request.getDetails(),
                request.getStatus(),
                request.getCategory(),
                request.getLocationLat(),
                request.getLocationLng(),
                request.getAddress(),
                request.getElderly().getId(),
                request.getCreatedAt(),
                request.getUpdatedAt(),
                photos);
    }

    private RequestPhotoResponse toPhotoResponse(RequestPhoto photo) {
        UUID photoId = photo.getId();
        return new RequestPhotoResponse(photoId, photo.getUrl(), photo.getContentType());
    }
}
