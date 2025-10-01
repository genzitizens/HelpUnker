package com.helpunker.common.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Set;

@Slf4j
@Component
public class RequestResponseLoggingFilter extends OncePerRequestFilter {

    private static final int MAX_PAYLOAD_LENGTH = 1000;
    private static final Set<String> READABLE_CONTENT_TYPES = Set.of(
            "application/json",
            "application/xml",
            "text/plain",
            "text/xml",
            "application/x-www-form-urlencoded"
    );

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (isAsyncDispatch(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        ContentCachingRequestWrapper requestWrapper = wrapRequest(request);
        ContentCachingResponseWrapper responseWrapper = wrapResponse(response);

        long start = System.currentTimeMillis();
        try {
            filterChain.doFilter(requestWrapper, responseWrapper);
        } finally {
            long duration = System.currentTimeMillis() - start;
            logExchange(requestWrapper, responseWrapper, duration);
            responseWrapper.copyBodyToResponse();
        }
    }

    private void logExchange(ContentCachingRequestWrapper request, ContentCachingResponseWrapper response, long duration) {
        String method = request.getMethod();
        String uri = request.getRequestURI();
        String queryString = request.getQueryString();
        String target = queryString != null ? uri + "?" + queryString : uri;
        String remoteAddress = request.getRemoteAddr();
        int status = response.getStatus();
        String requestBody = getPayload(request.getContentAsByteArray(), request.getCharacterEncoding(), request.getContentType());
        String responseBody = getPayload(response.getContentAsByteArray(), response.getCharacterEncoding(), response.getContentType());

        log.info("HTTP {} {} from {} -> status {} ({} ms) | requestBody={} | responseBody={}",
                method, target, remoteAddress, status, duration, requestBody, responseBody);
    }

    private ContentCachingRequestWrapper wrapRequest(HttpServletRequest request) {
        if (request instanceof ContentCachingRequestWrapper wrapper) {
            return wrapper;
        }
        return new ContentCachingRequestWrapper(request);
    }

    private ContentCachingResponseWrapper wrapResponse(HttpServletResponse response) {
        if (response instanceof ContentCachingResponseWrapper wrapper) {
            return wrapper;
        }
        return new ContentCachingResponseWrapper(response);
    }

    private String getPayload(byte[] content, String encoding, String contentType) {
        if (content == null || content.length == 0) {
            return "<empty>";
        }
        if (!isReadableContentType(contentType)) {
            return "<non-readable payload>";
        }

        Charset charset = resolveCharset(encoding);
        String payload = new String(content, charset);
        if (payload.length() <= MAX_PAYLOAD_LENGTH) {
            return payload;
        }
        return payload.substring(0, MAX_PAYLOAD_LENGTH) + "...(truncated)";
    }

    private boolean isReadableContentType(String contentType) {
        if (!StringUtils.hasText(contentType)) {
            return true;
        }
        String lowered = contentType.toLowerCase();
        return READABLE_CONTENT_TYPES.stream().anyMatch(lowered::contains);
    }

    private Charset resolveCharset(String encoding) {
        if (!StringUtils.hasText(encoding)) {
            return StandardCharsets.UTF_8;
        }
        try {
            return Charset.forName(encoding);
        } catch (IllegalArgumentException ignored) {
            return StandardCharsets.UTF_8;
        }
    }
}
