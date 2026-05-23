# Phase 14 — Kubernetes Deployment

> **Track:** Cloud/SRE · **Depends on:** Phase 10 (Docker images) · **New tools:** Kubernetes, Helm, kind/minikube  
> **Branch prefix:** `chore/phase-14-`

---

> **Skills you'll build:**
> - Deployments, Services, Ingress
> - ConfigMaps & Secrets
> - Liveness / readiness / startup probes
> - Horizontal Pod Autoscaler
> - Helm templating & values per environment
>
> **Best practices:** 12-factor config via env vars; one process per container; set resource requests/limits; never bake secrets into images; gate traffic behind readiness probes.

---

> **How to read this file**
> - **Why:** the problem this task solves — read it before you start.
> - **Done when:** a concrete way to prove the task works. If you can't demonstrate it, it isn't finished.

---

## Cluster & Workloads

### TASK-14.1 — Local cluster (kind/minikube) + namespace
> **Why:** You need a throwaway Kubernetes cluster on your laptop so you can break things safely before touching real cloud infrastructure.
> **Done when:** `kubectl get nodes` shows a Ready node and `kubectl get ns instagram` returns your namespace.
- [ ] Install `kind` (or `minikube`) and create a cluster via `k8s/kind-cluster.yaml`
- [ ] Create a dedicated namespace manifest `k8s/namespace.yaml` (`instagram`)
- [ ] Set the namespace as the default context for the project (`kubectl config set-context --current --namespace=instagram`)
- [ ] Load the Phase 10 backend/frontend images into the cluster (`kind load docker-image`)

### TASK-14.2 — Deployment + Service manifests for backend & frontend
> **Why:** A Deployment keeps the right number of pod replicas running; a Service gives them a stable in-cluster address so other pods can reach them.
> **Done when:** Both Deployments report all replicas Ready and `kubectl port-forward svc/backend 8080` serves `/actuator/health`.
- [ ] Write `k8s/backend-deployment.yaml` + `k8s/backend-service.yaml` (ClusterIP, port 8080)
- [ ] Write `k8s/frontend-deployment.yaml` + `k8s/frontend-service.yaml` (ClusterIP, port 80)
- [ ] Set `resources.requests` and `resources.limits` (CPU/memory) on both containers
- [ ] Pin image tags to the commit SHA from Phase 10's GHCR push (no `:latest`)
- [ ] Verify with `kubectl get deploy,svc` that everything is Ready

### TASK-14.3 — Postgres/Redis/MinIO for dev (StatefulSets or operators)
> **Why:** Stateful backing services need stable network identity and persistent storage that survives pod restarts — that's what StatefulSets and PVCs provide.
> **Done when:** Postgres, Redis, and MinIO pods are Running with bound PersistentVolumeClaims, and the backend connects to all three.
- [ ] Write `k8s/postgres-statefulset.yaml` with a `volumeClaimTemplates` PVC
- [ ] Write `k8s/redis-statefulset.yaml` (or a Deployment, since Redis dev data is disposable)
- [ ] Write `k8s/minio-statefulset.yaml` with a PVC for object storage
- [ ] Add headless Services for each StatefulSet
- [ ] Confirm `kubectl get pvc` shows all volumes Bound

---

## Config, Networking & Scaling

### TASK-14.4 — ConfigMap + Secret for app config
> **Why:** 12-factor config keeps environment-specific values out of the image, so the same image runs in dev, staging, and prod without a rebuild.
> **Done when:** The backend pod reads non-secret config from a ConfigMap and the DB password/JWT key from a Secret, with no credentials hard-coded in any manifest.
- [ ] Write `k8s/backend-configmap.yaml` (Spring profile, service URLs, feature flags)
- [ ] Create `k8s/backend-secret.yaml` for DB password, JWT key, MinIO creds (base64; document that real values come from a secret manager)
- [ ] Wire both into the backend Deployment via `envFrom` (`configMapRef` + `secretRef`)
- [ ] Verify with `kubectl exec` that the expected env vars are present in the pod

### TASK-14.5 — Liveness/readiness probes wired to `/actuator/health`
> **Why:** Probes let Kubernetes restart a hung pod (liveness) and withhold traffic until a pod is truly ready (readiness), preventing requests from hitting a half-started app.
> **Done when:** A pod that fails its liveness check is restarted automatically, and a not-yet-ready pod is excluded from the Service endpoints.
- [ ] Add a `readinessProbe` hitting `/actuator/health/readiness` on the backend container
- [ ] Add a `livenessProbe` hitting `/actuator/health/liveness`
- [ ] Add a `startupProbe` so slow JVM boot doesn't trip the liveness probe early
- [ ] Enable Spring Boot's `management.endpoint.health.probes.enabled=true` in the ConfigMap
- [ ] Test by killing the DB and confirming the pod goes NotReady (drops out of `kubectl get endpoints`)

### TASK-14.6 — Ingress + TLS
> **Why:** An Ingress exposes the cluster to the outside world on one entry point and terminates TLS, so users reach the app over HTTPS instead of `kubectl port-forward`.
> **Done when:** Browsing the Ingress host serves the frontend over HTTPS and `/api` routes reach the backend.
- [ ] Install an ingress controller (e.g. `ingress-nginx`) into the cluster
- [ ] Write `k8s/ingress.yaml` routing `/` → frontend Service and `/api` → backend Service
- [ ] Add a TLS section referencing a `tls` Secret (self-signed via `mkcert` for local)
- [ ] Map the host in `/etc/hosts` (or minikube tunnel) and confirm HTTPS works in the browser

### TASK-14.7 — HPA on CPU / a custom metric
> **Why:** A Horizontal Pod Autoscaler adds and removes replicas with load, so the app handles traffic spikes without you babysitting it.
> **Done when:** Under synthetic load the backend Deployment scales up past its baseline replica count, then scales back down when load stops.
- [ ] Install `metrics-server` so the cluster reports pod CPU usage
- [ ] Write `k8s/backend-hpa.yaml` targeting ~70% CPU, min 2 / max 6 replicas
- [ ] Generate load (e.g. `hey` or `ab` against `/api/v1/feed`) and watch `kubectl get hpa -w`
- [ ] (Optional) Scale on a Prometheus custom metric (request rate from `/actuator/prometheus`)

---

## Packaging

### TASK-14.8 — Package it all as a Helm chart with per-env values
> **Why:** Raw manifests duplicate values across environments; a Helm chart templates them once and swaps a `values-*.yaml` file per environment.
> **Done when:** `helm install instagram ./charts/instagram -f values-dev.yaml` brings up the full stack, and `-f values-prod.yaml` renders the same chart with prod settings.
- [ ] Scaffold `charts/instagram/` (`Chart.yaml`, `templates/`, `values.yaml`)
- [ ] Move every manifest from TASK-14.2–14.7 into `templates/` with parameterized values
- [ ] Create `values-dev.yaml` and `values-prod.yaml` (replica counts, image tags, resource sizes, host)
- [ ] Verify rendering with `helm template` and a dry run with `helm install --dry-run --debug`
- [ ] Document install/upgrade/rollback commands in the chart README
