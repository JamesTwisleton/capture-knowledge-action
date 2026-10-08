# `com.cka.provider.capture` — content listeners

The Capture stage's provider: `ContentListener`, which finds new content and hands each item to
`CaptureStage` exactly once. T09 adds the Google Drive **folder watcher** here.

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
