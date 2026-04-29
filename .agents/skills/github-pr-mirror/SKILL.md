---
name: github-pr-mirror
description: Mirror an external GitHub pull request into the internal GraalPython Bitbucket review flow, including OCA label checks, Jira creation or reuse, preserving PR commits, pre-commit cleanup, and handoff to the shared GraalPython Bitbucket PR flow for PR creation, Graal Bot tasks, gates, and fixes.
---

# GitHub PR Mirror

Use this skill when asked to mirror, import, port, or copy a GitHub pull request into Bitbucket for GraalPython review.

This workflow intentionally preserves the original GitHub PR commits. Do not rebase, squash, amend, or otherwise rewrite the contributor's commits unless the user explicitly tells you to abandon GitHub auto-closing behavior.

## Companion Skills

- Use the `jira` skill when creating or transitioning Jira issues.
- Use the `graalpython-bitbucket-pr` skill after the mirror branch is ready, to create the Bitbucket PR, handle Graal Bot tasks, start/watch gates, and fix or report gate failures.

## Inputs

Required:
- GitHub PR number or URL.

Optional:
- Existing Jira issue key, such as `GR-12345`.
- Bitbucket target branch. Default to the GitHub PR base branch, normally `master`.
- Bitbucket remote. Default to `origin`.
- GitHub remote. Default to `github`; if absent, use the remote matching `github.com/graalvm/graalpython`.
- Bitbucket project/repository. Default to `G` / `graalpython`.

## Workflow

### 1. Inspect the GitHub PR and verify OCA

Fetch GitHub PR metadata without modifying the repository:

```bash
gh pr view <PR_OR_URL> --repo graalvm/graalpython --json number,title,url,labels,baseRefName,headRefOid
```

Abort if the labels do not include exactly `OCA Verified`. Tell the user the PR is not mirrorable yet because OCA is missing.

Record:
- `PR_NUMBER`
- `PR_TITLE`
- `PR_URL`
- `BASE_BRANCH`
- `HEAD_SHA`

### 2. Create or reuse the Jira issue

If the user supplied a Jira issue, use it. Otherwise create one:

- Project: `GR`
- Component: `Python`
- Summary: `GitHub PR#<PR_NUMBER>: <PR_TITLE>`
- Description: the GitHub PR URL only
- Assignee: current user. If not already known, derive from `git config user.email`

Use a temporary JSON template for `gdev-cli jira create`, then remove the template after creation. Adapt the assignee field to the accepted Jira schema if the CLI rejects the first attempt.

Example template shape:

```json
{
  "fields": {
    "project": { "key": "GR" },
    "summary": "GitHub PR#<PR_NUMBER>: <PR_TITLE>",
    "description": "<PR_URL>",
    "issuetype": { "name": "Task" },
    "components": [{ "name": "Python" }],
    "assignee": { "emailAddress": "<GIT_EMAIL>" }
  }
}
```

After creation or reuse, transition the issue to In Progress:

```bash
gdev-cli jira transition --issues <JIRA_KEY> -t "Start Progress" --force
```

If Jira reports a different valid transition name for starting work, use that name.

### 3. Fetch the PR and create the mirror branch

Use a branch named exactly:

```text
github-pr/<PR_NUMBER>
```

Do not reuse a dirty or unrelated branch. If the branch already exists, inspect it; only continue if it already points at the same GitHub PR history or the user confirms replacement.

Fetch and branch from the GitHub PR head:

```bash
git fetch <GITHUB_REMOTE> pull/<PR_NUMBER>/head
git switch -c github-pr/<PR_NUMBER> FETCH_HEAD
```

Verify `HEAD` is the GitHub PR head SHA from step 1:

```bash
test "$(git rev-parse HEAD)" = "<HEAD_SHA>"
```

Do not rebase. Do not squash. Do not amend original commits.

Check that there are no merge conflicts using `git merge-tree`. If there are, fetch and merge the target branch without fast-forwarding and resolve the conflicts.

### 4. Run pre-commit on the PR range

Fetch the base branch and compute the original PR commit range:

```bash
git fetch <GITHUB_REMOTE> <BASE_BRANCH>
BASE_REF="$(git merge-base HEAD <GITHUB_REMOTE>/<BASE_BRANCH>)"
pre-commit run --from-ref "$BASE_REF" --to-ref HEAD
```

If pre-commit modified files, review the diff and create a new follow-up commit on top of the GitHub PR commits:

```bash
git status --short
git diff
git add <changed-files>
git commit -m "[GR-<ID>] Apply pre-commit fixes for GitHub PR#<PR_NUMBER>"
```

Only commit mechanical pre-commit output here. If pre-commit reveals non-mechanical problems, fix them in a separate follow-up commit when trivial; otherwise stop and ask the user.

### 5. Create the Bitbucket PR and follow gates

Use the `graalpython-bitbucket-pr` skill with these inputs:
- Source branch: `github-pr/<PR_NUMBER>`
- Bitbucket remote: `<BITBUCKET_REMOTE>`
- Target branch: `<TARGET_BRANCH>`
- Title: `[<JIRA_KEY>] GitHub PR#<PR_NUMBER>: <PR_TITLE>`
- Description: `Mirrors GitHub <PR_URL> for internal review.`
- Project/repository: `G` / `graalpython`
- Reviewers: repo-level default reviewers

Let that skill create the PR, handle Graal Bot comments/tasks, start or verify gates, watch gates, and fix or report failures.

## Final Report

Always report:
- Bitbucket PR URL
- Jira issue key or URL
- Gate status and any unresolved failures or bot tasks

## Guardrails
- Don't comment on the github PR unless asked. Never mention the internal bitbucket/buildbot/etc URLs in comments on github.
- After handing off to `graalpython-bitbucket-pr`, do not stop monitoring the gates until they finish, fail, or become blocked by tooling.
