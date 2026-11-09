package com.cka.provider.capture;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.drive.Drive;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.UserCredentials;
import java.time.Duration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

/**
 * The Google-specific beans behind {@link GoogleDriveRecordingFolder}: the credential and the
 * Drive client built on it. Here rather than in {@code com.cka.config} for the reason
 * {@code RabbitEventBusConfiguration} gives — which provider is in use is a property's business,
 * how this one reaches its service is this file's.
 */
@Configuration
@ConditionalOnProperty(prefix = "cka.provider", name = "capture", havingValue = "google-drive")
class GoogleDriveConfiguration {

    /**
     * A refresh token and the client that issued it, which together mint access tokens for as
     * long as the token stays valid. {@link UserCredentials} refreshes silently when one
     * expires, which is the whole reason consent is a one-time thing.
     *
     * <p>Read from the {@link Environment} rather than injected with {@code @Value}, as the LLM
     * provider's key is and for the same reason: a missing one fails with a sentence naming the
     * variable rather than Spring's "Could not resolve placeholder".
     */
    @Bean
    UserCredentials googleCredentials(Environment environment) {
        return UserCredentials.newBuilder()
                .setClientId(required(environment, "cka.capture.google-drive.client-id", "GOOGLE_CLIENT_ID"))
                .setClientSecret(
                        required(environment, "cka.capture.google-drive.client-secret", "GOOGLE_CLIENT_SECRET"))
                .setRefreshToken(
                        required(environment, "cka.capture.google-drive.refresh-token", "GOOGLE_REFRESH_TOKEN"))
                .build();
    }

    @Bean
    Drive drive(UserCredentials credentials) throws Exception {
        var timeout = (int) Duration.ofSeconds(30).toMillis();
        return new Drive.Builder(
                        GoogleNetHttpTransport.newTrustedTransport(), GsonFactory.getDefaultInstance(), request -> {
                            new HttpCredentialsAdapter(credentials).initialize(request);
                            // Defaults are generous enough that a hung connection would stall a
                            // poll past its own interval and queue the next one behind it.
                            request.setConnectTimeout(timeout);
                            request.setReadTimeout(timeout);
                        })
                .setApplicationName("Capture-Knowledge-Action")
                .build();
    }

    private String required(Environment environment, String property, String variable) {
        var value = environment.getProperty(property);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("No Google Drive credential: set " + variable + " in app/.env ("
                    + property + "). Run app/scripts/google-consent.sh once to obtain a refresh token, or set "
                    + "CKA_PROVIDER_CAPTURE=none to start the stack without capture.");
        }
        return value;
    }
}
