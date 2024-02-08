# Copyright (c) 2022, 2023, Oracle and/or its affiliates. All rights reserved.
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
import subprocess
import sys


def simple_replace(contents, match, replacement, assignment):
    "replaces the indexes in 'contents' described by the tuple 'group' with replacement"
    
    start, end = match.span(1)
    return contents[:start] + replacement + contents[end:]


def replace_pre(contents, match, replacement, assignment):
    "replaces a pre-increment/decrement"
    
    start, end = match.span(1)
    replacement = replacement.replace('%receiver', contents[start: end])
    start, end = match.span()
    return contents[:start] + replacement + contents[end:]


def replace_field_access(contents, match, replacement, assignment):
    "replaces a field access, scanning backwards to determine the receiver"
    
    start, end = match.span(1)
    level = 0
    
    def consume_whitespace_backwards(idx):
        while idx > 0 and contents[idx].isspace():
            idx -= 1
        return idx
    
    def consume_whitespace_forward(idx):
        while idx < len(contents) and contents[idx].isspace():
            idx += 1
        return idx
    
    start = consume_whitespace_backwards(start - 1)
    if start < 1 or contents[start - 1: start + 1] != '->':
        return contents
    
    # scan backwards to capture the whole receiver
    idx = consume_whitespace_backwards(start - 2)
    empty = True
    while idx > 1:
        c = contents[idx]
        c2 = contents[idx - 1: idx + 1]
        if c == ')' or c == ']' or (c == '>' and c2 != '->'):
            if level == 0 and c == ')' and not empty:
                idx += 1
                break  # don't include casts
            empty = False
            level += 1
            idx -= 1
        elif level > 0 and (c == '(' or c == '[' or c == '<'):
            level -= 1
            idx = consume_whitespace_backwards(idx - 1)
        else:
            if level > 0:
                idx -= 1
            elif c.isidentifier() or c.isdigit():
                empty = False
                idx -= 1
            elif c == '.':
                empty = True
                idx = consume_whitespace_backwards(idx - 1)
            elif c2 == '->':
                empty = True
                idx = consume_whitespace_backwards(idx - 2)
            else:
                idx += 1
                break
    
    receiver_start = consume_whitespace_forward(idx)
    receiver_string = contents[receiver_start: start - 1]
        
    if assignment is not None:
        
        # scan forwards to see if there's an assignment
        idx = consume_whitespace_forward(end)
        if contents[idx] == '=' and contents[idx+1] != '=':
            # this looks like an assignment, determine the value
            idx = consume_whitespace_forward(idx + 1)
            value_start = idx
            empty = True
            while idx < len(contents) - 2:
                c = contents[idx]
                c2 = contents[idx: idx + 2]
                if c == '(' or c == '[' or (c == '<' and c2 != '<=' and c2 != '<<'):
                    level += 1
                    idx += 1
                elif level > 0 and (c == ')' or c == ']' or (c == '>' and c2 != '>=' and c2 != '>>')):
                    level -= 1
                    idx += 1
                else:
                    if level == 0 and (c == ')' or c == ';' or c == ','):
                        break;
                    if c2 == '<=' or c2 == '<<' or c2 == '>='or c2 == '>>' or c2 == '->':
                        idx += 2
                    else:
                        idx += 1
            
            value_string = contents[value_start:idx]
            
            return contents[:receiver_start] + assignment.replace('%receiver', receiver_string).replace("%value", value_string) + contents[idx:]
    
    return contents[:receiver_start] + replacement.replace('%receiver', receiver_string) + contents[end:]


auto_replacements = {
                    # avoid direct access - predecrement/preincrement:
                    r'--([\d\w_]+)->ob_refcnt': (replace_pre, '(Py_SET_REFCNT(%receiver, Py_REFCNT(%receiver) - 1), Py_REFCNT(%receiver))'),
                    r'\+\+([\d\w_]+)->ob_refcnt': (replace_pre, '(Py_SET_REFCNT(%receiver, Py_REFCNT(%receiver) + 1), Py_REFCNT(%receiver))'),
                    # avoid direct access:
                    r'\W(ob_type)\W': (replace_field_access, 'Py_TYPE(%receiver)'),
                    r'\W(ob_refcnt)\W': (replace_field_access, 'Py_REFCNT(%receiver)'),
                    r'\W(ob_item)\W': (replace_field_access, 'PySequence_Fast_ITEMS((PyObject*)%receiver)'),
                    r'^\s*()(std::)?free\((const_cast<char \*>)?\(?\w+->m_ml->ml_doc\)?\);': (simple_replace, '//'),
                    r'\W(m_ml\s*->\s*ml_doc)\W': (replace_field_access, 'PyObject_GetDoc((PyObject*)(%receiver))', 'PyObject_SetDoc((PyObject*)(%receiver), %value)'),
                    # already defined by GraalPy:
                    r'^\s*()#\s*define\s+Py_SET_TYPE\W': (simple_replace, '//'),
                    r'^\s*()#\s*define\s+Py_SET_SIZE\W': (simple_replace, '//'),
                    r'^\s*()#\s*define\s+Py_SET_REFCNT\W': (simple_replace, '//'),
}

   
def auto_patch(path, dryrun):
    "reads the given file, applies all replacements, and writes back the result if there were changes"

    with open(path, mode='r') as f:
        try:
            contents = f.read()
        except UnicodeDecodeError:
             return  # may happen for binary files
         
    original = contents
    import re
    for pattern, (*ops,) in auto_replacements.items():
        pattern = re.compile(pattern, re.MULTILINE)
        func = ops[0]
        replacement = ops[1]
        assignment = ops[2] if len(ops) >= 3 else None
        start = 0
        try:
            while True:
                match = pattern.search(contents, start)
                if not match:
                    break
                start = match.end()
                contents = func(contents, match, replacement, assignment)
        except ValueError:
            pass  # pattern not found (any more)
    if contents != original:
        print("auto-patching C API usages in " + path)
        if dryrun:
            import difflib
            result = list(difflib.unified_diff(original.splitlines(keepends=True), contents.splitlines(keepends=True), fromfile="a/"+path, tofile="b/"+path))
            sys.stdout.writelines(result)
        else:
            with open(path, mode='w') as f:
                f.write(contents)


def auto_patch_tree(location, dryrun=False):
    if os.path.isfile(location):
        files = [location]
    else:
        files = [os.path.join(root, name)
                 for root, dirs, files in os.walk(location)
                 for name in files]

    for path in files:
        if '.c' in path or '.h' in path or '.inc' in path:
            auto_patch(path, dryrun)


if __name__ == "__main__":
    import argparse
    parser = argparse.ArgumentParser(description = "Auto-patch C API usages in the given directory (recursively).")
    parser.add_argument('path', type=str, help="path of the file or directory that should be processed (directories will be processed recursively)")
    parser.add_argument('-d', '--dryrun', help="don't write results to disk", action="store_true")
    args = parser.parse_args()
    if args.dryrun:
        print("dry-run")
    auto_patch_tree(args.path, args.dryrun)
