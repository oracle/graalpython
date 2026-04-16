import argparse
import asyncio
import json
import os
import re
import subprocess
import sys
import urllib.parse
import urllib.request

from openai_codex_sdk import Codex, Thread
from openai_codex_sdk.errors import ThreadRunError
from openai_codex_sdk import parsing as codex_parsing
from openai_codex_sdk.types import UnknownThreadItem
from termcolor import cprint, colored

REPO = "oracle/graalpython"
PROJECT_DIRECTORY = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
GHAPI = "https://api.github.com"
CODEX = Codex()
CODEX_MODEL = "gpt-5.4"
MAX_ISSUE_TEXT_CHARS = 700
CODEX_BATCH_SIZE = 1
CODEX_STDIO_READ_LIMIT = 1024 * 1024
ISSUE_CATEGORIES = {"easy-ai-fix", "non-relevant"}


def _log_info(message: str) -> None:
    cprint(message, "yellow")


def _log_success(message: str) -> None:
    cprint(message, "green", attrs=["bold"])
    
def _log_secondary(message: str) -> None:
    cprint(message, "dark_grey")


def _fmt_tag(label: str, value: str | int | None, *, label_color: str = "cyan", value_color: str = "white") -> str:
    return f"{colored(f'{label}=', label_color, attrs=['bold'])}{colored(str(value), value_color)}"


def _fmt_prefix_tag(prefix: str, tag: str, text: str) -> str:
    return " ".join(
        [
            colored(f"[{prefix}]", "blue", attrs=["bold"]),
            colored(f"[{tag}]", "magenta", attrs=["bold"]),
            colored(text, "light_grey"),
        ]
    )


def _token_usage_dict(usage) -> dict[str, int]:
    if usage is None:
        return {"input_tokens": 0, "cached_input_tokens": 0, "output_tokens": 0}
    return {
        "input_tokens": usage.input_tokens,
        "cached_input_tokens": usage.cached_input_tokens,
        "output_tokens": usage.output_tokens,
    }


def _token_usage_total(token_usage: dict[str, int]) -> int:
    return token_usage["input_tokens"] + token_usage["cached_input_tokens"] + token_usage["output_tokens"]


def _accumulate_token_usage(total: dict[str, int], usage: dict[str, int]) -> None:
    total["input_tokens"] += usage["input_tokens"]
    total["cached_input_tokens"] += usage["cached_input_tokens"]
    total["output_tokens"] += usage["output_tokens"]


def _raise_codex_access_error(exc: Exception) -> None:
    raise SystemExit(
        "Codex access test failed. Ensure the token from "
        "~/.codex/config.toml provider OCA_ACCESS_TOKEN is exported\n"
        f"Details: {exc}"
    ) from exc


def _new_codex_thread(*, sandbox_mode: str, web_search: bool, network_access: bool) -> Thread:
    return CODEX.start_thread(
        {
            "model": CODEX_MODEL,
            "approvalPolicy": "never",
            "sandboxMode": sandbox_mode,
            "webSearchEnabled": web_search,
            "networkAccessEnabled": network_access,
            "workingDirectory": PROJECT_DIRECTORY,
        }
    )


def print_usage(token_usage: dict, total: int) -> None:
    print(
        " ".join(
            [
                colored("Token usage:", "yellow", attrs=["bold"]),
                _fmt_tag("input", token_usage["input_tokens"], label_color="cyan"),
                _fmt_tag("cached", token_usage["cached_input_tokens"], label_color="cyan"),
                _fmt_tag("output", token_usage["output_tokens"], label_color="cyan"),
                _fmt_tag("total", total, label_color="green", value_color="green"),
            ]
        )
    )

### Ayncio subprocess limit patching and codex issue preparation
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


def _patch_codex_parse_file_change_in_progress() -> None:
    """Work around SDK validation strictness for in-progress file_change events.

    Some SDK versions model file_change status as completed|failed, while streamed
    events may emit in_progress. When that happens, parsing crashes the whole run.
    We fall back to UnknownThreadItem for any item payload that fails strict parsing.
    """
    original_parse_thread_item = codex_parsing.parse_thread_item
    if getattr(original_parse_thread_item, "_gh_issues_file_change_patch", False):
        return

    def _safe_parse_thread_item(data):
        try:
            return original_parse_thread_item(data)
        except Exception:
            if isinstance(data, dict) and isinstance(data.get("type"), str):
                return UnknownThreadItem.model_validate(data)
            raise

    setattr(_safe_parse_thread_item, "_gh_issues_file_change_patch", True)
    codex_parsing.parse_thread_item = _safe_parse_thread_item


