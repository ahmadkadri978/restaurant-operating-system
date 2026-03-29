package com.restaurantqr.sse;

import com.restaurantqr.sse.bridge.SseEventBridge;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Listens for {@link SseOutboundEvent}s and delegates to the active {@link SseEventBridge}
 * <b>only after the enclosing transaction has committed</b>.
 * <p>
 * The bridge decides whether to publish via Redis (multi-instance) or directly
 * to local registries (single-instance fallback).
 */
@Component
public class SseAfterCommitListener {

    private static final Logger log = LoggerFactory.getLogger(SseAfterCommitListener.class);

    private final SseEventBridge eventBridge;

    public SseAfterCommitListener(SseEventBridge eventBridge) {
        this.eventBridge = eventBridge;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onSseOutboundEvent(SseOutboundEvent event) {
        try {
            eventBridge.publish(event);
        } catch (Exception e) {
            // SSE broadcast is best-effort — log and swallow to avoid post-commit failures
            log.error("Failed to publish SSE event after commit. type={}, restaurantId={}",
                    event.eventType(), event.restaurantId(), e);
        }
    }
}


