# graalpython/ — SOURCE + STDLIB + TESTS

## OVERVIEW
Main implementation tree: Java (Truffle interpreter), C (CPython C-API compatibility), and Python (stdlib overlays + tooling/tests).

## WHERE TO LOOK
| Task | Location | Notes |
|------|----------|-------|
| Java interpreter | `com.oracle.graal.python/src/com/oracle/graal/python/{runtime,nodes,builtins}` | Most core behavior lives here. |
| Shared Java lib nodes | `com.oracle.graal.python/src/com/oracle/graal/python/lib` | Prefer adding/reusing lib nodes for common operations. |
| C-API headers + runtime | `com.oracle.graal.python.cext/include`, `.../src` | CPython-like naming; follow CPython invariants. |
| Adapted native modules | `com.oracle.graal.python.cext/modules/` | Many files are upstream-derived; patch carefully. |
| GraalPy stdlib overlays | `lib-graalpython/` | Python files executed at startup / for builtins. |
| Vendored CPython stdlib/tests | `lib-python/3/` | Treat as upstream-ish unless explicitly changing it. |
| GraalPy tests | `com.oracle.graal.python.test/src/tests/` | Python-level tests + tag files. |
| Parser components | `com.oracle.graal.python.pegparser*` | Parser implementation + golden files tests. |

## CONVENTIONS / GOTCHAS
- This subtree contains both “source of truth” code and vendored/upstream-ish imports; keep patches minimal in `lib-python/` and large C module imports.
- Some large headers/databases (unicode tables) are generated; avoid editing them by hand unless you also update the generator pipeline.

## ANTI-PATTERNS
- Don’t use `mxbuild/**` outputs to understand behavior; always navigate `.../src/...` trees.
- C-API: never mix `PyMem_*` / `PyObject_*` allocators with platform `malloc` family.