_patch_codex_parse_file_change_in_progress()


def _trim_text(value: str, max_chars: int = MAX_ISSUE_TEXT_CHARS) -> str:
    if len(value) <= max_chars:
        return value
    return value[:max_chars] + "\n...[truncated]"


def _prepare_issues_for_codex(issues: list[dict]) -> list[dict]:
    return [
        {
            "issue_id": issue.get("issue_id"),
            "title": _trim_text(str(issue.get("title", "")), max_chars=220),
            "author": _trim_text(str(issue.get("author", "")), max_chars=80),
            "labels": issue.get("labels", []),
            "description": _trim_text(str(issue.get("description", ""))),
        }
        for issue in issues
    ]

### Batching
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
        PROJECT_DIRECTORY,
    ]
    try:
        result = subprocess.run(cmd, capture_output=True, text=True, timeout=2, check=False)
    except (OSError, subprocess.TimeoutExpired):
        return []

    return [line.replace(f"{PROJECT_DIRECTORY}/", "") for line in result.stdout.splitlines()[:max_files]]


async def _codex_prompt_async(prompt: str, thread: Thread, stream_live: bool = False, prefix: str = "") -> tuple[str, dict[str, int]]:
    if not stream_live:
        try:
            turn = await thread.run(prompt)
        except ThreadRunError as exc:
            _raise_codex_access_error(exc)
        return turn.final_response, _token_usage_dict(turn.usage)

    try:
        streamed = await thread.run_streamed(prompt)
    except ThreadRunError as exc:
        _raise_codex_access_error(exc)

    final_response = ""
    usage_dict = _token_usage_dict(None)
    failure_message = None
    seen_reasoning: dict[str, str] = {}

    async for event in streamed.events:
        event_type = getattr(event, "type", "")
        if event_type == "turn.completed":
            usage_dict = _token_usage_dict(getattr(event, "usage", None))
            continue

        if event_type == "turn.failed":
            failure_message = getattr(getattr(event, "error", None), "message", "Unknown turn failure")
            break

        if event_type not in {"item.started", "item.updated", "item.completed"}:
            continue

        item = getattr(event, "item", None)
        if item is None:
            continue

        item_type = getattr(item, "type", "")
        if item_type == "agent_message" and event_type == "item.completed":
            final_response = getattr(item, "text", "")
            continue

        if item_type == "reasoning":
            reasoning_id = getattr(item, "id", "")
            text = str(getattr(item, "text", "")).strip()
            if text and seen_reasoning.get(reasoning_id) != text:
                seen_reasoning[reasoning_id] = text
                _log_secondary(_fmt_prefix_tag(prefix, "thinking", text))
            continue

        if item_type == "command_execution":
            command = str(getattr(item, "command", "")).strip()
            if command and event_type == "item.started":
                _log_secondary(_fmt_prefix_tag(prefix, "command", command))
            continue

        if item_type == "file_change" and event_type == "item.completed":
            changes = list(getattr(item, "changes", []) or [])
            if not changes:
                _log_success(_fmt_prefix_tag(prefix, "file_change", "Applied changes."))
                continue
            sample = ", ".join(str(getattr(change, "path", "?")) for change in changes[:6])
            suffix = f" (+{len(changes) - 6} more)" if len(changes) > 6 else ""
            _log_success(_fmt_prefix_tag(prefix, "file_change", f"{sample}{suffix}"))

    if failure_message is not None:
        raise ThreadRunError(failure_message)

    return final_response, usage_dict


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
    categorized: dict[str, list[dict]] = {"easy-ai-fix": [], "non-relevant": []}
    
    batches = _chunks(compact_issues, CODEX_BATCH_SIZE)

    async def _classify_batch(
        batch: list[dict], index: int, total: int, sem: asyncio.Semaphore
    ) -> tuple[list[dict], dict[str, int]]:
        async with sem:
            thread = _new_codex_thread(sandbox_mode="read-only", web_search=False, network_access=False)

            codebase_instruction = (
                "Read only the local files needed for this issue. "
                f"Read at most {max_files_to_read} files, and skim only minimal relevant sections."
            )
            candidate_files = _candidate_files_for_issue(batch[0], max_files=max_files_to_read)
            
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
            _log_secondary(f"Codex response for batch {index}:\n{response}")
            return json.loads(_extract_json_payload(response)), usage

    async def _run_all() -> list[tuple[list[dict], dict[str, int]]]:
        sem = asyncio.Semaphore(max(1, workers))
        tasks = [
            asyncio.create_task(_classify_batch(batch, i, len(batches), sem))
            for i, batch in enumerate(batches, start=1)
        ]
        try:
            return await asyncio.gather(*tasks, return_exceptions=True)
        except (asyncio.CancelledError, KeyboardInterrupt):
            for task in tasks:
                task.cancel()
            await asyncio.gather(*tasks, return_exceptions=True)
            raise

    parsed_batches = asyncio.run(_run_all())
    token_usage = {
        "input_tokens": 0,
        "cached_input_tokens": 0,
        "output_tokens": 0,
    }

    for item in parsed_batches:
        if isinstance(item, Exception):
            _log_info(f"Skipping failed issue classification batch: {item}")
            continue
        parsed, usage = item
        _accumulate_token_usage(token_usage, usage)
        for item in parsed:
            issue_id = item.get("issue_id")
            category = item.get("category")
            if issue_id not in by_id or category not in ISSUE_CATEGORIES:
                continue
            enriched = {"issue_id": issue_id} if short_output else {
                "issue_id": issue_id,
                "title": by_id[issue_id]["title"],
                "author": by_id[issue_id]["author"],
                "reason": _trim_text(str(item.get("reason", "")), max_chars=160),
            }
            if not any(existing["issue_id"] == issue_id for existing in categorized[category]):
                categorized[category].append(enriched)

    if print_token_usage:
        print_usage(token_usage, _token_usage_total(token_usage))
    return json.dumps(
        {
            "non-relevant": categorized["non-relevant"],
            "easy-ai-fix": categorized["easy-ai-fix"],
        },
        ensure_ascii=True,
        indent=2,
    )


