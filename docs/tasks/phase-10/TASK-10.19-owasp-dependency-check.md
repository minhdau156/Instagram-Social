# TASK-10.19 — OWASP dependency check in CI

## Overview

Add the OWASP Dependency-Check Maven plugin to the backend build and run it in the GitHub Actions CI pipeline. The plugin scans every JAR on the classpath against the National Vulnerability Database (NVD) and fails the build if any dependency carries a CVSS score of 7 or higher — the threshold that roughly corresponds to "High severity". The HTML report it produces tells you exactly which library, which CVE, and what the fix is.

---

## Level

**Core** — No direct pair in the security track, but works alongside [TASK-10.23 (DAST scan in CI)](TASK-10.23-dast-zap-scan.md), which checks the running app from the outside; this task checks the supply chain from the inside.

---

## Why

Most real-world breaches exploit known vulnerabilities — bugs that were already reported, catalogued, and assigned a CVE number. Attackers scan publicly reachable apps for known vulnerable dependency versions, sometimes within hours of a CVE being published. Scanning dependencies on every build means you find out about a newly published CVE affecting your version of, say, `jjwt` or `spring-boot` on the same day the CI pipeline runs — long before an attacker targets your deployment. The cost is a few minutes of build time; the alternative is hoping your dependency graph stays permanently clean without checking.

---

## Prerequisites

- The existing CI pipeline at `.github/workflows/ci.yml` — you will add a new step to it.
- Maven build at `backend/pom.xml`.
- An NVD API key is optional for local runs but **strongly recommended** for CI — without it the plugin downloads the vulnerability database over HTTP at a rate-limited speed, adding 30–60 minutes to the first CI run. See Step 3 for how to get and use a free API key.
- **Concept gloss:**
  - **CVSS** — Common Vulnerability Scoring System, a 0–10 severity score. 7+ = High; 9+ = Critical.
  - **CVE** — Common Vulnerabilities and Exposures — the official unique ID for a known vulnerability (e.g. `CVE-2021-44228` is Log4Shell).
  - **NVD** — National Vulnerability Database, maintained by NIST, the authoritative source the plugin queries.
  - **OWASP Dependency-Check** — open-source SCA (Software Composition Analysis) tool; the Maven plugin variant runs as part of `mvn verify`.
  - **False positive** — a CVE flagged against a library that doesn't actually affect the way you use it; these are suppressed in a `suppression.xml` file.

---

## Files to Create / Modify

```
backend/pom.xml                                                         (modify — add plugin)
backend/suppression.xml                                                 (new — false-positive suppressions)
.github/workflows/ci.yml                                                (modify — add check step)
```

---

## Step-by-Step

### 1. Add the OWASP Dependency-Check plugin to `pom.xml`

Open `backend/pom.xml`. Add the plugin inside the `<build><plugins>` block. If a `<build>` section does not exist, add one:

```xml
<build>
  <plugins>

    <!-- ... existing plugins (spring-boot-maven-plugin, flyway-maven-plugin) ... -->

    <plugin>
      <groupId>org.owasp</groupId>
      <artifactId>dependency-check-maven</artifactId>
      <version>10.0.3</version>
      <configuration>
        <!-- Fail the build on CVSS >= 7 (High severity) -->
        <failBuildOnCVSS>7</failBuildOnCVSS>

        <!-- Suppress known false positives (created in Step 2) -->
        <suppressionFiles>
          <suppressionFile>suppression.xml</suppressionFile>
        </suppressionFiles>

        <!-- Output formats: HTML report for humans, JSON for future tooling -->
        <formats>
          <format>HTML</format>
          <format>JSON</format>
        </formats>

        <!-- Report location inside target/ — gitignored automatically -->
        <outputDirectory>${project.build.directory}/dependency-check-report</outputDirectory>

        <!-- NVD API key for faster database updates (set via env var in CI) -->
        <nvdApiKey>${env.NVD_API_KEY}</nvdApiKey>

        <!-- Skip test-scope dependencies (optional — set false to scan them too) -->
        <skipTestScope>false</skipTestScope>
      </configuration>
      <executions>
        <execution>
          <goals>
            <!-- Binds to the 'verify' phase so `mvn verify` triggers the check -->
            <goal>check</goal>
          </goals>
        </execution>
      </executions>
    </plugin>

  </plugins>
</build>
```

