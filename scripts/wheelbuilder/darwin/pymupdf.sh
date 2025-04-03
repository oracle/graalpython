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

if [ -n "$GITHUB_RUN_ID" ]; then
    brew install python@3.11
    # Make sure homebrew Python 3.11 is first on PATH, not the GraalPy symlink
    # Also, BSD patch fails to apply our no-context pymupdf patch, so use
    # python-patch-ng
    $HOMEBREW_PREFIX/bin/python3.11 -m venv 311venv
    311venv/bin/pip install git+https://github.com/timfel/python-patch-ng
    311venv/bin/pip uninstall -y pip
    export PATH="$(pwd)/311venv/bin:$PATH"
fi

mkdir cc_bin
export PATH="$(pwd)/cc_bin:$PATH"
# darwin's linker does not support --gc-sections but mupdf passes that we wrap
# cc and pass all arguments on, but we remove the argument -Wl,--gc-sections
original_cc=`which cc`
cat <<EOF> cc_bin/cc
#!/bin/bash
# Wrapper for cc that removes --gc-sections from command line arguments if present
# Pass on all arguments, but remove -Wl,--gc-sections if it is given

if [[ "\$@" == *"-Wl,--gc-sections"* ]]; then
  echo "Removing -Wl,--gc-sections argument from command line..."
  newargs=()
  for arg in "\$@"; do
    if [ \$arg != "-Wl,--gc-sections" ]; then
      newargs+=("\$arg")
    fi
  done
  exec $original_cc "\${newargs[@]}"
else
  exec $original_cc "\$@"
fi
EOF
chmod +x cc_bin/cc
export CC="$(pwd)/cc_bin/cc"

export USE_SONAME="no"

if [ -n "$1" ]; then
    pip wheel "pymupdf==$1"
else
    pip wheel pymupdf
fi

rm -rf cc_bin

if [ -n "$GITHUB_RUN_ID" ]; then
    rm -rf 311venv
fi