def _parse_issue_ids(issue_ids: list[str]) -> list[int]:
    valid_issue_ids: list[int] = []
    for raw_issue_id in issue_ids:
        raw_issue_id = raw_issue_id.strip()
        if not raw_issue_id:
            continue
        try:
            valid_issue_ids.append(int(raw_issue_id))
        except ValueError:
            _log_secondary(f"Invalid issue ID '{raw_issue_id}', skipping.")
    return valid_issue_ids


def codex_fix_issues(issue_ids: list[str], codex_workers: int, print_token_usage: bool = False) -> None:
    valid_issue_ids = _parse_issue_ids(issue_ids)
    if not valid_issue_ids:
        _log_secondary("No valid issue IDs provided.")
        return

    async def _run_all() -> list[tuple[int, str]]:
        sem = asyncio.Semaphore(max(1, codex_workers))
        tasks = [
            asyncio.create_task(codex_fix_issue(issue_id, print_token_usage, sem))
            for issue_id in valid_issue_ids
        ]
        try:
            return await asyncio.gather(*tasks, return_exceptions=True)
        except (asyncio.CancelledError, KeyboardInterrupt):
            for task in tasks:
                task.cancel()
            await asyncio.gather(*tasks, return_exceptions=True)
            raise

    results = asyncio.run(_run_all())
    for result in results:
        if isinstance(result, Exception):
            _log_secondary(f"Skipping failed issue fix task: {result}")
            continue
        issue_id, fix = result
        try:
            payload = json.loads(_extract_json_payload(fix))
            print(json.dumps({"issue_id": issue_id, **payload}, ensure_ascii=True, indent=2))
        except (json.JSONDecodeError, TypeError):
            _log_secondary(f"Issue #{issue_id} response:")
            print(fix)


