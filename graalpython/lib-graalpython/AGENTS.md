# lib-graalpython/ — GRAALPY STDLIB OVERLAYS

## OVERVIEW
Python modules/patches that implement or tweak stdlib behavior for GraalPy; some execute at startup.

## WHERE TO LOOK
| Task | Location | Notes |
|------|----------|-------|
| Overlay modules | `modules/` | Python modules provided/overridden by GraalPy. |
| Patches | `patches/` | Patch files applied to external packages on pip installation to fix compatibility issues. |

## CONVENTIONS / GOTCHAS
- If a file name matches a builtin module name, it can run in that module context during startup (see contributor docs).
- Keep changes aligned with corresponding Java builtins (`com.oracle.graal.python.builtins`) where relevant.

## ANTI-PATTERNS
- Don’t fork upstream stdlib behavior unnecessarily; prefer minimal overlays.
