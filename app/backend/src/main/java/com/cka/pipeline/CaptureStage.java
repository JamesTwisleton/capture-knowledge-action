package com.cka.pipeline;

import com.cka.core.event.ContentCaptured;
import com.cka.provider.capture.ContentListener;
import com.cka.provider.eventbus.EventBus;
import lombok.RequiredArgsConstructor;

import java.util.UUID;

/**
 * Capture: publishes everything the content listener finds as content captured. This is
 * where an action decision chain begins, so it is where the chain id is minted.
 */
@RequiredArgsConstructor
public class CaptureStage {

    private final ContentListener listener;
    private final EventBus bus;

    public void start() {
        listener.listen(content -> bus.publish(new ContentCaptured(UUID.randomUUID(), content)));
    }
}
