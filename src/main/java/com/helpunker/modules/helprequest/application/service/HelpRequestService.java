package com.helpunker.modules.helprequest.application.service;

import com.helpunker.common.exception.BusinessRuleException;
import com.helpunker.common.exception.ResourceNotFoundException;
import com.helpunker.modules.helprequest.application.dto.response.HelpRequestResponse;
import com.helpunker.modules.helprequest.application.service.command.CreateHelpRequestCommand;
import com.helpunker.modules.helprequest.application.service.mapper.HelpRequestMapper;
import com.helpunker.modules.helprequest.domain.model.HelpRequest;
import com.helpunker.modules.helprequest.domain.model.RequestPhoto;
import com.helpunker.modules.helprequest.domain.model.RequestStatus;
import com.helpunker.modules.helprequest.domain.repository.HelpRequestRepository;
import com.helpunker.modules.helprequest.domain.repository.HelpRequestSpecifications;
import com.helpunker.modules.helprequest.infrastructure.sse.BoardEventPublisher;
import com.helpunker.modules.helprequest.infrastructure.sse.RequestEvent;
import com.helpunker.modules.helprequest.infrastructure.sse.RequestEventType;
import com.helpunker.modules.user.domain.model.User;
import com.helpunker.modules.user.domain.model.UserRole;
import com.helpunker.modules.user.domain.repository.UserRepository;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class HelpRequestService {

    private final HelpRequestRepository requestRepository;
    private final UserRepository userRepository;
    private final HelpRequestMapper mapper;
    private final BoardEventPublisher eventPublisher;

    public HelpRequestService(
            HelpRequestRepository requestRepository,
            UserRepository userRepository,
            HelpRequestMapper mapper,
            BoardEventPublisher eventPublisher) {
        this.requestRepository = requestRepository;
        this.userRepository = userRepository;
        this.mapper = mapper;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public HelpRequestResponse createRequest(CreateHelpRequestCommand command) {
        User elderly = userRepository
                .findById(command.elderlyId())
                .orElseThrow(() -> new ResourceNotFoundException("Elderly user not found: " + command.elderlyId()));
        if (elderly.getRole() != UserRole.ELDERLY) {
            throw new BusinessRuleException("Only elderly users can create help requests");
        }

        HelpRequest request = HelpRequest.builder()
                .id(UUID.randomUUID())
                .elderly(elderly)
                .title(command.title())
                .details(command.details())
                .status(RequestStatus.OPEN)
                .category(command.category())
                .locationLat(command.locationLat())
                .locationLng(command.locationLng())
                .address(command.address())
                .build();

        List<CreateHelpRequestCommand.Photo> photos = command.photos();
        if (photos != null) {
            photos.forEach(photo -> request.addPhoto(RequestPhoto.builder()
                    .id(UUID.randomUUID())
                    .url(photo.url())
                    .contentType(photo.contentType())
                    .build()));
        }

        HelpRequest saved = requestRepository.save(request);
        HelpRequestResponse response = mapper.toResponse(saved);
        RequestEvent event = new RequestEvent(RequestEventType.REQUEST_CREATED, response);
        eventPublisher.publishBoardEvent(event);
        eventPublisher.publishRequestEvent(saved.getId(), event);
        return response;
    }

    @Transactional
    public HelpRequestResponse cancelRequest(UUID requestId, UUID actorId) {
        HelpRequest request = requestRepository
                .findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("Request not found: " + requestId));

        if (request.getStatus() == RequestStatus.COMPLETED || request.getStatus() == RequestStatus.CANCELLED) {
            throw new BusinessRuleException("Request is already finalized");
        }

        User actor = userRepository
                .findById(actorId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + actorId));

        boolean isOwner = Objects.equals(request.getElderly().getId(), actorId);
        boolean isAdmin = actor.getRole() == UserRole.ADMIN;
        if (!isOwner && !isAdmin) {
            throw new BusinessRuleException("Only the owner or an admin can cancel this request");
        }

        request.setStatus(RequestStatus.CANCELLED);
        HelpRequest saved = requestRepository.save(request);
        HelpRequestResponse response = mapper.toResponse(saved);
        RequestEvent event = new RequestEvent(RequestEventType.REQUEST_CANCELLED, response);
        eventPublisher.publishBoardEvent(event);
        eventPublisher.publishRequestEvent(saved.getId(), event);
        return response;
    }

    @Transactional(readOnly = true)
    public HelpRequestResponse getRequest(UUID requestId) {
        HelpRequest request = requestRepository
                .findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("Request not found: " + requestId));
        return mapper.toResponse(request);
    }

    @Transactional(readOnly = true)
    public Page<HelpRequestResponse> searchRequests(HelpRequestSearchCriteria criteria, Pageable pageable) {
        Specification<HelpRequest> specification = Specification.where(HelpRequestSpecifications.hasStatus(criteria.status()))
                .and(HelpRequestSpecifications.ownedBy(criteria.elderlyId()))
                .and(HelpRequestSpecifications.nearLocation(criteria.latitude(), criteria.longitude(), criteria.radiusKm()));
        return requestRepository.findAll(specification, pageable).map(mapper::toResponse);
    }
}
