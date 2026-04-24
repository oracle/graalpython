#!/usr/bin/env python3
# Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
# DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
#
# The Universal Permissive License (UPL), Version 1.0
#
# Subject to the condition set forth below, permission is hereby granted to any
# person obtaining a copy of this software, associated documentation and/or
# data (collectively the "Software"), free of charge and under any and all
# copyright rights in the Software, and any and all patent rights owned or
# freely licensable by each licensor hereunder covering either (i) the
# unmodified Software as contributed to or provided by such licensor, or (ii)
# the Larger Works (as defined below), to deal in both
#
# (a) the Software, and
#
# (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
# one is included with the Software each a "Larger Work" to which the Software
# is contributed by such licensors),
#
# without restriction, including without limitation the rights to copy, create
# derivative works of, display, perform, and distribute the Software and make,
# use, sell, offer for sale, import, export, have made, and have sold the
# Software and the Larger Work(s), and to sublicense the foregoing rights on
# either these or other terms.
#
# This license is subject to the following condition:
#
# The above copyright notice and either this complete permission notice or at a
# minimum a reference to the UPL must be included in all copies or substantial
# portions of the Software.
#
# THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
# IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
# FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
# AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
# LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
# OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
# SOFTWARE.

from __future__ import annotations

import argparse
import json
import math
import os
import re
import shutil
import statistics
import subprocess
import sys

from dataclasses import asdict, dataclass
from pathlib import Path
from typing import Any


DEFAULT_HISTORY_DAYS = 60
DEFAULT_THRESHOLD_PERCENT = 5.0
DEFAULT_ABSOLUTE_TIME_DELTA_SECONDS = 0.002
DEFAULT_ANCESTOR_SEARCH_COMMITS = 30
DEFAULT_MIN_HISTORY_POINTS = 8
DEFAULT_RECENT_HISTORY_POINTS = 10
PREEXISTING_SHIFT_TAIL_POINTS = 3
MAD_SCALE_FACTOR = 1.4826
IQR_SIGMA_FACTOR = 1.349
REAL_MAD_Z_THRESHOLD = 4.5
FLAKY_MAD_Z_THRESHOLD = 2.5
DEFAULT_PROJECT = "G"
DEFAULT_REPOSITORY = "graalpython"
PRIMARY_TIME_METRIC = "time"
PRIMARY_HEAP_METRICS = ("memory", "allocated-memory")
PR_NUMBER_RE = re.compile(r"\[(?P<pr>\d+)\]")


@dataclass(frozen=True)
class BuildInfo:
    key: str
    state: str
    url: str
    build_number: int
    description: str | None


@dataclass(frozen=True)
class JobPair:
    job_name: str
    current_build_number: int
    baseline_build_number: int
    baseline_commit: str


@dataclass(frozen=True)
class Measurement:
    suite: str
    benchmark: str
    job_name: str
    machine_name: str
    host_vm: str
    host_vm_config: str
    guest_vm: str
    guest_vm_config: str
    branch: str
    commit_rev: str
    build_number: int
    metric_name: str
    metric_unit: str
    metric_better: str
    avg: float
    stddev: float
    count: int


@dataclass(frozen=True)
class HistoryStats:
    n: int
    minimum: float
    p10: float
    median: float
    p90: float
    maximum: float
    mean: float
    cv: float
    points_at_or_worse_than_current: int
    typical_adjacent_change_pct: float
    recent_n: int
    recent_median: float
    recent_mad: float
    recent_iqr: float
    recent_sigma: float
    recent_p90: float
    recent_maximum: float
    recent_points_at_or_worse_than_current: int
    recent_tail_n: int
    recent_tail_minimum: float
    recent_tail_maximum: float
    recent_tail_points_at_or_worse_than_current: int


@dataclass(frozen=True)
class RegressionFinding:
    direction: str
    benchmark: str
    suite: str
    machine_name: str
    host_vm: str
    host_vm_config: str
    guest_vm: str
    guest_vm_config: str
    metric_name: str
    metric_unit: str
    baseline_commit: str
    baseline_build_number: int
    baseline_value: float
    current_commit: str
    current_build_number: int
    current_value: float
    delta_pct: float
    abs_delta: float
    classification: str
    reason: str
    history: HistoryStats | None


