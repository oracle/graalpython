---
name: graalpython-rota
description: Run GraalPy ROTA maintenance workflows for (1) import update pull requests and (2) triage of recent periodic job failures in Jira. Use when asked to perform or guide recurring ROTA tasks from `docs/contributor/ROTA.md`, including branch setup, `mx` update commands, PR creation with reviewers/gates via `gdev-cli bitbucket`, and date-bounded periodic-failure issue triage via `gdev-cli jira`.
---

# GraalPy ROTA

## Overview
Execute recurring GraalPy ROTA tasks with exact commands and strict output structure. Prefer the procedures in this skill.

## Choose Workflow
- Use `Import update` when asked to refresh imports and open the standard PR.
- Use `Recent periodic issues` when asked to triage periodic job failures in Jira.

## Import Update Workflow
1. Run the automated branch setup, import update, GitHub unittest-tag refresh, enterprise unittest-tag refresh, push, and standard PR creation:
```bash
mx python-update-import --rota
```
2. If the command reports that `../graal-enterprise/graalpython-enterprise` is missing, stop and ask the user to provide that checkout.
3. Inspect the two generated commits and the created PR for plausibility. Expect mostly additions, not removals in the combined unittest-tag commit.
4. Use `gdev-cli bitbucket` to start gates on the created PR. Reviewer assignment comes from the default `gdev-cli` configuration.
5. Fix gate failures and push updates until gates pass.

## Recent Periodic Issues Workflow
1. Verify creator identity mapping:
- Treat `ol-automation_ww` as Jira username `olauto`.
- If query returns zero results, test both identities, then keep `creator = olauto` once verified.

2. Filter to recent periodic job failures, excluding in progress or closed.
- Default to the last 14 days unless user specifies otherwise.
- Always state concrete start/end calendar dates in the response.
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

7. Convert findings into an implementation-ready plan per issue:
- Extract failing job name, error signature, and log clue.
- Map probable source area in repo.
- Propose first verification command.
- Define exit criteria to close ticket.
- Prepare temporary git worktree per issue with branch naming based on Jira key plus very short hyphenated description.

## Output Contract For Periodic Triage
Return exactly:
1. Query scope used (component, creator, time window, status filter).
2. Count summary (total recent automation issues vs periodic failures).
3. Issue list with key, created date, summary, status.
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
