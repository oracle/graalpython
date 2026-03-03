# lib-python/ — VENDORED CPYTHON STDLIB + TESTS

## OVERVIEW
Vendored/adapted CPython standard library and CPython test suite used for compatibility validation.

## WHERE TO LOOK
| Task | Location | Notes |
|------|----------|-------|
| Stdlib sources | `3/` | CPython stdlib tree. |
| CPython tests | `3/test/` | Large upstream test suite; GraalPy runs selected tests via tag files. |
| Local lint configs | `3/test/.ruff.toml`, `3/test/**/mypy.ini` | Primarily for the vendored tests area. |

## CONVENTIONS
- Treat this tree as upstream-ish: changes should be minimal, well-justified, and ideally correspond to upstream fixes.

## ANTI-PATTERNS
- Don’t apply sweeping refactors or formatting changes here; it makes rebasing/upstream sync harder.
