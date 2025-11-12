#!/bin/bash
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

# Lists all the amd64-linux tags that are in the baseline tagged files
# and not here, i.e., the TODO list for Bytecode DSL interpreter

parent_dir=$(dirname "$(readlink -f "$0")")
parent2_dir=$(dirname "$parent_dir")
baseline_dir="$parent2_dir/unittest_tags/"

for path in $(ls $parent_dir/*.txt); do
    f=$(basename "$path")
    if ! diff $baseline_dir/$f $f > /dev/null; then
      comm -3 <(grep 'linux-x86_64' $baseline_dir/$f | egrep -v '^(#|!)' | sed 's/@.*//' | sort) <(grep 'linux-x86_64' $f | egrep -v '^(#|!)' | sed 's/@.*//' | sort) > /tmp/log 2>&1
      if [ -s "/tmp/log" ]; then
        echo "----------- $f"
        cat "/tmp/log"
      fi
    fi
done

for path in $(ls $baseline_dir/*.txt); do
    f=$(basename "$path")
    if [ ! -f "$parent_dir/$f" ]; then
        echo "COMPLETE SUITE: $f"
    fi
done
