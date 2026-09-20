[← Back to the CKA PRD](../README.md)

# CKA MVP: demo script

Source: narrated by James on 19 September 2026, cleaned up and structured for delivery. Written to be read aloud and adapted in the moment, not memorised word for word.

This script is the deliverable of [`mvp-tickets.md`](mvp-tickets.md) T21 ([#21](https://github.com/JamesTwisleton/capture-knowledge-action/issues/21)), and it **doubles as the MVP test plan** — every beat below is something the built system has to actually do. The [beat checklist](#beat-checklist-t21) at the end maps the narration onto T21's ten beats so it can be used as a pass/fail list, not just a voiceover.

> [!NOTE]
> This page is delivery material, not a numbered PRD section, so editing it does not require a PRD version bump. Section 14 of the PRD describes the recording; this is the script for it.

---

## 1. Opening overview

Today I'm going to demonstrate how Capture-Knowledge-Action, or CKA, can work for your organisation.

Capture-Knowledge-Action allows you to turn meetings, unstructured documentation, any kind of content that's part of your system, into knowledge. That knowledge is stored in a canonical, standardised format that works well in the AI era. And that knowledge can then be turned into actions.

Those actions could be a mention of a ticket during a meeting ending up as a comment on that ticket, or they could be changing a ticket's status, or reassigning it. These are examples: it can be any action you want to configure. And the key thing is that this happens safely, with a human in the loop. Status-changing actions are triaged by a human, and trust is earned over time before the system is ever allowed to act automatically.

There are already pipelines out there that do something like this, that take meeting notes, store them canonically, and turn them into agentic actions. But every existing solution is tightly bound to one vendor's ecosystem. If you're using Atlassian, you pay a hefty fee and Atlassian gives you this functionality, on Atlassian's terms. Change your meeting provider, your knowledge store, or your work item provider, and you're stuck: that's lock-in, you're paying for a managed solution from one provider.

The key differentiator CKA offers is composability and configuration. Want Teams for your meeting recordings, Confluence for your knowledge store, and GitHub for your work item provider? That's entirely possible. Want to use this LLM, another one, or your own self-hosted model? That's your choice too. CKA lets you compose the solution that actually works for your organisation, and it's open source, so you can shape it however works best for you.

Let me show you an example.

## 2. Demo walkthrough

### Capture

Here we have a Google Meet meeting summary and transcript, produced automatically by Google Meet.

Something important to note here, and this is important across the whole project, is that you can use any meeting provider, or you could simply have existing documentation, or unstructured notes, any kind of knowledge that can be captured. That's the capture part of Capture-Knowledge-Action.

I'm going to drop that recording, with its transcript and summary, into the folder CKA is watching.

### Setup: capture source, work item provider, trigger phrase

When you set CKA up, you configure your capture source: that could be Google Meet, Teams recordings, or simply a folder containing documents.

You also set up your work item provider: Jira, GitHub, Azure DevOps, whichever you use. As part of that, you set your **candidate pool** — the tickets that are actually in scope and might get mentioned during a meeting.

And you set a **trigger phrase**, which you can think of as a wake word: the term that tells CKA an instruction is deliberate. For example, "for the rubber duck" could be your trigger phrase. So in a meeting, someone might say, "for the rubber duck, please close ticket one-two-three," or "for the rubber duck, please assign ticket four-five-six to John." That's what the decision service picks up later.

### Content listener to LLM provider to knowledge provider

When a new piece of content lands in your capture source, this is noticed by the CKA **content listener**. For this example, we're using a simple folder watcher, one implementation of the content listener, polling the folder. It could equally be set up with a webhook. You can see it here, polling — and there, it's found something.

Once the content listener sees new content, it emits a **content-captured event** onto the event bus. This is picked up by the **LLM provider**: this could be your organisation's corporate LLM, an external service, or a private LLM running entirely offline. Google Meet has already given us the transcript, so there's nothing to transcribe — the LLM provider summarises the transcript and Meet's own summary together, using the prompt configured for this meeting type. When it's done, it emits a **content-summarised event**.

The **knowledge provider** subscribes to that event and takes care of actually storing it, in whatever format its implementation uses under the hood: an Obsidian-compatible Markdown vault here, but Confluence or otherwise if that's what you've configured. Once it's saved, it emits a **knowledge-stored event**.

Notice that each of those handoffs goes through the event bus. The LLM provider doesn't call the knowledge provider; it announces that it's finished and the knowledge provider picks the work up. That's what lets you swap either one without touching the other.

Here's the note it wrote. Let me open it — you can see the summary, the transcript, the link back to the original recording, and the links across to the tickets that were discussed.

### Decision service

With your capture source, work item provider, candidate pool and trigger phrase all configured, and knowledge now sitting in the vault, we're ready for the decision service to spring into action.

The decision service listens for the knowledge-stored event. It takes the knowledge and passes it to Jev, a decision-oriented AI, or your LLM of choice, to work out whether any actions need to be taken, based on the knowledge, your configured work items, and your trigger phrase.

For example, someone in the meeting says, "I completed ticket one-two-three today," and later, "for the rubber duck, please close ticket seven-eight-nine." That goes to Jev, which determines: was a ticket mentioned, and was any ticket action requested?

When a mention or a potential action is picked up, it goes through a series of **action checks**.

If a ticket mention is determined to have happened with a high degree of certainty, CKA automatically comments on that ticket: "this ticket was mentioned during this meeting," with a link back to the knowledge and any other relevant details. That happens without asking permission, since the confidence is high enough and a comment changes nothing.

If Jev thinks a ticket was probably mentioned but isn't confident enough, that doesn't get dropped — it goes to the triage list as a proposed comment for a human to confirm.

And if the trigger phrase plus an action is detected, that always goes to the triage list for a human to check, regardless of confidence — until you've explicitly granted that action type auto-approve, which I'll come back to.

So: comments happen automatically above the confidence threshold, comments below it get proposed, and anything that changes a ticket goes through a human first. And if something sounds like a ticket reference but doesn't match any real ticket in your pool, it's logged as an unmatched mention rather than guessed at or quietly dropped.

### Triage and audit

The triage list is where an admin sees everything CKA is proposing: ticket mentions it wasn't fully confident about, and actions like moving a ticket to done or reassigning it. Every comment that was made automatically, because it was above the confidence threshold, is also recorded, in the audit log, which gives you a full audit trail of everything the system has done or proposed.

Let me work through these. This one's correct, so I'll accept it. This one — CKA has matched a ticket number that was said in the meeting, but it's picked the wrong ticket; the number spoken wasn't the one that was meant. So I'll reject it. Both the accept and the reject are recorded.

When an admin accepts an item in the triage list, CKA emits an event to the work item provider to actually carry out that action, and records the outcome in the audit trail. Here are those comments on the real tickets, and here's the ticket that was closed.

And here's the audit trail: you can follow any one of these back from the action all the way to the original recording it came from.

Over time, this builds a trust picture. If you've accepted, say, one hundred percent of ticket reassignment actions over the last two weeks, CKA will offer to switch that action to auto-approve. Conversely, if you've only accepted fifty percent of ticket-close actions in the same period, it'll flag that and ask whether you want to disable that action, or try a different LLM provider if accuracy seems to be the issue.

### Error handling

If an action fails when a provider tries to carry it out, say GitHub is down or your token's expired, that shows up as a new event in the audit trail: "this failed." You're given the choice to retry, switch to another provider, or dismiss it. Nothing fails silently: you can always see exactly what happened and decide how to handle it.

Let me show you. I'll set the GitHub token to something invalid, and accept a proposal. There it is — a failed state, with the provider and the error, and my three options.

### One more on composability: MCP

Everything I've shown you is CKA's own front end. But there's a second way in, and it matters for the composability story.

CKA publishes an **MCP compatibility spec** — MCP being the open standard AI assistants use to discover and call tools. Two consequences. First, your own AI assistant can reach CKA directly: you could ask it "do I have anything from my last meeting that needs reviewing?" and it would find the triage list and act on it, with no bespoke integration written for either side. Second, it runs the other way — any of those providers we configured earlier can be an MCP server rather than CKA-specific code.

So the composability doesn't stop at swapping providers. You're not locked into CKA's own interface either. For today I'm showing you the front end, but the spec and CKA's own implementation of it ship with the product.

### Reset

One last thing, because this is a demo I need to be able to run again. This reset button undoes the actions CKA took on GitHub, clears the synthetic data and repopulates it. The audit trail is what tells it which actions to undo. And we're back to a clean slate.

## 3. Closing recap

So, in summary: CKA allows you to turn your content into structured knowledge, and turns that structured knowledge into safe, auditable actions — reachable through its own interface, or through any AI assistant that speaks MCP.

One thing not included in this MVP: a key part of the composability story is cost tracking, seeing how much you're spending with each LLM or provider, and comparing that against alternatives so you can swap providers with full visibility on cost. That's on the roadmap, but out of scope for what you're seeing today.

---

## Beat checklist (T21)

T21 specifies ten beats. Used as a test plan, each line below has to actually work in the built system, not just be narrated over.

| # | Beat | Section above |
|---|---|---|
| 1 | Drop the recording into the watched folder | Capture |
| 2 | The interface shows polling, and that it found something | Content listener to LLM provider to knowledge provider |
| 3 | Show it processing | Content listener to LLM provider to knowledge provider |
| 4 | Show the note saved to the knowledge store, and open it | Content listener to LLM provider to knowledge provider |
| 5 | Show the triage list | Triage and audit |
| 6 | Accept some proposals and reject the deliberate misclassification | Triage and audit |
| 7 | Show the comments posted on tickets and the approved actions in GitHub | Triage and audit |
| 8 | Show the audit trail reflecting the accept and reject decisions | Triage and audit |
| 9 | Break the GitHub token and show the error state | Error handling |
| 10 | Reset from the front end and show the clean state | Reset |

## Changes from the narrated version

Recorded so the edits are reviewable rather than silently absorbed.

**Corrections.**

- *"The LLM provider transcribes the content into an Obsidian-compatible document"* → the LLM provider **summarises**; the **knowledge provider** writes the document. There is no transcription in the MVP at all ([decision 40](15-decision-log.md#15-decision-log)) — Google Meet always supplies the transcript. As narrated, this both misnamed the step and gave the LLM provider the knowledge provider's job.
- *"content-transcribed event"* → **content-summarised event**, for the same reason: naming an event after a step that never runs in the MVP invites the obvious question on camera.
- *"that always goes to the triage list for a human to check, regardless of confidence"* → qualified with **"until you've explicitly granted that action type auto-approve"**. As narrated this contradicted the trust-ramp paragraph two sections later, which offers exactly that graduation.
- *"wake word"* → **trigger phrase**, the term used throughout the PRD, introduced once as "which you can think of as a wake word" so the plain-language version still lands.
- *"ticket pool"* → **candidate pool**, likewise the PRD's term.
- *"approved"* → **accepted**, matching the Triage Inbox's own wording (accept / reject).

**Additions**, to cover T21 beats the narration didn't reach:

- An explicit line for dropping the recording in (beat 1), and for polling finding it (beat 2).
- Opening the stored note (beat 4).
- **Rejecting the deliberate misclassification** (beat 6) — the narration only covered accepting, which would have left the reject path unrecorded despite the test data being built specifically to provoke it.
- Showing the comments and closed ticket in GitHub, and tracing an entry back through the audit trail (beats 7 and 8).
- A **Reset** section (beat 10), which the narration did not mention at all.
- A short line making the event-bus handoff between the LLM and knowledge providers explicit, since that decoupling is the architectural point this session established.
- A clause on **unmatched mentions** in the decision-service recap. Without it that recap listed two mention outcomes as though they were all of them; there are three.
- An **MCP section** before Reset, added when T18 landed. The script pitches composability throughout and MCP is the strongest version of that claim — you aren't locked into CKA's own interface either — so its absence was a hole. Decided: mentioned, not demonstrated live, which settles the open question in T18's notes about whether the recording features an external MCP client.

---

← Back to the PRD: [README](../README.md) · Related: [14. Landing page](14-landing-page.md) · [MVP ticket breakdown](mvp-tickets.md) · Ticket: [#21](https://github.com/JamesTwisleton/capture-knowledge-action/issues/21)
