# Copyright (c) 2020, 2025, Oracle and/or its affiliates. All rights reserved.
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
import sys

import abc
import argparse
import json
import mx
import os
import re
import shlex
import types
from pathlib import Path


def print_line(l):
    print('=' * l)


SUITE = mx.suite('graalpython')
GIT = SUITE.vc
DIR = Path(SUITE.vc_dir).absolute()
GRAAL_DIR = DIR.parent / 'graal'
VM_DIR = GRAAL_DIR / 'vm'
GRAAL_ENTERPRISE_DIR = DIR.parent / 'graal-enterprise'
VM_ENTERPRISE_DIR = GRAAL_ENTERPRISE_DIR / 'vm-enterprise'

SUITE_MAPPING = {
    GRAAL_DIR: VM_DIR,
    GRAAL_ENTERPRISE_DIR: VM_ENTERPRISE_DIR,
}

DOWNSTREAM_REPO_MAPPING = {
    DIR: GRAAL_DIR,
    GRAAL_DIR: GRAAL_ENTERPRISE_DIR,
}


def get_commit(repo_path: Path, ref='HEAD'):
    return GIT.git_command(repo_path, ['rev-parse', ref], abortOnError=True).strip()


def get_message(repo_path: Path, commit):
    return GIT.git_command(repo_path, ['log', '--format=%s', '-n', '1', commit]).strip()


