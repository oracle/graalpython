# Copyright (c) 2018, Oracle and/or its affiliates.
#
# The Universal Permissive License (UPL), Version 1.0
#
# Subject to the condition set forth below, permission is hereby granted to any
# person obtaining a copy of this software, associated documentation and/or data
# (collectively the "Software"), free of charge and under any and all copyright
# rights in the Software, and any and all patent rights owned or freely
# licensable by each licensor hereunder covering either (i) the unmodified
# Software as contributed to or provided by such licensor, or (ii) the Larger
# Works (as defined below), to deal in both
#
# (a) the Software, and
# (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
#     one is included with the Software (each a "Larger Work" to which the
#     Software is contributed by such licensors),
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

#!/usr/bin/env python3
import os 
import sys 
import re
from collections import defaultdict
from pprint import pprint, pformat

PTRN_ERROR = re.compile(r'^(?P<error>[A-Z][a-z][a-zA-Z]+):(?P<message>.*)$')
PTRN_UNITTEST = re.compile(r'^#### running: graalpython/lib-python/3/test/(?P<unittest>.*)$')
PTRN_NUM_TESTS = re.compile(r'^Ran (?P<num_tests>\d+) test.*$')
PTRN_NUM_ERRORS = re.compile(r'^FAILED \((failures=(?P<failures>\d+))?(, )?((errors=(?P<errors>\d+)))?\)$')


def process_output(output):
    unittests = []
    error_messages = defaultdict(set)
    
    class StatEntry(object):
        def __init__(self):
            self.total_runs = -1
            self.num_errors = -1
            self.num_fails = -1

        @property
        def num_passes(self):
            return self.total_runs - (self.num_fails + self.num_errors)
        
        def __str__(self):
            return '<{}: E={}, F={}, P={}>'.format(self.total_runs, self.num_errors, self.num_fails, self.num_passes)

        def __repr__(self):
            return self.__str__()

    stats = defaultdict(StatEntry)
        
    with open(output, 'r') as OUT:
        for line in OUT:
            match = re.match(PTRN_UNITTEST, line)
            if match:
                unittests.append(match.group('unittest'))
                continue

            # extract python reported python error messages 
            match = re.match(PTRN_ERROR, line)
            if match:
                error_messages[match.group('error')].add((unittests[-1], match.group('message')))
                continue

            # stats 
            if line.strip() == 'OK':
                stats[unittests[-1]].num_fails = 0
                stats[unittests[-1]].num_errors = 0
                continue

            match = re.match(PTRN_NUM_TESTS, line)
            if match:
                stats[unittests[-1]].total_runs = int(match.group('num_tests'))
                continue

            match = re.match(PTRN_NUM_ERRORS, line)
            if match:
                fails = match.group('failures')
                errs = match.group('errors')
                if not fails and not errs:
                    continue

                stats[unittests[-1]].num_fails = int(fails) if fails else 0
                stats[unittests[-1]].num_errors = int(errs) if errs else 0


if __name__ == '__main__':
    process_output(sys.argv[1])
