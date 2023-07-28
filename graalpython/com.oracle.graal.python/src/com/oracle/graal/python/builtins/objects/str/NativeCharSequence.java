/*
 * Copyright (c) 2019, 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.objects.str;

import static com.oracle.graal.python.util.PythonUtils.TS_ENCODING;

import java.util.Objects;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.objects.cext.common.CExtCommonNodesFactory.ReadUnicodeArrayNodeGen;
import com.oracle.graal.python.nodes.util.CastToJavaIntExactNode;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.TruffleLogger;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.strings.TruffleString;
import com.oracle.truffle.api.strings.TruffleString.Encoding;

public final class NativeCharSequence implements CharSequence {
    private static final TruffleLogger LOGGER = PythonLanguage.getLogger(NativeCharSequence.class);

    /**
     * Pointer to the native buffer (most like a {@code char*} containing ASCII characters but could
     * also be an arbitrary {@code void*} for {@code Py_UCS1}, {@code Py_UCS2}, or {@code Py_UCS4}
     * characters)
     */
    private final Object ptr;

    private final int elements;

    /**
     * The size of a single character in bytes (valid values are {@code 1, 2, 4}).
     */
    private final int elementSize;

    /**
     * Specifies if the native buffer contains only ASCII characters.
     */
    private final boolean asciiOnly;

    private TruffleString materialized;

    public NativeCharSequence(Object ptr, int elements, int elementSize, boolean asciiOnly) {
        assert elements >= -1; // -1 means until null byte
        assert elementSize == 1 || elementSize == 2 || elementSize == 4;
        assert !(ptr instanceof String);
        assert !(ptr instanceof PString);
        assert !(ptr instanceof TruffleString);
        this.ptr = ptr;
        this.elements = elements;
        this.elementSize = elementSize;
        this.asciiOnly = asciiOnly;
    }

    @Override
    public int length() {
        return TruffleString.CodePointLengthNode.getUncached().execute(materialize(), TS_ENCODING);
    }

    int length(InteropLibrary lib, CastToJavaIntExactNode castToJavaIntNode) {
        try {
            int arraySize = castToJavaIntNode.execute(lib.getArraySize(ptr));
            assert arraySize % elementSize == 0;
            // we need to subtract the terminating null character
            return arraySize / elementSize;
        } catch (UnsupportedMessageException e) {
            throw CompilerDirectives.shouldNotReachHere("pointer of NativeCharSequence is not an array");
        }
    }

    @Override
    public char charAt(int index) {
        return (char) TruffleString.CodePointAtIndexNode.getUncached().execute(materialize(), index, TS_ENCODING);
    }

    @Override
    public CharSequence subSequence(int start, int end) {
        return materialize().toJavaStringUncached().subSequence(start, end);
    }

    boolean isMaterialized() {
        return materialized != null;
    }

    TruffleString getMaterialized() {
        return materialized;
    }

    TruffleString materialize() {
        if (!isMaterialized()) {
            LOGGER.warning("uncached materialization of NativeCharSequence");
            assert TS_ENCODING == Encoding.UTF_32 : "needs switch_encoding otherwise";
            materialized = TruffleString.fromIntArrayUTF32Uncached(ReadUnicodeArrayNodeGen.getUncached().execute(getPtr(), getElements(), getElementSize()));
        }
        return materialized;
    }

    public Object getPtr() {
        return ptr;
    }

    public int getElementSize() {
        return elementSize;
    }

    public boolean isAsciiOnly() {
        return asciiOnly;
    }

    @Override
    public String toString() {
        CompilerAsserts.neverPartOfCompilation();
        if (isMaterialized()) {
            return materialized.toJavaStringUncached();
        }
        return Objects.toString(ptr);
    }

    public int getElements() {
        return elements;
    }
}
