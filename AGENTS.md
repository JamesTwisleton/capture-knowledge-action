# AGENTS.md

Instructions for AI agents working in this repository. Claude Code reads this file directly, as do other
agents following the [agents.md](https://agents.md) convention — no `CLAUDE.md` needed.

## Engineering conventions — read this before writing code

**Check what already exists before building it.** Tickets are written in advance and go stale.
Read the code first: the thing your ticket describes may already be there, half there, or there
under a different name. Say so and adjust scope rather than building it twice. The same applies
in reverse — if you find something a later ticket needs and it costs nothing now, leave it.

**Prefer the platform to your own code.** Spring Actuator already serves health; Next already
watches files; Compose already sequences startup. A hand-written version of something the
framework ships is code to maintain, plus a divergence waiting to happen. (This is the standing
"Ponytail" rule: does it need to exist, is it already here, does the platform do it.)

**Every acceptance criterion gets a test that runs in CI.** Not a note saying it was checked by
hand — a test. `app/scripts/verify-acceptance.sh` asserts the criteria for the environment itself
and runs on every push via `.github/workflows/ci.yml`; extend it as tickets land. Assert behaviour,
not configuration: an open port is not a debugger, and a running container is not a service that
answers.

**The repository has four places, and things belong in exactly one.** `app/` is everything
runnable — backend, frontend, compose file, scripts, entry points; work from there, not the root.
`docs/` is all documentation, the PRD included. `site/` is the published GitHub Pages site,
deployed by a workflow. The root holds only `README.md`, `AGENTS.md` and `LICENSE`, and should
stay that way. Link to `README.md` rather than restating it.

**Secrets live in `app/.env`, which is gitignored.** `app/.env.example` is the committed template.
From T20, `./setup.sh` writes real credentials into `app/.env` — never track it, never paste its contents
into a commit, a comment or an issue.

## PRD versioning — read this before editing README.md or anything under prd/

The PRD is `README.md` (Sections 1–6) plus the **numbered section pages** under [`docs/prd/`](docs/prd/) (Section 7 onward).
It carries a version line at the top of `README.md` — `**Product Requirements Document — Draft vX.Y(.Z) for
review**` — and a matching top entry in [`prd/changelog.md`](docs/prd/changelog.md).

**Not everything under `prd/` is the PRD.** Two delivery documents live there because they belong with the
documentation, but they are not numbered sections and **editing them does not require a version bump**:

- [`prd/mvp-tickets.md`](docs/prd/mvp-tickets.md) — the MVP ticket breakdown, tracked as GitHub epic #1.
- [`prd/demo-script.md`](docs/prd/demo-script.md) — the demo script, doubling as the MVP test plan (T21).

Each carries a note at the top saying so. If a change to one of them *also* changes a numbered section — say a
new ticket introduces a concept Section 7 has to define — then the edit to the numbered section is what earns
the bump, not the edit to the ticket file.

**Rule: never bump the version to v1.0 unless the user has explicitly said the draft is verified/approved.**
`README.md`'s own "Status" line says feedback is still being sought (see `prd/changelog.md` v0.8) — the version
number must not contradict that by claiming a finished, approved 1.0.

- From **v0.9 onward**, incremental edits bump the **third** number, not the second: v0.9 → v0.9.1 → v0.9.2 → …
  (standard semver patch bumps). Do not go v0.9 → v1.0, and do not go v0.9.4 → v1.0 either — v1.0 is never the
  automatic next step after any v0.9.x.
- Only move to v1.0 when the user explicitly says so (e.g. "the draft is verified," "ship v1.0," "graduate
  the version") — treat this as a deliberate, user-initiated milestone, never something you infer or default to.
- v0.1 through v0.9 predate this rule and were not retroactively renamed. The three-part scheme starts at v0.9.1.
- If you are ever unsure whether an edit "counts" as verification (it almost never does — a content fix,
  a rebuild, a new feature, a correction is not the same as human sign-off), treat it as not verified and
  keep bumping the patch number.

**When you make a content edit to the PRD that warrants a version bump** (the established pattern: most
substantive edits to `README.md` or any numbered section page under `prd/` get one, but see the two exceptions
above — see the changelog history for examples),
update both of these together:

1. The version line at the top of `README.md`.
2. A new top entry in `prd/changelog.md` describing what changed and why.

This rule exists because a version number is a claim other people will trust at face value — an LLM agent
skimming just the version line, or a stakeholder glancing at the repo, should never be misled into thinking
this PRD has cleared review when it hasn't.
