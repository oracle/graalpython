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

"""
Set up a repository folder following
https://packaging.python.org/en/latest/guides/hosting-your-own-index/
"""

import glob
import gzip
import os
import re
import shutil
import sys
import html


def normalize(name):
    """
    Taken from
    https://packaging.python.org/en/latest/specifications/name-normalization/#name-normalization
    """
    return re.sub(r"[-_.]+", "-", name).lower()


if __name__ == "__main__":
    if len(sys.argv) > 1:
        workspace = sys.argv[1]
    else:
        workspace = os.environ["GITHUB_WORKSPACE"]
    # find wheels either locally in the workspace or in an act artifact root structure
    wheels = glob.glob(os.path.join(workspace, "**/*.whl")) or glob.glob(
        os.path.join(workspace, "**/**/*.whl.gz__")
    )
    repo = os.path.join(workspace, "repository", "simple")
    os.makedirs(repo, exist_ok=True)
    pkg_index = [
        "<html><head><title>Simple Index</title>",
        "<meta name='api-version' value='2' /></head><body>",
    ]
    processed_wheels = set()
    for wheel in wheels:
        print("Processing", wheel)
        basename = os.path.basename(wheel)
        parts = basename.split("-")
        for idx, part in enumerate(parts):
            if part.startswith("graalpy3") or part.startswith("py2") or part.startswith("py3"):
                version_idx = idx - 1
                break
        else:
            continue
        wheel_name = normalize("-".join(parts[:version_idx]))
        target_dir = os.path.join(repo, wheel_name)
        os.makedirs(target_dir, exist_ok=True)
        if wheel.endswith(".gz__"):
            with gzip.open(wheel, "rb") as f_in:
                with open(
                    os.path.join(
                        target_dir, basename.replace(".gz__", "")
                    ),
                    "wb",
                ) as f_out:
                    shutil.copyfileobj(f_in, f_out)
        else:
            shutil.copy(wheel, target_dir)

        if wheel_name not in processed_wheels:
            processed_wheels.add(wheel_name)
            wheel_name = html.escape(wheel_name)
            pkg_index.append(f"<a href='{wheel_name}/'>{wheel_name}</a><br />")

        with open(os.path.join(target_dir, "index.html"), "a") as f:
            basename = html.escape(basename)
            f.write(f"<a href='{basename}'>{basename}</a><br />\n")

    pkg_index.append("</body></html>")
    with open(os.path.join(repo, "index.html"), "w") as f:
        f.write("\n".join(pkg_index))

    shutil.make_archive(f"{workspace}/repository", "zip", repo)
