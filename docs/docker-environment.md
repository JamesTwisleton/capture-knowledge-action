# The local environment

[← Back to the README](../README.md)

Three containers on one network: a Spring Boot **backend**, a Next.js **frontend**, and RabbitMQ
as the **event bus**. Everything runnable lives in `app/`, so that is where you work from.

```bash
cd app
cp .env.example .env     # once
docker compose up -d     # every time
```

Then <http://localhost:3000>. It should say the backend is connected. If it says unreachable, the
backend is still booting — Maven resolves dependencies on first run.

| | Host port | Set by | What |
|---|---|---|---|
| backend | 8080 | `BACKEND_PORT` | REST API and `/actuator/health` |
| backend | 5005 | `JVM_DEBUG_PORT` | JVM debugger (JDWP) |
| frontend | 3000 | `FRONTEND_PORT` | Next.js dev server |
| frontend | 9229 | `NODE_DEBUG_PORT` | Node inspector |
| event bus | 5672 | `EVENT_BUS_PORT` | AMQP |
| event bus | 15672 | `EVENT_BUS_MGMT_PORT` | RabbitMQ management UI |

## Changing code

**Backend:** `./refresh-backend.sh` from `app/`. It compiles inside the container; DevTools
notices the new classes and restarts the app in a second or two. No image rebuild, and **no Java
or Maven on your machine** — the container has both, which is rather the point. The script wraps
one line:

```bash
docker compose exec backend mvn -o compile
```

**Frontend:** just save. The dev server watches the mounted source, polling rather than waiting
for filesystem events, because those don't cross a bind mount reliably — hence
`WATCHPACK_POLLING=true` in the compose file.

**Changing `pom.xml` is different: restart the container.**

```bash
docker compose restart backend
```

Hot reload cannot pick up a dependency change, for two separate reasons. `refresh-backend.sh`
compiles offline, so a dependency that isn't in the local repository yet fails to resolve at all.
And even compiling online doesn't help: the JVM's classpath is fixed when it launches, and
DevTools restarts the *application context*, not the JVM — so the new jar is downloaded, the code
compiles, and the app then dies with `NoClassDefFoundError`. Restarting the container re-runs
`spring-boot:run`, which recomputes the classpath.

One consequence worth knowing: the dependency cache is baked into the image, not a volume, so a
jar fetched at runtime is lost when the container is recreated and will be downloaded again.
Rebuild the image to bake it in properly.

## Attaching a debugger

**Backend** — the JVM listens on 5005 from startup, so attach whenever. IntelliJ: *Run → Edit
Configurations → + → Remote JVM Debug*, host `localhost`, port `5005`. VS Code:
`{"type": "java", "request": "attach", "hostName": "localhost", "port": 5005}`.

**Frontend** — the Node inspector is always listening on 9229, same as the backend's. Attach to
`localhost:9229`, or open `chrome://inspect`. Next forks workers and gives each its own inspector
on the next port up, so you may see a second one announced on 9230; 9229 is the main process and
the one you want.

## Configuration

`app/.env` holds everything configurable. Compose reads it twice: to substitute `${...}` in
`docker-compose.yml`, and again via `env_file:` so the same values reach the processes inside the
containers. Both matter — the first decides which host port is published, the second is how the
application will read its credentials from T09 onwards.

`.env` is **gitignored**; `.env.example` is the committed template. From T20, `./setup.sh` writes
real credentials into `.env`, which is why it must never be tracked.

## Why it is built this way

**Containers run as a non-root user with UID 1000.** Bind mounts pass UIDs through untranslated,
so a container writing as root leaves root-owned files on your host. Matching the usual first-user
UID keeps the knowledge vault (T06) and SQLite database (T10) yours to open and delete. The
backend image needs a small dance for this — Ubuntu 24.04 ships a `ubuntu` user already squatting
on UID 1000 — while the frontend simply reuses the `node` user that is already there.

**Startup is sequenced on health, not hope.** `depends_on` alone waits for a container to start,
which is not the same as a broker accepting connections. Every service has a healthcheck and the
backend waits for the event bus to pass its own, so T07 doesn't spend its first afternoon
debugging a race.

**The browser calls the backend directly, across origins, so Actuator needs CORS.** Port 3000
and port 8080 are different origins as far as the browser is concerned; curl doesn't enforce
that, so this only shows up as `fetch()` failing in a real tab. Actuator's own
`management.endpoints.web.cors.*` properties handle it — no hand-written filter.

**Data lives on the host** under `app/data`, so the vault and database survive
`docker compose down` and open with ordinary tools. `node_modules` and `.next` get named volumes
instead: disposable, and keeping them out of the bind mount stops host copies shadowing the
container's.

**Versions are pinned and scanned, not assumed.** `maven:3.9.16-eclipse-temurin-25-noble` gives
Java 25 — the current LTS — and scans at zero critical and zero high, where the Alpine variant
carried 5 and 59. Spring Boot is on its latest release with Tomcat pinned ahead of it: Boot 4.1.1
manages Tomcat 11.0.24, which still carries CVEs fixed in 11.0.25, so `tomcat.version` is
overridden to 11.0.26. Delete that override once Boot catches up — tracked in [#26](https://github.com/JamesTwisleton/capture-knowledge-action/issues/26).

## Checking it still works

```bash
./app/scripts/verify-acceptance.sh
```

Brings the stack up, runs every check under `scripts/tests/` — one file per criterion, in parallel,
including a real JDWP handshake and a full hot-reload cycle — tears it down, and fails loudly if
anything regressed. CI runs the same script on every push.

## Everyday commands

From `app/`:

```bash
docker compose logs -f backend   # follow one service
docker compose ps                # what's up, and is it healthy
docker compose down              # stop
docker compose down -v           # stop and discard volumes
```

---

Related: [Architecture](prd/08-architecture.md) · [Deployment and portability](prd/11-deployment-and-portability.md)
