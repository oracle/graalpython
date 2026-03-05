# mx.graalpython/ — MX BUILD TOOLING

## OVERVIEW
`mx` suite definition and Python utilities used for building, testing, benchmarking, and CI orchestration.

## WHERE TO LOOK
| Task | Location | Notes |
|------|----------|-------|
| Suite definition | `suite.py` | Defines projects, imports, distributions, version metadata. |
| mx commands | `mx_graalpython.py` | Implements custom `mx` commands (e.g., test runners). |
| Import helpers | `mx_graalpython_import.py` | `mx sforceimport`-related logic. |
| Bench tooling | `mx_graalpython_benchmark.py`, `mx_graalpython_python_benchmarks.py` | Performance harness integration. |
| Bisect tooling | `mx_graalpython_bisect.py` | Benchmark/test bisect support. |

## CONVENTIONS
- Pylint is enforced (via pre-commit) only for `mx.graalpython/*.py`.
- Treat `suite.py` as the authoritative dependency/version map; CI workflows typically call into these mx gates.

## ANTI-PATTERNS
- Don’t re-implement build/test flows in ad-hoc scripts when an `mx` command already exists.
- Avoid adding heavyweight dependencies here; this code runs in many CI contexts.
