#!/usr/bin/env python3
# Copyright (c) 2022, 2026, Oracle and/or its affiliates. All rights reserved.
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

import io
from pathlib import Path
import subprocess
import tempfile
from zipfile import ZIP_DEFLATED, ZipFile, ZipInfo


REPO_ROOT = Path(__file__).resolve().parents[1]
BUNDLED_DIR = REPO_ROOT / "graalpython/lib-python/3/ensurepip/_bundled"


def patch_wheel(name, patch):
    patch = REPO_ROOT / patch
    wheels = sorted(BUNDLED_DIR.glob(f"{name}-*.whl"))
    if len(wheels) != 1:
        raise SystemExit(f"Expected one {name} wheel in {BUNDLED_DIR}, found {len(wheels)}")
    wheel = wheels[0]
    with patch.open("rb") as patch_file:
        try:
            original_bytes = subprocess.check_output(
                ["git", "show", f"python-import:{wheel.relative_to(REPO_ROOT).as_posix()}"], cwd=REPO_ROOT
            )
        except subprocess.CalledProcessError:
            raise SystemExit(
                "Error when getting the vanilla wheel from the python-import branch\n"
                "Do you have a local branch named 'python-import'?\n"
                "If not, you can use: git fetch origin python-import:python-import"
            ) from None

        with tempfile.TemporaryDirectory(prefix="repack-bundled-wheels-") as tmp:
            tmp = Path(tmp)
            extracted = tmp / "extracted"
            with ZipFile(io.BytesIO(original_bytes)) as original:
                timestamps = {info.filename: info.date_time for info in original.infolist()}
                original.extractall(extracted)
            subprocess.run(["patch", "--quiet", "-p1"], stdin=patch_file, cwd=extracted, check=True)
            [dist_info] = extracted.glob(f"{name}-*.dist-info")
            (dist_info / "GRAALPY_MARKER").touch()

            # Preserve upstream timestamps even for patched files. New files
            # (such as GRAALPY_MARKER) inherit the latest upstream timestamp.
            default_timestamp = max(timestamps.values())
            repacked = tmp / wheel.name
            with ZipFile(repacked, "w", compression=ZIP_DEFLATED, compresslevel=9) as archive:
                # Fix entry order and metadata independently of the host time
                # zone, filesystem traversal order, and caller's umask.
                paths = sorted(extracted.rglob("*"), key=lambda path: path.relative_to(extracted).as_posix())
                for path in paths:
                    if path.is_file():
                        entry_name = path.relative_to(extracted).as_posix()
                        info = ZipInfo(entry_name, date_time=timestamps.get(entry_name, default_timestamp))
                        info.create_system = 3  # Unix, independent of the host platform.
                        info.external_attr = 0o100644 << 16
                        archive.writestr(info, path.read_bytes(), compress_type=ZIP_DEFLATED, compresslevel=9)
            wheel.write_bytes(repacked.read_bytes())


if __name__ == "__main__":
    patch_wheel("pip", "graalpython/lib-graalpython/patches/pip-26.2.2.patch")
