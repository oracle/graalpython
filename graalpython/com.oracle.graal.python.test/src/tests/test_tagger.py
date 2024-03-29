# Copyright (c) 2020, 2024, Oracle and/or its affiliates. All rights reserved.
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

# test for the output parsing in test_tagged_unittests
from . import test_tagged_unittests

data = """
test_1 (module.test_1) ... ok
test2 (module.test2) ... FAIL
random output
test_2 (module.test_2) ... skipped 'requires Windows'
test_3 (module.test_3) ... skipped 'some
multiline reason'
some unrelated output
test_x (module.test_x)
Some docstring that
spans
multiple
lines ... ERROR
test_y (module.test_y)
Some docstring ... Some warning
ok
unrelated stuff
test_z (module.test_z) ... ok
testA (module.testA) ... test_B (module.test_B) ... ok
test_T (module.test_T) ... the test printed ok somewhere
skipped 'foo'
test_foo (module.test_foo) ... test_bar (module.test_bar) ... null
"""

expected = [
    ('module.test_1', 'ok'),
    ('module.test2', 'FAIL'),
    ('module.test_2', "skipped 'requires Windows'"),
    ('module.test_3', "skipped 'some\nmultiline reason'"),
    ('module.test_x', 'ERROR'),
    ('module.test_y', 'ok'),
    ('module.test_z', 'ok'),
    ('module.test_B', 'ok'),
    ('module.test_T', "skipped 'foo'"),
]


def test_re():
    parsed = list(test_tagged_unittests.parse_unittest_output(data))
    assert parsed == expected, parsed
