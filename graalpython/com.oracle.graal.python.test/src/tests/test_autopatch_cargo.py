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

import hashlib
import json
import os
import shutil
import subprocess
import sys
import tarfile
import tempfile
import textwrap
import tomllib
import unittest
from pathlib import Path
from unittest.mock import patch


if sys.implementation.name == "graalpy":
    import autopatch_cargo


    class AutoPatchCargoTest(unittest.TestCase):
        def setUp(self):
            self.tempdir = Path(tempfile.mkdtemp())
            self.cargo_home = self.tempdir / "cargo-home"
            self.workspace = self.tempdir / "project"
            self.patch_dir = self.tempdir / "patches"
            self.workspace.mkdir()
            self.patch_dir.mkdir()
            self.env_patch = patch.dict(os.environ, {"CARGO_HOME": str(self.cargo_home)})
            self.env_patch.start()

        def tearDown(self):
            self.env_patch.stop()
            shutil.rmtree(self.tempdir)

        @staticmethod
        def write(path, contents):
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(textwrap.dedent(contents).lstrip())

        def test_version_specifiers(self):
            contains = autopatch_cargo._specifier_contains
            assert contains(">=0.28,<0.29", "0.28.0")
            assert contains(">=0.28,<0.29", "0.28.3")
            assert not contains(">=0.28,<0.29", "0.27.2")
            assert not contains(">=0.28,<0.29", "0.29.0")
            assert contains("==0.29.0", "0.29")
            assert contains(">0.28.1,!=0.28.2,<=0.28.3", "0.28.3")
            assert not contains(">=0.28", "0.29.0-alpha.1")
            with self.assertRaises(ValueError):
                contains("~=0.28", "0.28.3")

        def prepare_cached_crate(self, name="made-up-crate", version="1.2.3"):
            crate = self.cargo_home / "registry" / "src" / "made-up-index" / f"{name}-{version}"
            self.write(
                crate / "Cargo.toml",
                f"""
                [package]
                name = "{name}"
                version = "{version}"
                edition = "2021"
                """,
            )
            self.write(crate / "src" / "lib.rs", 'pub fn message() -> &\'static str { "unpatched" }\n')
            return crate

        def prepare_repository(self, version=">=1,<2"):
            self.write(
                self.patch_dir / "metadata.toml",
                f"""
                [[made-up-crate.rules]]
                version = "{version}"
                patch = "made-up-crate.patch"
                license = "MIT"
                """,
            )
            self.write(
                self.patch_dir / "made-up-crate.patch",
                """
                --- a/src/lib.rs
                +++ b/src/lib.rs
                @@ -1 +1 @@
                -pub fn message() -> &'static str { "unpatched" }
                +pub fn message() -> &'static str { "patched" }
                """,
            )
            return autopatch_cargo.DirectoryPatchRepository(self.patch_dir)

        def prepare_workspace(self, checksum=None):
            if checksum is None:
                checksum = "0" * 64
            existing_patch = self.workspace / "existing-patch"
            self.write(
                existing_patch / "Cargo.toml",
                """
                [package]
                name = "existing-patch"
                version = "1.0.0"
                edition = "2021"
                """,
            )
            self.write(existing_patch / "src" / "lib.rs", "")
            self.write(
                self.workspace / "Cargo.toml",
                """
                [package]
                name = "made-up-project"
                version = "0.1.0"
                edition = "2021"

                [dependencies]
                made-up-crate = "=1.2.3"
                existing-patch = "=1.0.0"

                [patch.crates-io]
                existing-patch = { path = "existing-patch" }
                """,
            )
            self.write(self.workspace / "src" / "lib.rs", "pub use made_up_crate::message;\n")
            self.write(
                self.workspace / "Cargo.lock",
                """
                version = 4

                [[package]]
                name = "existing-patch"
                version = "1.0.0"

                [[package]]
                name = "made-up-crate"
                version = "1.2.3"
                source = "registry+https://github.com/rust-lang/crates.io-index"
                checksum = "{checksum}"

                [[package]]
                name = "made-up-project"
                version = "0.1.0"
                dependencies = [
                 "existing-patch",
                 "made-up-crate",
                ]
                """.format(checksum=checksum),
            )

        def test_patches_locked_crate_and_adds_cargo_override(self):
            cached_crate = self.prepare_cached_crate()
            repository = self.prepare_repository()
            self.prepare_workspace()

            assert autopatch_cargo.auto_patch_tree(self.workspace, repository) == 1
            patched_crate = self.workspace / ".graalpy" / "crates" / "made-up-crate-1.2.3"
            assert '"patched"' in (patched_crate / "src" / "lib.rs").read_text()
            assert '"unpatched"' in (cached_crate / "src" / "lib.rs").read_text()

            manifest = tomllib.loads((self.workspace / "Cargo.toml").read_text())
            cargo_patches = manifest["patch"]["crates-io"]
            assert cargo_patches["existing-patch"]["path"] == "existing-patch"
            override = cargo_patches["graalpy-made-up-crate-1_2_3"]
            assert override == {
                "package": "made-up-crate",
                "path": ".graalpy/crates/made-up-crate-1.2.3",
            }
            lock_packages = tomllib.loads((self.workspace / "Cargo.lock").read_text())["package"]
            locked_crate = next(package for package in lock_packages if package["name"] == "made-up-crate")
            assert "source" not in locked_crate
            assert "checksum" not in locked_crate

            if cargo := shutil.which("cargo"):
                metadata = subprocess.run(
                    [cargo, "metadata", "--format-version", "1", "--locked", "--offline"],
                    cwd=self.workspace,
                    check=True,
                    stdout=subprocess.PIPE,
                    text=True,
                )
                packages = json.loads(metadata.stdout)["packages"]
                crate = next(package for package in packages if package["name"] == "made-up-crate")
                assert "/.graalpy/crates/made-up-crate-1.2.3/Cargo.toml" in crate["manifest_path"]

            assert autopatch_cargo.auto_patch_tree(self.workspace, repository) == 0

        def test_downloads_and_verifies_uncached_crate(self):
            crate = self.tempdir / "archive-source" / "made-up-crate-1.2.3"
            self.write(
                crate / "Cargo.toml",
                """
                [package]
                name = "made-up-crate"
                version = "1.2.3"
                edition = "2021"
                """,
            )
            self.write(crate / "src" / "lib.rs", 'pub fn message() -> &\'static str { "unpatched" }\n')
            archive = self.tempdir / "made-up-crate-1.2.3.crate"
            with tarfile.open(archive, "w:gz") as tar:
                tar.add(crate, arcname=crate.name)
            checksum = hashlib.sha256(archive.read_bytes()).hexdigest()
            repository = self.prepare_repository()
            self.prepare_workspace(checksum)

            with open(archive, "rb") as response:
                with patch.object(autopatch_cargo.urllib.request, "urlopen", return_value=response) as urlopen:
                    assert autopatch_cargo.auto_patch_tree(self.workspace, repository) == 1

            urlopen.assert_called_once()
            patched_crate = self.workspace / ".graalpy" / "crates" / "made-up-crate-1.2.3"
            assert '"patched"' in (patched_crate / "src" / "lib.rs").read_text()

        def test_ignores_crate_outside_registered_version_range(self):
            self.prepare_cached_crate()
            repository = self.prepare_repository(version=">=2")
            self.prepare_workspace()

            assert autopatch_cargo.auto_patch_tree(self.workspace, repository) == 0
            assert not (self.workspace / ".graalpy").exists()
            assert "graalpy-made-up-crate" not in (self.workspace / "Cargo.toml").read_text()
