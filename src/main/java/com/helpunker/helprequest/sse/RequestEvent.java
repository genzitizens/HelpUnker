package com.helpunker.helprequest.sse;

import com.helpunker.helprequest.dto.response.HelpRequestResponse;

public record RequestEvent(RequestEventType type, HelpRequestResponse payload) {
}
