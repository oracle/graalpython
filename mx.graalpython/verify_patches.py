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
import argparse
import re
import sys
from pathlib import Path

from pip._vendor import tomli  # pylint: disable=no-name-in-module
from pip._vendor.packaging.specifiers import SpecifierSet  # pylint: disable=no-name-in-module

# Approved license identifiers in SPDX "short identifier" format
ALLOWED_LICENSES = {
    'MIT',  # https://spdx.org/licenses/MIT.html
    'BSD-3-Clause',  # https://spdx.org/licenses/BSD-3-Clause.html
    'BSD-2-Clause',  # https://spdx.org/licenses/BSD-2-Clause.html
    'Apache-2.0',  # https://spdx.org/licenses/Apache-2.0.html
    'MPL-2.0',  # https://spdx.org/licenses/MPL-2.0.html
    'LGPL-2.0-or-later',  # https://spdx.org/licenses/LGPL-2.0-or-later.html
    'LGPL-3.0-or-later',  # https://spdx.org/licenses/LGPL-3.0-or-later.html
    'PSF-2.0',  # https://spdx.org/licenses/PSF-2.0.html
}
ALLOWED_WITH_CLAUSES = {
    'openssl-exception',
}

SECTIONS = frozenset({'rules', 'add-sources'})
RULE_KEYS = frozenset({'version', 'patch', 'license', 'subdir', 'dist-type', 'install-priority'})


def validate_metadata(patches_dir):
    with open(patches_dir / 'metadata.toml', 'rb') as f:
        all_metadata = tomli.load(f)
    patches = set()
    for package, metadata in all_metadata.items():
        try:
            if unexpected_keys := set(metadata) - SECTIONS:
                assert False, f"Unexpected top-level metadata keys: {unexpected_keys}"
            if rules := metadata.get('rules'):
                for rule in rules:
                    if unexpected_keys := set(rule) - RULE_KEYS:
                        assert False, f"Unexpected rule keys: {unexpected_keys}"
                    if patch := rule.get('patch'):
                        patch_path = patches_dir / patch
                        assert patch_path.is_file(), f"Patch file does not exists: {patch_path}"
                        patches.add(patch_path)
                        license_id = rule.get('license')
                        assert license_id, f"'license' not specified for patch {patch}"
                        license_id = re.sub(r'[()]', ' ', license_id)
                        for part in re.split(f'AND|OR', license_id):
                            part = part.strip()
                            if ' WITH ' in part:
                                part, exception = re.split(r'\s+WITH\s+', part, 1)
                                assert exception in ALLOWED_WITH_CLAUSES, \
                                    f"License WITH clause {exception} not in allowed list of clauses: {', '.join(ALLOWED_WITH_CLAUSES)}"
                            assert part in ALLOWED_LICENSES, \
                                f"License {part} not in allowed list of licenses: {', '.join(ALLOWED_LICENSES)}"
                    if install_priority := rule.get('install-priority'):
                        assert isinstance(install_priority, int), "'rules.install_priority' must be an int"
                    if dist_type := rule.get('dist-type'):
                        assert dist_type in ('wheel', 'sdist'), "'rules.dist_type' must be on of 'wheel', 'sdist'"
                    if version := rule.get('version'):
                        # Just try that it doesn't raise
                        SpecifierSet(version)
            if add_sources := metadata.get('add-sources'):
                for add_source in add_sources:
                    if unexpected_keys := set(add_source) - {'version', 'url'}:
                        assert False, f"Unexpected add_source keys: {unexpected_keys}"
                    assert add_source.get('version'), "Missing 'add_sources.version' key"
                    assert add_source.get('url'), "Missing 'add_sources.url' key"
        except Exception as e:
            raise AssertionError(f"{package}: {e}")
    for file in patches_dir.iterdir():
        assert not file.name.endswith('patch') or file in patches, f"Dangling patch file: {file}"


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('patches_dir', type=Path)

    args = parser.parse_args()

    validate_metadata(args.patches_dir)


if __name__ == '__main__':
    main()