async def codex_fix_issue(issue_id: int, print_token_usage: bool, sem: asyncio.Semaphore) -> tuple[int, str]:
    async with sem:
        _log_info(f"Attempting to fix issue #{issue_id} with Codex...")
        thread = _new_codex_thread(sandbox_mode="workspace-write", web_search=True, network_access=True)
        prompt = (
            f"Attempt to fix github {REPO} issue #{issue_id} in the local codebase. "
            "Read only the local files needed. "
            "Skim only minimal relevant sections. "
            f"If you need a minimal reproduction harness first, use this command: "
            f"`python scripts/gh-issues.py gen-repro --issues-ids {issue_id}`. "
            "Make changes and run relevant tests. "
            "At the end of your response, return ONLY JSON with this schema: "
            "{\"fixed\": boolean, \"summary\": string}."
        )

        response, usage = await _codex_prompt_async(prompt, thread, stream_live=True, prefix=str(issue_id))
        _log_secondary(f"[{issue_id}] Codex response:")
        print(response)

        try:
            payload = json.loads(_extract_json_payload(response))
            fixed = bool(payload.get("fixed", False))
        except (json.JSONDecodeError, TypeError):
            fixed = False

        if fixed:
            _log_success(f"Codex marked issue #{issue_id} as fixed.")
            
        if print_token_usage:
            _log_info(f"[{issue_id}]")
            print_usage(usage, _token_usage_total(usage))
    return issue_id, response


### Issue categorizing

def _print_issue_results(category: str, issues: dict) -> None:
    _log_success(f"{category.capitalize()} issues:")
    for issue in issues.get(category, []):
        issue_id = issue.get("issue_id", "?")
        title = issue.get("title")
        reason = issue.get("reason")
        if title is None and reason is None:
            _log_info(f"- {colored('#', 'blue', attrs=['bold'])}{colored(str(issue_id), 'blue')}")
        elif reason is None:
            _log_info(
                f"- {colored('#', 'blue', attrs=['bold'])}{colored(str(issue_id), 'blue')}: "
                f"{colored(str(title), 'white')}"
            )
        elif title is None:
            _log_info(
                f"- {colored('#', 'blue', attrs=['bold'])}{colored(str(issue_id), 'blue')} "
                f"({colored('reason', 'magenta', attrs=['bold'])}: {colored(str(reason), 'white')})"
            )
        else:
            _log_info(
                f"- {colored('#', 'blue', attrs=['bold'])}{colored(str(issue_id), 'blue')}: "
                f"{colored(str(title), 'white')} "
                f"({colored('reason', 'magenta', attrs=['bold'])}: {colored(str(reason), 'white')})"
            )


def sort_issues(args: argparse.Namespace) -> str:
    issues_data = json.loads(get_issues(limit=args.limit, label=args.label))
    _log_success(f"Fetched {len(issues_data)} issues.")
    sorted_issues = codex_sort_issues(
        issues_data,
        workers=args.codex_workers,
        print_token_usage=args.print_token_usage,
        max_files_to_read=max(0, args.codex_max_files),
        short_output=args.short_output,
    )
    
    if args.json_output:
        print(sorted_issues)
    else:
        sorted_issues_data = json.loads(sorted_issues)
        _print_issue_results("non-relevant", sorted_issues_data)
        _print_issue_results("easy-ai-fix", sorted_issues_data)
    return sorted_issues

### GitHub API interaction

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
        url = f"{GHAPI}/repos/{REPO}/issues"
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


def get_issue(issue_id: int) -> dict:
    url = f"{GHAPI}/repos/{REPO}/issues/{issue_id}"
    req = build_github_request(url, {}, token=os.getenv("GITHUB_TOKEN"))
    with urllib.request.urlopen(req) as resp:
        item = json.loads(resp.read())
    if "pull_request" in item:
        raise ValueError(f"Issue #{issue_id} is a pull request, not an issue.")
    return {
        "issue_id": item["number"],
        "title": item.get("title", ""),
        "description": item.get("body") or "",
        "date": item.get("created_at", ""),
        "author": item.get("user", {}).get("login", ""),
        "labels": [label.get("name", "") for label in item.get("labels", [])],
    }


