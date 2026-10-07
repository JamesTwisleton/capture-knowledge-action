'use client';

import { useEffect, useState } from 'react';

// The browser makes this call, not the container, so the URL must be reachable from
// the host — Docker service names don't resolve outside the network.
const API_URL = process.env.NEXT_PUBLIC_API_URL ?? 'http://localhost:8080';

// T04's criterion is that stopping the backend turns the page red *while you are looking
// at it*, so this re-checks on a timer rather than once on mount. A one-shot check would
// leave a stale green on screen until someone reloaded by hand.
const POLL_MS = 5000;

// The three states the indicator can be in, and how each one looks. Colour lives next to
// its label so there is exactly one place to change either; the acceptance check asserts
// the data-connection-state attribute and that the two colours differ, never these
// literal values, so restyling this map doesn't break the test.
const PRESENTATION = {
  checking: { label: 'Checking…', colour: '#d29922' },
  connected: { label: 'Connected to back end', colour: '#3fb950' },
  disconnected: { label: 'Cannot connect', colour: '#f85149' },
} as const;

type ConnectionState = keyof typeof PRESENTATION;

export default function Home() {
  // One piece of state, not two: the detail only ever means something alongside a state,
  // and splitting them would allow the pair to render briefly out of step.
  const [{ state, detail }, setConnection] = useState<{
    state: ConnectionState;
    detail?: string;
  }>({ state: 'checking' });

  useEffect(() => {
    // Cancels an in-flight request when the component unmounts, so a late response can't
    // call setState on something that is already gone.
    const controller = new AbortController();

    const check = async () => {
      try {
        const res = await fetch(`${API_URL}/actuator/health`, { signal: controller.signal });
        const body = await res.json();
        setConnection(
          body.status === 'UP'
            ? { state: 'connected' }
            : // Reachable but unhealthy. Still red — the criterion is binary — but say
              // what it reported rather than discarding the one useful detail.
              { state: 'disconnected', detail: `reported ${body.status}` },
        );
      } catch {
        // Covers a stopped container (connection refused) and a running backend whose
        // CORS headers block the read. Both are "cannot connect" from here, which is the
        // honest thing to show: the CORS case is indistinguishable to this page, and
        // pretending otherwise is what made the old version claim it was fine.
        if (!controller.signal.aborted) setConnection({ state: 'disconnected' });
      }
    };

    check();
    const timer = setInterval(check, POLL_MS);
    return () => {
      controller.abort();
      clearInterval(timer);
    };
  }, []);

  const { label, colour } = PRESENTATION[state];

  return (
    <main style={{ maxWidth: 800, margin: '40px auto', padding: 20, border: '1px solid #333', borderRadius: 8 }}>
      <h1>Capture-Knowledge-Action</h1>
      <p>Frontend stub — proof the container runs and can reach the core.</p>
      <p
        data-connection-state={state}
        style={{
          marginTop: 20,
          padding: 15,
          background: '#1a1a1a',
          borderRadius: 4,
          borderLeft: `4px solid ${colour}`,
          color: colour,
        }}
      >
        <strong>Backend:</strong> {label}
        {detail ? ` (${detail})` : ''}
      </p>
    </main>
  );
}
