package com.cka.testdouble;

import com.cka.core.CapturedContent;
import com.cka.provider.ProviderHealth;
import com.cka.provider.capture.ContentListener;

import java.util.function.Consumer;

/** Finds content only when a test tells it to, via {@link #find}. */
public class ManualContentListener implements ContentListener {

    private Consumer<CapturedContent> onContent = content -> {
        throw new IllegalStateException("find() called before listen()");
    };

    @Override
    public void listen(Consumer<CapturedContent> onContent) {
        this.onContent = onContent;
    }

    public void find(CapturedContent content) {
        onContent.accept(content);
    }

    @Override
    public ProviderHealth checkHealth() {
        return ProviderHealth.up("manual");
    }
}
