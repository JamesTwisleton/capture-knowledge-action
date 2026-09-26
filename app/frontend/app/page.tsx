'use client';

import { useEffect, useState } from 'react';

// The browser makes this call, not the container, so the URL must be reachable from
// the host — Docker service names don't resolve outside the network.
const API_URL = process.env.NEXT_PUBLIC_API_URL ?? 'http://localhost:8080';

export default function Home() {
  const [status, setStatus] = useState('Checking…');

  useEffect(() => {
    fetch(`${API_URL}/actuator/health`)
      .then((res) => res.json())
      .then((body) => setStatus(body.status === 'UP' ? 'Connected' : `Reported ${body.status}`))
      .catch(() => setStatus('Unreachable'));
  }, []);

  return (
    <main style={{ maxWidth: 800, margin: '40px auto', padding: 20, border: '1px solid #333', borderRadius: 8 }}>
      <h1>Capture-Knowledge-Action</h1>
      <p>Frontend stub — proof the container runs and can reach the core.</p>
      <p style={{ marginTop: 20, padding: 15, background: '#1a1a1a', borderRadius: 4 }}>
        <strong>Backend:</strong> {status}
      </p>
    </main>
  );
}
