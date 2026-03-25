---
name: jira
description: Deal with Jira tickets autonomously
---

# Jira

## Overview

Deal with Jira tickets, pull their description and any linked context, search,
update, reproduce, potentially fix and/or close them. Go on with this workflow
to the end unless you are actually blocked or get to one of the points where
the workflow tells you to wait for confirmation or ask something.

### General Notes

Typical fields you need to know:
* "components" is typically one of "Python", "Mx", "Infra", "Compiler", "Truffle"
* "issuetype" is typically "Task", "Bug (non BugDB)", "Testing", "Build Failure"
* "project" is typically "GR"
* "labels" is typically left empty when creating new issues

### 1. Getting context

To get the issue data, start with `gdev-cli`, for example:

    gdev-cli jira get-issue --json -id GR-72840

Read the description and follow any links that seem relevant.

Run this in a subagent if possible and let it give you a summary.

### 2. Check if there is work to do

Issues may be stale, already solved, or no longer apply. Search the context and
logs for other potentially relevant keywords, use `gdev-cli jira search` to find
out if there are potentially other related issues, query the codebase and git
history and look for reproducers.

Run this in a subagent if possible and let it give you a summary.

### 3. Reproduce the issue

It is PARAMOUNT to reproduce an issue first before changing code. You should
not give up trying to reproduce an issue until you are certain that it *cannot*
be reproduced or you spent at least a few hours and a million tokens on it. If
after that amount of work you have not reproduced the issue, STOP AND ASK for
guidance.

Derive how to reproduce the issue from the logs and issues you find. This may
mean running existing tests in a loop if the issue appears transient, or
writing a new test in `graalpython/com.oracle.graal.python.tests/src/tests` or
just a script to reproduce it. Do NOT write new tests in
`graalpython/lib-python/3/tests`, if you want to add new tests, put them under
`graalpython/com.oracle.graal.python.tests/src/tests`. Tests should be run
using `mx graalpytest`.

Reproducing something that fails rarely or only on another architecture or in
the CI may be tough, but can be achieved using `gdev-cli bitbucket` and running
in the gate, stress testing there.

  1. Create a temporary branch.
  2. Potentially add a new CI job to run just the reproducer.
  3. Push to Bitbucket.
  4. Open a PR using gdev-cli bitbucket.
  5. Use the bitbucket buildbot REST API to request a merge commit `/rest/ci/1.0/base/projects/G/repos/<REPO>/pullRequest/<PRID>/mergeCommit`. You need a `Authorizatin: Bearer <TOKEN>` header.
     Make sure to disable any proxy environment variables for the API call. You should be able to find the token by decoding the base64 token from the gdev-cli config.
  6. Wait a bit, the CI will create a new commit on a branch. The name of that branch is `<BRANCHNAME_YOU_PUSHED>_gate`. Use `git fetch` to see the branch update come in.
  7. Use the bitbucket buildbot REST API to request CI job enumeration for the HEAD commit of that `<BRANCHNAME_YOU_PUSHED>_gate` branch: `/rest/ci/1.0/base/projects/G/repos/<REPO>/enumerate/<COMMIT>?branch=<URL_ENCODED_BRANCH_NAME>&force=false&toBranch=master`
  8. Wait a bit, the CI will enumerate the available CI jobs on that commit.
  9. Use gdev-cli bitbucket to start your reproducer job on that commit.
  10. Repeat steps 3 through 9 until you are satisfied with the reproducer.

DO NOT STOP POLLING AND RETRYING UNTIL EITHER YOU REPRODUCE THE ISSUE, MORE
THAN 8 HOURS HAVE ELAPSED WHILE YOU TRIED, OR YOU HAVE USED AT LEAST AROUND 2
MILLION TOKENS (you may estimate from the conversation history) WHILE TRYING!

Make sure to decline the temporary reproducer PR once you are done with it
using `gdev-cli bitbucket`.

### 4a. Fixing a reproducible issue.

