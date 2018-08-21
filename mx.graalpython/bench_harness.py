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

import argparse
import sys
from time import time


def _get_duration_message(i, name, duration):
    return "iteration=%s, name=%s, duration=%s".format(i, name, duration)


def benchmark(name, iterations=1):
    def fnbenchmark(func):
        def wrapper(*args, **kwargs):
            for i in range(iterations):
                start = time()
                func(*args, **kwargs)
                duration = "%.3f\n" % (time() - start)
                print(_get_duration_message(i, name, duration))
        return wrapper
    return fnbenchmark


def _call_if_defined(obj, attr_name):
    if hasattr(obj, attr_name):
        attr = getattr(obj, attr_name)
        if hasattr(attr, '__call__'):
            attr()


ATTR_SETUP = '__setup__'
ATTR_WARMUP = '__warmup__'
ATTR_BENCHMARK = '__benchmark__'


def run_benchmark(name):
    parser = argparse.ArgumentParser(description="Run specified benchmark.")
    parser.add_argument("-i", "--iterations", help="Number of iterations.", type=int)

    args = parser.parse_args()
    current_module = sys.modules[__name__]
    _call_if_defined(current_module, ATTR_SETUP)
    _call_if_defined(current_module, ATTR_WARMUP)
    bench = getattr(current_module, ATTR_BENCHMARK)
    if not bench:
        raise ValueError('%s not defined for %s'.format(ATTR_BENCHMARK, name))

    benchmark(name, iterations=args.iterations)(bench)()
