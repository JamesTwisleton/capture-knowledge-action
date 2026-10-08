# `com.cka.pipeline` — stage tests

Tests for [`main/.../pipeline`](../../../../../main/java/com/cka/pipeline/AGENTS.md), wired
with Mockito mocks for the providers and the real in-memory bus from
[`com.cka.testsupport`](../testsupport/AGENTS.md).

- **`PipelineWiringTest` is T05's acceptance criterion** — the core wired with a stand-in for
  every provider interface, running a fake event through the whole chain. It runs in CI's backend unit-test job.
  It has no shell check under `app/scripts/tests/`, unlike T01–T04's criteria, because it is
  about the core running on doubles, which the live stack cannot show; T06 onward, whose
  criteria are about real providers, get their checks there.
- **Every stage gets a "runs alone" test**: publish its input event by hand, with nothing upstream
  wired, and assert on what it publishes. It is the cheapest proof that the stages are decoupled
  the way PRD 7.1 claims.
- **Assert on events and audit entries, not on calls between stages.** There are no calls between
  stages; if a test needs to verify one, something is wired wrong.
