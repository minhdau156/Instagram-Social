# TODO: TASK-10.40 — k6 Load & Soak Testing

See `tasks/plan.md` for full detail and `SPEC.md` for the corrected API contracts and resolved decisions.

## Phase 1: Foundation
- [ ] Task 1: Fixture image + auth helper
  - Acceptance: `registerOnce()` tolerates 201/409; `login()` returns `data.accessToken`
  - Verify: exercised by Task 2's first run
  - Files: `k6/fixtures/test-image.jpg`, `k6/lib/auth.js`

## Phase 2: Core scenarios
- [ ] Task 2: Login scenario
  - Acceptance: uses `identifier` not `username`; thresholds tagged `{scenario:login}`
  - Verify: `k6 run k6/scenarios/login.js` → thresholds pass, exit 0
  - Files: `k6/scenarios/login.js`

- [ ] Task 3: Home-feed scenario
  - Acceptance: reuses one token from `setup()` across all iterations
  - Verify: `k6 run k6/scenarios/home-feed.js` → thresholds pass, exit 0
  - Files: `k6/scenarios/home-feed.js`

- [ ] Task 4: Media upload helper
  - Acceptance: returns a `mediaKey`; checks both upload-url call and MinIO PUT
  - Verify: exercised end-to-end by Task 5
  - Files: `k6/lib/media.js`

- [ ] Task 5: Create-post scenario
  - Acceptance: confirm `MediaItemRequest.java` required fields before hardcoding; every iteration creates a real 201 post reusing the setup `mediaKey`; caption has a unique marker
  - Verify: `k6 run k6/scenarios/create-post.js` → thresholds pass, exit 0; spot-check post was actually created
  - Files: `k6/scenarios/create-post.js`

## Checkpoint A (after Tasks 1-5)
- [ ] All three journey scenarios individually pass thresholds
- [ ] Human review before continuing

## Phase 3: Soak + combined runner + documentation
- [ ] Task 6: Soak scenario
  - Acceptance: ≥8 min steady run at 30 VUs
  - Verify: `k6 run k6/scenarios/soak.js`; poll actuator metrics (public, no auth) during run; note plateau vs. unbounded growth
  - Files: `k6/scenarios/soak.js`

- [ ] Task 7: Combined entry point
  - Acceptance: both scenarios run per configured `startTime` offsets
  - Verify: `k6 run k6/load-test.js` → exit 0
  - Files: `k6/load-test.js`

## Checkpoint B (after Tasks 6-7)
- [ ] Soak test completed with metrics observed
- [ ] Combined runner works
- [ ] Human review before final step

## Final task
- [ ] Task 8: Threshold-failure proof + baseline documentation
  - Acceptance: exit code 99 reproduced then reverted (clean diff); `docs/infra/load-test.md` has real (non-placeholder) numbers, k6 version, date, SLO table
  - Verify: `git diff` clean on scenario files after revert
  - Files: `docs/infra/load-test.md`

## Final Checkpoint
- [ ] All SPEC.md success criteria met
- [ ] `docs/infra/load-test.md` committed with real baseline
- [ ] Ready for human review / commit

## Prerequisites (once, before Task 1)
- [ ] `k6 version` installed and working
- [ ] `docker compose up -d` (Postgres, MinIO, Redis, observability — NOT backend/frontend)
- [ ] `cd backend && mvn spring-boot:run` in a separate terminal
