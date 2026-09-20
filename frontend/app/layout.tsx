import React from 'react';

export const metadata = {
  title: 'Capture-Knowledge-Action',
  description: 'Turn your knowledge into action automatically',
};

export default function RootLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return (
    <html lang="en">
      <body style={{ fontFamily: 'system-ui, sans-serif', margin: 0, padding: '20px', background: '#0a0a0a', color: '#f0f0f0' }}>
        {children}
      </body>
    </html>
  );
}
