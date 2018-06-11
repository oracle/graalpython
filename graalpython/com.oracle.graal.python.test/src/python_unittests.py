import csv
import os
import re
import subprocess
from collections import defaultdict
from json import dumps
from multiprocessing import Pool
from pprint import pformat

import argparse
import sys
from time import gmtime, strftime

# global CLI flags
flags = None

# constants
PATH_UNITTESTS = "graalpython/lib-python/3/test/"

CSV_RESULTS_NAME = "unittests.csv"
HTML_RESULTS_NAME = "unittests.html"

PTRN_ERROR = re.compile(r'^(?P<error>[A-Z][a-z][a-zA-Z]+):(?P<message>.*)$')
PTRN_UNITTEST = re.compile(r'^#### running: graalpython/lib-python/3/test/(?P<unittest>.*)$')
PTRN_NUM_TESTS = re.compile(r'^Ran (?P<num_tests>\d+) test.*$')
PTRN_NUM_ERRORS = re.compile(
    r'^FAILED \((failures=(?P<failures>\d+))?(, )?(errors=(?P<errors>\d+))?(, )?(skipped=(?P<skipped>\d+))?\)$')


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
    idx = name.rindex('.')
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


def _run_unittest(test_path):
    cmd = ["mx", "python3", "--python.CatchAllExceptions=true", test_path]
    success, output = _run_cmd(cmd)
    output = '''
##############################################################
#### running: {} 
    '''.format(test_path) + output
    return success, output


def run_unittests(unittests):
    assert isinstance(unittests, (list, tuple))
    log("[EXEC] running {} unittests ... ".format(len(unittests)))
    results = []
    pool = Pool()
    for ut in unittests:
        results.append(pool.apply_async(_run_unittest, args=(ut,)))
    pool.close()
    pool.join()
    return [r.get()[1] for r in results]


def get_unittests(base_tests_path, limit=None, sort=True):
    def _sorter(iterable):
        return sorted(iterable) if sort else iterable
    unittests = [os.path.join(base_tests_path, name)
                 for name in _sorter(os.listdir(base_tests_path))
                 if name.startswith("test_")]
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
        self.num_errors = 0
        self.num_fails = 0
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

    return unittests, error_messages, stats


# ----------------------------------------------------------------------------------------------------------------------
#
# python  error processing
#
# ----------------------------------------------------------------------------------------------------------------------
def process_errors(unittests, error_messages, error, msg_processor):
    missing_modules = defaultdict(lambda: 0)
    for ut in unittests:
        errors = error_messages[ut]
        for name in map(msg_processor, (msg for err, msg in errors if err == error)):
            missing_modules[name] = missing_modules[name] + 1

    return missing_modules


PTRN_MODULE_NOT_FOUND = re.compile(r'.*ModuleNotFound: \'(?P<module>.*)\'\..*', re.DOTALL)


def get_missing_module(msg):
    match = re.match(PTRN_MODULE_NOT_FOUND, msg)
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


CSV_HEADER = [
    Col.UNITTEST,
    Col.NUM_TESTS,
    Col.NUM_FAILS,
    Col.NUM_ERRORS,
    Col.NUM_SKIPPED,
    Col.NUM_PASSES,
    Col.PYTHON_ERRORS
]


