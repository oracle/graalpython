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

import argparse
import json
import sys
import urllib.error
import urllib.parse
import urllib.request
from dataclasses import dataclass
from pathlib import Path
from typing import Any

try:
    import tomllib
except ModuleNotFoundError:
    from pip._vendor import tomli as tomllib  # pylint: disable=no-name-in-module

try:
    from packaging.specifiers import InvalidSpecifier, SpecifierSet
    from packaging.version import InvalidVersion, Version
except ModuleNotFoundError:
    from pip._vendor.packaging.specifiers import InvalidSpecifier, SpecifierSet  # pylint: disable=no-name-in-module
    from pip._vendor.packaging.version import InvalidVersion, Version  # pylint: disable=no-name-in-module


DEFAULT_METADATA = Path(__file__).resolve().parents[1] / "graalpython" / "lib-graalpython" / "patches" / "metadata.toml"
PYPI_PROJECT_URL = "https://pypi.org/pypi/{project}/json"


@dataclass(frozen=True)
class Rule:
    package: str
    index: int
    version: str
    specifier: SpecifierSet
    patch: str


@dataclass(frozen=True)
class PackageUpdate:
    package: str
    latest: Version
    latest_patched: Version | None
    rules: tuple[Rule, ...]
    matched_by_metadata: bool


class CheckError(RuntimeError):
    pass


def load_metadata(path: Path) -> dict[str, Any]:
    with path.open("rb") as file:
        data = tomllib.load(file)
    if not isinstance(data, dict):
        raise CheckError(f"{path} does not contain a TOML table")
    return data


def has_upper_bound(specifier: SpecifierSet) -> bool:
    for spec in specifier:
        if spec.operator in {"<", "<=", "~="}:
            return True
        if spec.operator == "==":
            # PEP 440 prefix matching, such as "== 1.4.*", implies an upper bound.
            return True
    return False


def iter_upper_bounded_patch_rules(metadata: dict[str, Any]) -> list[Rule]:
    result: list[Rule] = []
    for package, package_metadata in metadata.items():
        rules = package_metadata.get("rules", [])
        for index, rule in enumerate(rules, start=1):
            version = rule.get("version")
            patch = rule.get("patch")
            if not version or not patch or rule.get("install-priority") == 0:
                continue
            try:
                specifier = SpecifierSet(version)
            except InvalidSpecifier as exc:
                raise CheckError(f"{package}.rules[{index}] has invalid version specifier {version!r}: {exc}") from exc
            if has_upper_bound(specifier):
                result.append(Rule(package, index, version, specifier, patch))
    return result


def iter_metadata_specifiers(package_metadata: dict[str, Any]) -> list[SpecifierSet]:
    result: list[SpecifierSet] = []
    for rule in package_metadata.get("rules", []):
        if version := rule.get("version"):
            result.append(SpecifierSet(version))
        else:
            result.append(SpecifierSet(""))
    return result


def fetch_pypi_releases(package: str, timeout: int) -> list[tuple[Version, list[dict[str, Any]]]]:
    quoted = urllib.parse.quote(package, safe="")
    request = urllib.request.Request(PYPI_PROJECT_URL.format(project=quoted), headers={"Accept": "application/json"})
    try:
        with urllib.request.urlopen(request, timeout=timeout) as response:
            payload = json.load(response)
    except urllib.error.HTTPError as exc:
        if exc.code == 404:
            raise CheckError(f"{package}: package not found on PyPI") from exc
        raise CheckError(f"{package}: PyPI returned HTTP {exc.code}") from exc
    except urllib.error.URLError as exc:
        raise CheckError(f"{package}: could not reach PyPI: {exc.reason}") from exc
    except json.JSONDecodeError as exc:
        raise CheckError(f"{package}: PyPI returned invalid JSON") from exc

    releases = payload.get("releases")
    if not isinstance(releases, dict):
        raise CheckError(f"{package}: PyPI returned an unexpected payload")

    parsed: list[tuple[Version, list[dict[str, Any]]]] = []
    for version_text, files in releases.items():
        try:
            version = Version(version_text)
        except InvalidVersion:
            continue
        if isinstance(files, list):
            parsed.append((version, [file for file in files if isinstance(file, dict)]))
    return parsed


def release_is_usable(
    version: Version, files: list[dict[str, Any]], include_prereleases: bool, include_yanked: bool
) -> bool:
    if version.is_prerelease and not include_prereleases:
        return False
    if not files:
        return False
    if include_yanked:
        return True
    return not all(file.get("yanked", False) for file in files)


