#!/usr/bin/env python3
import argparse
import fnmatch
import json
import os
import re
import shlex
import subprocess
import sys

from dataclasses import dataclass
from functools import cached_property, total_ordering
from typing import Any

DEFAULT_ENV = {
    "CI": "true",
    "PYTHONIOENCODING": "utf-8",
    "GITHUB_CI": "true"
}

# If any of these terms are in the job json, they do not run in public
# infrastructure
JOB_EXCLUSION_TERMS = (
    "enterprise",
    "corporate-compliance",

    # Jobs failing in GitHub Actions:buffer overflow, out of memory
    "python-svm-unittest",
    "cpython-gate",

    "darwin",
)

DOWNLOADS_LINKS = {
    "GRADLE_JAVA_HOME": "https://download.oracle.com/java/{major_version}/latest/jdk-{major_version}_{os}-{arch_short}_bin{ext}"
}

# Gitlab Runners OSS
OSS = {
    "macos-latest": ["darwin", "aarch64"],
    "ubuntu-latest": ["linux", "amd64"],
    "ubuntu-24.04-arm": ["linux", "aarch64"],
    "windows-latest": ["windows", "amd64"]
}

# Override unavailable Python versions for some OS/Arch combinations
PYTHON_VERSIONS = {
    "ubuntu-24.04-arm": "3.12.8",
}


@dataclass
class Artifact:
    name: str
    pattern: str


@total_ordering
class Job:
    def __init__(self, job: dict[str, Any]):
        self.job = job

    @cached_property
    def runs_on(self) -> str:
        capabilities = self.job.get("capabilities", [])

        for os, caps in OSS.items():
            if all(required in capabilities for required in caps): return os
            
        return "ubuntu-latest"

    @cached_property
    def name(self) -> str:
        return self.job["name"]

    @cached_property
    def targets(self) -> list[str]:
        return self.job.get("targets", [])

    @cached_property
    def env(self) -> dict[str, str]:
        return self.job.get("environment", {}) | DEFAULT_ENV

    @cached_property
    def mx_version(self) -> str | None:
        for k, v in self.job.get("packages", {}).items():
            if k == "mx":
                return v.strip("=<>~")

    @cached_property
    def python_version(self) -> str | None:
        python_version = None
        for k, v in self.job.get("packages", {}).items():
            if k == "python3":
                python_version = v.strip("=<>~")
        for k, v in self.job.get("downloads", {}).items():
            if k == "PYTHON3_HOME":
                python_version = v.get("version", python_version)
        if "MX_PYTHON" in self.env:
            del self.env["MX_PYTHON"]
        if "MX_PYTHON_VERSION" in self.env:
            del self.env["MX_PYTHON_VERSION"]
            
        if self.runs_on in PYTHON_VERSIONS:
            python_version = PYTHON_VERSIONS[self.runs_on]
        return python_version

    @cached_property
    def system_packages(self) -> list[str]:
        # TODO: support more packages
        system_packages = []
        for k, _ in self.job.get("packages", {}).items():
            if k in ["mx", "python3"]:
                continue
            if k.startswith("pip:"):
                continue
            elif k.startswith("00:") or k.startswith("01:"):
                k = k[3:]
            system_packages.append(f"'{k}'" if self.runs_on != "windows-latest" else f"{k}")
        return system_packages

    @cached_property
    def python_packages(self) -> list[str]:
        python_packages = []
        for k, v in self.job.get("packages", {}).items():
            if k.startswith("pip:"):
                python_packages.append(f"'{k[4:]}{v}'" if self.runs_on != "windows-latest" else f"{k[4:]}{v}")
        return python_packages

    def get_download_steps(self, key: str, version: str) -> str:
        download_link = self.get_download_link(key, version)
        filename = download_link.split('/')[-1]

        if self.runs_on == "windows-latest":
            return (f"""
                Invoke-WebRequest -Uri {download_link} -OutFile {filename}
                Expand-Archive -Path {filename} -DestinationPath .
                $dirname = (Get-ChildItem -Directory | Select-Object -First 1).Name
                Add-Content $env:GITHUB_ENV "{key}=$(Resolve-Path $dirname)"
            """)

        return (f"wget -q {download_link} && "
            f"dirname=$(tar -tzf {filename} | head -1 | cut -f1 -d '/') && "
            f"tar -xzf {filename} && "
            f'echo {key}=$(realpath "$dirname") >> $GITHUB_ENV')
    
    
    def get_download_link(self, key: str, version: str) -> str:
        os, arch = OSS[self.runs_on]
        major_version = version.split(".")[0]
        extension = ".tar.gz" if not os == "windows" else ".zip"
        os = os if os != "darwin" else "macos"
        arch_short = {"amd64": "x64", "aarch64": "aarch64"}[arch]

        vars = {
            "major_version": major_version,
            "os":os, 
            "arch": arch, 
            "arch_short": arch_short,
            "ext": extension,
        }

        return DOWNLOADS_LINKS[key].format(**vars)

    @cached_property
    def downloads(self) -> list[str]:
        downloads = []
        for k, download_info in self.job.get("downloads", {}).items():
            if k in DOWNLOADS_LINKS and download_info["version"]:
                downloads.append(self.get_download_steps(k, download_info["version"]))

        return downloads

    @staticmethod
    def common_glob(strings: list[str]) -> str:
        assert strings
        if len(strings) == 1:
            return strings[0]
        prefix = strings[0]
        for s in strings[1:]:
            i = 0
            while i < len(prefix) and i < len(s) and prefix[i] == s[i]:
                i += 1
            prefix = prefix[:i]
            if not prefix:
                break
        suffix = strings[0][len(prefix):]
        for s in strings[1:]:
            i = 1
            while i <= len(suffix) and i <= len(s) and suffix[-i] == s[-i]:
                i += 1
            if i == 1:
                suffix = ""
                break
            suffix = suffix[-(i-1):]
        return f"{prefix}*{suffix}"

    @cached_property
    def upload_artifact(self) -> Artifact | None:
        if artifacts := self.job.get("publishArtifacts", []):
            assert len(artifacts) == 1
            dir = artifacts[0].get("dir", ".")
            patterns = artifacts[0].get("patterns", ["*"])
            return Artifact(
                artifacts[0]["name"],
                " ".join([os.path.normpath(os.path.join(dir, p)) for p in patterns])
            )
        return None

    @cached_property
    def download_artifact(self) -> Artifact | None:
        if artifacts := self.job.get("requireArtifacts", []):
            pattern = self.common_glob([a["name"] for a in artifacts])
            return Artifact(pattern, os.path.normpath(artifacts[0].get("dir", ".")))
        return None
    

    @staticmethod
    def flatten_command(args: list[str | list[str]]) -> list[str]:
        flattened_args = []
        for s in args:
            if isinstance(s, list):
                flattened_args.append(f"$( {shlex.join(s)} )")
            else:
                flattened_args.append(s)
        return flattened_args

    @cached_property
    def setup(self) -> str:
        cmds = [self.flatten_command(step) for step in self.job.get("setup", [])]
        return "\n".join(shlex.join(s) for s in cmds)

    @cached_property
    def run(self) -> str:
        cmds = [self.flatten_command(step) for step in self.job.get("run", [])]
        return "\n".join(shlex.join(s) for s in cmds)

    @cached_property
    def logs(self) -> str:
        return "\n".join(os.path.normpath(p) for p in self.job.get("logs", []))

    def to_dict(self):
        """
        This is the interchange with the YAML file defining the Github jobs, so here
        is where we must match the strings and expectations of the Github workflow.
        """
        return {
            "name": self.name,
            "mx_version": self.mx_version,
            "os": self.runs_on,
            "python_version": self.python_version,
            "setup_steps": self.setup,
            "run_steps": self.run,
            "python_packages": " ".join(self.python_packages),
            "system_packages": " ".join(self.system_packages),
            "provide_artifact": [self.upload_artifact.name, self.upload_artifact.pattern] if self.upload_artifact else None,
            "require_artifact": [self.download_artifact.name, self.download_artifact.pattern] if self.download_artifact else None,
            "logs": self.logs.replace("../", "${{ env.PARENT_DIRECTORY }}/"),
            "env": self.env,
            "downloads_steps": " ".join(self.downloads),
        }

    def __str__(self):
        return str(self.to_dict())

    def __eq__(self, other):
        if isinstance(other, Job):
            return self.to_dict() == other.to_dict()
        return NotImplemented

    def __gt__(self, other):
        if isinstance(other, Job):
            if self.job.get("runAfter") == other.name:
                return True
            if self.download_artifact and not other.download_artifact:
                return True
            if self.download_artifact and other.upload_artifact:
                if fnmatch.fnmatch(other.upload_artifact.name, self.download_artifact.name):
                    return True
                if not self.upload_artifact:
                    return True
            return False
        return NotImplemented