def save_as_csv(report_path, unittests, error_messages, stats, current_date):
    rows = []
    with open(file_name(CSV_RESULTS_NAME, current_date), 'w') as CSV:
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
            unittest_errmsg = error_messages[unittest]
            rows.append({
                Col.UNITTEST: unittest,
                Col.NUM_TESTS: unittest_stats.num_tests,
                Col.NUM_FAILS: unittest_stats.num_fails,
                Col.NUM_ERRORS: unittest_stats.num_errors,
                Col.NUM_SKIPPED: unittest_stats.num_skipped,
                Col.NUM_PASSES: unittest_stats.num_passes,
                Col.PYTHON_ERRORS: dumps(list(unittest_errmsg))
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

        _all_runs = len(unittests)-total_not_run_at_all
        _all_total = len(unittests)
        _percent_all_runs = float(_all_runs) / float(_all_total) * 100.0
        _percent_all_full_passes = float(total_pass_all) / float(_all_total) * 100.0

        _test_runs = totals[Col.NUM_PASSES]
        _test_total = totals[Col.NUM_TESTS]
        _percent_test_runs = float(_test_runs) / float(_test_total) * 100.0 if _test_total else 0

        rows.append({
            Col.UNITTEST: 'TOTAL',
            Col.NUM_TESTS: totals[Col.NUM_TESTS],
            Col.NUM_FAILS: totals[Col.NUM_FAILS],
            Col.NUM_ERRORS: totals[Col.NUM_ERRORS],
            Col.NUM_SKIPPED: totals[Col.NUM_SKIPPED],
            Col.NUM_PASSES: totals[Col.NUM_PASSES],
            Col.PYTHON_ERRORS: 'Could run {0}/{1} unittests ({2:.2f}%). Unittests which pass completely: {3:.2f}%. '
                               'Of the ones which ran, could run: {4}/{5} tests ({6:.2f}%)'.format(
                _all_runs, _all_total, _percent_all_runs, _percent_all_full_passes,
                _test_runs, _test_total, _percent_test_runs)
        })

        writer = csv.DictWriter(CSV, fieldnames=CSV_HEADER)
        writer.writeheader()
        for row in rows:
            writer.writerow(row)

        return rows


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


def save_as_html(report_name, rows, missing_modules, current_date):
    def table(tid, tcols, trows):
        thead = '''
            <tr class="text-align: right;">
                <th data-orderable="false">&nbsp;</th>
                {columns}
            </tr>
            '''.format(columns='\n'.join(['<th>{}</th>'.format(c) for c in tcols]))

        format_val = lambda row, k: '<code>{}</code>'.format(row[k]) if k == Col.PYTHON_ERRORS else row[k]

        tbody = '\n'.join([
                '<tr class="{cls}"><td>{i}</td>{vals}</tr>'.format(
                    cls='info' if i % 2 == 0 else '', i=i,
                    vals=' '.join(['<td>{}</td>'.format(format_val(row, k)) for k in tcols]))
                for i, row in enumerate(trows)])

        return '''
        <div class="container-fluid">
            <div class="panel panel-default">
                <div class="panel-heading clickable">
                    <h3 class="panel-title"><i class="fa fa-minus-square-o" aria-hidden="true">&nbsp;&nbsp;</i>{title}</h3>
                </div>
                <table id="{tid}" class="table {tclass}" cellspacing="0" width="100%">
                    <thead>{thead}</thead><tbody>{tbody}</tbody>
                </table>
            </div>
        </div>
        '''.format(title='unittest run statistics', tid=tid, tclass='', thead=thead, tbody=tbody)

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

    modules_info = '''
        <div class="container-fluid">
            <div class="panel panel-default">
                <div class="panel-heading clickable">
                    <h3 class="panel-title"><i class="fa fa-minus-square-o" aria-hidden="true">&nbsp;&nbsp;</i>{title}</h3>
                </div>
                <ul class="list-group">{content}</ul>
            </div>
        </div>
    '''.format(title='missing modules', content='\n'.join([
        '<li class="list-group-item"><b>{}</b>&nbsp;<span class="text-muted">count: {}</span></li>'.format(name, cnt)
        for cnt, name in sorted(((cnt, name) for name, cnt in missing_modules.items()))
    ]))

    content = modules_info + table('stats', CSV_HEADER, rows)

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

    unittests = get_unittests(flags.tests_path, limit=flags.limit)
    results = run_unittests(unittests)
    unittests, error_messages, stats = process_output('\n'.join(results))

    csv_report_path = file_name(CSV_RESULTS_NAME, current_date)
    rows = save_as_csv(csv_report_path, unittests, error_messages, stats, current_date)

    missing_modules = process_errors(unittests, error_messages, 'ModuleNotFoundError', get_missing_module)
    log("[MISSING MODULES] \n{}", pformat(dict(missing_modules)))

    html_report_path = file_name(HTML_RESULTS_NAME, current_date)
    save_as_html(html_report_path, rows, missing_modules, current_date)

    if flags.path:
        log("[SAVE] saving results to {} ... ", flags.path)
        scp(csv_report_path, flags.path)
        scp(html_report_path, flags.path)

    log("[DONE]")


if __name__ == "__main__":
    main(sys.argv[0], sys.argv[1:])
