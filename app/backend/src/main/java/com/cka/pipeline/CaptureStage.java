package com.cka.pipeline;

import com.cka.core.ChainId;
import com.cka.core.event.ContentCaptured;
import com.cka.provider.capture.ContentListener;
import com.cka.provider.eventbus.EventBus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Capture: publishes everything the content listener finds as content captured. This is
 * where an action decision chain begins, so it is where the chain id is minted.
 */
@Slf4j
@RequiredArgsConstructor
public class CaptureStage {

    private final ContentListener listener;
    private final EventBus bus;

    public void start() {
        listener.listen(content -> {
            var chainId = ChainId.next();
            // The start of a chain is worth a line: it is the only point where something enters
            // the pipeline from outside, and the chain id is what ties every later line to it.
            log.info(
                    "Captured \"{}\" as {} — {} characters of transcript [chain {}]",
                    content.title(),
                    content.contentType(),
                    content.transcript().length(),
                    chainId);
            bus.publish(
                    ContentCaptured.builder().chainId(chainId).content(content).build());
        });
    }
}