def codex_gen_repros(issue_ids: list[str], codex_workers: int, print_token_usage: bool = False) -> None:
    valid_issue_ids = _parse_issue_ids(issue_ids)
    if not valid_issue_ids:
        _log_secondary("No valid issue IDs provided.")
        return

    async def _gen_repro(issue_id: int, sem: asyncio.Semaphore) -> tuple[int, str, dict[str, int]]:
        async with sem:
            _log_info(f"Generating repro for issue #{issue_id}...")
            issue = get_issue(issue_id)
            compact_issue = _prepare_issues_for_codex([issue])[0]
            candidate_files = _candidate_files_for_issue(compact_issue, max_files=8)
            thread = _new_codex_thread(sandbox_mode="read-only", web_search=False, network_access=False)
            prompt = (
                f"Create a minimal local reproduction harness for GitHub issue #{issue_id} in {REPO}. "
                "Read only relevant local files (minimal skim). "
                "Return ONLY JSON with this schema: "
                "{\"issue_id\": number, "
                "\"repro_script\": string}. "
                "Use a runnable Python snippet in repro_script. "
                "Do not include markdown fences and do not include any extra keys.\n\n"
                f"candidate_files (optional, prefer these first): {json.dumps(candidate_files)}\n\n"
                f"issue_json:\n{json.dumps(compact_issue, ensure_ascii=True, indent=2)}"
            )
            response, usage = await _codex_prompt_async(prompt, thread)
            return issue_id, response, usage

    async def _run_all() -> list[tuple[int, str, dict[str, int]]]:
        sem = asyncio.Semaphore(max(1, codex_workers))
        tasks = [asyncio.create_task(_gen_repro(issue_id, sem)) for issue_id in valid_issue_ids]
        try:
            return await asyncio.gather(*tasks, return_exceptions=True)
        except (asyncio.CancelledError, KeyboardInterrupt):
            for task in tasks:
                task.cancel()
            await asyncio.gather(*tasks, return_exceptions=True)
            raise

    results = asyncio.run(_run_all())
    total_usage = _token_usage_dict(None)
    for result in results:
        if isinstance(result, Exception):
            _log_secondary(f"Skipping failed repro generation task: {result}")
            continue
        issue_id, response, usage = result
        _accumulate_token_usage(total_usage, usage)
        try:
            payload = json.loads(_extract_json_payload(response))
            print(
                json.dumps(
                    {
                        "issue_id": payload.get("issue_id", issue_id),
                        "repro_script": payload.get("repro_script", ""),
                    },
                    ensure_ascii=True,
                    indent=2,
                )
            )
        except (json.JSONDecodeError, TypeError):
            _log_secondary(f"Issue #{issue_id} response:")
            print(response)
    if print_token_usage:
        print_usage(total_usage, _token_usage_total(total_usage))


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
    issues_parser.add_argument("--fix-easy-issues", type=bool, nargs="?", const=True, help="Attempt to fix easy-ai-fix issues with Codex")

    fix_issues_parser = subparsers.add_parser("fix-issues", help="Attempt to fix issue(s) with Codex")
    fix_issues_parser.add_argument("--issues-ids", type=str, required=True, help="Comma separated list of issue IDs to attempt fixing")
    fix_issues_parser.add_argument("--max-rounds", type=int, default=8, help="Maximum iterative Codex fix rounds")

    gen_repro_parser = subparsers.add_parser("gen-repro", help="Generate minimal reproduction harness for issue(s)")
    gen_repro_parser.add_argument("--issues-ids", type=str, required=True, help="Comma separated list of issue IDs")

    args = parser.parse_args()
    if args.command == "get-issues":
        issues = json.loads(sort_issues(args))
        if args.fix_easy_issues:
            issues_ids = [str(issue["issue_id"]) for issue in issues.get("easy-ai-fix", [])]
            codex_fix_issues(issues_ids, codex_workers=args.codex_workers, print_token_usage=args.print_token_usage)

    elif args.command == "fix-issues":
        issues_ids = args.issues_ids.split(",")
        codex_fix_issues(issues_ids, codex_workers=args.codex_workers, print_token_usage=args.print_token_usage)
    elif args.command == "gen-repro":
        issues_ids = args.issues_ids.split(",")
        codex_gen_repros(issues_ids, codex_workers=args.codex_workers, print_token_usage=args.print_token_usage)
if __name__ == "__main__":
    try:
        main()
    except KeyboardInterrupt:
        _log_info("Stopped.")
        sys.exit(0)
