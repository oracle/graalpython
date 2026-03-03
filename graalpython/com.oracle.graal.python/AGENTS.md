# com.oracle.graal.python/ — CORE JAVA IMPLEMENTATION

## OVERVIEW
Truffle-based Python runtime: AST nodes, builtins, compiler/bytecode support, interop, and shared runtime services.

## STRUCTURE
```text
com.oracle.graal.python/
└── src/com/oracle/graal/python/
    ├── runtime/     # Context, POSIX emulation, state, interop runtime glue
    ├── nodes/       # Truffle AST nodes + bytecode nodes
    ├── builtins/    # Builtin modules/types; @CoreFunctions + @Builtin nodes
    ├── lib/         # Shared lib nodes/utilities used across builtins/nodes
    ├── compiler/    # Bytecode compiler + DSL tooling
    └── util/        # Shared helpers/data structures
```

## WHERE TO LOOK
| Task | Location | Notes |
|------|----------|-------|
| Add/fix builtin | `.../builtins/**` | Modules/classes via `@CoreFunctions`; ops via `@Builtin` Nodes. |
| Cross-cutting ops | `.../lib/**` | Prefer adding/reusing lib nodes instead of duplicating patterns. |
| Interop behavior | `.../nodes/interop`, `.../runtime/interop` | Foreign call rules + conversions. |
| Bytecode execution | `.../nodes/bytecode/**` | Root nodes and bytecode interpreter pieces. |

## CONVENTIONS
- Code style enforced via pre-commit Eclipse formatter + checkstyle; don’t hand-format Java.
- Keep naming/layout close to CPython where practical (helps cross-referencing).

## ANTI-PATTERNS
- Don’t edit generated sources under `mxbuild/**` or distribution outputs; edit `src/**`.
- Avoid re-implementing common helpers in builtins; check `.../lib/**` first.