def latest_release(
    releases: list[tuple[Version, list[dict[str, Any]]]], include_prereleases: bool, include_yanked: bool
) -> Version | None:
    versions = [
        version
        for version, files in releases
        if release_is_usable(version, files, include_prereleases, include_yanked)
    ]
    return max(versions, default=None)


def latest_matching_release(
    releases: list[tuple[Version, list[dict[str, Any]]]],
    specifier: SpecifierSet,
    include_prereleases: bool,
    include_yanked: bool,
) -> Version | None:
    versions = [
        version
        for version, files in releases
        if release_is_usable(version, files, include_prereleases, include_yanked)
        and specifier.contains(version, prereleases=True)
    ]
    return max(versions, default=None)


def version_matches_any_metadata_rule(version: Version, package_metadata: dict[str, Any]) -> bool:
    return any(
        specifier.contains(version, prereleases=True) for specifier in iter_metadata_specifiers(package_metadata)
    )


def find_updates(
    metadata: dict[str, Any],
    timeout: int,
    include_prereleases: bool,
    include_yanked: bool,
    all_rules: bool,
) -> list[PackageUpdate]:
    updates: list[PackageUpdate] = []
    rules_by_package: dict[str, list[Rule]] = {}
    for rule in iter_upper_bounded_patch_rules(metadata):
        rules_by_package.setdefault(rule.package, []).append(rule)

    for package, rules in rules_by_package.items():
        releases = fetch_pypi_releases(package, timeout)
        latest = latest_release(releases, include_prereleases, include_yanked)
        if latest is None:
            continue

        outdated_rules: list[Rule] = []
        latest_patched: Version | None = None
        for rule in rules:
            patched = latest_matching_release(releases, rule.specifier, include_prereleases, include_yanked)
            if patched is not None:
                latest_patched = max(latest_patched, patched) if latest_patched is not None else patched
            if patched is not None and latest > patched:
                outdated_rules.append(rule)

        matched_by_metadata = version_matches_any_metadata_rule(latest, metadata[package])
        if all_rules:
            selected_rules = tuple(outdated_rules)
        elif matched_by_metadata:
            selected_rules = ()
        else:
            selected_rules = tuple(rules)

        if selected_rules:
            updates.append(PackageUpdate(package, latest, latest_patched, selected_rules, matched_by_metadata))
    return updates


def print_updates(updates: list[PackageUpdate], all_rules: bool) -> None:
    if not updates:
        print("No patch update candidates found.")
        return

    package_width = max(len(update.package) for update in updates)
    latest_width = max(len(str(update.latest)) for update in updates)
    patched_width = max(len(str(update.latest_patched or "-")) for update in updates)
    print(f"{'package'.ljust(package_width)}  {'latest'.ljust(latest_width)}  {'patched'.ljust(patched_width)}  rules")
    print(f"{'-' * package_width}  {'-' * latest_width}  {'-' * patched_width}  -----")
    for update in updates:
        rules = ", ".join(f"#{rule.index} {rule.version} ({rule.patch})" for rule in update.rules)
        if all_rules and update.matched_by_metadata:
            rules = f"{rules}; latest is already covered by another metadata rule"
        print(
            f"{update.package.ljust(package_width)}  {str(update.latest).ljust(latest_width)}  "
            f"{str(update.latest_patched or '-').ljust(patched_width)}  {rules}"
        )


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(
        description="Check upper-bounded GraalPy package patch rules against the newest PyPI releases."
    )
    parser.add_argument(
        "metadata",
        nargs="?",
        type=Path,
        default=DEFAULT_METADATA,
        help=f"Path to metadata.toml. Defaults to {DEFAULT_METADATA}",
    )
    parser.add_argument("--timeout", type=int, default=30, help="PyPI request timeout in seconds.")
    parser.add_argument("--include-prereleases", action="store_true", help="Consider pre-release PyPI versions.")
    parser.add_argument("--include-yanked", action="store_true", help="Consider yanked PyPI releases.")
    parser.add_argument(
        "--all-rules",
        action="store_true",
        help=(
            "Show every outdated upper-bounded patch rule, even when a later metadata rule already covers PyPI latest."
        ),
    )
    parser.add_argument("--fail-on-updates", action="store_true", help="Exit with status 1 when candidates are found.")
    args = parser.parse_args(argv)

    try:
        metadata = load_metadata(args.metadata)
        updates = find_updates(
            metadata,
            args.timeout,
            args.include_prereleases,
            args.include_yanked,
            args.all_rules,
        )
    except CheckError as exc:
        print(exc, file=sys.stderr)
        return 2

    print_updates(updates, args.all_rules)
    if updates and args.fail_on_updates:
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
