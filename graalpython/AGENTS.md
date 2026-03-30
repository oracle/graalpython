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

## RUNNING

- Python code can be executed with GraalPy using `mx python`, invoked just like normal `python` command. The project
  *must* be first built with `mx python-jvm` or you will execute stale code. Note that `mx python-jvm` just builds, it
  doesn't take arguments nor execute code.

## TESTING

- There are multiple kinds of tests:
    - GraalPy Python tests
        - Our own tests in `com.oracle.graal.python.test/src/tests/`
            - Executed with `mx graalpytest test_file_name`
            - New test should normally be added here, unless they need to be in Java
    - CPython tests, also called tagged tests
        - Tests copied from upstream CPython in `lib-python/3/tests`. Should not be modified unless specifically
          requested. If modified, modifications should be marked with a `# GraalPy change` comment above the changed
          part.
        - Executed with `mx graalpytest --tagged test_file_name`
        - Uses a "tagging" system where only a subset of tests specified in tag files is normally executed. The `--all`
          flag makes it ignore the tags and execute all tests.
    - JUnit tests
        - In `com.oracle.graal.python.test/src` and `com.oracle.graal.python.test.integration/src`
        - Used primarily for testing features exposed to Java, such as embedding, instrumentation or interop.
        - The tests need to be built with `mx build` prior to execution. The `mx unittest com.example.TestName` command
          can be used to run individual tests.
- The `mx graalpytest` command accepts pytest‑style test selectors (e.g., `test_mod.py::TestClass::test_method`) but is
  **not** a full pytest implementation. Standard pytest command‑line flags such as `-k`, `-m`, `-v`, `--maxfail` are not
  supported.
- Important: The test commands don't automatically rebuild the project. It is your reponsibility to rebuild the project
  using `mx python-jvm` after making changes prior to running tests otherwise the tests will run stale code.

## ANTI-PATTERNS
- Don’t use `mxbuild/**` outputs to understand behavior; always navigate `.../src/...` trees.
- C-API: never mix `PyMem_*` / `PyObject_*` allocators with platform `malloc` family.
