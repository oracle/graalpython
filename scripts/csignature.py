#!/usr/bin/env python3
# Copyright (c) 2018, 2026, Oracle and/or its affiliates. All rights reserved.
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

"""Generate C API signature inventories and stable-ABI export manifests.

ABI definitions live in a TOML configuration file. Local CPython builds are
supplied on the command line because they are intentionally not checked in.
"""

from __future__ import annotations

import argparse
from dataclasses import dataclass
import json
import os
from pathlib import Path
import re
import subprocess
import tempfile

try:
    import tomllib
except ModuleNotFoundError:
    from pip._vendor import tomli as tomllib  # pylint: disable=no-name-in-module


HELP = """\
Pass the ABI profile configuration with --config. Each selected profile needs
a local CPython source-tree build mapped in this form:

  PROFILE=CPYTHON_REPOSITORY

CPYTHON_REPOSITORY is a configured and built CPython Git checkout. The script
uses its python executable and queries it for the version, include directories,
and library location. The checkout must contain the source_ref configured for
the profile. A full profile also requires a shared libpython; a stable profile
reads symbol membership from Misc/stable_abi.toml at its configured source_ref.

The script requires pycparser and pycparser-fake-libc. One way to install them
without modifying the current environment is:

  python3 -m venv /tmp/graalpy-csignature
  /tmp/graalpy-csignature/bin/pip install pycparser pycparser-fake-libc

Regenerate the GraalPy 3.13 and ABI3T inventories and configured manifests:

  /tmp/graalpy-csignature/bin/python scripts/csignature.py \\
    --config capi-abis.toml \\
    --abi GRAALPY=/path/to/cpython-3.13 \\
    --abi ABI3T=/path/to/cpython-3.15

Pass only one --abi to regenerate just that profile. Pass --check to verify
that every selected output is current without modifying files:

  /tmp/graalpy-csignature/bin/python scripts/csignature.py \\
    --config capi-abis.toml \\
    --abi ABI3T=/path/to/cpython-3.15 \\
    --check

The script adds each CPython build directory to LD_LIBRARY_PATH while querying
its executable, so in-tree shared libpython builds do not need manual loader
configuration.

Output paths, preprocessing definitions, CPython versions, pinned source refs,
and platform exclusions are all defined by the selected TOML file. Relative
output paths are resolved from the directory containing that file.
"""
HEADER_SOURCE = """\
#define __attribute__(x)
#define _POSIX_THREADS
#include <Python.h>
#include <frameobject.h>
#include <datetime.h>
#include <structmember.h>
#include <marshal.h>
"""


@dataclass(frozen=True)
class Profile:
    name: str
    parent: str | None
    python_version: str
    source_ref: str
    mode: str
    defines: tuple[str, ...]
    inventory: Path
    manifest: Path | None
    disabled_features: frozenset[str]


@dataclass(frozen=True)
class LocalCPython:
    repository: Path
    includes: tuple[Path, ...]
    library: Path | None


def load_profiles(config: Path) -> dict[str, Profile]:
    config = config.resolve()
    values = tomllib.loads(config.read_text(encoding="utf-8"))["profiles"]
    root = config.parent
    profiles = {name: Profile(name, value.get("parent"), value["python_version"], value["source_ref"], value["mode"],
                              tuple(value.get("defines", [])), root / value["inventory"],
                              root / value["export_manifest"] if "export_manifest" in value else None,
                              frozenset(value.get("disabled_features", [])))
                for name, value in values.items()}
    for profile in profiles.values():
        if profile.mode not in ("full", "stable"):
            raise ValueError(f"{profile.name}: unknown mode {profile.mode!r}")
        if profile.parent and profile.parent not in profiles:
            raise ValueError(f"{profile.name}: unknown parent {profile.parent!r}")
    return profiles


def parse_mapping(value: str) -> tuple[str, Path]:
    try:
        name, repository = value.split("=", 1)
    except ValueError as exc:
        raise argparse.ArgumentTypeError("expected PROFILE=CPYTHON_REPOSITORY") from exc
    return name, Path(repository).resolve()


def local_cpython(repository: Path, profile: Profile) -> LocalCPython:
    executable = repository / "python"
    if not executable.is_file():
        raise ValueError(f"{profile.name}: CPython checkout is not built (missing {executable})")
    code = "import json,sys,sysconfig;print(json.dumps({'version':f'{sys.version_info.major}.{sys.version_info.minor}','include':sysconfig.get_path('include'),'confinclude':sysconfig.get_config_var('CONFINCLUDEPY'),'srcdir':sysconfig.get_config_var('srcdir'),'libdir':sysconfig.get_config_var('LIBDIR'),'library':sysconfig.get_config_var('LDLIBRARY')}))"
    env = os.environ.copy()
    env["LD_LIBRARY_PATH"] = os.pathsep.join(filter(None, (str(repository), env.get("LD_LIBRARY_PATH"))))
    data = json.loads(subprocess.check_output([str(executable), "-c", code], text=True, env=env))
    if data["version"] != profile.python_version:
        raise ValueError(f"{profile.name}: expected CPython {profile.python_version}, got {data['version']} from {executable}")
    library = Path(data["libdir"] or "") / (data["library"] or "")
    # A CPython build run from its source tree can retain its installation
    # prefix in sysconfig even though the freshly built library is beside it.
    if not library.is_file() and data["library"]:
        library = executable.parent / data["library"]
    if not data["include"]:
        raise ValueError(f"{profile.name}: CPython build lacks headers: {executable}")
    if profile.mode == "full" and not library.is_file():
        raise ValueError(f"{profile.name}: CPython build lacks the shared library required for full-export generation: {executable}")
    includes = tuple(Path(path) for path in (data["include"], data.get("confinclude"), data.get("srcdir")) if path)
    return LocalCPython(repository, includes, library if library.is_file() else None)


