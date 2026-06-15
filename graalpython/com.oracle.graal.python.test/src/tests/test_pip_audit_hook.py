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

import os
import shutil
import subprocess
import sys
import tempfile
import unittest
import zipfile
from pathlib import Path


def write_fake_pip(root, patched=False):
    pip_package = root / "pip"
    pip_package.mkdir()
    pip_package.joinpath("__init__.py").write_text(
        "__GRAALPY_PATCHED = True\n" if patched else "", encoding="utf-8"
    )
    return root


class PipAuditHookTests(unittest.TestCase):
    @unittest.skipUnless(sys.implementation.name == "graalpy", "GraalPy-specific test")
    @unittest.skipUnless(shutil.which("patch"), "requires the patch utility")
    def test_source_ready_event_applies_local_graalpy_patch_after_unpatched_pip_import(self):
        with tempfile.TemporaryDirectory(prefix="graalpy-pip-audit-") as tmp:
            root = Path(tmp)
            patches = root / "patches"
            source = root / "demo-1.0.0"
            fake_pip_root = root / "fake-pip"
            patches.mkdir()
            source.mkdir()
            fake_pip_root.mkdir()
            write_fake_pip(fake_pip_root)

            (patches / "metadata.toml").write_text(
                "\n".join(
                    [
                        "[[demo.rules]]",
                        "version = '== 1.0.0'",
                        "patch = 'demo.patch'",
                        "license = 'MIT'",
                    ]
                ),
                encoding="utf-8",
            )
            (patches / "demo.patch").write_text(
                "\n".join(
                    [
                        "diff --git a/demo_module.py b/demo_module.py",
                        "--- a/demo_module.py",
                        "+++ b/demo_module.py",
                        "@@ -1 +1 @@",
                        '-value = "old"',
                        '+value = "patched"',
                        "",
                    ]
                ),
                encoding="utf-8",
            )
            module = source / "demo_module.py"
            module.write_text('value = "old"\n', encoding="utf-8")

            env = os.environ.copy()
            env["PIP_GRAALPY_PATCHES_URL"] = patches.as_uri()
            env.pop("PIP_GRAALPY_DISABLE_PATCHING", None)

            subprocess.check_call(
                [
                    sys.executable,
                    "-S",
                    "-c",
                    (
                        "import sys; "
                        "sys.path.insert(0, sys.argv[1]); "
                        "import pip; "
                        "sys.audit('pip.requirement.source_ready', "
                        "'demo', None, 'https://example.invalid/demo-1.0.0.tar.gz', "
                        "sys.argv[2], False, 0)"
                    ),
                    str(fake_pip_root),
                    str(source),
                ],
                env=env,
            )

            self.assertEqual(
                module.read_text(encoding="utf-8"), 'value = "patched"\n'
            )

    @unittest.skipUnless(sys.implementation.name == "graalpy", "GraalPy-specific test")
    @unittest.skipUnless(shutil.which("patch"), "requires the patch utility")
    def test_wheel_install_event_rewrites_wheel_before_install(self):
        with tempfile.TemporaryDirectory(prefix="graalpy-pip-audit-wheel-") as tmp:
            root = Path(tmp)
            patches = root / "patches"
            fake_pip_root = root / "fake-pip"
            patches.mkdir()
            fake_pip_root.mkdir()
            write_fake_pip(fake_pip_root)
            wheel_path = root / "demo-1.0.0-py3-none-any.whl"

            (patches / "metadata.toml").write_text(
                "\n".join(
                    [
                        "[[demo.rules]]",
                        "version = '== 1.0.0'",
                        "dist-type = 'wheel'",
                        "patch = 'demo.patch'",
                        "license = 'MIT'",
                    ]
                ),
                encoding="utf-8",
            )
            (patches / "demo.patch").write_text(
                "\n".join(
                    [
                        "diff --git a/demo_module.py b/demo_module.py",
                        "--- a/demo_module.py",
                        "+++ b/demo_module.py",
                        "@@ -1 +1 @@",
                        '-value = "old"',
                        '+value = "patched"',
                        "",
                    ]
                ),
                encoding="utf-8",
            )

            with zipfile.ZipFile(wheel_path, "w") as z:
                z.writestr("demo_module.py", 'value = "old"\n')
                z.writestr(
                    "demo-1.0.0.dist-info/WHEEL",
                    "Wheel-Version: 1.0\nRoot-Is-Purelib: true\nTag: py3-none-any\n",
                )
                z.writestr(
                    "demo-1.0.0.dist-info/METADATA",
                    "Metadata-Version: 2.1\nName: demo\nVersion: 1.0.0\n",
                )
                z.writestr("demo-1.0.0.dist-info/RECORD", "")

            env = os.environ.copy()
            env["PIP_GRAALPY_PATCHES_URL"] = patches.as_uri()
            env.pop("PIP_GRAALPY_DISABLE_PATCHING", None)

            subprocess.check_call(
                [
                    sys.executable,
                    "-S",
                    "-c",
                    (
                        "import sys; "
                        "sys.path.insert(0, sys.argv[1]); "
                        "import pip; "
                        "sys.audit('pip.wheel.install', 'demo', sys.argv[2])"
                    ),
                    str(fake_pip_root),
                    str(wheel_path),
                ],
                env=env,
            )

            with zipfile.ZipFile(wheel_path) as z:
                self.assertEqual(
                    z.read("demo_module.py").decode("utf-8"), 'value = "patched"\n'
                )
                self.assertIn("demo-1.0.0.dist-info/GRAALPY_MARKER", z.namelist())
                record = z.read("demo-1.0.0.dist-info/RECORD").decode("utf-8")
                self.assertIn("demo_module.py,sha256=", record)
                self.assertIn("demo-1.0.0.dist-info/GRAALPY_MARKER,sha256=", record)

    @unittest.skipUnless(sys.implementation.name == "graalpy", "GraalPy-specific test")
    @unittest.skipUnless(shutil.which("patch"), "requires the patch utility")
    def test_patched_pip_does_not_install_audit_hook(self):
        with tempfile.TemporaryDirectory(prefix="graalpy-pip-audit-patched-") as tmp:
            root = Path(tmp)
            patches = root / "patches"
            source = root / "demo-1.0.0"
            fake_pip_root = root / "fake-pip"
            patches.mkdir()
            source.mkdir()
            fake_pip_root.mkdir()
            write_fake_pip(fake_pip_root, patched=True)

            (patches / "metadata.toml").write_text(
                "\n".join(
                    [
                        "[[demo.rules]]",
                        "version = '== 1.0.0'",
                        "patch = 'demo.patch'",
                        "license = 'MIT'",
                    ]
                ),
                encoding="utf-8",
            )
            (patches / "demo.patch").write_text(
                "\n".join(
                    [
                        "diff --git a/demo_module.py b/demo_module.py",
                        "--- a/demo_module.py",
                        "+++ b/demo_module.py",
                        "@@ -1 +1 @@",
                        '-value = "old"',
                        '+value = "patched"',
                        "",
                    ]
                ),
                encoding="utf-8",
            )
            module = source / "demo_module.py"
            module.write_text('value = "old"\n', encoding="utf-8")

            env = os.environ.copy()
            env["PIP_GRAALPY_PATCHES_URL"] = patches.as_uri()
            env.pop("PIP_GRAALPY_DISABLE_PATCHING", None)

            subprocess.check_call(
                [
                    sys.executable,
                    "-S",
                    "-c",
                    (
                        "import sys; "
                        "sys.path.insert(0, sys.argv[1]); "
                        "import pip; "
                        "sys.audit('pip.requirement.source_ready', "
                        "'demo', None, 'https://example.invalid/demo-1.0.0.tar.gz', "
                        "sys.argv[2], False, 0)"
                    ),
                    str(fake_pip_root),
                    str(source),
                ],
                env=env,
            )

            self.assertEqual(module.read_text(encoding="utf-8"), 'value = "old"\n')

    @unittest.skipUnless(sys.implementation.name == "graalpy", "GraalPy-specific test")
    def test_cli_parse_args_event_adds_graalpy_defaults(self):
        with tempfile.TemporaryDirectory(prefix="graalpy-pip-audit-cli-") as tmp:
            root = Path(tmp)
            fake_pip_root = root / "fake-pip"
            fake_pip_root.mkdir()
            write_fake_pip(fake_pip_root)

            env = os.environ.copy()
            env.pop("PIP_INDEX_URL", None)
            env.pop("PIP_CACHE_DIR", None)

            subprocess.check_call(
                [
                    sys.executable,
                    "-S",
                    "-c",
                    (
                        "import sys; "
                        "from types import SimpleNamespace; "
                        "sys.path.insert(0, sys.argv[1]); "
                        "import pip; "
                        "options = SimpleNamespace(extra_index_urls=[], cache_dir=sys.argv[2]); "
                        "sys.audit('pip.cli.parse_args', options, [], 0); "
                        "assert options.extra_index_urls == ['https://www.graalvm.org/python/wheels/']; "
                        "assert options.cache_dir == sys.argv[2] + '-graalpy'"
                    ),
                    str(fake_pip_root),
                    str(root / "pip-cache"),
                ],
                env=env,
            )

    @unittest.skipUnless(sys.implementation.name == "graalpy", "GraalPy-specific test")
    def test_import_function_installs_pip_audit_hook(self):
        with tempfile.TemporaryDirectory(prefix="graalpy-pip-audit-import-") as tmp:
            root = Path(tmp)
            fake_pip_root = root / "fake-pip"
            fake_pip_root.mkdir()
            write_fake_pip(fake_pip_root)

            env = os.environ.copy()
            env.pop("PIP_INDEX_URL", None)
            env.pop("PIP_CACHE_DIR", None)

            subprocess.check_call(
                [
                    sys.executable,
                    "-S",
                    "-c",
                    (
                        "import sys; "
                        "from types import SimpleNamespace; "
                        "sys.path.insert(0, sys.argv[1]); "
                        "__import__('pip'); "
                        "options = SimpleNamespace(extra_index_urls=[], cache_dir=sys.argv[2]); "
                        "sys.audit('pip.cli.parse_args', options, [], 0); "
                        "assert options.extra_index_urls == ['https://www.graalvm.org/python/wheels/']; "
                        "assert options.cache_dir == sys.argv[2] + '-graalpy'"
                    ),
                    str(fake_pip_root),
                    str(root / "pip-cache"),
                ],
                env=env,
            )

    @unittest.skipUnless(sys.implementation.name == "graalpy", "GraalPy-specific test")
    def test_find_all_candidates_event_adds_metadata_sources(self):
        with tempfile.TemporaryDirectory(prefix="graalpy-pip-audit-candidates-") as tmp:
            root = Path(tmp)
            patches = root / "patches"
            fake_pip_root = root / "fake-pip"
            patches.mkdir()
            fake_pip_root.mkdir()
            write_fake_pip(fake_pip_root)

            (patches / "metadata.toml").write_text(
                "\n".join(
                    [
                        "[[demo.add-sources]]",
                        "version = '1.0.0'",
                        "url = 'https://example.invalid/demo/archive/refs/tags/v1.0.0.tar.gz'",
                    ]
                ),
                encoding="utf-8",
            )

            env = os.environ.copy()
            env["PIP_GRAALPY_PATCHES_URL"] = patches.as_uri()

            subprocess.check_call(
                [
                    sys.executable,
                    "-S",
                    "-c",
                    (
                        "import sys; "
                        "sys.path.insert(0, sys.argv[1]); "
                        "import pip; "
                        "extra = []; "
                        "sys.audit('pip.package_finder.find_all_candidates', 'demo', (), extra); "
                        "assert extra == [('demo', '1.0.0', {"
                        "'url': 'https://example.invalid/demo/archive/refs/tags/v1.0.0.tar.gz', "
                        "'filename': 'demo-1.0.0.tar.gz', "
                        "'comes_from': 'GraalPy compatibility patches', "
                        "'requires_python': None, "
                        "'yanked_reason': None})]"
                    ),
                    str(fake_pip_root),
                ],
                env=env,
            )

    @unittest.skipUnless(sys.implementation.name == "graalpy", "GraalPy-specific test")
    @unittest.skipUnless(shutil.which("patch"), "requires the patch utility")
    def test_source_ready_event_uses_link_filename_for_add_source_urls(self):
        with tempfile.TemporaryDirectory(prefix="graalpy-pip-audit-link-filename-") as tmp:
            root = Path(tmp)
            patches = root / "patches"
            source = root / "demo-1.0.0"
            fake_pip_root = root / "fake-pip"
            patches.mkdir()
            source.mkdir()
            fake_pip_root.mkdir()
            write_fake_pip(fake_pip_root)

            (patches / "metadata.toml").write_text(
                "\n".join(
                    [
                        "[[demo.rules]]",
                        "version = '== 1.0.0'",
                        "patch = 'demo.patch'",
                        "license = 'MIT'",
                    ]
                ),
                encoding="utf-8",
            )
            (patches / "demo.patch").write_text(
                "\n".join(
                    [
                        "diff --git a/demo_module.py b/demo_module.py",
                        "--- a/demo_module.py",
                        "+++ b/demo_module.py",
                        "@@ -1 +1 @@",
                        '-value = "old"',
                        '+value = "patched"',
                        "",
                    ]
                ),
                encoding="utf-8",
            )
            module = source / "demo_module.py"
            module.write_text('value = "old"\n', encoding="utf-8")

            env = os.environ.copy()
            env["PIP_GRAALPY_PATCHES_URL"] = patches.as_uri()
            env.pop("PIP_GRAALPY_DISABLE_PATCHING", None)

            subprocess.check_call(
                [
                    sys.executable,
                    "-S",
                    "-c",
                    "\n".join(
                        [
                            "import sys",
                            "sys.path.insert(0, sys.argv[1])",
                            "import pip",
                            "class Link:",
                            "    filename = 'demo-1.0.0.tar.gz'",
                            "    def __str__(self):",
                            "        return 'https://example.invalid/demo/archive/refs/tags/v1.0.0.tar.gz'",
                            "sys.audit(",
                            "    'pip.requirement.source_ready',",
                            "    'demo',",
                            "    None,",
                            "    Link(),",
                            "    sys.argv[2],",
                            "    False,",
                            "    0,",
                            ")",
                        ]
                    ),
                    str(fake_pip_root),
                    str(source),
                ],
                env=env,
            )

            self.assertEqual(module.read_text(encoding="utf-8"), 'value = "patched"\n')

    @unittest.skipUnless(sys.implementation.name == "graalpy", "GraalPy-specific test")
    def test_source_ready_event_warns_about_suggested_versions(self):
        with tempfile.TemporaryDirectory(prefix="graalpy-pip-audit-suggest-") as tmp:
            root = Path(tmp)
            patches = root / "patches"
            source = root / "demo-2.0.0"
            fake_pip_root = root / "fake-pip"
            patches.mkdir()
            source.mkdir()
            fake_pip_root.mkdir()
            write_fake_pip(fake_pip_root)

            (patches / "metadata.toml").write_text(
                "\n".join(
                    [
                        "[[demo.rules]]",
                        "version = '== 1.0.0'",
                        "patch = 'demo.patch'",
                        "license = 'MIT'",
                    ]
                ),
                encoding="utf-8",
            )
            (patches / "demo.patch").write_text("", encoding="utf-8")

            env = os.environ.copy()
            env["PIP_GRAALPY_PATCHES_URL"] = patches.as_uri()

            subprocess.check_call(
                [
                    sys.executable,
                    "-S",
                    "-c",
                    "\n".join(
                        [
                            "import sys, warnings",
                            "sys.path.insert(0, sys.argv[1])",
                            "import pip",
                            "with warnings.catch_warnings(record=True) as caught:",
                            "    warnings.simplefilter('always')",
                            "    sys.audit(",
                            "        'pip.requirement.source_ready',",
                            "        'demo',",
                            "        None,",
                            "        'https://example.invalid/demo-2.0.0.tar.gz',",
                            "        sys.argv[2],",
                            "        False,",
                            "        0,",
                            "    )",
                            "    assert any('version(s): == 1.0.0' in str(w.message) for w in caught)",
                        ]
                    ),
                    str(fake_pip_root),
                    str(source),
                ],
                env=env,
            )


if __name__ == "__main__":
    unittest.main()
