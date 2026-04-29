---
name: rota-check-periodic-jobs
description: Analyze current GraalPy periodic job failures for ROTA. Use when asked to triage, summarize, or plan work for current periodic job failures, starting from scripts/rota_ci_failures.py output, validating linked Jira issues, inspecting logs, forming hypotheses, reproduction commands, and implementation order.
---

# ROTA Periodic Job Check

## Overview
Triage current GraalPy periodic job failures and produce implementation-ready plans.

## Workflow
1. Verify dashboard environment and run the periodic failure collector:
- This workflow starts from `scripts/rota_ci_failures.py`, not from a Jira search.
- The script requires `OTDASHBOARD_URL` and `OTDASHBOARD_TOKEN`.
- If either variable is missing, stop and ask the user to set the missing variable(s). Do not fall back to querying Jira for the failure list.
- Run from the repository root:
```bash
scripts/rota_ci_failures.py
```

2. Parse the script output:
- If it reports no failed jobs, report that there are no current failed periodic jobs and stop.
- For each failed row, capture target, job name, last successful run, Jira ID(s), and log URL.
- If a failed row has no Jira ID, flag it in the report and continue log analysis.

3. Validate every reported Jira issue:
- Fetch each Jira issue linked by the script output:
```bash
gdev-cli jira get-issue --json -id GR-XXXX
```
- Check that the Jira matches the current failure:
  - The issue summary or description should identify the same error signature/root cause from the current log.
- Check that the Jira is not too broad:
  - A generic timeout/build-failure ticket is acceptable only if it names this job or an intentionally scoped equivalent set of jobs.
  - A ticket covering unrelated jobs, unrelated targets, or unrelated error signatures is too broad.
- Check that the Jira has component `Python`.
- Notify the user about every Jira that fails any of these checks. Include the Jira key and the failed check(s).

4. Inspect failed job logs:
- Use the `log URL` from the script output. For Buildbot URLs, fetch the executor log with:
```bash
gdev-cli buildbot get-log BUILD_ID
```
- Use `gdev-cli buildbot rca --build BUILD_ID --wait` when useful, but still inspect the relevant raw log lines.
- Identify the exact failing command, error signature, first meaningful failure, and whether later errors are cleanup fallout.

5. Convert findings into an implementation-ready plan per failure:
- Extract failing job name, error signature, and log clue.
- Map probable source area in repo.
- Propose the first verification command.
- Define exit criteria to close or update the linked ticket.

## Output Contract
Group the output by the Jira issue. For each, report:
- The issue summary, status and assignee
- The failed jobs in a table with job name, last successful run and log URL.
- The analysis of the failure and the proposed plan

## Guardrails
- If `OTDASHBOARD_URL` or `OTDASHBOARD_TOKEN` is missing, ask the user to set the missing variable(s). Do not try to set them yourself
- Do not echo the `OTDASHBOARD_TOKEN` variable and do not leak it anywhere.
- Prefer `--json` for Jira issue fetches.
