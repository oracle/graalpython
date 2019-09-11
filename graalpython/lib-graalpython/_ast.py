# Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.
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


PyCF_ONLY_AST = 0


class AST:
    def __dir__(self):
        return []


class mod(AST):
    pass


class expr(AST):
    pass


class stmt(AST):
    pass


class expr_context(AST):
    pass


class slice(AST):
    pass


class boolop(AST):
    pass


class operator(AST):
    pass


class BitOr(operator):
    pass


class BitXor(operator):
    pass


class BitAnd(operator):
    pass


class LShift(operator):
    pass


class RShift(operator):
    pass


class Add(operator):
    pass


class Sub(operator):
    pass


class Mult(operator):
    pass


class Div(operator):
    pass


class FloorDiv(operator):
    pass


class Mod(operator):
    pass


class Eq(operator):
    pass


class NotEq(operator):
    pass


class Lt(operator):
    pass


class LtE(operator):
    pass


class Gt(operator):
    pass


class GtE(operator):
    pass


class Pow(operator):
    pass


class Is(operator):
    pass


class IsNot(operator):
    pass


class In(operator):
    pass


class NotIn(operator):
    pass


class MatMult(operator):
    pass


class unaryop(AST):
    pass


class cmpop(AST):
    pass


class comprehension(AST):
    pass


class excepthandler(AST):
    pass


class arguments(AST):
    pass


class keyword(AST):
    pass


class alias(AST):
    pass


class Eq(cmpop):
    pass


class In(cmpop):
    pass


class Not(unaryop):
    pass


class Invert(unaryop):
    pass


class USub(unaryop):
    pass


class UAdd(unaryop):
    pass


class NotEq(cmpop):
    pass


class NotIn(cmpop):
    pass