def get_tagged_jobs(buildspec, target, filter=None):
    jobs = [Job({"name": target}).to_dict()]
    for job in sorted([Job(build) for build in buildspec.get("builds", [])]):
        if not any(t for t in job.targets if t in [target]):
            if "weekly" in job.targets and target == "tier1": pass
            else: 
                continue
        if filter and not re.match(filter, job.name):
            continue
        if [x for x in JOB_EXCLUSION_TERMS if x in str(job)]:
            continue
        jobs.append(job.to_dict())
    return jobs


def main(jsonnet_bin, ci_jsonnet, target, filter=None, indent=False):

    result = subprocess.check_output([jsonnet_bin, ci_jsonnet], text=True)
    buildspec = json.loads(result)
    tagged_jobs = get_tagged_jobs(buildspec, target, filter=filter)
    matrix = tagged_jobs
    print(json.dumps(matrix, indent=2 if indent else None))


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Generate GitHub CI matrix from Jsonnet buildspec.")
    parser.add_argument("jsonnet_bin", help="Path to jsonnet binary")
    parser.add_argument("ci_jsonnet", help="Path to ci.jsonnet spec")
    parser.add_argument("target", help="Target name (e.g., tier1)")
    parser.add_argument("filter", nargs="?", default=None, help="Regex filter for job names (optional)")
    parser.add_argument('--indent', action='store_true', help='Indent output JSON')
    args = parser.parse_args()
    main(
        jsonnet_bin=args.jsonnet_bin,
        ci_jsonnet=args.ci_jsonnet,
        target=args.target,
        filter=args.filter,
        indent=args.indent or sys.stdout.isatty(),
    )
