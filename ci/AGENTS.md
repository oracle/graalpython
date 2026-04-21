# ci/ — CI DEFINITIONS

## OVERVIEW
Jsonnet/libsonnet CI definitions consumed by GitHub Actions matrix generation.

## WHERE TO LOOK
| Task | Location | Notes |
|------|----------|-------|
| Main pipelines | `../ci.jsonnet`, `python-gate.libsonnet` | Defines gates/tags executed in CI. |
| Shared constants | `constants.libsonnet` | Shared settings used across CI definitions. |
| Helpers | `utils.libsonnet` | Common functions/macros. |

## CONVENTIONS
- GitHub workflows typically call `ci-matrix-gen.yml`, which evaluates `ci.jsonnet` and these libraries.

## ANTI-PATTERNS
- Don’t encode secrets or environment-specific paths here.
- Keep changes compatible with matrix generation (small diffs, deterministic output).
- NEVER edit CI files in `./graal`
