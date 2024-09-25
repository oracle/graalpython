#!/bin/bash
# Copyright (c) 2022, 2024, Oracle and/or its affiliates. All rights reserved.
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
set -eo pipefail

GIT_DIR="$(realpath "$(dirname "$0")/..")"
BUNDLED_DIR="graalpython/lib-python/3/ensurepip/_bundled"

check_file() {
    if [ ! -f "$1" ]; then
        echo "File $1 does not exist"
        exit 1
    fi
}

missing_python_import() {
    echo "Error when getting the vanilla wheel from the python-import branch"
    echo "Do you have local branch named 'python-import'?"
    echo "If not, you can use: git fetch origin python-import:python-import"
    exit 1
}

patch_wheel() {
    cd "$GIT_DIR"
    local name="$1"
    local patch="$2"
    local wheel="$(echo $BUNDLED_DIR/$name-*.whl)"
    check_file "$patch"
    check_file "$wheel"
    local tmpdir="$(basename -s '.whl' "$wheel")"
    rm -rf "$tmpdir"
    mkdir "$tmpdir"
    git show "python-import:$wheel" > tmp.whl || missing_python_import
    cd "$tmpdir"
    unzip ../tmp.whl
    rm ../tmp.whl
    patch -p1 < "../$patch"
    touch "$(echo $name-*.dist-info)/GRAALPY_MARKER"
    rm "../$wheel"
    zip -r "../$wheel" .
    rm -rf "$tmpdir"
}

patch_wheel pip graalpython/lib-graalpython/patches/pip-23.2.1.patch
