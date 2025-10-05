package com.helpunker.helprequest.controller;

import com.helpunker.common.exception.BusinessRuleException;
import com.helpunker.helprequest.dto.request.CreateHelpRequestRequest;
import com.helpunker.helprequest.dto.response.HelpRequestResponse;
import com.helpunker.helprequest.dto.response.PagedResponse;
import com.helpunker.helprequest.service.HelpRequestSearchCriteria;
import com.helpunker.helprequest.service.HelpRequestService;
import com.helpunker.helprequest.service.command.CreateHelpRequestCommand;
import com.helpunker.helprequest.domain.RequestStatus;
import com.helpunker.helprequest.sse.BoardEventPublisher;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "Help Requests", description = "Operations related to managing help requests")
public class HelpRequestController {

    private final HelpRequestService requestService;
    private final BoardEventPublisher eventPublisher;

    public HelpRequestController(
            HelpRequestService requestService, BoardEventPublisher eventPublisher) {
        this.requestService = requestService;
        this.eventPublisher = eventPublisher;
    }

    @PostMapping(value = "/requests", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Create a help request", description = "Creates a new help request for the provided user ID.")
    @ApiResponses(
            value = {
                @ApiResponse(responseCode = "201", description = "Request created successfully"),
                @ApiResponse(
                        responseCode = "400",
                        description = "Invalid payload",
                        content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE))
            })
    public ResponseEntity<HelpRequestResponse> createRequest(
            @Parameter(
                            in = ParameterIn.HEADER,
                            name = "X-User-Id",
                            required = true,
                            description = "Identifier of the elderly user creating the help request")
                    @RequestHeader("X-User-Id")
                    UUID elderlyId,
            @Valid @RequestBody CreateHelpRequestRequest requestBody) {
        CreateHelpRequestCommand command = toCommand(elderlyId, requestBody);
        HelpRequestResponse response = requestService.createRequest(command);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(response.id())
                .toUri();
        return ResponseEntity.created(location).body(response);
    }

    @GetMapping(value = "/requests", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "List help requests",
            description = "Retrieves a paginated list of help requests using optional filters such as status, location and elderly ID.")
    public ResponseEntity<PagedResponse<HelpRequestResponse>> listRequests(
            @Parameter(description = "Filter by current request status")
                    @RequestParam(name = "status", required = false)
                    RequestStatus status,
            @Parameter(description = "Filter requests created by a specific elderly user")
                    @RequestParam(name = "elderlyId", required = false)
                    UUID elderlyId,
            @Parameter(description = "Find requests near this coordinate pair, formatted as '<lat>,<lng>'")
                    @RequestParam(name = "near", required = false)
                    String near,
            @Parameter(description = "Radius in kilometres to use with the near parameter")
                    @RequestParam(name = "radiusKm", required = false)
                    Double radiusKm,
            @Parameter(description = "Page number to retrieve", example = "0")
                    @RequestParam(name = "page", defaultValue = "0")
                    int page,
            @Parameter(description = "Number of elements per page", example = "20")
                    @RequestParam(name = "size", defaultValue = "20")
                    int size,
            @Parameter(description = "Sort property and direction formatted as 'property,direction'", example = "createdAt,DESC")
                    @RequestParam(name = "sort", defaultValue = "createdAt,DESC")
                    String sort) {

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
    @Operation(summary = "Get request details", description = "Fetches a single help request by its identifier.")
    public ResponseEntity<HelpRequestResponse> getRequest(
            @Parameter(description = "Identifier of the help request") @PathVariable("id") UUID id) {
        HelpRequestResponse response = requestService.getRequest(id);
        return ResponseEntity.ok(response);
    }

    @PostMapping(value = "/requests/{id}/cancel")
    @Operation(summary = "Cancel a request", description = "Cancels an active help request.")
    public ResponseEntity<HelpRequestResponse> cancelRequest(
            @Parameter(description = "Identifier of the help request") @PathVariable("id") UUID id,
            @Parameter(
                            in = ParameterIn.HEADER,
                            name = "X-User-Id",
                            required = true,
                            description = "Identifier of the user performing the cancellation")
                    @RequestHeader("X-User-Id")
                    UUID actorId) {
        HelpRequestResponse response = requestService.cancelRequest(id, actorId);
        return ResponseEntity.ok(response);
    }

    @GetMapping(value = "/stream/board", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "Stream board updates", description = "Subscribes to server-sent events for overall board activity.")
    public SseEmitter streamBoard() {
        return eventPublisher.registerBoardEmitter();
    }

    @GetMapping(value = "/stream/requests/{id}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "Stream request updates", description = "Subscribes to server-sent events for a specific help request.")
    public SseEmitter streamRequest(
            @Parameter(description = "Identifier of the help request") @PathVariable("id") UUID id) {
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