class ScriptError(RuntimeError):
    pass


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        formatter_class=argparse.ArgumentDefaultsHelpFormatter,
        description=(
            "Compare GraalPy benchmark results for a PR against merge-base baseline jobs and classify "
            "regressions (and optionally, improvements) as plausible, inconclusive, flaky."
        )
    )
    parser.add_argument("--pr", type=int, help="Bitbucket PR number to inspect.")
    parser.add_argument("--current-pr", action="store_true", help="Resolve the PR from the current HEAD commit.")
    parser.add_argument("--merge-commit", help="Override the PR merge commit used for current benchmark jobs.")
    parser.add_argument(
        "--head-commit",
        help="Commit used to compute the merge-base against the baseline branch. Defaults to local HEAD.",
    )
    parser.add_argument("--baseline-branch", default="master", help="Baseline branch to compare against.")
    parser.add_argument(
        "--baseline-branch-local-name",
        help="Local git name for the baseline branch when it differs from the bench-server branch name.",
    )
    parser.add_argument("--history-days", type=int, default=DEFAULT_HISTORY_DAYS, help="History window in days.")
    parser.add_argument(
        "--threshold",
        type=float,
        default=DEFAULT_THRESHOLD_PERCENT,
        help="Only inspect regressions strictly above this percent threshold.",
    )
    parser.add_argument(
        "--absolute-time-delta-floor",
        type=float,
        default=DEFAULT_ABSOLUTE_TIME_DELTA_SECONDS,
        help="Treat time regressions below this absolute delta as flaky tiny benchmarks.",
    )
    parser.add_argument(
        "--ancestor-search-commits",
        type=int,
        default=DEFAULT_ANCESTOR_SEARCH_COMMITS,
        help="How many first-parent baseline ancestors to search for missing benchmark jobs.",
    )
    parser.add_argument(
        "--min-history-points",
        type=int,
        default=DEFAULT_MIN_HISTORY_POINTS,
        help="Minimum history points required before a regression can be labeled plausible.",
    )
    parser.add_argument(
        "--recent-history-points",
        type=int,
        default=DEFAULT_RECENT_HISTORY_POINTS,
        help="Number of most recent baseline points to treat as recent history for regression classification.",
    )
    parser.add_argument("--project", default=DEFAULT_PROJECT, help="Bitbucket project key.")
    parser.add_argument("--repo", default=DEFAULT_REPOSITORY, help="Bitbucket repository key.")
    parser.add_argument("--repo-dir", default=".", help="Local repository directory for git operations.")
    parser.add_argument("--bench-cli", help="Path to bench-cli. Defaults to bench-cli on PATH or standard local build.")
    parser.add_argument("--show-improvements", action="store_true", help="Also show possible improvements.")
    parser.add_argument("--json-out", help="Write a machine-readable JSON report to this file.")
    return parser.parse_args()


def run_command(command: list[str], *, cwd: str | None = None, input_text: str | None = None) -> str:
    process = subprocess.run(
        command,
        cwd=cwd,
        input=input_text,
        text=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        check=False,
    )
    if process.returncode != 0:
        raise ScriptError(
            "Command failed with exit code {}:\n{}\n{}".format(
                process.returncode,
                " ".join(command),
                process.stderr.strip(),
            )
        )
    return process.stdout


def resolve_bench_cli(explicit_path: str | None) -> str:
    candidates = []
    if explicit_path:
        candidates.append(explicit_path)
    env_path = os.environ.get("BENCH_CLI")
    if env_path:
        candidates.append(env_path)
    which_bench_cli = shutil.which("bench-cli")
    if which_bench_cli:
        candidates.append(which_bench_cli)
    for candidate in candidates:
        if candidate and os.path.isfile(candidate) and os.access(candidate, os.X_OK):
            return candidate
    raise ScriptError(
        "Could not find bench-cli. Use --bench-cli or put bench-cli on PATH."
    )


def get_local_head_commit(repo_dir: str) -> str:
    return run_command(["git", "rev-parse", "HEAD"], cwd=repo_dir).strip()


def resolve_pr_number(args: argparse.Namespace, head_commit: str, repo_dir: str) -> int:
    if args.pr:
        return args.pr
    if not args.current_pr and args.merge_commit:
        raise ScriptError("--merge-commit requires --pr or --current-pr so the script can report context.")
    output = run_command(["gdev-cli", "bitbucket", "get-prs", "-c", head_commit], cwd=repo_dir)
    match = PR_NUMBER_RE.search(output)
    if not match:
        raise ScriptError("Could not resolve a PR for HEAD commit {}.\n{}".format(head_commit, output.strip()))
    return int(match.group("pr"))


def get_merge_base(repo_dir: str, baseline_branch_local_name: str, head_commit: str) -> str:
    return run_command(["git", "merge-base", baseline_branch_local_name, head_commit], cwd=repo_dir).strip()


def get_first_parent_ancestors(repo_dir: str, start_commit: str, limit: int) -> list[str]:
    output = run_command(
        ["git", "rev-list", "--first-parent", "--max-count", str(limit), start_commit],
        cwd=repo_dir,
    ).strip()
    return [line for line in output.splitlines() if line]


def get_gate_overview(project: str, repo: str, pr: int, repo_dir: str) -> dict[str, Any]:
    output = run_command(
        ["gdev-cli", "buildbot", "gate-overview", "-p", project, "-r", repo, "-pr", str(pr), "--json"],
        cwd=repo_dir,
    )
    return json.loads(output)


def get_builds_for_commit(commit: str, repo_dir: str) -> dict[str, BuildInfo]:
    output = run_command(["gdev-cli", "bitbucket", "get-builds", "-c", commit, "--all", "--json"], cwd=repo_dir)
    data = json.loads(output)
    values = data.get("values") or []
    builds = {}
    for value in values:
        url = value["url"]
        build_number_text = url.rstrip("/").split("/")[-1]
        if not build_number_text.isdigit():
            continue
        builds[value["key"]] = BuildInfo(
            key=value["key"],
            state=value["state"],
            url=url,
            build_number=int(build_number_text),
            description=value.get("description"),
        )
    return builds


