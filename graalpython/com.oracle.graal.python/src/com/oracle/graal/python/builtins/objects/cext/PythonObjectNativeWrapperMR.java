/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.cext.CExtNodes.AsPythonObjectNode;
import com.oracle.graal.python.builtins.objects.cext.CExtNodes.CExtBaseNode;
import com.oracle.graal.python.builtins.objects.cext.CExtNodes.MaterializeDelegateNode;
import com.oracle.graal.python.builtins.objects.cext.CExtNodes.ToJavaNode;
import com.oracle.graal.python.builtins.objects.cext.CExtNodes.ToSulongNode;
import com.oracle.graal.python.builtins.objects.cext.NativeWrappers.PrimitiveNativeWrapper;
import com.oracle.graal.python.builtins.objects.cext.NativeWrappers.PythonClassInitNativeWrapper;
import com.oracle.graal.python.builtins.objects.cext.NativeWrappers.PythonClassNativeWrapper;
import com.oracle.graal.python.builtins.objects.cext.NativeWrappers.PythonNativeWrapper;
import com.oracle.graal.python.builtins.objects.cext.NativeWrappers.PythonObjectNativeWrapper;
import com.oracle.graal.python.builtins.objects.cext.PythonObjectNativeWrapperMRFactory.GetSulongTypeNodeGen;
import com.oracle.graal.python.builtins.objects.cext.PythonObjectNativeWrapperMRFactory.InvalidateNativeObjectsAllManagedNodeGen;
import com.oracle.graal.python.builtins.objects.cext.PythonObjectNativeWrapperMRFactory.PAsPointerNodeGen;
import com.oracle.graal.python.builtins.objects.cext.PythonObjectNativeWrapperMRFactory.PGetDynamicTypeNodeGen;
import com.oracle.graal.python.builtins.objects.cext.PythonObjectNativeWrapperMRFactory.ToPyObjectNodeGen;
import com.oracle.graal.python.builtins.objects.ints.PInt;
import com.oracle.graal.python.builtins.objects.type.LazyPythonClass;
import com.oracle.graal.python.builtins.objects.type.PythonClass;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.object.GetLazyClassNode;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.interop.PythonMessageResolution;
import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.KeyInfo;
import com.oracle.truffle.api.interop.Message;
import com.oracle.truffle.api.interop.MessageResolution;
import com.oracle.truffle.api.interop.Resolve;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.ConditionProfile;

@MessageResolution(receiverType = PythonNativeWrapper.class)
public class PythonObjectNativeWrapperMR {
    private static final String GP_OBJECT = "gp_object";

    @SuppressWarnings("unknown-message")
    @Resolve(message = "com.oracle.truffle.llvm.spi.GetDynamicType")
    abstract static class GetDynamicTypeNode extends Node {
        @Child private PGetDynamicTypeNode getDynamicTypeNode = PGetDynamicTypeNode.create();

        public Object access(PythonNativeWrapper object) {
            return getDynamicTypeNode.execute(object);
        }
    }

    abstract static class PGetDynamicTypeNode extends PNodeWithContext {
        @Child private GetLazyClassNode getLazyClassNode = GetLazyClassNode.create();
        @Child private GetSulongTypeNode getSulongTypeNode = GetSulongTypeNode.create();
        @Child private AsPythonObjectNode getDelegate = AsPythonObjectNode.create();

        public abstract Object execute(PythonNativeWrapper obj);

        @Specialization(guards = "obj.isIntLike()")
        Object doIntLike(@SuppressWarnings("unused") PrimitiveNativeWrapper obj,
                        @Cached("getLongobjectType()") Object cachedSulongType) {
            return cachedSulongType;
        }

        @Specialization(guards = "obj.isBool()")
        Object doBool(@SuppressWarnings("unused") PrimitiveNativeWrapper obj,
                        @Cached("getBoolobjectType()") Object cachedSulongType) {
            return cachedSulongType;
        }

