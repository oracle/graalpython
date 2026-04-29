---
name: rota-check-periodic-jobs
description: Analyze recent GraalPy periodic job failure Jira tickets for ROTA. Use when asked to triage, summarize, or plan work for recent periodic-job-failures issues, including date-bounded Jira searches with gdev-cli, issue detail inspection, hypotheses, reproduction commands, and implementation order.
---

# ROTA Periodic Job Check

## Overview
Triage recent GraalPy periodic job failure Jira tickets and produce implementation-ready plans.

## Workflow
1. Verify creator identity mapping:
- Treat `ol-automation_ww` as Jira username `olauto`.
- If a query returns zero results unexpectedly, test both identities, then keep `creator = olauto` once verified.

2. Filter to recent periodic job failures, excluding in-progress or closed issues:
- Default to the last 14 days unless the user specifies otherwise.
- Always state concrete start and end calendar dates in the response.
```bash
gdev-cli jira search --json --max 100 \
  -f key,summary,creator,created,status,labels,components,assignee \
  -jql "project = GR AND component = Python AND creator = olauto AND labels = periodic-job-failures AND created >= -14d AND status != Closed AND status != 'In Progress' ORDER BY created DESC"
```

3. Fetch shortlisted issue details with `get-issue`:
```bash
gdev-cli jira get-issue --json -id GR-XXXX \
  | jq '{key, summary:.fields.summary, status:.fields.status.name, created:.fields.created, labels:.fields.labels, assignee:(.fields.assignee.name // null), description:.fields.description, comments:(.fields.comment.comments | map({author:.author.name, created, body}))}'
```

4. Convert findings into an implementation-ready plan per issue:
- Extract failing job name, error signature, and log clue.
- Map probable source area in repo.
- Propose the first verification command.
- Define exit criteria to close the ticket.
- Prepare a temporary git worktree per issue with branch naming based on Jira key plus a very short hyphenated description.

## Output Contract
Return exactly:
1. Query scope used: component, creator, time window, status filter.
2. Count summary: total recent automation issues vs periodic failures.
3. Issue list with key, created date, summary, and status.
4. Per-issue plan with:
- Hypothesis
- First code locations to inspect
- First reproducibility command
- Exit criteria for closing ticket
5. Recommended implementation order.

## Guardrails
- State concrete dates for recency windows.
- Prefer `--json` and explicit `-f` fields in searches.
- Use `get-issue` only for shortlisted issues to keep output small.
