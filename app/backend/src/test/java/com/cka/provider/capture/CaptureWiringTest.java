package com.cka.provider.capture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Selecting the capture provider has to produce a context that starts — a failure no unit test
 * can see, because every one of them calls a constructor directly.
 *
 * <p>No event bus, so no stage starts: the watcher never polls and the reconciler never runs.
 * Its own database, fresh each run, because Flyway would otherwise migrate the developer's own —
 * the default path is the one Compose mounts.
 */
@SpringBootTest(
        properties = {
            "cka.provider.capture=google-drive",
            "cka.provider.eventbus=none",
            "cka.capture.google-drive.folder-id=test-folder",
            "cka.capture.google-drive.client-id=test",
            "cka.capture.google-drive.client-secret=test",
            "cka.capture.google-drive.refresh-token=test"
        })
@DisplayName("Selecting the Drive capture provider wires a context that starts")
class CaptureWiringTest {

    @DynamicPropertySource
    static void ownDatabase(DynamicPropertyRegistry registry) throws IOException {
        var database = Files.createTempDirectory("cka-capture-wiring").resolve("cka.db");
        registry.add("spring.datasource.url", () -> "jdbc:sqlite:" + database);
    }

    /** Replaced so the context needs no Google credentials and makes no call. */
    @MockitoBean
    private com.google.api.services.drive.Drive drive;

    @Autowired
    private ContentListener listener;

    @Test
    @DisplayName("The folder watcher is the content listener")
    void theFolderWatcherIsTheContentListener() {
        assertThat(listener).isInstanceOf(FolderWatcher.class);
    }
}