def select_current_pybench_builds(builds: dict[str, BuildInfo]) -> dict[str, BuildInfo]:
    selected = {}
    for key, build in builds.items():
        if not key.startswith("pybench-"):
            continue
        if build.state != "SUCCESSFUL":
            continue
        if "/builders/ci_executor/builds/" not in build.url:
            continue
        selected[key] = build
    if not selected:
        raise ScriptError("No successful pybench builds found for the current merge commit.")
    return selected


def pair_jobs_with_baseline(
    current_builds: dict[str, BuildInfo],
    baseline_start_commit: str,
    repo_dir: str,
    ancestor_search_commits: int,
) -> tuple[list[JobPair], list[str]]:
    unresolved = set(current_builds)
    paired: list[JobPair] = []
    ancestor_commits = get_first_parent_ancestors(repo_dir, baseline_start_commit, ancestor_search_commits)
    seen_commits: set[str] = set()
    for commit in ancestor_commits:
        if commit in seen_commits:
            continue
        seen_commits.add(commit)
        baseline_builds = get_builds_for_commit(commit, repo_dir)
        for job_name in list(unresolved):
            baseline_build = baseline_builds.get(job_name)
            if baseline_build is None or baseline_build.state != "SUCCESSFUL":
                continue
            paired.append(
                JobPair(
                    job_name=job_name,
                    current_build_number=current_builds[job_name].build_number,
                    baseline_build_number=baseline_build.build_number,
                    baseline_commit=commit,
                )
            )
            unresolved.remove(job_name)
        if not unresolved:
            break
    paired.sort(key=lambda pair: pair.job_name)
    return paired, sorted(unresolved)


def metric_names_for_job(job_name: str) -> tuple[str, ...]:
    if "heap" in job_name:
        return PRIMARY_HEAP_METRICS
    return (PRIMARY_TIME_METRIC,)


def build_measurement_query(current_commit: str, job_pairs: list[JobPair]) -> dict[str, Any]:
    current_build_numbers = [str(pair.current_build_number) for pair in job_pairs]
    baseline_build_numbers = [str(pair.baseline_build_number) for pair in job_pairs]
    commit_revs = sorted({current_commit} | {pair.baseline_commit for pair in job_pairs})
    return {
        "queries": [
            {
                "format": {"type": "application/bench-results+json"},
                "selectors": [
                    "bench-suite",
                    "benchmark",
                    "build.job-name",
                    "machine.name",
                    "host-vm",
                    "host-vm-config",
                    "guest-vm",
                    "guest-vm-config",
                    "branch",
                    "commit.rev",
                    "build.number",
                    "metric.unit",
                    "metric.name",
                    "metric.better",
                    {"avg": "metric.value"},
                    {"stddev": "metric.value"},
                    {"count": "metric.value"},
                ],
                "filters": {
                    "commit.rev": {"enum": commit_revs},
                    "build.number": {"enum": current_build_numbers + baseline_build_numbers},
                    "metric.name": {"enum": [PRIMARY_TIME_METRIC, *PRIMARY_HEAP_METRICS]},
                },
                "grouping": [
                    "bench-suite",
                    "benchmark",
                    "build.job-name",
                    "machine.name",
                    "host-vm",
                    "host-vm-config",
                    "guest-vm",
                    "guest-vm-config",
                    "branch",
                    "commit.rev",
                    "build.number",
                    "metric.unit",
                    "metric.name",
                    "metric.better",
                ],
                "clustering": [
                    {"build.job-name": "asc"},
                    {"bench-suite": "asc"},
                    {"benchmark": "asc"},
                    {"metric.name": "asc"},
                ],
                "samplers": [],
            }
        ]
    }


def run_bench_query(bench_cli: str, query: dict[str, Any], repo_dir: str) -> dict[str, Any]:
    output = run_command([bench_cli, "run", "-"], cwd=repo_dir, input_text=json.dumps(query))
    return json.loads(output)


def parse_measurements(query_result: dict[str, Any]) -> list[Measurement]:
    selectors = query_result["metadata"]["selectors"]
    selector_to_index = {name: index for index, name in enumerate(selectors)}
    measurements = []
    for cluster in query_result["results"]:
        for row in cluster["data"]:
            measurements.append(
                Measurement(
                    suite=row[selector_to_index["bench-suite"]],
                    benchmark=row[selector_to_index["benchmark"]],
                    job_name=row[selector_to_index["build.job-name"]],
                    machine_name=row[selector_to_index["machine.name"]],
                    host_vm=row[selector_to_index["host-vm"]],
                    host_vm_config=row[selector_to_index["host-vm-config"]],
                    guest_vm=row[selector_to_index["guest-vm"]],
                    guest_vm_config=row[selector_to_index["guest-vm-config"]],
                    branch=row[selector_to_index["branch"]],
                    commit_rev=row[selector_to_index["commit.rev"]],
                    build_number=int(row[selector_to_index["build.number"]]),
                    metric_unit=row[selector_to_index["metric.unit"]],
                    metric_name=row[selector_to_index["metric.name"]],
                    metric_better=row[selector_to_index["metric.better"]],
                    avg=float(row[selector_to_index["metric.value:AVG"]]),
                    stddev=float(row[selector_to_index["metric.value:STDDEV"]]),
                    count=int(row[selector_to_index["metric.value:COUNT"]]),
                )
            )
    return measurements


