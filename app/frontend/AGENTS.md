# Frontend (`app/frontend/`)

The CKA web UI: Next.js 16, React 19, TypeScript 7, on Node 24 (current LTS).

| | |
|---|---|
| Run the stack | `docker compose up -d` from `app/` |
| Apply a code change | Just save — the dev server polls the mounted source and rebuilds |
| Dependencies | `npm ci` against the committed lockfile; never `npm install` in the image |
| Debugger | Always listening on 9229 — just attach, or open `chrome://inspect` |

## Conventions

- **Dependencies are hand-picked and few.** Three runtime (`next`, `react`, `react-dom`) and four
  type/build packages. Adding one needs a reason a reviewer would accept.
- **The browser, not the container, calls the backend.** Use `NEXT_PUBLIC_API_URL`, which points
  at a host port — Docker service names don't resolve outside the network.
- **Polling, not filesystem events.** `WATCHPACK_POLLING=true` is set in compose because native
  watch events don't cross a bind mount reliably. Remove it and hot reload breaks silently.
- **`tsconfig.json` is effectively Next's.** It rewrites the file on build to add what it needs
  (`jsx: react-jsx`, `allowJs`, `resolveJsonModule`), so match its output rather than fighting it.
- **Check before you build** — read the existing source before implementing a ticket; an earlier
  one may already have covered part of it.
- Every acceptance criterion gets a test that runs in CI.

## Layout

- `app/` — App Router pages. `page.tsx` is a stub proving the backend is reachable.
- `Dockerfile` — dev image running `next dev` as the base image's `node` user (UID 1000).
  `node_modules` and `.next` are named volumes so host copies never shadow the container's.
  Alpine, deliberately: for Node it scans far cleaner than the Debian variants.
