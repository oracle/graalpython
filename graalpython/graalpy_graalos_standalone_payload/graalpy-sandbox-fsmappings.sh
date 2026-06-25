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

json_escape() {
    printf '%s' "$1" | sed 's/\\/\\\\/g; s/"/\\"/g'
}

emit_mapping() {
    local outfile="$1"
    local concrete="$2"
    local virt="$3"
    local extra="${4:-}"
    if [ "$need_comma" = "true" ]; then
        printf ',\n' >> "$outfile"
    fi
    need_comma=true
    {
        printf '    {\n'
        printf '      "concrete": "%s",\n' "$(json_escape "$concrete")"
        if [ -n "$extra" ]; then
            printf '%s\n' "$extra"
        fi
        printf '      "virt": "%s"\n' "$(json_escape "$virt")"
        printf '    }'
    } >> "$outfile"
}

emit_pseudo_mapping() {
    local outfile="$1"
    local concrete="$2"
    local virt="$3"
    local mutable="${4:-false}"
    local extra
    extra='      "using": {"handler": "pseudo_fs"},'
    if [ "$mutable" = "true" ]; then
        extra="${extra}"$'\n''      "mutable": true,'
    fi
    emit_mapping "$outfile" "$concrete" "$virt" "$extra"
}

emit_musl_interpreter_mapping() {
    local outfile="$1"
    local executable="$2"
    local safe_libc="$3"
    local interp

    if ! command -v readelf >/dev/null 2>&1; then
        return 0
    fi

    interp="$(readelf -l "$executable" 2>/dev/null | sed -n 's/.*Requesting program interpreter: \([^]]*\).*/\1/p' | head -n 1)"
    case "$interp" in
        /*)
            case "${emitted_musl_interpreters:-}" in
                *"
${interp}
"*) return 0 ;;
            esac
            emitted_musl_interpreters="${emitted_musl_interpreters:-}
${interp}
"
            emit_mapping "$outfile" "$safe_libc" "$interp" '      "verif": true,'
            ;;
    esac
}

graalpy_sandbox_emit_fsmappings() {
    local standalone_home="$1"
    local outfile="$2"
    local sysroot_lib="${GRAALPY_SANDBOX_SYSROOT_LIB_DIR:-${standalone_home}/lib/graalos/sysroot/lib}"
    local safe_libc="${GRAALPY_SANDBOX_SAFE_LIBC:-${standalone_home}/lib/graalos/libc.so}"
    local native_bin="${GRAALPY_SANDBOX_NATIVE_BIN_DIR:-${standalone_home}/lib/graalos/native-bin}"

    if [ ! -d "$standalone_home" ]; then
        echo "missing standalone home: $standalone_home" >&2
        return 126
    fi

    if [ ! -f "$safe_libc" ]; then
        echo "missing GraalHost safe libc: $safe_libc" >&2
        return 126
    fi

    need_comma=false
    emitted_musl_interpreters=""
    emit_mapping "$outfile" "$standalone_home" "/" '      "using": {"handler": "host_fs"},
      "mutable": true,
      "allow_set_x_bit": true,
      "verif": false,'
    emit_pseudo_mapping "$outfile" "/etc/passwd" "/etc/passwd"
    emit_pseudo_mapping "$outfile" "/proc" "/proc"
    emit_mapping "$outfile" "/proc/sys/vm/max_map_count" "/proc/sys/vm/max_map_count" '      "verif": true,'
    emit_pseudo_mapping "$outfile" "/dev" "/dev" true

    if [ -d "$native_bin" ]; then
        while IFS= read -r file; do
            virt="/bin/${file#"$native_bin"/}"
            emit_mapping "$outfile" "$file" "$virt" '      "verif": true,'
            emit_musl_interpreter_mapping "$outfile" "$file" "$safe_libc"
        done < <(find "$native_bin" -maxdepth 1 -type f -perm -111 | sort)
    else
        while IFS= read -r file; do
            virt="/${file#"$standalone_home"/}"
            emit_mapping "$outfile" "$file" "$virt" '      "verif": true,'
            emit_musl_interpreter_mapping "$outfile" "$file" "$safe_libc"
        done < <(find "${standalone_home}/bin" -maxdepth 1 -type f -perm -111 | sort)
    fi

    while IFS= read -r file; do
        virt="/${file#"$standalone_home"/}"
        emit_mapping "$outfile" "$file" "$virt" '      "verif": true,'
    done < <(find "$standalone_home" -type f \( -name "*.so" -o -name "*.so.*" \) ! -path "${standalone_home}/lib/graalos/*" | sort)

    for lib in libc++.so libc++.so.1 libc++.so.1.0 libc++abi.so libc++abi.so.1 libc++abi.so.1.0 libunwind.so libunwind.so.1 libunwind.so.1.0; do
        if [ -e "${sysroot_lib}/${lib}" ]; then
            emit_mapping "$outfile" "${sysroot_lib}/${lib}" "/lib/${lib}" '      "verif": true,'
        fi
    done

    emit_mapping "$outfile" "$safe_libc" "/lib/libc.so" '      "verif": true,'
    emit_mapping "$outfile" "$safe_libc" "/lib/ld-musl-x86_64.so.1" '      "verif": true,'
}
