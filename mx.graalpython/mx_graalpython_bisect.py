# Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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

import argparse
import os
import re
import shlex
import sys
import types

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
        ['log', '--first-parent', '--format=format:%H', '{}^..{}'.format(good, bad)],
        abortOnError=True,
    ).splitlines()
    if not commits:
        raise RuntimeError("No merge commits found in the range. Did you swap good and bad?")
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
            raise RuntimeError(
                "Didn't detect a regression - less that 3% difference between good value "
                "{} and bad value {}".format(values[good_index], values[bad_index])
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
        out = ["{} {}".format(level_marker, self.repo_name)]
        for index, (commit, value) in enumerate(zip(self.commits, self.values)):
            if value is not None:
                out.append("{} {} {:6.6} s {}".format(level_marker, commit, value, get_message(self.suite, commit)))
            if self.subresults and index in self.subresults:
                out.append(self.subresults[index].visualize(level + 1))
        return '\n'.join(out)

    def summarize(self):
        if self.bad_commit and self.good_commit:
            for subresult in self.subresults.values():
                sub = subresult.summarize()
                if sub:
                    return sub
            return ("Detected bad commit in {} repository:\n{} {}"
                    .format(self.repo_name, self.bad_commit, get_message(self.suite, self.bad_commit)))
        return ''


def _bisect_benchmark(argv, initial_branch, email_to):
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
        args.benchmark_criterion = sec.get('benchmark_criterion', 'BEST')
        args.enterprise = sec.getboolean('enterprise', False)
    else:
        parser = argparse.ArgumentParser()
        parser.add_argument('bad', help="Bad commit for bisection")
        parser.add_argument('good', help="Good commit for bisection")
        parser.add_argument('build_command', help="Command to run in order to build the configuration")
        parser.add_argument('benchmark_command',
                            help="Command to run in order to run the benchmark. Output needs to be in mx's format")
        parser.add_argument('--benchmark-criterion', default='BEST',
                            help="Which result parameter should be used for comparisons")
        parser.add_argument('--enterprise', action='store_true', help="Whether to checkout graal-enterprise")
        args = parser.parse_args(argv)

    primary_suite = mx.primary_suite()

    fetched_enterprise = [False]

    def benchmark_callback(suite, commit):
        suite.vc.update_to_branch(suite.vc_dir, commit)
        mx.run_mx(['sforceimports'], suite=suite)
        if args.enterprise and suite.name != 'vm-enterprise':
            checkout_args = ['--dynamicimports', '/vm-enterprise', 'checkout-downstream', 'vm', 'vm-enterprise']
            if fetched_enterprise[0]:
                checkout_args.append('--no-fetch')
            mx.run_mx(checkout_args, out=mx.OutputCapture())
            mx.run_mx(['--env', 'ee', 'sforceimports'], suite=get_suite('/vm-enterprise'))
            fetched_enterprise[0] = True
        elif suite.name != 'vm':
            mx.run_mx(['--env', 'ce', 'sforceimports'], suite=get_suite('/vm'))
        suite.vc.update_to_branch(suite.vc_dir, commit)
        mx.run_mx(['sforceimports'], suite=suite)
        env = os.environ.copy()
        if 'CI' not in os.environ:
            env['MX_ALT_OUTPUT_ROOT'] = 'mxbuild-{}'.format(commit)
        retcode = mx.run(shlex.split(args.build_command), env=env, nonZeroIsFatal=False)
        if retcode:
            raise RuntimeError("Failed to execute the build command for {}".format(commit))
        output = mx.OutputCapture()
        retcode = mx.run(shlex.split(args.benchmark_command), env=env, out=mx.TeeOutputCapture(output),
                         nonZeroIsFatal=False)
        if retcode:
            raise RuntimeError("Failed to execute benchmark for {}".format(commit))
        match = re.search(r'{}.*duration: ([\d.]+)'.format(re.escape(args.benchmark_criterion)), output.data)
        if not match:
            raise RuntimeError("Failed to get result from the benchmark")
        return float(match.group(1))

    bad = get_commit(primary_suite, args.bad)
    good = get_commit(primary_suite, args.good)
    result = run_bisect_benchmark(primary_suite, bad, good, benchmark_callback)
    visualization = result.visualize()
    summary = result.summarize()

    print()
    print(visualization)
    print()
    print(summary)

    if 'CI' not in os.environ:
        print("You can rerun a benchmark for a particular commit using:\nMX_ALT_OUTPUT_ROOT=mxbuild-$commit {}".format(
            args.benchmark_command))

    send_email(
        initial_branch,
        email_to,
        "Bisection job has finished successfully.\n{}\n".format(summary)
        + "Note I'm just a script and I don't validate statistical significance of the above result.\n"
        + "Please take a moment to also inspect the detailed results below.\n\n{}\n\n".format(visualization)
        + os.environ.get('BUILD_URL', 'Unknown URL')
    )


def bisect_benchmark(argv):
    suite = mx.primary_suite()
    initial_branch = suite.vc.git_command(suite.vc_dir, ['rev-parse', '--abbrev-ref', 'HEAD']).strip()
    email_to = suite.vc.git_command(suite.vc_dir, ['log', '--format=%cE', '-n', '1']).strip()
    try:
        _bisect_benchmark(argv, initial_branch, email_to)
    except Exception:
        send_email(initial_branch, email_to, "Job failed.\n {}".format(os.environ.get('BUILD_URL', 'Unknown URL')))
        raise


def send_email(initial_branch, email_to, content):
    if 'BISECT_EMAIL_SMTP_SERVER' in os.environ:
        import smtplib
        from email.message import EmailMessage

        msg = EmailMessage()
        msg['Subject'] = "Bisection result for {}".format(initial_branch)
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
