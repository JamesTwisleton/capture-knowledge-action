# `com.cka.api` — REST tests

Tests for [`main/.../api`](../../../../../main/java/com/cka/api/AGENTS.md).

| Class | Covers |
|---|---|
| `UnstoredCapturesControllerTest` | listing and retrying unstored meetings over HTTP, and the CORS header the front end needs |

- **A real port and a database file of its own**, as `SqlCapturedItemsTest` has: what is being
  tested is the JSON a browser receives, which a call to the controller method would not show.
- **Retry is tested here and not in `SqlCapturedItemsTest`.** These calls reach the real table,
  so one pair of tests proves the SQL and the status codes together.
