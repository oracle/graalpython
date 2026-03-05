# com.oracle.graal.python.test/ — TEST SUITES

## OVERVIEW
Python-level tests, tag files, and harnesses used by `mx python-gate` and `mx graalpytest`.

## WHERE TO LOOK
| Task | Location | Notes |
|------|----------|-------|
| Python tests | `src/tests/` | Main Python tests; includes C-API related tests. |
| Tagged/unittest tiers | `src/tests/unittest_tags/` | Tag files select which stdlib tests should pass. |
| Test runner | `src/runner.py` | Harness for executing Python tests. |
| Test data | `testData/` | Golden files and fixtures. |

## CONVENTIONS
- Tests are typically run via `mx python-gate --tags ...` rather than invoking pytest directly.
- Use `mx [-d] graalpytest TEST-SELECTOR` to rerun a failing test or selection.

## ANTI-PATTERNS
- Don’t delete failing tests; fix root causes or update tag selections when behavior intentionally changes.
