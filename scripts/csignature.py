# Copyright (c) 2018, 2024, Oracle and/or its affiliates. All rights reserved.
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

# This script parses gcc output on the embedded source and generates a list of
# C API functions that can be stored in CAPIFunctions.txt to automatically
# check the consistency of the C API implementation in GraalPy.
#
# Steps to update CAPI functions list:
# 1. Install GNU Binutils (2.43.1).
# 2. Build the desired version of CPython.
# 3. Create and activate a venv using the CPython binary you built above.
# 4. do `pip install pycparser pycparser-fake-libc`
# 5. Finally:
# Just run it in this folder e.g python csignature.py > ../graalpython/com.oracle.graal.python.cext/CAPIFunctions.txt
#
# Make sure to use the exact same CPython version as we are targeting

import os
import re
import subprocess
import sysconfig
import tempfile

import pycparser_fake_libc
from pycparser import c_ast, parse_file, c_generator

source = """\
#define __attribute__(x)
#define _POSIX_THREADS

#include <Python.h>
#include <frameobject.h>
#include <datetime.h>
#include <structmember.h>
"""

include_path = sysconfig.get_config_var("INCLUDEPY")
with tempfile.NamedTemporaryFile('w') as f:
    f.write(source)
    f.flush()
    cpp_args = ['-I', pycparser_fake_libc.directory, '-I', include_path]
    ast = parse_file(f.name, use_cpp=True, cpp_args=cpp_args)

lib_path = os.path.join(sysconfig.get_config_var('LIBDIR'), sysconfig.get_config_var('LDLIBRARY'))
out = subprocess.check_output(['nm', '--defined-only', '--format=just-symbols', lib_path], text=True)
exported_symbols = out.rstrip().splitlines()


def cleanup(str):
    for i in range(4):
        str = re.sub(" \\*", "*", str)
        str = re.sub("\\* ", "*", str)
    return str


class FuncDeclVisitor(c_ast.NodeVisitor):
    def __init__(self):
        self.gen = c_generator.CGenerator()
        self.results = []

    def visit_Decl(self, node):
        if isinstance(node.type, c_ast.FuncDecl):
            if node.name not in exported_symbols:
                return
            ret = cleanup(self.gen.visit(node.type.type))
            for p in node.type.args.params:
                # erase parameter names
                if not isinstance(p, c_ast.EllipsisParam):
                    t = p.type
                    while isinstance(t, (c_ast.PtrDecl, c_ast.FuncDecl, c_ast.ArrayDecl)):
                        t = t.type
                    t.declname = None
            args = [cleanup(self.gen.visit(p)) for p in node.type.args.params]
            args = "|".join(args)
            self.results.append(f"{node.name};{ret};{args}")


v = FuncDeclVisitor()
v.visit(ast)
for line in sorted(v.results):
    print(line)
