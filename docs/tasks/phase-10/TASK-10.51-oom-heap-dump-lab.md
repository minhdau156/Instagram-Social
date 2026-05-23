# TASK-10.51 — Diagnose an `OutOfMemoryError` from a heap dump (hands-on lab)

## Overview

This is a hands-on exercise: deliberately trigger an `OutOfMemoryError: Java heap space` on a throwaway endpoint, let the JVM write an `.hprof` heap dump automatically, then open the dump in Eclipse MAT (or VisualVM) and identify the object class retaining the most memory. The throwaway endpoint is deleted before you commit anything. The goal is to leave this lab having seen a real heap dump and knowing how to read a Dominator Tree — a skill you will need the first time a production service crashes with an OOM.

This task modifies source code temporarily for the exercise only. **The temporary endpoint must be deleted before any commit is made.**

---

## Level

Warm-up · Pairs with [TASK-10.11 — Streaming large exports](TASK-10.11-streaming-large-exports.md), [TASK-10.27 — Actuator & Micrometer](TASK-10.27-actuator-micrometer.md), and [TASK-10.49 — Troubleshooting runbook](TASK-10.49-troubleshooting-runbook.md)

---

## Why

When the JVM runs out of heap memory it throws `OutOfMemoryError`. The stack trace tells you which allocation failed, but that is almost never the leaking code — it is just the unlucky allocation that happened to request memory when none was left. The actual leak could be a cache that grows without bound, a `static` list that accumulates objects, or a connection that never gets closed. The only reliable tool for finding the real culprit is a heap dump: a snapshot of every object in the JVM at the moment of the crash. Reading one is a learnable skill, and the only way to get comfortable with it is to do it at least once in a controlled, safe environment — which is exactly what this lab provides.

---

## Prerequisites

