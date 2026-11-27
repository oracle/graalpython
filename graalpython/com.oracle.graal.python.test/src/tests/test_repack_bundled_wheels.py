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

from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
from zipfile import ZipFile

from tests.util import skip_if_sandboxed


# A test that can catch possible tampering with the vendored pip wheel. It will
# also remind you if you forgot to repack after updating the patch or if you
# forgot to push python-import after doing a new CPython version import
class RepackBundledWheelsTest(unittest.TestCase):
    @unittest.skipIf(sys.platform == "win32", "Repacking should be done on Linux/Mac")
    @skip_if_sandboxed("requires external git and patch executables")
    def test_repack_preserves_bundled_wheel(self):
        repo_root = Path(__file__).resolve().parents[4]
        bundled_dir = repo_root / "graalpython/lib-python/3/ensurepip/_bundled"

        with tempfile.TemporaryDirectory(prefix="test-repack-bundled-wheels-") as tmp:
            output_dir = Path(tmp)
            subprocess.run(
                [sys.executable, str(repo_root / "scripts/repack-bundled-wheels.py"), "--output-dir", tmp],
                check=True,
            )
            self.assertEqual(
                sorted(wheel.name for wheel in output_dir.glob("*.whl")),
                sorted(wheel.name for wheel in bundled_dir.glob("*.whl")),
            )
            for repacked in output_dir.glob("*.whl"):
                with self.subTest(wheel=repacked.name):
                    # Compression output can differ between zlib implementations.
                    with ZipFile(repacked) as rebuilt, ZipFile(bundled_dir / repacked.name) as bundled:
                        self.assertEqual(sorted(rebuilt.namelist()), sorted(bundled.namelist()))
                        for name in rebuilt.namelist():
                            with self.subTest(entry=name):
                                self.assertEqual(rebuilt.read(name), bundled.read(name))
