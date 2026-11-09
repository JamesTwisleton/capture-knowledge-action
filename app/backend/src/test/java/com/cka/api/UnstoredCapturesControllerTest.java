package com.cka.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.cka.provider.capture.CapturedItems;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Each test uses its own title, because the class shares one database file as it shares one
 * Spring context.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"cka.provider.eventbus=none", "cka.api.allowed-origin=http://localhost:3000"})
@DisplayName("Meetings captured and never stored can be listed and retried over HTTP")
class UnstoredCapturesControllerTest {

    private static final Path DATABASE = temporaryDatabasePath();

    @LocalServerPort
    private int port;

    @Autowired
    private CapturedItems captured;

    @DynamicPropertySource
    static void sqliteDatabase(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:sqlite:" + DATABASE);
    }

    private static Path temporaryDatabasePath() {
        try {
            return Files.createTempDirectory("cka-api-test").resolve("cka.db");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private HttpResponse<String> send(HttpRequest.Builder request) throws Exception {
        return HttpClient.newHttpClient().send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpRequest.Builder to(String path) {
        return HttpRequest.newBuilder(URI.create("http://localhost:" + port + path));
    }

    private HttpRequest.Builder retrying(String title) {
        return to("/api/captures/unstored/retry")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"title\":\"" + title + "\"}"));
    }

    @Test
    @DisplayName("An unstored meeting is listed with when it was captured, readable from the front end's origin")
    void anUnstoredMeetingIsListed() throws Exception {
        captured.remember("Sprint Review 2026-11-08");

        var response = send(to("/api/captures/unstored").header("Origin", "http://localhost:3000"));

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("\"title\":\"Sprint Review 2026-11-08\"");
        // An ISO instant, not epoch milliseconds: the front end shows it to a person.
        assertThat(response.body()).containsPattern("\"capturedAt\":\"\\d{4}-\\d{2}-\\d{2}T");
        assertThat(response.headers().firstValue("Access-Control-Allow-Origin")).contains("http://localhost:3000");
    }

    @Test
    @DisplayName("Retrying an unstored meeting forgets it, so the next poll captures it again")
    void retryingForgetsTheMeeting() throws Exception {
        captured.remember("Design Crit / Mobile 2026-11-08");

        var response = send(retrying("Design Crit / Mobile 2026-11-08"));

        assertThat(response.statusCode()).isEqualTo(204);
        assertThat(captured.alreadyCaptured("Design Crit / Mobile 2026-11-08")).isFalse();
    }

    @Test
    @DisplayName("Retrying a meeting that is not unstored is a 404, and its note is not written twice")
    void retryingAStoredMeetingIsNotFound() throws Exception {
        captured.remember("Stored 2026-11-08");
        captured.confirmStored("Stored 2026-11-08");

        assertThat(send(retrying("Stored 2026-11-08")).statusCode()).isEqualTo(404);
        assertThat(send(retrying("Never captured 2026-11-08")).statusCode()).isEqualTo(404);
        assertThat(captured.alreadyCaptured("Stored 2026-11-08")).isTrue();
    }
}
