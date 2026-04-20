---
name: pr-gate-check
description: Check gate status for a Bitbucket PR by resolving the PR head commit, finding the gate merge commit, inspecting builds on that merge commit, and summarizing root-cause failures with actionable next steps.
---

# PR Gate Check

## Overview
Use this workflow when asked for gate status of a PR. Usually the builds are tied to a merge commit generated on Bitbucket, so this skill goes through finding the remote merge commit.

## Workflow
1. Get PR commits and identify PR head commit (first commit in `gdev-cli bitbucket commits` output):
```bash
gdev-cli bitbucket commits --project=G --repo=graalpython --pullrequest=<PR_ID> --all --json
```

2. Fetch refs and locate merge commit whose parent includes PR head:
```bash
git ls-remote origin 'refs/pull-requests/<PR_ID>/*'
git fetch --no-tags origin '+refs/heads/*:refs/remotes/origin/*' --prune
git rev-list --all --parents | rg ' <PR_HEAD_SHA>( |$)'
```
Pick the merge commit where one parent is `<PR_HEAD_SHA>` and the other is the target branch tip at merge time.

If you cannot find it this way, another heuristic is to take the branch name and append `_gate` - that usually has the merge commit we want as tip.

3. Check builds on that merge commit:
```bash
gdev-cli bitbucket get-builds --commit=<MERGE_SHA> --all --format=key,state,url
```

4. Separate root failures from fan-out failures:
- `FAILED` + `/builders/.../builds/...` URL: executed failed build (root failure candidate).
- `FAILED` + `build_request?brid=` URL: usually not-run/downstream due to earlier failure.

5. Inspect root failed build logs and extract exact failing test/error:
- Open build URL and `Run executor` stdio log.
- Capture failing test id, traceback/assertion, and command context.

6. Report back:
- PR head SHA
- merge SHA + parents (target parent + PR parent)
- build summary counts
- root cause failure(s)
- fix options and next action question

## Output Template
1. `PR head:` `<sha>`
2. `Gate merge commit:` `<sha>` (`parent1=<target_sha>`, `parent2=<pr_sha>`)
3. `Builds:` `<total>` total, `<success>` successful, `<failed>` failed
4. `Root failure(s):`
- `<build key>`: `<error summary>`
- `<failing test/path>`
- `<build url>`
5. `Proposed fixes:` short list
6. Ask user what to do next.

## Guardrails
- Do not conclude from PR commit statuses alone; always resolve and inspect merge-commit builds.
- If many builds are failed but only one executed failure exists, treat that one as primary cause.
- Keep proposed fixes minimal and scoped to observed failure.
