# Model Assets

`model-registry-v1.json` is the source of truth for runtime model bundles and external neural priors.

Expected folders:

- `models/onnx/` for ETA/ranker/risk champion models.
- `models/routefinder/` for RouteFinder checkpoints.
- `models/rrnco/` for RRNCO challenger checkpoints.

Use `scripts/model/download_models.ps1` to download/check model assets and write `models/download-manifest.json`.

Neural prior sidecar:

- Start with `python scripts/model/neural_route_prior_service.py`.
- Set `ROUTECHAIN_NEURAL_PRIOR_CMD` to a real inference command that reads JSON from STDIN and prints JSON to STDOUT.
- Backend endpoint override: JVM prop `-Droutechain.neuralPrior.endpoint=http://127.0.0.1:8094/prior`.
