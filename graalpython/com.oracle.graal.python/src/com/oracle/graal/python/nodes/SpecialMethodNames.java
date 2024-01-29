/*
 * Copyright (c) 2018, 2024, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.oracle.graal.python.nodes;

import static com.oracle.graal.python.util.PythonUtils.tsLiteral;

import com.oracle.truffle.api.strings.TruffleString;

public abstract class SpecialMethodNames {

    public static final String J___NEW__ = "__new__";
    public static final TruffleString T___NEW__ = tsLiteral(J___NEW__);

    public static final String J___ALLOC__ = "__alloc__";
    public static final TruffleString T___ALLOC__ = tsLiteral(J___ALLOC__);

    public static final String J___INIT__ = "__init__";
    public static final TruffleString T___INIT__ = tsLiteral(J___INIT__);

    public static final String J___CEIL__ = "__ceil__";
    public static final TruffleString T___CEIL__ = tsLiteral(J___CEIL__);

    public static final String J___COPY__ = "__copy__";
    public static final TruffleString T___COPY__ = tsLiteral(J___COPY__);

    public static final String J___DEEPCOPY__ = "__deepcopy__";
    public static final TruffleString T___DEEPCOPY__ = tsLiteral(J___DEEPCOPY__);

    public static final String J___DEALLOC__ = "__dealloc__";
    public static final TruffleString T___DEALLOC__ = tsLiteral(J___DEALLOC__);

    public static final String J___DEL__ = "__del__";
    public static final TruffleString T___DEL__ = tsLiteral(J___DEL__);

    public static final String J___FLOOR__ = "__floor__";
    public static final TruffleString T___FLOOR__ = tsLiteral(J___FLOOR__);

    public static final String J___REPR__ = "__repr__";
    public static final TruffleString T___REPR__ = tsLiteral(J___REPR__);

    public static final String J___STR__ = "__str__";
    public static final TruffleString T___STR__ = tsLiteral(J___STR__);

    public static final String J___BYTES__ = "__bytes__";
    public static final TruffleString T___BYTES__ = tsLiteral(J___BYTES__);

    public static final String J___FORMAT__ = "__format__";
    public static final TruffleString T___FORMAT__ = tsLiteral(J___FORMAT__);

    public static final String J___FREE__ = "__free__";
    public static final TruffleString T___FREE__ = tsLiteral(J___FREE__);

    public static final String J___LT__ = "__lt__";
    public static final TruffleString T___LT__ = tsLiteral(J___LT__);

    public static final String J___LE__ = "__le__";
    public static final TruffleString T___LE__ = tsLiteral(J___LE__);

    public static final String J___EQ__ = "__eq__";
    public static final TruffleString T___EQ__ = tsLiteral(J___EQ__);

    public static final String J___NE__ = "__ne__";
    public static final TruffleString T___NE__ = tsLiteral(J___NE__);

    public static final String J___GT__ = "__gt__";
    public static final TruffleString T___GT__ = tsLiteral(J___GT__);

    public static final String J___GE__ = "__ge__";
    public static final TruffleString T___GE__ = tsLiteral(J___GE__);

    public static final String J___HASH__ = "__hash__";
    public static final TruffleString T___HASH__ = tsLiteral(J___HASH__);

    public static final String J___BOOL__ = "__bool__";
    public static final TruffleString T___BOOL__ = tsLiteral(J___BOOL__);

    public static final String J___GETATTR__ = "__getattr__";
    public static final TruffleString T___GETATTR__ = tsLiteral(J___GETATTR__);

    public static final String J___GETATTRIBUTE__ = "__getattribute__";
    public static final TruffleString T___GETATTRIBUTE__ = tsLiteral(J___GETATTRIBUTE__);

    public static final String J___PREPARE__ = "__prepare__";
    public static final TruffleString T___PREPARE__ = tsLiteral(J___PREPARE__);

    public static final String J___SETATTR__ = "__setattr__";
    public static final TruffleString T___SETATTR__ = tsLiteral(J___SETATTR__);

    public static final String J___DELATTR__ = "__delattr__";
    public static final TruffleString T___DELATTR__ = tsLiteral(J___DELATTR__);

    public static final String J___DIR__ = "__dir__";
    public static final TruffleString T___DIR__ = tsLiteral(J___DIR__);

    public static final String J___GET__ = "__get__";
    public static final TruffleString T___GET__ = tsLiteral(J___GET__);

    public static final String J___SET__ = "__set__";
    public static final TruffleString T___SET__ = tsLiteral(J___SET__);

    public static final String J___DELETE__ = "__delete__";
    public static final TruffleString T___DELETE__ = tsLiteral(J___DELETE__);

    public static final String J___SET_NAME__ = "__set_name__";
    public static final TruffleString T___SET_NAME__ = tsLiteral(J___SET_NAME__);

    public static final String J___INIT_SUBCLASS__ = "__init_subclass__";
    public static final TruffleString T___INIT_SUBCLASS__ = tsLiteral(J___INIT_SUBCLASS__);

    public static final String J___INSTANCECHECK__ = "__instancecheck__";
    public static final TruffleString T___INSTANCECHECK__ = tsLiteral(J___INSTANCECHECK__);

    public static final String J___SUBCLASSCHECK__ = "__subclasscheck__";
    public static final TruffleString T___SUBCLASSCHECK__ = tsLiteral(J___SUBCLASSCHECK__);

    public static final String J___SUBCLASSES__ = "__subclasses__";
// public static final TruffleString T___SUBCLASSES__ = tsLiteral(J___SUBCLASSES__);

    public static final String J___SUBCLASSHOOK__ = "__subclasshook__";
// public static final TruffleString T___SUBCLASSHOOK__ = tsLiteral(J___SUBCLASSHOOK__);

    public static final String J___CALL__ = "__call__";
    public static final TruffleString T___CALL__ = tsLiteral(J___CALL__);

    public static final String J___CALLBACK__ = "__callback__";
// public static final TruffleString T___CALLBACK__ = tsLiteral(J___CALLBACK__);

    public static final String J___CLEAR__ = "__clear__";
    public static final TruffleString T___CLEAR__ = tsLiteral(J___CLEAR__);

    public static final String J___LEN__ = "__len__";
    public static final TruffleString T___LEN__ = tsLiteral(J___LEN__);

    public static final String J___LENGTH_HINT__ = "__length_hint__";
    public static final TruffleString T___LENGTH_HINT__ = tsLiteral(J___LENGTH_HINT__);

    public static final String J___GETITEM__ = "__getitem__";
    public static final TruffleString T___GETITEM__ = tsLiteral(J___GETITEM__);

    public static final String J___MISSING__ = "__missing__";
    public static final TruffleString T___MISSING__ = tsLiteral(J___MISSING__);

    public static final String J___SETITEM__ = "__setitem__";
    public static final TruffleString T___SETITEM__ = tsLiteral(J___SETITEM__);

    public static final String J___DELITEM__ = "__delitem__";
    public static final TruffleString T___DELITEM__ = tsLiteral(J___DELITEM__);

    public static final String J___ITER__ = "__iter__";
    public static final TruffleString T___ITER__ = tsLiteral(J___ITER__);

    public static final String J___NEXT__ = "__next__";
    public static final TruffleString T___NEXT__ = tsLiteral(J___NEXT__);

    public static final String J___REVERSED__ = "__reversed__";
    public static final TruffleString T___REVERSED__ = tsLiteral(J___REVERSED__);

    public static final String J___CONTAINS__ = "__contains__";
    public static final TruffleString T___CONTAINS__ = tsLiteral(J___CONTAINS__);

    public static final String J___ADD__ = "__add__";
    public static final TruffleString T___ADD__ = tsLiteral(J___ADD__);

    public static final String J___SUB__ = "__sub__";
    public static final TruffleString T___SUB__ = tsLiteral(J___SUB__);

    public static final String J___MUL__ = "__mul__";
    public static final TruffleString T___MUL__ = tsLiteral(J___MUL__);

    public static final String J___MATMUL__ = "__matmul__";
    public static final TruffleString T___MATMUL__ = tsLiteral(J___MATMUL__);

    public static final String J___TRUEDIV__ = "__truediv__";
    public static final TruffleString T___TRUEDIV__ = tsLiteral(J___TRUEDIV__);

    public static final String J___TRUNC__ = "__trunc__";
    public static final TruffleString T___TRUNC__ = tsLiteral(J___TRUNC__);

    public static final String J___FLOORDIV__ = "__floordiv__";
    public static final TruffleString T___FLOORDIV__ = tsLiteral(J___FLOORDIV__);

    public static final String J___MOD__ = "__mod__";
    public static final TruffleString T___MOD__ = tsLiteral(J___MOD__);

    public static final String J___DIVMOD__ = "__divmod__";
    public static final TruffleString T___DIVMOD__ = tsLiteral(J___DIVMOD__);

    public static final String J___POW__ = "__pow__";
    public static final TruffleString T___POW__ = tsLiteral(J___POW__);

    public static final String J___LSHIFT__ = "__lshift__";
    public static final TruffleString T___LSHIFT__ = tsLiteral(J___LSHIFT__);

    public static final String J___RSHIFT__ = "__rshift__";
    public static final TruffleString T___RSHIFT__ = tsLiteral(J___RSHIFT__);

    public static final String J___AND__ = "__and__";
    public static final TruffleString T___AND__ = tsLiteral(J___AND__);

    public static final String J___XOR__ = "__xor__";
    public static final TruffleString T___XOR__ = tsLiteral(J___XOR__);

    public static final String J___OR__ = "__or__";
    public static final TruffleString T___OR__ = tsLiteral(J___OR__);

    public static final String J___RADD__ = "__radd__";
    public static final TruffleString T___RADD__ = tsLiteral(J___RADD__);

    public static final String J___RSUB__ = "__rsub__";
    public static final TruffleString T___RSUB__ = tsLiteral(J___RSUB__);

    public static final String J___RMUL__ = "__rmul__";
    public static final TruffleString T___RMUL__ = tsLiteral(J___RMUL__);

    public static final String J___RMATMUL__ = "__rmatmul__";
    public static final TruffleString T___RMATMUL__ = tsLiteral(J___RMATMUL__);

    public static final String J___RTRUEDIV__ = "__rtruediv__";
    public static final TruffleString T___RTRUEDIV__ = tsLiteral(J___RTRUEDIV__);

    public static final String J___RFLOORDIV__ = "__rfloordiv__";
    public static final TruffleString T___RFLOORDIV__ = tsLiteral(J___RFLOORDIV__);

    public static final String J___RMOD__ = "__rmod__";
    public static final TruffleString T___RMOD__ = tsLiteral(J___RMOD__);

    public static final String J___RDIVMOD__ = "__rdivmod__";
    public static final TruffleString T___RDIVMOD__ = tsLiteral(J___RDIVMOD__);

    public static final String J___RPOW__ = "__rpow__";
    public static final TruffleString T___RPOW__ = tsLiteral(J___RPOW__);

    public static final String J___RLSHIFT__ = "__rlshift__";
    public static final TruffleString T___RLSHIFT__ = tsLiteral(J___RLSHIFT__);

    public static final String J___RRSHIFT__ = "__rrshift__";
    public static final TruffleString T___RRSHIFT__ = tsLiteral(J___RRSHIFT__);

    public static final String J___RAND__ = "__rand__";
    public static final TruffleString T___RAND__ = tsLiteral(J___RAND__);

    public static final String J___RXOR__ = "__rxor__";
    public static final TruffleString T___RXOR__ = tsLiteral(J___RXOR__);

    public static final String J___ROR__ = "__ror__";
    public static final TruffleString T___ROR__ = tsLiteral(J___ROR__);

    public static final String J___IADD__ = "__iadd__";
    public static final TruffleString T___IADD__ = tsLiteral(J___IADD__);

    public static final String J___ISUB__ = "__isub__";
    public static final TruffleString T___ISUB__ = tsLiteral(J___ISUB__);

    public static final String J___IMUL__ = "__imul__";
    public static final TruffleString T___IMUL__ = tsLiteral(J___IMUL__);

    public static final String J___IMATMUL__ = "__imatmul__";
    public static final TruffleString T___IMATMUL__ = tsLiteral(J___IMATMUL__);

    public static final String J___ITRUEDIV__ = "__itruediv__";
    public static final TruffleString T___ITRUEDIV__ = tsLiteral(J___ITRUEDIV__);

    public static final String J___IFLOORDIV__ = "__ifloordiv__";
    public static final TruffleString T___IFLOORDIV__ = tsLiteral(J___IFLOORDIV__);

    public static final String J___IMOD__ = "__imod__";
    public static final TruffleString T___IMOD__ = tsLiteral(J___IMOD__);

    public static final String J___IPOW__ = "__ipow__";
    public static final TruffleString T___IPOW__ = tsLiteral(J___IPOW__);

    public static final String J___ILSHIFT__ = "__ilshift__";
    public static final TruffleString T___ILSHIFT__ = tsLiteral(J___ILSHIFT__);

    public static final String J___IRSHIFT__ = "__irshift__";
    public static final TruffleString T___IRSHIFT__ = tsLiteral(J___IRSHIFT__);

    public static final String J___IAND__ = "__iand__";
    public static final TruffleString T___IAND__ = tsLiteral(J___IAND__);

    public static final String J___IXOR__ = "__ixor__";
    public static final TruffleString T___IXOR__ = tsLiteral(J___IXOR__);

    public static final String J___IOR__ = "__ior__";
    public static final TruffleString T___IOR__ = tsLiteral(J___IOR__);

    public static final String J___NEG__ = "__neg__";
    public static final TruffleString T___NEG__ = tsLiteral(J___NEG__);

    public static final String J___POS__ = "__pos__";
    public static final TruffleString T___POS__ = tsLiteral(J___POS__);

    public static final String J___ABS__ = "__abs__";
    public static final TruffleString T___ABS__ = tsLiteral(J___ABS__);

    public static final String J___INVERT__ = "__invert__";
    public static final TruffleString T___INVERT__ = tsLiteral(J___INVERT__);

    public static final String J___COMPLEX__ = "__complex__";
    public static final TruffleString T___COMPLEX__ = tsLiteral(J___COMPLEX__);

    public static final String J___INT__ = "__int__";
    public static final TruffleString T___INT__ = tsLiteral(J___INT__);

    public static final String J___FLOAT__ = "__float__";
    public static final TruffleString T___FLOAT__ = tsLiteral(J___FLOAT__);

    public static final String J___ROUND__ = "__round__";
    public static final TruffleString T___ROUND__ = tsLiteral(J___ROUND__);

    public static final String J___INDEX__ = "__index__";
    public static final TruffleString T___INDEX__ = tsLiteral(J___INDEX__);

    public static final String J___ENTER__ = "__enter__";
    public static final TruffleString T___ENTER__ = tsLiteral(J___ENTER__);

    public static final String J___EXIT__ = "__exit__";
    public static final TruffleString T___EXIT__ = tsLiteral(J___EXIT__);

    public static final String J___AWAIT__ = "__await__";
    public static final TruffleString T___AWAIT__ = tsLiteral(J___AWAIT__);

    public static final String J___AITER__ = "__aiter__";
    public static final TruffleString T___AITER__ = tsLiteral(J___AITER__);

    public static final String J___ANEXT__ = "__anext__";
    public static final TruffleString T___ANEXT__ = tsLiteral(J___ANEXT__);

    public static final String J___AENTER__ = "__aenter__";
    public static final TruffleString T___AENTER__ = tsLiteral(J___AENTER__);

    public static final String J___AEXIT__ = "__aexit__";
    public static final TruffleString T___AEXIT__ = tsLiteral(J___AEXIT__);

    public static final String J___REDUCE__ = "__reduce__";
    public static final TruffleString T___REDUCE__ = tsLiteral(J___REDUCE__);

    public static final String J___REDUCE_EX__ = "__reduce_ex__";
    public static final TruffleString T___REDUCE_EX__ = tsLiteral(J___REDUCE_EX__);

    public static final String J___GETINITARGS__ = "__getinitargs__";
    public static final TruffleString T___GETINITARGS__ = tsLiteral(J___GETINITARGS__);

    public static final String J___GETNEWARGS__ = "__getnewargs__";
    public static final TruffleString T___GETNEWARGS__ = tsLiteral(J___GETNEWARGS__);

    public static final String J___GETNEWARGS_EX__ = "__getnewargs_ex__";
    public static final TruffleString T___GETNEWARGS_EX__ = tsLiteral(J___GETNEWARGS_EX__);

    public static final String J___GETSTATE__ = "__getstate__";
    public static final TruffleString T___GETSTATE__ = tsLiteral(J___GETSTATE__);

    public static final String J___SETSTATE__ = "__setstate__";
    public static final TruffleString T___SETSTATE__ = tsLiteral(J___SETSTATE__);

    public static final String J___GETFORMAT__ = "__getformat__";
// public static final TruffleString T___GETFORMAT__ = tsLiteral(J___GETFORMAT__);

    public static final String J___SETFORMAT__ = "__setformat__";
// public static final TruffleString T___SETFORMAT__ = tsLiteral(J___SETFORMAT__);

    public static final String J___OBJCLASS__ = "__objclass__";
    public static final TruffleString T___OBJCLASS__ = tsLiteral(J___OBJCLASS__);

    public static final String J___ISABSTRACTMETHOD__ = "__isabstractmethod__";
    public static final TruffleString T___ISABSTRACTMETHOD__ = tsLiteral(J___ISABSTRACTMETHOD__);

    public static final String J___MRO_ENTRIES__ = "__mro_entries__";
    public static final TruffleString T___MRO_ENTRIES__ = tsLiteral(J___MRO_ENTRIES__);

    public static final String J_KEYS = "keys";
    public static final TruffleString T_KEYS = tsLiteral(J_KEYS);

    public static final String J_ITEMS = "items";
    public static final TruffleString T_ITEMS = tsLiteral(J_ITEMS);

    public static final String J_VALUES = "values";
    public static final TruffleString T_VALUES = tsLiteral(J_VALUES);

    public static final String J___FSPATH__ = "__fspath__";
    public static final TruffleString T___FSPATH__ = tsLiteral(J___FSPATH__);

    public static final String J_TOBYTES = "tobytes";
// public static final TruffleString T_TOBYTES = tsLiteral(J_TOBYTES);

    public static final String J_DECODE = "decode";
    public static final TruffleString T_DECODE = tsLiteral(J_DECODE);

    public static final String J___SIZEOF__ = "__sizeof__";
    public static final TruffleString T___SIZEOF__ = tsLiteral(J___SIZEOF__);

    public static final String J___CLASS_GETITEM__ = "__class_getitem__";
    public static final TruffleString T___CLASS_GETITEM__ = tsLiteral(J___CLASS_GETITEM__);

    public static final String J___MATCH_ARGS = "__match_args__";
    public static final TruffleString T___MATCH_ARGS = tsLiteral(J___MATCH_ARGS);

    public static final String J_FILENO = "fileno";
    public static final TruffleString T_FILENO = tsLiteral(J_FILENO);

    public static final String J_ISDISJOINT = "isdisjoint";
// public static final TruffleString T_ISDISJOINT = tsLiteral(J_ISDISJOINT);

    public static final String J_MRO = "mro";
    public static final TruffleString T_MRO = tsLiteral(J_MRO);

    public static final String J_SORT = "sort";
    public static final TruffleString T_SORT = tsLiteral(J_SORT);

    public static final String J_JOIN = "join";
    public static final TruffleString T_JOIN = tsLiteral(J_JOIN);

    public static final String J_COPY = "copy";
    public static final TruffleString T_COPY = tsLiteral(J_COPY);

    public static final String J_CLEAR = "clear";
    public static final TruffleString T_CLEAR = tsLiteral(J_CLEAR);

    public static final String J_GET = "get";
    public static final TruffleString T_GET = tsLiteral(J_GET);

    public static final String J_INSERT = "insert";
    public static final TruffleString T_INSERT = tsLiteral(J_INSERT);

    public static final TruffleString T_UPDATE = tsLiteral("update");

    public static final String J_RICHCMP = "__truffle_richcompare__";
    public static final TruffleString T_RICHCMP = tsLiteral(J_RICHCMP);

    public static final String J_TRUFFLE_SOURCE = "__truffle_source__";

    public static final String J_SHUTDOWN = "_shutdown";
    public static final TruffleString T_SHUTDOWN = tsLiteral(J_SHUTDOWN);
}
