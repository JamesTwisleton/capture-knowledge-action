# CLAUDE.md

Instructions for AI agents working in this repository.

## PRD versioning — read this before editing README.md or anything under prd/

The PRD is `README.md` (Sections 1–6) plus linked pages under [`prd/`](prd/) (Section 7 onward). It carries a
version line at the top of `README.md` — `**Product Requirements Document — Draft vX.Y(.Z) for review**` — and a
matching top entry in [`prd/changelog.md`](prd/changelog.md).

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
substantive edits to `README.md` or any file under `prd/` get one — see the changelog history for examples),
update both of these together:

1. The version line at the top of `README.md`.
2. A new top entry in `prd/changelog.md` describing what changed and why.

This rule exists because a version number is a claim other people will trust at face value — an LLM agent
skimming just the version line, or a stakeholder glancing at the repo, should never be misled into thinking
this PRD has cleared review when it hasn't.
