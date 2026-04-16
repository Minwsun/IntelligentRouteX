# Dispatch V2 Model Registry

Model entries are stored in `services/models/model-manifest.yaml`.

Each entry must include:

- `schemaVersion`
- `model_name`
- `model_version`
- `artifact_digest`
- `rollback_artifact_digest`
- `runtime_image`
- `startup_warmup_request`
- `compatibility_contract_version`
- `min_supported_java_contract_version`

