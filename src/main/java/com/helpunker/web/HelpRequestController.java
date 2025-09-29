package com.helpunker.web;

import com.helpunker.domain.model.RequestStatus;
import com.helpunker.service.CreateHelpRequestCommand;
import com.helpunker.service.HelpRequestSearchCriteria;
import com.helpunker.service.HelpRequestService;
import com.helpunker.service.exception.BusinessRuleException;
import com.helpunker.service.sse.BoardEventPublisher;
import com.helpunker.web.request.CreateHelpRequestRequest;
import com.helpunker.web.response.HelpRequestResponse;
import com.helpunker.web.response.PagedResponse;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping
@Validated
public class HelpRequestController {

    private final HelpRequestService requestService;
    private final BoardEventPublisher eventPublisher;

    public HelpRequestController(
            HelpRequestService requestService, BoardEventPublisher eventPublisher) {
        this.requestService = requestService;
        this.eventPublisher = eventPublisher;
    }

    @PostMapping(value = "/requests", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<HelpRequestResponse> createRequest(
            @RequestHeader("X-User-Id") UUID elderlyId, @Valid @RequestBody CreateHelpRequestRequest requestBody) {
        CreateHelpRequestCommand command = toCommand(elderlyId, requestBody);
        HelpRequestResponse response = requestService.createRequest(command);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(response.id())
                .toUri();
        return ResponseEntity.created(location).body(response);
    }

    @GetMapping(value = "/requests", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<PagedResponse<HelpRequestResponse>> listRequests(
            @RequestParam(name = "status", required = false) RequestStatus status,
            @RequestParam(name = "elderlyId", required = false) UUID elderlyId,
            @RequestParam(name = "near", required = false) String near,
            @RequestParam(name = "radiusKm", required = false) Double radiusKm,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size,
            @RequestParam(name = "sort", defaultValue = "createdAt,DESC") String sort) {

        Pageable pageable = PageRequest.of(page, size, toSort(sort));
        Double latitude = null;
        Double longitude = null;
        if (StringUtils.hasText(near)) {
            String[] parts = near.split(",");
            if (parts.length != 2) {
                throw new BusinessRuleException("near parameter must be formatted as '<lat>,<lng>'");
            }
            try {
                latitude = Double.parseDouble(parts[0]);
                longitude = Double.parseDouble(parts[1]);
            } catch (NumberFormatException ex) {
                throw new BusinessRuleException("near parameter must contain valid decimal coordinates");
            }
        }

        Page<HelpRequestResponse> pageResult = requestService.searchRequests(
                new HelpRequestSearchCriteria(status, elderlyId, latitude, longitude, radiusKm), pageable);
        List<HelpRequestResponse> content = pageResult.getContent();
        PagedResponse<HelpRequestResponse> response = new PagedResponse<>(
                content, pageResult.getNumber(), pageResult.getSize(), pageResult.getTotalElements(), pageResult.getTotalPages());
        return ResponseEntity.ok(response);
    }

    @GetMapping(value = "/requests/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<HelpRequestResponse> getRequest(@PathVariable("id") UUID id) {
        HelpRequestResponse response = requestService.getRequest(id);
        return ResponseEntity.ok(response);
    }

    @PostMapping(value = "/requests/{id}/cancel")
    public ResponseEntity<HelpRequestResponse> cancelRequest(
            @PathVariable("id") UUID id, @RequestHeader("X-User-Id") UUID actorId) {
        HelpRequestResponse response = requestService.cancelRequest(id, actorId);
        return ResponseEntity.ok(response);
    }

    @GetMapping(value = "/stream/board", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamBoard() {
        return eventPublisher.registerBoardEmitter();
    }

    @GetMapping(value = "/stream/requests/{id}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamRequest(@PathVariable("id") UUID id) {
        return eventPublisher.registerRequestEmitter(id);
    }

    private CreateHelpRequestCommand toCommand(UUID elderlyId, CreateHelpRequestRequest requestBody) {
        List<CreateHelpRequestCommand.Photo> photos = requestBody.photos() == null
                ? List.of()
                : requestBody.photos().stream()
                        .map(photo -> new CreateHelpRequestCommand.Photo(photo.url(), photo.contentType()))
                        .toList();
        return new CreateHelpRequestCommand(
                elderlyId,
                requestBody.title(),
                requestBody.details(),
                requestBody.category(),
                requestBody.locationLat(),
                requestBody.locationLng(),
                requestBody.address(),
                photos);
    }

    private Sort toSort(String sort) {
        String[] tokens = sort.split(",");
        String property = tokens[0];
        Sort.Direction direction = tokens.length > 1 ? Sort.Direction.fromString(tokens[1]) : Sort.Direction.ASC;
        return Sort.by(direction, toEntityField(property));
    }

    private String toEntityField(String property) {
        return switch (property) {
            case "createdAt" -> "createdAt";
            case "updatedAt" -> "updatedAt";
            case "status" -> "status";
            default -> "createdAt";
        };
    }
}
