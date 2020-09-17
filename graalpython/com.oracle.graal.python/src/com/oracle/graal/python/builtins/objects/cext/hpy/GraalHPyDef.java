/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.objects.cext.hpy;

import com.oracle.truffle.api.object.HiddenKey;

/**
 * A container class for mirroring definitions of {@code hpydef.h}
 */
public abstract class GraalHPyDef {

    public static final HiddenKey TYPE_HPY_BASICSIZE = new HiddenKey("hpy_basicsize");
    public static final HiddenKey TYPE_HPY_ITEMSIZE = new HiddenKey("hpy_itemsize");
    public static final HiddenKey TYPE_HPY_FLAGS = new HiddenKey("hpy_flags");

    /* enum values of 'HPyDef_Kind' */
    public static final int HPY_DEF_KIND_SLOT = 1;
    public static final int HPY_DEF_KIND_METH = 2;
    public static final int HPY_DEF_KIND_MEMBER = 3;
    public static final int HPY_DEF_KIND_GETSET = 4;

    /* enum values of 'HPyFunc_Signature' */
    public static final int HPyFunc_VARARGS = 1;  // METH_VARARGS
    public static final int HPyFunc_KEYWORDS = 2;  // METH_VARARGS | METH_KEYWORDS
    public static final int HPyFunc_NOARGS = 3;  // METH_NOARGS
    public static final int HPyFunc_O = 4;  // METH_O
    public static final int HPyFunc_DESTROYFUNC = 5;
    public static final int HPyFunc_UNARYFUNC = 6;
    public static final int HPyFunc_BINARYFUNC = 7;
    public static final int HPyFunc_TERNARYFUNC = 8;
    public static final int HPyFunc_INQUIRY = 9;
    public static final int HPyFunc_LENFUNC = 10;
    public static final int HPyFunc_SSIZEARGFUNC = 11;
    public static final int HPyFunc_SSIZESSIZEARGFUNC = 12;
    public static final int HPyFunc_SSIZEOBJARGPROC = 13;
    public static final int HPyFunc_SSIZESSIZEOBJARGPROC = 14;
    public static final int HPyFunc_OBJOBJARGPROC = 15;
    public static final int HPyFunc_FREEFUNC = 16;
    public static final int HPyFunc_GETATTRFUNC = 17;
    public static final int HPyFunc_GETATTROFUNC = 18;
    public static final int HPyFunc_SETATTRFUNC = 19;
    public static final int HPyFunc_SETATTROFUNC = 20;
    public static final int HPyFunc_REPRFUNC = 21;
    public static final int HPyFunc_HASHFUNC = 22;
    public static final int HPyFunc_RICHCMPFUNC = 23;
    public static final int HPyFunc_GETITERFUNC = 24;
    public static final int HPyFunc_ITERNEXTFUNC = 25;
    public static final int HPyFunc_DESCRGETFUNC = 26;
    public static final int HPyFunc_DESCRSETFUNC = 27;
    public static final int HPyFunc_INITPROC = 28;
    public static final int HPyFunc_GETTER = 29;
    public static final int HPyFunc_SETTER = 30;

    /* enum values of 'HPyMember_FieldType' */
    public static final int HPY_MEMBER_SHORT = 0;
    public static final int HPY_MEMBER_INT = 1;
    public static final int HPY_MEMBER_LONG = 2;
    public static final int HPY_MEMBER_FLOAT = 3;
    public static final int HPY_MEMBER_DOUBLE = 4;
    public static final int HPY_MEMBER_STRING = 5;
    public static final int HPY_MEMBER_OBJECT = 6;
    public static final int HPY_MEMBER_CHAR = 7;
    public static final int HPY_MEMBER_BYTE = 8;
    public static final int HPY_MEMBER_UBYTE = 9;
    public static final int HPY_MEMBER_USHORT = 10;
    public static final int HPY_MEMBER_UINT = 11;
    public static final int HPY_MEMBER_ULONG = 12;
    public static final int HPY_MEMBER_STRING_INPLACE = 13;
    public static final int HPY_MEMBER_BOOL = 14;
    public static final int HPY_MEMBER_OBJECT_EX = 16;
    public static final int HPY_MEMBER_LONGLONG = 17;
    public static final int HPY_MEMBER_ULONGLONG = 18;
    public static final int HPY_MEMBER_HPYSSIZET = 19;
    public static final int HPY_MEMBER_NONE = 20;

    /* enum values of 'HPyType_SpecParam_Kind' */
    public static final int HPyType_SPEC_PARAM_BASE = 1;
    public static final int HPyType_SPEC_PARAM_BASES_TUPLE = 2;

    /* type flags according to 'hpytype.h' */
    public static final long _Py_TPFLAGS_HEAPTYPE = (1L << 9);
    public static final long HPy_TPFLAGS_BASETYPE = (1L << 10);
    public static final long HPy_TPFLAGS_DEFAULT = _Py_TPFLAGS_HEAPTYPE;

}
