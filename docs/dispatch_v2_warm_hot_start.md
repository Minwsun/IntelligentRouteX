# Dispatch V2 Warm and Hot Start

## Warm Start

Boot preloads:

- model artifacts
- traffic profiles
- corridor map
- zone state/history
- weather cache seed
- graph cache seed
- previous snapshots when available

## Hot Start

Per tick reuse is allowed for:

- pair graph
- cluster graph
- bundle pool
- route pool
- forecast state
- traffic/weather state
- incumbent routes

