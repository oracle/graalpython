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
package com.oracle.graal.python.builtins.objects.cext;

import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.cext.CExtNodes.AllToJavaNode;
import com.oracle.graal.python.builtins.objects.cext.CExtNodes.GetNativeNullNode;
import com.oracle.graal.python.builtins.objects.cext.CExtNodes.ToJavaNode;
import com.oracle.graal.python.builtins.objects.cext.CExtNodes.ToSulongNode;
import com.oracle.graal.python.builtins.objects.cext.CExtNodes.TransformExceptionToNativeNode;
import com.oracle.graal.python.builtins.objects.cext.DynamicObjectNativeWrapper.PAsPointerNode;
import com.oracle.graal.python.builtins.objects.cext.DynamicObjectNativeWrapper.ToPyObjectNode;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.nodes.attributes.LookupInheritedAttributeNode;
import com.oracle.graal.python.nodes.attributes.WriteAttributeToDynamicObjectNode;
import com.oracle.graal.python.nodes.call.special.CallVarargsMethodNode;
import com.oracle.graal.python.nodes.object.GetLazyClassNode;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.interop.InteropArray;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.llvm.spi.NativeTypeLibrary;

/**
 * This wrapper should only be used for class {@code PyDateTime_CAPI} defined in
 * {@code python_cext.py}. It emulates following native ABI:
 * 
 * <pre>
 * typedef struct {
 *     PyTypeObject *DateType;
 *     PyTypeObject *DateTimeType;
 *     PyTypeObject *TimeType;
 *     PyTypeObject *DeltaType;
 *     PyTypeObject *TZInfoType;
 *
 *     PyObject *TimeZone_UTC;
 *
 *     PyObject *(*Date_FromDate)(int, int, int, PyTypeObject*);
 *     PyObject *(*DateTime_FromDateAndTime)(int, int, int, int, int, int, int, PyObject*, PyTypeObject*);
 *     PyObject *(*Time_FromTime)(int, int, int, int, PyObject*, PyTypeObject*);
 *     PyObject *(*Delta_FromDelta)(int, int, int, int, PyTypeObject*);
 *     PyObject *(*TimeZone_FromTimeZone)(PyObject *offset, PyObject *name);
 *
 *     PyObject *(*DateTime_FromTimestamp)(PyObject*, PyObject*, PyObject*);
 *     PyObject *(*Date_FromTimestamp)(PyObject*, PyObject*);
 *
 *     PyObject *(*DateTime_FromDateAndTimeAndFold)(int, int, int, int, int, int, int, PyObject*, int, PyTypeObject*);
 *     PyObject *(*Time_FromTimeAndFold)(int, int, int, int, PyObject*, int, PyTypeObject*);
 *
 * } PyDateTime_CAPI
 * </pre>
 */
@ExportLibrary(InteropLibrary.class)
@ExportLibrary(NativeTypeLibrary.class)
public final class PyDateTimeCAPIWrapper extends PythonNativeWrapper {

    // IMPORTANT: if you modify this array, also adopt INVOCABLE_MEMBER_START_IDX
    @CompilationFinal(dimensions = 1) private static final String[] MEMBERS = {"DateType", "DateTimeType", "TimeType", "DeltaType", "TZInfoType", "TimeZone_UTC", "Date_FromDate",
                    "DateTime_FromDateAndTime", "Time_FromTime", "Delta_FromDelta", "TimeZone_FromTimeZone", "DateTime_FromTimestamp", "Date_FromTimestamp", "DateTime_FromDateAndTimeAndFold",
                    "Time_FromTimeAndFold"};

    // IMPORTANT: this is the index of the first function; keep it in sync with MEMBERS !!
    private static final int INVOCABLE_MEMBER_START_IDX = 6;

    @ExplodeLoop
    private static int indexOf(String member) {
        for (int i = 0; i < MEMBERS.length; i++) {
            if (MEMBERS[i].equals(member)) {
                return i;
            }
        }
        return -1;
    }

    public PyDateTimeCAPIWrapper(Object delegate) {
        super(delegate);
    }

