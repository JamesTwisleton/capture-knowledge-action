package com.cka.provider.capture;

import com.cka.core.CapturedContent;
import com.cka.provider.Provider;
import java.util.function.Consumer;

/**
 * Listens for new content from a capture provider. How it listens is the implementation's
 * business: a folder watcher polls on an interval (T09), a webhook receiver would be called.
 * Either way it hands each new item to the consumer it was given, once.
 */
public interface ContentListener extends Provider {

    /** Starts listening; {@code onContent} is called once for each new item found. */
    void listen(Consumer<CapturedContent> onContent);
}
