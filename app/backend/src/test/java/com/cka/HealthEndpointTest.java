package com.cka;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The health endpoint is Actuator's, not ours, so this asserts it is actually exposed
 * rather than re-testing Spring's implementation of it. The container healthcheck and
 * T04's connectivity slice both depend on this URL existing.
 *
 * <p>Deliberately a real HTTP call over a real port, using the JDK's own client: it is
 * the same thing the healthcheck does, and it avoids the test-autoconfigure modules,
 * which Spring Boot 4 rearranged.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class HealthEndpointTest {

    @LocalServerPort
    private int port;

    @Test
    void actuatorHealthIsExposedAndReportsUp() throws Exception {
        HttpResponse<String> response = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/actuator/health")).build(),
                HttpResponse.BodyHandlers.ofString());

        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("\"status\":\"UP\""), response.body());
    }
}
