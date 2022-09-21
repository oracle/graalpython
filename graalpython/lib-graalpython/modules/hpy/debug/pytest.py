# MIT License
# 
# Copyright (c) 2021, Oracle and/or its affiliates.
# Copyright (c) 2019 pyhandle
# 
# Permission is hereby granted, free of charge, to any person obtaining a copy
# of this software and associated documentation files (the "Software"), to deal
# in the Software without restriction, including without limitation the rights
# to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
# copies of the Software, and to permit persons to whom the Software is
# furnished to do so, subject to the following conditions:
# 
# The above copyright notice and this permission notice shall be included in all
# copies or substantial portions of the Software.
# 
# THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
# IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
# FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
# AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
# LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
# OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
# SOFTWARE.

# hpy.debug / pytest integration

import pytest
from .leakdetector import LeakDetector

# For now "hpy_debug" just does leak detection, but in the future it might
# grows extra features: that's why it's called generically "hpy_debug" instead
# of "detect_leaks".

# NOTE: the fixture itself is currently untested :(. It turns out that testing
# that the fixture raises during the teardown is complicated and probably
# requires to write a full-fledged plugin. We might want to turn this into a
# real plugin in the future, but for now I think this is enough.

# pypy still uses a very ancient version of pytest, 2.9.2: pytest<3.x needs to
# use @yield_fixture, which is deprecated in newer version of pytest (where
# you can just use @fixture)
if pytest.__version__ < '3':
    fixture = pytest.yield_fixture
else:
    fixture = pytest.fixture

@fixture
def hpy_debug(request):
    """
    pytest fixture which makes it possible to control hpy.debug from within a test.

    In particular, it automatically check that the test doesn't leak.
    """
    with LeakDetector() as ld:
        yield ld
