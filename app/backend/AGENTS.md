# Backend (`backend/`)

The CKA core: Spring Boot 4, Java 25, Maven.

| | |
|---|---|
| Run the stack | `docker compose up -d` (from the repo root) |
| Apply a code change | `./refresh-backend.sh` — compiles in the container, DevTools restarts |
| Apply a **`pom.xml`** change | `docker compose restart backend` — hot reload cannot add a jar to a running JVM |
| Tests | `docker compose exec backend mvn test`, or `mvn test` here if you have a local JDK 25 |
| Health | `/actuator/health` on port 8080 |
| Debugger | JDWP on 5005, listening from startup |

## Conventions

- **Health is Actuator's job.** Don't hand-roll an endpoint for it; T02 requires Actuator and the
  container healthcheck, T04's connectivity slice and CI all read `/actuator/health`.
- **Use `var` for local variables wherever the type is already obvious from the right-hand side** —
  a constructor call, a factory method, a literal. It keeps the declaration focused on the name
  and the value rather than a type the compiler already knows. Skip it only where that would hide
  something a reader needs: a bare numeric or boolean literal, or a call whose return type isn't
  obvious from its name.
- **Use Lombok for boilerplate.** `@Getter`, `@Setter`, `@RequiredArgsConstructor`, `@Slf4j` — reach
  for the annotation before hand-writing what it already generates. Nothing in this package needs
  it yet (a `@SpringBootApplication` main class and a test have no boilerplate to remove), but
  domain and provider classes from T05 onward should default to it.
- **Tests before code**, and every acceptance criterion gets one that runs in CI.
- **Check before you build** — read the existing source before implementing a ticket, since an
  earlier one may already have covered part of it.
- An `AGENTS.md` belongs in each package as real structure appears; right now there is only
  `com.cka`, and a file per empty folder would be noise.

## Layout

- `src/main/java/com/cka/` — application code.
- `src/test/java/com/cka/` — tests.
- `Dockerfile` — dev image: full JDK and Maven, runs via `spring-boot:run` for hot reload. A
  deployable image would be a separate multi-stage build on a JRE; this is not it.
