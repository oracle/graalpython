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
package com.oracle.graal.python.builtins.objects.type;

/**
 * This class defines the type flags as specified in CPython's {@code Include/object.h}. The values
 * should be kept in sync with this header.
 */
public abstract class TypeFlags {

    public static final long HEAPTYPE = (1L << 9);
    public static final long BASETYPE = (1L << 10);
    public static final long HAVE_VECTORCALL = (1L << 11);
    public static final long READY = (1L << 12);
    public static final long READYING = (1L << 13);
    public static final long HAVE_GC = (1L << 14);
    public static final long HAVE_STACKLESS_EXTENSION = 0;
    public static final long METHOD_DESCRIPTOR = (1L << 17);
    public static final long HAVE_VERSION_TAG = (1L << 18);
    public static final long VALID_VERSION_TAG = (1L << 19);
    public static final long IS_ABSTRACT = (1L << 20);
    public static final long LONG_SUBCLASS = (1L << 24);
    public static final long LIST_SUBCLASS = (1L << 25);
    public static final long TUPLE_SUBCLASS = (1L << 26);
    public static final long BYTES_SUBCLASS = (1L << 27);
    public static final long UNICODE_SUBCLASS = (1L << 28);
    public static final long DICT_SUBCLASS = (1L << 29);
    public static final long BASE_EXC_SUBCLASS = (1L << 30);
    public static final long TYPE_SUBCLASS = (1L << 31);
    public static final long DEFAULT = HAVE_STACKLESS_EXTENSION | HAVE_VERSION_TAG;
    public static final long HAVE_FINALIZE = 1L;
}
