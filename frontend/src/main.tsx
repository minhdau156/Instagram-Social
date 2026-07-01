import * as Sentry from '@sentry/react';
import ReactDOM from 'react-dom/client';
import { QueryClientProvider } from '@tanstack/react-query';
import { ReactQueryDevtools } from '@tanstack/react-query-devtools';
import App from './App';
import { queryClient } from './lib/queryClient';
import { AuthProvider } from './context/AuthContext';
import { reportWebVitals } from './lib/vitals';

// Initialize Sentry before rendering the app.
// VITE_SENTRY_DSN is empty in local dev (see .env.example) — SDK is a no-op when DSN is falsy.
Sentry.init({
  dsn: import.meta.env.VITE_SENTRY_DSN,
  environment: import.meta.env.MODE,
  release: import.meta.env.VITE_APP_VERSION,

  integrations: [
    Sentry.browserTracingIntegration(),
    Sentry.replayIntegration({
      maskAllText: false,
      blockAllMedia: false,
    }),
  ],

  tracesSampleRate: import.meta.env.PROD ? 0.1 : 1.0,
  replaysSessionSampleRate: 0.1,
  replaysOnErrorSampleRate: 1.0,

  // Propagate trace context to backend API calls so frontend and backend
  // events are correlated by traceId (pairs with TASK-10.28)
  tracePropagationTargets: [
    'localhost',
    /^https:\/\/api\.instagram-social\.example\.com/,
  ],

  beforeSend(event) {
    if (event.request?.headers) {
      delete event.request.headers['Authorization'];
    }
    if (event.request?.data) {
      const data = event.request.data as Record<string, unknown>;
      if (data.content) {
        data.content = '[redacted]';
      }
      if (data.email) {
        data.email = '[redacted]';
      }
    }
    return event;
  },
});

reportWebVitals();

ReactDOM.createRoot(document.getElementById('root')!).render(

  <QueryClientProvider client={queryClient}>
    <AuthProvider>
      <App />
    </AuthProvider>
    {import.meta.env.DEV && <ReactQueryDevtools initialIsOpen={false} />}
  </QueryClientProvider>

);
