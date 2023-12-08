# Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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

import re
import copy
from typing import Literal, Any
from dataclasses import dataclass, field


__all__ = [
    "BuildSpec",
    "create_jobs",
    "Linux",
    "LinuxX86",
    "LinuxARM",
    "Mac",
    "MacX86",
    "MacARM",
    "Windows",
    "WindowsX86",
    "PLATFORMS",
]


def _glob_all_from_pattern(string, pattern=re.compile(r"[^a-zA-Z0-9]")):
    return re.sub(pattern, "*", string)


@dataclass(frozen=True)
class Platform:
    name: Literal["linux", "macos", "windows"] = "linux"
    arch: Literal["amd64", "aarch64"] = "amd64"

    @property
    def runs_on(self) -> str:
        match (self.name, self.arch):
            case ("linux", "amd64"):
                return ["self-hosted", "Linux", "X64"]
                # return "ubuntu-latest"
            case ("linux", "aarch64"):
                return ["self-hosted", "Linux", "ARM64"]
                # return "ubuntu-latest"
            case ("macos", "amd64"):
                # return "macos-11"
                return ["self-hosted", "macOS", "X64"]
            case ("macos", "aarch64"):
                return ["self-hosted", "macOS", "ARM64"]
        raise RuntimeError(f"Invalid platform spec {self.name}:{self.arch}")

    @property
    def container(self) -> str | None:
        match (self.name, self.arch):
            case ("linux", "amd64"):
                return "quay.io/pypa/manylinux_2_28_x86_64"
            case ("linux", "aarch64"):
                return "quay.io/pypa/manylinux_2_28_aarch64"
        return None

    def install_cmd(self, packages: list[str]) -> list[str]:
        match self.name:
            case "linux":
                return [
                    "dnf install -y epel-release",
                    "crb enable",
                    "dnf makecache --refresh",
                    "dnf module install -y nodejs:18",
                    "dnf install -y /usr/bin/patch",
                    f"dnf install -y {' '.join(packages)}" if packages else "",
                ]
            case "macos":
                return [f"brew install {' '.join(packages)}"]
        raise RuntimeError(f"Invalid platform spec {self.name}")

    def get_graalpy_cmd(self) -> list[str]:
        match self.name:
            case "linux" | "macos":
                return [
                    f"curl -L -o graalpy.tar.gz ${{{{ inputs.graalpy }}}}-{self.name}-{self.arch}.tar.gz",
                    "mkdir -p graalpy",
                    "tar -C $(pwd)/graalpy --strip-components=1 -xzf graalpy.tar.gz",
                    "graalpy/bin/graalpy -s -m ensurepip",
                    "graalpy/bin/graalpy -m pip install wheel",
                ]
        raise RuntimeError(f"Invalid platform spec {self.name}")

    def build_cmd(self, cmds: list[str]) -> list[str]:
        match self.name:
            case "linux" | "macos":
                return [
                    "export PIP_FIND_LINKS=$(pwd)",
                    "export PATH=$(pwd)/graalpy/bin/:$PATH",
                ] + cmds
            case "windows":
                return [
                    "set PIP_FIND_LINKS=%cd%",
                    "set PATH=%cd%\\graalpy\\bin;%PATH%",
                ] + cmds
        raise RuntimeError(f"Invalid platform spec {self.name}")

    def build_wheels(self, specs: list[str]) -> list[str]:
        match self.name:
            case "linux" | "macos":
                return self.build_cmd(
                    [
                        f"graalpy/bin/graalpy -m pip wheel --find-links $(pwd) {spec}"
                        for spec in specs
                    ]
                )
            case "windows":
                return self.build_cmd(
                    [
                        f"graalpy/bin/graalpy -m pip wheel --find-links %cd% {spec}"
                        for spec in specs
                    ]
                )
        raise RuntimeError(f"Invalid platform spec {self.name}")


