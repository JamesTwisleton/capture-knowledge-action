# `com.cka.provider.workitem` — work item provider tests

Tests for [`main/.../provider/workitem`](../../../../../../main/java/com/cka/provider/workitem/AGENTS.md).

`WorkItemMapperTest` states the mapping contract from PRD 7.9 as executable assertions: the
tracker's own type label survives alongside the generic type, a type with no good generic match
becomes `OTHER` rather than a convenient near-equivalent, children keep their parent, and a round
trip back to the tracker's shape changes nothing. It runs against `TicketMapper`, the stand-in in
[`com.cka.testsupport`](../../../testsupport/AGENTS.md), because T05 ships no real provider.

- **T13's GitHub mapper gets its own test in this package**, asserting the same four things
  against real issue payloads. Treat `WorkItemMapperTest` as the worked example to follow, not as
  coverage that already includes your mapper — it tests the stand-in only.
- **Don't mock a mapper.** Stubbing `toWorkItem` to return a `WorkItem` asserts nothing, since the
  mapping is what is under test.
