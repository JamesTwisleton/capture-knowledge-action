# `com.cka.provider.capture` — content listeners

The Capture stage's provider: `ContentListener`, which finds new content and hands each item to
`CaptureStage` exactly once. `FolderWatcher` (T09) is the implementation.

| | |
|---|---|
| Polls | a `RecordingFolder` — the seam that knows where recordings live |
| Interval | `cka.capture.poll-interval-seconds`, measured from the end of the previous poll |
| Status | `FolderWatcher.status()`, a `PollingStatus` for T16's activity log |
| Not built yet | the Google Drive `RecordingFolder` and its OAuth, T09's second pass |

**T09 is in two passes, as T08 is.** The first is here: the watcher's own logic — grouping Meet's
files into meetings, deciding which are ready, remembering what it has handed on, naming the
content type — all of which is testable with no Google account and is where the bugs are. The
second reaches Drive, which is a `RecordingFolder` implementation and nothing else.

- **`listen(Consumer)` is push-shaped on purpose.** A folder watcher polls on its own schedule and
  calls the consumer; a webhook receiver would call it from a request. Both fit, and neither
  leaks its mechanism into the stage. Don't add a `poll()` to the interface for one of them.
- **"Exactly once" is the implementation's job.** The listener remembers what it has already
  handed over — a re-delivered recording would start a second action decision chain and comment
  on every ticket twice.
- **Assign the content type here** (`CapturedContent.contentType`, PRD 7.6), from what the source
  knows — for Meet, the meeting title. The LLM provider picks its prompt by it.
- Polling status for the front end (T09) is an addition to the implementation, not to this
  interface, unless a second listener needs it too.

## The folder watcher

- **Meet names a meeting's files alike**, with a suffix saying which is which, and grouping by
  that shared prefix is the whole mechanism — which is why `RecordingFolder` deals in names and
  not ids. The suffixes are a vendor's naming convention rather than an API: re-check them
  against a real recording before trusting them.
- **A meeting with no transcript is not handed on.** The transcript is what gets summarised, so
  publishing without one starts a chain the LLM provider cannot finish, and the failure surfaces
  two stages from the folder that caused it. Meet writes the recording first and the transcript
  minutes later, so an incomplete meeting is normal and must not be remembered as handed on —
  nor must one whose transcript file exists but is still empty.
- **The content type is the meeting's own name, and nothing maps it.** PRD 7.6 keys prompts by
  content type, says adding one is a row rather than code, and `PromptStore.current` already
  falls back to General for a type it has never seen. A table of known meeting names here would
  be a second place to add a type, and the one that silently wins. The name is the title with
  Meet's trailing timestamp removed — by `lastIndexOf`, because names carry brackets of their
  own ("Planning (Q4)") and the timestamp is the last group, not the first.
- **A failure is caught per meeting, not per poll.** The consumer is the bus and a publish can
  fail; catching around the loop means one failure skips every meeting after it, and since the
  failing one keeps its place at the front of the next poll, the folder stops making progress at
  all. A meeting is remembered only *after* the consumer returns, so a failed hand-off is retried
  — forever, if it always fails, which is the right way round.
- **A poll never throws.** It runs on a timer with nothing above it to catch anything, so an
  unreachable folder is logged and the next poll still happens. A timer that dies on the first
  network blip takes the pipeline quiet with it and says so only once.
- **What it has handed on is in memory**, so a restart re-hands-on everything still in the
  folder. A real limitation, left alone deliberately: persisting it belongs with whatever gives
  the pipeline an idempotency story, not with the thing that reads a directory.
- **`scheduleWithFixedDelay`, not `scheduleAtFixedRate`.** The delay runs from the end of the
  previous poll, so a slow folder spaces its own polls out instead of queueing a backlog the
  executor then runs back to back. One thread, so two polls never walk the folder at once.
