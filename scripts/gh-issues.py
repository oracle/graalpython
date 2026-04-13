import argparse
import asyncio
import json
import os
import re
import subprocess
import urllib.parse
import urllib.request

from openai_codex_sdk import Codex, Thread
from openai_codex_sdk.errors import ThreadRunError
from termcolor import cprint

REPO = "oracle/graalpython"
WORKING_DIRECTORY = "/home/vcalvez/graalpython"
CODEX = Codex()
MAX_ISSUE_TEXT_CHARS = 700
CODEX_BATCH_SIZE = 1
CODEX_STDIO_READ_LIMIT = 1024 * 1024


def _log_info(message: str) -> None:
    cprint(message, "cyan")


def _log_success(message: str) -> None:
    cprint(message, "green")


def _patch_codex_stdio_limit(limit: int = CODEX_STDIO_READ_LIMIT) -> None:
    """Raise asyncio subprocess stream limit used by openai_codex_sdk.

    This avoids ValueError("Separator is found, but chunk is longer than limit")
    when Codex emits a very large JSON line in experimental-json mode.
    """
    create_subprocess_exec = asyncio.create_subprocess_exec
    if getattr(create_subprocess_exec, "_gh_issues_limit_patched", False):
        return

    async def _create_subprocess_exec_with_limit(*args, **kwargs):
        kwargs.setdefault("limit", limit)
        return await create_subprocess_exec(*args, **kwargs)

    setattr(_create_subprocess_exec_with_limit, "_gh_issues_limit_patched", True)
    asyncio.create_subprocess_exec = _create_subprocess_exec_with_limit


_patch_codex_stdio_limit()


def _trim_text(value: str, max_chars: int = MAX_ISSUE_TEXT_CHARS) -> str:
    if len(value) <= max_chars:
        return value
    return value[:max_chars] + "\n...[truncated]"


def _prepare_issues_for_codex(issues: list[dict]) -> list[dict]:
    prepared: list[dict] = []
    for issue in issues:
        prepared.append(
            {
                "issue_id": issue.get("issue_id"),
                "title": _trim_text(str(issue.get("title", "")), max_chars=220),
                "author": _trim_text(str(issue.get("author", "")), max_chars=80),
                "labels": issue.get("labels", []),
                "description": _trim_text(str(issue.get("description", ""))),
            }
        )
    return prepared


def _chunks(items: list[dict], size: int) -> list[list[dict]]:
    return [items[i:i + size] for i in range(0, len(items), size)]


def _extract_json_payload(text: str) -> str:
    stripped = text.strip()
    if stripped.startswith("```"):
        match = re.search(r"```(?:json)?\s*(.*?)\s*```", stripped, re.DOTALL)
        if match:
            return match.group(1)
    return stripped


def _candidate_files_for_issue(issue: dict, max_files: int = 5) -> list[str]:
    terms = re.findall(r"[A-Za-z_][A-Za-z0-9_]{3,}", str(issue.get("title", "")))
    terms.extend(str(x) for x in issue.get("labels", []))
    terms = [t.lower() for t in terms if t.lower() not in {"issue", "error", "python", "graalpy", "graalpython"}]
    terms = terms[:4]
    if not terms:
        return []

    pattern = "|".join(re.escape(t) for t in terms)
    cmd = [
        "grep",
        "-RIlE",
        "--exclude-dir=.git",
        "--exclude-dir=venv",
        "--exclude-dir=build",
        "--exclude-dir=dist",
        "--exclude-dir=__pycache__",
        "--binary-files=without-match",
        pattern,
        WORKING_DIRECTORY,
    ]
    try:
        result = subprocess.run(cmd, capture_output=True, text=True, timeout=2, check=False)
    except (OSError, subprocess.TimeoutExpired):
        return []

    files = []
    for line in result.stdout.splitlines():
        rel = line.replace(f"{WORKING_DIRECTORY}/", "")
        files.append(rel)
        if len(files) >= max_files:
            break
    return files


async def _codex_prompt_async(prompt: str, thread: Thread) -> tuple[str, dict[str, int]]:
    try:
        turn = await thread.run(prompt)
    except ThreadRunError as exc:
        raise SystemExit(
            "Codex access test failed. Ensure the token from "
            "~/.codex/config.toml provider OCA_ACCESS_TOKEN is exported\n"
            f"Details: {exc}"
        ) from exc
    usage = turn.usage
    usage_dict = {
        "input_tokens": usage.input_tokens if usage else 0,
        "cached_input_tokens": usage.cached_input_tokens if usage else 0,
        "output_tokens": usage.output_tokens if usage else 0,
    }
    return turn.final_response, usage_dict