# OS arguments to match all platforms this os is available for
Linux = Platform("linux", "any")
Mac = Platform("macos", "any")
Windows = Platform("windows", "any")


LinuxX86 = Platform("linux", "amd64")
LinuxARM = Platform("linux", "aarch64")
MacX86 = Platform("macos", "amd64")
MacARM = Platform("macos", "aarch64")
WindowsX86 = Platform("windows", "amd64")
PLATFORMS = [LinuxX86, LinuxARM, MacX86, MacARM, WindowsX86]


@dataclass
class BuildSpec:
    name: str
    extra_versions: list[str] = field(default_factory=list)
    platforms: list[Platform] = field(
        default_factory=lambda: [LinuxX86, LinuxARM, MacX86, MacARM]
    )
    system_dependencies: dict[Platform, list[str]] | list[str] = field(
        default_factory=dict
    )
    spec_dependencies: list["BuildSpec"] = field(default_factory=list)
    before_build: dict[Platform, list[str]] | list[str] = field(default_factory=list)
    build: dict[Platform, list[str]] | list[str] | None = None
    environment: dict[str, Any] | None = None
    custom_steps: dict[str, Any] = field(default_factory=dict)

    def get_before_build(self, platform: Platform) -> list[str]:
        if isinstance(self.before_build, list):
            return self.before_build
        else:
            return self.before_build.get(platform, [])

    def get_system_dependencies(self, platform: Platform) -> list[str]:
        if isinstance(self.system_dependencies, list):
            return self.system_dependencies
        else:
            return self.system_dependencies.setdefault(platform, [])

    def platform_name(self, platform: Platform) -> str:
        return f"{self.name}-{platform.name}-{platform.arch}"

    @classmethod
    def _append_instance(cls, instance):
        cls.instances = getattr(cls, "instances", [])
        cls.instances.append(instance)

    @classmethod
    def get_instances(cls) -> list["BuildSpec"]:
        return getattr(cls, "instances", [])

    def _append_downstream(self, spec: "BuildSpec"):
        self.dependents: list["BuildSpec"] = getattr(self, "dependents", [])
        self.dependents.append(spec)

    def get_downstream_specs(self):
        dependents = getattr(self, "dependents", [])
        dependent_names = set([s.name for s in dependents])
        for s1 in dependents:
            for s2 in s1.get_downstream_specs():
                if s2.name not in dependent_names:
                    dependent_names.add(s2.name)
                    dependents.append(s2)
        return dependents

    @classmethod
    def __new__(cls, *args, **kwargs):
        os_platform_map = {
            Linux: [LinuxX86, LinuxARM],
            Mac: [MacX86, MacARM],
            Windows: [WindowsX86],
        }
        assert kwargs and len(args) == 1, "Pass all arguments as keywords"
        for k in ["system_dependencies", "before_build", "build"]:
            if k in kwargs and isinstance(kwargs[k], dict):
                for os, platforms in os_platform_map.items():
                    if os_key := kwargs[k].get(os):
                        for platform in platforms:
                            kwargs[k].setdefault(platform, os_key)
                        del kwargs[k][os]
        if kwargs.get("platforms"):
            for os, platforms in os_platform_map.items():
                if os in platforms:
                    platforms.remove(os)
                    platforms.extend(platforms)
        new_instance = super().__new__(cls)
        cls._append_instance(new_instance)
        for dependency in kwargs.get("spec_dependencies", []):
            dependency._append_downstream(new_instance)
        return new_instance


