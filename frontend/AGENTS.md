# Frontend (`frontend/`)

The CKA web UI: Next.js 15, React 19, TypeScript.

| | |
|---|---|
| Run the stack | `docker compose up -d` (from the repo root) |
| Apply a code change | Just save — the dev server polls the mounted source and rebuilds |
| Dependencies | `npm ci` against the committed lockfile; never `npm install` in the image |
| Debugger | Node inspector on 9229 (`npm run dev:debug`), or `chrome://inspect` |

## Conventions

- **The browser, not the container, calls the backend.** Use `NEXT_PUBLIC_API_URL`, which points
  at a host port — Docker service names don't resolve outside the network.
- **Polling, not filesystem events.** `WATCHPACK_POLLING=true` is set in compose because native
  watch events don't cross a bind mount reliably. Removing it breaks hot reload silently.
- **Check before you build** — read the existing source before implementing a ticket.
- Every acceptance criterion gets a test that runs in CI.

## Layout

- `app/` — App Router pages. `page.tsx` is currently a stub proving the backend is reachable.
- `Dockerfile` — dev image running `next dev`. Runs as the base image's `node` user (UID 1000);
  `node_modules` and `.next` are named volumes so host copies never shadow the container's.
