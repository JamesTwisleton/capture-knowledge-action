/* Capture-Knowledge-Action prototype — navigation, wizard, inbox and
   trust-ramp state. Everything is in-memory and fabricated; "Reset demo"
   restores the seeded state. */

(function () {
  const D = window.CKA_DATA;
  const $view = document.getElementById("view");

  /* ---------------- state ---------------- */

  let S;

  function freshState() {
    const selections = {};
    D.providerCategories.forEach(c => {
      const def = c.options.find(o => o.default) || c.options[0];
      selections[c.id] = def.id;
    });
    return {
      wizard: {
        step: 0,
        maxVisited: 0,
        completed: false,
        selections,
        ack: false,
        connected: { google: false, github: false },
        trigger: "for the rubber duck",
        pool: "recent14",
        prompts: Object.fromEntries(D.contentTypes.map(t => [t.id, t.prompt]))
      },
      trust: JSON.parse(JSON.stringify(D.trustSeed)),
      proposals: [
        {
          id: "p-close-142", type: "close", item: "#142",
          itemTitle: "Retry with backoff for provider timeouts",
          verb: "Close #142",
          quote: D.triggerSegments[0].text,
          speaker: D.triggerSegments[0].speaker, time: D.triggerSegments[0].time,
          confidence: 94, status: "pending"
        },
        {
          id: "p-assign-156", type: "assign", item: "#156",
          itemTitle: "Migrate audit writes to batched inserts",
          verb: "Assign #156 to Sofia Marchetti",
          quote: D.triggerSegments[1].text,
          speaker: D.triggerSegments[1].speaker, time: D.triggerSegments[1].time,
          confidence: 91, status: "pending"
        }
      ],
      outbox: D.outbox.map(o => ({ ...o })),
      audit: D.auditSeed.map(a => ({ ...a })),
      dismissed: { closeSuggestion: false, assignSuggestion: false },
      replayDone: false
    };
  }

  function resetDemo() {
    S = freshState();
    toast("Demo reset to its seeded state");
    render();
  }

  /* ---------------- helpers ---------------- */

  function toast(msg) {
    const el = document.getElementById("toast");
    el.textContent = msg;
    el.classList.add("show");
    clearTimeout(el._t);
    el._t = setTimeout(() => el.classList.remove("show"), 2600);
  }

  function gbp(n) {
    return "£" + n.toFixed(2);
  }

  function audit(event, detail, by) {
    const t = new Date();
    const hh = String(t.getHours()).padStart(2, "0");
    const mm = String(t.getMinutes()).padStart(2, "0");
    const ss = String(t.getSeconds()).padStart(2, "0");
    S.audit.push({ t: `${hh}:${mm}:${ss}`, event, detail, by });
  }

  function pendingCount() {
    return S.proposals.filter(p => p.status === "pending").length;
  }

  function confBar(pct, threshold) {
    const low = threshold != null && pct < threshold;
    return `<span class="conf"><span class="track"><span class="fill${low ? " low" : ""}" style="width:${pct}%"></span></span><span class="val">${pct}%</span></span>`;
  }

  function attr(text) {
    return `<span class="provider-attr">${text}</span>`;
  }

  /* ---------------- routing ---------------- */

  const routes = {
    "": viewOverview,
    "overview": viewOverview,
    "wizard": viewWizard,
    "activity": viewActivity,
    "inbox": viewInbox,
    "costs": viewCosts
  };

  function currentRoute() {
    return (location.hash.replace(/^#\/?/, "") || "overview").split("?")[0];
  }

  function render() {
    const r = currentRoute();
    (routes[r] || viewOverview)();
    document.querySelectorAll("nav.tabs a").forEach(a => {
      a.classList.toggle("active", a.dataset.route === r);
    });
    const badge = document.getElementById("inbox-badge");
    const n = pendingCount();
    badge.textContent = n || "";
    badge.style.display = n ? "" : "none";
    window.scrollTo(0, 0);
  }

  window.addEventListener("hashchange", render);

  /* ---------------- Overview ---------------- */

  function viewOverview() {
    $view.innerHTML = `
      <div class="hero">
        <div class="kicker">Interactive prototype · fabricated data</div>
        <h1>Take control of your AI usage.</h1>
        <p class="tagline">Capture-Knowledge-Action turns meeting recordings and documentation into
        searchable knowledge and safe, auditable workflow actions — assembled from
        <strong>whatever providers you already have</strong>, with no vendor commitment.
        Use whatever AI you like — including your own.</p>
        <div class="cta-row">
          <a class="btn primary" href="#/wizard">Run the setup wizard (J4)</a>
          <a class="btn" href="#/activity">Skip to the configured demo workspace</a>
        </div>
      </div>

      <div class="warning-box">
        <div class="wtitle">⚠ DATA SENSITIVITY — READ BEFORE YOU SET ANYTHING UP</div>
        <p>This product sends meeting transcripts and documents to whichever LLM providers you
        configure. Transcripts routinely contain names, project details, commercial information and
        occasionally conversations about people. <strong>Before enabling any cloud LLM provider, confirm your
        organisation permits that data to be sent to that provider.</strong> The expected setup is your
        organisation's own contracted LLM; local self-hosted models are a first-class option and keep
        data inside your own infrastructure. The setup wizard requires this to be acknowledged before an
        LLM provider is enabled.</p>
      </div>

      <h2>This demo's stack</h2>
      <p class="lede small">Deliberately different from the Teams + Confluence + Jira example in the PRD,
      to show the framework is genuinely provider-agnostic. Every cell below is swappable.</p>
      <div class="stack-grid">
        ${Object.entries({
          "Meeting capture": D.demoStack.capture,
          "Knowledge": D.demoStack.knowledge,
          "Work items": D.demoStack.workItems,
          "LLM": D.demoStack.llm,
          "Decision": D.demoStack.decision,
          "Event bus": D.demoStack.eventBus,
          "Audit store": D.demoStack.audit,
          "Notifications": D.demoStack.notifications
        }).map(([k, v]) => `<div class="stack-cell"><div class="cat">${k}</div><div class="val">${v}</div></div>`).join("")}
      </div>

      <h2>The journeys in this prototype</h2>
      <div class="journey-cards">
        <a class="journey-card" href="#/wizard">
          <div class="jid">J4</div><div class="jname">Setup wizard</div>
          <p>Zero to a working pipeline: choose providers by capability, authorise with OAuth, set the trigger phrase and trust defaults.</p>
        </a>
        <a class="journey-card" href="#/activity">
          <div class="jid">J1</div><div class="jname">Golden path</div>
          <p>A refinement meeting flows through Capture → Knowledge → Action, with every step attributed and audited.</p>
        </a>
        <a class="journey-card" href="#/inbox">
          <div class="jid">J5</div><div class="jname">Triage Inbox &amp; trust ramp</div>
          <p>Approve or reject proposed actions. The system earns auto-approval per action type — or offers to switch itself off.</p>
        </a>
        <a class="journey-card" href="#/costs">
          <div class="jid">J7</div><div class="jname">Cost dashboard</div>
          <p>Spend per provider, model, job and content type — predicted next to actual, and what it would cost elsewhere.</p>
        </a>
      </div>
      <p class="flow-note" style="margin-top:14px">The stages are composable, not a fixed pipeline: Capture → Knowledge alone (J2)
      and Knowledge → Action alone (J3) are complete, valid configurations — not shown as separate screens here.</p>
    `;
  }

  /* ---------------- Wizard (J4) ---------------- */

  const WIZ_STEPS = [
    { id: 0, title: "Choose providers" },
    { id: 1, title: "Authorise" },
    { id: 2, title: "Capture set up for you" },
    { id: 3, title: "Content types & prompts" },
    { id: 4, title: "Trigger phrase" },
    { id: 5, title: "Candidate pool" },
    { id: 6, title: "Trust defaults" },
    { id: 7, title: "Audit store" },
    { id: 8, title: "Test run" }
  ];

  function llmIsCloud() {
    const sel = S.wizard.selections.llm;
    const opt = D.providerCategories.find(c => c.id === "llm").options.find(o => o.id === sel);
    return !!(opt && opt.cloud);
  }

  function llmName() {
    const sel = S.wizard.selections.llm;
    const opt = D.providerCategories.find(c => c.id === "llm").options.find(o => o.id === sel);
    return opt ? opt.name : null;
  }

  /* Decision fallback chain: Jev first; if an LLM provider is configured it is
     the fallback (constrained-decision mode via the gateway); with no LLM,
     Jev handles all decisions with no fallback. The user can also run
     decisions on their LLM directly. */
  function decisionNoteHTML() {
    const name = llmName();
    if (S.wizard.selections.decision === "jev") {
      return name
        ? `<p class="small" style="color:#4ec24e">✓ Fallback configured: if Jev is unavailable, decision calls fall back to <strong>${name}</strong> — your chosen LLM — in constrained-decision mode, and the switch is recorded in the audit trail.</p>`
        : `<p class="small muted">No LLM provider configured — Jev handles all decisions with no fallback.</p>`;
    }
    return `<p class="small dim2">Decisions will run on <strong>${name}</strong> in constrained-decision mode via the gateway. A general LLM's confidence is less calibrated than a dedicated decision model, so review thresholds apply more conservatively.</p>`;
  }

  function decisionAttr() {
    if (S.wizard.selections.decision === "llm") {
      return `Decision · ${llmName()} via gateway (constrained-decision mode)`;
    }
    const name = llmName();
    return `Decision · Jev (TypeSafe AI)${name ? ` · fallback: ${name}` : ""}`;
  }

  function wizCanNext() {
    const w = S.wizard;
    switch (w.step) {
      case 0: return !llmIsCloud() || w.ack;
      case 1: return w.connected.google && w.connected.github;
      default: return true;
    }
  }

  function wizNextHint() {
    const w = S.wizard;
    if (w.step === 0 && llmIsCloud() && !w.ack) return "Acknowledge the data-sensitivity warning to continue.";
    if (w.step === 1 && !(w.connected.google && w.connected.github)) return "Connect both accounts to continue.";
    return "";
  }

  function viewWizard() {
    const w = S.wizard;
    $view.innerHTML = `
      <div class="kicker">Journey J4</div>
      <h1>Setup wizard</h1>
      <p class="lede">From an empty install to a running pipeline, with as little manual admin work as
      possible. Every choice below has a sensible default and every default is overridable.</p>
      <div class="wizard" style="margin-top:24px">
        <aside class="wizard-rail"><ol>
          ${WIZ_STEPS.map(s => {
            const cls = s.id === w.step ? "current" : s.id <= w.maxVisited ? "done" : "locked";
            return `<li class="${cls}" data-step="${s.id}">${s.id + 1} · ${s.title}</li>`;
          }).join("")}
        </ol></aside>
        <section id="wiz-body"></section>
      </div>
    `;
    document.querySelectorAll(".wizard-rail li").forEach(li => {
      li.addEventListener("click", () => {
        const n = +li.dataset.step;
        if (n <= S.wizard.maxVisited) { S.wizard.step = n; viewWizard(); }
      });
    });
    renderWizStep();
  }

  function renderWizStep() {
    const w = S.wizard;
    const el = document.getElementById("wiz-body");
    const step = WIZ_STEPS[w.step];
    let body = "";

    if (w.step === 0) {
      body = D.providerCategories.map(cat => `
        <div class="opt-group">
          <h3>${cat.name} <span class="badge dim">${cat.role}</span></h3>
          <p class="small muted">${cat.blurb}</p>
          ${cat.options.map(o => `
            <label class="opt-row ${w.selections[cat.id] === o.id ? "selected" : ""}">
              <input type="radio" name="cat-${cat.id}" value="${o.id}" ${w.selections[cat.id] === o.id ? "checked" : ""}>
              <span><span class="opt-name">${o.name}</span>${o.note ? ` <span class="opt-note">— ${o.note}</span>` : ""}</span>
            </label>`).join("")}
          ${cat.id === "decision" ? decisionNoteHTML() : ""}
          ${cat.sensitive && w.selections[cat.id] && llmIsCloud() ? `
            <div class="warning-box">
              <div class="wtitle">⚠ DATA SENSITIVITY</div>
              <p>You have chosen a <strong>cloud LLM provider</strong>. Meeting transcripts and documents will be
              sent to it. Confirm your organisation permits this data to flow to this provider — the expected
              setup is your organisation's own contracted LLM. Choosing <em>Local models via Ollama</em> instead
              keeps all data inside your own infrastructure.</p>
              <label class="checkline"><input type="checkbox" id="wiz-ack" ${w.ack ? "checked" : ""}>
              <span>I confirm my organisation permits meeting transcripts and documents to be sent to this provider.</span></label>
            </div>` : cat.sensitive ? `<p class="small" style="color:#4ec24e">✓ Local model selected — data never leaves your infrastructure. No acknowledgement needed.</p>` : ""}
        </div>`).join("");
    }

    if (w.step === 1) {
      const rows = [
        { key: "google", name: "Google", scopes: "Drive (watch the recordings folder) · Meet transcripts · read-only calendar titles for content-type rules" },
        { key: "github", name: "GitHub", scopes: "Issues on atlas-platform (read, comment, edit) — nothing else" }
      ];
      body = `
        <p class="lede small">CKA uses OAuth — “log in with…” — so it can perform setup on your behalf. It
        requests only the scopes it needs, and never sees your password.</p>
        ${rows.map(r => `
          <div class="card"><div class="oauth-row">
            <div><strong>${r.name}</strong><div class="small muted">${r.scopes}</div></div>
            ${w.connected[r.key]
              ? `<span class="badge good">✓ Connected as demo-user</span>`
              : `<button class="btn primary" data-connect="${r.key}">Connect ${r.name}</button>`}
          </div></div>`).join("")}
      `;
    }

    if (w.step === 2) {
      body = `
        <p class="lede small">Based on your choices, CKA has configured capture for you — no admin console required.</p>
        <div class="card">
          <p>✓ Watching the Drive folder <span class="mono">Meet Recordings/Atlas team</span> for new recordings.</p>
          <p style="margin-top:8px">✓ <strong>Google Meet supplies its own transcripts</strong> — CKA detected this, so the
          transcription step will be skipped entirely. That removes an LLM call and its cost from every meeting.</p>
          <p style="margin-top:8px" class="dim2 small">Where a platform's API can't be set up automatically, the wizard
          generates precise step-by-step instructions for a human instead.</p>
        </div>`;
    }

    if (w.step === 3) {
      body = `
        <p class="lede small">Every captured item gets a <strong>content type</strong>, and each type has its own
        summary prompt — a stand-up is summarised very differently from a refinement session. Prompts are plain
        text, editable here, and versioned so changes can be reviewed and rolled back. Add your own types without code.</p>
        ${D.contentTypes.map(t => `
          <details class="prompt-item">
            <summary><strong>${t.name}</strong> <span class="badge dim">${t.family}</span></summary>
            <div class="pbody">
              <textarea data-prompt="${t.id}">${w.prompts[t.id]}</textarea>
              <p class="prompt-rule"><strong>Assignment rule:</strong> ${t.rule}</p>
            </div>
          </details>`).join("")}
        <p class="small muted" style="margin-top:10px">A type can also be assigned manually after capture, or classified by
        Jev. A wrong type produces a summary shaped for the wrong kind of content — never a wrong action.</p>`;
    }

    if (w.step === 4) {
      body = `
        <p class="lede small">The trigger phrase is the team's <strong>wake word for actions</strong>. It is required for
        anything that changes state, and must be said with an explicit item ID in the same sentence. Passive mentions
        never need it — they only ever produce comments.</p>
        <div class="card">
          <label class="small muted" for="wiz-trigger">Team trigger phrase</label><br>
          <input class="text" id="wiz-trigger" value="${w.trigger}" style="margin-top:6px">
          <p class="small dim2" style="margin-top:12px">Example: <em>“For the rubber duck: please close issue 142.”</em></p>
        </div>
        <div class="warning-box">
          <div class="wtitle">GUIDANCE</div>
          <p>Choose something distinctive that will survive imperfect audio, and always say the item ID in the
          same sentence. A garbled phrase may not trigger — ambiguous segments go to a review list rather than
          being guessed at. “Close it” with no ID is deliberately ignored: the system does not do everything for you.</p>
        </div>`;
    }

    if (w.step === 5) {
      const pools = [
        { id: "sprint", name: "Current sprint", note: "smallest pool, highest precision" },
        { id: "recent14", name: "Team board items modified in the last 14 days", note: "recommended default · currently 84 items" },
        { id: "open", name: "All open items in the project", note: "widest coverage, weakest confidences" }
      ];
      body = `
        <p class="lede small">The candidate pool is the set of work items Jev classifies against. It is scoped
        <em>before</em> classification so confidences stay meaningful and costs stay low.</p>
        ${pools.map(p => `
          <label class="opt-row ${w.pool === p.id ? "selected" : ""}">
            <input type="radio" name="wiz-pool" value="${p.id}" ${w.pool === p.id ? "checked" : ""}>
            <span><span class="opt-name">${p.name}</span> <span class="opt-note">— ${p.note}</span></span>
          </label>`).join("")}
        <div class="warning-box"><div class="wtitle">KNOWN LIMITATION</div>
        <p>Items outside the pool will not be detected. This is a deliberate trade for precision and cost.</p></div>`;
    }

    if (w.step === 6) {
      body = `
        <p class="lede small">Trust is set <strong>per action type, never globally</strong>. Mutating actions start in
        review; the system earns auto-approval by proving itself against your decisions (see the Triage Inbox).</p>
        <div class="table-scroll"><table class="data">
          <tr><th>Action type</th><th>Risk</th><th>Default</th></tr>
          <tr><td><strong>Comment</strong></td><td>Low — adds information, changes nothing</td><td><span class="badge good">Automatic</span></td></tr>
          <tr><td><strong>Transition</strong></td><td>Changes state</td><td><span class="badge blue">Human review</span></td></tr>
          <tr><td><strong>Close</strong></td><td>Changes state</td><td><span class="badge blue">Human review</span></td></tr>
          <tr><td><strong>Assign</strong></td><td>Changes state</td><td><span class="badge blue">Human review</span></td></tr>
        </table></div>
        <p class="small muted">Each can be tuned now or later. Auto-approval is something the system asks for
        once its approval rate has stayed high — and it will just as readily suggest disabling an action type
        that keeps getting rejected.</p>`;
    }

    if (w.step === 7) {
      body = `
        <p class="lede small">Every action, decision, approval and failure is recorded in one authoritative
        place that doesn't depend on any third-party platform being available.</p>
        <label class="opt-row selected"><input type="radio" checked>
          <span><span class="opt-name">SQLite</span> <span class="opt-note">— zero-setup, single-file database · configured, nothing to do</span></span></label>
        <label class="opt-row"><input type="radio" disabled>
          <span><span class="opt-name">Postgres / MySQL / any SQL database</span> <span class="opt-note">— substitute by configuration</span></span></label>`;
    }

    if (w.step === 8) {
      body = `
        <p class="lede small">Your stack is configured. Run one capture end to end and watch it flow
        through to the Triage Inbox.</p>
        <div class="stack-grid" style="margin-bottom:18px">
          ${D.providerCategories.map(cat => {
            const o = cat.options.find(x => x.id === S.wizard.selections[cat.id]);
            return `<div class="stack-cell"><div class="cat">${cat.name}</div><div class="val">${o ? o.name : "—"}</div></div>`;
          }).join("")}
        </div>
        <button class="btn primary" id="wiz-testrun">▶ Run test capture</button>
        <p class="small muted" style="margin-top:10px">This replays a fabricated 41-minute refinement meeting through
        the whole pipeline.</p>`;
    }

    el.innerHTML = `
      <div class="wizard-step-head"><span class="stepnum">Step ${w.step + 1} of ${WIZ_STEPS.length}</span><h2 style="margin:0">${step.title}</h2></div>
      ${body}
      <div class="wizard-nav">
        <button class="btn ghost" id="wiz-back" ${w.step === 0 ? "disabled" : ""}>← Back</button>
        ${w.step < WIZ_STEPS.length - 1
          ? `<button class="btn primary" id="wiz-next" ${wizCanNext() ? "" : "disabled"}>Continue →</button>`
          : ""}
        <span class="hint">${wizNextHint()}</span>
      </div>
    `;

    /* wire events */
    el.querySelectorAll("input[type=radio][name^=cat-]").forEach(r => {
      r.addEventListener("change", () => {
        const catId = r.name.slice(4);
        S.wizard.selections[catId] = r.value;
        if (catId === "llm") S.wizard.ack = false;
        renderWizStep();
      });
    });
    const ack = el.querySelector("#wiz-ack");
    if (ack) ack.addEventListener("change", () => { S.wizard.ack = ack.checked; renderWizStep(); });
    el.querySelectorAll("[data-connect]").forEach(b => {
      b.addEventListener("click", () => {
        S.wizard.connected[b.dataset.connect] = true;
        toast(`Connected via OAuth — only the listed scopes were requested`);
        renderWizStep();
      });
    });
    el.querySelectorAll("textarea[data-prompt]").forEach(t => {
      t.addEventListener("input", () => { S.wizard.prompts[t.dataset.prompt] = t.value; });
    });
    const trig = el.querySelector("#wiz-trigger");
    if (trig) trig.addEventListener("input", () => { S.wizard.trigger = trig.value; });
    el.querySelectorAll("input[name=wiz-pool]").forEach(r => {
      r.addEventListener("change", () => { S.wizard.pool = r.value; renderWizStep(); });
    });
    const back = el.querySelector("#wiz-back");
    if (back) back.addEventListener("click", () => { S.wizard.step--; viewWizard(); });
    const next = el.querySelector("#wiz-next");
    if (next) next.addEventListener("click", () => {
      S.wizard.step++;
      S.wizard.maxVisited = Math.max(S.wizard.maxVisited, S.wizard.step);
      viewWizard();
    });
    const run = el.querySelector("#wiz-testrun");
    if (run) run.addEventListener("click", () => {
      S.wizard.completed = true;
      S.replayDone = false;
      toast("Test capture started — watch it flow through the pipeline");
      location.hash = "#/activity";
    });
  }

  /* ---------------- Activity (J1) ---------------- */

  function vaultNoteHTML() {
    const m = D.meeting;
    return `<div class="vault-note">
<span class="fm">---<br>
type: ticket-refinement<br>
date: 2026-09-17<br>
attendees: [${m.attendees.join(", ")}]<br>
tags: [meeting, sprint-14, atlas]<br>
recording: drive://meet-recordings/atlas/2026-09-17 (demo link)<br>
---</span><br>
<span class="md-h"># ${m.title}</span><br><br>
Items discussed: <span class="wikilink">[[issue-142]]</span> · <span class="wikilink">[[issue-156]]</span> · <span class="wikilink">[[issue-128]]</span> · <span class="wikilink">[[issue-151]]</span> · <span class="wikilink">[[issue-139]]</span> — deferred: <span class="wikilink">[[issue-133]]</span><br><br>
<span class="md-h">## Summary</span><br>
${m.summary.map(p => p + "<br><br>").join("")}
<span class="md-h">## Transcript</span><br>
Full platform transcript stored below this section (elided in this prototype).
</div>`;
  }

  function viewActivity() {
    const m = D.meeting;
    const pool = D.candidatePool;
    const steps = [
      {
        title: "Content captured", by: "Meeting capture · Google Meet (Drive folder watch)",
        html: `<p class="small dim2">${m.title} · ${m.date} · ${m.duration} · ${m.attendees.length} attendees.
        The recording landed in the watched Drive folder, <strong>with the platform's own transcript attached</strong>.</p>`
      },
      {
        title: "Content type assigned — Ticket refinement", by: "CKA core (rule)",
        html: `<p class="small dim2">${m.typeAssignedBy}. The type selects the summary prompt and shapes what the
        Action stage looks for. It could also have been chosen manually or classified by Jev.</p>`
      },
      {
        title: "Transcription skipped", by: "CKA core", skipped: true,
        html: `<p class="small dim2">Google Meet supplied its own transcript, so no LLM transcription call was made —
        <strong>~£0.11 saved on this meeting</strong>. Platforms without transcripts would use the configured
        speech-to-text provider here instead.</p>`
      },
      {
        title: "Summarised with the ticket-refinement prompt", by: "LLM · claude-sonnet-5 via LangChain4j · prompt v3",
        html: `<details><summary class="small" style="cursor:pointer;color:var(--series-1)">Show summary</summary>
          <div class="card sub" style="margin-top:10px">${m.summary.map(p => `<p class="small dim2" style="margin-bottom:8px">${p}</p>`).join("")}</div>
        </details>`
      },
      {
        title: "Stored in the knowledge vault", by: "Knowledge · Local Markdown vault (Obsidian-compatible)",
        html: `<p class="small dim2">Written as <span class="mono">${m.vaultPath}</span> — plain Markdown with
        wiki-links, readable by any editor, by Obsidian, and directly by any LLM.</p>
        <details><summary class="small" style="cursor:pointer;color:var(--series-1)">Show the stored note</summary>
        <div style="margin-top:10px">${vaultNoteHTML()}</div></details>`
      },
      {
        title: "Mention detection over the scoped candidate pool", by: decisionAttr(),
        html: `<p class="small dim2">Pool: ${pool.scope.toLowerCase()} — ${pool.size} items. Jev returns a calibrated
        confidence per candidate; the threshold is ${pool.threshold}%. This replaces brittle exact-text matching and
        tolerates mistranscribed item IDs.</p>
        <div class="table-scroll"><table class="data">
          <tr><th>Item</th><th>Title</th><th>Mentioned?</th><th></th></tr>
          ${pool.items.map(i => `<tr>
            <td class="mono">${i.id}</td><td>${i.title}</td>
            <td>${confBar(i.confidence, pool.threshold)}</td>
            <td>${i.confidence >= pool.threshold ? '<span class="badge good">comment</span>' : '<span class="badge dim">ignored</span>'}</td>
          </tr>`).join("")}
        </table></div>
        <p class="small muted">${pool.size - pool.items.length} further candidates below 10% not shown. Items outside
        the pool are not detected — a documented trade for precision and cost.</p>`
      },
      {
        title: "Comments posted on mentioned items", by: "Work items · GitHub Issues",
        html: `<p class="small dim2">Commenting is low-risk, so no trigger phrase is needed and the trust default is
        <em>automatic</em>. Posted on <span class="mono">#142 #156 #128 #139</span>:</p>
        <blockquote class="small dim2" style="border-left:2px solid var(--hairline);padding:4px 12px;margin:8px 0;font-style:italic">
        “${D.commentTemplate()}”</blockquote>
        <p class="small dim2">The comment on <span class="mono">#151</span> hit a rate limit and exhausted its
        retries — it's in the <a href="#/inbox">Outbox</a> with a notification sent. Nothing stalls silently. (J6)</p>`
      },
      {
        title: "Trigger phrase heard — actions proposed", by: decisionAttr(),
        html: `<p class="small dim2">Two segments used the team phrase <em>“${S.wizard.trigger}”</em> with an explicit
        item ID, so they were classified for action intent. <em>Close</em> and <em>assign</em> are in
        <strong>review</strong>, so both proposals went to the <a href="#/inbox">Triage Inbox</a> rather than being
        applied.</p>
        ${D.triggerSegments.map(s => `<blockquote class="small dim2" style="border-left:2px solid var(--hairline);padding:4px 12px;margin:8px 0;font-style:italic">${s.text} <span class="muted">— ${s.speaker}, ${s.time} · ${s.action} ${s.item} · confidence ${s.confidence}%</span></blockquote>`).join("")}
        <p class="small dim2">One further segment was flagged as a <em>possible</em> garbled trigger phrase with no
        item ID — per policy it was <strong>not guessed at</strong> and waits in the Inbox's ambiguity list.</p>`
      }
    ];

    $view.innerHTML = `
      <div class="kicker">Journey J1 · golden path</div>
      <h1>Activity</h1>
      <p class="lede">One captured meeting, followed through Capture → Knowledge → Action. Every step names
      the provider and model that performed it, and every step is written to the audit service.</p>
      <div class="card" style="display:flex;gap:14px;align-items:center;flex-wrap:wrap">
        <div style="flex:1;min-width:220px"><strong>${m.title}</strong>
          <div class="small muted">${m.platform} · ${m.date} · ${m.duration} · platform transcript ✓</div></div>
        <span class="badge blue">${m.contentType}</span>
        <button class="btn small" id="replay">▶ Replay pipeline</button>
      </div>
      <div class="pipeline" id="pipeline">
        ${steps.map((s, i) => `
          <div class="pipe-step${s.skipped ? " skipped" : ""}" data-i="${i}">
            <div class="pipe-rail"><div class="pipe-dot"></div>${i < steps.length - 1 ? '<div class="pipe-line"></div>' : ""}</div>
            <div class="card pipe-card">
              <div class="pipe-head">
                <span class="pipe-title">${s.title}</span>
                ${s.skipped ? '<span class="badge dim">skipped</span>' : ""}
                <span class="audited badge dim" title="Written to the audit service">audited ✓</span>
              </div>
              ${attr(s.by)}
              <div style="margin-top:8px">${s.html}</div>
            </div>
          </div>`).join("")}
      </div>
      <h2>Audit trail</h2>
      <p class="small muted">From the audit service (SQLite in this demo) — the basis for the trust ramp, this
      activity view, and “why did it do that?” investigations.</p>
      <div class="card audit-log" id="audit-log">
        ${S.audit.map(a => `<div><span class="t">${a.t}</span><span class="e">${a.event}</span><span class="d">${a.detail}</span> <span class="by">· ${a.by}</span></div>`).join("")}
      </div>
    `;

    document.getElementById("replay").addEventListener("click", () => {
      const nodes = [...document.querySelectorAll(".pipe-step")];
      nodes.forEach(n => n.classList.add("pending"));
      nodes.forEach((n, i) => setTimeout(() => n.classList.remove("pending"), 350 + i * 650));
      setTimeout(() => toast("Pipeline complete — two proposals are waiting in the Triage Inbox"), 350 + nodes.length * 650);
    });

    if (!S.replayDone) {
      S.replayDone = true;
      document.getElementById("replay").click();
    }
  }

  /* ---------------- Inbox (J5) ---------------- */

  function trustPct(t) {
    const total = t.approvals + t.rejections;
    return total ? Math.round((t.approvals / total) * 100) : null;
  }

  function viewInbox() {
    const t = S.trust;
    const suggestions = [];

    if (t.close.mode === "review" && t.close.approvals >= 12 && t.close.rejections === 0 && !S.dismissed.closeSuggestion) {
      suggestions.push(`
        <div class="suggestion">
          <div class="stitle">You've accepted every proposed closure for two weeks (${t.close.approvals}/${t.close.approvals}).
          Switch closures to auto-approve?</div>
          <p>Auto-approved actions still pass every gate — trigger phrase, explicit ID, confidence threshold — and
          remain fully audited. You can revert at any time, and CKA will drop back to review by itself if accuracy falls.</p>
          <div class="btn-row">
            <button class="btn good" data-suggest="close-auto">Switch Close to auto-approve</button>
            <button class="btn" data-suggest="close-later">Not yet</button>
          </div>
        </div>`);
    }
    if (t.assign.mode === "review" && !S.dismissed.assignSuggestion) {
      suggestions.push(`
        <div class="suggestion negative">
          <div class="stitle">You've rejected most proposed Assign actions this month (${t.assign.rejections} of ${t.assign.approvals + t.assign.rejections}).</div>
          <p>This is costing you money in LLM calls for proposals you don't want. Disable this action type, or switch
          the provider or model making these decisions? Every proposal shows who produced it, so a bad provider is
          attributable — not just “the app got it wrong”.</p>
          <div class="btn-row">
            <button class="btn danger" data-suggest="assign-disable">Disable Assign proposals</button>
            <button class="btn" data-suggest="assign-switch">Switch provider / model…</button>
            <button class="btn ghost" data-suggest="assign-keep">Keep as is</button>
          </div>
        </div>`);
    }

    const pending = S.proposals.filter(p => p.status === "pending");
    const resolved = S.proposals.filter(p => p.status !== "pending");

    $view.innerHTML = `
      <div class="kicker">Journey J5</div>
      <h1>Triage Inbox</h1>
      <p class="lede">Proposed mutating actions wait here for a human. Your approvals and rejections are what the
      trust ramp is built from — per action type, never globally.</p>
      ${suggestions.join("")}

      <h2>Pending proposals ${pending.length ? `<span class="badge warn">${pending.length}</span>` : ""}</h2>
      ${pending.length === 0 ? `<p class="muted small">Nothing waiting. New proposals arrive when a meeting uses the trigger phrase.</p>` : ""}
      ${pending.map(proposalCard).join("")}

      ${resolved.length ? `<h3>Decided just now</h3>${resolved.map(proposalCard).join("")}` : ""}

      <h2>Possible trigger phrases <span class="badge dim">not acted on</span></h2>
      ${D.ambiguousSegments.map(s => `
        <div class="card">
          <blockquote style="border-left:2px solid var(--hairline);padding:4px 12px;font-style:italic" class="dim2 small">${s.text}
          <span class="muted">— ${s.speaker}, ${s.time}</span></blockquote>
          <p class="small muted">${s.note}</p>
        </div>`).join("")}

      <h2>Outbox <span class="badge ${S.outbox.length ? "crit" : "dim"}">${S.outbox.length} failed</span></h2>
      ${S.outbox.length === 0 ? `<p class="muted small">No failed actions.</p>` : ""}
      ${S.outbox.map(o => `
        <div class="card" style="border-left:4px solid var(--critical)">
          <div class="p-head"><strong>${o.title}</strong> <span class="badge crit">failed</span></div>
          ${attr(o.provider)}
          <p class="small dim2" style="margin:8px 0">${o.error} · ${o.when} · ${o.notified}.</p>
          <div class="p-actions">
            <button class="btn small" data-outbox-retry="${o.id}">Retry now</button>
            <button class="btn small" data-outbox-switch="${o.id}">Switch provider / model…</button>
            <button class="btn small ghost" data-outbox-dismiss="${o.id}">Dismiss</button>
          </div>
        </div>`).join("")}

      <h2>Trust per action type</h2>
      <div class="trust-grid">
        ${Object.entries(t).map(([key, v]) => {
          const pct = trustPct(v);
          const modeBadge = v.mode === "auto" ? '<span class="badge good">auto-approve</span>'
            : v.mode === "disabled" ? '<span class="badge dim">disabled</span>'
            : '<span class="badge blue">review</span>';
          return `<div class="trust-cell">
            <div class="t-head"><span class="t-name">${v.label}</span>${modeBadge}</div>
            ${v.applied != null
              ? `<div class="t-stats">${v.applied} applied automatically this window</div>`
              : `<div class="t-stats">${v.approvals} approved · ${v.rejections} rejected · 14-day window</div>
                 <div class="trust-meter"><span class="fill${pct != null && pct < 50 ? " bad" : ""}" style="width:${pct == null ? 0 : pct}%;display:block"></span></div>`}
            ${v.note ? `<div class="t-note">${v.note}</div>` : ""}
          </div>`;
        }).join("")}
      </div>
      <p class="small muted" style="margin-top:10px">A team can fully trust “comment” while never auto-approving
      “close”. Auto-approval is earned from this history — and reverting is one click.</p>
    `;

    /* proposal actions */
    document.querySelectorAll("[data-approve]").forEach(b => b.addEventListener("click", () => decide(b.dataset.approve, true)));
    document.querySelectorAll("[data-reject]").forEach(b => b.addEventListener("click", () => decide(b.dataset.reject, false)));

    /* suggestion actions */
    document.querySelectorAll("[data-suggest]").forEach(b => b.addEventListener("click", () => {
      const a = b.dataset.suggest;
      if (a === "close-auto") { S.trust.close.mode = "auto"; S.trust.close.note = "Auto-approval earned on " + new Date().toLocaleDateString("en-GB") + " — still fully audited."; audit("Trust changed", "Close switched to auto-approve by demo-user", "Human decision"); toast("Close is now auto-approved — still fully audited"); }
      if (a === "close-later") { S.dismissed.closeSuggestion = true; }
      if (a === "assign-disable") { S.trust.assign.mode = "disabled"; S.trust.assign.note = "Disabled by demo-user — no more Assign proposals or their LLM costs."; audit("Trust changed", "Assign proposals disabled by demo-user", "Human decision"); toast("Assign proposals disabled — no more spend on them"); }
      if (a === "assign-switch") { toast("In the full product this opens the provider picker for the decision layer"); return; }
      if (a === "assign-keep") { S.dismissed.assignSuggestion = true; }
      viewInbox(); renderBadge();
    }));

    /* outbox actions */
    document.querySelectorAll("[data-outbox-retry]").forEach(b => b.addEventListener("click", () => {
      S.outbox = S.outbox.filter(o => o.id !== b.dataset.outboxRetry);
      audit("Outbox retry", "Comment on #151 retried — succeeded", "Human decision → Work item provider");
      toast("Retried — the comment on #151 posted successfully ✓");
      viewInbox();
    }));
    document.querySelectorAll("[data-outbox-switch]").forEach(b => b.addEventListener("click", () =>
      toast("In the full product this opens the provider picker")));
    document.querySelectorAll("[data-outbox-dismiss]").forEach(b => b.addEventListener("click", () => {
      S.outbox = S.outbox.filter(o => o.id !== b.dataset.outboxDismiss);
      audit("Outbox dismissed", "Comment on #151 dismissed", "Human decision");
      viewInbox();
    }));
  }

  function proposalCard(p) {
    const cls = p.status === "approved" ? "resolved-approved" : p.status === "rejected" ? "resolved-rejected" : "";
    return `<div class="card proposal ${cls}">
      <div class="p-head">
        <span class="p-title">${S.wizard.selections.decision === "llm" ? llmName() : "Jev"} proposes: ${p.verb}</span>
        <span class="badge blue">confidence ${p.confidence}%</span>
        <span class="badge dim">${p.type}</span>
      </div>
      <div class="p-meta">${p.item} — ${p.itemTitle}</div>
      <blockquote>${p.quote} <span class="muted">— ${p.speaker}, ${p.time}</span></blockquote>
      <div class="p-meta">Proposed by ${attr(decisionAttr())} from ${attr("transcript · Google Meet")} · gates passed: trigger phrase ✓ · explicit ID ✓ · confidence ✓</div>
      ${p.status === "pending"
        ? `<div class="p-actions">
             <button class="btn good small" data-approve="${p.id}">✓ Approve</button>
             <button class="btn danger small" data-reject="${p.id}">✕ Reject</button>
           </div>`
        : p.status === "approved"
          ? `<div class="outcome ok">✓ Approved — applied on GitHub Issues and audited</div>`
          : `<div class="outcome no">✕ Rejected — nothing applied; the rejection feeds this action type's trust score</div>`}
    </div>`;
  }

  function decide(id, approved) {
    const p = S.proposals.find(x => x.id === id);
    if (!p || p.status !== "pending") return;
    p.status = approved ? "approved" : "rejected";
    const t = S.trust[p.type];
    if (t) approved ? t.approvals++ : t.rejections++;
    audit(approved ? "Action approved" : "Action rejected",
      `${p.verb} · confidence ${p.confidence}% · decided by demo-user`,
      approved ? "Human decision → Work item provider · GitHub Issues" : "Human decision");
    if (approved) toast(`${p.verb} — applied and audited`);
    else toast(`${p.verb} — rejected; feeds the trust score for “${p.type}”`);
    viewInbox();
    renderBadge();
  }

  function renderBadge() {
    const badge = document.getElementById("inbox-badge");
    const n = pendingCount();
    badge.textContent = n || "";
    badge.style.display = n ? "" : "none";
  }

  /* ---------------- Costs (J7) ---------------- */

  function barRows(rows, max, opts = {}) {
    return rows.map(r => `
      <div class="bar-row${opts.pair ? " pair" : ""}">
        <span class="b-label">${r.label}${r.sub ? `<span class="b-sub">${r.sub}</span>` : ""}</span>
        <span class="b-track">
          ${opts.pair
            ? `<span class="b-fill predicted" style="width:${(r.predicted / max) * 100}%"></span>
               <span class="b-fill actual" style="width:${(r.actual / max) * 100}%"></span>`
            : `<span class="b-fill${r.value === 0 ? " zero" : ""}" style="width:${(r.value / max) * 100}%"></span>`}
        </span>
        <span class="b-val">${opts.pair ? `${gbp(r.predicted)} / ${gbp(r.actual)}` : r.value === 0 ? "£0.00" : gbp(r.value)}</span>
      </div>`).join("");
  }

  function viewCosts() {
    const c = D.costs;
    const maxJob = Math.max(...c.perJob.map(j => Math.max(j.predicted, j.actual)));
    const maxType = Math.max(...c.perContentType.map(x => x.spend));
    const delta = ((c.totals.actual - c.totals.predicted) / c.totals.predicted * 100).toFixed(1);

    $view.innerHTML = `
      <div class="kicker">Journey J7</div>
      <h1>Cost &amp; provider dashboard</h1>
      <p class="lede">What each provider and model is costing you, what the prediction got right, and what the
      same work would cost elsewhere. ${c.period}.</p>

      <div class="tile-row">
        <div class="tile"><div class="t-label">Predicted spend</div><div class="t-value">${gbp(c.totals.predicted)}</div><div class="t-sub">token estimates × current pricing, via the LLM gateway</div></div>
        <div class="tile"><div class="t-label">Actual metered spend</div><div class="t-value">${gbp(c.totals.actual)}</div><div class="t-sub">from linked provider billing · prediction off by ${delta}%</div></div>
        <div class="tile"><div class="t-label">Transcription saved</div><div class="t-value">${gbp(c.totals.transcriptionSaved)}</div><div class="t-sub good">platform transcripts used — 14 of 14 meetings skipped the LLM call</div></div>
      </div>

      <div class="card">
        <h3 style="margin-top:0">Spend per job — predicted vs actual</h3>
        <div class="legend">
          <span><span class="sw" style="background:var(--series-1)"></span>Predicted</span>
          <span><span class="sw" style="background:var(--series-2)"></span>Actual (metered)</span>
        </div>
        <div class="chart">
          ${barRows(c.perJob.map(j => ({ label: j.job, sub: j.model, predicted: j.predicted, actual: j.actual })), maxJob, { pair: true })}
        </div>
        <p class="ballpark">Each job can use a different provider and model independently — they have very
        different cost and complexity profiles.</p>
      </div>

      <div class="card">
        <h3 style="margin-top:0">Summarisation spend per content type</h3>
        <div class="chart">
          ${barRows(c.perContentType.map(x => ({ label: x.type, value: x.spend })), maxType)}
        </div>
        <p class="ballpark">Prompts are per content type, so spend is attributable per type too.</p>
      </div>

      <div class="card">
        <h3 style="margin-top:0">What if? — the same summarisation workload elsewhere</h3>
        <div class="table-scroll"><table class="data">
          <tr><th>Model</th><th class="num">Estimated cost</th><th>Note</th></tr>
          ${c.whatIf.map(w => `<tr>
            <td>${w.config}</td><td class="num">${w.est === 0 ? "£0.00*" : gbp(w.est)}</td>
            <td class="small muted">${w.note}</td></tr>`).join("")}
        </table></div>
        <p class="ballpark">* No per-token cost; runs on your own compute. Switching provider is configuration,
        not a rewrite.</p>
      </div>

      <p class="ballpark">All figures are <strong>ballpark and fabricated for this prototype</strong>: token usage is hard to
      predict precisely, which is why predicted and actual are shown side by side — the accuracy of the
      prediction is itself visible over time. Currency is configurable; GBP shown.</p>
    `;
  }

  /* ---------------- boot ---------------- */

  document.getElementById("reset-demo").addEventListener("click", resetDemo);
  S = freshState();
  render();
})();
