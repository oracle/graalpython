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

## CPYTHON SOURCE CONVENTION

In files derived from CPython under `src/`, `include/`, and `modules/`, all content
is verbatim CPython source unless explicitly marked with a `// GraalPy change:`
comment. This includes comments, whitespace, declarations, and generated Clinic
wrappers, not just executable statements.

- When restoring upstream code, copy the complete snippet exactly, including its
  comments and blank lines. Preserve upstream function order where possible.
- Mark every intentional deviation with `// GraalPy change:` immediately before
  the changed code, and briefly explain why GraalPy needs it. Make the scope clear;
  use an end marker for a multi-function block. For a wholly custom implementation
  in a CPython-derived file, explicitly state that the marker covers the whole file.
- Mark disabled upstream code and replacement stubs on their preprocessor guards,
  for example `#if 0 // GraalPy change: ...` and `#endif // GraalPy change: ...`.
- When restoring upstream behavior, remove obsolete change markers only where the
  resulting content is verbatim upstream. Keep markers for remaining adaptations.
- Apply this convention to CPython-derived files; GraalPy-only build configuration
  and Java implementations do not need CPython change markers.

## ANTI-PATTERNS
- **NEVER** nest `Py_BEGIN_ALLOW_THREADS` blocks (see `include/ceval.h`).
- Never mix `PyMem_*` / `PyObject_*` allocators with platform `malloc` family.
