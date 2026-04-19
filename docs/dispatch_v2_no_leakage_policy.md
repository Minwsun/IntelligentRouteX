# Dispatch V2 No Leakage Policy

## Principle

The distillation rail must strictly separate:

- decision-time observations
- teacher-time scores and proposals
- post-decision outcomes

## Allowed

Observation may contain only facts visible at decision time.

Teacher traces may contain only teacher outputs produced at decision time.

Outcome rows may contain only post-dispatch realized labels and provenance.

## Forbidden

Observation must not contain:

- actual completion time
- actual merchant wait
- actual route outcome
- future traffic or weather
- final selection copied backward into upstream feature payload

Teacher traces must not contain:

- realized outcome labels
- final selector winner labels unless the teacher itself produced them at decision time
- future state snapshots

Candidate rows must not be backfilled with:

- selected status as an upstream feature
- future completion metrics
- post-decision queue resolution

## Replay Policy

Replay data is excluded from live distillation Bronze in v1 to avoid contamination and duplicate lineage.

## Validation

Validation must fail hard when:

- an observation row contains an outcome field
- a teacher trace contains future-only data
- an outcome value is merged into a decision-time feature row
- replay harvest mixes with live or simulation data
