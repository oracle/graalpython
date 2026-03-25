# PROJECT KNOWLEDGE BASE

## OVERVIEW
GraalPy is an alternative implementation of Python. The reference implementation of Python is CPython and GraalPy aims to be as compatible with CPython as possible.
It consists of: Java (Truffle) + C (CPython C-API compatibility) + Python stdlib/overrides, built and tested via the `mx` build tool.

## STRUCTURE
```text
./
├── graalpython/                 # Core sources + stdlib + tests (multi-language)
│   ├── com.oracle.graal.python/ # Main Java implementation (Truffle AST, runtime, builtins)
│   ├── com.oracle.graal.python.cext/ # C-API (headers + C sources + adapted CPython modules)
│   ├── com.oracle.graal.python.test/ # Python-level + Java-level tests and runners
│   ├── lib-graalpython/         # GraalPy-specific Python modules/patches
│   └── lib-python/              # Vendored/adapted CPython stdlib + CPython tests
├── mx.graalpython/              # `mx` suite + helper commands (build/test/bench/bisect)
├── scripts/                     # Dev utilities (launcher, formatting hooks, codegen)
├── .github/workflows/           # GitHub Actions entrypoints
├── ci/                          # CI definitions (jsonnet/libsonnet)
├── docs/                        # User + contributor docs
├── benchmarks/                  # Benchmark harnesses
└── mxbuild/                     # GENERATED build artifacts (ignore for code navigation)
```

## WHERE TO LOOK
| Task | Location | Notes |
|------|----------|-------|
| Build / run | `docs/contributor/CONTRIBUTING.md`, `mx.graalpython/` | This repo is `mx`-first (not a typical Maven/Gradle-only build). |
| Java runtime & Truffle nodes | `graalpython/com.oracle.graal.python/src/com/oracle/graal/python/{runtime,nodes,builtins}` | Main interpreter implementation. |
| C-API / native extensions | `graalpython/com.oracle.graal.python.cext/{include,src,modules}` | Mirrors CPython naming; many files are adapted from CPython. |
| Python stdlib overrides | `graalpython/lib-graalpython/` | GraalPy-specific modules executed at startup and/or used by builtins. |
| Vendored stdlib + CPython tests | `graalpython/lib-python/3/` | Large; treat as upstream-ish unless you are explicitly changing stdlib/tests. |
| Python-level tests | `graalpython/com.oracle.graal.python.test/src/tests/` | Includes tagged tests + C-API tests. Runner: `.../src/runner.py`. |
| CI pipelines | `.github/workflows/`, `ci.jsonnet`, `ci/` | Workflows typically drive `mx` gates/tags. |
| Launchers / helper scripts | `scripts/python.sh`, `scripts/*` | `python.sh` is the local launcher wrapper. |

## CONVENTIONS (DEVIATIONS)
- `mx` is the primary build/test entrypoint; suite definition lives in `mx.graalpython/suite.py`.
- `black` is configured but intentionally **disabled** for this repo (root `pyproject.toml`); line length is 120 and version locked to `23` for consistency.
- Formatting/linting is enforced via **pre-commit** with repo-specific hooks (Eclipse formatter, checkstyle, copyright), and pylint only on `mx.graalpython/*.py`.
- Large generated/build outputs exist in-tree (`mxbuild/`, `*.dist/`); do not use them as the source of truth when navigating code.

## ANTI-PATTERNS (THIS PROJECT)
- **Do not** base edits or reviews on `mxbuild/**` or `*.dist/**` outputs; change sources under `graalpython/**`, `mx.graalpython/**`, etc.
- Security reports: follow `SECURITY.md` (do **NOT** file public issues for vulnerabilities).
- C-API: heed CPython-style invariants in headers (e.g. `ceval.h`: **NEVER** nest `Py_BEGIN_ALLOW_THREADS` blocks; avoid mixing PyMem/PyObject allocators with `malloc`).
- Interop: foreign `executable` / `instantiable` objects are **never** called with keyword arguments (see `docs/user/Interoperability.md`).

## UNIQUE STYLES / GOTCHAS
- Builtins: implemented in Java classes annotated with `@CoreFunctions`; individual operations are Nodes annotated with `@Builtin` (see contributing guide).
- Many operations have shared equivalents under `com.oracle.graal.python.lib`; prefer using/adding shared lib nodes instead of duplicating patterns.
- Parser work may require regenerating golden files (see `docs/contributor/CONTRIBUTING.md`).

## COMMANDS

* Import dependent suites / download deps
  `mx sforceimport`
* Build and run
  `mx python-jvm`
  `mx python -c 'print(42)'`
* Common local testing
    * Run cpyext tests
      `mx graalpytest cpyext`
    * Rerun a specific failing test
      `mx graalpytest TEST-SELECTOR`
    * Run JUnit tests
      `mx python-gate --tags python-junit`
* Style / formatting
  `mx python-style --fix`
  `mx python-gate --tags style`

## NOTES
- When searching for implementation, prefer `graalpython/com.oracle.graal.python/src/...` over vendored `lib-python` unless you are intentionally modifying upstream stdlib/tests.
- If you see very large files under `com.oracle.graal.python.cext/modules/_sqlite/` or `expat/`, treat them as upstream imports/adaptations (patch carefully).

## PULL REQUESTS AND JIRA TICKETS

We use Jira and Bitbucket, and each PR should reference a Jira ticket with the form [GR-XXXX] where XXXX is the ticket number.
When asked to open pull requests, agents should ask for the Jira ticket number.
When asked to create a ticket, the `gdev-cli jira` tool can be used to create a ticket for the "Python" component.
When asked to create, run gates on, or check on the builds previously run on a pull request, use the `gdev-cli bitbucket` tool.
