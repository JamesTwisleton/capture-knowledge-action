# `com.cka.api` — the REST surface

What the front end calls. T15 owns this package and settles its conventions; the one controller
here arrived early, with T09, because a meeting that was captured and never stored needed a way
back that a person chooses.

| Controller | Serves |
|---|---|
| `UnstoredCapturesController` | `GET /api/captures/unstored`, `POST /api/captures/unstored/retry` |

- **Controllers are thin.** They call a provider or a pipeline service and shape the response;
  the decision lives below them, where `PipelineWiringTest` and the provider tests reach it.
- **Paths start `/api/`**, apart from Actuator's own `/actuator/`.
- **The browser calls this cross-origin** (port 3000 to 8080), so each controller carries
  `@CrossOrigin(origins = "${cka.api.allowed-origin}")`. Without it the response is opaque to
  `fetch()` even though `curl` sees it. Actuator's CORS is configured separately, in Compose.
- **Tested over a real port** with the JDK's HTTP client, as `HealthEndpointTest` is, which
  avoids the test-autoconfigure modules Spring Boot 4 rearranged.