def pick_preferred_measurements(
    measurements: list[Measurement],
    current_commit: str,
    job_pairs: list[JobPair],
) -> list[tuple[Measurement, Measurement]]:
    comparison_pairs = []
    for pair in job_pairs:
        current_rows = [
            measurement
            for measurement in measurements
            if measurement.job_name == pair.job_name
            and measurement.build_number == pair.current_build_number
            and measurement.commit_rev == current_commit
        ]
        baseline_rows = [
            measurement
            for measurement in measurements
            if measurement.job_name == pair.job_name
            and measurement.build_number == pair.baseline_build_number
            and measurement.commit_rev == pair.baseline_commit
        ]
        grouped_current = {
            (
                measurement.machine_name,
                measurement.host_vm,
                measurement.host_vm_config,
                measurement.guest_vm,
                measurement.guest_vm_config,
                measurement.benchmark,
                measurement.metric_name,
            ): measurement
            for measurement in current_rows
        }
        grouped_baseline = {
            (
                measurement.machine_name,
                measurement.host_vm,
                measurement.host_vm_config,
                measurement.guest_vm,
                measurement.guest_vm_config,
                measurement.benchmark,
                measurement.metric_name,
            ): measurement
            for measurement in baseline_rows
        }
        machines_and_benchmarks = {
            (machine_name, host_vm, host_vm_config, guest_vm, guest_vm_config, benchmark)
            for machine_name, host_vm, host_vm_config, guest_vm, guest_vm_config, benchmark, _metric_name in grouped_current
        }
        for machine_name, host_vm, host_vm_config, guest_vm, guest_vm_config, benchmark in sorted(machines_and_benchmarks):
            chosen_current = None
            chosen_baseline = None
            for metric_name in metric_names_for_job(pair.job_name):
                candidate_current = grouped_current.get(
                    (machine_name, host_vm, host_vm_config, guest_vm, guest_vm_config, benchmark, metric_name)
                )
                candidate_baseline = grouped_baseline.get(
                    (machine_name, host_vm, host_vm_config, guest_vm, guest_vm_config, benchmark, metric_name)
                )
                if candidate_current is None or candidate_baseline is None:
                    continue
                chosen_current = candidate_current
                chosen_baseline = candidate_baseline
                break
            if chosen_current is None or chosen_baseline is None:
                continue
            comparison_pairs.append((chosen_current, chosen_baseline))
    return comparison_pairs


def compute_regression_percent(current_value: float, baseline_value: float, lower_is_better: bool) -> float:
    if baseline_value == 0:
        return 0.0
    if lower_is_better:
        return (current_value / baseline_value - 1.0) * 100.0
    return (1.0 - current_value / baseline_value) * 100.0


def worse_direction_delta(observed_value: float, reference_value: float, lower_is_better: bool) -> float:
    if lower_is_better:
        return observed_value - reference_value
    return reference_value - observed_value


def directional_delta(observed_value: float, reference_value: float, lower_is_better: bool, direction: str) -> float:
    worse_delta = worse_direction_delta(observed_value, reference_value, lower_is_better)
    if direction == "regression":
        return worse_delta
    if direction == "improvement":
        return -worse_delta
    raise ValueError("Unsupported direction: {}".format(direction))


def directional_percent(observed_value: float, reference_value: float, lower_is_better: bool, direction: str) -> float:
    regression_pct = compute_regression_percent(observed_value, reference_value, lower_is_better)
    if direction == "regression":
        return regression_pct
    if direction == "improvement":
        return -regression_pct
    raise ValueError("Unsupported direction: {}".format(direction))


def directional_extreme_value(values: list[float], lower_is_better: bool, direction: str) -> float:
    if direction == "regression":
        return max(values) if lower_is_better else min(values)
    if direction == "improvement":
        return min(values) if lower_is_better else max(values)
    raise ValueError("Unsupported direction: {}".format(direction))


def percentile(values: list[float], fraction: float) -> float:
    if not values:
        raise ValueError("percentile() requires at least one value")
    if len(values) == 1:
        return values[0]
    index = (len(values) - 1) * fraction
    lower_index = math.floor(index)
    upper_index = math.ceil(index)
    if lower_index == upper_index:
        return values[int(index)]
    return values[lower_index] * (upper_index - index) + values[upper_index] * (index - lower_index)


