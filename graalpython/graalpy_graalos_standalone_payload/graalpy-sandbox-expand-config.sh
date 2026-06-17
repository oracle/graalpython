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

if [ "$#" -ne 3 ]; then
    echo "usage: $0 STANDALONE_HOME CONFIG_JSON OUTFILE" >&2
    exit 2
fi

standalone_home="$(cd "$1" && pwd -P)"
config_json="$2"
outfile="$3"
helper_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd -P)"
. "${helper_dir}/graalpy-sandbox-fsmappings"

if [ ! -d "$standalone_home" ]; then
    echo "missing standalone home: $standalone_home" >&2
    exit 126
fi

if [ ! -f "$config_json" ]; then
    echo "missing sandbox config: $config_json" >&2
    exit 126
fi

tmp_prefix="${outfile}.tmp"
trap 'rm -f "${tmp_prefix}.head" "${tmp_prefix}.tail"' EXIT

awk '
    BEGIN { skipping = 0; skipping_graalhost = 0; replaced = 0 }
    /^[[:space:]]*"graalhost"[[:space:]]*:/ {
        skipping_graalhost = 1
        if ($0 ~ /\}[[:space:]]*,?[[:space:]]*$/) {
            skipping_graalhost = 0
        }
        next
    }
    skipping_graalhost {
        if ($0 ~ /^[[:space:]]*\}[[:space:]]*,?[[:space:]]*$/) {
            skipping_graalhost = 0
        }
        next
    }
    /^[[:space:]]*"fsmappings"[[:space:]]*:/ {
        print "  \"fsmappings\": ["
        print "@@GRAALPY_SANDBOX_FSMAPPINGS@@"
        skipping = 1
        replaced = 1
        if ($0 ~ /\]/) {
            skipping = 0
        }
        next
    }
    skipping {
        if ($0 ~ /\]/) {
            skipping = 0
        }
        next
    }
    { print }
    END {
        if (!replaced) {
            exit 42
        }
    }
' "$config_json" > "${tmp_prefix}.head" || {
    status=$?
    if [ "$status" -eq 42 ]; then
        echo "sandbox config must contain a top-level fsmappings array" >&2
    fi
    exit "$status"
}

before_marker="${tmp_prefix}.head"
sed -n '1,/@@GRAALPY_SANDBOX_FSMAPPINGS@@/p' "$before_marker" | sed '$d' > "$outfile"

graalpy_sandbox_emit_fsmappings "$standalone_home" "$outfile"

printf '\n  ]\n' >> "$outfile"
sed -n '/@@GRAALPY_SANDBOX_FSMAPPINGS@@/,$p' "$before_marker" | sed '1d' >> "$outfile"