### 2. Create `backend/suppression.xml`

On the first run the plugin will almost certainly flag some false positives — CVEs that apply to a different component with a similar name, or vulnerabilities in features of a library that this project doesn't use. The suppression file lets you document and silence them without raising the failure threshold.

Create `backend/suppression.xml` with an empty template (fill in suppressions as you discover false positives):

```xml
<?xml version="1.0" encoding="UTF-8"?>
<!--
  OWASP Dependency-Check suppression file.

  Add a <suppress> element for each known false positive.
  Document WHY it is a false positive — a future reader should be able
  to verify your reasoning without asking you.

  Format:
    <suppress>
      <notes>CVE-XXXX-XXXXX — affects the foo feature of libraryName;
             this project does not use foo. Reviewed 2026-05-23.</notes>
      <cve>CVE-XXXX-XXXXX</cve>
    </suppress>
-->
<suppressions xmlns="https://jeremylong.github.io/DependencyCheck/dependency-suppression.1.3.xsd">

  <!--
    Example suppression (delete when you have a real one):
    <suppress>
      <notes>CVE-2099-00000 - false positive: affects a CLI tool bundled
             with h2.jar, not the JDBC driver used here.</notes>
      <cve>CVE-2099-00000</cve>
    </suppress>
  -->

</suppressions>
```

Commit this file. It will be updated whenever a new false positive is identified.

### 3. Get an NVD API key (strongly recommended for CI)

Without an API key, the plugin falls back to a rate-limited NVD download that can take 30–60 minutes per CI run. A free key reduces this to under 2 minutes.

