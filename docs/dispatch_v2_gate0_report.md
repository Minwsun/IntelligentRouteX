# Dispatch V2 Gate 0 Report

## 1. Build / machine / profile info

- scope: `DISPATCH_V2_GATE0`
- branch: `perf/dispatch-v2-gate0`
- commit SHA: `f690049c587dfb066220a95dd6675f7a08b97625`
- profiles requested this run: `['dispatch-v2-balanced']`
- profiles evaluated from artifacts: `['dispatch-v2-lite', 'dispatch-v2-balanced']`
- started at: `2026-04-20T05:29:06Z`
- finished at: `2026-04-20T05:31:54Z`
- machine profile: `{"hostname": "DESKTOP-SEIH8FO", "platform": "Windows-11-10.0.26200-SP0", "pythonVersion": "3.13.11", "processor": "Intel64 Family 6 Model 158 Stepping 10, GenuineIntel", "machine": "AMD64"}`

## 2. Dry-run + targeted test status

- `balanced-perf`: `passed`
- `balanced-benchmark`: `passed`

## 3. Perf / latency summary

- lite: A/S/cold=615/615/615ms, C/S/hot=255/255/255ms
- balanced: A/S/cold=584/584/584ms, C/S/hot=201/201/201ms

## 4. Quality summary by scenario and baseline

- lite: heavy-rain/S: +6/-1, normal-clear/S: +4/-4, traffic-shock/S: +9/-2
- balanced: heavy-rain/S: +6/-1, normal-clear/S: +4/-4, traffic-shock/S: +9/-2

## 5. Fallback / execution validity summary

- Gate 0 benchmark verdict is based on compact `controlled` artifacts; local-real worker attachment and forecast GPU proof are validated separately.
- lite: heavy-rain/A: selectedAssignmentCount=11, executedAssignmentCount=11, conflictFree=True, workerFallbackRate=0.0, liveSourceFallbackRate=1.0, workerAppliedSources=[]; heavy-rain/C: selectedAssignmentCount=11, executedAssignmentCount=11, conflictFree=True, workerFallbackRate=0.14285714285714285, liveSourceFallbackRate=0.5, workerAppliedSources=['chronos-2', 'greedrl-local', 'routefinder-local', 'tabular-test']; normal-clear/A: selectedAssignmentCount=2, executedAssignmentCount=2, conflictFree=True, workerFallbackRate=0.0, liveSourceFallbackRate=1.0, workerAppliedSources=[]; normal-clear/C: selectedAssignmentCount=2, executedAssignmentCount=2, conflictFree=True, workerFallbackRate=0.14285714285714285, liveSourceFallbackRate=1.0, workerAppliedSources=['chronos-2', 'greedrl-local', 'routefinder-local', 'tabular-test']; traffic-shock/A: selectedAssignmentCount=3, executedAssignmentCount=3, conflictFree=True, workerFallbackRate=0.0, liveSourceFallbackRate=1.0, workerAppliedSources=[]; traffic-shock/C: selectedAssignmentCount=4, executedAssignmentCount=4, conflictFree=True, workerFallbackRate=0.14285714285714285, liveSourceFallbackRate=0.5, workerAppliedSources=['chronos-2', 'greedrl-local', 'routefinder-local', 'tabular-test']
- balanced: heavy-rain/A: selectedAssignmentCount=11, executedAssignmentCount=11, conflictFree=True, workerFallbackRate=0.0, liveSourceFallbackRate=1.0, workerAppliedSources=[]; heavy-rain/C: selectedAssignmentCount=11, executedAssignmentCount=11, conflictFree=True, workerFallbackRate=0.14285714285714285, liveSourceFallbackRate=0.5, workerAppliedSources=['chronos-2', 'greedrl-local', 'routefinder-local', 'tabular-test']; normal-clear/A: selectedAssignmentCount=2, executedAssignmentCount=2, conflictFree=True, workerFallbackRate=0.0, liveSourceFallbackRate=1.0, workerAppliedSources=[]; normal-clear/C: selectedAssignmentCount=2, executedAssignmentCount=2, conflictFree=True, workerFallbackRate=0.14285714285714285, liveSourceFallbackRate=1.0, workerAppliedSources=['chronos-2', 'greedrl-local', 'routefinder-local', 'tabular-test']; traffic-shock/A: selectedAssignmentCount=3, executedAssignmentCount=3, conflictFree=True, workerFallbackRate=0.0, liveSourceFallbackRate=1.0, workerAppliedSources=[]; traffic-shock/C: selectedAssignmentCount=4, executedAssignmentCount=4, conflictFree=True, workerFallbackRate=0.14285714285714285, liveSourceFallbackRate=0.5, workerAppliedSources=['chronos-2', 'greedrl-local', 'routefinder-local', 'tabular-test']

## 6. Verdict + next decision

- verdict: `FAIL`
- known limits: `['heavy-rain/A liveSourceFallbackRate too high', 'normal-clear/A liveSourceFallbackRate too high', 'normal-clear/C liveSourceFallbackRate too high', 'traffic-shock/A liveSourceFallbackRate too high']`
- next decision: Stop expansion and return to runtime/profile optimization before any new lane.
