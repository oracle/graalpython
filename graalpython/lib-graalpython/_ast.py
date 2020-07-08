# Copyright (c) 2018, 2020, Oracle and/or its affiliates. All rights reserved.
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
    def __init__(self, *args, **kwargs):
        pass

    def __dir__(self):
        return []


class Add(AST):
    pass


class And(AST):
    pass


class AnnAssign(AST):
    pass


class Assert(AST):
    pass


class Assign(AST):
    pass


class AsyncFor(AST):
    pass


class AsyncFunctionDef(AST):
    pass


class AsyncWith(AST):
    pass


class Attribute(AST):
    pass


class AugAssign(AST):
    pass


class AugLoad(AST):
    pass


class AugStore(AST):
    pass


class Await(AST):
    pass


class BinOp(AST):
    pass


class BitAnd(AST):
    pass


class BitOr(AST):
    pass


class BitXor(AST):
    pass


class BoolOp(AST):
    pass


class Break(AST):
    pass


class Bytes(AST):
    pass


class Call(AST):
    pass


class ClassDef(AST):
    pass


class Compare(AST):
    pass


class Constant(AST):
    pass


class Continue(AST):
    pass


class Del(AST):
    pass


class Delete(AST):
    pass


class Dict(AST):
    pass


class DictComp(AST):
    pass


class Div(AST):
    pass


class Ellipsis(AST):
    pass


class Eq(AST):
    pass


class ExceptHandler(AST):
    pass


class Expr(AST):
    pass


class Expression(AST):
    pass


class ExtSlice(AST):
    pass


class FloorDiv(AST):
    pass


class For(AST):
    pass


class FormattedValue(AST):
    pass


class FunctionDef(AST):
    pass


class GeneratorExp(AST):
    pass


class Global(AST):
    pass


class Gt(AST):
    pass


class GtE(AST):
    pass


class If(AST):
    pass


class IfExp(AST):
    pass


class Import(AST):
    pass


class ImportFrom(AST):
    pass


class In(AST):
    pass


class Index(AST):
    pass


class Interactive(AST):
    pass


class Invert(AST):
    pass


class Is(AST):
    pass


class IsNot(AST):
    pass


class JoinedStr(AST):
    pass


class LShift(AST):
    pass


class Lambda(AST):
    pass


class List(AST):
    pass


class ListComp(AST):
    pass


class Load(AST):
    pass


class Lt(AST):
    pass


class LtE(AST):
    pass


class MatMult(AST):
    pass


class Mod(AST):
    pass


class Module(AST):
    pass


class Mult(AST):
    pass


class Name(AST):
    pass


class NameConstant(AST):
    pass


class Nonlocal(AST):
    pass


class Not(AST):
    pass


class NotEq(AST):
    pass


class NotIn(AST):
    pass


class Num(AST):
    pass


class Or(AST):
    pass


class Param(AST):
    pass


class Pass(AST):
    pass


class Pow(AST):
    pass


class RShift(AST):
    pass


class Raise(AST):
    pass


class Return(AST):
    pass


class Set(AST):
    pass


class SetComp(AST):
    pass


class Slice(AST):
    pass


class Starred(AST):
    pass


class Store(AST):
    pass


class Str(AST):
    pass


class Sub(AST):
    pass


class Subscript(AST):
    pass


class Suite(AST):
    pass


class Try(AST):
    pass


class Tuple(AST):
    pass


class UAdd(AST):
    pass


class USub(AST):
    pass


class UnaryOp(AST):
    pass


class While(AST):
    pass


class With(AST):
    pass


class Yield(AST):
    pass


class YieldFrom(AST):
    pass


class alias(AST):
    pass


class arg(AST):
    pass


class arguments(AST):
    pass


class boolop(AST):
    pass


class cmpop(AST):
    pass


class comprehension(AST):
    pass


class excepthandler(AST):
    pass


class expr(AST):
    pass


class expr_context(AST):
    pass


class keyword(AST):
    pass


class mod(AST):
    pass


class operator(AST):
    pass


class slice(AST):
    pass


class stmt(AST):
    pass


class unaryop(AST):
    pass


class withitem(AST):
    pass
