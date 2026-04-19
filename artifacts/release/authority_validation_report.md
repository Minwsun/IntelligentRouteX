# Dispatch V2 Authority Validation Report

- status: `NOT_RUN`
- reason: `V1_PORTABLE_CLOSED` has not been achieved yet

## Prerequisites Still Open

- portable bundle must rebuild cleanly after `gradlew clean`
- launcher smoke must pass on the bundle candidate
- clean-machine validation must pass end to end

## Planned Authority Commands

- `python scripts/verify_dispatch_v2_phase3.py --include-full-suite`
- `.\gradlew.bat --no-daemon test`
- large-scale `L`
- `XL` if supported
- long soak
- full chaos matrix
- final `python scripts/verify_dispatch_v2_release.py`
