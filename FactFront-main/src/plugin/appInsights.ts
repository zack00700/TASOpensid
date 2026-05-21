import { ApplicationInsights } from '@microsoft/applicationinsights-web';

const connectionString = import.meta.env.VITE_APPINSIGHTS_CONNECTION_STRING as string | undefined;
const instrumentationKey = import.meta.env.VITE_APPINSIGHTS_KEY as string | undefined;

let appInsights: ApplicationInsights | null = null;

if (connectionString || instrumentationKey) {
  appInsights = new ApplicationInsights({
    config: {
      connectionString,
      instrumentationKey,
      enableAutoRouteTracking: true,    // track SPA page views
      autoTrackPageVisitTime: true,
      // Disable correlation headers — they add Request-Id / Request-Context
      // to every XHR which requires the backend to explicitly allow them in CORS.
      enableCorsCorrelation: false,
      disableCorrelationHeaders: true,
    },
  });
  appInsights.loadAppInsights();
  appInsights.trackPageView();
}

export { appInsights };

/**
 * Log an error to Application Insights (and console).
 * Safe to call even if App Insights is not configured.
 */
export function trackError(error: unknown, properties?: Record<string, string>) {
  const err = error instanceof Error ? error : new Error(String(error));
  console.error(err, properties);
  appInsights?.trackException({ exception: err, properties });
}

/**
 * Log a custom event to Application Insights.
 */
export function trackEvent(name: string, properties?: Record<string, string>) {
  appInsights?.trackEvent({ name, properties });
}
