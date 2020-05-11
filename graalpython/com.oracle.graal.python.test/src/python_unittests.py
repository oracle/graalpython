# Copyright (c) 2018, 2020, Oracle and/or its affiliates. All rights reserved.
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

import csv
import gzip
import os
import signal
import re
import html
import time
import subprocess
from collections import defaultdict
from json import dumps
from multiprocessing import Pool, TimeoutError
from pprint import pformat

   
import argparse
import sys
from time import gmtime, strftime

# global CLI flags
flags = None

# constants
PATH_UNITTESTS = "graalpython/lib-python/3/test/"

_BASE_NAME = "unittests"
TXT_RESULTS_NAME = "{}.txt.gz".format(_BASE_NAME)
CSV_RESULTS_NAME = "{}.csv".format(_BASE_NAME)
HTML_RESULTS_NAME = "{}.html".format(_BASE_NAME)

HR = "".join(['-' for _ in range(120)])

PTRN_ERROR = re.compile(r'^(?P<error>[A-Z][a-z][a-zA-Z]+):(?P<message>.*)$')
PTRN_UNITTEST = re.compile(r'^#### running: graalpython/lib-python/3/test/(?P<unittest>[\w.]+).*$', re.DOTALL)
PTRN_NUM_TESTS = re.compile(r'^Ran (?P<num_tests>\d+) test.*$')
PTRN_FAILED = re.compile(
    r'^FAILED \((failures=(?P<failures>\d+))?(, )?(errors=(?P<errors>\d+))?(, )?(skipped=(?P<skipped>\d+))?\)$')
PTRN_OK = re.compile(
    r'^OK \((failures=(?P<failures>\d+))?(, )?(errors=(?P<errors>\d+))?(, )?(skipped=(?P<skipped>\d+))?\)$')
PTRN_JAVA_EXCEPTION = re.compile(r'^(?P<exception>com\.oracle\.[^:]*):(?P<message>.*)')
PTRN_MODULE_NOT_FOUND = re.compile(r'.*ModuleNotFound: \'(?P<module>.*)\'\..*', re.DOTALL)
PTRN_IMPORT_ERROR = re.compile(r".*cannot import name \'(?P<module>.*)\'.*", re.DOTALL)
PTRN_REMOTE_HOST = re.compile(r"(?P<user>\w+)@(?P<host>[\w.]+):(?P<path>.+)")
PTRN_VALID_CSV_NAME = re.compile(r"unittests-\d{4}-\d{2}-\d{2}.csv")
PTRN_TEST_STATUS_INDIVIDUAL = re.compile(r"(?P<name>test[\w_]+ \(.+?\)) ... (?P<status>.+)")
PTRN_TEST_STATUS_ERROR = re.compile(r"(?P<status>.+): (?P<name>test[\w_]+ \(.+?\))")

TEST_TYPES = ('array','buffer','code','frame','long','memoryview','unicode','exceptions',
            'baseexception','range','builtin','bytes','thread','property','class','dictviews',
            'sys','imp','rlcompleter','types','coroutines','dictcomps','int_literal','mmap',
            'module','numeric_tower','syntax','traceback','typechecks','int','keyword','raise',
            'descr','generators','list','complex','tuple','enumerate','super','float',
            'bool','fstring','dict','iter','string','scope','with','set')

TEST_APP_SCRIPTING = ('test_json','csv','io','memoryio','bufio','fileio','file','fileinput','tempfile',
            'pickle','pickletester','pickle','picklebuffer','pickletools','codecs','functools',
            'itertools','math','operator','zlib','zipimport_support','zipfile','zipimport','re',
            'zipapp','gzip','bz2','builtin')

TEST_SERVER_SCRIPTING_DS = ('sqlite3','asyncio','marshal','select','crypt','ssl','uuid','multiprocessing',
                            'fork','forkserver','main_handling','spawn','socket','socket','socketserver',
                            'signal','mmap','resource','thread','dummy_thread','threading','threading_local',
                            'threadsignals','dummy_threading','threadedtempfile','thread','hashlib',
                            'pyexpat','locale','_locale','locale','c_locale_coercion','struct') + TEST_APP_SCRIPTING


USE_CASE_GROUPS = {
        'Python Language and Built-in Types': TEST_TYPES,
        'Application Scripting': TEST_APP_SCRIPTING,
        'Server-Side Scripting and Data Science': TEST_SERVER_SCRIPTING_DS
         }

# ----------------------------------------------------------------------------------------------------------------------
#
# logging utils
#
# ----------------------------------------------------------------------------------------------------------------------
def log(msg, *args, **kwargs):
    print(msg.format(*args, **kwargs))


def debug(msg, *args, **kwargs):
    if flags.verbose:
        log(msg, args, kwargs)


def file_name(name, current_date_time):
    idx = name.index('.')
    if idx > 0:
        return '{}-{}{}'.format(name[:idx], current_date_time, name[idx:])
    return '{}-{}'.format(name, current_date_time)

def get_tail(output, count=15):
    lines = output.split("\n")
    start = max(0, len(lines) - count)
    return '\n'.join(lines[start:])

TIMEOUT = 60 * 20  # 20 mins per unittest wait time max ...