def git_show(repository: Path, ref: str, filename: str) -> str:
    return subprocess.check_output(["git", "-C", str(repository), "show", f"{ref}:{filename}"], text=True)


def stable_abi(local: LocalCPython, profile: Profile) -> dict:
    return tomllib.loads(git_show(local.repository, profile.source_ref, "Misc/stable_abi.toml"))


def enabled(properties: dict, profile: Profile) -> bool:
    return properties.get("ifdef") not in profile.disabled_features


def cleanup(value: str) -> str:
    for _ in range(4):
        value = re.sub(" \\*", "*", value)
        value = re.sub("\\* ", "*", value)
    return value


def full_members(local: LocalCPython) -> set[str]:
    assert local.library is not None
    output = subprocess.check_output(["nm", "--defined-only", "--dynamic", "--format=just-symbols", str(local.library)], text=True)
    return set(output.splitlines())


def members(profile: Profile, local: LocalCPython) -> set[str]:
    if profile.mode == "full":
        return full_members(local)
    return {name for name, properties in stable_abi(local, profile)["function"].items() if enabled(properties, profile)}


def inventory(profile: Profile, local: LocalCPython) -> str:
    try:
        import pycparser_fake_libc
        from pycparser import c_ast, c_generator, parse_file
    except ModuleNotFoundError as exc:
        raise SystemExit("missing parser dependency; install pycparser and pycparser-fake-libc (see --help)") from exc

    class FunctionVisitor(c_ast.NodeVisitor):
        def __init__(self, function_members: set[str]):
            self.members = function_members
            self.generator = c_generator.CGenerator()
            self.results: list[str] = []

        def visit_Decl(self, node):  # pylint: disable=invalid-name
            if not isinstance(node.type, c_ast.FuncDecl) or node.name not in self.members:
                return
            params = node.type.args.params if node.type.args else []
            for param in params:
                if not isinstance(param, c_ast.EllipsisParam):
                    typ = param.type
                    while isinstance(typ, (c_ast.PtrDecl, c_ast.FuncDecl, c_ast.ArrayDecl)):
                        typ = typ.type
                    typ.declname = None
            result = f"{node.name};{cleanup(self.generator.visit(node.type.type))};"
            self.results.append(result + "|".join(cleanup(self.generator.visit(param)) for param in params))

    with tempfile.NamedTemporaryFile("w", suffix=".c") as source:
        source.write(HEADER_SOURCE)
        source.flush()
        args = ["-I", pycparser_fake_libc.directory]
        for include in local.includes:
            args.extend(("-I", str(include)))
        args.extend(f"-D{definition}" for definition in profile.defines)
        ast = parse_file(source.name, use_cpp=True, cpp_args=args)
    visitor = FunctionVisitor(members(profile, local))
    visitor.visit(ast)
    return "\n".join(sorted(set(visitor.results))) + "\n"


def manifest(profile: Profile, local: LocalCPython) -> str:
    if profile.mode != "stable" or profile.manifest is None:
        raise ValueError(f"{profile.name} has no stable export manifest")
    commit = subprocess.check_output(["git", "-C", str(local.repository), "rev-parse", f"{profile.source_ref}^{{commit}}"], text=True).strip()
    symbols = []
    for item_kind, elf_kind in (("function", "FUNC"), ("data", "OBJECT")):
        symbols.extend((name, elf_kind) for name, properties in stable_abi(local, profile).get(item_kind, {}).items() if enabled(properties, profile))
    lines = ["# Generated by scripts/csignature.py. Do not edit manually.",
             f"# Profile: {profile.name}; source: CPython {profile.source_ref} ({commit})",
             "# Source file: Misc/stable_abi.toml",
             f"# Disabled features: {', '.join(sorted(profile.disabled_features)) or 'none'}",
             "# Format: <expected ELF symbol kind> <symbol name>", ""]
    lines.extend(f"{kind} {name}" for name, kind in sorted(symbols))
    return "\n".join(lines) + "\n"


def write_or_check(path: Path, contents: str, check: bool) -> None:
    if check:
        if not path.is_file() or path.read_text(encoding="utf-8") != contents:
            raise SystemExit(f"out of date: {path}")
    else:
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(contents, encoding="utf-8")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__, epilog=HELP, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--config", required=True, type=Path, metavar="TOML", help="ABI profile configuration file")
    parser.add_argument("--abi", action="append", type=parse_mapping, metavar="PROFILE=CPYTHON_REPOSITORY",
                        help="select an ABI profile and map its CPython source-tree build; repeat to process multiple profiles")
    parser.add_argument("--check", action="store_true", help="verify selected outputs without modifying them")
    parser.add_argument("--manifest-only", action="store_true", help="skip inventories and process only configured stable export manifests")
    args = parser.parse_args()
    profiles = load_profiles(args.config)
    if not args.abi:
        parser.error("at least one --abi mapping is required")
    for name, repository in args.abi:
        if name not in profiles:
            parser.error(f"unknown profile: {name}")
        profile = profiles[name]
        local = local_cpython(repository, profile)
        if not args.manifest_only:
            write_or_check(profile.inventory, inventory(profile, local), args.check)
        if profile.manifest:
            write_or_check(profile.manifest, manifest(profile, local), args.check)


if __name__ == "__main__":
    main()
