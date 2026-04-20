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

#!/usr/bin/env bash
set -euo pipefail

# Benchmarks to run (argument to runner.py)
BENCHES=(richards fibonacci sieve deltablue)

# Runner script
RUNNER="benchmarks/interpreter/runner.py"

# Where to store final hprof outputs
HPROF_DIR="hprofs"
mkdir -p "${HPROF_DIR}"

# Base flags (common to all runs)
BASE_FLAGS=(
  --vm.Dengine.Splitting=false
  --vm.Dengine.OSR=false
  --python.BuiltinsInliningMaxCallerSize=0
  --python.ForceInitializeSourceSections=true
  --python.EnableDebuggingBuiltins=true
)

# Configs:
#   name         EnableBytecodeDSLInterpreter   PyCachePrefix         output-prefix
CONFIGS=(
  "dsl|true|./dsl_cache|dsl"
  "manual|false|./manual_cache|manual"
)

# Find the newest .hprof created since a given time marker
find_newest_hprof_since() {
  local since_file="$1"
  ls -1t ./*.hprof 2>/dev/null | while read -r f; do
    if [[ "$f" -nt "$since_file" ]]; then
      echo "$f"
      return 0
    fi
  done
  return 1
}

run_mx_python() {
  local bench_name="$1"
  local enable_dsl="$2"
  local cache_prefix="$3"

  mx python \
    "${BASE_FLAGS[@]}" \
    --vm.Dpython.EnableBytecodeDSLInterpreter="${enable_dsl}" \
    --python.PyCachePrefix="${cache_prefix}" \
    "${RUNNER}" "${bench_name}"
}

# Sanity check
if [[ ! -f "${RUNNER}" ]]; then
  echo "ERROR: runner not found: ${RUNNER}" >&2
  exit 1
fi

for bench in "${BENCHES[@]}"; do
  for cfg in "${CONFIGS[@]}"; do
    IFS='|' read -r cfg_name enable_dsl cache_prefix out_prefix <<< "${cfg}"

    # 1) Warmup run (and delete its hprof)
    warmup_marker="$(mktemp)"
    rm -f ./*.hprof 2>/dev/null || true
    echo "==> Warmup (bytecode gen): ${bench} [${cfg_name}]"
    run_mx_python "${bench}" "${enable_dsl}" "${cache_prefix}"

    warmup_hprof="$(find_newest_hprof_since "${warmup_marker}" || true)"
    rm -f "${warmup_marker}"
    if [[ -n "${warmup_hprof}" ]]; then
      echo "    Deleting warmup hprof: ${warmup_hprof}"
      rm -f "${warmup_hprof}"
    else
      echo "    WARNING: No warmup .hprof found to delete (continuing)."
    fi

    # 2) Actual run (capture hprof and move/rename)
    run_marker="$(mktemp)"
    echo "==> Measured run: ${bench} [${cfg_name}]"
    run_mx_python "${bench}" "${enable_dsl}" "${cache_prefix}"

    run_hprof="$(find_newest_hprof_since "${run_marker}" || true)"
    rm -f "${run_marker}"
    if [[ -z "${run_hprof}" ]]; then
      echo "ERROR: No .hprof found after measured run for ${bench} [${cfg_name}]." >&2
      exit 1
    fi

    dest="${HPROF_DIR}/${out_prefix}_${bench}.hprof"
    echo "    Moving hprof: ${run_hprof} -> ${dest}"
    mv -f "${run_hprof}" "${dest}"
  done
done

echo "Done. HPROFs are in: ${HPROF_DIR}/"