def build_history_query(
    baseline_branch: str,
    history_days: int,
    candidates: list[tuple[Measurement, Measurement]],
) -> dict[str, Any]:
    jobs = sorted({current.job_name for current, _baseline in candidates})
    benchmarks = sorted({current.benchmark for current, _baseline in candidates})
    machines = sorted({current.machine_name for current, _baseline in candidates})
    metric_names = sorted({current.metric_name for current, _baseline in candidates})
    return {
        "queries": [
            {
                "format": {"type": "application/bench-results+json"},
                "selectors": [
                    "bench-suite",
                    "benchmark",
                    "build.job-name",
                    "machine.name",
                    "host-vm",
                    "host-vm-config",
                    "guest-vm",
                    "guest-vm-config",
                    "branch",
                    "commit.rev",
                    "commit.committer-ts",
                    "build.number",
                    "metric.unit",
                    "metric.name",
                    "metric.better",
                    {"avg": "metric.value"},
                ],
                "filters": {
                    "build.job-name": {"enum": jobs},
                    "benchmark": {"enum": benchmarks},
                    "machine.name": {"enum": machines},
                    "metric.name": {"enum": metric_names},
                    "branch": {"enum": [baseline_branch]},
                    "commit.committer-ts": {"last-n": history_days, "unit": "D"},
                },
                "grouping": [
                    "bench-suite",
                    "benchmark",
                    "build.job-name",
                    "machine.name",
                    "host-vm",
                    "host-vm-config",
                    "guest-vm",
                    "guest-vm-config",
                    "branch",
                    "commit.rev",
                    "commit.committer-ts",
                    "build.number",
                    "metric.unit",
                    "metric.name",
                    "metric.better",
                ],
                "clustering": [
                    {"build.job-name": "asc"},
                    {"benchmark": "asc"},
                    {"commit.committer-ts": "asc"},
                ],
                "samplers": [],
            }
        ]
    }


def history_key_for_measurement(measurement: Measurement) -> tuple[str, str, str, str, str, str, str, str]:
    return (
        measurement.job_name,
        measurement.machine_name,
        measurement.host_vm,
        measurement.host_vm_config,
        measurement.guest_vm,
        measurement.guest_vm_config,
        measurement.benchmark,
        measurement.metric_name,
    )


def parse_history_values(query_result: dict[str, Any]) -> dict[tuple[str, str, str, str, str, str, str, str], list[float]]:
    selectors = query_result["metadata"]["selectors"]
    selector_to_index = {name: index for index, name in enumerate(selectors)}
    history: dict[tuple[str, str, str, str, str, str, str, str], list[float]] = {}
    for cluster in query_result["results"]:
        for row in cluster["data"]:
            key = (
                row[selector_to_index["build.job-name"]],
                row[selector_to_index["machine.name"]],
                row[selector_to_index["host-vm"]],
                row[selector_to_index["host-vm-config"]],
                row[selector_to_index["guest-vm"]],
                row[selector_to_index["guest-vm-config"]],
                row[selector_to_index["benchmark"]],
                row[selector_to_index["metric.name"]],
            )
            history.setdefault(key, []).append(float(row[selector_to_index["metric.value:AVG"]]))
    return history


def calculate_history_stats(
    values: list[float],
    current_value: float,
    lower_is_better: bool,
    recent_history_points: int,
    direction: str,
) -> HistoryStats:
    ordered_values = list(values)
    sorted_values = sorted(ordered_values)
    recent_values = ordered_values[-min(len(ordered_values), recent_history_points) :]
    recent_sorted_values = sorted(recent_values)
    recent_p25 = percentile(recent_sorted_values, 0.25)
    recent_p75 = percentile(recent_sorted_values, 0.75)
    recent_median = statistics.median(recent_sorted_values)
    recent_absolute_deviations = [abs(value - recent_median) for value in recent_values]
    recent_mad = statistics.median(recent_absolute_deviations)
    recent_iqr = recent_p75 - recent_p25
    recent_mad_sigma = MAD_SCALE_FACTOR * recent_mad
    recent_iqr_sigma = recent_iqr / IQR_SIGMA_FACTOR if recent_iqr else 0.0
    # Pure MAD is too optimistic for multi-modal recent windows. Use the broader robust scale.
    recent_sigma = max(recent_mad_sigma, recent_iqr_sigma)
    mean_value = statistics.mean(sorted_values)
    stdev_value = statistics.pstdev(sorted_values) if len(sorted_values) > 1 else 0.0
    if mean_value == 0:
        cv = 0.0
    else:
        cv = stdev_value / mean_value
    points_at_or_worse = 0
    for value in ordered_values:
        if directional_percent(value, current_value, lower_is_better, direction) >= 0:
            points_at_or_worse += 1
    adjacent_changes = []
    for previous, current in zip(ordered_values, ordered_values[1:]):
        if previous != 0:
            adjacent_changes.append(abs(compute_regression_percent(current, previous, lower_is_better)))
    recent_points_at_or_worse = 0
    for value in recent_values:
        if directional_percent(value, current_value, lower_is_better, direction) >= 0:
            recent_points_at_or_worse += 1
    recent_tail_values = ordered_values[-min(len(ordered_values), PREEXISTING_SHIFT_TAIL_POINTS) :]
    recent_tail_points_at_or_worse = 0
    for value in recent_tail_values:
        if directional_percent(value, current_value, lower_is_better, direction) >= 0:
            recent_tail_points_at_or_worse += 1
    return HistoryStats(
        n=len(sorted_values),
        minimum=sorted_values[0],
        p10=percentile(sorted_values, 0.10),
        median=statistics.median(sorted_values),
        p90=percentile(sorted_values, 0.90),
        maximum=sorted_values[-1],
        mean=mean_value,
        cv=cv,
        points_at_or_worse_than_current=points_at_or_worse,
        typical_adjacent_change_pct=statistics.median(adjacent_changes) if adjacent_changes else 0.0,
        recent_n=len(recent_sorted_values),
        recent_median=recent_median,
        recent_mad=recent_mad,
        recent_iqr=recent_iqr,
        recent_sigma=recent_sigma,
        recent_p90=percentile(recent_sorted_values, 0.90),
        recent_maximum=recent_sorted_values[-1],
        recent_points_at_or_worse_than_current=recent_points_at_or_worse,
        recent_tail_n=len(recent_tail_values),
        recent_tail_minimum=min(recent_tail_values),
        recent_tail_maximum=max(recent_tail_values),
        recent_tail_points_at_or_worse_than_current=recent_tail_points_at_or_worse,
    )


