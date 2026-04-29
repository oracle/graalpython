#!/usr/bin/python
#
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

import json
import os
import sys
import urllib.error
import urllib.parse
import urllib.request
from collections.abc import Iterable
from dataclasses import dataclass
from datetime import UTC, datetime
from typing import Any

API_PATH = "/api/periodic-jobs/GraalVM"
REPO = "graalpython"
BRANCH = "master"
TARGETS = ["post-merge", "daily", "weekly", "monthly"]


@dataclass(frozen=True)
class FailureRow:
    target: str
    job_name: str
    last_successful_run: str
    jira_ids: str
    log_url: str


class DashboardError(RuntimeError):
    pass


def get_dashboard_token() -> str:
    token = os.environ.get("OTDASHBOARD_TOKEN")
    if token:
        return token
    raise DashboardError("OTDASHBOARD_TOKEN environment variable is not set.")


def get_api_base() -> str:
    dashboard_url = os.environ.get("OTDASHBOARD_URL")
    if dashboard_url:
        return f"{dashboard_url.rstrip('/')}{API_PATH}"
    raise DashboardError("OTDASHBOARD_URL environment variable is not set.")


def fetch_json(method: str, url: str, token: str, payload: dict[str, Any] | None = None) -> Any:
    data = None
    headers = {
        "Accept": "application/json",
        "Authorization": f"Bearer {token}",
    }
    if payload is not None:
        data = json.dumps(payload).encode("utf-8")
        headers["Content-Type"] = "application/json"
    request = urllib.request.Request(url, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(request, timeout=30) as response:
            return json.load(response)
    except urllib.error.HTTPError as exc:
        body = exc.read().decode("utf-8", "replace").strip()
        message = body or exc.reason
        raise DashboardError(f"Dashboard API request failed with HTTP {exc.code}: {message}") from exc
    except urllib.error.URLError as exc:
        raise DashboardError(f"Could not reach dashboard API: {exc.reason}") from exc
    except json.JSONDecodeError as exc:
        raise DashboardError(f"Dashboard API returned invalid JSON for {url}") from exc


def get_latest_runs(api_base: str, token: str) -> dict[str, dict[str, Any]]:
    payload = {
        "repo": REPO,
        "branch": BRANCH,
        "targets": TARGETS,
    }
    result = fetch_json("POST", f"{api_base}/latest", token, payload)
    if not isinstance(result, dict):
        raise DashboardError("Dashboard API returned an unexpected payload for the latest runs.")

    latest_runs: dict[str, dict[str, Any]] = {}
    for target in TARGETS:
        runs = result.get(target)
        if isinstance(runs, list) and runs and isinstance(runs[0], dict):
            latest_runs[target] = runs[0]
    return latest_runs


def get_failed_jobs(api_base: str, token: str, run_id: str, target: str) -> list[dict[str, Any]]:
    params = urllib.parse.urlencode({
        "id": run_id,
        "status": "failed",
        "target": target,
    })
    url = f"{api_base}/jobs?{params}"
    result = fetch_json("GET", url, token)
    if not isinstance(result, list):
        raise DashboardError(f"Dashboard API returned an unexpected payload for failed jobs in target {target}.")
    return [job for job in result if isinstance(job, dict)]


def format_timestamp_ms(timestamp_ms: Any) -> str:
    if not isinstance(timestamp_ms, int | float):
        return "-"
    return datetime.fromtimestamp(timestamp_ms / 1000, tz=UTC).strftime("%Y-%m-%d %H:%M:%S UTC")


def format_jira_ids(tickets: Any) -> str:
    if not isinstance(tickets, Iterable) or isinstance(tickets, str | bytes):
        return "-"
    ticket_ids: list[str] = []
    for ticket in tickets:
        if isinstance(ticket, dict):
            ticket_id = ticket.get("ticketId")
            if isinstance(ticket_id, str) and ticket_id not in ticket_ids:
                ticket_ids.append(ticket_id)
    return ", ".join(ticket_ids) if ticket_ids else "-"


def get_nested_timestamp(job: dict[str, Any]) -> Any:
    last_successful = job.get("lastSuccessful")
    if isinstance(last_successful, dict):
        return last_successful.get("run")
    return None


def get_string(value: Any) -> str:
    return value if isinstance(value, str) and value else "-"


def build_rows(api_base: str, token: str) -> list[FailureRow]:
    rows: list[FailureRow] = []
    latest_runs = get_latest_runs(api_base, token)
    for target in TARGETS:
        latest_run = latest_runs.get(target)
        if not latest_run:
            continue
        failed_count = latest_run.get("failed", 0)
        run_id = latest_run.get("id")
        if not isinstance(failed_count, int) or failed_count <= 0 or not isinstance(run_id, str):
            continue

        for job in get_failed_jobs(api_base, token, run_id, target):
            rows.append(FailureRow(
                target=target,
                job_name=get_string(job.get("jobName")),
                last_successful_run=format_timestamp_ms(get_nested_timestamp(job)),
                jira_ids=format_jira_ids(job.get("tickets")),
                log_url=get_string(job.get("url")),
            ))

    return rows


def print_table(rows: list[FailureRow]) -> None:
    if not rows:
        print("No failed jobs found in the latest periodic CI runs.")
        return

    headers = ("target", "job name", "last successful run", "jira ID(s)", "log URL")
    values = [headers]
    values.extend((row.target, row.job_name, row.last_successful_run, row.jira_ids, row.log_url) for row in rows)
    widths = [max(len(str(row[column])) for row in values) for column in range(len(headers))]

    def render(columns: tuple[str, ...]) -> str:
        return " | ".join(value.ljust(widths[index]) for index, value in enumerate(columns))

    separator = "-+-".join("-" * width for width in widths)
    print(render(headers))
    print(separator)
    for row in rows:
        print(render((row.target, row.job_name, row.last_successful_run, row.jira_ids, row.log_url)))


def main() -> int:
    try:
        api_base = get_api_base()
        token = get_dashboard_token()
        rows = build_rows(api_base, token)
        print_table(rows)
    except DashboardError as exc:
        print(f"error: {exc}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
