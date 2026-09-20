# Local Docker Compose Environment

[← Back to Main README](../README.md)

This document describes the local Docker Compose development environment for Capture-Knowledge-Action (CKA).

---

## 1. Overview

The local environment is orchestrated using Docker Compose to provide a simple, reproducible local development loop for all services in CKA.

The environment consists of three primary services:
1. **Backend (`backend`)**: Spring Boot (Java 21) REST API and orchestration core.
2. **Frontend (`frontend`)**: Next.js 15 (TypeScript / React 19) web UI.
3. **Event Bus (`event-bus`)**: RabbitMQ message broker for asynchronous event handling.

---

## 2. Network Topology & Ports

All containers run on a shared Docker bridge network named `cka-network`.

### Service & Debug Port Mapping

| Service | Container Port | Host Port (Default) | Env Variable | Purpose |
|---|---|---|---|---|
| `backend` | `8080` | `8080` | `BACKEND_PORT` | REST API |
| `backend` | `5005` | `5005` | `JVM_DEBUG_PORT` | JVM JDWP Remote Debugger |
| `frontend` | `3000` | `3000` | `FRONTEND_PORT` | Next.js Development Web Server |
| `event-bus` | `5672` | `5672` | `EVENT_BUS_PORT` | AMQP Protocol Port |
| `event-bus` | `15672` | `15672` | `EVENT_BUS_MGMT_PORT` | RabbitMQ Management Web UI |

---

## 3. How to Run

### Start Environment
```bash
docker compose up -d
```

### View Service Logs
```bash
docker compose logs -f
```

### Stop Environment
```bash
docker compose down
```

---

## 4. Debugging

### Attaching a Remote Debugger to the Backend JVM

The `backend` container starts with JDWP options enabled:
```
-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005
```

To attach a debugger from your IDE:

#### IntelliJ IDEA
1. Go to **Run > Edit Configurations...**.
2. Click **+** and select **Remote JVM Debug**.
3. Set **Host**: `localhost`, **Port**: `5005`.
4. Select JDK 21 as the target module SDK.
5. Click **Debug**. IDE will attach to the JVM socket on port 5005.

#### VS Code
1. Open `.vscode/launch.json`.
2. Add a configuration:
```json
{
  "type": "java",
  "name": "Attach to Remote JVM",
  "request": "attach",
  "hostName": "localhost",
  "port": 5005
}
```
3. Start debugging (**F5**).

---

## 5. Backend Hot Reload

Backend hot reloading is powered by **Spring Boot DevTools**.

- Source code (`./backend`) and target build directory (`./backend/target`) are volume-mounted into the `backend` container.
- When you run `mvn compile` on your host machine (or save code changes with automatic build in your IDE), Maven recompiles modified Java files into `./backend/target/classes`.
- Spring Boot DevTools detects changes in `target/classes` and automatically reloads the Spring application context inside the running container within seconds without requiring a full container rebuild.

---

## 6. Configuration via Environment Variables

All parameters can be overridden using a `.env` file in the root directory:

```env
BACKEND_PORT=8080
JVM_DEBUG_PORT=5005
FRONTEND_PORT=3000
NEXT_PUBLIC_API_URL=http://localhost:8080
EVENT_BUS_PORT=5672
EVENT_BUS_MGMT_PORT=15672
RABBITMQ_USER=guest
RABBITMQ_PASS=guest
```

---

## 7. Decisions Made and Rationale

1. **Shared Docker Network (`cka-network`)**: Enables direct inter-service communication (e.g. frontend contacting backend via service name `backend:8080` or host mapping, backend communicating with event bus on `event-bus:5672`).
2. **Standardised Debug Port (5005)**: Exposing standard JDWP port ensures zero-friction remote debugging for Java developers without modifying container entry points.
3. **Volume Mounts for Dev Loop**: Mounts source and output directories into dev containers so changes reflect instantly without rebuilding image layers.
4. **RabbitMQ for Event Bus Stub**: Lightweight, Alpine-based image that provides both standard AMQP and a visual Management UI on port 15672 out of the box.

---

## References

- [Main Product Overview (README.md)](../README.md)
- [Architecture Details](../prd/08-architecture.md)
- [Deployment & Portability](../prd/11-deployment-and-portability.md)
