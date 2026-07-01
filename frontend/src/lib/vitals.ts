import * as Sentry from '@sentry/react';
import { onCLS, onINP, onLCP } from 'web-vitals';

export function reportWebVitals(): void {
  const reportToSentry = (metric: { name: string; value: number; id: string }) => {
    Sentry.setMeasurement(metric.name, metric.value, metric.name === 'CLS' ? '' : 'millisecond');
  };

  onCLS(reportToSentry);
  onINP(reportToSentry);
  onLCP(reportToSentry);
}
