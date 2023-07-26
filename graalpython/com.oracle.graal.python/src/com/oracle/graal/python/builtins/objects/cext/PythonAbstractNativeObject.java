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
package com.oracle.graal.python.builtins.objects.cext;

import static com.oracle.graal.python.builtins.objects.cext.structs.CFields.PyTypeObject__tp_name;

import java.util.Objects;

import com.oracle.graal.python.builtins.objects.PythonAbstractObject;
import com.oracle.graal.python.builtins.objects.buffer.PythonBufferAcquireLibrary;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes;
import com.oracle.graal.python.builtins.objects.cext.capi.PythonNativeWrapper;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTransitions.NativeObjectReference;
import com.oracle.graal.python.builtins.objects.cext.structs.CStructAccess;
import com.oracle.graal.python.builtins.objects.memoryview.PMemoryView;
import com.oracle.graal.python.builtins.objects.type.TypeNodes;
import com.oracle.graal.python.nodes.classes.IsSubtypeNode;
import com.oracle.graal.python.nodes.interop.PForeignToPTypeNode;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.nodes.util.CannotCastException;
import com.oracle.graal.python.nodes.util.CastToJavaStringNode;
import com.oracle.graal.python.runtime.GilNode;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.library.ExportMessage.Ignore;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.ValueProfile;
import com.oracle.truffle.api.strings.TruffleString;
import com.oracle.truffle.api.utilities.TriState;

@ExportLibrary(InteropLibrary.class)
@ExportLibrary(PythonBufferAcquireLibrary.class)
public final class PythonAbstractNativeObject extends PythonAbstractObject implements PythonNativeObject, PythonNativeClass {

    public final Object object;
    public NativeObjectReference ref;

    public PythonAbstractNativeObject(Object object) {
        assert !(object instanceof Number || object instanceof PythonNativeWrapper || object instanceof String || object instanceof TruffleString);
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

    @Override
    public Object getPtr() {
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
        return PythonUtils.formatJString("PythonAbstractNativeObject(%s)", object);
    }

    @ExportMessage
    int identityHashCode(@CachedLibrary("this.object") InteropLibrary lib) throws UnsupportedMessageException {
        if (lib.isPointer(object)) {
            return Long.hashCode(lib.asPointer(object));
        } else {
            return lib.identityHashCode(object);
        }
    }

    @ExportMessage
    boolean isIdentical(Object other, InteropLibrary otherInterop,
                    @Cached("createClassProfile()") ValueProfile otherProfile,
                    @CachedLibrary(limit = "1") InteropLibrary thisLib,
                    @CachedLibrary(limit = "3") InteropLibrary lib1,
                    @CachedLibrary(limit = "3") InteropLibrary lib2,
                    @Exclusive @Cached GilNode gil) {
        boolean mustRelease = gil.acquire();
        try {
            Object profiled = otherProfile.profile(other);
            if (profiled instanceof PythonAbstractNativeObject) {
                Object otherPtr = ((PythonAbstractNativeObject) other).getPtr();
                if (lib1.isPointer(getPtr())) {
                    if (lib2.isPointer(otherPtr)) {
                        try {
                            return lib1.asPointer(getPtr()) == lib2.asPointer(otherPtr);
                        } catch (UnsupportedMessageException e) {
                            throw CompilerDirectives.shouldNotReachHere(e);
                        }
                    } else {
                        return false;
                    }
                } else {
                    if (lib2.isPointer(otherPtr)) {
                        return false;
                    } else {
                        return lib1.isIdentical(getPtr(), otherPtr, lib2);
                    }
                }
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
                        @CachedLibrary("receiver") InteropLibrary objLib,
                        @CachedLibrary(limit = "1") InteropLibrary otherObjectLib) {
            return TriState.valueOf(objLib.isIdentical(receiver, other, otherObjectLib));
        }

        @Fallback
        static TriState doOther(PythonAbstractNativeObject receiver, Object other) {
            return TriState.UNDEFINED;
        }
    }

    @ExportMessage(library = InteropLibrary.class)
    boolean isMetaObject(
                    @Bind("$node") Node inliningTarget,
                    @Exclusive @Cached TypeNodes.IsTypeNode isType,
                    @Exclusive @Cached GilNode gil) {
        boolean mustRelease = gil.acquire();
        try {
            return isType.execute(inliningTarget, this);
        } finally {
            gil.release(mustRelease);
        }
    }

    @ExportMessage
    boolean isMetaInstance(Object instance,
                    @Bind("$node") Node inliningTarget,
                    @Exclusive @Cached TypeNodes.IsTypeNode isType,
                    @Cached GetClassNode getClassNode,
                    @Cached PForeignToPTypeNode convert,
                    @Cached IsSubtypeNode isSubtype,
                    @Exclusive @Cached GilNode gil) throws UnsupportedMessageException {
        boolean mustRelease = gil.acquire();
        try {
            if (!isType.execute(inliningTarget, this)) {
                throw UnsupportedMessageException.create();
            }
            return isSubtype.execute(getClassNode.execute(inliningTarget, convert.executeConvert(instance)), this);
        } finally {
            gil.release(mustRelease);
        }
    }

    @ExportMessage
    String getMetaSimpleName(
                    @Bind("$node") Node inliningTarget,
                    @Shared("isType") @Cached TypeNodes.IsTypeNode isType,
                    @Shared("getTypeMember") @Cached CStructAccess.ReadCharPtrNode getTpNameNode,
                    @Shared("castToJavaStringNode") @Cached CastToJavaStringNode castToJavaStringNode,
                    @Exclusive @Cached GilNode gil) throws UnsupportedMessageException {
        return getSimpleName(getMetaQualifiedName(inliningTarget, isType, getTpNameNode, castToJavaStringNode, gil));
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
                    @Bind("$node") Node inliningTarget,
                    @Shared("isType") @Cached TypeNodes.IsTypeNode isType,
                    @Shared("getTypeMember") @Cached CStructAccess.ReadCharPtrNode getTpNameNode,
                    @Shared("castToJavaStringNode") @Cached CastToJavaStringNode castToJavaStringNode,
                    @Exclusive @Cached GilNode gil) throws UnsupportedMessageException {
        boolean mustRelease = gil.acquire();
        try {
            if (!isType.execute(inliningTarget, this)) {
                throw UnsupportedMessageException.create();
            }
            // 'tp_name' contains the fully-qualified name, i.e., 'module.A.B...'
            try {
                return castToJavaStringNode.execute(getTpNameNode.readFromObj(this, PyTypeObject__tp_name));
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