    @ExportMessage
    boolean hasMembers() {
        return true;
    }

    @ExportMessage
    Object getMembers(boolean includeInternal) {
        return new InteropArray(new String[0]);
    }

    @ExportMessage
    boolean isMemberReadable(String member) {
        return indexOf(member) != -1;
    }

    @ExportMessage
    boolean isMemberInvocable(String member) {
        return indexOf(member) >= INVOCABLE_MEMBER_START_IDX;
    }

    @ExportMessage
    boolean isMemberModifiable(String member) {
        return isMemberReadable(member);
    }

    @ExportMessage
    boolean isMemberInsertable(String member) {
        return false;
    }

    @ExportMessage
    Object readMember(String member,
                    @CachedLibrary("this") PythonNativeWrapperLibrary lib,
                    @Shared("lookupAttrNode") @Cached LookupInheritedAttributeNode.Dynamic lookupGetattributeNode,
                    @Exclusive @Cached ToSulongNode toSulongNode) throws UnknownIdentifierException {
        Object attr = lookupGetattributeNode.execute(lib.getDelegate(this), member);
        if (attr != PNone.NO_VALUE) {
            return toSulongNode.execute(attr);
        }
        throw UnknownIdentifierException.create(member);
    }

    @ExportMessage
    Object invokeMember(String member, Object[] args,
                    @CachedLibrary("this") PythonNativeWrapperLibrary lib,
                    @Cached AllToJavaNode allToJavaNode,
                    @Cached CallVarargsMethodNode callNode,
                    @Shared("lookupAttrNode") @Cached LookupInheritedAttributeNode.Dynamic lookupGetattributeNode,
                    @Exclusive @Cached ToSulongNode toSulongNode,
                    @Cached TransformExceptionToNativeNode transformExceptionToNativeNode,
                    @Cached GetNativeNullNode getNativeNullNode) throws UnknownIdentifierException {
        Object attr = lookupGetattributeNode.execute(lib.getDelegate(this), member);
        if (attr != PNone.NO_VALUE) {
            try {
                Object[] convertedArgs = allToJavaNode.execute(args);
                return toSulongNode.execute(callNode.execute(null, attr, convertedArgs, PKeyword.EMPTY_KEYWORDS));
            } catch (PException e) {
                transformExceptionToNativeNode.execute(null, e);
                return toSulongNode.execute(getNativeNullNode.execute());
            }
        }
        throw UnknownIdentifierException.create(member);
    }

    @ExportMessage
    void writeMember(String member, Object value,
                    @CachedLibrary("this") PythonNativeWrapperLibrary lib,
                    @Cached WriteAttributeToDynamicObjectNode writeAttrNode,
                    @Exclusive @Cached ToJavaNode toJavaNode) throws UnknownIdentifierException {
        if (isMemberModifiable(member)) {
            writeAttrNode.execute(lib.getDelegate(this), member, toJavaNode.execute(value));
        } else {
            // reach member is modifiable
            throw UnknownIdentifierException.create(member);
        }
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    boolean hasNativeType() {
        return true;
    }

    @ExportMessage
    Object getNativeType(
                    @CachedLibrary("this") PythonNativeWrapperLibrary lib,
                    @Cached GetLazyClassNode getClassNode,
                    @Cached GetSulongTypeNode getSulongTypeNode) {
        return getSulongTypeNode.execute(getClassNode.execute(lib.getDelegate(this)));
    }

    @ExportMessage
    boolean isPointer(
                    @Cached CExtNodes.IsPointerNode isPointerNode) {
        return isPointerNode.execute(this);
    }

    @ExportMessage
    long asPointer(
                    @Exclusive @Cached PAsPointerNode pAsPointerNode) {
        return pAsPointerNode.execute(this);
    }

    @ExportMessage
    void toNative(
                    @Exclusive @Cached ToPyObjectNode toPyObjectNode,
                    @Exclusive @Cached InvalidateNativeObjectsAllManagedNode invalidateNode) {
        invalidateNode.execute();
        setNativePointer(toPyObjectNode.execute(this));
    }

}
