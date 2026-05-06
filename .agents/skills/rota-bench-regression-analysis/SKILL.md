---
name: rota-bench-regression-analysis
description: Analyze recent GraalPy benchmark regressions on `master` as part of the weekly rota. Use when asked to analyze benchmarks for rota.
---

# Bench Regression Analysis

## Use This Skill For
- Recent regression summaries from `scripts/compare_bench_regressions.py --rota`.
- Follow-up inspection of unattributed plausible change points.

## Core Workflow
1. Run:
```bash
scripts/compare_bench_regressions.py --rota --json-out /tmp/compare_bench_regressions_rota.json
```
2. Use the text output for the human summary and the JSON for precise inspection.
3. Focus on `plausible` regressions. Ignore `flaky` and `inconclusive` items unless they help explain a plausible shift.
4. Split the summary into:
- `Attributed`
- `To bisect`
- `To watch`
5. Show the current summary
6. Execute the bisect script for each "to bisect" entry in parallel, then wait for all of them to finish.
   The builds can take many hours without the script showing any output, make sure you wait for them with a long timeout.
   If running in codex: round-robin poll the processes with `write_stdin` and 1 hour timeout (the configuration might cap this at a lower timeout in practice)
7. Collect the bisect results and move any benchmarks that were attributed by the bisections.
8. Show the final summary. Note any failed bisects.

## Useful JSON Queries
```bash
jq '.direct_suspects[] | {good_commit, bad_commit, bad_author_email, bad_subject}' \
  /tmp/compare_bench_regressions_rota.json

jq '[.change_points[] | select(.classification == "plausible")]' \
  /tmp/compare_bench_regressions_rota.json
```

## Attributed Regressions
- Start from `direct_suspects` in `/tmp/compare_bench_regressions_rota.json`.
- For each suspect, keep the abbreviated bad commit ID, full author email, full commit subject, and the worst example benchmarks per suite, not the full list.
- Prefer one worst example per affected suite such as `micro`, `meso`, `macro`...

## Unattributed Regressions
- Start from plausible `change_points` whose `(good_commit, bad_commit]` pair is not already covered by `direct_suspects`.
- Inspect the range with:
```bash
git log --first-parent --reverse --format='%H%x09%ae%x09%s' GOOD..BAD
git show --stat --summary --format=fuller BAD
git diff --stat GOOD..BAD
```
- If needed, inspect individual commits in the range with `git show --stat --summary --format=fuller COMMIT`.

## Attribution Rules
- If the change point is an exact single-parent GraalPy commit and `mx.graalpython/suite.py` imports did not change, it can usually be attributed to that commit.
- Changes to imports in `mx.graalpython/suite.py` can never be confidently attributed without bisecting Graal. Keep those unattributed and say so explicitly (including the graal commit range)
- If an unattributed first-parent range contains one plausible GraalPy code change and the rest are documentation, tests, retags, or other non-performance changes, attribute it to that one code change.
- If the series is already shifted by an earlier attributed commit and a later unattributed range only preserves the new level, fold the later item into the earlier attribution.
- Cross-configuration correlation matters.
- If `native` shows an exact jump on one commit and `jvm` later shows the same benchmark shifted upward through a range containing that commit, treat them as likely the same cause unless the later range has a better candidate.
- If both `jvm` and `native` jump at or immediately after a Graal import update, keep both under the same unattributed Graal-side cause.

## Flakiness Check
- Use Bench Server data when the unattributed item is small or suspicious.
- Query the benchmark series with `bench-cli run -` and check whether the change is a clean step up that stays high, a one-point spike that immediately falls back, or already present before the reported range.
- A stable step change is a real regression candidate.
- An isolated last-point bump with no supporting related regressions is usually watch-and-rerun material, not a strong attribution.

## Bench Server Checks
- Prefer querying only the specific benchmark and configuration under investigation.
- Typical filters: `branch = master`, target benchmark, `host-vm = graalvm-ee`, target `host-vm-config`, `guest-vm = graalpython`, target `guest-vm-config`, `metric.name = time`, `commit.committer-ts last-n 30d`.
- Reduce output to `commit.rev` and average metric value so the step pattern is easy to inspect.
- `bench-cli` sometimes fails with 404 when the server is overloaded. If that happens, wait for a minute and try again

## Output Contract
- List findings first, not process notes.
- Keep three top-level sections (if not empty): `Attributed` and `To bisect` and `To watch`
- In the attributed section, use this header format: `abcd1234efgh | author@oracle.com | Full subject`
- Unattributed changes that look plausible go to "to bisect", flaky ones go to "to watch"
- In the "to bisect" section, add an invocation (don't execute yet) of `scripts/bisect_benchmark_regression.py` that can bisect it (use unabbreviated commits in this case)
- In the "to watch" section, say whether the item looks flaky, or likely the same cause as another attributed item.
- Do not abbreviate commit subjects.
- Keep author emails.
- Abbreviate commit IDs to 12 characters.
- Do not list every benchmark if there are many; only the worst examples from each affected suite. If you didn't list all, say "and X others".

## Guardrails
- If the script or you can't find `bench-cli`, ask the user to provide it from the `bench-server` repo.
  - You may offer to clone the repo and create the cli for the user. The repo is on the same bitbucket as graalpython, the project is `INFRA` and the repo is called `bench-server`
- Don't submit more than 5 bisect jobs. If there are more in the "to bisect" list, pick 5 that look the most serious and leave the rest as "to bisect".
