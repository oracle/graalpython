# Copyright (c) 2024, 2025, Oracle and/or its affiliates. All rights reserved.
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
    dnf install -y /usr/bin/c++ /usr/bin/make /usr/bin/which

    # Make sure system Python is first on PATH, not the GraalPy symlink :(
    # Installing a new Python3.11 might break the outer pip, so we just symlink
    # whatever the outer python is :((
    current_path="$PATH"
    builder_pip=`which pip`
    builder_pip_dirname=`dirname $pip`
    path_without_builder_venv=`echo $PATH | sed "s#$pip_dirname##"`
    export PATH="$path_without_builder_venv"
    outer_python3=`which python3`
    outer_python3_dir=`dirname $outer_python3_dir`
    rm -f "${pip_dirname}/python3.11"
    if which python3.11; then
        echo "Python3.11 is available"
    else
        echo "Symlinking $outer_python3 to be Python3.11"
        ln -sf "$outer_python3" "${outer_python3_dir}/python3.11"
    fi
fi

export USE_SONAME="no"

if [ -n "$1" ]; then
    pip wheel "pymupdf==$1"
else
    pip wheel pymupdf
fi