def classify_change(
    current: Measurement,
    baseline: Measurement,
    history_values: list[float] | None,
    threshold_percent: float,
    absolute_time_delta_floor: float,
    min_history_points: int,
    recent_history_points: int,
    direction: str,
) -> tuple[str, str, HistoryStats | None]:
    lower_is_better = current.metric_better == "lower"
    delta_pct = directional_percent(current.avg, baseline.avg, lower_is_better, direction)
    abs_delta = abs(current.avg - baseline.avg)
    if current.metric_name == PRIMARY_TIME_METRIC and abs_delta < absolute_time_delta_floor:
        return "flaky", "tiny absolute time delta", None
    if not history_values:
        return "inconclusive", "no baseline history available", None
    stats = calculate_history_stats(history_values, current.avg, lower_is_better, recent_history_points, direction)
    current_vs_recent_median = directional_percent(current.avg, stats.recent_median, lower_is_better, direction)
    if stats.n < min_history_points:
        return "inconclusive", "too little baseline history to run the recent median/robust-scale check", stats
    if stats.recent_n < min_history_points:
        return "inconclusive", "too little recent baseline history to run the recent median/robust-scale check", stats

    change_delta = directional_delta(current.avg, stats.recent_median, lower_is_better, direction)
    if change_delta <= 0:
        return "flaky", "current value is not {} than the recent median".format("better" if direction == "improvement" else "worse"), stats

    if stats.recent_sigma == 0:
        if current_vs_recent_median > max(threshold_percent / 2.0, 5.0):
            if stats.recent_tail_points_at_or_worse_than_current > 0:
                tail_extreme = directional_extreme_value(
                    [stats.recent_tail_minimum, stats.recent_tail_maximum], lower_is_better, direction
                )
                return (
                    "inconclusive",
                    "one of the last {} baseline points already reached {:.6g}".format(stats.recent_tail_n, tail_extreme),
                    stats,
                )
            return "plausible", "current exceeds a perfectly stable recent baseline", stats
        return "inconclusive", "recent baseline is perfectly stable but effect size is still small", stats

    mad_z_score = change_delta / stats.recent_sigma
    if mad_z_score >= REAL_MAD_Z_THRESHOLD and current_vs_recent_median > max(threshold_percent / 2.0, 5.0):
        if stats.recent_tail_points_at_or_worse_than_current > 0:
            tail_extreme = directional_extreme_value(
                [stats.recent_tail_minimum, stats.recent_tail_maximum], lower_is_better, direction
            )
            return (
                "inconclusive",
                "one of the last {} baseline points already reached {:.6g}".format(stats.recent_tail_n, tail_extreme),
                stats,
            )
        return "plausible", "current is {:.1f} robust-sigmas {} than the recent median".format(mad_z_score, "better" if direction == "improvement" else "worse"), stats
    if mad_z_score <= FLAKY_MAD_Z_THRESHOLD:
        return "flaky", "current is only {:.1f} robust-sigmas {} than the recent median".format(mad_z_score, "better" if direction == "improvement" else "worse"), stats
    return "inconclusive", "current is {:.1f} robust-sigmas {} than the recent median".format(mad_z_score, "better" if direction == "improvement" else "worse"), stats


def make_finding(
    current: Measurement,
    baseline: Measurement,
    classification: str,
    reason: str,
    history: HistoryStats | None,
    direction: str,
) -> RegressionFinding:
    lower_is_better = current.metric_better == "lower"
    delta_pct = directional_percent(current.avg, baseline.avg, lower_is_better, direction)
    return RegressionFinding(
        direction=direction,
        benchmark=current.benchmark,
        suite=current.suite,
        machine_name=current.machine_name,
        host_vm=current.host_vm,
        host_vm_config=current.host_vm_config,
        guest_vm=current.guest_vm,
        guest_vm_config=current.guest_vm_config,
        metric_name=current.metric_name,
        metric_unit=current.metric_unit,
        baseline_commit=baseline.commit_rev,
        baseline_build_number=baseline.build_number,
        baseline_value=baseline.avg,
        current_commit=current.commit_rev,
        current_build_number=current.build_number,
        current_value=current.avg,
        delta_pct=delta_pct,
        abs_delta=abs(current.avg - baseline.avg),
        classification=classification,
        reason=reason,
        history=history,
    )


