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
import base64
import json
import re
import shlex
import subprocess
import sys
import tempfile
import time

from dataclasses import dataclass
from pathlib import Path
from urllib.error import HTTPError, URLError
from urllib.parse import parse_qs, quote, urlencode, urlparse
from urllib.request import Request, urlopen


CONFIG_FILENAME = "bisect-benchmark.ini"
RESULT_FILENAME = "bisect-benchmark-result.json"
BISECT_JOB_NAME = "bisect-benchmark"
JOB_SUBMISSION_REGEX = BISECT_JOB_NAME
BRANCH_SUPPORT_FILES = (
    Path("mx.graalpython") / "mx_graalpython_bisect.py",
    Path("ci") / "python-bench.libsonnet",
)
DEFAULT_PROJECT = "G"
DEFAULT_REPOSITORY = "graalpython"
AUTH_FILE = Path.home() / ".ol" / "ola_auth.json"
ENUMERATION_POLL_SECONDS = 5
BUILD_POLL_SECONDS = 15
ENUMERATION_TIMEOUT_SECONDS = 20 * 60
BUILD_TIMEOUT_SECONDS = 24 * 60 * 60
CONFIG_VIEW_BITBUCKET_RE = re.compile(r"^\s*[•*]?\s*bitbucket:\s*(\S+)\s*$", re.MULTILINE)

DEBUG = False


class ScriptError(RuntimeError):
    pass


@dataclass(frozen=True)
class BuildRecord:
    key: str
    state: str
    url: str
    date_added: int
    build_number: int | None
    request_id: int | None

    @property
    def sort_key(self) -> tuple[int, int, int]:
        return (
            self.date_added,
            self.build_number if self.build_number is not None else -1,
            self.request_id if self.request_id is not None else -1,
        )


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        formatter_class=argparse.ArgumentDefaultsHelpFormatter,
        description="Generate and optionally submit a bisect-benchmark CI workflow for a benchmark regression.",
    )
    parser.add_argument("benchmark_job_name", help="Benchmark job key, for example pybench-micro-graalvm_ee_default-post_merge-linux-amd64-jdk-latest.")
    parser.add_argument("benchmark_name", help="Benchmark selector to narrow the benchmark command to a single benchmark.")
    parser.add_argument("metric", help="Benchmark metric name, or WORKS.")
    parser.add_argument("good_commit", help="Known good GraalPy commit or ref.")
    parser.add_argument("bad_commit", help="Known bad GraalPy commit or ref.")
    parser.add_argument("--config-only", action="store_true", help="Print the generated bisect config and exit.")
    parser.add_argument("--force-rebuild", action="store_true", help="Submit a fresh bisect job even if one already exists.")
    parser.add_argument("--debug", action="store_true", help="Print progress information to stderr.")
    parser.add_argument("--repo-dir", default=".", help=argparse.SUPPRESS)
    return parser.parse_args()


def debug(message: str) -> None:
    if DEBUG:
        print(message, file=sys.stderr, flush=True)


