# Dispatch V2 Contracts

All persisted and exchanged contracts are schema-versioned from day 1.

## Initial Java Contracts

- `DispatchV2Request`
- `DispatchV2Result`
- `EtaContext`
- `EtaEstimateRequest`
- `EtaEstimate`
- `WarmStartState`
- `HotStartState`
- `DecisionLogRecord`

## Rule

No contract may change shape without a `schemaVersion` bump and matching replay/snapshot migration handling.