def collect_findings(
    comparison_pairs: list[tuple[Measurement, Measurement]],
    history_by_key: dict[tuple[str, str, str, str, str, str, str, str], list[float]],
    threshold_percent: float,
    absolute_time_delta_floor: float,
    min_history_points: int,
    recent_history_points: int,
    direction: str,
) -> list[RegressionFinding]:
    findings = []
    for current, baseline in comparison_pairs:
        lower_is_better = current.metric_better == "lower"
        delta_pct = directional_percent(current.avg, baseline.avg, lower_is_better, direction)
        if delta_pct <= threshold_percent:
            continue
        history_key = history_key_for_measurement(current)
        classification, reason, history = classify_change(
            current,
            baseline,
            history_by_key.get(history_key),
            threshold_percent,
            absolute_time_delta_floor,
            min_history_points,
            recent_history_points,
            direction,
        )
        findings.append(make_finding(current, baseline, classification, reason, history, direction))
    findings.sort(
        key=lambda finding: (
            -finding.delta_pct,
            finding.host_vm,
            finding.host_vm_config,
            finding.guest_vm,
            finding.guest_vm_config,
            finding.benchmark,
        )
    )
    return findings


def render_table(rows: list[list[str]], headers: list[str]) -> str:
    widths = [len(header) for header in headers]
    for row in rows:
        for index, value in enumerate(row):
            widths[index] = max(widths[index], len(value))
    header_line = "  ".join(header.ljust(widths[index]) for index, header in enumerate(headers))
    separator_line = "  ".join("-" * width for width in widths)
    body_lines = ["  ".join(value.ljust(widths[index]) for index, value in enumerate(row)) for row in rows]
    return "\n".join([header_line, separator_line, *body_lines])


def configuration_label(finding: RegressionFinding) -> str:
    return ",".join(
        [
            finding.host_vm,
            finding.host_vm_config,
            finding.guest_vm,
            finding.guest_vm_config,
        ]
    )


def append_direction_sections(lines: list[str], findings: list[RegressionFinding], direction: str) -> None:
    plausible_findings = [finding for finding in findings if finding.direction == direction and finding.classification == "plausible"]
    flaky_findings = [finding for finding in findings if finding.direction == direction and finding.classification == "flaky"]
    inconclusive_findings = [finding for finding in findings if finding.direction == direction and finding.classification == "inconclusive"]
    plural = "{}s".format(direction)
    title = direction.capitalize()
    if plausible_findings:
        rows = []
        for finding in plausible_findings:
            rows.append(
                [
                    finding.benchmark,
                    "{:+.1f}%".format(finding.delta_pct),
                    configuration_label(finding),
                    finding.reason,
                ]
            )
        lines.append("{}:".format(plural))
        lines.append(render_table(rows, ["benchmark", "delta", "configuration", "reason"]))
    else:
        lines.append("{}: none".format(plural))
    if inconclusive_findings:
        lines.append("")
        lines.append("Inconclusive {}:".format(plural))
        for finding in inconclusive_findings:
            lines.append(
                "- {} ({:+.1f}%, {}): {}".format(
                    finding.benchmark,
                    finding.delta_pct,
                    configuration_label(finding),
                    finding.reason,
                )
            )
    else:
        lines.append("")
        lines.append("Inconclusive {}: none".format(plural))
    if flaky_findings:
        lines.append("")
        lines.append("Flaky {}:".format(plural))
        for finding in flaky_findings:
            lines.append(
                "- {} ({:+.1f}%, {}): {}".format(
                    finding.benchmark,
                    finding.delta_pct,
                    configuration_label(finding),
                    finding.reason,
                )
            )
    else:
        lines.append("")
        lines.append("Flaky {}: none".format(plural))


def build_warnings(job_pairs: list[JobPair], baseline_commit: str, unresolved_jobs: list[str]) -> list[str]:
    warnings = []
    fallback_counts: dict[str, int] = {}
    for pair in job_pairs:
        if pair.baseline_commit == baseline_commit:
            continue
        fallback_counts[pair.baseline_commit] = fallback_counts.get(pair.baseline_commit, 0) + 1

    if fallback_counts:
        fallback_summary = ", ".join(
            "{} job(s) used {}".format(count, commit[:12])
            for commit, count in sorted(fallback_counts.items(), key=lambda item: item[0])
        )
        warnings.append(
            "Baseline was not uniform: {} job(s) used an earlier baseline than merge-base {}. {}".format(
                sum(fallback_counts.values()),
                baseline_commit[:12],
                fallback_summary,
            )
        )

    if unresolved_jobs:
        preview = ", ".join(unresolved_jobs[:5])
        if len(unresolved_jobs) > 5:
            preview += ", ..."
        warnings.append(
            "WARNING: {} job(s) did not find any baseline within the ancestor search window and were skipped: {}".format(
                len(unresolved_jobs),
                preview,
            )
        )

    return warnings


