import re
import os
import sys
import argparse
import shlex
import types
import configparser

import mx


def get_suite(name):
    suite_name = name.lstrip('/')
    suite = mx.suite(suite_name, fatalIfMissing=False)
    if not suite:
        suite = mx.primary_suite().import_suite(suite_name, version=None, urlinfos=None, in_subdir=name.startswith('/'))
    assert suite
    return suite


def get_downstream_suite(suite):
    downstreams = {
        'graalpython-apptests': 'graalpython',
        'graalpython-extensions': 'graalpython',
        'graalpython': '/vm',
        'vm': '/vm-enterprise',
    }
    downstream = downstreams.get(suite.name)
    if downstream:
        return get_suite(downstream)


def get_commit(suite, ref='HEAD'):
    if not suite:
        return None
    return suite.vc.git_command(suite.vc_dir, ['rev-parse', ref], abortOnError=True).strip()


def get_message(suite, commit):
    return suite.vc.git_command(suite.vc_dir, ['log', '--format=%s', '-n', '1', commit]).strip()


def run_bisect_benchmark(suite, bad, good, callback, threshold=None):
    git_dir = suite.vc_dir
    commits = suite.vc.git_command(
        git_dir,
        ['log', '--first-parent', '--format=format:%H', f'{good}^..{bad}'],
        abortOnError=True,
    ).splitlines()
    if not commits:
        sys.exit("No merge commits found in the range. Did you swap good and bad?")
    downstream_suite = get_downstream_suite(suite)
    values = [None] * len(commits)
    if threshold is None:
        bad_index = 0
        good_index = len(commits) - 1
        values[bad_index] = callback(suite, bad)
        downstream_bad = get_commit(downstream_suite)
        values[good_index] = callback(suite, good)
        downstream_good = get_commit(downstream_suite)
        threshold = (values[bad_index] + values[good_index]) / 2
        if values[good_index] * 1.03 > values[bad_index]:
            sys.exit(
                "Didn't detect a regression - less that 3% difference between good value "
                f"{values[good_index]} and bad value {values[bad_index]}"
            )
    else:
        bad_index = -1
        good_index = len(commits)
        downstream_bad = None
        downstream_good = None
    while True:
        index = bad_index + ((good_index - bad_index) // 2)
        if index == bad_index or index == good_index:
            assert good_index - bad_index == 1
            break
        commit = commits[index]
        values[index] = callback(suite, commit)
        if values[index] < threshold:
            good_index = index
            downstream_good = get_commit(downstream_suite)
        else:
            bad_index = index
            downstream_bad = get_commit(downstream_suite)
    subresults = {}
    if downstream_bad and downstream_good and downstream_bad != downstream_good:
        subresult = run_bisect_benchmark(downstream_suite, downstream_bad, downstream_good, callback, threshold)
        subresults[bad_index] = subresult
    return BisectResult(suite, commits, values, good_index, bad_index, subresults)


class BisectResult:
    def __init__(self, suite, commits, values, good_index, bad_index, subresults):
        self.suite = suite
        self.commits = commits
        self.values = values
        self.good_index = good_index
        self.bad_index = bad_index
        self.subresults = subresults

    @property
    def repo_name(self):
        return os.path.basename(self.suite.vc_dir)

    @property
    def good_commit(self):
        try:
            return self.commits[self.good_index]
        except IndexError:
            return None

    @property
    def bad_commit(self):
        try:
            return self.commits[self.bad_index]
        except IndexError:
            return None

    def visualize(self, level=1):
        level_marker = '=' * level
        print(f"{level_marker} {self.repo_name}")
        for index, (commit, value) in enumerate(zip(self.commits, self.values)):
            if value is not None:
                print(f"{level_marker} {commit} {value:6.6} {get_message(self.suite, commit)}")
            if self.subresults and index in self.subresults:
                self.subresults[index].visualize(level + 1)

    def summarize(self):
        if self.bad_commit and self.good_commit:
            for subresult in self.subresults.values():
                if subresult.summarize():
                    return True
            print(f"Detected bad commit in {self.repo_name} repository:\n{self.bad_commit} {get_message(self.suite, self.bad_commit)}")
            return True
        return False


def bisect_benchmark(argv):
    if 'BISECT_BENCHMARK_CONFIG' in os.environ:
        cp = configparser.ConfigParser()
        cp.read(os.environ['BISECT_BENCHMARK_CONFIG'])
        sec = cp['bisect-benchmark']
        args = types.SimpleNamespace()
        args.bad = sec['bad']
        args.good = sec['good']
        args.build_command = sec['build_command']
        args.benchmark_command = sec['benchmark_command']
        args.benchmark_criterion = sec.get('benchmark_criterion', 'BEST')
        args.enterprise = sec.getboolean('enterprise', False)
    else:
        parser = argparse.ArgumentParser()
        parser.add_mutually_exclusive_group()
        parser.add_argument('bad')
        parser.add_argument('good')
        parser.add_argument('build_command')
        parser.add_argument('benchmark_command')
        parser.add_argument('--benchmark-criterion', default='BEST')
        parser.add_argument('--enterprise', action='store_true')
        args = parser.parse_args(argv)

    primary_suite = mx.primary_suite()

    fetched_enterprise = False

    def benchmark_callback(suite, commit):
        nonlocal fetched_enterprise
        suite.vc.update_to_branch(suite.vc_dir, commit)
        mx.run_mx(['sforceimports'], suite=suite)
        if args.enterprise and suite.name != 'vm-enterprise':
            checkout_args = ['--dynamicimports', '/vm-enterprise', 'checkout-downstream', 'vm', 'vm-enterprise']
            if fetched_enterprise:
                checkout_args.append('--no-fetch')
            mx.run_mx(checkout_args, out=mx.OutputCapture())
            mx.run_mx(['--env', 'ee', 'sforceimports'], suite=get_suite('/vm-enterprise'))
            fetched_enterprise = True
        elif suite.name != 'vm':
            mx.run_mx(['--env', 'ce', 'sforceimports'], suite=get_suite('/vm'))
        suite.vc.update_to_branch(suite.vc_dir, commit)
        mx.run_mx(['sforceimports'], suite=suite)
        env = os.environ.copy()
        if 'CI' not in os.environ:
            env['MX_ALT_OUTPUT_ROOT'] = f'mxbuild-{commit}'
        retcode = mx.run(shlex.split(args.build_command), env=env, nonZeroIsFatal=False)
        if retcode:
            sys.exit(f"Failed to execute the build command for {commit}")
        output = mx.OutputCapture()
        retcode = mx.run(shlex.split(args.benchmark_command), env=env, out=mx.TeeOutputCapture(output), nonZeroIsFatal=False)
        if retcode:
            sys.exit(f"Failed to execute benchmark for {commit}")
        match = re.search(rf'{re.escape(args.benchmark_criterion)}.*duration: ([\d.]+)', output.data)
        if not match:
            sys.exit(f"Failed to get result from the benchmark")
        return float(match.group(1))

    bad = get_commit(primary_suite, args.bad)
    good = get_commit(primary_suite, args.good)
    result = run_bisect_benchmark(primary_suite, bad, good, benchmark_callback)
    print()
    result.visualize()
    print()
    result.summarize()
    print()

    if 'CI' not in os.environ:
        print(f"You can rerun a benchmark for a particular commit using:\nMX_ALT_OUTPUT_ROOT=mxbuild-$commit {args.benchmark_command}")
