# Dispatch V2 Route Vector Training Contract

Student training uses normalized decision logs plus route-vector traces.

## Builder Inputs

- `decision_stage_input`
- `decision_stage_output`
- `decision_stage_join`
- `dispatch_execution`
- `dispatch_outcome`
- `route_leg_vector_trace`
- `route_vector_summary_trace`

## Builder Outputs

- `stage_inputs.jsonl`
- `stage_outputs.jsonl`
- `stage_joins.jsonl`
- `dispatch_execution.jsonl`
- `dispatch_outcomes.jsonl`
- `route_vectors.jsonl`
- `dataset_manifest.json`

## Sample Keys

Each dataset row should preserve:

- `traceId`
- `tickId`
- `stageName`
- `brainType`
- `authorityMode`
- `selectedIds`
- `outcomeRefs`
- `routeVectorRefs`

## Exclusions

Do not include:

- raw prompts
- raw provider response bodies
- secrets
- network headers
