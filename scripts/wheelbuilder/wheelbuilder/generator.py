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

import os
import sys
import yaml

from . import *


__all__ = ["generate"]


# We tweak pyyaml to produce yaml files formatted like in the Github examples
# for consistency
def literal_str_representer(dumper: yaml.Dumper, data):
    if "\n" in data:
        return dumper.represent_scalar("tag:yaml.org,2002:str", data, style="|")
    else:
        return dumper.represent_scalar("tag:yaml.org,2002:str", data)


yaml.add_representer(str, literal_str_representer)


class IndentedListDumper(yaml.Dumper):
    def increase_indent(self, flow=False, indentless=False):
        return super().increase_indent(flow, False)


def generate(workflow_directory):
    # We generate separate workflow files for each platform, because github
    # chokes on workflow files with too many jobs
    original_platforms = [spec.platforms for spec in BuildSpec.get_instances()]

    # We also add needs relationships between all jobs, to make a totally
    # ordered queue. This is because there's no good way to limit concurrency
    # on github or act, and we do not want to overload our worker
    linked_specs = []
    unlinked_specs = list(BuildSpec.get_instances())
    last_spec = None
    while unlinked_specs:
        for spec in sorted(
            unlinked_specs[:],
            key=lambda s: 0 if last_spec in s.spec_dependencies else 1,
        ):
            if all(dep in linked_specs for dep in spec.spec_dependencies):
                linked_specs.append(spec)
                unlinked_specs.remove(spec)
                if last_spec and last_spec not in spec.spec_dependencies:
                    spec.spec_dependencies.append(last_spec)
                last_spec = spec

    for p in PLATFORMS:
        for spec, original_platform in zip(linked_specs, original_platforms):
            if p in original_platform:
                spec.platforms = [p]
            else:
                spec.platforms = []
            jobs = create_jobs(
                linked_specs, f"build-{p.name}-{p.arch}-wheels", "workflow_dispatch"
            )
        with open(
            os.path.join(workflow_directory, f"build-{p.name}-{p.arch}-wheels.yml"), "w"
        ) as f:
            f.write(yaml.dump(jobs, Dumper=IndentedListDumper, sort_keys=False))

    # act workflow for local execution. These need node explictly in the Linux docker containers
    for spec, original_platform in zip(linked_specs, original_platforms):
        spec.platforms = original_platform
        spec.get_downstream_specs().clear()
    jobs = create_jobs(linked_specs, "build-act-wheels", "workflow_dispatch")
    with open(os.path.join(workflow_directory, "build-act-wheels.yml"), "w") as f:
        f.write(yaml.dump(jobs, Dumper=IndentedListDumper, sort_keys=False))

    # generate a workflow to create the repository
    repo_job = {
        "name": "build-repository",
        "on": "workflow_dispatch",
        "jobs": {
            "build-repo": {
                "runs-on": "ubuntu-latest",
                "steps": [
                    {
                        "name": "Checkout",
                        "uses": "actions/checkout@v4",
                    },
                ]
                + [
                    {
                        "name": f"Download artifacts for {p.name}-{p.arch}",
                        "uses": "dawidd6/action-download-artifact@268677152d06ba59fcec7a7f0b5d961b6ccd7e1e",
                        "continue-on-error": True,
                        "with": {
                            "workflow": f"build-{p.name}-{p.arch}-wheels.yml",
                            "workflow_conclusion": "",
                            "if_no_artifact_found": "warn",
                            "allow_forks": "false",
                        },
                    }
                    for p in PLATFORMS
                ]
                + [
                    {
                        "name": "Set up Python",
                        "uses": "actions/setup-python@v4",
                        "with": {
                            "python-version": "3.10",
                        },
                    },
                    {
                        "name": "Create repository",
                        "run": "python ${GITHUB_WORKSPACE}/scripts/wheelbuilder/generate_repository.py",
                    },
                    {
                        "name": "Store repository",
                        "uses": "umutozd/upload-artifact@5c459179e7745e2c730c50b10a6459da0b6f25db",
                        "with": {
                            "name": "repository",
                            "path": "repository.zip",
                            "if-no-files-found": "error",
                        },
                    },
                ],
            },
        },
    }
    with open(os.path.join(workflow_directory, "build-repository.yml"), "w") as f:
        f.write(yaml.dump(repo_job, Dumper=IndentedListDumper, sort_keys=False))

    print(
        "Generated specs to build the following packages\n\t",
        "\n\t".join([s.name for s in linked_specs]),
        sep="",
    )