- The backend must compile and start locally: `cd backend && mvn spring-boot:run`.
- [TASK-10.27](TASK-10.27-actuator-micrometer.md) — `/actuator/metrics/jvm.memory.used` must be reachable. You will watch heap usage climb in real time before the crash.
- [TASK-10.49](TASK-10.49-troubleshooting-runbook.md) — the troubleshooting runbook will be updated with a "How to read a heap dump" entry at the end of this task.
- **Tools to install before starting:**
  - **Eclipse MAT (Memory Analyzer Tool)** — free, standalone, no IDE required. Download from [https://eclipse.dev/mat/](https://eclipse.dev/mat/). Alternatively, use **VisualVM** (`jvisualvm` — bundled with JDK 8, standalone download for JDK 11+; see [https://visualvm.github.io/](https://visualvm.github.io/)).
  - A valid JWT token for your local user (to call the test endpoint).
- **Concepts to skim:**
  - **Java heap** — the memory region where all objects live. The JVM grows it up to `-Xmx` (max heap). When even a minor GC cannot free enough space for a new allocation, the JVM throws `OutOfMemoryError: Java heap space`.
  - **`-XX:+HeapDumpOnOutOfMemoryError`** — JVM flag that tells the JVM to write an `.hprof` file automatically when the OOM occurs.
  - **`.hprof` file** — a binary snapshot of every object currently on the heap, including which objects hold references to which other objects.
  - **Dominator Tree** — a tree representation of object retention. If object A is the only path keeping object B alive, A "dominates" B. The object with the highest "retained heap" at the root of the tree is the likely memory leak.
  - **`GC overhead limit exceeded`** — a different `OutOfMemoryError` subtype that fires when the JVM spends more than 98% of CPU time in GC with less than 2% reclaimed. It means the heap is not large enough, not necessarily that there is a leak.

---

## Files to Create / Modify

```
# Temporary (delete before committing — never ship this endpoint)
backend/src/main/java/com/instagram/adapter/in/web/DevController.java    (new — DELETE AFTER LAB)

# Permanent update
docs/troubleshooting.md                                                   (modify — add heap-dump entry)
```

---

## Step-by-Step

### 1. Add the JVM flags for heap-dump-on-crash and a small max heap

You need two things: a maximum heap small enough to OOM quickly (256 MB), and the JVM flag that writes the dump file automatically. Do not set these permanently — use them only for this lab session.

**Option A — run directly from PowerShell (recommended for this lab):**

```powershell
# From the repo root
cd backend
$env:MAVEN_OPTS = "-Xmx256m -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=./heapdump.hprof"
mvn spring-boot:run
```

**Option B — via the `spring-boot-maven-plugin` JVM args (if you prefer not to use `MAVEN_OPTS`):**

Temporarily add to `backend/pom.xml` inside the `spring-boot-maven-plugin` configuration:

```xml
<plugin>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-maven-plugin</artifactId>
    <configuration>
        <jvmArguments>
            -Xmx256m
            -XX:+HeapDumpOnOutOfMemoryError
            -XX:HeapDumpPath=./heapdump.hprof
        </jvmArguments>
    </configuration>
</plugin>
```

Remove this `<jvmArguments>` block from `pom.xml` after the lab.

Wait for:
```
Started SocialMediaApplication in X.XXX seconds
```

---

### 2. Create the temporary OOM endpoint

Create a new controller file **for this lab only** at:

```
backend/src/main/java/com/instagram/adapter/in/web/DevController.java
```

Paste the following content exactly as shown. Read the `WARNING` comment — this endpoint must never be merged or deployed:

```java
package com.instagram.adapter.in.web;

import java.util.ArrayList;
import java.util.List;

import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * WARNING: DEVELOPMENT / LAB ONLY.
 * This controller must be deleted before making any commit.
 * It exists solely for the TASK-10.51 heap-dump lab.
 *
 * The @Profile("local") annotation prevents this endpoint from starting
 * if the app is accidentally booted with a non-local profile,
 * but the file still must not be committed.
 */
@Profile("local")
@RestController
@RequestMapping("/dev")
public class DevController {

    /**
     * Appends 1 MB chunks to a list until the JVM heap is exhausted.
     * The JVM will throw OutOfMemoryError and (with -XX:+HeapDumpOnOutOfMemoryError)
     * write heapdump.hprof to the working directory before crashing.
     *
     * DO NOT call this endpoint in any environment other than a local lab session.
     */
    @PostMapping("/oom")
    public ResponseEntity<String> triggerOom() {
        List<byte[]> leak = new ArrayList<>();
        // Keep allocating 1 MB chunks until the heap is full
        while (true) {
            leak.add(new byte[1024 * 1024]); // 1 MB per iteration
        }
    }
}
```

The `@Profile("local")` annotation limits this endpoint to the `local` Spring profile. It will not start if the app is booted with `prod` or `test`. That is a safety net — the real safeguard is deleting the file before you commit.

---

### 3. Restart the backend (it must pick up the new file)

Stop the running backend (`Ctrl+C` in the Maven terminal) and start it again with the same `MAVEN_OPTS`:

```powershell
# MAVEN_OPTS is still set from Step 1 in the same PowerShell session
mvn spring-boot:run
```

Confirm the endpoint is registered by looking for this in the startup log:

```
Mapped "{[/dev/oom],methods=[POST]}"
```

---

### 4. Watch heap usage before triggering the OOM

Open a second terminal. Poll the Actuator heap metric every two seconds. This shows you the heap climbing in real time once you trigger the endpoint.

```powershell
# Requires: backend is running, Actuator is on /actuator (default)
# Replace TOKEN with a valid JWT for your local user
$token = "Bearer <your-jwt-here>"

while ($true) {
    $result = Invoke-RestMethod -Uri "http://localhost:8080/actuator/metrics/jvm.memory.used" `
        -Headers @{ Authorization = $token }
    $used = ($result.measurements | Where-Object { $_.statistic -eq "VALUE" }).value
    Write-Host "Heap used: $([math]::Round($used / 1MB, 1)) MB  $(Get-Date -Format 'HH:mm:ss')"
    Start-Sleep -Seconds 2
}
```

You should see a stable reading (typically 80–150 MB at idle with a 256 MB max).

---

### 5. Trigger the OOM endpoint

In a third terminal, call the endpoint. The JVM will crash within a few seconds:

```powershell
$token = "Bearer <your-jwt-here>"
Invoke-RestMethod -Method Post `
    -Uri "http://localhost:8080/dev/oom" `
    -Headers @{ Authorization = $token }
