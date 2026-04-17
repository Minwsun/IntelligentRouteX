# ml-routefinder-worker

RouteFinder worker for bounded route refinement and route alternatives.

Boot now depends on a materialized local model directory resolved from
`services/models/model-manifest.yaml`.

Local model workflow:

- materialize a local model with `scripts/materialize_routefinder_local.ps1` or `scripts/materialize_routefinder_local.sh`
- the materializer now promotes a pinned upstream RouteFinder checkpoint from `ai4co/routefinder`
- worker `/ready` returns `true` only after local model discovery, fingerprint verification, runtime load, and warmup succeed
- worker `/version` reports local load metadata without changing the existing Java bootstrap contract
- worker inference remains offline-safe after materialization; it never downloads during boot or request handling