def create_jobs(
    specs: list[BuildSpec], name: str, on: str | list[str]
) -> dict[str, Any]:
    result = {}
    for spec in specs:
        for platform in spec.platforms:
            job: dict[str, Any] = {
                "runs-on": platform.runs_on,
            }
            if platform.container:
                job["container"] = platform.container
            if spec_deps := spec.spec_dependencies:
                needs = [spec_dep.platform_name(platform) for spec_dep in spec_deps]
                job["needs"] = needs[0] if len(needs) == 1 else needs
            job["if"] = (
                "${{ !cancelled() && ("
                + (
                    " || ".join(
                        [
                            "inputs.name == ''",
                            f"inputs.name == '{spec.name}'",
                            *[
                                f"inputs.name == '{spec_dep.name}'"
                                for spec_dep in spec.get_downstream_specs()
                                if platform in spec_dep.platforms
                            ],
                        ]
                    )
                )
                + ") }}"
            )
            if spec.environment:
                job["env"] = spec.environment
            steps: list[dict[str, Any]] = []
            job["steps"] = steps
            if (
                deps := spec.get_system_dependencies(platform)
            ) or platform.name == "linux":
                steps.append(
                    {
                        "name": "Install dependencies",
                        "run": "\n".join(platform.install_cmd(deps)),
                    }
                )
            steps.append(
                {
                    "name": "Checkout",
                    "uses": "actions/checkout@v3",
                }
            )
            if spec.custom_steps:
                steps += copy.deepcopy(spec.custom_steps)
            steps.append(
                {
                    "name": "Setup custom GraalPy",
                    "if": "inputs.graalpy != ''",
                    "run": "\n".join(platform.get_graalpy_cmd()),
                }
            )
            steps.append(
                {
                    "name": "Setup GraalPy",
                    "uses": "actions/setup-python@main",
                    "if": "inputs.graalpy == ''",
                    "with": {
                        "python-version": "graalpy23.1",
                    },
                }
            )
            steps.append(
                {
                    "name": "Setup local GraalPy venv",
                    "if": "inputs.graalpy == ''",
                    "run": "python -m venv graalpy",
                }
            )
            if spec_deps := spec.spec_dependencies:
                for spec_dep in spec_deps:
                    steps.append(
                        {
                            "name": f"Download artifacts from {spec_dep.name}",
                            "uses": "actions/download-artifact@v3",
                            "continue-on-error": True,
                            "with": {"name": spec_dep.platform_name(platform)},
                        }
                    )
            wheel_builds = [spec.name]
            for version in spec.extra_versions:
                wheel_builds.append(f"{spec.name}=={version}")
            if isinstance(spec.build, dict) and (buildp := spec.build.get(platform)):
                steps.append(
                    {
                        "name": f"Build wheel custom on {platform.name}",
                        "run": "\n".join(
                            spec.get_before_build(platform) + platform.build_cmd(buildp)
                        ),
                    }
                )
            elif isinstance(spec.build, list):
                steps.append(
                    {
                        "name": "Build wheel custom",
                        "run": "\n".join(
                            spec.get_before_build(platform)
                            + platform.build_cmd(spec.build)
                        ),
                    }
                )
            else:
                steps.append(
                    {
                        "name": "Build wheel",
                        "run": "\n".join(
                            spec.get_before_build(platform)
                            + platform.build_wheels(wheel_builds)
                        ),
                    }
                )
            steps.append(
                {
                    "name": "Store wheels",
                    "uses": "umutozd/upload-artifact@5c459179e7745e2c730c50b10a6459da0b6f25db",
                    "with": {
                        "name": spec.platform_name(platform),
                        "path": f"{_glob_all_from_pattern(spec.name)}*.whl",
                        "if-no-files-found": "error",
                    },
                }
            )
            result[spec.platform_name(platform)] = job
    if on == "workflow_dispatch":
        on_spec = {
            "workflow_dispatch": {
                "inputs": {
                    "name": {
                        "type": "string",
                        "description": "Pkg to build (empty for all)",
                        "required": False,
                    },
                    "graalpy": {
                        "type": "string",
                        "description": "GraalPy download url prefix (empty for default)",
                        "required": False,
                    },
                }
            }
        }
    else:
        on_spec = on
    return {
        "name": name,
        "on": on_spec,
        "jobs": result,
    }
