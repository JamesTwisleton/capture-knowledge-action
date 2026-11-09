package com.cka.provider.capture;

import com.google.auth.oauth2.ClientId;
import com.google.auth.oauth2.UserAuthorizer;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * The one-time consent flow (T09): run it once, approve in a browser, and it prints the refresh
 * token the backend then uses forever. Run it through {@code app/scripts/google-consent.sh};
 * T20's {@code ./setup.sh} will call that.
 *
 * <p>A refresh token is what lets CKA keep working without anybody present. Access tokens last an
 * hour; this is exchanged for a fresh one silently, which is why consent happens once rather than
 * every morning.
 *
 * <p><strong>Nothing here is printed except the refresh token itself.</strong> The client secret
 * is read from the environment and never echoed, and the token is printed because there is
 * nowhere else to put it — it has to reach {@code app/.env}, which is gitignored, and writing to
 * that file on somebody's behalf would mean parsing and rewriting a file they own.
 */
public final class GoogleConsent {

    /**
     * Read-only, and the narrowest scope that can list a folder and export a Doc. CKA never
     * writes to Drive: a recording is the one thing in the pipeline that cannot be regenerated,
     * so the credential it holds should not be able to touch one.
     */
    private static final List<String> SCOPES = List.of("https://www.googleapis.com/auth/drive.readonly");

    private GoogleConsent() {}

    public static void main(String[] args) throws Exception {
        var clientId = required("GOOGLE_CLIENT_ID");
        var clientSecret = required("GOOGLE_CLIENT_SECRET");

        // Port 0: the OS picks a free one. A fixed port would collide with whatever else is
        // listening, and a Desktop-app OAuth client accepts any loopback port, so there is
        // nothing to register.
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        var callback = URI.create("http://127.0.0.1:" + server.getAddress().getPort());

        var authorizer = UserAuthorizer.newBuilder()
                .setClientId(ClientId.of(clientId, clientSecret))
                .setScopes(SCOPES)
                .setCallbackUri(callback)
                .build();

        BlockingQueue<String> codes = new ArrayBlockingQueue<>(1);
        server.createContext("/", exchange -> {
            var query = exchange.getRequestURI().getQuery();
            var code = valueOf(query, "code");
            var reply = code != null
                    ? "CKA is connected to Google Drive. You can close this tab."
                    : "No authorisation code came back: " + query;
            var bytes = reply.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "text/plain; charset=utf-8");
            exchange.sendResponseHeaders(code != null ? 200 : 400, bytes.length);
            try (var out = exchange.getResponseBody()) {
                out.write(bytes);
            }
            if (code != null) {
                codes.offer(code);
            }
        });
        server.start();

        try {
            System.out.println("Open this in a browser and approve:\n");
            // No extra parameters. UserAuthorizer already sets access_type=offline, which is
            // what asks for a refresh token at all, and approval_prompt=force, which makes
            // Google issue a new one even for an account that has approved before. Adding
            // prompt=consent for that second job is both redundant and rejected: Google returns
            // "Conflict params: approval_prompt and prompt".
            System.out.println(authorizer.getAuthorizationUrl(null, null, callback));
            System.out.println("\nWaiting for the redirect…");

            var code = codes.poll(5, TimeUnit.MINUTES);
            if (code == null) {
                System.err.println("Nothing came back within five minutes. Nothing has changed; run it again.");
                System.exit(1);
            }

            var refreshToken = authorizer.getCredentialsFromCode(code, callback).getRefreshToken();
            if (refreshToken == null) {
                // Google can still withhold one for an account that has approved before — worth
                // saying, because the flow otherwise looks successful.
                System.err.println("Google returned no refresh token. Remove CKA from "
                        + "myaccount.google.com/permissions and run this again.");
                System.exit(1);
            }
            System.out.println("\nAdd this line to app/.env:\n");
            System.out.println("GOOGLE_REFRESH_TOKEN=" + refreshToken);
        } finally {
            server.stop(0);
        }
    }

    private static String required(String variable) {
        var value = System.getenv(variable);
        if (value == null || value.isBlank()) {
            System.err.println(variable + " is not set. Put the Desktop-app OAuth client's id and "
                    + "secret in app/.env, then run this with those variables exported — "
                    + "`set -a; . app/.env; set +a` does it.");
            System.exit(1);
        }
        return value;
    }

    /** The one query parameter this needs, without pulling in a URL-parsing dependency for it. */
    private static String valueOf(String query, String name) {
        if (query == null) {
            return null;
        }
        for (var pair : query.split("&")) {
            var eq = pair.indexOf('=');
            if (eq > 0 && pair.substring(0, eq).equals(name)) {
                return java.net.URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8);
            }
        }
        return null;
    }
}
