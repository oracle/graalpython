/*
 * Copyright (c) 2019, 2021, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.graal.python.builtins.objects.cext.capi.NativeCAPISymbol.FUN_PY_OBJECT_GENERIC_GET_DICT;

import java.util.Objects;

import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.PythonAbstractObject;
import com.oracle.graal.python.builtins.objects.buffer.PythonBufferAcquireLibrary;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.GetTypeMemberNode;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.PCallCapiFunction;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.ToJavaNode;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.ToSulongNode;
import com.oracle.graal.python.builtins.objects.cext.capi.NativeMember;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.memoryview.PMemoryView;
import com.oracle.graal.python.builtins.objects.object.PythonObjectLibrary;
import com.oracle.graal.python.builtins.objects.type.TypeNodes;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.classes.IsSubtypeNode;
import com.oracle.graal.python.nodes.interop.PForeignToPTypeNode;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.nodes.util.CannotCastException;
import com.oracle.graal.python.nodes.util.CastToJavaStringNode;
import com.oracle.graal.python.runtime.GilNode;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.library.ExportMessage.Ignore;
import com.oracle.truffle.api.profiles.ValueProfile;
import com.oracle.truffle.api.utilities.TriState;

@ExportLibrary(PythonObjectLibrary.class)
@ExportLibrary(InteropLibrary.class)
@ExportLibrary(PythonBufferAcquireLibrary.class)
public final class PythonAbstractNativeObject extends PythonAbstractObject implements PythonNativeObject, PythonNativeClass {

    public final TruffleObject object;

    public PythonAbstractNativeObject(TruffleObject object) {
        this.object = object;
    }

    public int compareTo(Object o) {
        return 0;
    }

    public void lookupChanged() {
        // TODO invalidate cached native MRO
        CompilerDirectives.transferToInterpreter();
        throw new UnsupportedOperationException("not yet implemented");
    }

    public TruffleObject getPtr() {
        return object;
    }

    @Override
    public int hashCode() {
        CompilerAsserts.neverPartOfCompilation();
        // this is important for the default '__hash__' implementation
        return Objects.hashCode(object);
    }

    @Ignore
    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        PythonAbstractNativeObject other = (PythonAbstractNativeObject) obj;
        return Objects.equals(object, other.object);
    }

    public boolean equalsProfiled(Object obj, ValueProfile profile) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        PythonAbstractNativeObject other = (PythonAbstractNativeObject) obj;
        return Objects.equals(profile.profile(object), profile.profile(other.object));
    }

    @Override
    public String toString() {
        CompilerAsserts.neverPartOfCompilation();
        return String.format("PythonAbstractNativeObject(%s)", object);
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    public boolean hasDict() {
        return true;
    }

    @ExportMessage
    @SuppressWarnings({"static-method", "unused"})
    public void setDict(PDict value) throws UnsupportedMessageException {
        throw UnsupportedMessageException.create();
    }

    @ExportMessage
    @SuppressWarnings({"static-method", "unused"})
    public void deleteDict() throws UnsupportedMessageException {
        throw UnsupportedMessageException.create();
    }

    @ExportMessage
    public PDict getDict(
                    @Exclusive @Cached PRaiseNode raiseNode,
                    @Exclusive @Cached ToSulongNode toSulong,
                    @Exclusive @Cached ToJavaNode toJava,
                    @Exclusive @Cached PCallCapiFunction callGetDictNode) {
        Object javaDict = toJava.execute(callGetDictNode.call(FUN_PY_OBJECT_GENERIC_GET_DICT, toSulong.execute(this)));
        if (javaDict instanceof PDict) {
            return (PDict) javaDict;
        } else if (javaDict == PNone.NO_VALUE) {
            return null;
        } else {
            throw raiseNode.raise(PythonBuiltinClassType.TypeError, ErrorMessages.DICT_MUST_BE_SET_TO_DICT, javaDict);
        }
    }

    @ExportMessage
    int identityHashCode(@CachedLibrary("this.object") InteropLibrary lib) throws UnsupportedMessageException {
        return lib.identityHashCode(object);
    }

    @ExportMessage
    boolean isIdentical(Object other, InteropLibrary otherInterop,
                    @Cached("createClassProfile()") ValueProfile otherProfile,
                    @CachedLibrary(limit = "1") InteropLibrary thisLib,
                    @CachedLibrary("this.object") InteropLibrary objLib,
                    @CachedLibrary(limit = "1") InteropLibrary otherObjLib,
                    @Exclusive @Cached GilNode gil) {
        boolean mustRelease = gil.acquire();
        try {
            Object profiled = otherProfile.profile(other);
            if (profiled instanceof PythonAbstractNativeObject) {
                return objLib.isIdentical(object, ((PythonAbstractNativeObject) profiled).object, otherObjLib);
            }
            return otherInterop.isIdentical(profiled, this, thisLib);
        } finally {
            gil.release(mustRelease);
        }
    }

    @ExportMessage
    @SuppressWarnings("unused")
    static final class IsIdenticalOrUndefined {
        @Specialization
        static TriState doPythonAbstractNativeObject(PythonAbstractNativeObject receiver, PythonAbstractNativeObject other,
                        @CachedLibrary("receiver.object") InteropLibrary objLib,
                        @CachedLibrary(limit = "1") InteropLibrary otherObjectLib) {
            return TriState.valueOf(objLib.isIdentical(receiver.object, other.object, otherObjectLib));
        }

        @Fallback
        static TriState doOther(PythonAbstractNativeObject receiver, Object other) {
            return TriState.UNDEFINED;
        }
    }

    @ExportMessage(library = PythonObjectLibrary.class, name = "isLazyPythonClass")
    @ExportMessage(library = InteropLibrary.class)
    boolean isMetaObject(
                    @Exclusive @Cached TypeNodes.IsTypeNode isType,
                    @Exclusive @Cached GilNode gil) {
        boolean mustRelease = gil.acquire();
        try {
            return isType.execute(this);
        } finally {
            gil.release(mustRelease);
        }
    }

    @ExportMessage
    boolean isMetaInstance(Object instance,
                    @Shared("isType") @Cached TypeNodes.IsTypeNode isType,
                    @Cached GetClassNode getClassNode,
                    @Cached PForeignToPTypeNode convert,
                    @Cached IsSubtypeNode isSubtype,
                    @Exclusive @Cached GilNode gil) throws UnsupportedMessageException {
        boolean mustRelease = gil.acquire();
        try {
            if (!isType.execute(this)) {
                throw UnsupportedMessageException.create();
            }
            return isSubtype.execute(getClassNode.execute(convert.executeConvert(instance)), this);
        } finally {
            gil.release(mustRelease);
        }
    }

    @ExportMessage
    String getMetaSimpleName(
                    @Shared("isType") @Cached TypeNodes.IsTypeNode isType,
                    @Shared("getTypeMember") @Cached GetTypeMemberNode getTpNameNode,
                    @Shared("castToJavaStringNode") @Cached CastToJavaStringNode castToJavaStringNode,
                    @Exclusive @Cached GilNode gil) throws UnsupportedMessageException {
        return getSimpleName(getMetaQualifiedName(isType, getTpNameNode, castToJavaStringNode, gil));
    }

    @TruffleBoundary
    private static String getSimpleName(String fqname) {
        int firstDot = fqname.indexOf('.');
        if (firstDot != -1) {
            return fqname.substring(firstDot + 1);
        }
        return fqname;
    }

    @ExportMessage
    String getMetaQualifiedName(
                    @Shared("isType") @Cached TypeNodes.IsTypeNode isType,
                    @Shared("getTypeMember") @Cached GetTypeMemberNode getTpNameNode,
                    @Shared("castToJavaStringNode") @Cached CastToJavaStringNode castToJavaStringNode,
                    @Exclusive @Cached GilNode gil) throws UnsupportedMessageException {
        boolean mustRelease = gil.acquire();
        try {
            if (!isType.execute(this)) {
                throw UnsupportedMessageException.create();
            }
            // 'tp_name' contains the fully-qualified name, i.e., 'module.A.B...'
            try {
                return castToJavaStringNode.execute(getTpNameNode.execute(this, NativeMember.TP_NAME));
            } catch (CannotCastException e) {
                throw CompilerDirectives.shouldNotReachHere();
            }
        } finally {
            gil.release(mustRelease);
        }
    }

    @ExportMessage
    boolean hasBuffer(
                    @Cached CExtNodes.HasNativeBufferNode hasNativeBuffer) {
        return hasNativeBuffer.execute(this);
    }

    @ExportMessage
    Object acquire(int flags,
                    @Cached CExtNodes.CreateMemoryViewFromNativeNode createMemoryView) {
        PMemoryView mv = createMemoryView.execute(this, flags);
        mv.setShouldReleaseImmediately(true);
        return mv;
    }
}
