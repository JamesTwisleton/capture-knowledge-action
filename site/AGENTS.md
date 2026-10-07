# Published site (`site/`)

This folder **is** the public site at
<https://jamestwisleton.github.io/capture-knowledge-action/>. Everything in it is served.

- `index.html`, `landing.css`, `media/` — the landing page.
- `app/` — the interactive click-through prototype (fabricated data, no backend).

Deployed by [`.github/workflows/pages.yml`](../.github/workflows/pages.yml) on every push to
`main` that touches this folder. Branch-based Pages can only serve the repository root or `/docs`;
we wanted `docs/` for documentation, so the workflow publishes from here instead. Published URLs
are unchanged — `site/index.html` is `/`, `site/app/` is `/app/`.

## Before you add anything here

Ask whether it should be public. Engineering documentation and the PRD belong in
[`docs/`](../docs/AGENTS.md). A stray Markdown file here is a page on the product's website.
