/*
 * Copyright (c) 2026, 2026, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.graal.python.builtins.objects.cext.capi;

public final class PyMethodFlags {
    public static final int METH_VARARGS = 0x0001;
    public static final int METH_KEYWORDS = 0x0002;
    public static final int METH_NOARGS = 0x0004;
    public static final int METH_O = 0x0008;
    public static final int METH_CLASS = 0x0010;
    public static final int METH_STATIC = 0x0020;
    public static final int METH_COEXIST = 0x0040;
    public static final int METH_FASTCALL = 0x0080;
    public static final int METH_METHOD = 0x0200;
    // Filter out only the base convention, without orthogonal modifiers
    private static final int CALL_CONVENTION_MASK = METH_VARARGS | METH_KEYWORDS | METH_NOARGS | METH_O | METH_FASTCALL | METH_METHOD;

    private PyMethodFlags() {
    }

    public static boolean isMethVarargs(int flags) {
        return (flags & CALL_CONVENTION_MASK) == METH_VARARGS;
    }

    public static boolean isMethVarargsWithKeywords(int flags) {
        return (flags & CALL_CONVENTION_MASK) == (METH_VARARGS | METH_KEYWORDS);
    }

    public static boolean isMethNoArgs(int flags) {
        return (flags & CALL_CONVENTION_MASK) == METH_NOARGS;
    }

    public static boolean isMethO(int flags) {
        return (flags & CALL_CONVENTION_MASK) == METH_O;
    }

    public static boolean isMethFastcall(int flags) {
        return (flags & CALL_CONVENTION_MASK) == METH_FASTCALL;
    }

    public static boolean isMethFastcallWithKeywords(int flags) {
        return (flags & CALL_CONVENTION_MASK) == (METH_FASTCALL | METH_KEYWORDS);
    }

    public static boolean isMethMethod(int flags) {
        return (flags & CALL_CONVENTION_MASK) == (METH_FASTCALL | METH_KEYWORDS | METH_METHOD);
    }

    public static boolean isMethStatic(int flags) {
        return (flags & METH_STATIC) != 0;
    }

    public static boolean isMethClass(int flags) {
        return (flags & METH_CLASS) != 0;
    }
}
