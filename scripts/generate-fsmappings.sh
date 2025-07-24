# Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

#!/bin/bash
if [ -z "$MUSL_TOOLCHAIN" ]; then
    echo "MUSL_TOOLCHAIN not set to point to the toolchain path."
    exit 1
fi

if [ $# -ne 1 ]; then
    echo "Usage: $0 [TARGET_CONFIG_FILE]"
    exit 1
fi

PARENT_DIR=$(dirname "$(readlink -f "${BASH_SOURCE[0]}" | xargs dirname)")
OUTFILE=$(readlink -f "$1")

cat <<EOF> $OUTFILE
{
    "allowed_ports": [],
    "env": {},
    "working_dir": "/proc",
    "testing_default_mappings": true,
    "fsmappings": [
        {
            "concrete": "${PARENT_DIR}/mxbuild/linux-amd64/GRAALPY_NATIVE_STANDALONE",
            "mutable": false,
            "verif": false,
            "virt": "/proc"
        },
        {
            "concrete": "${PARENT_DIR}/mxbuild/linux-amd64/GRAALPY_NATIVE_STANDALONE/bin",
            "mutable": false,
            "verif": false,
            "virt": "/proc/self"
        },
        {
            "concrete": "${PARENT_DIR}/mxbuild/linux-amd64/GRAALPY_NATIVE_STANDALONE/bin/graalpy",
            "verif": true,
            "virt": "/proc/self/exe"
        },
EOF

pushd "${PARENT_DIR}/mxbuild/linux-amd64/GRAALPY_NATIVE_STANDALONE/" >/dev/null
for i in `find . -name "*.so"`; do
    cat <<EOF>> $OUTFILE
        {
            "concrete": "${PARENT_DIR}/mxbuild/linux-amd64/GRAALPY_NATIVE_STANDALONE/$i",
            "verif": true,
            "virt": "/proc/$i"
        },
EOF
done
popd >/dev/null

cat <<EOF>> $OUTFILE
        {
            "concrete": "${MUSL_TOOLCHAIN}/lib/libc++.so.1.0",
            "verif": true,
            "virt": "/proc/lib/libc++.so.1.0"
        },
        {
            "concrete": "${MUSL_TOOLCHAIN}/lib/libc++abi.so.1.0",
            "verif": true,
            "virt": "/proc/lib/libc++abi.so.1.0"
        },
        {
            "concrete": "${MUSL_TOOLCHAIN}/lib/libunwind.so.1.0",
            "verif": true,
            "virt": "/proc/lib/libunwind.so.1.0"
        }
    ]
}
EOF
