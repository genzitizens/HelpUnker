package com.helpunker.helprequest.sse;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Component
public class BoardEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(BoardEventPublisher.class);
    private static final long DEFAULT_TIMEOUT = Duration.ofMinutes(30).toMillis();

    private final List<SseEmitter> boardEmitters = new CopyOnWriteArrayList<>();
    private final Map<UUID, List<SseEmitter>> requestEmitters = new ConcurrentHashMap<>();

    public SseEmitter registerBoardEmitter() {
        SseEmitter emitter = new SseEmitter(DEFAULT_TIMEOUT);
        configureEmitter(emitter, boardEmitters);
        return emitter;
    }

    public SseEmitter registerRequestEmitter(UUID requestId) {
        SseEmitter emitter = new SseEmitter(DEFAULT_TIMEOUT);
        requestEmitters.computeIfAbsent(requestId, id -> new CopyOnWriteArrayList<>()).add(emitter);
        emitter.onCompletion(() -> removeEmitter(requestId, emitter));
        emitter.onTimeout(() -> removeEmitter(requestId, emitter));
        emitter.onError(throwable -> removeEmitter(requestId, emitter));
        return emitter;
    }

    public void publishBoardEvent(RequestEvent event) {
        boardEmitters.forEach(emitter -> send(emitter, event));
    }

    public void publishRequestEvent(UUID requestId, RequestEvent event) {
        requestEmitters.getOrDefault(requestId, List.of()).forEach(emitter -> send(emitter, event));
    }

    private void configureEmitter(SseEmitter emitter, List<SseEmitter> registry) {
        registry.add(emitter);
        emitter.onCompletion(() -> registry.remove(emitter));
        emitter.onTimeout(() -> registry.remove(emitter));
        emitter.onError(throwable -> registry.remove(emitter));
    }

    private void send(SseEmitter emitter, RequestEvent event) {
        try {
            emitter.send(SseEmitter.event()
                    .name(event.type().name())
                    .data(event, MediaType.APPLICATION_JSON));
        } catch (IOException ex) {
            log.debug("Removing closed SSE emitter", ex);
            emitter.completeWithError(ex);
        }
    }

    private void removeEmitter(UUID requestId, SseEmitter emitter) {
        List<SseEmitter> emitters = requestEmitters.get(requestId);
        if (emitters != null) {
            emitters.remove(emitter);
            if (emitters.isEmpty()) {
                requestEmitters.remove(requestId);
            }
        }
    }
}
