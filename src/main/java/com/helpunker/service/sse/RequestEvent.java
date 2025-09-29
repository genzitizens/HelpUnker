package com.helpunker.service.sse;

import com.helpunker.web.response.HelpRequestResponse;

public record RequestEvent(RequestEventType type, HelpRequestResponse payload) {
}
