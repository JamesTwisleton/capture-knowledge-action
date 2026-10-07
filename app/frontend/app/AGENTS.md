# `app/` — App Router pages

Next.js App Router. `layout.tsx` is the shell every page renders inside; `page.tsx` is the root
route. T16 adds the real views (activity log, triage, errors) — expect a directory per route here.

See the parent [`frontend/AGENTS.md`](../AGENTS.md) for the frontend-wide conventions (hand-picked
dependencies, `NEXT_PUBLIC_API_URL`, polling-based file watching, the dev Dockerfile).

## Conventions

- **Server components by default; `'use client'` only when you need the browser.** `page.tsx` is a
  client component because it fetches on a timer and holds state. A view that just renders
  server-fetched data should not be.
- **The browser calls the backend, so the URL is a host address.** Always `NEXT_PUBLIC_API_URL`,
  never a Docker service name — nothing resolves `backend` outside the Compose network. This is
  T04's recorded decision: the browser hits the exposed host port directly rather than the Next
  server proxying it.
- **Treat a failed fetch as "cannot connect", not "fine".** A backend that is up but whose CORS
  headers block the read is indistinguishable from a stopped one *from this code's position*, and
  claiming otherwise is exactly the bug #24 had to fix. Report what you can actually observe.

## Two couplings in `page.tsx` that are easy to break by accident

Both are asserted by checks in [`app/scripts/tests/`](../../scripts/tests/), so breaking either
fails CI — but the failure reads oddly if you don't know the coupling is there.

1. **The literal string `Frontend stub`.** `t03_hot_reload_frontend.test.sh` proves hot reload by
   `sed`-ing that exact phrase out of this file and waiting for its replacement to be served. Reword
   that sentence and the check fails with "edit never appeared", pointing at `WATCHPACK_POLLING`
   rather than at the rename that actually caused it.
2. **The `data-connection-state` attribute**, whose value is one of `checking`, `connected`,
   `disconnected`. `t04_connectivity.test.sh` renders the page in headless Chrome and asserts that
   attribute flips when the backend container stops and starts. It is the test's whole contract
   with this page — it deliberately does *not* assert the colour values, so restyling is free, but
   renaming or removing the attribute is not.