        @Specialization(guards = "obj.isDouble()")
        Object doDouble(@SuppressWarnings("unused") PrimitiveNativeWrapper obj,
                        @Cached("getFloatobjectType()") Object cachedSulongType) {
            return cachedSulongType;
        }

        @Specialization
        Object doGeneric(PythonNativeWrapper obj) {
            return getSulongTypeNode.execute(getLazyClassNode.execute(getDelegate.execute(obj)));
        }

        protected Object getLongobjectType() {
            return getSulongTypeNode.execute(PythonBuiltinClassType.PInt);
        }

        protected Object getBoolobjectType() {
            return getSulongTypeNode.execute(PythonBuiltinClassType.Boolean);
        }

        protected Object getFloatobjectType() {
            return getSulongTypeNode.execute(PythonBuiltinClassType.PFloat);
        }

        public static PGetDynamicTypeNode create() {
            return PGetDynamicTypeNodeGen.create();
        }
    }

    abstract static class GetSulongTypeNode extends PNodeWithContext {

        private final ConditionProfile profile = ConditionProfile.createBinaryProfile();

        public abstract Object execute(LazyPythonClass clazz);

        @Specialization(guards = "clazz == cachedClass", limit = "10")
        Object doBuiltinCached(@SuppressWarnings("unused") PythonBuiltinClassType clazz,
                        @Cached("clazz") @SuppressWarnings("unused") PythonBuiltinClassType cachedClass,
                        @Cached("getSulongTypeForBuiltinClass(clazz)") Object sulongType) {
            return sulongType;
        }

        @Specialization(replaces = "doBuiltinCached")
        Object doBuiltinGeneric(PythonBuiltinClassType clazz) {
            return getSulongTypeForBuiltinClass(clazz);
        }

        @Specialization(assumptions = "singleContextAssumption()", guards = "clazz == cachedClass")
        Object doGeneric(@SuppressWarnings("unused") PythonClass clazz,
                        @Cached("clazz") @SuppressWarnings("unused") PythonClass cachedClass,
                        @Cached("doGeneric(clazz)") Object sulongType) {
            return sulongType;
        }

        @Specialization
        Object doGeneric(PythonClass clazz) {
            return getSulongTypeForClass(clazz);
        }

        protected Object getSulongTypeForBuiltinClass(PythonBuiltinClassType clazz) {
            PythonClass pythonClass = getPythonClass(clazz, profile);
            return getSulongTypeForClass(pythonClass);
        }

        private static Object getSulongTypeForClass(PythonClass klass) {
            Object sulongType = klass.getSulongType();
            if (sulongType == null) {
                CompilerDirectives.transferToInterpreter();
                sulongType = findBuiltinClass(klass);
                if (sulongType == null) {
                    throw new IllegalStateException("sulong type for " + klass.getName() + " was not registered");
                }
            }
            return sulongType;
        }

        private static Object findBuiltinClass(PythonClass klass) {
            PythonClass[] mro = klass.getMethodResolutionOrder();
            Object sulongType = null;
            for (PythonClass superClass : mro) {
                sulongType = superClass.getSulongType();
                if (sulongType != null) {
                    klass.setSulongType(sulongType);
                    break;
                }
            }
            return sulongType;
        }

        public static GetSulongTypeNode create() {
            return GetSulongTypeNodeGen.create();
        }

    }

    @Resolve(message = "EXECUTE")
    abstract static class ExecuteNode extends Node {
        @Child PythonMessageResolution.ExecuteNode executeNode;
        @Child private ToJavaNode toJavaNode;
        @Child private ToSulongNode toSulongNode;

        public Object access(PythonNativeWrapper object, Object[] arguments) {
            if (executeNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                executeNode = insert(new PythonMessageResolution.ExecuteNode());
            }
            // convert args
            Object[] converted = new Object[arguments.length];
            for (int i = 0; i < arguments.length; i++) {
                converted[i] = getToJavaNode().execute(arguments[i]);
            }
            Object result;
            try {
                result = executeNode.execute(object.getDelegate(), converted);
            } catch (PException e) {
                result = PNone.NO_VALUE;
            }
            return getToSulongNode().execute(result);
        }