def codex_sort_issues(
    issues: list[dict],
    workers: int = 1,
    print_token_usage: bool = False,
    max_files_to_read: int = 5,
    short_output: bool = False,
) -> str:
    _log_info("Sorting issues with Codex...")
    compact_issues = _prepare_issues_for_codex(issues)
    by_id = {issue["issue_id"]: issue for issue in compact_issues}
    easy_ai_fix: list[dict] = []
    non_relevant: list[dict] = []
    
    batches = _chunks(compact_issues, CODEX_BATCH_SIZE)

    async def _classify_batch(
        batch: list[dict], index: int, total: int, sem: asyncio.Semaphore
    ) -> tuple[list[dict], dict[str, int]]:
        async with sem:
            thread_options: dict[str, object] = {
                "approvalPolicy": "never",
                "sandboxMode": "read-only",
                "webSearchEnabled": False,
                "networkAccessEnabled": False,
                "workingDirectory": WORKING_DIRECTORY,
            }
            thread = CODEX.start_thread(thread_options)

            codebase_instruction = (
                "Read only the local files needed for this issue. "
                f"Read at most {max_files_to_read} files, and skim only minimal relevant sections."
            )
            candidate_files = _candidate_files_for_issue(batch[0], max_files=5)
            
            prompt = (
                "Classify each issue into one of: easy-ai-fix, non-relevant, ignore. "
                "Use short reasoning (<=120 chars). "
                "Assign no longer relevant only if the issue is already fixed. "
                "Do not assign easy-ai-fix if the issue appears already solved. "
                "Return JSON array only with entries (do not include ignored ones): "
                "{\"issue_id\": number, \"category\": \"easy-ai-fix|non-relevant\", \"title\": string, \"author\": string, \"reason\": string}. "
                f"No markdown, no extra text. {codebase_instruction}\n\n"
                f"candidate_files (optional, prefer these first): {json.dumps(candidate_files)}\n\n"
                f"issues_json:\n{json.dumps(batch, ensure_ascii=True, indent=2)}"
            )
            response, usage = await _codex_prompt_async(prompt, thread)
            _log_success(f"Processed batch {index}/{total}")
            _log_info(f"Batch {index} response: {response.strip()[:200]}{'...' if len(response.strip()) > 200 else ''}")
            _log_info(f"Batch {index} token usage: input={usage['input_tokens']}, cached_input={usage['cached_input_tokens']}, output={usage['output_tokens']}")
            return json.loads(_extract_json_payload(response)), usage

    async def _run_all() -> list[tuple[list[dict], dict[str, int]]]:
        sem = asyncio.Semaphore(max(1, workers))
        tasks = [
            asyncio.create_task(_classify_batch(batch, i, len(batches), sem))
            for i, batch in enumerate(batches, start=1)
        ]
        return await asyncio.gather(*tasks)

    parsed_batches = asyncio.run(_run_all())
    token_usage = {
        "input_tokens": 0,
        "cached_input_tokens": 0,
        "output_tokens": 0,
    }

    for parsed, usage in parsed_batches:
        token_usage["input_tokens"] += usage["input_tokens"]
        token_usage["cached_input_tokens"] += usage["cached_input_tokens"]
        token_usage["output_tokens"] += usage["output_tokens"]
        for item in parsed:
            issue_id = item.get("issue_id")
            category = item.get("category")
            if issue_id not in by_id or category not in {"easy-ai-fix", "non-relevant"}:
                continue
            enriched = {"issue_id": issue_id} if short_output else {
                "issue_id": issue_id,
                "title": by_id[issue_id]["title"],
                "author": by_id[issue_id]["author"],
                "reason": _trim_text(str(item.get("reason", "")), max_chars=160),
            }
            if category == "easy-ai-fix":
                if not any(existing["issue_id"] == issue_id for existing in easy_ai_fix):
                    easy_ai_fix.append(enriched)
            else:
                if not any(existing["issue_id"] == issue_id for existing in non_relevant):
                    non_relevant.append(enriched)

    if print_token_usage:
        total = (
            token_usage["input_tokens"]
            + token_usage["cached_input_tokens"]
            + token_usage["output_tokens"]
        )
        _log_info(
            "Token usage: "
            f"input={token_usage['input_tokens']}, "
            f"cached_input={token_usage['cached_input_tokens']}, "
            f"output={token_usage['output_tokens']}, "
            f"total={total}"
        )

    return json.dumps(
        {
            "non-relevant": non_relevant,
            "easy-ai-fix": easy_ai_fix,
        },
        ensure_ascii=True,
        indent=2,
    )