```

In the monitoring terminal (Step 4) you will see heap usage climbing rapidly:

```
Heap used: 120.3 MB  14:22:01
Heap used: 178.6 MB  14:22:03
Heap used: 234.1 MB  14:22:05
```

Shortly after, the backend terminal will print something like:

```
java.lang.OutOfMemoryError: Java heap space
Dumping heap to ./heapdump.hprof ...
Heap dump file created [258765432 bytes in 3.421 secs]
```

The backend process will exit. The file `heapdump.hprof` now exists in `backend/` (or wherever you ran Maven from).

---

### 6. Confirm the dump file exists

```powershell
# From the backend directory
Get-Item heapdump.hprof | Select-Object Name, Length, LastWriteTime
```

The file will be between 200 MB and 300 MB in size (it is a full snapshot of the 256 MB heap).

---

### 7. Open the heap dump in Eclipse MAT

1. Launch Eclipse MAT (`MemoryAnalyzer.exe` on Windows).
2. **File → Open Heap Dump** → navigate to `backend/heapdump.hprof` → click **Open**.
3. MAT will parse and index the dump (this takes 30–90 seconds for a 256 MB file).
4. When prompted to run a report, choose **Leak Suspects Report** → click **Finish**.

The Leak Suspects Report opens automatically. It will name the most likely leak. For this lab the report will point to the `byte[]` array being held by an `ArrayList` inside `DevController.triggerOom`.

**To see the raw data yourself, use the Dominator Tree:**

1. Click the **Dominator Tree** icon (or go to **Window → Heap Dump Details → Dominator Tree**).
2. The top row is the object with the highest **Retained Heap** (the total bytes freed if this object were garbage-collected).
3. Expand the top entry to see what it references. You will see a chain: `Thread → ArrayList → byte[][]`.

---

### 8. Note the difference between the two OOM subtypes

Add a short note to your personal notes (or to `docs/troubleshooting.md` in Step 10 below):

| Error Message | Meaning | Typical Cause |
|---|---|---|
| `OutOfMemoryError: Java heap space` | A single allocation failed because there is no free space | Real memory leak, or heap simply too small for the workload |
| `OutOfMemoryError: GC overhead limit exceeded` | GC is running constantly but reclaiming almost nothing | Heap too small for the object graph; not necessarily a leak |

The heap-space variant almost always points to a real leak — find it with a heap dump. The GC-overhead variant often just means `-Xmx` needs to be raised.

---

### 9. Delete the temporary endpoint

**This step is mandatory before any `git add` or `git commit`.**

```powershell
Remove-Item backend/src/main/java/com/instagram/adapter/in/web/DevController.java
```

Confirm it is gone:

```powershell
Test-Path backend/src/main/java/com/instagram/adapter/in/web/DevController.java
```

Expected: `False`

Also delete the `.hprof` file — it is large and contains heap contents (potentially including sensitive data from in-memory objects):

```powershell
Remove-Item backend/heapdump.hprof -ErrorAction SilentlyContinue
```

Add `.hprof` to `.gitignore` if it is not already there:

```powershell
Add-Content .gitignore "`n*.hprof"
```

---

### 10. Add the "How to read a heap dump" entry to `docs/troubleshooting.md`

Open `docs/troubleshooting.md` (created in TASK-10.49) and confirm or add the following section at the end:

```markdown
## How to read a heap dump

When the JVM crashes with `OutOfMemoryError`, a heap dump lets you find the object holding onto all the memory.

**Enable auto-dump on OOM (add to JVM flags):**
```powershell
$env:MAVEN_OPTS = "-XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=./heapdump.hprof"
```

**Where the file lands:**
The directory where the JVM process was started (the `backend/` directory when using
`mvn spring-boot:run`), or the path set in `-XX:HeapDumpPath`.

