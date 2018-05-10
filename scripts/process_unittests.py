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

import os 
import sys 
import re
import csv
from json import dumps
from collections import defaultdict
from pprint import pprint, pformat

PTRN_ERROR = re.compile(r'^(?P<error>[A-Z][a-z][a-zA-Z]+):(?P<message>.*)$')
PTRN_UNITTEST = re.compile(r'^#### running: graalpython/lib-python/3/test/(?P<unittest>.*)$')
PTRN_NUM_TESTS = re.compile(r'^Ran (?P<num_tests>\d+) test.*$')
PTRN_NUM_ERRORS = re.compile(r'^FAILED \((failures=(?P<failures>\d+))?(, )?((errors=(?P<errors>\d+)))?(, )?((skipped=(?P<skipped>\d+)))?\)$')


def process_output(output):
    unittests = []
    error_messages = defaultdict(set)
    
    class StatEntry(object):
        def __init__(self):
            self.num_tests = -1
            self.num_errors = -1
            self.num_fails = -1
            self.num_skipped = -1

        def all_ok(self):
            self.num_errors = 0
            self.num_fails = 0
            self.num_skipped = 0

        @property
        def num_passes(self):
            if self.num_tests > 0:
                return self.num_tests - (self.num_fails + self.num_errors + self.num_skipped)
            return -1

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
                error_messages[unittests[-1]].add((match.group('error'), match.group('message')))
                continue

            # stats 
            if line.strip() == 'OK':
                stats[unittests[-1]].all_ok()
                continue

            match = re.match(PTRN_NUM_TESTS, line)
            if match:
                stats[unittests[-1]].num_tests = int(match.group('num_tests'))
                continue

            match = re.match(PTRN_NUM_ERRORS, line)
            if match:
                fails = match.group('failures')
                errs = match.group('errors')
                skipped = match.group('skipped')
                if not fails and not errs and not skipped:
                    continue

                stats[unittests[-1]].num_fails = int(fails) if fails else 0
                stats[unittests[-1]].num_errors = int(errs) if errs else 0
                stats[unittests[-1]].num_skipped = int(skipped) if skipped else 0

    with open('unittests.csv', 'w') as CSV:
        fieldnames = ['unittest', 'num_tests', 'num_fails', 'num_errors', 'num_skipped', 'num_passes', 'python_errors']
        writer = csv.DictWriter(CSV, fieldnames=fieldnames)
        writer.writeheader()

        totals = {
            'num_tests': 0, 
            'num_fails': 0, 
            'num_errors': 0, 
            'num_skipped': 0, 
            'num_passes': 0, 
        }
        total_not_run_at_all = 0
        total_pass_all = 0

        for unittest in unittests:
            unittest_stats = stats[unittest]
            unittest_errmsg = error_messages[unittest]
            writer.writerow({
                'unittest': unittest,
                'num_tests': unittest_stats.num_tests, 
                'num_fails': unittest_stats.num_fails, 
                'num_errors': unittest_stats.num_errors, 
                'num_skipped': unittest_stats.num_skipped, 
                'num_passes': unittest_stats.num_passes, 
                'python_errors': dumps(list(unittest_errmsg))
                })

            # update totals that ran in some way
            if unittest_stats.num_tests > 0:
                totals['num_tests'] += unittest_stats.num_tests
                totals['num_fails'] += unittest_stats.num_fails
                totals['num_errors'] += unittest_stats.num_errors
                totals['num_skipped'] += unittest_stats.num_skipped
                totals['num_passes'] += unittest_stats.num_passes
                if unittest_stats.num_tests == unittest_stats.num_passes:
                    total_pass_all += 1
            else:
                total_not_run_at_all += 1

        _all_runs = len(unittests)-total_not_run_at_all
        _all_total = len(unittests)
        _percent_all_runs = float(_all_runs) / float(_all_total) * 100.0
        _percent_all_full_passes = float(total_pass_all) / float(_all_total) * 100.0

        _test_runs = totals['num_passes']
        _test_total = totals['num_tests']
        _percent_test_runs = float(_test_runs) / float(_test_total) * 100.0

        writer.writerow({
            'unittest': 'TOTAL',
            'num_tests': totals['num_tests'], 
            'num_fails': totals['num_fails'], 
            'num_errors': totals['num_errors'], 
            'num_skipped': totals['num_skipped'], 
            'num_passes': totals['num_passes'], 
            'python_errors': 'Could run {0}/{1} unittests ({2:.2f}%). Unittests which pass completely: {3:.2f}%. Of the ones which ran, could run: {4}/{5} tests ({6:.2f}%)'.format(
                _all_runs, _all_total, _percent_all_runs, _percent_all_full_passes,
                _test_runs, _test_total, _percent_test_runs)
            })

if __name__ == '__main__':
    process_output(sys.argv[1])
