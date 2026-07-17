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

package com.oracle.graal.python.builtins.objects.cext.capi.transitions;

import static com.oracle.graal.python.builtins.objects.cext.structs.CFields.PyObject__ob_type;
import static com.oracle.graal.python.builtins.objects.cext.structs.CStructAccess.readLongField;
import static com.oracle.graal.python.builtins.objects.cext.structs.CStructAccess.readPtrField;
import static com.oracle.graal.python.builtins.objects.cext.structs.CStructAccess.writeLongField;
import static com.oracle.graal.python.builtins.objects.cext.structs.CStructAccess.writePtrField;

import com.oracle.graal.python.annotations.CApiConstant;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTransitions.HandlePointerConverter;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTransitions.NativeToPythonClassInternalNode;
import com.oracle.graal.python.builtins.objects.cext.structs.CFields;
import com.oracle.graal.python.builtins.objects.cext.structs.CStructs;
import com.oracle.graal.python.builtins.objects.type.TypeFlags;
import com.oracle.graal.python.nodes.object.BuiltinClassProfiles.IsBuiltinClassProfile;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.nativeaccess.NativeMemory;

public final class GraalPyUnicodeObjectUtil {

    @CApiConstant //
    public static final int GRAALPY_UNICODE_INTERN_STATE_UNDETERMINED = 0;
    @CApiConstant //
    public static final int GRAALPY_UNICODE_INTERN_STATE_INTERNED = 1;
    @CApiConstant //
    public static final int GRAALPY_UNICODE_INTERN_STATE_NOT_INTERNED = 2;
    @CApiConstant //
    private static final int GRAALPY_UNICODE_KIND_MASK = 0x7;
    @CApiConstant //
    private static final long GRAALPY_UNICODE_IS_ASCII_FLAG = 1L << 3;
    @CApiConstant //
    private static final int GRAALPY_UNICODE_INTERN_STATE_SHIFT = 4;
    @CApiConstant //
    private static final long GRAALPY_UNICODE_INTERN_STATE_MASK = 0x3L << GRAALPY_UNICODE_INTERN_STATE_SHIFT;
    @CApiConstant //
    private static final long GRAALPY_UNICODE_IS_COMPACT_FLAG = 1L << 6;

    private GraalPyUnicodeObjectUtil() {
    }

    public static void initializeGraalPyUnicodeObject(long rawPointer, long data, long length, long byteLength, int charSize, boolean isAscii, int interned, boolean compact) {
        assert charSize == 1 || charSize == 2 || charSize == 4;
        assert byteLength == length * charSize;
        assert interned == GRAALPY_UNICODE_INTERN_STATE_UNDETERMINED || interned == GRAALPY_UNICODE_INTERN_STATE_INTERNED || interned == GRAALPY_UNICODE_INTERN_STATE_NOT_INTERNED;
        // If compact, the GraalPyUnicodeObject struct was over-allocated and the data bytes are after the last field.
        assert !compact || (data == rawPointer + CStructs.GraalPyUnicodeObject.size());

        writeLongField(rawPointer, CFields.GraalPyUnicodeObject__length, length);
        writeLongField(rawPointer, CFields.GraalPyUnicodeObject__byte_length, byteLength);
        writeLongField(rawPointer, CFields.GraalPyUnicodeObject__hash, -1);
        writeLongField(rawPointer, CFields.GraalPyUnicodeObject__state, createState(charSize, isAscii, interned, compact));
        writePtrField(rawPointer, CFields.GraalPyUnicodeObject__data, data);
        assert isStateInitialized(rawPointer);
        // Unicode data must be followed by one kind-sized NUL code unit.
        NativeMemory.memset(data + byteLength, (byte) 0, charSize);
    }

    // Keep in sync with unicodeobject.c:GraalPyUnicodeObject_CreateState.
    private static long createState(int charSize, boolean isAscii, int interned, boolean compact) {
        assert (charSize & ~GRAALPY_UNICODE_KIND_MASK) == 0;
        return charSize | encodeAscii(isAscii) | encodeInterned(interned) | encodeCompact(compact);
    }

    // Keep in sync with unicodeobject.c:GraalPyUnicodeObject_EncodeAscii.
    private static long encodeAscii(boolean isAscii) {
        return isAscii ? GRAALPY_UNICODE_IS_ASCII_FLAG : 0;
    }

