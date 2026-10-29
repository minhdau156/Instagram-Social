# Architecture & Flow Diagrams

All diagrams are written in [Mermaid](https://mermaid.js.org/) and render natively on GitHub.
To preview locally, install the
[Markdown Preview Mermaid Support](https://marketplace.visualstudio.com/items?itemName=bierner.markdown-mermaid)
VS Code extension.

---

## Deployment Architecture

Sourced from `deploy/docker-compose.prod.yml`, `deploy/Caddyfile`, and `deploy/alloy/config.alloy`. A single EC2 host runs every container; Caddy is the one public entry point and routes by path to the backend, MinIO, or the frontend. Backend logs and traces go straight to Grafana Cloud; only metrics are relayed through Alloy.

```mermaid
flowchart TD
    Browser["Browser / Mobile client"]

    subgraph Host["Single Docker host (docker-compose.prod.yml)"]
        Caddy["caddy :80 / :443\nTLS for DOMAIN"]

        subgraph App["Application"]
            FE["frontend :80\n(static build)"]
            BE["backend :8080\n(Spring Boot)"]
        end

        subgraph Data["Data"]
            PG["postgres :5432"]
            REDIS["redis :6379"]
            MINIO["minio :9000\nbucket: instagram-media"]
        end

        ALLOY["alloy\n(Grafana Agent)"]
    end

    subgraph GC["Grafana Cloud"]
        GCPROM["Prometheus\n(metrics)"]
        GCLOKI["Loki\n(logs)"]
        GCTEMPO["Tempo\n(traces)"]
        GRAFANA["Grafana dashboards"]
    end

    Browser -->|HTTPS| Caddy
    Caddy -->|"/api*, /ws*, /oauth2/*, /login/oauth2/*"| BE
    Caddy -->|"/instagram-media*\n(presigned uploads + public images)"| MINIO
    Caddy -->|everything else| FE

    BE --> PG
    BE --> REDIS
    BE --> MINIO

    ALLOY -->|"scrape /actuator/prometheus"| BE
    ALLOY -->|remote_write| GCPROM
    BE -->|OTLP traces| GCTEMPO
    BE -->|logs| GCLOKI
    GRAFANA --> GCPROM
    GRAFANA --> GCLOKI
    GRAFANA --> GCTEMPO

    style Data fill:#e8f5e9,stroke:#388e3c
    style App fill:#e3f2fd,stroke:#1565c0
    style GC fill:#fff3e0,stroke:#e65100
```

**Why MinIO is routed through Caddy, not called directly:** MinIO issues presigned upload/read URLs signed with whatever endpoint it was told it's reachable at (`MINIO_ENDPOINT: https://${DOMAIN}`). Routing `/instagram-media*` through Caddy on the same domain means those presigned URLs resolve over HTTPS on the public domain instead of an internal `minio:9000` address the browser could never reach.

**Why logs/traces skip Alloy:** the backend is configured to ship OTLP traces (`OTEL_ENDPOINT`) and logs (`LOKI_URL`) directly to Grafana Cloud with Basic Auth baked into the request headers. Alloy's only job here is scraping `/actuator/prometheus` and remote-writing it — there's no local Prometheus/Loki/Tempo to relay through, unlike the `docker-compose.yml` (local dev) stack which runs its own Grafana/Prometheus/Loki/Tempo containers.
