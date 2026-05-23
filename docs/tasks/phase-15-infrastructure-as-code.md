# Phase 15 — Infrastructure as Code (Terraform)

> **Track:** Cloud/SRE · **Depends on:** Phase 14 · **New tools:** Terraform, a cloud account (AWS/GCP/Azure)  
> **Branch prefix:** `chore/phase-15-`

---

> **Skills you'll build:**
> - Providers, resources, modules, variables, outputs
> - Remote state + locking
> - Multiple environments (dev/staging/prod)
> - The `plan`/`apply` workflow and drift detection
>
> **Best practices:** never click in the console for prod; keep state in a remote backend with locking; build reusable modules; least-privilege IAM; tag every resource.

---

> **How to read this file**
> - **Why:** the problem this task solves — read it before you start.
> - **Done when:** a concrete way to prove the task works. If you can't demonstrate it, it isn't finished.

---

## Foundations

### TASK-15.1 — Terraform skeleton + remote state backend
> **Why:** Terraform's state file tracks what it built; storing it remotely with locking lets a team (and CI) run `apply` safely without overwriting each other.
> **Done when:** `terraform init` succeeds against a remote backend and a second concurrent `apply` is blocked by the state lock.
- [ ] Create `terraform/` with `main.tf`, `providers.tf`, `versions.tf` (pin provider + Terraform versions)
- [ ] Provision the state bucket + lock table out-of-band (e.g. `terraform/bootstrap/` for the S3 bucket + DynamoDB table)
- [ ] Configure the remote `backend` block (S3 + DynamoDB lock, or GCS/Azure equivalent)
- [ ] Run `terraform init` and confirm state lives remotely, not in a local file
- [ ] Add a `default_tags` block (project, environment, owner) on the provider

### TASK-15.2 — Network module (VPC/subnets)
> **Why:** Every other resource sits inside a network; defining the VPC, subnets, and routing as a reusable module gives you isolated public/private tiers per environment.
> **Done when:** `terraform apply` of the network module creates a VPC with public and private subnets across availability zones, shown in the cloud console.
- [ ] Create `terraform/modules/network/` (`main.tf`, `variables.tf`, `outputs.tf`)
- [ ] Define VPC, public + private subnets across ≥2 AZs, an internet gateway, and a NAT gateway
- [ ] Output VPC id and subnet ids for downstream modules to consume
- [ ] Call the module from `terraform/envs/dev/` and verify with `terraform plan` before apply

### TASK-15.3 — Managed Postgres + Redis modules
> **Why:** Managed database/cache services handle backups, patching, and failover for you — far safer than self-hosting stateful pods in prod.
> **Done when:** `terraform apply` provisions a managed Postgres and Redis instance in the private subnets, and the connection endpoints are exposed as outputs.
- [ ] Create `terraform/modules/database/` (managed Postgres, e.g. RDS) in private subnets
- [ ] Create `terraform/modules/cache/` (managed Redis, e.g. ElastiCache)
- [ ] Restrict access with security groups so only the cluster's nodes can connect
- [ ] Store generated credentials in a secret manager resource, not in state output as plaintext
- [ ] Output connection endpoints/ports for the Helm release to consume

### TASK-15.4 — Object storage (S3) + CDN
> **Why:** Media uploads need durable object storage, and a CDN serves those bytes from edge locations so the app server never streams images (continues Phase 10's CDN work).
> **Done when:** `terraform apply` creates a private media bucket fronted by a CDN distribution, and an object served through the CDN URL loads in the browser.
- [ ] Create `terraform/modules/storage/` for the media bucket (private, versioned, encrypted)
- [ ] Add a CDN distribution (e.g. CloudFront) with the bucket as origin via an origin-access identity
- [ ] Output the CDN base URL to feed back into `VITE_CDN_BASE_URL`
- [ ] Confirm direct public bucket access is blocked but CDN access works

### TASK-15.5 — Container registry + Kubernetes cluster module
> **Why:** You need a private registry to host your images and a managed Kubernetes control plane to run the Phase 14 workloads in the cloud.
> **Done when:** `terraform apply` creates the registry and a managed cluster, and `kubectl get nodes` (using the generated kubeconfig) shows Ready nodes.
- [ ] Create `terraform/modules/registry/` for a private container registry (ECR/GAR/ACR)
- [ ] Create `terraform/modules/cluster/` for a managed Kubernetes service (EKS/GKE/AKS) with a node group in private subnets
- [ ] Apply least-privilege IAM roles for the cluster and node group
- [ ] Output the kubeconfig / cluster auth and verify `kubectl get nodes`

---

## Deploy & Operate

### TASK-15.6 — Deploy the Phase 14 Helm release (Terraform or GitOps handoff)
> **Why:** The infrastructure is useless until the app runs on it; this task wires the Phase 14 Helm chart onto the Terraform-provisioned cluster.
> **Done when:** The full stack is running on the cloud cluster and reachable through its Ingress host over HTTPS.
- [ ] Option A: use the Terraform `helm` provider to install `charts/instagram` with `values-prod.yaml`
- [ ] Option B: hand off to GitOps — provision Argo CD via Terraform and point it at the chart repo
- [ ] Inject the TASK-15.3/15.4 endpoints (DB, Redis, bucket, CDN) into the Helm values
- [ ] Confirm pods are Running and the Ingress host serves the app end-to-end

### TASK-15.7 — Per-environment configs + `terraform plan` in CI
> **Why:** Separate env directories prevent a dev change from touching prod, and running `plan` in CI catches drift and surfaces the diff before anyone clicks apply.
> **Done when:** A pull request automatically posts a `terraform plan` for the changed environment, and dev/prod are provisioned from isolated state.
- [ ] Split into `terraform/envs/dev/` and `terraform/envs/prod/`, each with its own backend key and `*.tfvars`
- [ ] Add a CI workflow that runs `terraform fmt -check`, `validate`, and `plan` on PRs
- [ ] Post the plan output as a PR comment; require manual approval before `apply` to prod
- [ ] Run a scheduled `plan` to detect drift and alert when live infra diverges from code
