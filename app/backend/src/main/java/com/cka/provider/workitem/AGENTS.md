# `com.cka.provider.workitem` — work item providers and mappers

`WorkItemProvider` queries the candidate pool and comments on or closes items; `WorkItemMapper`
converts the provider's native items to and from the core `WorkItem`. T13 adds GitHub Issues
here — the provider and its mapper.

- **Every work item provider ships a mapper** (PRD 7.9, a named extension point). Native types
  stay inside the provider; only `WorkItem` crosses into the core.
- **Map both type fields.** The generic `WorkItemType` for the core to reason about, and the
  tracker's own label in `providerTypeName`. When nothing fits, `OTHER` plus the label — never a
  false equivalence like "milestone is an epic".
- **Nesting is by `parentId`.** Set it from the tracker's own hierarchy (GitHub sub-issues, Jira
  epic links).
- **One method per operation** (PRD 10), so a team can override how comments are posted without
  replacing the provider. A new operation is a new method, alongside the `ActionType` it performs.
- **Failures surface as `ActionFailed`, not as swallowed exceptions** — T17 builds retry,
  switch-provider and dismiss on top of that.