1. Register at [nvd.nist.gov/developers/request-an-api-key](https://nvd.nist.gov/developers/request-an-api-key).
2. The key arrives by email within a minute.
3. Add it as a GitHub Actions secret named `NVD_API_KEY` in the repository settings (Settings → Secrets and variables → Actions → New repository secret).

### 4. Add the check step to `.github/workflows/ci.yml`

Open `.github/workflows/ci.yml`. In the `backend-ci` job, add a new step **after** the `Build and verify backend` step:

```yaml
      - name: OWASP Dependency Check
        working-directory: ./backend
        env:
          NVD_API_KEY: ${{ secrets.NVD_API_KEY }}
        run: mvn dependency-check:check -DfailBuildOnCVSS=7

      - name: Upload OWASP Dependency Check report
        if: always()   # Upload even if the check failed so you can read the report
        uses: actions/upload-artifact@v4
        with:
          name: dependency-check-report
          path: backend/target/dependency-check-report/
          retention-days: 30
```

The `if: always()` on the upload step is important — if the check fails and stops the job, you still want the HTML report uploaded so you can see which CVE caused the failure.

### 5. Run the check locally to see your first report

```powershell
cd backend
# The first run downloads the NVD database (~600 MB) — takes several minutes
mvn dependency-check:check -DNVD_API_KEY=<your-key>
```

Open the report:

```powershell
Start-Process "backend\target\dependency-check-report\dependency-check-report.html"
```

The report shows:
- Each dependency scanned.
- Any CVEs found, with their CVSS score.
- A "Suppress" button that generates the XML for `suppression.xml`.

If the build fails because CVSS ≥ 7 CVEs are found:
1. Read each finding in the HTML report.
2. If it is a genuine vulnerability in a feature you use, update the dependency to a patched version.
3. If it is a false positive, add a `<suppress>` entry to `suppression.xml` with a clear `<notes>` explanation.

### 6. Cache the NVD database in CI (optional but recommended)

The NVD database is large and re-downloading it on every CI run wastes time and NVD rate limits. Add a cache step to the CI job:

```yaml
      - name: Cache OWASP Dependency-Check database
        uses: actions/cache@v4
        with:
          path: ~/.m2/repository/org/owasp/dependency-check-data
          key: ${{ runner.os }}-owasp-dc-${{ hashFiles('backend/pom.xml') }}
          restore-keys: ${{ runner.os }}-owasp-dc-
```

Place this step before the `OWASP Dependency Check` step.

---

## Checklist

- [ ] Add `org.owasp:dependency-check-maven` plugin to `pom.xml`
  - [ ] `failBuildOnCVSS` set to `7`
  - [ ] `suppressionFiles` references `suppression.xml`
  - [ ] `execution` binds the `check` goal to the `verify` phase
- [ ] Add `mvn dependency-check:check` step to `.github/workflows/ci.yml` (fail on CVSS ≥ 7)
  - [ ] `NVD_API_KEY` secret set in GitHub repository settings
  - [ ] Upload artifact step with `if: always()` to persist the HTML report even on failure
  - [ ] (Optional) NVD database cache step to speed up subsequent runs

---

## How to Verify

**Local build with the check:**

```powershell
cd backend
mvn verify -DNVD_API_KEY=<your-key>
# Expected: BUILD SUCCESS if no CVE >= 7 found (or all are suppressed)
# Expected: BUILD FAILURE with CVE details if a high-severity CVE is present
```

**CI pipeline check:**

1. Push a commit to a branch targeting `main`.
2. Open the GitHub Actions run in the repository → Actions tab.
3. Expand the `backend-ci` job and look for the `OWASP Dependency Check` step.
4. Passing result: the step completes with exit code 0 (green check).
5. The `dependency-check-report` artifact appears under the run's **Artifacts** section regardless of pass/fail.

**Simulate a failure (to confirm the threshold is enforced):**

Temporarily lower the threshold to 0 and confirm the build fails:

```powershell
cd backend
mvn dependency-check:check -DfailBuildOnCVSS=0
# Expected: BUILD FAILURE — almost every dependency will have some informational CVE
```

Restore the threshold to 7 before committing.

---

## Notes / Gotchas

**The first CI run will be slow.**
The NVD database download takes 5–20 minutes even with an API key on the first run. Subsequent runs use the cache (Step 6). Plan for this when estimating CI time.

**False positives are inevitable.**
Dependency-Check matches by library name and version. If two different components share a name, their CVEs cross-contaminate. For example, the H2 database JAR has historically been flagged with CVEs that apply to the H2 console web application — which this project does not expose. Add a well-documented suppression rather than raising the `failBuildOnCVSS` threshold.

**`mvn verify` vs `mvn dependency-check:check`:**
Binding the goal to the `verify` phase means `mvn verify` runs the check automatically. Running `mvn dependency-check:check` directly skips the compile/test phases and only does the scan — useful for a quick check but not a replacement for the full build.

**Keep the plugin version pinned.**
The plugin version should be pinned (`10.0.3` in the example) rather than using a version range. The NVD data format changes occasionally and newer plugin versions handle those changes; unpinned versions can break unexpectedly.

**CVE in `spring-boot-starter` vs in a transitive dependency:**
The report may flag a CVE in a library you've never heard of. That library is a transitive dependency — pulled in by one of your direct dependencies. Check which direct dependency brings it in (`mvn dependency:tree | grep <library>`), then either upgrade the direct dependency (which may pull in a patched transitive version) or add a suppression if the vulnerable code path is not reachable.

**Reference docs:**
- [OWASP Dependency-Check — Maven plugin](https://jeremylong.github.io/DependencyCheck/dependency-check-maven/index.html)
- [NVD API key request](https://nvd.nist.gov/developers/request-an-api-key)
- [OWASP — Using Dependency-Check](https://owasp.org/www-project-dependency-check/)
