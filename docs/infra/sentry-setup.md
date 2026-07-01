# Sentry Setup

## Projects

| Project | Platform | DSN env var |
|---|---|---|
| Instagram Frontend | JavaScript (React) | `VITE_SENTRY_DSN` (frontend build) |
| Instagram Backend | Java (Spring Boot) | `SENTRY_DSN` (backend runtime) |

## Local Development

Leave `VITE_SENTRY_DSN` and `SENTRY_DSN` empty (or unset) locally.
The SDKs are no-ops when the DSN is blank — no events are sent.

## Staging / Production

Set DSNs as environment variables or Docker secrets. Never commit them to source control.

## Source map upload (frontend)

Source maps are uploaded to Sentry during `npm run build` when these CI env vars are set:
- `SENTRY_AUTH_TOKEN` — Sentry auth token with `project:releases` scope
- `SENTRY_ORG` — Sentry organization slug
- `SENTRY_PROJECT` — Sentry project slug for the frontend

## Sampling rates

| Environment | Frontend traces | Backend traces |
|---|---|---|
| local | 100% (`1.0`) | 0% (disabled) |
| staging | 100% (`1.0`) | 10% (`0.1`) |
| prod | 10% (`0.1`) | 10% (`0.1`) |

## PII scrubbing

`beforeSend` in `main.tsx` removes `Authorization` headers and redacts
`content` and `email` fields from request data.
Do not add user email addresses or message content to custom Sentry contexts.
