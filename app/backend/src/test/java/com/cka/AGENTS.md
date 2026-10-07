# `com.cka` — tests

Mirrors the main source tree package for package. A test for `com.cka.provider.knowledge.Foo`
belongs in `com/cka/provider/knowledge/FooTest.java`, not in a parallel hierarchy of its own.

The one exception is [`testsupport/`](testsupport/AGENTS.md), which holds the few fixtures a
mock cannot replace — the in-memory event bus above all. Mock the providers; see that file before
adding anything to it.

## How to run them

| | |
|---|---|
| In the container | `docker compose exec backend mvn test` |
| On the host | `mvn test` from `backend/`, if you have a local JDK 25 |
| In CI | the `backend-tests` job in `.github/workflows/ci.yml`, on every push |

## Conventions

- **Every test class and test method carries a `@DisplayName`.** It is JUnit's own annotation —
  there is no `@Description` in Jupiter, that is TestNG and Allure. Write a sentence that says what
  the behaviour is, not a restatement of the method name, and use the punctuation a method name
  cannot have: `/actuator/health is exposed and reports UP`, `Round-trips back to the tracker's own
  shape`. `pom.xml` configures Surefire to report by display name, so these are what the XML the
  Actions UI renders contains. (Surefire still prints the raw method identifier on the console's
  failure line; that one is not configurable, which is another reason method names stay readable
  in their own right.)
- **Tests before code.** Red-green-refactor, properly: write the failing test, watch it fail for
  the right reason, then make it pass. This is a standing repository rule, and T02's acceptance
  criteria name it explicitly.
- **Test our code, not the framework's.** `HealthEndpointTest` asserts that `/actuator/health` is
  *exposed and reporting UP*, because the container healthcheck and T04's connectivity slice both
  depend on that URL existing. It does not re-test Spring's implementation of health reporting.
  If a test would fail only when Spring itself is broken, it is not earning its runtime.
- **Prefer a real HTTP call over a mocked one** for anything another process will actually call.
  `HealthEndpointTest` uses `@SpringBootTest(webEnvironment = RANDOM_PORT)` with the JDK's own
  `HttpClient` — the same thing the Docker healthcheck does. Mocks here would pass while the real
  endpoint 404s.
- **Unit tests are not the acceptance criteria.** A ticket's criteria get a shell check under
  [`app/scripts/tests/`](../../../../../../scripts/tests/) that runs against the real running stack.
  Tests here prove the code; those prove the promise. Both, not either.

## Gotchas specific to this package

- **Spring Boot 4 rearranged the test-autoconfigure modules.** Several `@...Test` slice annotations
  moved or changed artifact. `HealthEndpointTest` deliberately avoids them and makes a plain HTTP
  call instead, which is both simpler and immune to that churn. Check what is actually on the
  classpath before reaching for a slice annotation from a Boot 3 tutorial.
- **Lombok in tests needs the same annotation processor path** as main (configured in `pom.xml`).
  If a `@Builder` on a test fixture appears to generate nothing, that is why — see
  [`../../../../main/java/com/cka/AGENTS.md`](../../../../main/java/com/cka/AGENTS.md).
