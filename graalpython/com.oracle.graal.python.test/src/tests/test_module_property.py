# Copyright (c) 2018, 2021, Oracle and/or its affiliates. All rights reserved.
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


def test_builtins():
    import array
    assert array.array.__module__ == "array" # builtin class
    assert int.__module__ == "builtins" # builtin class
    import sys
    assert sys.getrecursionlimit.__module__ == "sys" # builtin module method

def test_imported():
    import code
    assert code.InteractiveInterpreter.__module__ == "code" # class
    assert code.InteractiveInterpreter.runsource.__module__ == "code" # function
    assert code.InteractiveInterpreter().runsource.__module__ == "code" # method

class TestClass():
    def foo(self):
        pass

def test_user_class():
    assert TestClass.__module__ == __name__
    assert TestClass().__module__ == __name__
    assert TestClass.foo.__module__ == __name__
    assert TestClass().foo.__module__ == __name__
    # test redefine:
    TestClass.__module__ = "bar"
    assert TestClass.__module__ == "bar"
    t = TestClass()
    t.__module__ = "baz"
    assert t.__module__ == "baz"
    
def test_wrong_property():
    import time
    try:
        time.no_existing_property
    except AttributeError as ae:
        assert str(ae) == "module 'time' has no attribute 'no_existing_property'"
    else:
        assert False
        
def test_wrong_property_in_moudule_without_name():
    import time
    del time.__name__
    try:
        time.no_existing_property
    except AttributeError as ae:
        assert str(ae) == "module has no attribute 'no_existing_property'"
    else:
        assert False
        
