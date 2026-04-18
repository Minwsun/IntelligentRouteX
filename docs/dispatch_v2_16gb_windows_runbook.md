# Dispatch V2 16GB Windows Stability Runbook

Use this runbook on a constrained 16GB Windows machine to close boot, runtime, benchmark, and Phase 3 validation work without forcing one large JVM run. This machine is a staged validation machine, not the final authority benchmark machine.

## Operating Modes

Keep the workstation in one mode at a time.

### `dev`

- IDE allowed
- browser light only
- small tests only
- no large-scale runs
- no soak runs
- do not materialize models while benchmarks are running

### `validation`

- Windows page file enabled and machine rebooted after any page-file change
- browser, Docker, WSL, and other heavy apps closed
- only required workers running
- one test or benchmark family at a time
- keep artifact directories between runs for review

Pinned machine prerequisites:

- Windows page file set to `System managed`, or manual min `16384 MB` and max `32768-49152 MB`
- system drive free space at least `40 GB`
- machine rebooted after changing the page file
- no concurrent materialization and benchmark runs

## Boot And Readiness Sequence

Always boot in this order.

### 1. Verify materialized local models

Check the expected promoted model state for:

- tabular
- routefinder
- chronos
- greedrl

Use [local_model_rematerialization_runbook.md](/E:/Code%20_Project/IntelligentRouteX/docs/local_model_rematerialization_runbook.md) if fingerprints drift, promoted files are missing, or a worker fails local-load checks.

### 2. Verify worker contracts individually

For each required worker, verify:

- `/health`
- `/ready`
- `/version`

Hard rule:

- if a worker is not `ready=true` with the expected contract, do not treat that machine state as release-ready
- deterministic fallback is still acceptable for bounded development or validation where current runtime policy allows it

### 3. Verify the ops readiness snapshot

Check the existing readiness output or `/actuator/info` and confirm:

- worker readiness matches the expected profile
- fingerprints match the intended local materialization
- live-source states are explicit
- no secret value appears in logs or readiness output

### 4. Boot the main app

Boot only after the model, worker, and readiness checks above are clean.

### 5. Run one tiny dispatch smoke

Run one cold-compatible smoke dispatch and confirm:

- all 12 stages are present in canonical order
- the result is conflict-free
- there is no boot-time regression before larger validation work starts

## Validation Ladder

Run tests in increasing cost order. Stop at the first real regression.

### Tier 1: integration and small contracts

```powershell
.\gradlew.bat --no-daemon test --tests com.routechain.v2.integration.*
```

### Tier 2: benchmark support

```powershell
.\gradlew.bat --no-daemon test --tests com.routechain.v2.perf.*
.\gradlew.bat --no-daemon test --tests com.routechain.v2.benchmark.*
```

### Tier 3: certification and Phase 3 support

```powershell
.\gradlew.bat --no-daemon test --tests com.routechain.v2.certification.*
.\gradlew.bat --no-daemon test --tests com.routechain.v2.chaos.*
```

### Tier 4: full suite only if the machine stays stable

```powershell
.\gradlew.bat --no-daemon test
```

If Tier 4 still fails because the machine cannot sustain the JVM:

- do not block progress on that machine alone
- use targeted package passes plus the dedicated Phase 3 and release verification scripts as the local gate
- move the final full-suite validation to a stronger machine

## Conservative Runtime Defaults

Keep local runtime behavior conservative until benchmark closure is complete.

- `budgetEnforcementEnabled=false`
- telemetry enabled
- hot-start enabled
- ML workers enabled only when the current scenario needs them
- live traffic and weather enabled only when the current scenario needs them
- fallback paths always available

Workload rule on this machine:

- prefer `S` and `M`
- run `L` only as an isolated benchmark step
- treat `XL` as an authority-machine target unless the current machine proves stable enough

## Benchmark And Certification Order

Run the local validation stack in this order.

### 1. Perf smoke

```powershell
python scripts/run_dispatch_v2_perf.py --baseline A --size S --mode cold --output-dir artifacts/perf
python scripts/run_dispatch_v2_perf.py --baseline A --size S --mode warm --output-dir artifacts/perf
python scripts/run_dispatch_v2_perf.py --baseline C --size S --mode hot --output-dir artifacts/perf
```

### 2. Quality smoke

```powershell
python scripts/run_dispatch_v2_benchmark.py --baseline all --size S --scenario-pack normal-clear --execution-mode controlled --output-dir artifacts/benchmark
python scripts/run_dispatch_v2_benchmark.py --baseline all --size S --scenario-pack heavy-rain --execution-mode controlled --output-dir artifacts/benchmark
```

### 3. Ablation smoke

```powershell
python scripts/run_dispatch_v2_ablation.py --component tabular --size S --scenario-pack normal-clear --execution-mode controlled --output-dir artifacts/benchmark
python scripts/run_dispatch_v2_ablation.py --component routefinder --size S --scenario-pack normal-clear --execution-mode controlled --output-dir artifacts/benchmark
python scripts/run_dispatch_v2_ablation.py --component greedrl --size S --scenario-pack normal-clear --execution-mode controlled --output-dir artifacts/benchmark
python scripts/run_dispatch_v2_ablation.py --component forecast --size S --scenario-pack normal-clear --execution-mode controlled --output-dir artifacts/benchmark
```

### 4. Phase 3 closure path

```powershell
python scripts/verify_dispatch_v2_phase3.py --dry-run
python scripts/run_dispatch_v2_large_scale.py --baseline C --size M --scenario-pack normal-clear --execution-mode controlled --output-dir artifacts/large-scale
python scripts/run_dispatch_v2_soak.py --duration 1h --size M --scenario-pack normal-clear --execution-mode controlled --sample-count-override 3 --output-dir artifacts/soak
python scripts/run_dispatch_v2_chaos.py --fault tabular-unavailable --size M --scenario-pack worker-degradation --execution-mode controlled --output-dir artifacts/chaos
```

### 5. Full Phase 3 verification only after the smoke path is clean

```powershell
python scripts/verify_dispatch_v2_phase3.py
```

## Phase 3 Close Criteria

Treat Phase 3 as closed on the local machine only when all of the following are true:

- app boot succeeds
- worker readiness checks pass for the intended profile
- the tiny dispatch smoke passes
- all 12 stages remain correct
- selected and executed assignments stay conflict-free
- replay isolation and warm boot still pass
- targeted `integration`, `perf`, `benchmark`, `certification`, and `chaos` Gradle groups pass on this machine or on a stronger validation machine
- large-scale, soak, and chaos runner smokes write real JSON and Markdown artifacts
- `python scripts/verify_dispatch_v2_phase3.py` passes on at least one machine with enough JVM capacity

## Release Handoff

After Phase 3 closes, hand off to RC work in this order:

1. authority benchmark runs
2. threshold pinning
3. RC-0 report
4. `python scripts/verify_dispatch_v2_release.py`
5. release decision: `go`, `canary-only`, or `no-go`

Retain these artifact directories for review:

- `artifacts/perf/`
- `artifacts/benchmark/`
- `artifacts/large-scale/`
- `artifacts/soak/`
- `artifacts/chaos/`

## Practical Rule

On a 16GB Windows machine, the correct strategy is:

- stabilize the machine first
- run validation by tier
- preserve artifacts from every smoke and gate
- move full-suite JVM stress and long soak to a stronger machine

Do not treat a single failed all-in-one `gradlew test` run on this machine as the only truth if the targeted validation ladder and dedicated verification gates are clean.
