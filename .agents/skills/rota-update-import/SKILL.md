---
name: rota-update-import
description: Run the GraalPy ROTA import update workflow. Use when asked to refresh imports, create the standard Graal import update pull request, inspect generated commits, and hand off to the shared GraalPython Bitbucket PR flow for tasks, gates, and failure fixes.
---

# ROTA Import Update

## Overview
Execute the GraalPy ROTA import update workflow using the repo's automated command, then use the shared GraalPython Bitbucket PR workflow for post-creation tasks and gates.

## Companion Skills
- Use the `graalpython-bitbucket-pr` skill after `mx python-update-import --rota` creates the PR, to handle Graal Bot tasks, start or verify gates, watch gates, and fix or report failures.

## Workflow
1. Run the automated branch setup, import update, GitHub unittest-tag refresh, enterprise unittest-tag refresh, push, and standard PR creation:
```bash
mx python-update-import --rota
```

2. If the command reports that `../graal-enterprise/graalpython-enterprise` is missing, stop and ask the user to provide that checkout.

3. Inspect the two generated commits and the created PR for plausibility:
- Expect one import update commit.
- Expect one combined unittest-tag update commit.
- Expect mostly additions, not removals, in the combined unittest-tag commit.

4. Use the `graalpython-bitbucket-pr` skill in existing-PR mode with the PR created by `mx python-update-import --rota`.

5. Let that skill handle Graal Bot tasks, start or verify gates, watch gates, and fix or report failures.

## Guardrails
- Use `mx python-update-import --rota`; do not manually reconstruct the standard ROTA sequence unless the command fails and the failure requires targeted recovery.
- Preserve the automated commit structure unless there is a concrete reason to amend it.
- When reporting status, include the branch name, PR link or ID, gate status, and any follow-up failures.
