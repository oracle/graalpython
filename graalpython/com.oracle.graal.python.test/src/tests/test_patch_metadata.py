# Copyright (c) 2023, 2024, Oracle and/or its affiliates. All rights reserved.
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

import sys

SECTIONS = frozenset({'rules', 'add-sources'})
RULE_KEYS = frozenset({'version', 'patch', 'subdir', 'dist-type', 'install-priority', 'ignore-rule-on-llvm'})

if sys.implementation.name == 'graalpy':
    import ensurepip

    bundled_dir = Path(ensurepip.__file__).parent / '_bundled'
    pip_wheel = next(bundled_dir.glob('pip-*.whl'))
    sys.path.append(str(pip_wheel))
    from pip._vendor import tomli
    from pip._vendor.packaging.specifiers import SpecifierSet

    patch_dir = Path(__graalpython__.core_home) / 'patches'


    def validate_metadata(package_dir, metadata):
        if unexpected_keys := set(metadata) - SECTIONS:
            assert False, f"Unexpected top-level metadata keys: {unexpected_keys}"
        patches = set()
        if rules := metadata.get('rules'):
            for rule in rules:
                if unexpected_keys := set(rule) - RULE_KEYS:
                    assert False, f"Unexpected rule keys: {unexpected_keys}"
                if patch := rule.get('patch'):
                    patch_path = package_dir / patch
                    assert patch_path.is_file(), f"Patch file does not exists: {patch_path}"
                    patches.add(patch_path)
                if install_priority := rule.get('install-priority'):
                    assert isinstance(install_priority, int), "'rules.install_priority' must be an int"
                if dist_type := rule.get('dist-type'):
                    assert dist_type in ('wheel', 'sdist'), "'rules.dist_type' must be on of 'wheel', 'sdist'"
                if version := rule.get('version'):
                    # Just try that it doesn't raise
                    SpecifierSet(version)
                if ignore_on_llvm := rule.get('ignore-rule-on-llvm'):
                    assert isinstance(ignore_on_llvm, bool)
        for file in package_dir.iterdir():
            assert file.name == 'metadata.toml' or file in patches, f"Dangling file in patch directory: {file}"
        if add_sources := metadata.get('add-sources'):
            for add_source in add_sources:
                if unexpected_keys := set(add_source) - {'version', 'url'}:
                    assert False, f"Unexpected add_source keys: {unexpected_keys}"
                assert add_source.get('version'), f"Missing 'add_sources.version' key in {package_dir}"
                assert add_source.get('url'), f"Missing 'add_sources.url' key in {package_dir}"


    def test_patch_metadata():
        for package_dir in patch_dir.iterdir():
            if package_dir.is_dir():
                if (metadata_path := package_dir / 'metadata.toml').is_file():
                    with open(metadata_path, 'rb') as f:
                        metadata = tomli.load(f)
                        validate_metadata(package_dir, metadata)
                else:
                    assert False, f"Patch directory without metadata: {package_dir}"