**How to open the file:**
1. Download [Eclipse MAT](https://eclipse.dev/mat/) (free, no IDE required).
2. File → Open Heap Dump → select the `.hprof` file.
3. When prompted, run the **Leak Suspects Report** for an automatic summary.
4. For manual investigation: open the **Dominator Tree** view.

**What to look for:**
The object at the top of the Dominator Tree has the largest **Retained Heap** —
the bytes that would be freed if that object were garbage-collected.
Expand it to trace the reference chain back to the root cause.

**Never commit `.hprof` files** — they can be hundreds of MB and may contain
sensitive runtime data (tokens, user objects, cached passwords in memory).
Add `*.hprof` to `.gitignore`.
```

---

## Checklist

- [ ] Start the app with a small heap and auto-dump on crash: `-Xmx256m -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=./heapdump.hprof`
- [ ] Add a temporary `/dev/oom` endpoint that appends to an in-memory `List<byte[]>` in a loop until the heap is exhausted — **delete it after the exercise** (it must never ship)
- [ ] Watch `/actuator/metrics/jvm.memory.used` (TASK-10.27) climb toward the max right before the crash
- [ ] Open the generated `heapdump.hprof` in Eclipse MAT (or VisualVM) and use the **Dominator Tree** / **Leak Suspects** report to find the object retaining the most memory
- [ ] Note the difference between `OutOfMemoryError: Java heap space` and `GC overhead limit exceeded`, and decide which points to a real leak vs. a heap that's simply too small
- [ ] Add a short "How to read a heap dump" entry to `docs/troubleshooting.md` (TASK-10.49): how to enable the dump, where it lands, and how to open the dominator tree

---

## How to Verify

**1. Endpoint was deleted**

```powershell
Test-Path backend/src/main/java/com/instagram/adapter/in/web/DevController.java
```

Expected: `False`

**2. `.hprof` is in `.gitignore`**

```powershell
Get-Content .gitignore | Select-String "hprof"
```

Expected: a line containing `*.hprof`.

**3. Heap dump entry exists in the troubleshooting runbook**

```powershell
Get-Content docs/troubleshooting.md | Select-String "heap dump"
```

Expected: at least one matching line.

**4. You can answer these questions (manual verification)**

- What class appears at the top of the Dominator Tree in MAT? (Answer: `byte[]` or `ArrayList`, depending on how MAT groups them.)
- What is the Retained Heap value, approximately? (It should be close to 256 MB — the full heap.)
- What is the reference chain from the GC root to the leaking `byte[]`? (Answer: `Thread → stack frame → DevController.triggerOom → ArrayList leak → byte[][]`.)

**Passing result:** `DevController.java` does not exist in the codebase, `.hprof` is in `.gitignore`, the troubleshooting runbook has a heap-dump section, and you can describe the Dominator Tree results from your lab run.

---

## Notes / Gotchas

**"MAT says it cannot open the file — 'not enough memory'."**
MAT itself needs heap space to index the dump. By default it starts with 1 GB. If the dump is 256 MB and your machine has limited RAM, increase MAT's own heap by editing `MemoryAnalyzer.ini` in the MAT installation folder:
```
-Xmx1g
```
Change to `-Xmx2g` or more.

**"The dump file was not created — the backend just crashed with no file."**
Check that the Maven process was started with `MAVEN_OPTS` set in the *same* PowerShell session. PowerShell environment variables are scoped to the session. Open a new session and the variable will be gone. Verify with:
```powershell
$env:MAVEN_OPTS
```

**"VisualVM shows a different view than MAT — where is the Dominator Tree?"**
In VisualVM, after loading the heap dump, go to **File → Load** → select the `.hprof`. Then click **Classes** to see the largest object types by instance count, or **Summary** for totals. The Dominator Tree as a named view exists in Eclipse MAT but not directly in VisualVM. For a beginner, MAT's Leak Suspects report is easier to read.

**"The backend crashes before I can call the endpoint."**
256 MB is enough for Spring Boot to start (Spring Boot idles at roughly 100–180 MB with all the phases loaded). If your machine is slow or has other memory pressure, raise the limit slightly for the lab: `-Xmx384m`. The OOM will take a few more iterations to trigger but the lab still works.

**"I accidentally committed `DevController.java`."**
Remove it from the last commit using `git reset HEAD~1` (if not yet pushed) or by creating a new commit that deletes the file. Never force-push to `main`. Alert the team so no one merges the branch containing it.

**"What is a retained heap vs. a shallow heap?"**
- **Shallow heap**: the memory occupied by the object itself (its fields only).
- **Retained heap**: the total memory freed if the object and everything it exclusively keeps alive were garbage-collected.
In a leak investigation, **retained heap** is the useful number — it tells you how much memory you get back by fixing the leak at this point in the reference chain.

**Reference docs:**
- [Eclipse MAT — Getting Started](https://eclipse.dev/mat/documentation/heapdump.html)
- [VisualVM — Analyzing Heap Dumps](https://visualvm.github.io/documentation.html)
- [Oracle JVM — `OutOfMemoryError` types](https://docs.oracle.com/en/java/javase/21/troubleshoot/troubleshoot-memory-leaks.html)

**Related tasks:**
- [TASK-10.11](TASK-10.11-streaming-large-exports.md) — covers preventing OOM on large data exports using streaming
- [TASK-10.27](TASK-10.27-actuator-micrometer.md) — exposes the `/actuator/metrics/jvm.memory.used` endpoint watched in Step 4
- [TASK-10.49](TASK-10.49-troubleshooting-runbook.md) — the troubleshooting runbook updated in Step 10
