# docs/ — DOCUMENTATION

## OVERVIEW
Contributor and user documentation; many repo-wide rules live here.

## WHERE TO LOOK
| Task | Location | Notes |
|------|----------|-------|
| Build/test instructions | `contributor/CONTRIBUTING.md` | Primary “how to build/run gates” guide. |
| Runtime internals | `contributor/IMPLEMENTATION_DETAILS.md` | Interpreter design details. |
| Interop rules | `user/Interoperability.md` | Keyword-arg restrictions for foreign calls, etc. |
| Native extensions | `user/Native-Extensions.md` | Guidance on native packages and tooling. |
| Standalone apps | `user/Python-Standalone-Applications.md` | Notes on packaging and limitations. |

## CONVENTIONS
- Prefer updating docs here rather than duplicating instructions in random READMEs.

## ANTI-PATTERNS
- Security: follow `SECURITY.md` at repo root; do not instruct users to file public vulnerability issues.
