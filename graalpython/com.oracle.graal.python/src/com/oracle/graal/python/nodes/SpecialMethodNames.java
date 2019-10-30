/*
 * Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;

public abstract class SpecialMethodNames {

    public static final String __NEW__ = "__new__";
    public static final String __ALLOC__ = "__alloc__";
    public static final String __INIT__ = "__init__";
    public static final String __CEIL__ = "__ceil__";
    public static final String __DEL__ = "__del__";
    public static final String __FLOOR__ = "__floor__";
    public static final String __REPR__ = "__repr__";
    public static final String __STR__ = "__str__";
    public static final String __BYTES__ = "__bytes__";
    public static final String __FORMAT__ = "__format__";
    public static final String __LT__ = "__lt__";
    public static final String __LE__ = "__le__";
    public static final String __EQ__ = "__eq__";
    public static final String __NE__ = "__ne__";
    public static final String __GT__ = "__gt__";
    public static final String __GE__ = "__ge__";
    public static final String __HASH__ = "__hash__";
    public static final String __BOOL__ = "__bool__";
    public static final String __GETATTR__ = "__getattr__";
    public static final String __GETATTRIBUTE__ = "__getattribute__";
    public static final String __PREPARE__ = "__prepare__";
    public static final String __SETATTR__ = "__setattr__";
    public static final String __DELATTR__ = "__delattr__";
    public static final String __DIR__ = "__dir__";
    public static final String __GET__ = "__get__";
    public static final String __SET__ = "__set__";
    public static final String __DELETE__ = "__delete__";
    public static final String __SET_NAME__ = "__set_name__";
    public static final String __INIT_SUBCLASS__ = "__init_subclass__";
    public static final String __INSTANCECHECK__ = "__instancecheck__";
    public static final String __SUBCLASSCHECK__ = "__subclasscheck__";
    public static final String __SUBCLASSES__ = "__subclasses__";
    public static final String __SUBCLASSHOOK__ = "__subclasshook__";
    public static final String __CALL__ = "__call__";
    public static final String __CALLBACK__ = "__callback__";
    public static final String __LEN__ = "__len__";
    public static final String __LENGTH_HINT__ = "__length_hint__";
    public static final String __GETITEM__ = "__getitem__";
    public static final String __MISSING__ = "__missing__";
    public static final String __SETITEM__ = "__setitem__";
    public static final String __DELITEM__ = "__delitem__";
    public static final String __ITER__ = "__iter__";
    public static final String __NEXT__ = "__next__";
    public static final String __REVERSED__ = "__reversed__";
    public static final String __CONTAINS__ = "__contains__";
    public static final String __ADD__ = "__add__";
    public static final String __SUB__ = "__sub__";
    public static final String __MUL__ = "__mul__";
    public static final String __DIV__ = "__div__";
    public static final String __MATMUL__ = "__matmul__";
    public static final String __TRUEDIV__ = "__truediv__";
    public static final String __TRUNC__ = "__trunc__";
    public static final String __FLOORDIV__ = "__floordiv__";
    public static final String __MOD__ = "__mod__";
    public static final String __DIVMOD__ = "__divmod__";
    public static final String __POW__ = "__pow__";
    public static final String __LSHIFT__ = "__lshift__";
    public static final String __RSHIFT__ = "__rshift__";
    public static final String __AND__ = "__and__";
    public static final String __XOR__ = "__xor__";
    public static final String __OR__ = "__or__";
    public static final String __RADD__ = "__radd__";
    public static final String __RSUB__ = "__rsub__";
    public static final String __RMUL__ = "__rmul__";
    public static final String __RMATMUL__ = "__rmatmul__";
    public static final String __RTRUEDIV__ = "__rtruediv__";
    public static final String __RFLOORDIV__ = "__rfloordiv__";
    public static final String __RMOD__ = "__rmod__";
    public static final String __RDIVMOD__ = "__rdivmod__";
    public static final String __RPOW__ = "__rpow__";
    public static final String __RLSHIFT__ = "__rlshift__";
    public static final String __RAND__ = "__rand__";
    public static final String __RXOR__ = "__rxor__";
    public static final String __ROR__ = "__ror__";
    public static final String __IADD__ = "__iadd__";
    public static final String __ISUB__ = "__isub__";
    public static final String __IMUL__ = "__imul__";
    public static final String __IMATMUL__ = "__imatmul__";
    public static final String __ITRUEDIV__ = "__itruediv__";
    public static final String __IFLOORDIV__ = "__ifloordiv__";
    public static final String __IMOD__ = "__imod__";
    public static final String __IDIVMOD__ = "__idivmod__";
    public static final String __IPOW__ = "__ipow__";
    public static final String __ILSHIFT__ = "__ilshift__";
    public static final String __IRSHIFT__ = "__irshift__";
    public static final String __IAND__ = "__iand__";
    public static final String __IXOR__ = "__ixor__";
    public static final String __IOR__ = "__ior__";
    public static final String __NEG__ = "__neg__";
    public static final String __POS__ = "__pos__";
    public static final String __ABS__ = "__abs__";
    public static final String __INVERT__ = "__invert__";
    public static final String __COMPLEX__ = "__complex__";
    public static final String __INT__ = "__int__";
    public static final String __FLOAT__ = "__float__";
    public static final String __ROUND__ = "__round__";
    public static final String __INDEX__ = "__index__";
    public static final String __ENTER__ = "__enter__";
    public static final String __EXIT__ = "__exit__";
    public static final String __AWAIT__ = "__await__";
    public static final String __AITER__ = "__aiter__";
    public static final String __ANEXT__ = "__anext__";
    public static final String __AENTER__ = "__aenter__";
    public static final String __AEXIT__ = "__aexit__";
    public static final String __REDUCE__ = "__reduce__";
    public static final String __REDUCE_EX__ = "__reduce_ex__";
    public static final String __GETINITARGS__ = "__getinitargs__";
    public static final String __GETNEWARGS__ = "__getnewargs__";
    public static final String __GETSTATE__ = "__getstate__";
    public static final String __SETSTATE__ = "__setstate__";
    public static final String __GETFORMAT__ = "__getformat__";
    public static final String __SETFORMAT__ = "__setformat__";
    public static final String KEYS = "keys";
    public static final String ITEMS = "items";
    public static final String VALUES = "values";
    public static final String __FSPATH__ = "__fspath__";
    public static final String TOBYTES = "tobytes";
    public static final String DECODE = "decode";
    public static final String __SIZEOF__ = "__sizeof__";
    public static final String __CLASS_GETITEM__ = "__class_getitem__";

    public static final String RICHCMP = "__truffle_richcompare__";
    public static final String TRUFFLE_SOURCE = "__truffle_source__";
    public static final String SHUTDOWN = "_shutdown";

    // (tfel): The order of these matches the one in CPython, and thus is assumed to remain the same
    // in various places
    @CompilationFinal(dimensions = 1) private static final String[] COMPARE_OPSTRINGS = new String[]{"<", "<=", "==", "!=", ">", ">="};
    @CompilationFinal(dimensions = 1) private static final String[] COMPARE_OPNAMES = new String[]{__LT__, __LE__, __EQ__, __NE__, __GT__, __GE__};
    @CompilationFinal(dimensions = 1) private static final String[] COMPARE_REVERSALS = new String[]{__GT__, __GE__, __EQ__, __NE__, __GT__, __GE__};
    public static final int COMPARE_OP_COUNT = COMPARE_OPNAMES.length;

    public static String getCompareOpString(int op) {
        return COMPARE_OPSTRINGS[op];
    }

    public static String getCompareName(int op) {
        return COMPARE_OPNAMES[op];
    }

    public static String getCompareReversal(int op) {
        return COMPARE_REVERSALS[op];
    }
}
