/* Capture-Knowledge-Action — interactive prototype.
   All data below is fabricated for demonstration. No real people, meetings,
   work items or costs. Names and figures are illustrative only. */

window.CKA_DATA = {

  demoStack: {
    capture: "Google Meet (Drive folder watch)",
    knowledge: "Local Markdown vault (Obsidian-compatible)",
    workItems: "GitHub Issues",
    llm: "Claude via LangChain4j gateway",
    decision: "Jev (TypeSafe AI)",
    eventBus: "In-memory",
    audit: "SQLite",
    notifications: "Email"
  },

  providerCategories: [
    {
      id: "capture", name: "Meeting capture", role: "Golden path",
      blurb: "Detects and fetches new recordings — and the platform's own transcript where one exists.",
      options: [
        { id: "gmeet", name: "Google Meet", note: "via Drive folder watch · supplies transcripts", default: true },
        { id: "teams", name: "Microsoft Teams", note: "supplies transcripts" },
        { id: "zoom", name: "Zoom", note: "recording only — transcription needed" }
      ]
    },
    {
      id: "knowledge", name: "Knowledge", role: "Golden path",
      blurb: "Stores, links and indexes knowledge. An offline, LLM-readable store is a first-class option, not a fallback.",
      options: [
        { id: "vault", name: "Local Markdown vault", note: "Obsidian-compatible · offline · no vendor account", default: true },
        { id: "confluence", name: "Confluence", note: "hosted" },
        { id: "notion", name: "Notion", note: "hosted" }
      ]
    },
    {
      id: "workitems", name: "Work items", role: "Golden path",
      blurb: "Query, comment, transition, create.",
      options: [
        { id: "github", name: "GitHub Issues", note: "targets REST v2022-11-28", default: true },
        { id: "jira", name: "Jira", note: "targets platform v3" },
        { id: "ado", name: "Azure DevOps (Boards)", note: "targets 7.1" }
      ]
    },
    {
      id: "llm", name: "LLM", role: "Golden path", sensitive: true,
      blurb: "Transcribe, summarise, extract — all through an LLM gateway (LangChain4j / LiteLLM). Each job can use a different model.",
      options: [
        { id: "claude", name: "Claude", note: "cloud · via gateway", cloud: true, default: true },
        { id: "openai", name: "OpenAI", note: "cloud · via gateway", cloud: true },
        { id: "gemini", name: "Gemini", note: "cloud · via gateway", cloud: true },
        { id: "ollama", name: "Local models via Ollama", note: "data never leaves your infrastructure" }
      ]
    },
    {
      id: "decision", name: "Decision", role: "Golden path",
      blurb: "Calibrated classification and scoring — a probability or a fixed choice, never prose.",
      options: [
        { id: "jev", name: "Jev (TypeSafe AI)", note: "purpose-built decision model · mention detection · action classification", default: true },
        { id: "llm", name: "Your configured LLM", note: "constrained-decision mode via the gateway — uses whichever LLM you chose above" }
      ]
    },
    {
      id: "eventbus", name: "Event bus", role: "Supporting",
      blurb: "Publish and subscribe between the three stages.",
      options: [
        { id: "memory", name: "In-memory", note: "local runs · zero setup", default: true },
        { id: "kafka", name: "Kafka", note: "" },
        { id: "pubsub", name: "Google Pub/Sub", note: "" },
        { id: "rabbit", name: "RabbitMQ", note: "" }
      ]
    },
    {
      id: "audit", name: "Audit store", role: "Supporting",
      blurb: "One authoritative record of every action, decision, approval and failure.",
      options: [
        { id: "sqlite", name: "SQLite", note: "zero-setup, single-file database", default: true },
        { id: "postgres", name: "Postgres", note: "" },
        { id: "mysql", name: "MySQL", note: "" }
      ]
    },
    {
      id: "notify", name: "Notifications", role: "Supporting",
      blurb: "How humans are alerted when something needs them.",
      options: [
        { id: "email", name: "Email", note: "", default: true },
        { id: "slack", name: "Slack", note: "" }
      ]
    }
  ],

  contentTypes: [
    {
      id: "standup", name: "Stand-up", family: "Meeting type",
      rule: "Calendar title contains “stand-up” or daily recurrence before 10:30",
      prompt: "Summarise this stand-up. For each person: what changed since yesterday, what they will do next, and — most importantly — whether they are blocked and on what. List blockers first. Keep it under 200 words. Do not invent detail that was not said."
    },
    {
      id: "refinement", name: "Ticket refinement", family: "Meeting type",
      rule: "Calendar title contains “refinement” or “backlog”",
      prompt: "Summarise this refinement session per work item discussed: what was decided, what estimates or acceptance criteria changed, what was deferred and why. Quote item IDs exactly as spoken. End with a list of items touched and items deferred."
    },
    {
      id: "planning", name: "Sprint planning", family: "Meeting type",
      rule: "Calendar title contains “planning”",
      prompt: "Summarise this planning session: the sprint goal in one sentence, the items committed, the items discussed but not taken, and any risks or dependencies raised. Note who raised each risk."
    },
    {
      id: "retro", name: "Retrospective", family: "Meeting type",
      rule: "Calendar title contains “retro”",
      prompt: "Summarise this retrospective: what went well, what didn't, and the concrete follow-up actions with owners. Keep personal remarks out of the summary; record actions, not blame."
    },
    {
      id: "decision-record", name: "Decision record", family: "Document type",
      rule: "Source page lives under /decisions or carries the “adr” label",
      prompt: "Summarise this decision record: the decision in one sentence, the options considered, why the chosen option won, and what would trigger revisiting it."
    },
    {
      id: "general", name: "General", family: "Fallback",
      rule: "Used when no rule matches and no type was chosen — the page will carry a call to action to assign one",
      prompt: "Summarise this content faithfully: main topics, decisions if any, follow-ups if any. State clearly if the content type is unclear so a human can assign a better one."
    }
  ],

  meeting: {
    title: "Atlas platform — Sprint 14 ticket refinement",
    platform: "Google Meet",
    date: "Wednesday 17 September 2026, 10:00–10:41",
    duration: "41 min",
    attendees: ["Priya Nair", "Tom Okafor", "Sofia Marchetti", "Dan Whittaker"],
    contentType: "Ticket refinement",
    typeAssignedBy: "Rule: calendar title contains “refinement”",
    platformTranscript: true,
    vaultPath: "meetings/2026-09-17-atlas-sprint-14-refinement.md",
    summary: [
      "Six items discussed. #142 (retry with backoff for provider timeouts) is code-complete and merged; Priya used the team's trigger phrase to ask for it to be closed, citing this morning's merge and staging verification. #156 (batched audit inserts) re-estimated from 3 to 5 points after Sofia flagged the SQLite locking behaviour; acceptance criteria updated to include a migration test, and Tom used the trigger phrase to assign it to Sofia. #128 (silent OAuth token refresh failure) reproduced by Tom; agreed it must log and notify, not swallow — moved up the board.",
      "#151 (GBP currency formatting) confirmed small; Dan takes it next. #139 (wizard transcript detection) demoed working against Meet and Teams fixtures; needs copy review only. #133 (RabbitMQ event bus spike) deferred to Sprint 15 — no capacity, and the in-memory bus is sufficient for the demo path.",
      "Items touched: #142, #156, #128, #151, #139. Deferred: #133."
    ]
  },

  candidatePool: {
    scope: "Team board items modified in the last 14 days",
    size: 84,
    threshold: 90,
    items: [
      { id: "#142", title: "Retry with backoff for provider timeouts", state: "In progress", confidence: 97 },
      { id: "#156", title: "Migrate audit writes to batched inserts", state: "In progress", confidence: 95 },
      { id: "#128", title: "OAuth token refresh fails silently", state: "To do", confidence: 93 },
      { id: "#151", title: "GBP currency formatting on the cost dashboard", state: "To do", confidence: 91 },
      { id: "#139", title: "Wizard: detect platform-supplied transcripts", state: "In review", confidence: 90 },
      { id: "#147", title: "Dark mode contrast pass on the Triage Inbox", state: "To do", confidence: 62 },
      { id: "#133", title: "Spike: RabbitMQ event bus provider", state: "To do", confidence: 34 },
      { id: "#160", title: "Contributor guide first draft", state: "To do", confidence: 11 }
    ]
  },

  commentTemplate: (item) =>
    `This item was discussed in “Atlas platform — Sprint 14 ticket refinement” on 17 Sep 2026 — see the meeting page in the knowledge vault for the summary and transcript.`,

  triggerSegments: [
    {
      id: "seg-close-142",
      speaker: "Priya Nair", time: "10:22",
      text: "“For the rubber duck: please close issue 142 — the retry policy merged this morning and it's verified in staging.”",
      action: "Close", item: "#142", confidence: 94
    },
    {
      id: "seg-assign-156",
      speaker: "Tom Okafor", time: "10:31",
      text: "“…and for the rubber duck, assign issue 156 to Sofia.”",
      action: "Assign", item: "#156", assignee: "Sofia Marchetti", confidence: 91
    }
  ],

  ambiguousSegments: [
    {
      id: "seg-garbled",
      speaker: "Dan Whittaker", time: "10:38",
      text: "“—the rubber d— [inaudible] one three—”",
      note: "Possible trigger phrase, but garbled and no item ID was heard in the same sentence. Per policy, nothing is guessed: this is flagged for a human, not acted on."
    }
  ],

  /* Per-action-type trust. mode: auto | review | disabled.
     approvals/rejections are the seeded history for the current 14-day window. */
  trustSeed: {
    comment:    { label: "Comment",    mode: "auto",   applied: 132, approvals: 0,  rejections: 0, note: "Low risk — automatic by default. No trigger phrase required." },
    close:      { label: "Close",      mode: "review", approvals: 11, rejections: 0, note: "Every proposed closure this window was approved." },
    transition: { label: "Transition", mode: "review", approvals: 7,  rejections: 2, note: "" },
    assign:     { label: "Assign",     mode: "review", approvals: 2,  rejections: 9, note: "Most proposed assignments this window were rejected." }
  },

  outbox: [
    {
      id: "out-1",
      title: "Comment on #151 failed",
      provider: "Work item provider · GitHub Issues",
      error: "403 rate limited — retry policy exhausted (3 attempts, exponential backoff)",
      when: "17 Sep 2026, 10:44",
      notified: "Email sent to the team address"
    }
  ],

  auditSeed: [
    { t: "10:41:12", event: "Content captured", detail: "Recording + platform transcript · Google Meet via Drive watch", by: "Meeting capture provider" },
    { t: "10:41:13", event: "Content type assigned", detail: "Ticket refinement · rule match on calendar title", by: "CKA core (rule)" },
    { t: "10:41:13", event: "Transcription skipped", detail: "Platform transcript supplied — no LLM call, ~£0.11 saved", by: "CKA core" },
    { t: "10:41:58", event: "Summarised", detail: "claude-sonnet-5 via LangChain4j · prompt: ticket-refinement v3", by: "LLM provider" },
    { t: "10:41:58", event: "Content summarised", detail: "Event published — the LLM provider never calls the knowledge provider directly", by: "Event bus · RabbitMQ" },
    { t: "10:41:59", event: "Knowledge stored", detail: "meetings/2026-09-17-atlas-sprint-14-refinement.md", by: "Knowledge provider · Markdown vault" },
    { t: "10:42:31", event: "Mentions detected", detail: "5 of 84 candidates ≥ 90% confidence", by: "Decision provider · Jev" },
    { t: "10:42:40", event: "Comments posted", detail: "#142 #156 #128 #139 · comment on #151 failed → Outbox", by: "Work item provider · GitHub Issues" },
    { t: "10:42:52", event: "Action proposed", detail: "Close #142 · confidence 94% → Triage Inbox (trust: review)", by: "Decision provider · Jev" },
    { t: "10:42:52", event: "Action proposed", detail: "Assign #156 to Sofia Marchetti · confidence 91% → Triage Inbox", by: "Decision provider · Jev" },
    { t: "10:42:53", event: "Ambiguity flagged", detail: "Possible garbled trigger phrase at 10:38 — no ID heard, not acted on", by: "CKA core" }
  ],

  costs: {
    period: "September 2026 to date · 14 meetings · 3 documents",
    currency: "GBP",
    totals: { predicted: 4.36, actual: 4.51, transcriptionSaved: 3.10 },
    perJob: [
      { job: "Summarisation", model: "claude-sonnet-5", predicted: 2.84, actual: 2.96 },
      { job: "Mention detection", model: "Jev decision model", predicted: 1.12, actual: 1.14 },
      { job: "Action decisions", model: "Jev decision model", predicted: 0.31, actual: 0.32 },
      { job: "Type classification", model: "Jev decision model", predicted: 0.09, actual: 0.09 },
      { job: "Transcription", model: "— skipped (platform transcripts)", predicted: 0.00, actual: 0.00 }
    ],
    perContentType: [
      { type: "Ticket refinement", spend: 1.61 },
      { type: "Stand-up", spend: 0.98 },
      { type: "Sprint planning", spend: 0.41 },
      { type: "Retrospective", spend: 0.24 },
      { type: "Decision record", spend: 0.18 },
      { type: "General", spend: 0.09 }
    ],
    whatIf: [
      { config: "claude-sonnet-5 (current)", est: 2.84, note: "current summarisation model" },
      { config: "claude-haiku-4-5", est: 0.87, note: "cheaper; shorter summaries in spot checks" },
      { config: "OpenAI gpt-4o-mini", est: 0.79, note: "requires org approval for this data" },
      { config: "Gemini 2.5 Flash", est: 0.82, note: "requires org approval for this data" },
      { config: "Ollama · llama3.1-8b (local)", est: 0.00, note: "no per-token cost — your own compute; quality untested here" }
    ]
  }
};
