# Copyright (c) 2024, 2024, Oracle and/or its affiliates. All rights reserved.
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

import os
import re
import glob
import sysconfig
import argparse

DELIM = "=" * 120

verbose = False

TR = re.compile(r'PyTypeObject\s+([\w_-]+Type)\s+=\s+\{')
COMMENT_TP_NAME = re.compile(r',\s*/\*\s*tp_name\s*\*/')
COMMENT_TP_FLAGS = re.compile(r',\s*/\*\s*tp_flags\s*\*/')

class CPythonType:
    def __init__(self, var_name, location):
        self.var_name = var_name
        self.location = location
        self.tp_name = ""
        self.tp_flags = ""

    def __repr__(self):
        return f"{self.var_name}(tp_name={self.tp_name}, tp_flags = {self.tp_flags}) at {self.location}"


def get_tp_flags(lines, start, stop):
    tp_flags_init = [lines[start]]
    j = start - 1
    while j > stop:
        if "Py_TPFLAGS" in lines[j]:
            tp_flags_init.insert(0, lines[j])
        j -= 1
    return " ".join(x.strip() for x in tp_flags_init).replace("\n", " ")


def process_source_file(path):
    if verbose:
        print(f"Processing file '{path}'")
    with open(path, "r") as f:
        lines = f.readlines()
    start_line = -1
    type_name = None
    tp_name = None
    tp_flags = None
    for i, line in enumerate(lines):
        match = TR.search(line)
        if match:
            start_line = i
            type_name = match.group(1)
        if type_name:
            if "PyObject_HEAD_INIT" in line or "PyVarObject_HEAD_INIT" in line:
                tp_name = lines[i+1].strip()
            elif not tp_name and "/* tp_name */" in line:
                tp_name = COMMENT_TP_NAME.sub("", line).strip()
            if "/* tp_flags */" in line:
                tp_flags = get_tp_flags(lines, i, start_line)
            if ";" in line:
                if tp_flags:
                    tp_flags_bare = COMMENT_TP_FLAGS.sub("", tp_flags)
                    print(f"{type_name}")
                    print(f"    location: {path}:{start_line+1}:")
                    print(f"    tp_name: {tp_name}")
                    print(f"    tp_flags: {tp_flags_bare}")
                type_name = None
                tp_flags = None


def main():
    global verbose
    parser = argparse.ArgumentParser(description="Extracts tp_flags of CPython types.")
    parser.add_argument("src_dir",
                        help="CPython source directory to scan.")
    parser.add_argument("-v", "--verbose", action="store_true",
                        help="Enable verbose output.")
    parsed_args = parser.parse_args()
    verbose = parsed_args.verbose

    for path in glob.glob(os.path.join(parsed_args.src_dir, "Objects", "*.c"), recursive=True):
        process_source_file(path)

    for path in glob.glob(os.path.join(parsed_args.src_dir, "Modules", "*.c"), recursive=True):
        process_source_file(path)


if __name__ == '__main__':
    main()
