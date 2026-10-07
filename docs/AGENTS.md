# Documentation (`docs/`)

All documentation lives here: the product requirements and the engineering notes.

- [`prd/`](prd/) — the PRD from Section 7 onward, plus the [ticket breakdown](prd/mvp-tickets.md),
  the [demo script](prd/demo-script.md) and the [changelog](prd/changelog.md). Sections 1–6 are
  the repository [`README.md`](../README.md).
- [`docker-environment.md`](docker-environment.md) — the local Compose environment: running it,
  debugging it, and why it is shaped that way.

**Not to be confused with `site/`**, which is the published GitHub Pages site — the landing page
and the interactive prototype. Nothing here is served to the public.

## Writing here

- Link to the README and the PRD rather than restating them; duplicated prose goes stale in one
  place and not the other.
- Document the *why*. The how is readable from the compose file; the reasoning is not.
- Keep ports and commands honest — change `app/docker-compose.yml` and change them here in the
  same commit. `./app/scripts/verify-acceptance.sh` will catch you if you forget.
- Editing anything under `prd/` means a version bump; see the rules in [`AGENTS.md`](../AGENTS.md).