# ----------------------------------------------------------------------------------------------------------------------
#
# exec utils
#
# ----------------------------------------------------------------------------------------------------------------------
def _run_cmd(cmd, timeout=TIMEOUT, capture_on_failure=True):
    if isinstance(cmd, str):
        cmd = cmd.split(" ")
    assert isinstance(cmd, (list, tuple))

    cmd_string = ' '.join(cmd)
    log("[EXEC] starting '{}' ...".format(cmd_string))

    start_time = time.monotonic()
    # os.setsid is used to create a process group, to be able to call os.killpg upon timeout
    proc = subprocess.Popen(cmd, preexec_fn=os.setsid, stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
    try:
        output = proc.communicate(timeout=timeout)[0]
    except subprocess.TimeoutExpired as e:
        delta = time.monotonic() - start_time
        os.killpg(proc.pid, signal.SIGKILL)
        output = proc.communicate()[0]
        msg = "TimeoutExpired: {:.3f}s".format(delta)
        tail = get_tail(output.decode('utf-8', 'ignore'))
        log("[ERR] timeout '{}' after {:.3f}s, killing process group {}, last lines of output:\n{}\n{}", cmd_string, delta, proc.pid, tail, HR)
    else:
        delta = time.monotonic() - start_time
        log("[EXEC] finished '{}' with exit code {} in {:.3f}s", cmd_string, proc.returncode, delta)
        msg = "Finished: {:.3f}s".format(delta)
    
    return proc.returncode == 0, output.decode("utf-8", "ignore") + "\n" + msg


def scp(results_file_path, destination_path, destination_name=None):
    dst_path = destination_name if destination_name else os.path.basename(results_file_path)
    remote_dst = os.path.join(destination_path, dst_path)
    cmd = ['scp', results_file_path, remote_dst]
    return _run_cmd(cmd)[0]


def _run_unittest(test_path, timeout, with_cpython=False):
    if with_cpython:
        cmd = ["python3", test_path, "-v"]
    else:
        cmd = ["mx", "python3", "--python.CatchAllExceptions=true", test_path, "-v"]
    output = _run_cmd(cmd, timeout)[1]
    output = '''
##############################################################
#### running: {} 
    '''.format(test_path) + output
    return output



def run_unittests(unittests, timeout, with_cpython=False):
    assert isinstance(unittests, (list, tuple))
    num_unittests = len(unittests)
    log("[EXEC] running {} unittests ... ", num_unittests)
    log("[EXEC] timeout per unittest: {} seconds", timeout)

    start_time = time.monotonic()
    pool = Pool(processes=(os.cpu_count() // 4) or 1) # to account for hyperthreading and some additional overhead
    
    out = []
    def callback(result):
        out.append(result)
        log("[PROGRESS] {} / {}: \t {:.1f}%", len(out), num_unittests, len(out) * 100 / num_unittests)
        
    # schedule all unittest runs
    for ut in unittests:
        pool.apply_async(_run_unittest, args=(ut, timeout, with_cpython), callback=callback)
        
    pool.close()
    pool.join()
    pool.terminate()
    log("[STATS] processed {} unittests in {:.3f}s", num_unittests, time.monotonic() - start_time)
    return out


def get_unittests(base_tests_path, limit=None, sort=True, skip_tests=None):
    def _sorter(iterable):
        return sorted(iterable) if sort else iterable
    unittests = [os.path.join(base_tests_path, name)
                 for name in _sorter(os.listdir(base_tests_path))
                 if name.startswith("test_")]
    if skip_tests:
        log("[INFO] skipping unittests: {}", skip_tests)
        cnt = len(unittests)
        unittests = [t for t in unittests if t not in skip_tests]
        log("[INFO] running {} of {} unittests", len(unittests), cnt)
    return unittests[:limit] if limit else unittests


def get_remote_host(scp_path):
    match = re.match(PTRN_REMOTE_HOST, scp_path)
    return match.group('user'), match.group('host'), match.group('path')


def ssh_ls(scp_path):
    user, host, path = get_remote_host(scp_path)
    cmd = ['ssh', '{}@{}'.format(user, host), 'ls', '-l', path]
    return map(lambda l: l.split()[-1], _run_cmd(cmd)[1].splitlines())


def read_csv(path):
    rows = []
    with open(path, "r") as CSV_FILE:
        reader = csv.reader(CSV_FILE)
        headers = next(reader)[1:]
        for row in reader:
            rows.append(row)
    return rows


class TestStatus(object):
    ERROR = 'error'
    FAIL = 'fail'
    SKIPPED = 'skipped'
    OK = 'ok'


# ----------------------------------------------------------------------------------------------------------------------
#
# result (output processing)
#
# ----------------------------------------------------------------------------------------------------------------------
class StatEntry(object):
    def __init__(self):
        self.num_tests = -1
        # reported stats
        self._num_errors = -1
        self._num_fails = -1
        self._num_skipped = -1
        # tracked stats
        self._tracked = False

    def _reset(self):
        self._num_fails = 0
        self._num_errors = 0
        self._num_skipped = 0

    def all_ok(self):
        self._reset()

    @property
    def num_errors(self):
        return self._num_errors

    @num_errors.setter
    def num_errors(self, value):
        if not self._tracked:
            self._num_errors = value

    @property
    def num_fails(self):
        return self._num_fails

    @num_fails.setter
    def num_fails(self, value):
        if not self._tracked:
            self._num_fails = value

    @property
    def num_skipped(self):
        return self._num_skipped

    @num_skipped.setter
    def num_skipped(self, value):
        if not self._tracked:
            self._num_skipped = value

    @property
    def num_passes(self):
        if self.num_tests > 0:
            return self.num_tests - (self._num_fails + self._num_errors + self._num_skipped)
        return -1

    def update(self, test_detailed_stats):
        if len(test_detailed_stats) > 0:
            self._tracked = True
            self._reset()
            for test, stats in test_detailed_stats.items():
                stats = {s.lower() for s in stats}
                if TestStatus.ERROR in stats:
                    self._num_errors += 1
                elif TestStatus.FAIL in stats:
                    self._num_fails += 1
                else:
                    for s in stats:
                        if s.startswith(TestStatus.SKIPPED):
                            self._num_skipped += 1
                            break


def process_output(output_lines):
    if isinstance(output_lines, str):
        output_lines = output_lines.split("\n")

    unittests = []
    # stats tracked per unittest
    unittest_tests = defaultdict(list)
    error_messages = defaultdict(dict)
    java_exceptions = defaultdict(set)
    stats = defaultdict(StatEntry)

    for line in output_lines:
        match = re.match(PTRN_UNITTEST, line)
        if match:
            unittest = match.group('unittest')
            unittests.append(unittest)
            unittest_tests.clear()
            continue

        # extract python reported python error messages
        match = re.match(PTRN_ERROR, line)
        if match:
            error_message = (match.group('error'), match.group('message'))
            if not error_message[0] == 'Directory' and not error_message[0] == 'Components':
                error_message_dict = error_messages[unittests[-1]]
                d = error_message_dict.get(error_message)
                if not d:
                    d = 0
                error_message_dict[error_message] = d + 1
            continue

        # extract java exceptions
        match = re.match(PTRN_JAVA_EXCEPTION, line)
        if match:
            java_exceptions[unittests[-1]].add((match.group('exception'), match.group('message')))
            continue

        # stats
        # tracking stats
        match = re.match(PTRN_TEST_STATUS_INDIVIDUAL, line)
        if not match:
            match = re.match(PTRN_TEST_STATUS_ERROR, line)
        if match:
            name = match.group('name')
            status = match.group('status')
            unittest_tests[name].append(status)
            continue

        if line.strip() == 'OK':
            stats[unittests[-1]].all_ok()
            continue

        match = re.match(PTRN_OK, line)
        if match:
            fails = match.group('failures')
            errs = match.group('errors')
            skipped = match.group('skipped')

            stats[unittests[-1]].num_fails = int(fails) if fails else 0
            stats[unittests[-1]].num_errors = int(errs) if errs else 0
            stats[unittests[-1]].num_skipped = int(skipped) if skipped else 0
            continue

        match = re.match(PTRN_NUM_TESTS, line)
        if match:
            stats[unittests[-1]].num_tests = int(match.group('num_tests'))
            stats[unittests[-1]].update(unittest_tests)
            unittest_tests.clear()
            continue

        match = re.match(PTRN_FAILED, line)
        if match:
            fails = match.group('failures')
            errs = match.group('errors')
            skipped = match.group('skipped')
            if not fails and not errs and not skipped:
                continue

            stats[unittests[-1]].num_fails = int(fails) if fails else 0
            stats[unittests[-1]].num_errors = int(errs) if errs else 0
            stats[unittests[-1]].num_skipped = int(skipped) if skipped else 0
            continue

    return unittests, error_messages, java_exceptions, stats


# ----------------------------------------------------------------------------------------------------------------------
#
# python  error processing
#
# ----------------------------------------------------------------------------------------------------------------------
def process_errors(unittests, error_messages, err=None, msg_processor=None):
    if isinstance(err, str):
        err = {err,}

    def _err_filter(item):
        if not err:
            return True
        return item[0] in err

    def _processor(msg):
        if not msg_processor:
            return msg
        return msg_processor(msg)

    missing_modules = defaultdict(lambda: 0)
    for ut in unittests:
        errors = error_messages[ut]
        for name in map(_processor, (msg for err, msg in filter(_err_filter, errors))):
            missing_modules[name] = missing_modules[name] + 1

    return missing_modules


def get_missing_module(msg):
    match = re.match(PTRN_MODULE_NOT_FOUND, msg)
    return match.group('module') if match else None


def get_cannot_import_module(msg):
    match = re.match(PTRN_IMPORT_ERROR, msg)
    return match.group('module') if match else None


# ----------------------------------------------------------------------------------------------------------------------
#
# csv reporting
#
# ----------------------------------------------------------------------------------------------------------------------
class Col(object):
    UNITTEST = 'unittest'
    NUM_TESTS = 'num_tests'
    NUM_FAILS = 'num_fails'
    NUM_ERRORS = 'num_errors'
    NUM_SKIPPED = 'num_skipped'
    NUM_PASSES = 'num_passes'
    PYTHON_ERRORS = 'python_errors'
    CPY_NUM_TESTS = 'cpy_num_tests'
    CPY_NUM_FAILS = 'cpy_num_fails'
    CPY_NUM_ERRORS = 'cpy_num_errors'
    CPY_NUM_SKIPPED = 'cpy_num_skipped'
    CPY_NUM_PASSES = 'cpy_num_passes'


CSV_HEADER = [
    Col.UNITTEST,
    Col.NUM_TESTS,
    Col.NUM_FAILS,
    Col.NUM_ERRORS,
    Col.NUM_SKIPPED,
    Col.NUM_PASSES,
    Col.CPY_NUM_TESTS,
    Col.CPY_NUM_FAILS,
    Col.CPY_NUM_ERRORS,
    Col.CPY_NUM_SKIPPED,
    Col.CPY_NUM_PASSES,
    Col.PYTHON_ERRORS
]


class Stat(object):
    # unittest level aggregates
    UT_TOTAL = "ut_total"  # all the unittests
    UT_RUNS = 'ut_runs'  # all unittests which could run
    UT_PASS = 'ut_pass'  # all unittests which pass
    UT_PERCENT_RUNS = "ut_percent_runs"  # all unittests which could run even with failures (percent)
    UT_PERCENT_PASS = "ut_percent_pass"  # all unittests which could run with no failures (percent)
    # test level aggregates
    TEST_RUNS = "test_runs"  # total number of tests that could be loaded and run even with failures
    TEST_PASS = "test_pass"  # number of tests which ran
    TEST_PERCENT_PASS = "test_percent_pass"  # percentage of tests which pass from all running tests (all unittests)


def save_as_txt(report_path, results):
    with gzip.open(report_path, 'wb') as TXT:
        output = '\n'.join(results)
        TXT.write(bytes(output, 'utf-8'))
        return output


def save_as_csv(report_path, unittests, error_messages, java_exceptions, stats, cpy_stats=None):
    rows = []
    with open(report_path, 'w') as CSV:
        totals = {
            Col.NUM_TESTS: 0,
            Col.NUM_FAILS: 0,
            Col.NUM_ERRORS: 0,
            Col.NUM_SKIPPED: 0,
            Col.NUM_PASSES: 0,
        }
        total_not_run_at_all = 0
        total_pass_all = 0

        for unittest in unittests:
            unittest_stats = stats[unittest]
            cpy_unittest_stats = cpy_stats[unittest] if cpy_stats else None
            unittest_errmsg = error_messages[unittest]
            if not unittest_errmsg:
                unittest_errmsg = java_exceptions[unittest]
            if not unittest_errmsg:
                unittest_errmsg = {}

            rows.append({
                Col.UNITTEST: unittest,
                # graalpython stats
                Col.NUM_TESTS: unittest_stats.num_tests,
                Col.NUM_FAILS: unittest_stats.num_fails,
                Col.NUM_ERRORS: unittest_stats.num_errors,
                Col.NUM_SKIPPED: unittest_stats.num_skipped,
                Col.NUM_PASSES: unittest_stats.num_passes,
                # cpython stats
                Col.CPY_NUM_TESTS: cpy_unittest_stats.num_tests if cpy_unittest_stats else None,
                Col.CPY_NUM_FAILS: cpy_unittest_stats.num_fails if cpy_unittest_stats else None,
                Col.CPY_NUM_ERRORS: cpy_unittest_stats.num_errors if cpy_unittest_stats else None,
                Col.CPY_NUM_SKIPPED: cpy_unittest_stats.num_skipped if cpy_unittest_stats else None,
                Col.CPY_NUM_PASSES: cpy_unittest_stats.num_passes if cpy_unittest_stats else None,
                # errors
                Col.PYTHON_ERRORS: dumps(list(unittest_errmsg.items())),
            })

            # update totals that ran in some way
            if unittest_stats.num_tests > 0:
                totals[Col.NUM_TESTS] += unittest_stats.num_tests
                totals[Col.NUM_FAILS] += unittest_stats.num_fails
                totals[Col.NUM_ERRORS] += unittest_stats.num_errors
                totals[Col.NUM_SKIPPED] += unittest_stats.num_skipped
                totals[Col.NUM_PASSES] += unittest_stats.num_passes
                if unittest_stats.num_tests == unittest_stats.num_passes:
                    total_pass_all += 1
            else:
                total_not_run_at_all += 1

        # unittest stats
        totals[Stat.UT_TOTAL] = len(unittests)
        totals[Stat.UT_RUNS] = len(unittests) - total_not_run_at_all
        totals[Stat.UT_PASS] = total_pass_all
        totals[Stat.UT_PERCENT_RUNS] = float(totals[Stat.UT_RUNS]) / float(totals[Stat.UT_TOTAL]) * 100.0
        totals[Stat.UT_PERCENT_PASS] = float(totals[Stat.UT_PASS]) / float(totals[Stat.UT_TOTAL]) * 100.0
        # test stats
        totals[Stat.TEST_RUNS] = totals[Col.NUM_TESTS]
        totals[Stat.TEST_PASS] = totals[Col.NUM_PASSES]
        totals[Stat.TEST_PERCENT_PASS] = float(totals[Stat.TEST_PASS]) / float(totals[Stat.TEST_RUNS]) * 100.0 \
            if totals[Stat.TEST_RUNS] else 0

        writer = csv.DictWriter(CSV, fieldnames=CSV_HEADER)
        writer.writeheader()
        for row in rows:
            writer.writerow(row)
        writer.writerow({
            Col.UNITTEST: 'TOTAL',
            Col.NUM_TESTS: totals[Col.NUM_TESTS],
            Col.NUM_FAILS: totals[Col.NUM_FAILS],
            Col.NUM_ERRORS: totals[Col.NUM_ERRORS],
            Col.NUM_SKIPPED: totals[Col.NUM_SKIPPED],
            Col.NUM_PASSES: totals[Col.NUM_PASSES],
            Col.PYTHON_ERRORS: 'Could run {0}/{1} unittests ({2:.2f}%). Unittests which pass completely: {3:.2f}%. '
                               'Of the ones which ran, could run: {4}/{5} tests ({6:.2f}%)'.format(
                totals[Stat.UT_RUNS], totals[Stat.UT_TOTAL],
                totals[Stat.UT_PERCENT_RUNS], totals[Stat.UT_PERCENT_PASS],
                totals[Stat.TEST_PASS], totals[Stat.TEST_PASS],
                totals[Stat.TEST_PERCENT_PASS])
        })

        return rows, totals


HTML_TEMPLATE = '''
<!DOCTYPE html>
<html lang="en" xmlns="http://www.w3.org/1999/html">
    <head>
        <meta charset="utf-8">
        <meta http-equiv="X-UA-Compatible" content="IE=edge">
        <meta name="viewport" content="width=device-width, initial-scale=1">
        <!-- The above 3 meta tags *must* come first in the head; any other head content must come *after* these tags -->
        <title>{title}</title>
        <link rel="stylesheet" href="https://maxcdn.bootstrapcdn.com/bootstrap/{bootstrap_version}/css/bootstrap.min.css"
              integrity="{bootstrap_css_integrity}" crossorigin="anonymous">
        <link rel="stylesheet" href="https://maxcdn.bootstrapcdn.com/bootstrap/{bootstrap_version}/css/bootstrap-theme.min.css"
              integrity="{bootstrap_theme_css_integrity}" crossorigin="anonymous">
        <link rel="stylesheet" href="https://maxcdn.bootstrapcdn.com/font-awesome/{fontawesome_version}/css/font-awesome.min.css">
        <style>.clickable {{cursor: pointer;}}</style>
        <link rel="stylesheet" href="https://cdn.datatables.net/{datatables_version}/css/dataTables.bootstrap.min.css">
        <style type="text/css">
            .table {{font-size: 10px; border: solid 1px #ddd;}}
            td, th {{border-left: solid 1px #ddd; white-space: nowrap;}}
            td.highlight {{background-color: whitesmoke !important;}}
            .nan {{color: #E0E0E0;}}
        </style>
        <!--[if lt IE 9]>
        <script src="https://oss.maxcdn.com/html5shiv/3.7.3/html5shiv.min.js"></script>
        <script src="https://oss.maxcdn.com/respond/1.4.2/respond.min.js"></script>
        <![endif]-->
    </head>
    <body>
        <nav class="navbar navbar-default">
            <div class="container-fluid">
                <div class="navbar-header">
                    <button type="button" class="navbar-toggle collapsed" data-toggle="collapse" data-target="#bs-example-navbar-collapse-1" aria-expanded="false">
                        <span class="sr-only">Toggle navigation</span><span class="icon-bar"></span><span class="icon-bar"></span><span class="icon-bar"></span>
                    </button>
                    <a class="navbar-brand" href="#"><i class="fa fa-check-square"></i>&nbsp;<b>{title}</b></a>
                </div>
                <div class="collapse navbar-collapse" id="bs-example-navbar-collapse-1">
                    <ul class="nav navbar-nav">
                        {navbar_links}
                    </ul>

                    <ul class="nav navbar-nav navbar-right">
                        <li><a href="#"><i class="fa fa-clock-o"></i>&nbsp;{current_date_time}</a></li>
                    </ul>
                </div>
            </div>
        </nav>
        {content}
        <script src="https://code.jquery.com/jquery-{jquery_version}.min.js" ></script>
        <script src="https://maxcdn.bootstrapcdn.com/bootstrap/{bootstrap_version}/js/bootstrap.min.js" integrity="{bootstrap_js_integrity}" crossorigin="anonymous"></script>
        <script>
            $(document).on('click', '.panel-heading', function(e){{
                var $this = $(this);
                if(!$this.hasClass('panel-collapsed')) {{
                    $this.parents('.panel').find('.list-group').slideUp();
                    $this.parents('.panel').find('.panel-body').slideUp();
                    $this.parents('.panel').find('.dataTables_wrapper').slideUp();
                    $this.addClass('panel-collapsed');
                    $this.find('i').removeClass('fa-minus-square-o').addClass('fa-plus-square-o');
                }} else {{
                    $this.parents('.panel').find('.list-group').slideDown();
                    $this.parents('.panel').find('.panel-body').slideDown();
                    $this.parents('.panel').find('.dataTables_wrapper').slideDown();
                    $this.removeClass('panel-collapsed');
                    $this.find('i').removeClass('fa-plus-square-o').addClass('fa-minus-square-o');
                }}
            }});
        </script>
        {scripts}
    </body>
</html>
'''


def save_as_html(report_name, rows, totals, missing_modules, cannot_import_modules, java_issues, current_date):
    def grid(*components):
        def _fmt(cmp):
            if isinstance(cmp, tuple):
                return '<div class="col-sm-{}">{}</div>'.format(cmp[1], cmp[0])
            return '<div class="col-sm">{}</div>'.format(cmp)
        return '''
        <div class="container" style="width: 100%;">
          <div class="row">
            {}
          </div>
        </div>
        '''.format('\n'.join([_fmt(cmp) for cmp in components]))

    def progress_bar(value, color='success'):
        return '''
        <div class="progress">
          <div class="progress-bar progress-bar-{color}" role="progressbar" aria-valuenow="{value}"
            aria-valuemin="0" aria-valuemax="100" style="width:{value}%">
            {value:.2f}% Complete
          </div>
        </div>
        '''.format(color=color, value=value)

    def fluid_div(title, div_content):
        return '''
        <div class="container-fluid">
            <div class="panel panel-default">
                <div class="panel-heading clickable">
                    <h3 class="panel-title"><i class="fa fa-minus-square-o" aria-hidden="true">&nbsp;&nbsp;</i>{title}</h3>
                </div>
                {content}
            </div>
        </div>
        '''.format(title=title, content=div_content)

    def ul(title, items):
        return fluid_div(title, '<ul class="list-group">{}</ul>'.format('\n'.join([
            '<li class="list-group-item">{}</span></li>'.format(itm) for itm in items
        ])))

    def table(tid, tcols, trows):
        _thead = '''
            <tr class="text-align: right;">
                <th data-orderable="false">&nbsp;</th>
                {columns}
            </tr>
            '''.format(columns='\n'.join(['<th>{}</th>'.format(c) for c in tcols]))

        def format_val(row, k):
            value = row[k]
            if k == Col.PYTHON_ERRORS:
                return "(click to expand)"
            elif k == Col.UNITTEST:
                _class = "text-info"
            elif k == Col.NUM_PASSES and value > 0:
                _class = "text-success"
            elif k in [Col.NUM_ERRORS, Col.NUM_FAILS] and value > 0:
                _class = "text-danger"
            elif k == Col.NUM_SKIPPED and value > 0:
                _class = "text-warning"
            elif k == Col.NUM_TESTS:
                _class = "text-dark"
            else:
                _class = "text-danger" if value and value < 0 else "text-muted"
            return '<span class="{} h6"><b>{}</b></span>'.format(_class, value)

        _tbody = '\n'.join([
                '<tr class="{cls}" data-errors="{errors}"><td>{i}</td>{vals}</tr>'.format(
                    errors = html.escape(row[Col.PYTHON_ERRORS], quote=True), # put the errors data into a data attribute
                    cls='info' if i % 2 == 0 else '', i=i,
                    vals=' '.join(['<td>{}</td>'.format(format_val(row, k)) for k in tcols]))
                for i, row in enumerate(trows)])

        _table = '''
        <table id="{tid}" class="table {tclass}" cellspacing="0" width="100%">
            <thead>{thead}</thead><tbody>{tbody}</tbody>
        </table>
        '''.format(tid=tid, tclass='', thead=_thead, tbody=_tbody)

        return fluid_div('<b>cPython unittests</b> run statistics', _table)

    scripts = '''
        <script src="https:////cdn.datatables.net/{datatables_version}/js/jquery.dataTables.min.js"></script>
        <script src="https:////cdn.datatables.net/{datatables_version}/js/dataTables.bootstrap.min.js"></script>
        <script>
            function initTable(table_id) {{
                var table = $(table_id).DataTable({{
                    "lengthMenu": [[50, 100, 200, -1], [50, 100, 200, "All"]],
                    paging: false, scrollX: true, scrollCollapse: true, "order": []
                }});
                // expand and show the errors when a row is clicked upon
                $(table_id).on('click', 'td', function () {{
                    var tr = $(this).closest('tr');
                    var row = table.row( tr );

                    if ( row.child.isShown() ) {{
                        row.child.hide();
                        tr.removeClass('shown');
                    }}
                    else {{
                        var data = tr.data('errors');
                        if (data) {{
                            function formatEntry(entry) {{
                                var description = entry[0][0];
                                var text = ('' + entry[0][1]).replace(/(.{{195}} )/g, '$1<br/>&nbsp;&nbsp;&nbsp;&nbsp;'); // break long lines
                                var count = (entry[1] > 1 ? ('<font color="red"> (x ' + entry[1] + ')</font>') : '');
                                return description + ': ' + text + count + '<br/>';
                            }}
                            var e = '<font style="font-family: monospace; font-size: 12px">' + data.map(formatEntry).join("") + '</font>';
                            row.child( e ).show();
                            tr.addClass('shown');
                        }}
                    }}
                }} );
            }}
            $(document).ready(function() {{ initTable('#{table_id}'); }});
        </script>
        '''

    missing_modules_info = ul('missing modules', [
        '<b>{}</b>&nbsp;<span class="text-muted">imported by {} unittests</span>'.format(name, cnt)
        for cnt, name in sorted(((cnt, name) for name, cnt in missing_modules.items()), reverse=True)
    ])

    cannot_import_modules_info = ul('modules which could not be imported', [
        '<b>{}</b>&nbsp;<span class="text-muted">could not be imported by {} unittests</span>'.format(name, cnt)
        for cnt, name in sorted(((cnt, name) for name, cnt in cannot_import_modules.items()), reverse=True)
    ])

    java_issues_info = ul('Java issues', [
        '<b>{}</b>&nbsp;<span class="text-muted">caused by {} unittests</span>'.format(name, cnt)
        for cnt, name in sorted(((cnt, name) for name, cnt in java_issues.items()), reverse=True)
    ])

    modules_dnf = ul('Unittests that did not finish', [
        '<b>{}</b>'.format(r[Col.UNITTEST])
        for r in rows if r[Col.NUM_ERRORS] == -1
    ])
    
    usecase_scores = dict()
    for usecase_name, usecase_modules in USE_CASE_GROUPS.items():
        score_sum = 0
        for m in usecase_modules:
            for r in rows:
                if ("test_" + m + ".py") == r[Col.UNITTEST]:
                    if r[Col.NUM_PASSES] > 0 and r[Col.NUM_TESTS] > 0:
                        score_sum += r[Col.NUM_PASSES] / r[Col.NUM_TESTS]
        usecase_scores[usecase_name] = score_sum / len(usecase_modules)
            
    
    use_case_stats_info = ul("<b>Summary per Use Case</b>", 
                                [ grid((progress_bar(avg_score * 100, color="info"), 3), '<b>{}</b>'.format(usecase_name)) +
                                  grid(", ".join(USE_CASE_GROUPS[usecase_name])) for usecase_name, avg_score in usecase_scores.items()])

    total_stats_info = ul("<b>Summary</b>", [
        grid('<b># total</b> unittests: {}'.format(totals[Stat.UT_TOTAL])),
        grid((progress_bar(totals[Stat.UT_PERCENT_RUNS], color="info"), 3),
             '<b># unittest</b> which run: {}'.format(totals[Stat.UT_RUNS])),
        grid((progress_bar(totals[Stat.UT_PERCENT_PASS], color="success"), 3),
             '<b># unittest</b> which pass: {}'.format(totals[Stat.UT_PASS])),
        grid('<b># tests</b> which run: {}'.format(totals[Stat.TEST_RUNS])),
        grid((progress_bar(totals[Stat.TEST_PERCENT_PASS], color="info"), 3),
             '<b># tests</b> which pass: {}'.format(totals[Stat.TEST_PASS])),
    ])

    table_stats = table('stats', CSV_HEADER, rows)

    content = ' <br> '.join([use_case_stats_info,
                             total_stats_info,
                             table_stats,
                             missing_modules_info,
                             cannot_import_modules_info,
                             java_issues_info,
                             modules_dnf])

    report = HTML_TEMPLATE.format(
        title='GraalPython Unittests Stats',
        bootstrap_version='3.3.7',
        bootstrap_css_integrity='sha384-BVYiiSIFeK1dGmJRAkycuHAHRg32OmUcww7on3RYdg4Va+PmSTsz/K68vbdEjh4u',
        bootstrap_theme_css_integrity='sha384-rHyoN1iRsVXV4nD0JutlnGaslCJuC7uwjduW9SVrLvRYooPp2bWYgmgJQIXwl/Sp',
        bootstrap_js_integrity='sha384-Tc5IQib027qvyjSMfHjOMaLkfuWVxZxUPnCJA7l2mCWNIpG9mGCD8wGNIcPD7Txa',
        fontawesome_version='4.7.0',
        jquery_version='3.2.1',
        datatables_version='1.10.15',
        navbar_links='',
        scripts=scripts.format(table_id='stats', datatables_version='1.10.15'),
        content=content,
        current_date_time=current_date)

    with open(report_name, 'w') as HTML:
        HTML.write(report)


# ----------------------------------------------------------------------------------------------------------------------
#
# main tool
#
# ----------------------------------------------------------------------------------------------------------------------
def main(prog, args):
    parser = argparse.ArgumentParser(prog=prog,
                                     description="Run the standard python unittests.")
    parser.add_argument("-v", "--verbose", help="Verbose output.", action="store_true")
    parser.add_argument("-n", "--no_cpython", help="Do not run the tests with cpython (for comparison).",
                        action="store_true")
    parser.add_argument("-l", "--limit", help="Limit the number of unittests to run.", default=None, type=int)
    parser.add_argument("-t", "--tests_path", help="Unittests path.", default=PATH_UNITTESTS)
    parser.add_argument("-T", "--timeout", help="Timeout per unittest run (seconds).", default=TIMEOUT, type=int)
    parser.add_argument("-o", "--only_tests", help="Run only these unittests (comma sep values).", default=None)
    parser.add_argument("-s", "--skip_tests", help="Run all unittests except (comma sep values)."
                                                   "the only_tets option takes precedence", default=None)
    parser.add_argument("-r", "--regression_running_tests", help="Regression threshold for running tests.", type=float,
                        default=None)
    parser.add_argument("-g", "--gate", help="Run in gate mode (Skip cpython runs; Do not upload results; "
                                             "Detect regressions).", action="store_true")
    parser.add_argument("path", help="Path to store the csv output and logs to.", nargs='?', default=None)

    global flags
    flags = parser.parse_args(args=args)

    current_date = strftime("%Y-%m-%d", gmtime())

    log("[INFO] current date        : {}", current_date)
    log("[INFO] unittests path      : {}", flags.tests_path)
    if flags.path:
        log("[INFO] results (save) path : {}", flags.path)
    else:
        log("[INFO] results will not be saved remotely")

    if flags.gate:
        log("[INFO] running in gate mode")
        if not flags.regression_running_tests:
            log("[WARNING] --regression_running_tests not set while in gate mode. "
                "Regression detection will not be performed")

    def _fmt(t):
        t = t.strip()
        return os.path.join(flags.tests_path, t if t.endswith(".py") else t + ".py")

    if flags.only_tests:
        only_tests = set([_fmt(test) for test in flags.only_tests.split(",")])
        unittests = [t for t in get_unittests(flags.tests_path) if t in only_tests]
    else:
        skip_tests = set([_fmt(test) for test in flags.skip_tests.split(",")]) if flags.skip_tests else None
        unittests = get_unittests(flags.tests_path, limit=flags.limit, skip_tests=skip_tests)

    # get cpython stats
    if not flags.gate and not flags.no_cpython:
        log(HR)
        log("[INFO] get cpython stats")
        cpy_results = run_unittests(unittests, 60 * 5, with_cpython=True)
        cpy_stats = process_output('\n'.join(cpy_results))[-1]
        # handle the timeout
        timeout = flags.timeout if flags.timeout else None
    else:
        cpy_stats = None
        # handle the timeout
        timeout = flags.timeout if flags.timeout else 60 * 5  # 5 minutes if no value specified (in gate mode only)

    # get graalpython stats
    log(HR)
    log("[INFO] get graalpython stats")
    results = run_unittests(unittests, timeout, with_cpython=False)
    txt_report_path = file_name(TXT_RESULTS_NAME, current_date)
    output = save_as_txt(txt_report_path, results)

    unittests, error_messages, java_exceptions, stats = process_output(output)

    csv_report_path = file_name(CSV_RESULTS_NAME, current_date)
    rows, totals = save_as_csv(csv_report_path, unittests, error_messages, java_exceptions, stats, cpy_stats=cpy_stats)

    missing_modules = process_errors(unittests, error_messages, 'ModuleNotFoundError',
                                     msg_processor=get_missing_module)
    log("[MISSING MODULES] \n{}", pformat(dict(missing_modules)))

    cannot_import_modules = process_errors(unittests, error_messages, err='ImportError',
                                           msg_processor=get_cannot_import_module)
    log("[CANNOT IMPORT MODULES] \n{}", pformat(dict(cannot_import_modules)))

    java_issues = process_errors(unittests, java_exceptions)
    log("[JAVA ISSUES] \n{}", pformat(dict(java_issues)))

    html_report_path = file_name(HTML_RESULTS_NAME, current_date)
    if not flags.gate:
        save_as_html(html_report_path, rows, totals, missing_modules, cannot_import_modules, java_issues, current_date)

    if not flags.gate and flags.path:
        log("[SAVE] saving results to {} ... ", flags.path)
        scp(txt_report_path, flags.path)
        scp(csv_report_path, flags.path)
        scp(html_report_path, flags.path)

    gate_failed = False
    if flags.gate and flags.regression_running_tests:
        log("[REGRESSION] detecting regression, acceptance threshold = {}%".format(
            flags.regression_running_tests * 100))
        csv_files = list(filter(lambda entry: True if PTRN_VALID_CSV_NAME.match(entry) else False, ssh_ls(flags.path)))
        last_csv = csv_files[-1]
        # log('\n'.join(csv_files))
        # read the remote csv and extract stats
        log("[REGRESSION] comparing against: {}".format(last_csv))
        scp('{}/{}'.format(flags.path, last_csv), '.', destination_name=last_csv)
        rows = read_csv(last_csv)
        prev_totals = {
            Col.NUM_TESTS: int(rows[-1][1]),
            Col.NUM_FAILS: int(rows[-1][2]),
            Col.NUM_ERRORS: int(rows[-1][3]),
            Col.NUM_SKIPPED: int(rows[-1][4]),
            Col.NUM_PASSES: int(rows[-1][5]),
        }
        print(prev_totals)
        if float(totals[Col.NUM_TESTS]) < float(prev_totals[Col.NUM_TESTS]) * (1.0 - flags.regression_running_tests):
            log("[REGRESSION] REGRESSION DETECTED, passed {} tests vs {} from {}".format(
                totals[Col.NUM_TESTS], prev_totals[Col.NUM_TESTS], last_csv))
            gate_failed = True
        else:
            log("[REGRESSION] no regression detected")

    log("[DONE]")
    if flags.gate and gate_failed:
        exit(1)


if __name__ == "__main__":
    main(sys.argv[0], sys.argv[1:])
