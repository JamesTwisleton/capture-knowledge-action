# Documentation (`documentation/`)

Engineering documentation: how the repo is built and how to work in it.

**Not to be confused with `docs/`**, which is the published GitHub Pages site — the landing page,
the interactive prototype, and their assets. Nothing here should end up there.

- [`docker-environment.md`](docker-environment.md) — the local Compose environment: running it,
  debugging it, and why it is shaped the way it is.

## Writing here

- Link to `README.md` and the PRD rather than restating them; duplicated prose goes stale in one
  place and not the other.
- Document the *why*. The how is readable from the compose file; the reasoning is not.
- Keep port tables and commands honest — if you change `docker-compose.yml`, change them here in
  the same commit, and let `./scripts/verify-acceptance.sh` catch you if you forget.
