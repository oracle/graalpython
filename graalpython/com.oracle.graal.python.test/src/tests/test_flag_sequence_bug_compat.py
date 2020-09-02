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

def test_op_order():
    class C(object):
        def __radd__(self, other):        
            return "RADD"
        def __add__(self, other):
            return "ADD"

    # bytearray    
    assert bytearray(b'ab') + C() == "RADD"
    assert C() + bytearray(b'ab') == "ADD"    

    class BA(bytearray):
        pass

#    assert BA(b'abc') + C() == "RADD"
    assert C() + BA(b'abc') == "ADD"
    
    # bytes
    assert b'ab' + C() == "RADD"
    assert C() + b'ab' == "ADD"    

    class B(bytes):
        pass

#    assert B(b'ab') + C() == "RADD"
    assert C() + B(b'ab') == "ADD"
    
    # list
    assert [1,2] + C() == "RADD"
    assert C() + [1,2] == "ADD"    

    class L(list):
        pass

#    assert L([1,2]) + C() == "RADD"
    assert C() + L([1,2]) == "ADD"
    
    # tuple
    assert (1, 2)+ C() == "RADD"
    assert C() + (1,2) == "ADD"    

    class T(tuple):
        pass
     
#    assert T((1,2)) + C() == "RADD"
    assert C() +  T((1,2)) == "ADD"
    
    # str
    assert ":" + C() == "RADD"
    assert C() + ":" == "ADD"    

    class S(str):
        pass

#    assert S(":") + C() == "RADD"
    assert C() + S(":") == "ADD"        
    
    # int
    assert 1 + C() == "RADD"
    assert C() + 1 == "ADD"
    
    class I(int):
        pass

    assert I(1) + C() == "RADD"
    assert C() + I(1) == "ADD"
    
    
    
    