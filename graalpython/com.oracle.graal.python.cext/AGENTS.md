# com.oracle.graal.python.cext/ — CPYTHON C-API COMPAT

## OVERVIEW
C headers and native code implementing CPython C-API compatibility plus adapted builtin extension modules.

## WHERE TO LOOK
| Task | Location | Notes |
|------|----------|-------|
| Public C-API headers | `include/` | Mirrors CPython header structure. |
| Core C-API impl | `src/` | CPython-like file naming (`unicodeobject.c`, `typeobject.c`, ...). |
| Adapted extension modules | `modules/` | Large upstream-derived modules (e.g., `_sqlite`). |
| Embedded/third-party imports | `expat/`, `modules/_sqlite/sqlite/` | Treat as upstream; patch minimally. |

## CONVENTIONS / GOTCHAS
- Follow CPython invariants in headers (examples: do not nest `Py_BEGIN_ALLOW_THREADS`; “never mix allocators” warnings).
- Large unicode databases/headers are often generated (e.g., `unicodename_db.h`, `unicodedata_db.h`): avoid hand edits.

## ANTI-PATTERNS
- **NEVER** nest `Py_BEGIN_ALLOW_THREADS` blocks (see `include/ceval.h`).
- Never mix `PyMem_*` / `PyObject_*` allocators with platform `malloc` family.