Once you have a reproducer (even if it may mean running something in a loop for
a while), you can try to make a plan to fix it. Use the sources and tools at
your disposal to investigate the issue. Present the plan and ask for additional
guidance before committing to a code change.

When you implement a change, make sure to verify using the reproducer.

### 4b. Fixing a non-reproducible issue.

If you failed to reproduce an issue, spent time reasoning about it and form one
or more hypotheses. Present your hypotheses clearly, point to source code,
documentation, or web sources that lead you to believe something may fix an
issue. Then STOP AND ASK for guidance.

### 5. Preparing a code change for merge

Once a code change has been implemented and verified (either with reproducer or
by approval of the human user), it needs to be prepared for inclusion.

Transition the Jira issue to be "In Progress" using `gdev-cli jira transition`.

Make sure your changes are committed in reviewable, focused, incremental
commits.

Run a subagent to REVIEW the code changes. Give it enough context to understand
why specific implementation decisions were made. Consider the subagent's
comments carefully, change the code where the subagent's comments make sense.

Create a bitbucket PR

  1. Push your branch.
  2. Open a PR using gdev-cli bitbucket with a title including the Jira issue ID, like "[GR-XXXXX] Short description of overall fix."
  3. Use the bitbucket buildbot REST API to request a merge commit `/rest/ci/1.0/base/projects/G/repos/<REPO>/pullRequest/<PRID>/mergeCommit`. You need a `Authorizatin: Bearer <TOKEN>` header.
     Make sure to disable any proxy environment variables for the API call. You should be able to find the token by decoding the base64 token from the gdev-cli config.
  4. Wait a bit, the CI will create a new commit on a branch. The name of that branch is `<BRANCHNAME_YOU_PUSHED>_gate`. Use `git fetch` to see the branch update come in.
  5. Use the bitbucket buildbot REST API to request CI job enumeration for the HEAD commit of that `<BRANCHNAME_YOU_PUSHED>_gate` branch: `/rest/ci/1.0/base/projects/G/repos/<REPO>/enumerate/<COMMIT>?branch=<URL_ENCODED_BRANCH_NAME>&force=false&toBranch=master`
  6. Wait a bit, the CI will enumerate the available CI jobs on that commit.
  7. Use gdev-cli bitbucket to start and watch the gate jobs on the HEAD commit of that `<BRANCHNAME_YOU_PUSHED>_gate` branch. They may take a few hours to finish, so poll sparingly.
  8. If there are failures, investigate them and try to fix them yourself on top of the PR.
  9. Repeat steps 1 through 8 until the gates pass or you need help from the human.

### 6. Updating the Jira issue contents.

Once you have determined that no code change is necessary, or a code change has
been implemented and the PR created, the Jira issue needs to be updated.

You can do this in parallel while watching the Bitbucket PR from step 5.

Add a comment using `gdev-cli jira comment` to the Jira issue, summarizing your
findings and any work you may have done. Do NOT use Attlassian markup, the
comment just ONLY be PLAIN TEXT. For paragraphs, just use double '\n'. You can
make plaintext lists by making lines begin with '* '. Do NOT use ADF, use raw
text, regardless of what the tool's help message says.

Also decide yourself or confer with the human about whether this change needs
to be backported, and what the "fix version" assignment for the Jira label
should be.

A typical template for a Jira issue update looks like this:

    {
      "fields": {
        "fixVersions": [
          {
            "name": "graalvm-25.1.0"
          }
        ],
        "labels": [
          "no-backport"
        ]
      }
    }

`fixVersions` could also be `n/a` when it's not really version specific like a
CI failure. The version name can be derived from the mx.graalpython/suite.py
version string, with `graalvm-` prepended.

`labels` could also be `to-backport`, confer with the user which one to choose
unless it is obvious.

Make sure not to delete an pre-existing fixVersions or labels.

If the issue is already fixed, close it.

### 7. Final report

Finalize your report to the user: post the complete URL to the PR and the Jira,
summarize a last time what you found and did.

