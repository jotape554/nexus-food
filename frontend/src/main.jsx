import React, { lazy, Suspense } from 'react';
import ReactDOM from 'react-dom/client';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import App from './App';
import { DEMO } from './demo/modo';
import './styles/app.css';

const queryClient = new QueryClient({
  defaultOptions: {
    queries: { retry: 1, refetchOnWindowFocus: true },
  },
});

// Na build de demonstração o sistema abre dentro da casca da demo; na build normal esse
// código nem entra no pacote.
const DemoShell = DEMO ? lazy(() => import('./demo/DemoShell')) : null;

ReactDOM.createRoot(document.getElementById('root')).render(
  <React.StrictMode>
    <QueryClientProvider client={queryClient}>
      {DEMO ? (
        <Suspense fallback={null}>
          <DemoShell />
        </Suspense>
      ) : (
        <App />
      )}
    </QueryClientProvider>
  </React.StrictMode>
);