        private ToJavaNode getToJavaNode() {
            if (toJavaNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                toJavaNode = insert(ToJavaNode.create());
            }
            return toJavaNode;
        }

        private ToSulongNode getToSulongNode() {
            if (toSulongNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                toSulongNode = insert(ToSulongNode.create());
            }
            return toSulongNode;
        }
    }

    @Resolve(message = "KEY_INFO")
    abstract static class KeyInfoNode extends Node {
        public int access(Object object, Object fieldName) {
            int info = KeyInfo.NONE;
            if (object instanceof PythonObjectNativeWrapper) {
                if (fieldName.equals(GP_OBJECT)) {
                    info |= KeyInfo.READABLE;
                } else if (fieldName instanceof String && NativeMemberNames.isValid((String) fieldName)) {
                    info |= KeyInfo.READABLE;

                    // TODO be more specific
                    info |= KeyInfo.MODIFIABLE;
                }
            }
            return info;
        }
    }

    @Resolve(message = "HAS_KEYS")
    abstract static class HasKeysNode extends Node {
        public Object access(Object obj) {
            return obj instanceof PythonNativeWrapper;
        }
    }

    @Resolve(message = "KEYS")
    abstract static class PForeignKeysNode extends Node {
        @Child Node objKeys = Message.KEYS.createNode();

        public Object access(Object object) {
            if (object instanceof PythonNativeWrapper) {
                return PythonLanguage.getContextRef().get().getEnv().asGuestValue(new String[]{GP_OBJECT});
            } else {
                throw UnsupportedMessageException.raise(Message.KEYS);
            }
        }
    }

    @Resolve(message = "TO_NATIVE")
    abstract static class ToNativeNode extends Node {
        @Child private ToPyObjectNode toPyObjectNode;
        @Child private InvalidateNativeObjectsAllManagedNode invalidateNode = InvalidateNativeObjectsAllManagedNode.create();

        Object access(PythonClassInitNativeWrapper obj) {
            invalidateNode.execute();
            if (!obj.isNative()) {
                obj.setNativePointer(getToPyObjectNode().execute(obj));
            }
            return obj;
        }

        Object access(PythonNativeWrapper obj) {
            assert !(obj instanceof PythonClassInitNativeWrapper);
            invalidateNode.execute();
            if (!obj.isNative()) {
                obj.setNativePointer(getToPyObjectNode().execute(obj));
            }
            return obj;
        }

        private ToPyObjectNode getToPyObjectNode() {
            if (toPyObjectNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                toPyObjectNode = insert(ToPyObjectNode.create());
            }
            return toPyObjectNode;
        }
    }

    @Resolve(message = "IS_POINTER")
    abstract static class IsPointerNode extends Node {
        @Child private CExtNodes.IsPointerNode pIsPointerNode = CExtNodes.IsPointerNode.create();

        boolean access(PythonNativeWrapper obj) {
            return pIsPointerNode.execute(obj);
        }
    }

    abstract static class InvalidateNativeObjectsAllManagedNode extends PNodeWithContext {

        public abstract void execute();

        @Specialization(assumptions = {"singleContextAssumption()", "nativeObjectsAllManagedAssumption()"})
        void doValid() {
            nativeObjectsAllManagedAssumption().invalidate();
        }

        @Specialization
        void doInvalid() {
        }

        protected Assumption nativeObjectsAllManagedAssumption() {
            return getContext().getNativeObjectsAllManagedAssumption();
        }

        public static InvalidateNativeObjectsAllManagedNode create() {
            return InvalidateNativeObjectsAllManagedNodeGen.create();
        }
    }

    @Resolve(message = "AS_POINTER")
    abstract static class AsPointerNode extends Node {
        @Child private PAsPointerNode pAsPointerNode = PAsPointerNode.create();

        long access(PythonNativeWrapper obj) {
            return pAsPointerNode.execute(obj);
        }
    }

    abstract static class PAsPointerNode extends PNodeWithContext {
        @Child private Node asPointerNode;