async def codex_fix_issue(issue_id: int) -> str:
    thread = CODEX.start_thread({"workingDirectory": WORKING_DIRECTORY})
    prompt = (
        f"Attempt to fix the issue #{issue_id} in the codebase. "
        "Read only the local files needed for this issue. "
        "Skim only minimal relevant sections. "
        "Make relevant tests and iterate fix."
    )

    response, _ = await _codex_prompt_async(prompt, thread)
    return response


def build_github_request(url: str, query_params: dict[str, str], token: str | None = None) -> urllib.request.Request:
    headers = {
        "Accept": "application/vnd.github+json",
        "X-GitHub-Api-Version": "2022-11-28",
    }
    if token:
        headers["Authorization"] = f"Bearer {token}"
    full_url = f"{url}?{urllib.parse.urlencode(query_params)}"
    return urllib.request.Request(full_url, headers=headers, method="GET")


def get_issues(limit: int = 30, label: str | None = None) -> str:
    issues = []
    page = 1
    while len(issues) < limit:
        per_page = min(100, max(1, limit - len(issues)))
        query_params = {
            "state": "open",
            "per_page": per_page,
            "page": page,
        }
        if label:
            query_params["labels"] = label
        url = f"https://api.github.com/repos/{REPO}/issues"
        req = build_github_request(url, query_params, token=os.getenv("GITHUB_TOKEN"))
        with urllib.request.urlopen(req) as resp:
            raw_issues = json.loads(resp.read())

        if not raw_issues:
            break

        for item in raw_issues:
            if "pull_request" in item:
                continue
            issues.append(
                {
                    "issue_id": item["number"],
                    "title": item.get("title", ""),
                    "description": item.get("body") or "",
                    "date": item.get("created_at", ""),
                    "author": item.get("user", {}).get("login", ""),
                    "labels": [label.get("name", "") for label in item.get("labels", [])],
                }
            )
            if len(issues) >= limit:
                break
        page += 1

    return json.dumps(issues, ensure_ascii=True)


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--codex-workers", type=int, default=1, help="Parallel Codex workers")
    parser.add_argument("--print-token-usage", action="store_true", help="Print Codex token usage")

    subparsers = parser.add_subparsers(dest="command", required=True)
    issues_parser = subparsers.add_parser("get-issues", help="Fetch and sort GitHub issues")
    issues_parser.add_argument("--limit", type=int, default=30, help="Maximum number of issues to fetch")
    issues_parser.add_argument("--label", type=str, help="Filter issues by label")
    issues_parser.add_argument("--codex-max-files", type=int, default=5, help="Max local files Codex should read per issue")
    issues_parser.add_argument("--short-output", action="store_true", help="Output only issue IDs in each category")
    issues_parser.add_argument("--json-output", action="store_true", help="Output only JSON result")

    fix_parser = subparsers.add_parser("fix-issue", help="Attempt to fix an issue with Codex")
    fix_parser.add_argument("--issue-id", type=int, required=True, help="ID of the issue to fix")

    args = parser.parse_args()

    if args.command == "get-issues":
        issues = get_issues(limit=args.limit, label=args.label)
        _log_info(f"Fetched {len(json.loads(issues))} issues. Now sorting with Codex...")
        sorted_issues = codex_sort_issues(
            json.loads(issues),
            workers=args.codex_workers,
            print_token_usage=args.print_token_usage,
            max_files_to_read=max(0, args.codex_max_files),
            short_output=args.short_output,
        )
        if args.json_output:
            print(sorted_issues)
        else:
            sorted_issues_data = json.loads(sorted_issues)
            _log_success("Non-relevant issues:")
            non_relevant = sorted_issues_data.get("non-relevant", [])
            for issue in non_relevant:
                _log_info(f"- #{issue['issue_id']}: {issue['title']} (reason: {issue['reason']})")

            _log_success("\nEasy AI fix issues:")
            easy_ai_fix = sorted_issues_data.get("easy-ai-fix", [])
            for issue in easy_ai_fix:
                _log_info(f"- #{issue['issue_id']}: {issue['title']} (reason: {issue['reason']})")

    elif args.command == "fix-issue":
        issue_id = args.issue_id
        _log_info(f"Attempting to fix issue #{issue_id} with Codex...")
        fix = asyncio.run(codex_fix_issue(issue_id))
        print(json.dumps(fix, ensure_ascii=True, indent=2))

if __name__ == "__main__":
    main()