def run_command(command: list[str], *, cwd: str | Path | None = None) -> str:
    process = subprocess.run(
        command,
        cwd=str(cwd) if cwd is not None else None,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        text=True,
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


def get_repo_root(repo_dir: str | Path) -> Path:
    return Path(run_command(["git", "rev-parse", "--show-toplevel"], cwd=repo_dir).strip())


def resolve_commit(repo_dir: str | Path, revision: str) -> str:
    return run_command(["git", "rev-parse", revision], cwd=repo_dir).strip()


def build_branch_name(job_name: str, benchmark_name: str, metric: str, good_commit: str, bad_commit: str) -> str:
    slug = "_".join(
        [
            job_name,
            benchmark_name,
            metric,
            good_commit,
            bad_commit,
        ]
    )
    return "bisect/{}".format(slug)


def parse_build_number(url: str) -> int | None:
    if "/builders/ci_executor/builds/" not in url:
        return None
    tail = url.rstrip("/").split("/")[-1]
    return int(tail) if tail.isdigit() else None


def parse_request_id(url: str) -> int | None:
    parsed = urlparse(url)
    request_id = parse_qs(parsed.query).get("brid", [None])[0]
    return int(request_id) if request_id and request_id.isdigit() else None


def parse_build_record(value: dict[str, object]) -> BuildRecord:
    url = str(value["url"])
    return BuildRecord(
        key=str(value["key"]),
        state=str(value["state"]),
        url=url,
        date_added=int(value.get("dateAdded") or 0),
        build_number=parse_build_number(url),
        request_id=parse_request_id(url),
    )


def get_matching_builds(repo_dir: str | Path, commit: str, job_name: str) -> list[BuildRecord]:
    output = run_command(["gdev-cli", "bitbucket", "get-builds", "-c", commit, "--all", "--json"], cwd=repo_dir)
    data = json.loads(output)
    return [
        parse_build_record(value)
        for value in data.get("values", [])
        if value.get("key") == job_name
    ]


def pick_newest_build(builds: list[BuildRecord]) -> BuildRecord | None:
    if not builds:
        return None
    return max(builds, key=lambda build: build.sort_key)


def read_text_from_url(url: str, headers: dict[str, str] | None = None) -> str:
    request_headers = {"User-Agent": "bisect-benchmark-regression"}
    if headers:
        request_headers.update(headers)
    request = Request(url, headers=request_headers)
    try:
        with urlopen(request, timeout=60) as response:
            return response.read().decode("utf-8")
    except (HTTPError, URLError) as exc:
        raise ScriptError("Failed to fetch {}: {}".format(url, exc)) from exc


def fetch_uploaded_log_text(build_url: str, filename: str) -> str:
    artifact_url = "{}/steps/LogfileUploader/logs/{}/text".format(build_url.rstrip("/"), quote(filename, safe=""))
    return read_text_from_url(artifact_url)


def get_reference_build(repo_dir: str | Path, job_name: str, bad_commit: str, good_commit: str) -> BuildRecord:
    for commit in (bad_commit, good_commit):
        build = pick_newest_build(
            [record for record in get_matching_builds(repo_dir, commit, job_name) if record.build_number is not None]
        )
        if build is not None:
            return build
    raise ScriptError(
        "Could not find any completed '{}' builds for {} or {}.".format(job_name, bad_commit, good_commit)
    )


def benchmark_selector_for_command(benchmark_name: str) -> str:
    if ":" in benchmark_name:
        return benchmark_name.rsplit(":", 1)[-1]
    if "." in benchmark_name:
        return benchmark_name.rsplit(".", 1)[-1]
    return benchmark_name


def narrow_command(benchmark_command: str, benchmark_name: str) -> str:
    components = shlex.split(benchmark_command)
    try:
        benchmark_args_start = components.index("benchmark") + 1
        benchmark_args_end = components.index("--")
        before = components[:benchmark_args_start]
        after = components[benchmark_args_end:]
        benchmark_args = components[benchmark_args_start:benchmark_args_end]
        benchmark_arg = next(arg for arg in benchmark_args if not arg.startswith("-"))
        suite = re.sub(r":.*", "", benchmark_arg)
        benchmark_args[benchmark_args.index(benchmark_arg)] = "{}:{}".format(
            suite,
            benchmark_selector_for_command(benchmark_name),
        )
        return shlex.join(before + benchmark_args + after)
    except (StopIteration, ValueError) as exc:
        raise ScriptError("Could not determine how to narrow benchmark command '{}'.".format(benchmark_command)) from exc


def extract_commands(log: str, benchmark_name: str) -> tuple[str, str]:
    build_commands = re.findall(r"\bRunning (mx\b.*\bbuild\b.*)", log)
    benchmark_commands = re.findall(r"\bRunning (mx\b.*\bbenchmark\b.*)", log)
    if not build_commands:
        raise ScriptError("Could not find a build command in the benchmark build log.")
    if not benchmark_commands:
        raise ScriptError("Could not find a benchmark command in the benchmark build log.")
    return build_commands[-1], narrow_command(benchmark_commands[-1], benchmark_name)


def benchmark_match_score(candidate: str, selector: str) -> int:
    if candidate == selector:
        return 100
    if candidate.rsplit(".", 1)[-1] == selector:
        return 90
    if candidate.rsplit(":", 1)[-1] == selector:
        return 80
    if candidate.endswith(".{}".format(selector)):
        return 70
    if candidate.endswith(":{}".format(selector)):
        return 60
    return 0


def resolve_results_benchmark_name(build_url: str, selector: str, metric: str) -> str | None:
    if metric == "WORKS":
        return None
    data = json.loads(fetch_uploaded_log_text(build_url, "bench-results.json"))
    candidates: list[tuple[int, int, str]] = []
    for index, document in enumerate(data.get("queries", [])):
        if document.get("metric.name") != metric:
            continue
        benchmark = document.get("benchmark")
        if not isinstance(benchmark, str):
            continue
        score = benchmark_match_score(benchmark, selector)
        if score > 0:
            candidates.append((score, index, benchmark))
    if not candidates:
        return None
    candidates.sort()
    best_score = candidates[-1][0]
    best_matches = [benchmark for score, _index, benchmark in candidates if score == best_score]
    if len(set(best_matches)) != 1:
        return None
    best_match = best_matches[-1]
    return best_match if best_match != selector else None


def build_config_text(
    build_command: str,
    benchmark_command: str,
    good_commit: str,
    bad_commit: str,
    metric: str,
    enterprise: bool,
    benchmark_name: str | None,
) -> str:
    lines = [
        "[bisect-benchmark]",
        "build_command = {}".format(build_command),
        "benchmark_command = {}".format(benchmark_command),
    ]
    if benchmark_name:
        lines.append("benchmark_name = {}".format(benchmark_name))
    lines.extend(
        [
            "bad = {}".format(bad_commit),
            "good = {}".format(good_commit),
            "enterprise = {}".format("true" if enterprise else "false"),
            "benchmark_metric = {}".format(metric),
        ]
    )
    return "\n".join(lines) + "\n"


def get_bitbucket_base_url(repo_dir: str | Path) -> str:
    output = run_command(["gdev-cli", "config-view"], cwd=repo_dir)
    match = CONFIG_VIEW_BITBUCKET_RE.search(output)
    if not match:
        raise ScriptError("Could not determine Bitbucket base URL from gdev-cli config-view.")
    return match.group(1)


def get_bitbucket_token(bitbucket_base_url: str) -> str:
    with AUTH_FILE.open(encoding="utf-8") as auth_file:
        data = json.load(auth_file)
    try:
        encoded_token = data["auths"][bitbucket_base_url]["token"]
    except KeyError as exc:
        raise ScriptError("Could not find a token for {} in {}.".format(bitbucket_base_url, AUTH_FILE)) from exc
    try:
        return base64.b64decode(encoded_token).decode("utf-8")
    except Exception as exc:  # pylint: disable=broad-except
        raise ScriptError("Could not decode the Bitbucket token from {}.".format(AUTH_FILE)) from exc


def enumerate_commit(
    bitbucket_base_url: str,
    bitbucket_token: str,
    project: str,
    repository: str,
    commit: str,
    branch_name: str,
    force: bool,
) -> dict[str, object]:
    query = urlencode(
        {
            "branch": branch_name,
            "force": "true" if force else "false",
            "toBranch": "null",
        }
    )
    url = (
        "{}/rest/ci/1.0/base/projects/{}/repos/{}/enumerate/{}?{}".format(
            bitbucket_base_url.rstrip("/"),
            project,
            repository,
            quote(commit, safe=""),
            query,
        )
    )
    debug("Enumerate {}".format(url))
    return json.loads(read_text_from_url(url, headers={"Authorization": "Bearer {}".format(bitbucket_token)}))


def get_remote_branch_head(repo_dir: str | Path, branch_name: str) -> str | None:
    output = run_command(["git", "ls-remote", "--heads", "origin", branch_name], cwd=repo_dir).strip()
    if not output:
        return None
    return output.split()[0]


def wait_for_enumeration(
    repo_dir: str | Path,
    project: str,
    repository: str,
    branch_name: str,
    commit: str,
    required_job: str,
) -> None:
    bitbucket_base_url = get_bitbucket_base_url(repo_dir)
    bitbucket_token = get_bitbucket_token(bitbucket_base_url)
    deadline = time.monotonic() + ENUMERATION_TIMEOUT_SECONDS
    last_status: tuple[object, ...] | None = None
    while time.monotonic() < deadline:
        data = enumerate_commit(bitbucket_base_url, bitbucket_token, project, repository, commit, branch_name, False)
        enumeration = data.get("enumeration") or []
        status = (
            data.get("finished"),
            data.get("successful"),
            data.get("buildnumber"),
            data.get("url"),
            len(enumeration),
        )
        if status != last_status:
            debug(
                "Enumeration state: finished={} successful={} buildnumber={} url={} jobs={}".format(
                    data.get("finished"),
                    data.get("successful"),
                    data.get("buildnumber"),
                    data.get("url"),
                    len(enumeration),
                )
            )
            last_status = status
        if any(job.get("name") == required_job for job in enumeration):
            return
        time.sleep(ENUMERATION_POLL_SECONDS)
    raise ScriptError(
        "Commit {} was not enumerated with '{}' within {} seconds.".format(
            commit, required_job, ENUMERATION_TIMEOUT_SECONDS
        )
    )


def submit_bisect_job(repo_dir: str | Path, branch_name: str, commit: str) -> None:
    debug("Submitting {} on {} ({})".format(BISECT_JOB_NAME, branch_name, commit))
    run_command(
        [
            "gdev-cli",
            "bitbucket",
            "run-gates",
            "-p",
            DEFAULT_PROJECT,
            "-r",
            DEFAULT_REPOSITORY,
            "-b",
            branch_name,
            "-c",
            commit,
            "-rf",
            JOB_SUBMISSION_REGEX,
        ],
        cwd=repo_dir,
    )


def is_terminal_state(state: str) -> bool:
    return state not in {"INPROGRESS", "PENDING", "QUEUED", "SCHEDULED"}


def wait_for_bisect_build(
    repo_dir: str | Path,
    commit: str,
    previous_marker: tuple[int, int, int] | None = None,
) -> BuildRecord:
    deadline = time.monotonic() + BUILD_TIMEOUT_SECONDS
    printed_url = False
    last_status: tuple[object, ...] | None = None
    while time.monotonic() < deadline:
        builds = get_matching_builds(repo_dir, commit, BISECT_JOB_NAME)
        if previous_marker is not None:
            builds = [build for build in builds if build.sort_key > previous_marker]
        build = pick_newest_build(builds)
        if build is not None:
            status = (build.state, build.url, build.build_number, build.request_id)
            if status != last_status:
                debug(
                    "Bisect build state: state={} build_number={} request_id={} url={}".format(
                        build.state,
                        build.build_number,
                        build.request_id,
                        build.url,
                    )
                )
                last_status = status
            if build.build_number is not None and not printed_url:
                print(build.url, flush=True)
                printed_url = True
            if build.build_number is not None and is_terminal_state(build.state):
                return build
        time.sleep(BUILD_POLL_SECONDS)
    raise ScriptError("Timed out waiting for '{}' on commit {}.".format(BISECT_JOB_NAME, commit))


def write_temp_branch(repo_dir: Path, branch_name: str, config_text: str) -> str:
    origin_url = run_command(["git", "remote", "get-url", "origin"], cwd=repo_dir).strip()
    base_commit = resolve_commit(repo_dir, "HEAD")
    commit_message = "Add bisect benchmark config"
    debug("Creating branch {} from {}".format(branch_name, base_commit))
    with tempfile.TemporaryDirectory(prefix="bisect-benchmark-") as temp_root:
        clone_dir = Path(temp_root) / "repo"
        run_command(["git", "clone", str(repo_dir), str(clone_dir)], cwd=repo_dir)
        run_command(["git", "remote", "set-url", "origin", origin_url], cwd=clone_dir)
        for key in ("user.name", "user.email"):
            try:
                value = run_command(["git", "config", "--get", key], cwd=repo_dir).strip()
            except ScriptError:
                continue
            if value:
                run_command(["git", "config", key, value], cwd=clone_dir)
        run_command(["git", "checkout", "-b", branch_name, base_commit], cwd=clone_dir)
        (clone_dir / CONFIG_FILENAME).write_text(config_text, encoding="utf-8")
        for relative_path in BRANCH_SUPPORT_FILES:
            source_path = repo_dir / relative_path
            destination_path = clone_dir / relative_path
            destination_path.parent.mkdir(parents=True, exist_ok=True)
            destination_path.write_text(source_path.read_text(encoding="utf-8"), encoding="utf-8")
        run_command(["git", "add", CONFIG_FILENAME], cwd=clone_dir)
        run_command(["git", "add", *[str(path) for path in BRANCH_SUPPORT_FILES]], cwd=clone_dir)
        run_command(["git", "commit", "-m", commit_message], cwd=clone_dir)
        commit = resolve_commit(clone_dir, "HEAD")
        run_command(["git", "push", "origin", "HEAD:refs/heads/{}".format(branch_name)], cwd=clone_dir)
        debug("Pushed branch {} at {}".format(branch_name, commit))
        return commit


def render_bisect_results(build: BuildRecord) -> str:
    data = json.loads(fetch_uploaded_log_text(build.url, RESULT_FILENAME))
    summary = data.get("summary") or ""
    visualization = data.get("visualization") or ""
    if summary and visualization:
        return "{}\n\n{}".format(summary, visualization)
    if summary:
        return summary
    if visualization:
        return visualization
    return json.dumps(data, indent=2, sort_keys=True)


def generate_config(
    repo_dir: Path,
    benchmark_job_name: str,
    benchmark_name: str,
    metric: str,
    good_commit: str,
    bad_commit: str,
) -> str:
    reference_build = get_reference_build(repo_dir, benchmark_job_name, bad_commit, good_commit)
    debug("Using reference build {} ({})".format(reference_build.build_number, reference_build.url))
    build_log = run_command(["gdev-cli", "buildbot", "get-log", str(reference_build.build_number)], cwd=repo_dir)
    build_command, benchmark_command = extract_commands(build_log, benchmark_name)
    results_benchmark_name = resolve_results_benchmark_name(reference_build.url, benchmark_name, metric)
    enterprise = "enterprise" in build_command
    return build_config_text(
        build_command=build_command,
        benchmark_command=benchmark_command,
        good_commit=good_commit,
        bad_commit=bad_commit,
        metric=metric,
        enterprise=enterprise,
        benchmark_name=results_benchmark_name,
    )


def main() -> int:
    args = parse_args()
    global DEBUG
    DEBUG = args.debug
    repo_dir = get_repo_root(args.repo_dir)
    debug("Repo root: {}".format(repo_dir))
    good_commit = resolve_commit(repo_dir, args.good_commit)
    bad_commit = resolve_commit(repo_dir, args.bad_commit)
    debug("Resolved good={} bad={}".format(good_commit, bad_commit))
    config_text = generate_config(
        repo_dir=repo_dir,
        benchmark_job_name=args.benchmark_job_name,
        benchmark_name=args.benchmark_name,
        metric=args.metric,
        good_commit=good_commit,
        bad_commit=bad_commit,
    )

    if args.config_only:
        print(config_text, end="")
        return 0

    branch_name = build_branch_name(
        args.benchmark_job_name,
        args.benchmark_name,
        args.metric,
        good_commit,
        bad_commit,
    )
    debug("Branch name: {}".format(branch_name))

    branch_head = get_remote_branch_head(repo_dir, branch_name)
    if branch_head is None:
        branch_head = write_temp_branch(repo_dir, branch_name, config_text)
        wait_for_enumeration(
            repo_dir,
            DEFAULT_PROJECT,
            DEFAULT_REPOSITORY,
            branch_name,
            branch_head,
            BISECT_JOB_NAME,
        )
        submit_bisect_job(repo_dir, branch_name, branch_head)
        build = wait_for_bisect_build(repo_dir, branch_head)
    else:
        debug("Remote branch head: {}".format(branch_head))
        existing_builds = get_matching_builds(repo_dir, branch_head, BISECT_JOB_NAME)
        if existing_builds and not args.force_rebuild:
            build = wait_for_bisect_build(repo_dir, branch_head)
        else:
            wait_for_enumeration(
                repo_dir,
                DEFAULT_PROJECT,
                DEFAULT_REPOSITORY,
                branch_name,
                branch_head,
                BISECT_JOB_NAME,
            )
            previous_build = pick_newest_build(existing_builds)
            previous_marker = previous_build.sort_key if previous_build is not None else None
            submit_bisect_job(repo_dir, branch_name, branch_head)
            build = wait_for_bisect_build(repo_dir, branch_head, previous_marker=previous_marker)

    if build.state != "SUCCESSFUL":
        print("Job failed.")
        return 1

    print(render_bisect_results(build))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
