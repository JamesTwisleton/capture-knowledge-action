# `com.cka.provider.capture` — capture tests

Tests for [`main/.../provider/capture`](../../../../../../main/java/com/cka/provider/capture/AGENTS.md).

`FolderWatcherTest` covers T09's first pass: what the watcher decides, over a hand-written
`RecordingFolder`.

- **A hand-written fake, not a mock.** The folder has real behaviour the tests depend on — files
  appearing between polls, reading one returning its text, listing throwing when it is
  unreachable — and stubbing that in Mockito would mean writing the implementation in stubs. This
  is the exception `testsupport/AGENTS.md` describes, kept local because only this class needs it.
- **Each test says what breaks in production, not what the method does.** "One meeting that
  cannot be handed on does not starve the others" is a bug that stops a folder making progress
  entirely; "a meeting name containing brackets keeps them" is a prompt chosen by where somebody
  put a bracket in a calendar invite.
- **The schedule is tested, not assumed.** `listen()` is production code, so one test runs it at a
  20ms interval, adds a meeting *after* listening starts, and asserts the folder was looked at
  more than once — a one-shot schedule would pass every other test in the class. It also asserts
  no further polls happen after `stop()`, since a daemon thread outliving a shutdown is a leak
  nothing else would notice.
- **No Google account, and no Drive.** Everything here is the watcher's own logic. Reaching Drive
  is T09's second pass and will be an implementation of `RecordingFolder`.
