---
name: graalpython-bitbucket-pr
description: Create or continue a GraalPython Bitbucket pull request and drive it through Graal Bot tasks, gate start, gate monitoring, failure investigation, fixes, pushes, and gate reruns. Use after a branch is ready for internal GraalPython review, or when an automation command has already created the PR and the remaining work is task cleanup and gate follow-up.
---

# GraalPython Bitbucket PR

## Overview
Create or continue a GraalPython Bitbucket PR, resolve administrative Graal Bot tasks, start or verify gates, watch gates, and fix or report failures.

## Companion Skills
- Use the `bitbucket` skill for PR creation, comments, and tasks.
- Use the `buildbot` skill for starting, watching, rerunning, and investigating gates.

## Inputs
Required for new PRs:
- Source branch already pushed or ready to push.
- Target branch.
- PR title.
- PR description.
- Jira key in the PR title.

Required for existing PRs:
- Bitbucket PR ID or URL.

Defaults:
- Project/repository: `G` / `graalpython`.
- Reviewers: use the repo-level default reviewers unless the caller supplies a different list.
- Target branch: master

## Workflow

### 1. Push and create the PR, if needed

If the source branch is not pushed yet:

```bash
git push <BITBUCKET_REMOTE> <SOURCE_BRANCH>
```

Create the Bitbucket PR:

```bash
gdev-cli bitbucket create-pr \
  -p G \
  -r graalpython \
  -fb <SOURCE_BRANCH> \
  -tb <TARGET_BRANCH> \
  -t "<PR_TITLE>" \
  -rv "<REVIEWERS>" \
  -d "<PR_DESCRIPTION>" \
  --gate
```

Capture the Bitbucket PR URL and ID. If `--gate` is not accepted or fails transiently, create the PR first, then start gates with:

```bash
gdev-cli buildbot start-gate -p G -r graalpython -pr <BITBUCKET_PR_ID>
```

If the PR already exists, capture the PR URL and ID, then continue with task handling and gate checks.

### 2. Handle Graal Bot comments and tasks

Wait about 15 seconds after PR creation, then list comments and tasks:

```bash
gdev-cli bitbucket comment list -p G -r graalpython -pr <BITBUCKET_PR_ID> --all --json
gdev-cli bitbucket task list -p G -r graalpython -pr <BITBUCKET_PR_ID> --json
```

For tasks or comments authored by Graal Bot:
- Resolve tasks that are clearly administrative and already satisfied by the PR workflow.
- There should be a task regarding changelog. Changelog items are usually not needed for bugfixes and compatibility improvements.
  They are needed for added context options, changed public API's in `polyglot` module or large features. If a changelog item is needed
  but not present, suggest one and notify the user.

Resolve a task only when justified:

```bash
gdev-cli bitbucket task resolve -p G -r graalpython -pr <BITBUCKET_PR_ID> -cm <COMMENT_ID>
```

### 3. Watch gates

Use sparse polling because full gates can take about an hour. Prefer structured output for status decisions:

```bash
gdev-cli buildbot gate-overview -p G -r graalpython -pr <BITBUCKET_PR_ID> --json
gdev-cli buildbot gate-builds -p G -r graalpython -pr <BITBUCKET_PR_ID> --json
```

Human-readable output is fine for reporting, but avoid parsing it when JSON is available.

Suggested cadence:
- First check after PR creation and bot handling.
- Then every 10 minutes while many jobs are running.
- When fewer than about 5 jobs remain, poll every 5 minutes.
- Poll silently while gates are still running.
- If there are failures, try to diagnose them as soon as they appear.

Treat the gate as successful only when there are no running and no failed gate builds. Other PR vetoes, such as reviewer approval, merge queue state, or GitHub mirroring consideration, are not gate failures.

### 4. Fix or report gate failures

If gates fail:
- Inspect failing build logs with the `buildbot` skill.
- Fix trivial issues yourself, such as style output, generated pre-commit fallout, missing `@TruffleBoundary`, obvious test-selector mistakes, or small import/order problems.
- Commit fixes on top of the PR branch, push again, and restart or rerun gates as appropriate.
- Report non-trivial semantic failures, broad compatibility failures, or failures that require product judgment to the user with the Bitbucket PR URL, Jira key, and concise failure summary.
- Many gate failures are transient. Use your judgement to determine if a failure might be transient and if it's the case, restart the gate and go back to polling.
- Typical signs of a transient failure:
  - Infrastructure issues in the worker setup, git checkout or cleanup
  - Single failed test on a single platform that succeeded on other platforms
  - Test failures in tests involving weakrefs, subprocesses or multi-threading in PRs that didn't touch those subsystems.

Do not leave a long sleep process running after an interruption or handoff. If monitoring is interrupted, clean up any background sleep/polling process you started, then resume with a fresh status check.

## Final Report
Always report:
- Bitbucket PR URL
- Gate status
- Any unresolved failures or Graal Bot tasks
- Any transient issues encountered, with links to failed builds
