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
tmp_root="${standalone_home}/tmp"
launcher_verbose=false
launcher_show_help=false

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

mkdir -p "$tmp_root"
tmp_base="${TMPDIR:-}"
if [ -z "$tmp_base" ] || [ ! -d "$tmp_base" ]; then
    tmp_base="$tmp_root"
fi

tmpdir="$(mktemp -d "${tmp_base}/graalpy-sandbox.XXXXXXXXXX")"
trap 'rm -rf "$tmpdir"' EXIT
endpoint_config="${tmpdir}/config.json"
"$expand_config" "$standalone_home" "$config" "$endpoint_config"

graalhost_config="$(
    awk '
        /"graalhost"[[:space:]]*:/ { in_obj = 1 }
        in_obj { print }
        in_obj && /^[[:space:]]*}[[:space:]]*,?[[:space:]]*$/ { exit }
    ' "$config"
)"

print_graalhost_help() {
    cat <<'EOF'

Additional graalhost launcher options:
  --graalhost.verbose
      Enable graalhost verbose logging on stderr for this launch.
  --graalhost.run_snapshot=PATH
      Restore and run a GraalOS snapshot instead of starting a new Python process.
  --graalhost.log_level=LEVEL
      Override graalhost log level for this launch.
  --graalhost.log_to=DEST
      Override graalhost log sink(s) for this launch.
  --graalhost.visorcalloutput=DEST
      Override graalhost visorcall logging destination for this launch.
  --graalhost.seccomp=MODE
      Override graalhost seccomp mode for this launch.
  --graalhost.extra_arg=ARG
      Append one raw graalhost argument for this launch. May be repeated.
EOF
}

extract_graalhost_string() {
    local key="$1"
    printf '%s\n' "$graalhost_config" | sed -n "s/^[[:space:]]*\"${key}\"[[:space:]]*:[[:space:]]*\"\\([^\"]*\\)\".*/\\1/p" | head -n 1
}

extract_graalhost_scalar() {
    local key="$1"
    printf '%s\n' "$graalhost_config" | sed -n "s/^[[:space:]]*\"${key}\"[[:space:]]*:[[:space:]]*\\([^,][^,}]*\\).*/\\1/p" | head -n 1 | sed 's/[[:space:]]*$//'
}

extract_graalhost_array() {
    local key="$1"
    printf '%s\n' "$graalhost_config" | awk -v key="$key" '
        $0 ~ ("\"" key "\"[[:space:]]*:[[:space:]]*\\[") {
            in_arr = 1
            line = substr($0, index($0, "[") + 1)
        }
        in_arr {
            if (!length(line)) {
                line = $0
            }
            while (match(line, /"([^"]*)"/)) {
                print substr(line, RSTART + 1, RLENGTH - 2)
                line = substr(line, RSTART + RLENGTH)
            }
            line = ""
            if ($0 ~ /\]/) {
                exit
            }
        }
    '
}

graalhost_args=()
python_args=()
cli_run_snapshot=""
cli_log_level=""
cli_log_to=""
cli_visorcalloutput=""
cli_seccomp=""
cli_extra_args=()
for arg in "$@"; do
    case "$arg" in
        --graalhost.verbose)
            launcher_verbose=true
            ;;
        --graalhost.run_snapshot=*)
            cli_run_snapshot="${arg#--graalhost.run_snapshot=}"
            ;;
        --graalhost.log_level=*)
            cli_log_level="${arg#--graalhost.log_level=}"
            ;;
        --graalhost.log_to=*)
            cli_log_to="${arg#--graalhost.log_to=}"
            ;;
        --graalhost.visorcalloutput=*)
            cli_visorcalloutput="${arg#--graalhost.visorcalloutput=}"
            ;;
        --graalhost.seccomp=*)
            cli_seccomp="${arg#--graalhost.seccomp=}"
            ;;
        --graalhost.extra_arg=*)
            cli_extra_args+=("${arg#--graalhost.extra_arg=}")
            ;;
        -h|--help)
            launcher_show_help=true
            python_args+=("$arg")
            ;;
        *)
            python_args+=("$arg")
            ;;
    esac
done

if [ "$launcher_verbose" = "true" ]; then
    graalhost_args+=(--verbose --log_to stderr)
else
    graalhost_args+=(--log_level off --log_to visorbase --visorcalloutput @none)
fi

seccomp="$(extract_graalhost_scalar seccomp)"
case "$seccomp" in
    "" | "null") ;;
    *) graalhost_args+=(--seccomp "$seccomp") ;;
esac

if [ "$launcher_verbose" != "true" ]; then
    log_level="$(extract_graalhost_string log_level)"
    log_to="$(extract_graalhost_string log_to)"
    visorcalloutput="$(extract_graalhost_string visorcalloutput)"
else
    log_level=""
    log_to=""
    visorcalloutput=""
fi

if [ -n "$log_level" ]; then
    graalhost_args+=(--log_level "$log_level")
fi

if [ -n "$log_to" ]; then
    graalhost_args+=(--log_to "$log_to")
fi

if [ -n "$visorcalloutput" ]; then
    graalhost_args+=(--visorcalloutput "$visorcalloutput")
fi

while IFS= read -r extra_arg; do
    [ -n "$extra_arg" ] || continue
    graalhost_args+=("$extra_arg")
done < <(extract_graalhost_array extra_args)

if [ -n "$cli_seccomp" ]; then
    graalhost_args+=(--seccomp "$cli_seccomp")
fi

if [ -n "$cli_log_level" ]; then
    graalhost_args+=(--log_level "$cli_log_level")
fi

if [ -n "$cli_log_to" ]; then
    graalhost_args+=(--log_to "$cli_log_to")
fi

if [ -n "$cli_visorcalloutput" ]; then
    graalhost_args+=(--visorcalloutput "$cli_visorcalloutput")
fi

for extra_arg in "${cli_extra_args[@]}"; do
    graalhost_args+=("$extra_arg")
done

set +e
if [ -n "$cli_run_snapshot" ]; then
    if [ "${#python_args[@]}" -gt 0 ]; then
        echo "--graalhost.run_snapshot cannot be combined with Python arguments" >&2
        status=2
    else
        "$graalhost" \
            ${graalhost_args[@]+"${graalhost_args[@]}"} \
            --musl_path "$libc" \
            --run "$cli_run_snapshot"
        status=$?
    fi
else
    "$graalhost" \
        ${graalhost_args[@]+"${graalhost_args[@]}"} \
        --musl_path "$libc" \
        --run_config=@"$endpoint_config" \
        --run_virtual "$virtual_executable" \
        "${python_args[@]}"
    status=$?
fi
set -e

if [ "$launcher_show_help" = "true" ]; then
    print_graalhost_help
fi

exit "$status"