def run_bisect_benchmark(repo_path: Path, bad, good, callback, good_result=None, bad_result=None):
    commits = GIT.git_command(
        repo_path,
        ['log', '--first-parent', '--format=format:%H', '{}^..{}'.format(good, bad)],
        abortOnError=True,
    ).splitlines()
    if not commits:
        raise RuntimeError("No merge commits found in the range. Did you swap good and bad?")
    downstream_repo_path = DOWNSTREAM_REPO_MAPPING.get(repo_path)
    results = [None] * len(commits)
    if good_result is None and bad_result is None:
        bad_index = 0
        good_index = len(commits) - 1
        bad_result = results[bad_index] = callback(repo_path, bad)
        downstream_bad = get_commit(downstream_repo_path)
        good_result = results[good_index] = callback(repo_path, good)
        downstream_good = get_commit(downstream_repo_path)
        if not good_result.bound_is_valid(bad_result):
            raise RuntimeError(
                "Didn't detect a regression: "
                f"'good' value ({good_result}) is worse than or same as than 'bad' value ({bad_result})"
            )
        if not good_result.bound_is_significant(bad_result, 0.03):
            raise RuntimeError(
                "Didn't detect a regression: "
                f"less that 3% difference between 'good' value ({good_result}) and 'bad' value ({bad_result})"
            )
    else:
        bad_index = -1
        good_index = len(commits)
        downstream_bad = None
        downstream_good = None
    while True:
        index = bad_index + ((good_index - bad_index) // 2)
        if index in [bad_index, good_index]:
            assert good_index - bad_index == 1
            break
        commit = commits[index]
        result = results[index] = callback(repo_path, commit)
        if result.is_good(good_result, bad_result):
            good_index = index
            downstream_good = get_commit(downstream_repo_path)
        else:
            bad_index = index
            downstream_bad = get_commit(downstream_repo_path)
    subresults = {}
    if downstream_bad and downstream_good and downstream_bad != downstream_good:
        GIT.update_to_branch(DIR, commits[good_index])
        subresult = run_bisect_benchmark(downstream_repo_path, downstream_bad, downstream_good, callback, good_result,
                                         bad_result)
        subresults[bad_index] = subresult
    return BisectResult(downstream_repo_path, commits, results, good_index, bad_index, subresults)


class BisectResult:
    def __init__(self, repo_path: Path, commits, results, good_index, bad_index, dependency_results):
        self.repo_path = repo_path
        self.commits = commits
        self.results = results
        self.good_index = good_index
        self.bad_index = bad_index
        self.dependency_results = dependency_results

    @property
    def repo_name(self):
        return self.repo_path.name

    @property
    def good_commit(self):
        if 0 <= self.good_index < len(self.commits):
            return self.commits[self.good_index]

    @property
    def bad_commit(self):
        if 0 <= self.bad_index < len(self.commits):
            return self.commits[self.bad_index]

    def visualize(self, level=1):
        level_marker = '=' * level
        out = ["{} {}".format(level_marker, self.repo_name)]
        for index, (commit, value) in enumerate(zip(self.commits, self.results)):
            if value is not None:
                out.append(f"{level_marker} {commit} {value} {get_message(self.repo_path, commit)}")
            if self.dependency_results and index in self.dependency_results:
                out.append(self.dependency_results[index].visualize(level + 1))
        return '\n'.join(out)

    def summarize(self):
        if self.bad_commit and self.good_commit:
            for dependency_result in self.dependency_results.values():
                summary = dependency_result.summarize()
                if summary:
                    return summary
            return ("Detected bad commit in {} repository:\n{} {}"
                    .format(self.repo_name, self.bad_commit, get_message(self.repo_path, self.bad_commit)))
        return ''


class BenchmarkResult(abc.ABC):
    def __init__(self, value, unit=None):
        self.value = value
        self.unit = unit

    def __str__(self):
        return f'{self.value:6.6} {self.unit}'

    def bound_is_valid(self, bad_result):
        return self.is_good(self, bad_result) or not bad_result.is_good(self, bad_result)

    def bound_is_significant(self, bad_result, epsilon):
        avg = (self.value + bad_result.value) / 2
        if not avg:
            return False
        diff = abs(self.value - bad_result.value)
        return diff / avg >= epsilon

    @abc.abstractmethod
    def is_good(self, good_result, bad_result):
        pass


class LowerIsBetterResult(BenchmarkResult):
    def is_good(self, good_result, bad_result):
        threshold = (good_result.value + bad_result.value) / 2
        return self.value < threshold


class HigherIsBetterResult(BenchmarkResult):
    def is_good(self, good_result, bad_result):
        threshold = (good_result.value + bad_result.value) / 2
        return self.value > threshold


class WorksResult(BenchmarkResult):
    def is_good(self, good_result, bad_result):
        return self.value == 0

    def bound_is_significant(self, bad_result, epsilon):
        return True

    def __str__(self):
        return "works" if self.value == 0 else "doesn't work"


def _bisect_benchmark(argv, bisect_id, email_to):
    default_metric = 'time'
    if 'BISECT_BENCHMARK_CONFIG' in os.environ:
        import configparser
        cp = configparser.ConfigParser()
        cp.read(os.environ['BISECT_BENCHMARK_CONFIG'])
        sec = cp['bisect-benchmark']
        args = types.SimpleNamespace()
        args.bad = sec['bad']
        args.good = sec['good']
        args.build_command = sec['build_command']
        args.benchmark_command = sec['benchmark_command']
        args.benchmark_metric = sec.get('benchmark_metric', default_metric)
        args.benchmark_name = sec.get('benchmark_name', None)
        args.enterprise = sec.getboolean('enterprise', False)
        args.no_clean = sec.getboolean('no_clean', False)
        args.rerun_with_commands = sec.get('rerun_with_commands')
    else:
        parser = argparse.ArgumentParser()
        parser.add_argument('bad', help="Bad commit for bisection")
        parser.add_argument('good', help="Good commit for bisection")
        parser.add_argument('build_command', help="Command to run in order to build the configuration")
        parser.add_argument('benchmark_command',
                            help="Command to run in order to run the benchmark. Output needs to be in mx's format")
        parser.add_argument('benchmark_name',
                            help="Filters the results to choose only benchmarks of given name. "
                                 "Useful if the benchmark command runs multiple benchmarks.")
        parser.add_argument('--rerun-with-commands',
                            help="Re-run the bad and good commits with this benchmark command(s) "
                                 "(multiple commands separated by ';')")
        parser.add_argument(
            '--benchmark-metric', default=default_metric,
            help=(
                "Which result metric should be used for comparisons (metric.name in the result json). "
                "A special value 'WORKS' can be used to consider only the success of the benchmark command."
            ),
        )
        parser.add_argument('--enterprise', action='store_true', help="Whether to checkout graal-enterprise")
        parser.add_argument('--no-clean', action='store_true', help="Do not run 'mx clean' between runs")
        args = parser.parse_args(argv)

    def checkout(repo_path: Path, commit):
        GIT.update_to_branch(repo_path, commit)
        suite_dir = SUITE_MAPPING.get(repo_path, repo_path)
        mx.run_mx(['sforceimports'], suite=str(suite_dir))
        mx.run_mx(['--env', 'ce', 'sforceimports'], suite=str(VM_DIR))
        if args.enterprise:
            if repo_path.name != 'graal-enterprise':
                mx.run_mx(['--quiet', 'checkout-downstream', 'vm', 'vm-enterprise', '--no-fetch'],
                          suite=str(VM_ENTERPRISE_DIR))
            mx.run_mx(['--env', 'ee', 'sforceimports'], suite=str(VM_ENTERPRISE_DIR))
        GIT.update_to_branch(repo_path, commit)
        mx.run_mx(['sforceimports'], suite=str(suite_dir))
        debug_str = f"debug: {SUITE.name}={get_commit(SUITE.vc_dir)} graal={get_commit(GRAAL_DIR)}"
        if args.enterprise:
            debug_str += f" graal-enterprise={get_commit(GRAAL_ENTERPRISE_DIR)}"
        print(debug_str)

    def fetch_jdk():
        import mx_fetchjdk
        if args.enterprise:
            fetch_args = [
                '--configuration', str(GRAAL_ENTERPRISE_DIR / 'common.json'),
                '--jdk-binaries', str(GRAAL_ENTERPRISE_DIR / 'ci' / 'jdk-binaries.json'),
                'labsjdk-ee-latest',
            ]
        else:
            fetch_args = [
                '--configuration', str(GRAAL_DIR / 'common.json'),
                'labsjdk-ce-latest',
            ]
        # Awkward way to suppress the confirmation prompt
        ci = 'CI' in os.environ
        if not ci:
            os.environ['CI'] = '1'
        try:
            return mx_fetchjdk.fetch_jdk(fetch_args)
        finally:
            if not ci:
                del os.environ['CI']

    def checkout_and_build(repo_path, commit):
        checkout(repo_path, commit)
        os.environ['JAVA_HOME'] = fetch_jdk()
        build_command = shlex.split(args.build_command)
        if not args.no_clean:
            try:
                clean_command = build_command[:build_command.index('build')] + ['clean']
                retcode = mx.run(clean_command, nonZeroIsFatal=False)
                if retcode:
                    print("Warning: clean command failed")
            except ValueError:
                pass
        retcode = mx.run(build_command, nonZeroIsFatal=False)
        if retcode:
            raise RuntimeError("Failed to execute the build command for {}".format(commit))

    def benchmark_callback(repo_path: Path, commit, bench_command=args.benchmark_command):
        checkout_and_build(repo_path, commit)
        retcode = mx.run(shlex.split(bench_command), nonZeroIsFatal=False)
        if args.benchmark_metric == 'WORKS':
            return WorksResult(retcode)
        if retcode:
            raise RuntimeError("Failed to execute benchmark for {}".format(commit))

        with open('bench-results.json') as f:
            data = json.load(f)
        docs = [x for x in data['queries'] if x['metric.name'] == args.benchmark_metric]
        if args.benchmark_name:
            docs = [x for x in docs if x['benchmark'] == args.benchmark_name]
        if not docs:
            raise RuntimeError(f"Couldn't find specified metric {args.benchmark_metric!r} in the results")
        if len(docs) > 1:
            print("WARNING: found multiple results for the metric, picking the last")
        names = set([x['benchmark'] for x in docs])
        if len(names) != 1:
            print(f"WARNING: found multiple results with different benchmark name attributes: {names}. "
                  "Use benchmark_name option to filter specific benchmark name.")
        doc = docs[-1]
        result_class = HigherIsBetterResult if doc.get('metric.better', 'lower') == 'higher' else LowerIsBetterResult
        return result_class(doc['metric.value'], doc['metric.unit'])

    bad = get_commit(DIR, args.bad)
    good = get_commit(DIR, args.good)
    result = run_bisect_benchmark(DIR, bad, good, benchmark_callback)
    visualization = result.visualize()
    summary = result.summarize()

    print()
    print(visualization)
    print()
    print(summary)

    if args.rerun_with_commands:
        print('\n\nRerunning the good and bad commits with extra benchmark commands:')
        repo_path = DIR
        current_result = result
        while current_result.subresults and current_result.bad_index in current_result.subresults:
            downstream_repo_path = DOWNSTREAM_REPO_MAPPING.get(repo_path)
            next_result = current_result.subresults[current_result.bad_index]
            if not next_result.good_commit or not next_result.bad_commit:
                print(f"Next downstream repo {downstream_repo_path.name} does not have both good and bad commits")
                break
            print(f"Recursing to downstream repo: {downstream_repo_path.name}, commit: {current_result.bad_commit}")
            checkout(downstream_repo_path, current_result.bad_commit)
            current_result = next_result
            repo_path = downstream_repo_path
        for commit in [current_result.good_commit, current_result.bad_commit]:
            print_line(80)
            print("Commit: {}".format(commit))
            checkout_and_build(repo_path, commit)
            for cmd in args.rerun_with_commands.split(";"):
                print_line(40)
                mx.run(shlex.split(cmd.strip()), nonZeroIsFatal=False)

    send_email(
        bisect_id,
        email_to,
        "Bisection job has finished successfully.\n{}\n".format(summary)
        + "Note I'm just a script and I don't validate statistical significance of the above result.\n"
        + "Please take a moment to also inspect the detailed results below.\n\n{}\n\n".format(visualization)
        + os.environ.get('BUILD_URL', 'Unknown URL')
    )


def bisect_benchmark(argv):
    initial_branch = GIT.git_command(DIR, ['rev-parse', '--abbrev-ref', 'HEAD']).strip()
    initial_commit = GIT.git_command(DIR, ['log', '--format=%s', '-n', '1']).strip()
    email_to = GIT.git_command(DIR, ['log', '--format=%cE', '-n', '1']).strip()
    bisect_id = f'{initial_branch}: {initial_commit}'
    try:
        _bisect_benchmark(argv, bisect_id, email_to)
    finally:
        GIT.update_to_branch(DIR, initial_branch)


def send_email(bisect_id, email_to, content):
    if 'BISECT_EMAIL_SMTP_SERVER' in os.environ:
        import smtplib
        from email.message import EmailMessage

        msg = EmailMessage()
        msg['Subject'] = "Bisection result for {}".format(bisect_id)
        msg['From'] = os.environ['BISECT_EMAIL_FROM']
        validate_to = os.environ['BISECT_EMAIL_TO_PATTERN']
        if not re.match(validate_to, email_to):
            sys.exit("Email {} not allowed, aborting sending".format(email_to))
        msg['To'] = email_to
        msg.set_content(content)
        print(msg)
        smtp = smtplib.SMTP(os.environ['BISECT_EMAIL_SMTP_SERVER'])
        smtp.send_message(msg)
        smtp.quit()
