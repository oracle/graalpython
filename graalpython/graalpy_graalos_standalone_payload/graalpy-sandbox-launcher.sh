#!/usr/bin/env bash
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

set -euo pipefail

if [ "$#" -lt 1 ]; then
    echo "usage: $0 VIRTUAL_EXECUTABLE [ARGS...]" >&2
    exit 2
fi

virtual_executable="$1"
shift

script="${BASH_SOURCE[0]}"
while [ -L "$script" ]; do
    script_dir="$(cd "$(dirname "$script")" && pwd -P)"
    script="$(readlink "$script")"
    case "$script" in
        /*) ;;
        *) script="${script_dir}/${script}" ;;
    esac
done

script_dir="$(cd "$(dirname "$script")" && pwd -P)"
standalone_home="$(cd "${script_dir}/../.." && pwd -P)"
graalhost="${standalone_home}/lib/graalos/graalhost"
libc="${standalone_home}/lib/graalos/libc.so"
expand_config="${standalone_home}/lib/graalos/graalpy-sandbox-expand-config"
config="${standalone_home}/config.json"

if [ ! -x "$graalhost" ]; then
    echo "missing or non-executable GraalHost binary: $graalhost" >&2
    exit 126
fi

if [ ! -f "$libc" ]; then
    echo "missing GraalHost libc: $libc" >&2
    exit 126
fi

if [ ! -x "$expand_config" ]; then
    echo "missing or non-executable sandbox config expander: $expand_config" >&2
    exit 126
fi

if [ ! -f "$config" ]; then
    echo "missing sandbox config: $config" >&2
    exit 126
fi

tmpdir="$(mktemp -d "${TMPDIR:-/tmp}/graalpy-sandbox.XXXXXXXXXX")"
trap 'rm -rf "$tmpdir"' EXIT
endpoint_config="${tmpdir}/config.json"
"$expand_config" "$standalone_home" "$config" "$endpoint_config"

graalhost_args=()
log_level="$(sed -n 's/^[[:space:]]*"log_level"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p' "$config" | head -n 1)"
if [ -n "$log_level" ]; then
    graalhost_args+=(--log_level "$log_level")
fi

exec "$graalhost" \
    ${graalhost_args[@]+"${graalhost_args[@]}"} \
    --musl_path "$libc" \
    --run_config=@"$endpoint_config" \
    --run_virtual "$virtual_executable" \
    "$@"
