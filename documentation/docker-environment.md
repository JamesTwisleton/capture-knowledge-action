# The local environment

[← Back to the README](../README.md)

Three containers on one network: a Spring Boot **backend**, a Next.js **frontend**, and RabbitMQ
as the **event bus**. The backend and frontend are deliberately thin — enough to prove the
environment works, not enough to be the product.

```bash
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

## Changing backend code

```bash
./refresh-backend.sh
```

That compiles inside the container; DevTools notices the new classes and restarts the app in a
second or two. No image rebuild, and **no Java or Maven on your machine** — the container has
both, which is rather the point. The script is a one-line wrapper around:

```bash
docker compose exec backend mvn -o compile
```

Frontend changes need nothing: the dev server watches the mounted source. It polls rather than
waiting for filesystem events, because those don't cross a bind mount reliably — hence
`WATCHPACK_POLLING=true` in the compose file.

## Attaching a debugger

**Backend** — the JVM listens on 5005 from startup, so attach whenever you like. In IntelliJ:
*Run → Edit Configurations → + → Remote JVM Debug*, host `localhost`, port `5005`. In VS Code, a
launch config of `{"type": "java", "request": "attach", "hostName": "localhost", "port": 5005}`.

**Frontend** — run the dev server with the inspector enabled (`npm run dev:debug` inside the
container) and attach to `localhost:9229`, or open `chrome://inspect`.

## Configuration

`.env` holds everything configurable. Compose reads it twice: to substitute `${...}` in
`docker-compose.yml`, and again via `env_file:` so the same values reach the processes inside the
containers. Both matter — the first sets which host port you publish, the second is how the
application will read its credentials from T09 onwards.

`.env` is **gitignored**. `.env.example` is the committed template; copy it. From T20 `./setup.sh`
will write real credentials into `.env` — API keys, OAuth secrets — which is why it must never be
tracked.

## Why it is built this way

**Containers run as a non-root user with UID 1000.** Bind mounts pass UIDs through untranslated,
so a container writing as root leaves root-owned files on your host. Matching the usual first-user
UID means the knowledge vault (T06) and SQLite database (T10) stay yours to open and delete. The
backend image needs a small dance for this — Ubuntu 24.04 ships a `ubuntu` user already squatting
on UID 1000 — and the frontend image just reuses the `node` user, which is already there.

**Startup is sequenced on health, not hope.** `depends_on` alone waits for a container to start,
which is not the same as a broker accepting connections. Each service has a healthcheck and the
backend waits for the event bus to pass its own, so T07 doesn't spend its first afternoon
debugging a race.

**Data lives on the host** under `./data`, so the vault and database survive `docker compose down`
and can be opened with ordinary tools. `node_modules` and `.next` get named volumes instead:
they're disposable, and keeping them out of the bind mount stops the host's copies shadowing the
container's.

**The base images are pinned and scanned.** `maven:3.9.16-eclipse-temurin-21-noble` was chosen
over the Alpine variant on evidence, not instinct: at the time of writing Alpine carried 5 critical
and 59 high vulnerabilities, and this carries none. Re-scan before bumping.

## Checking it still works

```bash
./scripts/verify-acceptance.sh
```

Brings the stack up, proves each of T01's acceptance criteria — including a real JDWP handshake
and an actual hot-reload cycle — tears it down, and fails loudly if anything regressed. CI runs
the same script on every push.

## Everyday commands

```bash
docker compose logs -f backend   # follow one service
docker compose ps                # what's up, and is it healthy
docker compose down              # stop
docker compose down -v           # stop and discard volumes
```

---

Related: [Architecture](../prd/08-architecture.md) · [Deployment and portability](../prd/11-deployment-and-portability.md)