    // Keep in sync with unicodeobject.c:GraalPyUnicodeObject_EncodeInterned.
    private static long encodeInterned(int interned) {
        return (long) interned << GRAALPY_UNICODE_INTERN_STATE_SHIFT;
    }

    // Keep in sync with unicodeobject.c:GraalPyUnicodeObject_EncodeCompact.
    private static long encodeCompact(boolean compact) {
        return compact ? GRAALPY_UNICODE_IS_COMPACT_FLAG : 0;
    }

    // Keep in sync with unicodeobject.c:GraalPyUnicodeObject_GetInterned.
    public static int getInterned(long rawPointer) {
        long state = readLongField(rawPointer, CFields.GraalPyUnicodeObject__state);
        return (int) ((state & GRAALPY_UNICODE_INTERN_STATE_MASK) >> GRAALPY_UNICODE_INTERN_STATE_SHIFT);
    }

    // Keep in sync with unicodeobject.c:GraalPyUnicodeObject_GetKindFromState.
    private static int getKindFromState(long state) {
        return (int) (state & GRAALPY_UNICODE_KIND_MASK);
    }

    // Keep in sync with unicodeobject.c:GraalPyUnicodeObject_UpdateInterned.
    private static long updateInterned(long state, int interned) {
        return (state & ~GRAALPY_UNICODE_INTERN_STATE_MASK) | encodeInterned(interned);
    }

    // Keep in sync with unicodeobject.c:GraalPyUnicodeObject_GetKind.
    public static int getKind(long rawPointer) {
        return getKindFromState(readLongField(rawPointer, CFields.GraalPyUnicodeObject__state));
    }

    public static void setInterned(long rawPointer, int interned) {
        assert interned == GRAALPY_UNICODE_INTERN_STATE_UNDETERMINED || interned == GRAALPY_UNICODE_INTERN_STATE_INTERNED || interned == GRAALPY_UNICODE_INTERN_STATE_NOT_INTERNED;
        long state = readLongField(rawPointer, CFields.GraalPyUnicodeObject__state);
        writeLongField(rawPointer, CFields.GraalPyUnicodeObject__state, updateInterned(state, interned));
    }

    // Keep in sync with unicodeobject.c:GraalPyUnicodeObject_IsCompact.
    public static boolean isCompact(long rawPointer) {
        assert !HandlePointerConverter.pointsToPyHandleSpace(rawPointer);
        return (readLongField(rawPointer, CFields.GraalPyUnicodeObject__state) & GRAALPY_UNICODE_IS_COMPACT_FLAG) != 0;
    }

    public static boolean isStateInitialized(long rawPointer) {
        return getKind(rawPointer) != 0;
    }

    /**
     * Given the raw (untagged) pointer to a {@code GraalPyObject}, this method checks if the object is a unicode object with non-compact data.
     */
    public static boolean isNonCompactGraalPyUnicodeObject(long rawPointer) {
        assert !HandlePointerConverter.pointsToPyHandleSpace(rawPointer);
        long obType = readPtrField(rawPointer, PyObject__ob_type);
        boolean isUnicodeSubclass = (readLongField(obType, CFields.PyTypeObject__tp_flags) & TypeFlags.UNICODE_SUBCLASS) != 0L;
        assert !HandlePointerConverter.pointsToPyHandleSpace(obType);
        // During finalization, the native reference for obType may already have been freed, so it cannot be converted back to a managed class.
        assert PythonContext.get(null).isFinalizing() || IsBuiltinClassProfile.profileClassSlowPath(NativeToPythonClassInternalNode.executeUncached(obType),
                        PythonBuiltinClassType.PString) == isUnicodeSubclass;
        return isUnicodeSubclass && !GraalPyUnicodeObjectUtil.isCompact(rawPointer);
    }

    /** Similar to {@code unicodeobject.h:_PyUnicode_NONCOMPACT_DATA} */
    public static long getNonCompactDataPointer(long rawPointer) {
        assert isNonCompactGraalPyUnicodeObject(rawPointer);
        return readPtrField(rawPointer, CFields.GraalPyUnicodeObject__data);
    }
}
