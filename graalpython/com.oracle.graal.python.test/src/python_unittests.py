# Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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
import re
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


# ----------------------------------------------------------------------------------------------------------------------
#
# exec utils
#
# ----------------------------------------------------------------------------------------------------------------------
def _run_cmd(cmd, capture_on_failure=True):
    if isinstance(cmd, str):
        cmd = cmd.split(" ")
    assert isinstance(cmd, (list, tuple))

    log("[EXEC] cmd: {} ...".format(' '.join(cmd)))
    success = True
    output = None

    try:
        output = subprocess.check_output(cmd, stderr=subprocess.STDOUT)
    except subprocess.CalledProcessError as e:
        log("[ERR] Could not execute CMD. Reason: {}".format(e))
        if capture_on_failure:
            output = e.output

    return success, output.decode("utf-8", "ignore")


def scp(results_file_path, destination_path, destination_name=None):
    dst_path = destination_name if destination_name else os.path.basename(results_file_path)
    remote_dst = os.path.join(destination_path, dst_path)
    cmd = ['scp', results_file_path, remote_dst]
    return _run_cmd(cmd)[0]


def _run_unittest(test_path, with_cpython=False):
    if with_cpython:
        cmd = ["python3", test_path, "-v"]
    else:
        cmd = ["mx", "python3", "--python.CatchAllExceptions=true", test_path, "-v"]
    success, output = _run_cmd(cmd)
    output = '''
##############################################################
#### running: {} 
    '''.format(test_path) + output
    return success, output


TIMEOUT = 60 * 20  # 20 mins per unittest wait time max ...


def run_unittests(unittests, timeout, with_cpython=False):
    assert isinstance(unittests, (list, tuple))
    num_unittests = len(unittests)
    log("[EXEC] running {} unittests ... ", num_unittests)
    log("[EXEC] timeout per unittest: {} seconds", timeout)
    results = []

    pool = Pool()
    for ut in unittests:
        results.append(pool.apply_async(_run_unittest, args=(ut, with_cpython)))
    pool.close()

    log("[INFO] collect results ... ")
    out = []
    timed_out = []
    for i, res in enumerate(results):
        try:
            _, output = res.get(timeout)
            out.append(output)
        except TimeoutError:
            log("[ERR] timeout while getting results for {}, skipping!", unittests[i])
            timed_out.append(unittests[i])
        log("[PROGRESS] {} / {}: \t {}%", i+1, num_unittests, int(((i+1) * 100.0) / num_unittests))

    if timed_out:
        log(HR)
        for t in timed_out:
            log("[TIMEOUT] skipped: {}", t)
        log(HR)
    log("[STATS] processed {} out of {} unittests", num_unittests - len(timed_out), num_unittests)
    pool.terminate()
    pool.join()
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


# ----------------------------------------------------------------------------------------------------------------------
#
# result (output processing)
#
# ----------------------------------------------------------------------------------------------------------------------
class StatEntry(object):
    def __init__(self):
        self.num_tests = -1
        self.num_errors = -1
        self.num_fails = -1
        self.num_skipped = -1

    def all_ok(self):
        self.num_fails = 0
        self.num_errors = 0
        self.num_skipped = 0

    @property
    def num_passes(self):
        if self.num_tests > 0:
            return self.num_tests - (self.num_fails + self.num_errors + self.num_skipped)
        return -1


def process_output(output_lines):
    if isinstance(output_lines, str):
        output_lines = output_lines.split("\n")

    unittests = []
    error_messages = defaultdict(set)
    java_exceptions = defaultdict(set)
    stats = defaultdict(StatEntry)

    for line in output_lines:
        match = re.match(PTRN_UNITTEST, line)
        if match:
            unittests.append(match.group('unittest'))
            continue

        # extract python reported python error messages
        match = re.match(PTRN_ERROR, line)
        if match:
            error_messages[unittests[-1]].add((match.group('error'), match.group('message')))
            continue

        # extract java exceptions
        match = re.match(PTRN_JAVA_EXCEPTION, line)
        if match:
            java_exceptions[unittests[-1]].add((match.group('exception'), match.group('message')))
            continue

        # stats
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
                Col.PYTHON_ERRORS: dumps(list(unittest_errmsg)),
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
        if 0.0 <= value <= 1.0:
            value = 100 * value
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
                return '<code class="h6">{}</code>'.format(value)
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
                _class = "text-danger" if value < 0 else "text-muted"
            return '<span class="{} h6"><b>{}</b></span>'.format(_class, value)

        _tbody = '\n'.join([
                '<tr class="{cls}"><td>{i}</td>{vals}</tr>'.format(
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

    content = ' <br> '.join([total_stats_info, table_stats,
                             missing_modules_info,
                             cannot_import_modules_info,
                             java_issues_info])

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
    parser.add_argument("-l", "--limit", help="Limit the number of unittests to run.", default=None, type=int)
    parser.add_argument("-t", "--tests_path", help="Unittests path.", default=PATH_UNITTESTS)
    parser.add_argument("-T", "--timeout", help="Timeout per unittest run.", default=TIMEOUT, type=int)
    parser.add_argument("-o", "--only_tests", help="Run only these unittests (comma sep values).", default=None)
    parser.add_argument("-s", "--skip_tests", help="Run all unittests except (comma sep values)."
                                                   "the only_tets option takes precedence", default=None)
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
    log(HR)
    log("[INFO] get cpython stats")
    cpy_results = run_unittests(unittests, flags.timeout, with_cpython=True)
    cpy_stats = process_output('\n'.join(cpy_results))[-1]

    # get graalpython stats
    log(HR)
    log("[INFO] get graalpython stats")
    results = run_unittests(unittests, flags.timeout, with_cpython=False)
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
    save_as_html(html_report_path, rows, totals, missing_modules, cannot_import_modules, java_issues, current_date)

    if flags.path:
        log("[SAVE] saving results to {} ... ", flags.path)
        scp(txt_report_path, flags.path)
        scp(csv_report_path, flags.path)
        scp(html_report_path, flags.path)

    log("[DONE]")


if __name__ == "__main__":
    main(sys.argv[0], sys.argv[1:])