def summarize_findings(findings: list[RegressionFinding], warnings: list[str]) -> str:
    lines = []
    if warnings:
        lines.append("Warnings:")
        for warning in warnings:
            lines.append("- {}".format(warning))
        lines.append("")
    append_direction_sections(lines, findings, "regression")
    if any(finding.direction == "improvement" for finding in findings):
        lines.append("")
        append_direction_sections(lines, findings, "improvement")
    return "\n".join(lines)


def report_json_object(
    pr_number: int,
    head_commit: str,
    merge_commit: str,
    baseline_commit: str,
    unresolved_jobs: list[str],
    findings: list[RegressionFinding],
    warnings: list[str],
) -> dict[str, Any]:
    return {
        "context": {
            "pr": pr_number,
            "head_commit": head_commit,
            "merge_commit": merge_commit,
            "baseline_commit": baseline_commit,
            "unresolved_jobs": unresolved_jobs,
            "warnings": warnings,
        },
        "plausible": [finding_to_dict(finding) for finding in findings if finding.direction == "regression" and finding.classification == "plausible"],
        "flaky": [finding_to_dict(finding) for finding in findings if finding.direction == "regression" and finding.classification == "flaky"],
        "inconclusive": [finding_to_dict(finding) for finding in findings if finding.direction == "regression" and finding.classification == "inconclusive"],
        "improvements": [finding_to_dict(finding) for finding in findings if finding.direction == "improvement"],
    }


def finding_to_dict(finding: RegressionFinding) -> dict[str, Any]:
    data = asdict(finding)
    if finding.history is None:
        data["history"] = None
    return data


def main() -> int:
    args = parse_args()
    repo_dir = str(Path(args.repo_dir).resolve())
    bench_cli = resolve_bench_cli(args.bench_cli)
    head_commit = args.head_commit or get_local_head_commit(repo_dir)
    pr_number = resolve_pr_number(args, head_commit, repo_dir)
    gate_overview = get_gate_overview(args.project, args.repo, pr_number, repo_dir)
    merge_commit = args.merge_commit or gate_overview["summary"]["mergeCommit"]
    baseline_branch_local_name = args.baseline_branch_local_name or args.baseline_branch
    baseline_commit = get_merge_base(repo_dir, baseline_branch_local_name, head_commit)

    current_builds = select_current_pybench_builds(get_builds_for_commit(merge_commit, repo_dir))
    job_pairs, unresolved_jobs = pair_jobs_with_baseline(
        current_builds,
        baseline_commit,
        repo_dir,
        args.ancestor_search_commits,
    )
    if not job_pairs:
        raise ScriptError("Could not pair any current pybench jobs with baseline jobs.")

    measurement_result = run_bench_query(bench_cli, build_measurement_query(merge_commit, job_pairs), repo_dir)
    measurements = parse_measurements(measurement_result)
    comparison_pairs = pick_preferred_measurements(measurements, merge_commit, job_pairs)
    if not comparison_pairs:
        raise ScriptError("Bench Server query returned no comparable primary metrics for paired jobs.")

    history_result = run_bench_query(
        bench_cli,
        build_history_query(args.baseline_branch, args.history_days, comparison_pairs),
        repo_dir,
    )
    history_by_key = parse_history_values(history_result)
    findings = collect_findings(
        comparison_pairs,
        history_by_key,
        args.threshold,
        args.absolute_time_delta_floor,
        args.min_history_points,
        args.recent_history_points,
        "regression",
    )
    if args.show_improvements:
        findings.extend(
            collect_findings(
                comparison_pairs,
                history_by_key,
                args.threshold,
                args.absolute_time_delta_floor,
                args.min_history_points,
                args.recent_history_points,
                "improvement",
            )
        )
    warnings = build_warnings(job_pairs, baseline_commit, unresolved_jobs)

    header = [
        "PR {} | head {} | merge {} | baseline {}".format(
            pr_number,
            head_commit[:12],
            merge_commit[:12],
            baseline_commit[:12],
        ),
        "Paired pybench jobs: {} | unresolved baseline matches: {}".format(len(job_pairs), len(unresolved_jobs)),
        "{} above {:.1f}%: {}".format(
            "Candidate changes" if args.show_improvements else "Candidate regressions",
            args.threshold,
            len(findings),
        ),
        "",
    ]
    print("\n".join(header) + summarize_findings(findings, warnings))

    if args.json_out:
        json_path = Path(args.json_out)
        json_path.write_text(
            json.dumps(
                report_json_object(pr_number, head_commit, merge_commit, baseline_commit, unresolved_jobs, findings, warnings),
                indent=2,
                sort_keys=True,
            )
            + "\n",
            encoding="utf-8",
        )
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except ScriptError as error:
        print(str(error), file=sys.stderr)
        raise SystemExit(1)