        public abstract long execute(PythonNativeWrapper o);

        @Specialization(guards = {"obj.isBool()", "!obj.isNative()"})
        long doBoolNotNative(PrimitiveNativeWrapper obj,
                        @Cached("create()") MaterializeDelegateNode materializeNode) {
            // special case for True and False singletons
            PInt boxed = (PInt) materializeNode.execute(obj);
            assert obj.getNativePointer() == boxed.getNativeWrapper().getNativePointer();
            return doFast(obj);
        }

        @Specialization(guards = {"obj.isBool()", "obj.isNative()"})
        long doBoolNative(PrimitiveNativeWrapper obj) {
            return doFast(obj);
        }

        @Specialization(guards = "!isBoolNativeWrapper(obj)")
        long doFast(PythonNativeWrapper obj) {
            // the native pointer object must either be a TruffleObject or a primitive
            return ensureLong(obj.getNativePointer());
        }

        private long ensureLong(Object nativePointer) {
            if (nativePointer instanceof Long) {
                return (long) nativePointer;
            } else {
                if (asPointerNode == null) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    asPointerNode = insert(Message.AS_POINTER.createNode());
                }
                try {
                    return ForeignAccess.sendAsPointer(asPointerNode, (TruffleObject) nativePointer);
                } catch (UnsupportedMessageException e) {
                    CompilerDirectives.transferToInterpreter();
                    throw e.raise();
                }
            }
        }

        protected static boolean isBoolNativeWrapper(Object obj) {
            return obj instanceof PrimitiveNativeWrapper && ((PrimitiveNativeWrapper) obj).isBool();
        }

        public static PAsPointerNode create() {
            return PAsPointerNodeGen.create();
        }
    }

    abstract static class ToPyObjectNode extends CExtBaseNode {
        @CompilationFinal private TruffleObject PyObjectHandle_FromJavaObject;
        @CompilationFinal private TruffleObject PyObjectHandle_FromJavaType;
        @CompilationFinal private TruffleObject PyNoneHandle;
        @Child private PCallNativeNode callNativeUnary;
        @Child private PCallNativeNode callNativeBinary;
        @Child private CExtNodes.ToSulongNode toSulongNode;

        public abstract Object execute(PythonNativeWrapper wrapper);

        @Specialization(guards = "isManagedPythonClass(wrapper)")
        Object doClass(PythonClassNativeWrapper wrapper) {
            return callUnaryIntoCapi(getPyObjectHandle_ForJavaType(), wrapper);
        }

        @Fallback
        Object doObject(PythonNativeWrapper wrapper) {
            return callUnaryIntoCapi(getPyObjectHandle_ForJavaObject(), wrapper);
        }

        private TruffleObject getPyObjectHandle_ForJavaType() {
            if (PyObjectHandle_FromJavaType == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                PyObjectHandle_FromJavaType = importCAPISymbol(NativeCAPISymbols.FUN_PY_OBJECT_HANDLE_FOR_JAVA_TYPE);
            }
            return PyObjectHandle_FromJavaType;
        }

        private TruffleObject getPyObjectHandle_ForJavaObject() {
            if (PyObjectHandle_FromJavaObject == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                PyObjectHandle_FromJavaObject = importCAPISymbol(NativeCAPISymbols.FUN_PY_OBJECT_HANDLE_FOR_JAVA_OBJECT);
            }
            return PyObjectHandle_FromJavaObject;
        }

        protected static boolean isManagedPythonClass(PythonClassNativeWrapper wrapper) {
            assert wrapper.getDelegate() instanceof PythonClass;
            return !(wrapper.getDelegate() instanceof PythonNativeClass);
        }

        private Object callUnaryIntoCapi(TruffleObject fun, Object arg) {
            if (callNativeUnary == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                callNativeUnary = insert(PCallNativeNode.create());
            }
            return callNativeUnary.execute(fun, new Object[]{arg});
        }

        public static ToPyObjectNode create() {
            return ToPyObjectNodeGen.create();
        }
    }
}
