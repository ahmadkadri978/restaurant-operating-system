package com.restaurantqr.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Servlet filter that establishes request correlation context for logging.
 * <p>
 * Sets MDC fields:
 * <ul>
 *   <li>{@code requestId} — from incoming {@code X-Request-Id} header, or a generated UUID</li>
 *   <li>{@code instanceId} — the configured application instance identifier</li>
 * </ul>
 * The {@code X-Request-Id} is echoed back in the response for client-side correlation.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestCorrelationFilter extends OncePerRequestFilter {

    private static final String REQUEST_ID_HEADER = "X-Request-Id";
    private static final String MDC_REQUEST_ID = "requestId";
    private static final String MDC_INSTANCE_ID = "instanceId";

    private final String instanceId;

    public RequestCorrelationFilter(
            @Value("${app.instance-id:#{T(java.net.InetAddress).getLocalHost().getHostName()}}") String instanceId
    ) {
        this.instanceId = instanceId;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        try {
            String requestId = request.getHeader(REQUEST_ID_HEADER);
            if (requestId == null || requestId.isBlank()) {
                requestId = UUID.randomUUID().toString();
            }

            MDC.put(MDC_REQUEST_ID, requestId);
            MDC.put(MDC_INSTANCE_ID, instanceId);

            response.setHeader(REQUEST_ID_HEADER, requestId);

            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_REQUEST_ID);
            MDC.remove(MDC_INSTANCE_ID);
        }
    }
}

