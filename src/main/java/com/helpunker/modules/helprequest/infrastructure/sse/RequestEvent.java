package com.helpunker.modules.helprequest.infrastructure.sse;

import com.helpunker.modules.helprequest.application.dto.response.HelpRequestResponse;

public record RequestEvent(RequestEventType type, HelpRequestResponse payload) {
}
