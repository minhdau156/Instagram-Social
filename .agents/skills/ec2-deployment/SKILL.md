---
name: ec2-deployment
description: This project's production deployment pattern - single EC2 host, Docker Compose, Caddy as the one public entry point, multi-arch images for Graviton (ARM), and logs/traces sent directly to Grafana Cloud. Use when the user asks about deploying, the prod stack, docker-compose.prod.yml, Caddy routing, the EC2 instance, or "how do I pull/update the deployed backend".
---

# EC2 Deployment Skill

How this project actually runs in production: one EC2 instance, one Docker Compose file, one reverse proxy in front of everything. Use this skill instead of inventing a generic cloud-deployment answer — the real config is in `deploy/`.

## When to Use
- "how do I deploy / redeploy this"
- "pull the docker [image] in backend/frontend/prod"
- "why isn't my change showing up on the server"
- "what does the prod architecture look like"
- Reviewing changes to `deploy/docker-compose.prod.yml`, `deploy/Caddyfile`, or `deploy/alloy/config.alloy`
- Reviewing AWS security group / EC2 instance settings for this project

## Source of Truth

Read these before answering — don't guess at the topology:
- `deploy/docker-compose.prod.yml` — every prod service, env var, healthcheck
- `deploy/Caddyfile` — the only public entry point; routes by path
- `deploy/alloy/config.alloy` — the one thing Alloy does (scrape + remote_write)
- `docs/diagrams.md` — the architecture diagram, kept in sync with the above
- `.github/workflows/ci.yml` — builds and pushes the images this stack pulls

## Architecture (one host, one proxy)

```
Browser ──HTTPS──▶ Caddy :80/:443 (TLS for $DOMAIN)
                      ├─ /api*, /ws*, /oauth2/*, /login/oauth2/*  ──▶ backend:8080
                      ├─ /instagram-media*                        ──▶ minio:9000
                      └─ everything else                          ──▶ frontend:80

backend ──▶ postgres:5432, redis:6379, minio:9000
alloy   ──▶ scrapes backend:8080/actuator/prometheus ──▶ remote_write ──▶ Grafana Cloud Prometheus
backend ──▶ OTLP traces ──▶ Grafana Cloud Tempo (direct, no relay)
backend ──▶ logs        ──▶ Grafana Cloud Loki  (direct, no relay)
```

Every container in `deploy/docker-compose.prod.yml` runs on **one** EC2 instance — there's no orchestrator, no load balancer, no multi-AZ failover. That's a deliberate scale tradeoff, not an oversight; don't propose Kubernetes/ECS/ALB changes unless asked.

## Key design decisions (the "why", not just the "what")

**MinIO is reverse-proxied through Caddy, not called directly.**
`MINIO_ENDPOINT` is set to `https://$DOMAIN`, so MinIO *signs* presigned upload/read URLs against the public domain. `deploy/Caddyfile` routes `/instagram-media*` to `minio:9000` for exactly this reason — if MinIO were hit on its own port/host, presigned URLs would point somewhere the browser can't reach. See the recent commits `fix(deploy): serve MinIO through Caddy...` and `fix: read media base URL from VITE_CDN_BASE_URL...` for the bug this fixes.

**Logs and traces skip Alloy; only metrics go through it.**
The backend ships OTLP traces (`OTEL_ENDPOINT` = `GC_TEMPO_OTLP_ENDPOINT`) and logs (`LOKI_URL` = `GC_LOKI_URL`) straight to Grafana Cloud with Basic Auth in the request headers — `deploy/alloy/config.alloy` only has a `prometheus.scrape` + `prometheus.remote_write` block. This differs from the local-dev `docker-compose.yml`, which runs its own Prometheus/Loki/Tempo/Grafana containers. Don't assume Alloy relays everything just because it's "the observability container."

**Images are built multi-arch (amd64 + arm64) because prod runs on Graviton.**
The EC2 instance type is `t4g.small` — ARM64, not x86. `.github/workflows/ci.yml` sets up QEMU + Buildx and builds with `platforms: linux/amd64,linux/arm64` for exactly this reason. If someone "simplifies" the CI to build amd64-only, the prod `docker compose pull` will silently fail or fall back to emulation on that box.

**A single Caddy container is the only public ingress.**
Ports 80/443 are the only ones meant to be internet-facing. Backend (8080), Postgres (5432), Redis (6379), and MinIO (9000) are only reachable from other containers on the compose network — they're not published to the host, by design. If the user mentions exposing one of those directly, check whether they actually need Caddy to route it instead.

## Updating the deployed stack

From the directory holding `deploy/docker-compose.prod.yml`, on the EC2 host itself (these commands do nothing from a local machine):

```bash
docker compose -f docker-compose.prod.yml pull backend frontend
docker compose -f docker-compose.prod.yml up -d backend frontend
```

The images come from `ghcr.io/minhdau156/instagram-backend:latest` / `instagram-frontend:latest`, published by the `docker-publish` job in `.github/workflows/ci.yml` on every push to `main`. A `git push` to `main` does **not** redeploy by itself — someone (or a future CD step) still has to pull + recreate the containers on the host.

## Security group notes (insta-sg, sg-05efc32620a4a6a55)

As of the last review, inbound rules on this project's security group are:

| Port | Protocol | Source | Purpose |
|------|----------|--------|---------|
| 80 | TCP | `0.0.0.0/0` | HTTP (Caddy redirects to HTTPS) |
| 443 | TCP | `0.0.0.0/0` | HTTPS (Caddy) |
| 22 | TCP | a single `/32` IP | SSH, correctly restricted |
| **All** | **All** | **`0.0.0.0/0`** | ⚠️ overly broad — see below |

**Flag this when reviewing the security group:** an inbound rule allowing all ports/all protocols from anywhere is far wider than this deployment needs — only 80/443 (public) and 22 (restricted) are actually used. Recommend narrowing or removing that rule rather than leaving it as a catch-all. (The *outbound* all-ports-all-protocol-to-0.0.0.0/0 rule is the AWS default and is normal — don't flag that one.)

## Related

- `docs/diagrams.md` — Mermaid source for the architecture diagram; update it alongside `deploy/` changes
- `code-quality` skill — for reviewing the Java/Spring side of what's being deployed
