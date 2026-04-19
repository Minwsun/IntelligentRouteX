# Dispatch V2 Gate 0 Report

## 1. Build / machine / profile info

- scope: `DISPATCH_V2_GATE0`
- branch: `perf/dispatch-v2-gate0`
- commit SHA: `b8a4087f08a055c7db0ad65e52a1162fd0ee661f`
- profiles: `['dispatch-v2-lite', 'dispatch-v2-balanced']`
- started at: `2026-04-19T21:20:19Z`
- finished at: `2026-04-19T21:20:19Z`
- machine profile: `{"hostname": "DESKTOP-SEIH8FO", "platform": "Windows-11-10.0.26200-SP0", "pythonVersion": "3.13.11", "processor": "Intel64 Family 6 Model 158 Stepping 10, GenuineIntel", "machine": "AMD64"}`

## 2. Dry-run + targeted test status

- `dry-run-phase3`: `passed`
- `dry-run-release`: `passed`
- `targeted-tests`: `passed`
- `lite-perf`: `passed`
- `lite-benchmark`: `passed`
- `balanced-perf`: `passed`
- `balanced-benchmark`: `passed`

## 3. Perf / latency summary

- lite: no perf artifacts.
- balanced: no perf artifacts.

## 4. Quality summary by scenario and baseline

- lite: no comparison artifacts.
- balanced: no comparison artifacts.

## 5. Fallback / execution validity summary

- lite: no baseline artifacts.
- balanced: no baseline artifacts.

## 6. Verdict + next decision

- verdict: `PASS_WITH_LIMITS`
- known limits: `['Dry-run only; Gate 0 verdict is not a real benchmark conclusion.']`
- next decision: Proceed to the next compact lane only if the verdict is PASS or PASS_WITH_LIMITS.
