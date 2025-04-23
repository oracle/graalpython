import re
import time
from collections import defaultdict
import pytest
import _valgrind

class Timer:

    def __init__(self, nodeid):
        self.nodeid = nodeid
        self.start = None
        self.stop = None

    def __enter__(self):
        if self.start is not None:
            raise ValueError('You cannot use "with timer:" more than once')
        _valgrind.lib.callgrind_start()
        self.start = time.time()

    def __exit__(self, etype, evalue, tb):
        self.stop = time.time()
        _valgrind.lib.callgrind_stop()

    def __str__(self):
        if self.start is None:
            return '[NO TIMING]'
        if self.stop is None:
            return '[IN-PROGRESS]'
        usec = (self.stop - self.start) * 1000
        return f'{usec:.2f} us'

    @property
    def elapsed(self):
        if self.start is not None and self.stop is not None:
            return self.stop - self.start
        return None


class TimerSession:

    NODEID = re.compile(r'(.*)\[(.*)\]')

    def __init__(self):
        self.apis = set() # ['cpy', 'hpy', ...]
        self.table = defaultdict(dict)   # {shortid: {api: timer}}
        self.timers = {}  # nodeid -> Timer

    def new_timer(self, nodeid):
        shortid, api = self.split_nodeid(nodeid)
        timer = Timer(nodeid)
        self.apis.add(api)
        self.table[shortid][api] = timer
        self.timers[nodeid] = timer
        return timer

    def get_timer(self, nodeid):
        return self.timers.get(nodeid)

    def split_nodeid(self, nodeid):
        shortid = '::'.join(nodeid.split('::')[-2:]) # take only class::function
        m = self.NODEID.match(shortid)
        if not m:
            return shortid, ''
        return m.group(1), m.group(2)

    def format_ratio(self, reference, value):
        if reference and reference.elapsed and value and value.elapsed:
            ratio = value.elapsed / reference.elapsed
            return f'[{ratio:.2f}]'
        return ''

    def display_summary(self, tr):
        w = tr.write_line
        w('')
        tr.write_sep('=', 'BENCHMARKS', cyan=True)
        w(' '*40 + '             cpy                    hpy')
        w(' '*40 + '----------------    -------------------')
        for shortid, timings in self.table.items():
            cpy = timings.get('cpy')
            hpy = timings.get('hpy')
            hpy_ratio = self.format_ratio(cpy, hpy)
            cpy = cpy or ''
            hpy = hpy or ''
            w(f'{shortid:<40} {cpy!s:>15} {hpy!s:>15} {hpy_ratio}')
        w('')



@pytest.fixture
def timer(request, api):
    nodeid = request.node.nodeid
    return request.config._timersession.new_timer(nodeid)

def pytest_configure(config):
    config._timersession = TimerSession()
    config.addinivalue_line("markers", "hpy: mark modules using the HPy API")
    config.addinivalue_line("markers", "cpy: mark modules using the old Python/C API")

def pytest_addoption(parser):
    parser.addoption(
        "--fast", action="store_true", default=False, help="run microbench faster"
    )
    parser.addoption(
        "--slow", action="store_true", default=False, help="run microbench slower"
    )


VERBOSE_TEST_NAME_LENGTH = 90

@pytest.hookimpl(hookwrapper=True)
def pytest_report_teststatus(report, config):
    outcome = yield
    category, letter, word = outcome.get_result()
    timer = config._timersession.get_timer(report.nodeid)
    if category == 'passed' and timer:
        L = VERBOSE_TEST_NAME_LENGTH - len(report.nodeid)
        word = str(timer).rjust(L)
        markup = None
        if timer.elapsed is None:
            markup = {'yellow': True}
        outcome.force_result((category, letter, (word, markup)))

def pytest_terminal_summary(terminalreporter, config):
    config._timersession.display_summary(terminalreporter)
