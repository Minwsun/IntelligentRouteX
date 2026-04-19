# Teacher Trace Contract

## Required Families

- `tabular_teacher_trace`
- `routefinder_teacher_trace`
- `greedrl_teacher_trace`
- `forecast_teacher_trace`

## Required Envelope Fields

- `schemaVersion`
- `runId`
- `sliceId`
- `tickId`
- `traceId`
- `worldTime`
- `seed`
- `teacherFamily`
- `stageName`

## Payload Rules

- payloads wrap existing Dispatch V2 stage outputs when available
- traces must preserve causal linkage to the dispatch observation that triggered them
- traces must be append-only and replay-safe
- V1 does not infer hidden teacher labels beyond what Dispatch V2 already exposes
