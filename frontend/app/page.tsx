'use client';

import { useEffect, useState } from 'react';

export default function Home() {
  const [backendStatus, setBackendStatus] = useState<string>('Checking...');

  useEffect(() => {
    const apiUrl = process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8080';
    fetch(`${apiUrl}/api/health`)
      .then((res) => res.json())
      .then((data) => setBackendStatus(`${data.service} is ${data.status}`))
      .catch(() => setBackendStatus('Backend unreachable'));
  }, []);

  return (
    <main style={{ maxWidth: '800px', margin: '40px auto', padding: '20px', border: '1px solid #333', borderRadius: '8px' }}>
      <h1>Capture-Knowledge-Action (CKA)</h1>
      <p>Frontend Service Stub (Next.js)</p>
      <div style={{ marginTop: '20px', padding: '15px', background: '#1a1a1a', borderRadius: '4px' }}>
        <strong>Backend Connection:</strong> {backendStatus}
      </div>
    </main>
  );
}
