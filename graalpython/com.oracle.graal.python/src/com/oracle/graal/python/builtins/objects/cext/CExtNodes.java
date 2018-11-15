/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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

import java.util.List;

import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.modules.BuiltinFunctions.GetAttrNode;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.PythonAbstractObject;
import com.oracle.graal.python.builtins.objects.cext.CExtNodesFactory.AllToJavaNodeGen;
import com.oracle.graal.python.builtins.objects.cext.CExtNodesFactory.AllToSulongNodeGen;
import com.oracle.graal.python.builtins.objects.cext.CExtNodesFactory.AsCharPointerNodeGen;
import com.oracle.graal.python.builtins.objects.cext.CExtNodesFactory.AsDoubleNodeGen;
import com.oracle.graal.python.builtins.objects.cext.CExtNodesFactory.AsLongNodeGen;
import com.oracle.graal.python.builtins.objects.cext.CExtNodesFactory.AsPythonObjectNodeGen;
import com.oracle.graal.python.builtins.objects.cext.CExtNodesFactory.CextUpcallNodeGen;
import com.oracle.graal.python.builtins.objects.cext.CExtNodesFactory.DirectUpcallNodeGen;
import com.oracle.graal.python.builtins.objects.cext.CExtNodesFactory.GetNativeClassNodeGen;
import com.oracle.graal.python.builtins.objects.cext.CExtNodesFactory.IsPointerNodeGen;
import com.oracle.graal.python.builtins.objects.cext.CExtNodesFactory.MaterializeDelegateNodeGen;
import com.oracle.graal.python.builtins.objects.cext.CExtNodesFactory.ObjectUpcallNodeGen;
import com.oracle.graal.python.builtins.objects.cext.CExtNodesFactory.ToJavaNodeGen;
import com.oracle.graal.python.builtins.objects.cext.CExtNodesFactory.ToSulongNodeGen;
import com.oracle.graal.python.builtins.objects.cext.NativeWrappers.DynamicObjectNativeWrapper;
import com.oracle.graal.python.builtins.objects.cext.NativeWrappers.PrimitiveNativeWrapper;
import com.oracle.graal.python.builtins.objects.cext.NativeWrappers.PythonClassNativeWrapper;
import com.oracle.graal.python.builtins.objects.cext.NativeWrappers.PythonNativeWrapper;
import com.oracle.graal.python.builtins.objects.cext.NativeWrappers.PythonObjectNativeWrapper;
import com.oracle.graal.python.builtins.objects.cext.NativeWrappers.TruffleObjectNativeWrapper;
import com.oracle.graal.python.builtins.objects.floats.PFloat;
import com.oracle.graal.python.builtins.objects.function.PFunction;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.builtins.objects.function.PythonCallable;
import com.oracle.graal.python.builtins.objects.ints.PInt;
import com.oracle.graal.python.builtins.objects.str.PString;
import com.oracle.graal.python.builtins.objects.type.PythonBuiltinClass;
import com.oracle.graal.python.builtins.objects.type.PythonClass;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.SpecialMethodNames;
import com.oracle.graal.python.nodes.argument.CreateArgumentsNode;
import com.oracle.graal.python.nodes.argument.ReadArgumentNode;
import com.oracle.graal.python.nodes.argument.ReadVarArgsNode;
import com.oracle.graal.python.nodes.attributes.ReadAttributeFromObjectNode;
import com.oracle.graal.python.nodes.call.CallNode;
import com.oracle.graal.python.nodes.call.InvokeNode;
import com.oracle.graal.python.nodes.call.special.CallBinaryMethodNode;
import com.oracle.graal.python.nodes.call.special.CallTernaryMethodNode;
import com.oracle.graal.python.nodes.call.special.CallUnaryMethodNode;
import com.oracle.graal.python.nodes.call.special.LookupAndCallUnaryNode;
import com.oracle.graal.python.nodes.classes.IsSubtypeNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonTernaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.nodes.object.GetLazyClassNode;
import com.oracle.graal.python.nodes.object.IsBuiltinClassProfile;
import com.oracle.graal.python.nodes.util.CastToIndexNode;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.exception.PythonErrorType;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.InteropException;
import com.oracle.truffle.api.interop.Message;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.NodeUtil;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.api.profiles.ConditionProfile;

public abstract class CExtNodes {

    /**
     * For some builtin classes, the CPython approach to creating a subclass instance is to just
     * call the alloc function and then assign some fields. This needs to be done in C. This node
     * will call that subtype C function with two arguments, the C type object and an object
     * argument to fill in from.
     */
    public static class SubtypeNew extends CExtBaseNode {
        @Child private Node executeNode = Message.EXECUTE.createNode();
        @Child private ToSulongNode toSulongNode = ToSulongNode.create();
        @Child private ToJavaNode toJavaNode = ToJavaNode.create();

        private final String functionName;

        @CompilationFinal private TruffleObject subtypeFunc;

        /**
         * @param typenamePrefix the <code>typename</code> in <code>typename_subtype_new</code>
         */
        public SubtypeNew(String typenamePrefix) {
            functionName = typenamePrefix + "_subtype_new";
        }

        public Object execute(PythonNativeClass object, Object arg) {
            try {
                return toJavaNode.execute(ForeignAccess.sendExecute(executeNode, getFunction(), toSulongNode.execute(object), arg));
            } catch (UnsupportedMessageException | UnsupportedTypeException | ArityException e) {
                throw new IllegalStateException("C subtype_new function failed", e);
            }
        }

        private TruffleObject getFunction() {
            if (subtypeFunc == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                subtypeFunc = importCAPISymbol(functionName);
            }
            return subtypeFunc;
        }
    }

    public static class FromNativeSubclassNode<T> extends CExtBaseNode {
        private final PythonBuiltinClassType expectedType;
        private final String conversionFuncName;
        @CompilationFinal private TruffleObject conversionFunc;
        @Child private Node executeNode;
        @Child private GetClassNode getClass = GetClassNode.create();
        @Child private IsSubtypeNode isSubtype = IsSubtypeNode.create();
        @Child private ToSulongNode toSulongNode;

        private FromNativeSubclassNode(PythonBuiltinClassType expectedType, String conversionFuncName) {
            this.expectedType = expectedType;
            this.conversionFuncName = conversionFuncName;
        }

        private PythonBuiltinClass getExpectedClass() {
            return getCore().lookupType(expectedType);
        }

        private Node getExecNode() {
            if (executeNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                executeNode = insert(Message.EXECUTE.createNode());
            }
            return executeNode;
        }

        private TruffleObject getConversionFunc() {
            if (conversionFunc == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                conversionFunc = importCAPISymbol(conversionFuncName);
            }
            return conversionFunc;
        }

        private ToSulongNode getToSulongNode() {
            if (toSulongNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                toSulongNode = insert(ToSulongNode.create());
            }
            return toSulongNode;
        }

        @SuppressWarnings("unchecked")
        public T execute(PythonNativeObject object) {
            if (isSubtype(object)) {
                try {
                    return (T) ForeignAccess.sendExecute(getExecNode(), getConversionFunc(), getToSulongNode().execute(object));
                } catch (UnsupportedMessageException | UnsupportedTypeException | ArityException e) {
                    throw new IllegalStateException("C object conversion function failed", e);
                }
            }
            return null;
        }

        public boolean isSubtype(PythonNativeObject object) {
            return isSubtype.execute(getClass.execute(object), getExpectedClass());
        }

        public static <T> FromNativeSubclassNode<T> create(PythonBuiltinClassType expectedType, String conversionFuncName) {
            return new FromNativeSubclassNode<>(expectedType, conversionFuncName);
        }
    }

    @ImportStatic(PGuards.class)
    abstract static class CExtBaseNode extends PNodeWithContext {
        @Child private Node readSymbolNode;

        protected static boolean isNativeWrapper(Object obj) {
            return obj instanceof PythonNativeWrapper;
        }

        protected static boolean isNativeNull(Object object) {
            return object instanceof PythonNativeNull;
        }

        protected static boolean isMaterialized(PrimitiveNativeWrapper wrapper) {
            return wrapper.getMaterializedObject() != null;
        }

        protected TruffleObject importCAPISymbol(String name) {
            TruffleObject capiLibrary = (TruffleObject) getContext().getCapiLibrary();
            if (readSymbolNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                readSymbolNode = insert(Message.READ.createNode());
            }
            try {
                return (TruffleObject) ForeignAccess.sendRead(readSymbolNode, capiLibrary, name);
            } catch (UnknownIdentifierException | UnsupportedMessageException e) {
                CompilerDirectives.transferToInterpreter();
                throw e.raise();
            }
        }

    }

    public abstract static class ToSulongNode extends CExtBaseNode {

        public abstract Object execute(Object obj);

        @Specialization
        Object doString(String str,
                        @Cached("createBinaryProfile()") ConditionProfile noWrapperProfile) {
            return PythonObjectNativeWrapper.wrap(factory().createString(str), noWrapperProfile);
        }

        @Specialization
        Object doBoolean(boolean b,
                        @Cached("createBinaryProfile()") ConditionProfile profile) {
            PInt boxed = factory().createInt(b);
            DynamicObjectNativeWrapper nativeWrapper = boxed.getNativeWrapper();
            if (profile.profile(nativeWrapper == null)) {
                nativeWrapper = PrimitiveNativeWrapper.createBool(b);
                boxed.setNativeWrapper(nativeWrapper);
            }
            return nativeWrapper;
        }

        @Specialization
        Object doInteger(int i) {
            return PrimitiveNativeWrapper.createInt(i);
        }

        @Specialization
        Object doLong(long l) {
            return PrimitiveNativeWrapper.createLong(l);
        }

        @Specialization
        Object doDouble(double d) {
            return PrimitiveNativeWrapper.createDouble(d);
        }

        @Specialization
        Object doNativeClass(PythonNativeClass nativeClass) {
            return nativeClass.object;
        }

        @Specialization
        Object doNativeObject(PythonNativeObject nativeObject) {
            return nativeObject.object;
        }

        @Specialization
        Object doNativeNull(PythonNativeNull object) {
            return object.getPtr();
        }

        @Specialization(guards = {"!isNativeClass(object)", "object == cachedObject"}, limit = "3")
        Object doPythonClass(@SuppressWarnings("unused") PythonClass object,
                        @SuppressWarnings("unused") @Cached("object") PythonClass cachedObject,
                        @Cached("wrap(object)") PythonClassNativeWrapper wrapper) {
            return wrapper;
        }

        @Specialization(replaces = "doPythonClass", guards = {"!isNativeClass(object)"})
        Object doPythonClassUncached(PythonClass object) {
            return PythonClassNativeWrapper.wrap(object);
        }

        @Specialization(guards = {"cachedClass == object.getClass()", "!isPythonClass(object)", "!isNativeObject(object)", "!isNoValue(object)"}, limit = "3")
        Object runAbstractObjectCached(PythonAbstractObject object,
                        @Cached("createBinaryProfile()") ConditionProfile noWrapperProfile,
                        @Cached("object.getClass()") Class<? extends PythonAbstractObject> cachedClass) {
            assert object != PNone.NO_VALUE;
            return PythonObjectNativeWrapper.wrap(CompilerDirectives.castExact(object, cachedClass), noWrapperProfile);
        }

        @Specialization(guards = {"!isPythonClass(object)", "!isNativeObject(object)", "!isNoValue(object)"}, replaces = "runAbstractObjectCached")
        Object runAbstractObject(PythonAbstractObject object,
                        @Cached("createBinaryProfile()") ConditionProfile noWrapperProfile) {
            assert object != PNone.NO_VALUE;
            return PythonObjectNativeWrapper.wrap(object, noWrapperProfile);
        }

        @Specialization(guards = {"isForeignObject(object)", "!isNativeWrapper(object)", "!isNativeNull(object)"})
        Object doForeignObject(TruffleObject object) {
            return TruffleObjectNativeWrapper.wrap(object);
        }

        @Fallback
        Object run(Object obj) {
            assert obj != null : "Java 'null' cannot be a Sulong value";
            return obj;
        }

        protected static boolean isNativeClass(PythonAbstractObject o) {
            return o instanceof PythonNativeClass;
        }

        protected static boolean isPythonClass(PythonAbstractObject o) {
            return o instanceof PythonClass;
        }

        protected static boolean isNativeObject(PythonAbstractObject o) {
            return o instanceof PythonNativeObject;
        }

        public static ToSulongNode create() {
            return ToSulongNodeGen.create();
        }
    }

    /**
     * Unwraps objects contained in {@link PythonObjectNativeWrapper} instances or wraps objects
     * allocated in native code for consumption in Java.
     */
    public abstract static class AsPythonObjectNode extends CExtBaseNode {
        @Child private MaterializeDelegateNode materializeNode;
        @Child private IsPointerNode isPointerNode;

        public abstract Object execute(Object value);

        @Specialization(guards = "object.isBool()")
        boolean doBoolNativeWrapper(PrimitiveNativeWrapper object) {
            return object.getBool();
        }

        @Specialization(guards = {"object.isByte()", "!isNative(object)"})
        byte doByteNativeWrapper(PrimitiveNativeWrapper object) {
            return object.getByte();
        }

        @Specialization(guards = {"object.isInt()", "!isNative(object)"})
        int doIntNativeWrapper(PrimitiveNativeWrapper object) {
            return object.getInt();
        }

        @Specialization(guards = {"object.isLong()", "!isNative(object)"})
        long doLongNativeWrapper(PrimitiveNativeWrapper object) {
            return object.getLong();
        }

        @Specialization(guards = {"object.isDouble()", "!isNative(object)"})
        double doDoubleNativeWrapper(PrimitiveNativeWrapper object) {
            return object.getDouble();
        }

        @Specialization(guards = {"!object.isBool()", "isNative(object)"})
        Object doPrimitiveNativeWrapper(PrimitiveNativeWrapper object) {
            return getMaterializeNode().execute(object);
        }

        @Specialization(guards = "!isPrimitiveNativeWrapper(object)")
        Object doNativeWrapper(PythonNativeWrapper object) {
            return object.getDelegate();
        }

        @Specialization(guards = {"isForeignObject(object, getClassNode, isForeignClassProfile)", "!isNativeWrapper(object)", "!isNativeNull(object)"}, limit = "1")
        PythonAbstractObject doNativeObject(TruffleObject object,
                        @SuppressWarnings("unused") @Cached("create()") GetLazyClassNode getClassNode,
                        @SuppressWarnings("unused") @Cached("create()") IsBuiltinClassProfile isForeignClassProfile) {
            return factory().createNativeObjectWrapper(object);
        }

        @Specialization
        PythonNativeNull doNativeNull(@SuppressWarnings("unused") PythonNativeNull object) {
            return object;
        }

        @Specialization
        PythonAbstractObject doPythonObject(PythonAbstractObject object) {
            return object;
        }

        @Specialization
        String doString(String object) {
            return object;
        }

        @Specialization
        boolean doBoolean(boolean b) {
            return b;
        }

        @Specialization
        byte doLong(byte b) {
            return b;
        }

        @Specialization
        int doLong(int i) {
            return i;
        }

        @Specialization
        long doLong(long l) {
            return l;
        }

        @Specialization
        double doDouble(double d) {
            return d;
        }

        @Fallback
        Object run(Object obj) {
            throw raise(PythonErrorType.SystemError, "invalid object from native: %s", obj);
        }

        protected boolean isNative(PythonNativeWrapper object) {
            if (isPointerNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                isPointerNode = insert(IsPointerNode.create());
            }
            return isPointerNode.execute(object);
        }

        protected static boolean isPrimitiveNativeWrapper(PythonNativeWrapper object) {
            return object instanceof PrimitiveNativeWrapper;
        }

        protected boolean isForeignObject(TruffleObject obj, GetLazyClassNode getClassNode, IsBuiltinClassProfile isForeignClassProfile) {
            return isForeignClassProfile.profileClass(getClassNode.execute(obj), PythonBuiltinClassType.TruffleObject);
        }

        @TruffleBoundary
        public static Object doSlowPath(Object object) {
            if (object instanceof PythonNativeWrapper) {
                return ((PythonNativeWrapper) object).getDelegate();
            } else if (IsBuiltinClassProfile.profileClassSlowPath(GetClassNode.getItSlowPath(object), PythonBuiltinClassType.TruffleObject)) {
                throw new AssertionError("Unsupported slow path operation: converting 'to_java(" + object + ")");
            }
            return object;
        }

        private MaterializeDelegateNode getMaterializeNode() {
            if (materializeNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                materializeNode = insert(MaterializeDelegateNode.create());
            }
            return materializeNode;
        }

        public static AsPythonObjectNode create() {
            return AsPythonObjectNodeGen.create();
        }
    }

    /**
     * Materializes a primitive value of a primitive native wrapper to ensure pointer equality.
     */
    public abstract static class MaterializeDelegateNode extends CExtBaseNode {

        public abstract Object execute(PythonNativeWrapper object);

        @Specialization(guards = {"!isMaterialized(object)", "object.isBool()"})
        PInt doBoolNativeWrapper(PrimitiveNativeWrapper object) {
            PInt materializedInt = factory().createInt(object.getBool());
            object.setMaterializedObject(materializedInt);
            if (materializedInt.getNativeWrapper() != null) {
                object.setNativePointer(materializedInt.getNativeWrapper().getNativePointer());
            } else {
                materializedInt.setNativeWrapper(object);
            }
            return materializedInt;
        }

        @Specialization(guards = {"!isMaterialized(object)", "object.isByte()"})
        PInt doByteNativeWrapper(PrimitiveNativeWrapper object) {
            PInt materializedInt = factory().createInt(object.getByte());
            object.setMaterializedObject(materializedInt);
            materializedInt.setNativeWrapper(object);
            return materializedInt;
        }

        @Specialization(guards = {"!isMaterialized(object)", "object.isInt()"})
        PInt doIntNativeWrapper(PrimitiveNativeWrapper object) {
            PInt materializedInt = factory().createInt(object.getInt());
            object.setMaterializedObject(materializedInt);
            materializedInt.setNativeWrapper(object);
            return materializedInt;
        }

        @Specialization(guards = {"!isMaterialized(object)", "object.isLong()"})
        PInt doLongNativeWrapper(PrimitiveNativeWrapper object) {
            PInt materializedInt = factory().createInt(object.getLong());
            object.setMaterializedObject(materializedInt);
            materializedInt.setNativeWrapper(object);
            return materializedInt;
        }

        @Specialization(guards = {"!isMaterialized(object)", "object.isDouble()"})
        PFloat doDoubleNativeWrapper(PrimitiveNativeWrapper object) {
            PFloat materializedInt = factory().createFloat(object.getDouble());
            object.setMaterializedObject(materializedInt);
            materializedInt.setNativeWrapper(object);
            return materializedInt;
        }

        @Specialization(guards = {"object.getClass() == cachedClass", "isMaterialized(object)"})
        Object doMaterialized(PrimitiveNativeWrapper object,
                        @SuppressWarnings("unused") @Cached("object.getClass()") Class<? extends PrimitiveNativeWrapper> cachedClass) {
            return CompilerDirectives.castExact(object, cachedClass).getDelegate();
        }

        @Specialization(guards = {"!isPrimitiveNativeWrapper(object)", "object.getClass() == cachedClass"}, limit = "3")
        Object doNativeWrapper(PythonNativeWrapper object,
                        @SuppressWarnings("unused") @Cached("object.getClass()") Class<? extends PythonNativeWrapper> cachedClass) {
            return CompilerDirectives.castExact(object, cachedClass).getDelegate();
        }

        @Specialization(guards = "!isPrimitiveNativeWrapper(object)", replaces = "doNativeWrapper")
        Object doNativeWrapperGeneric(PythonNativeWrapper object) {
            return object.getDelegate();
        }

        protected static boolean isPrimitiveNativeWrapper(PythonNativeWrapper object) {
            return object instanceof PrimitiveNativeWrapper;
        }

        public static MaterializeDelegateNode create() {
            return MaterializeDelegateNodeGen.create();
        }
    }

    public abstract static class GetNativeWrapper extends CExtBaseNode {

        public abstract PythonNativeWrapper execute(Object obj);

    }

    /**
     * Does the same conversion as the native function {@code to_java}. The node tries to avoid
     * calling the native function for resolving native handles.
     */
    public abstract static class ToJavaNode extends CExtBaseNode {
        @Child private PCallNativeNode callNativeNode;
        @Child private AsPythonObjectNode toJavaNode = AsPythonObjectNode.create();

        @CompilationFinal private TruffleObject nativeToJavaFunction;
        @CompilationFinal private TruffleObject nativePointerToJavaFunction;

        public abstract Object execute(Object value);

        public abstract boolean executeBool(boolean value);

        public abstract byte executeByte(byte value);

        public abstract int executeInt(int value);

        public abstract long executeLong(long value);

        public abstract double executeDouble(double value);

        @Specialization
        PythonAbstractObject doPythonObject(PythonAbstractObject value) {
            return value;
        }

        @Specialization
        Object doWrapper(PythonNativeWrapper value) {
            return toJavaNode.execute(value);
        }

        @Specialization
        String doString(String object) {
            return object;
        }

        @Specialization
        boolean doBoolean(boolean b) {
            return b;
        }

        @Specialization
        Object doInt(int i) {
            // Unfortunately, an int could be a native pointer and therefore a handle. So, we must
            // try resolving it. At least we know that it's not a native type.
            return native_pointer_to_java(i);
        }

        @Specialization
        Object doLong(long l) {
            // Unfortunately, a long could be a native pointer and therefore a handle. So, we must
            // try resolving it. At least we know that it's not a native type.
            return native_pointer_to_java(l);
        }

        @Specialization
        byte doLong(byte b) {
            return b;
        }

        @Fallback
        Object doForeign(Object value) {
            if (nativeToJavaFunction == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                nativeToJavaFunction = importCAPISymbol(NativeCAPISymbols.FUN_NATIVE_TO_JAVA);
            }
            return call_native_conversion(nativeToJavaFunction, value);
        }

        private Object native_pointer_to_java(Object value) {
            if (nativePointerToJavaFunction == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                nativePointerToJavaFunction = importCAPISymbol(NativeCAPISymbols.FUN_NATIVE_POINTER_TO_JAVA);
            }
            return call_native_conversion(nativePointerToJavaFunction, value);
        }

        private Object call_native_conversion(TruffleObject target, Object value) {
            if (callNativeNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                callNativeNode = insert(PCallNativeNode.create());
            }
            return toJavaNode.execute(callNativeNode.execute(target, new Object[]{value}));
        }

        public static ToJavaNode create() {
            return ToJavaNodeGen.create();
        }
    }

    public abstract static class AsCharPointer extends CExtBaseNode {

        @CompilationFinal TruffleObject truffle_string_to_cstr;
        @CompilationFinal TruffleObject truffle_byte_array_to_native;

        public abstract Object execute(Object obj);

        @Specialization
        Object doPString(PString str,
                        @Cached("createExecute()") Node executeNode) {
            return doString(str.getValue(), executeNode);
        }

        @Specialization
        Object doString(String str,
                        @Cached("createExecute()") Node executeNode) {
            try {
                return ForeignAccess.sendExecute(executeNode, getTruffleStringToCstr(), str, str.length());
            } catch (UnsupportedTypeException | ArityException | UnsupportedMessageException e) {
                throw e.raise();
            }
        }

        @Specialization
        Object doByteArray(byte[] arr,
                        @Cached("createExecute()") Node executeNode) {
            try {
                return ForeignAccess.sendExecute(executeNode, getTruffleByteArrayToNative(), getContext().getEnv().asGuestValue(arr), arr.length);
            } catch (UnsupportedTypeException | ArityException | UnsupportedMessageException e) {
                throw e.raise();
            }
        }

        TruffleObject getTruffleStringToCstr() {
            if (truffle_string_to_cstr == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                truffle_string_to_cstr = importCAPISymbol(NativeCAPISymbols.FUN_PY_TRUFFLE_STRING_TO_CSTR);
            }
            return truffle_string_to_cstr;
        }

        TruffleObject getTruffleByteArrayToNative() {
            if (truffle_byte_array_to_native == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                truffle_byte_array_to_native = importCAPISymbol(NativeCAPISymbols.FUN_PY_TRUFFLE_BYTE_ARRAY_TO_NATIVE);
            }
            return truffle_byte_array_to_native;
        }

        protected Node createExecute() {
            return Message.EXECUTE.createNode();
        }

        public static AsCharPointer create() {
            return AsCharPointerNodeGen.create();
        }
    }

    public static class FromCharPointerNode extends CExtBaseNode {

        @CompilationFinal TruffleObject truffle_cstr_to_string;
        @Child private Node executeNode;

        TruffleObject getTruffleStringToCstr() {
            if (truffle_cstr_to_string == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                truffle_cstr_to_string = importCAPISymbol(NativeCAPISymbols.FUN_PY_TRUFFLE_CSTR_TO_STRING);
            }
            return truffle_cstr_to_string;
        }

        private Node getExecuteNode() {
            if (executeNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                executeNode = insert(Message.EXECUTE.createNode());
            }
            return executeNode;
        }

        public String execute(Object charPtr) {
            try {
                return (String) ForeignAccess.sendExecute(getExecuteNode(), getTruffleStringToCstr(), charPtr);
            } catch (UnsupportedTypeException | ArityException | UnsupportedMessageException e) {
                throw e.raise();
            }
        }

        public static FromCharPointerNode create() {
            return new FromCharPointerNode();
        }
    }

    public abstract static class GetNativeClassNode extends CExtBaseNode {

        @Child PCallNativeNode callGetObTypeNode;
        @Child ToJavaNode toJavaNode;

        @CompilationFinal private TruffleObject func;

        public abstract PythonClass execute(PythonNativeObject object);

        @Specialization(guards = "object == cachedObject", limit = "1")
        PythonClass getNativeClassCached(@SuppressWarnings("unused") PythonNativeObject object,
                        @SuppressWarnings("unused") @Cached("object") PythonNativeObject cachedObject,
                        @Cached("getNativeClass(cachedObject)") PythonClass cachedClass) {
            // TODO: (tfel) is this really something we can do? It's so rare for this class to
            // change that it shouldn't be worth the effort, but in native code, anything can
            // happen. OTOH, CPython also has caches that can become invalid when someone just goes
            // and changes the ob_type of an object.
            return cachedClass;
        }

        @Specialization
        PythonClass getNativeClass(PythonNativeObject object) {
            // do not convert wrap 'object.object' since that is really the native pointer object
            Object[] args = new Object[]{object.object};
            return (PythonClass) getToJavaNode().execute(getCallGetObTypeNode().execute(getObTypeFunction(), args));
        }

        private ToJavaNode getToJavaNode() {
            if (toJavaNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                toJavaNode = insert(ToJavaNode.create());
            }
            return toJavaNode;
        }

        private PCallNativeNode getCallGetObTypeNode() {
            if (callGetObTypeNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                callGetObTypeNode = insert(PCallNativeNode.create());
            }
            return callGetObTypeNode;
        }

        TruffleObject getObTypeFunction() {
            if (func == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                func = importCAPISymbol(NativeCAPISymbols.FUN_GET_OB_TYPE);
            }
            return func;
        }

        public static GetNativeClassNode create() {
            return GetNativeClassNodeGen.create();
        }
    }

    public static class SizeofWCharNode extends CExtBaseNode {

        @CompilationFinal long wcharSize = -1;

        public long execute() {
            if (wcharSize < 0) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                try {
                    wcharSize = (long) ForeignAccess.sendExecute(Message.EXECUTE.createNode(), importCAPISymbol(NativeCAPISymbols.FUN_WHCAR_SIZE));
                    assert wcharSize >= 0L;
                } catch (InteropException e) {
                    throw e.raise();
                }
            }
            return wcharSize;
        }

        public static SizeofWCharNode create() {
            return new SizeofWCharNode();
        }
    }

    public static class IsNode extends CExtBaseNode {
        @CompilationFinal private TruffleObject isFunc = null;
        @Child Node executeNode = Message.EXECUTE.createNode();

        public boolean execute(PythonNativeObject a, PythonNativeObject b) {
            try {
                return (int) ForeignAccess.sendExecute(executeNode, getNativeFunction(), a.object, b.object) != 0;
            } catch (UnsupportedTypeException | ArityException | UnsupportedMessageException e) {
                CompilerDirectives.transferToInterpreter();
                throw new IllegalStateException(NativeCAPISymbols.FUN_PTR_COMPARE + " didn't work!");
            }
        }

        private TruffleObject getNativeFunction() {
            if (isFunc == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                isFunc = importCAPISymbol(NativeCAPISymbols.FUN_PTR_COMPARE);
            }
            return isFunc;
        }

    }

    public abstract static class AllToJavaNode extends PNodeWithContext {
        @Child private CExtNodes.AsPythonObjectNode toJavaNode = CExtNodes.AsPythonObjectNode.create();

        abstract Object[] execute(Object[] args);

        @Specialization(guards = {"args.length == cachedLength", "cachedLength < 5"}, limit = "5")
        @ExplodeLoop
        Object[] cached(Object[] args,
                        @Cached("args.length") int cachedLength) {
            Object[] output = new Object[cachedLength];
            for (int i = 0; i < cachedLength; i++) {
                output[i] = toJavaNode.execute(args[i]);
            }
            return output;
        }

        @Specialization(replaces = "cached")
        Object[] uncached(Object[] args) {
            int len = args.length;
            Object[] output = new Object[len];
            for (int i = 0; i < len; i++) {
                output[i] = toJavaNode.execute(args[i]);
            }
            return output;
        }

        public static AllToJavaNode create() {
            return AllToJavaNodeGen.create();
        }
    }

    public abstract static class AllToSulongNode extends PNodeWithContext {
        public abstract void executeInto(Object[] args, int argsOffset, Object[] dest, int destOffset);

        protected boolean isArgsOffsetPlus(int len, int off, int plus) {
            return len == off + plus;
        }

        protected boolean isLeArgsOffsetPlus(int len, int off, int plus) {
            return len < plus + off;
        }

        @SuppressWarnings("unused")
        @Specialization(guards = {"args.length == argsOffset"})
        void cached0(Object[] args, int argsOffset, Object[] dest, int destOffset) {
        }

        @Specialization(guards = {"isArgsOffsetPlus(args.length, argsOffset, 1)"})
        void cached1(Object[] args, int argsOffset, Object[] dest, int destOffset,
                        @Cached("create()") ToSulongNode toSulongNode1) {
            dest[destOffset + 0] = toSulongNode1.execute(args[argsOffset + 0]);
        }

        @Specialization(guards = {"isArgsOffsetPlus(args.length, argsOffset, 2)"})
        void cached2(Object[] args, int argsOffset, Object[] dest, int destOffset,
                        @Cached("create()") ToSulongNode toSulongNode1,
                        @Cached("create()") ToSulongNode toSulongNode2) {
            dest[destOffset + 0] = toSulongNode1.execute(args[argsOffset + 0]);
            dest[destOffset + 1] = toSulongNode2.execute(args[argsOffset + 1]);
        }

        @Specialization(guards = {"isArgsOffsetPlus(args.length, argsOffset, 3)"})
        void cached3(Object[] args, int argsOffset, Object[] dest, int destOffset,
                        @Cached("create()") ToSulongNode toSulongNode1,
                        @Cached("create()") ToSulongNode toSulongNode2,
                        @Cached("create()") ToSulongNode toSulongNode3) {
            dest[destOffset + 0] = toSulongNode1.execute(args[argsOffset + 0]);
            dest[destOffset + 1] = toSulongNode2.execute(args[argsOffset + 1]);
            dest[destOffset + 2] = toSulongNode3.execute(args[argsOffset + 2]);
        }

        @Specialization(guards = {"args.length == cachedLength", "isLeArgsOffsetPlus(cachedLength, argsOffset, 8)"}, limit = "1", replaces = {"cached0", "cached1", "cached2", "cached3"})
        @ExplodeLoop
        void cachedLoop(Object[] args, int argsOffset, Object[] dest, int destOffset,
                        @Cached("args.length") int cachedLength,
                        @Cached("create()") ToSulongNode toSulongNode) {
            for (int i = 0; i < cachedLength - argsOffset; i++) {
                dest[destOffset + i] = toSulongNode.execute(args[argsOffset + i]);
            }
        }

        @Specialization(replaces = {"cached0", "cached1", "cached2", "cached3", "cachedLoop"})
        void uncached(Object[] args, int argsOffset, Object[] dest, int destOffset,
                        @Cached("create()") ToSulongNode toSulongNode) {
            int len = args.length;
            for (int i = 0; i < len - argsOffset; i++) {
                dest[destOffset + i] = toSulongNode.execute(args[argsOffset + i]);
            }
        }

        public static AllToSulongNode create() {
            return AllToSulongNodeGen.create();
        }
    }

    public abstract static class DirectUpcallNode extends PNodeWithContext {
        @Child private AllToJavaNode allToJava;

        protected AllToJavaNode getAllToJavaNode() {
            if (allToJava == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                allToJava = insert(AllToJavaNode.create());
            }
            return allToJava;
        }

        public abstract Object execute(VirtualFrame frame, Object callable, Object[] args);

        @Specialization(guards = "args.length == 0")
        Object upcall0(VirtualFrame frame, Object callable, @SuppressWarnings("unused") Object[] args,
                        @Cached("create()") CallNode callNode) {
            return callNode.execute(frame, callable, new Object[0], new PKeyword[0]);
        }

        @Specialization(guards = "args.length == 1")
        Object upcall1(Object callable, Object[] args,
                        @Cached("create()") CallUnaryMethodNode callNode,
                        @Cached("create()") CExtNodes.AsPythonObjectNode toJavaNode) {
            return callNode.executeObject(callable, toJavaNode.execute(args[0]));
        }

        @Specialization(guards = "args.length == 2")
        Object upcall2(Object callable, Object[] args,
                        @Cached("create()") CallBinaryMethodNode callNode) {
            Object[] converted = getAllToJavaNode().execute(args);
            return callNode.executeObject(callable, converted[0], converted[1]);
        }

        @Specialization(guards = "args.length == 3")
        Object upcall3(Object callable, Object[] args,
                        @Cached("create()") CallTernaryMethodNode callNode) {
            Object[] converted = getAllToJavaNode().execute(args);
            return callNode.execute(callable, converted[0], converted[1], converted[2]);
        }

        @Specialization(replaces = {"upcall0", "upcall1", "upcall2", "upcall3"})
        Object upcall(VirtualFrame frame, Object callable, Object[] args,
                        @Cached("create()") CallNode callNode) {
            Object[] converted = getAllToJavaNode().execute(args);
            return callNode.execute(frame, callable, converted, new PKeyword[0]);
        }

        public static DirectUpcallNode create() {
            return DirectUpcallNodeGen.create();
        }
    }

    protected abstract static class UpcallNode extends PNodeWithContext {
        @Child private AllToJavaNode allToJava;

        protected AllToJavaNode getAllToJavaNode() {
            if (allToJava == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                allToJava = insert(AllToJavaNode.create());
            }
            return allToJava;
        }

        protected Object getAttr(@SuppressWarnings("unused") Object object, @SuppressWarnings("unused") String name) {
            throw new IllegalStateException();
        }

        public abstract Object execute(VirtualFrame frame, Object cextModule, String name, Object[] args);

        @Specialization(guards = "args.length == 0")
        Object upcall0(VirtualFrame frame, Object cextModule, String name, @SuppressWarnings("unused") Object[] args,
                        @Cached("create()") CallNode callNode) {
            Object callable = getAttr(cextModule, name);
            return callNode.execute(frame, callable, new Object[0], new PKeyword[0]);
        }

        @Specialization(guards = "args.length == 1")
        Object upcall1(Object cextModule, String name, Object[] args,
                        @Cached("create()") CallUnaryMethodNode callNode,
                        @Cached("create()") CExtNodes.AsPythonObjectNode toJavaNode) {
            Object callable = getAttr(cextModule, name);
            return callNode.executeObject(callable, toJavaNode.execute(args[0]));
        }

        @Specialization(guards = "args.length == 2")
        Object upcall2(Object cextModule, String name, Object[] args,
                        @Cached("create()") CallBinaryMethodNode callNode) {
            Object[] converted = getAllToJavaNode().execute(args);
            Object callable = getAttr(cextModule, name);
            return callNode.executeObject(callable, converted[0], converted[1]);
        }

        @Specialization(guards = "args.length == 3")
        Object upcall3(Object cextModule, String name, Object[] args,
                        @Cached("create()") CallTernaryMethodNode callNode) {
            Object[] converted = getAllToJavaNode().execute(args);
            Object callable = getAttr(cextModule, name);
            return callNode.execute(callable, converted[0], converted[1], converted[2]);
        }

        @Specialization(replaces = {"upcall0", "upcall1", "upcall2", "upcall3"})
        Object upcall(VirtualFrame frame, Object cextModule, String name, Object[] args,
                        @Cached("create()") CallNode callNode) {
            Object[] converted = getAllToJavaNode().execute(args);
            Object callable = getAttr(cextModule, name);
            return callNode.execute(frame, callable, converted, new PKeyword[0]);
        }
    }

    public abstract static class CextUpcallNode extends UpcallNode {
        @Child ReadAttributeFromObjectNode getAttrNode = ReadAttributeFromObjectNode.create();

        public static CextUpcallNode create() {
            return CextUpcallNodeGen.create();
        }

        @Override
        protected final Object getAttr(Object object, String name) {
            return getAttrNode.execute(object, name);
        }
    }

    public abstract static class ObjectUpcallNode extends UpcallNode {
        @Child GetAttrNode getAttrNode = GetAttrNode.create();

        public static ObjectUpcallNode create() {
            return ObjectUpcallNodeGen.create();
        }

        @Override
        protected final Object getAttr(Object object, String name) {
            return getAttrNode.executeWithArgs(object, name, PNone.NO_VALUE);
        }
    }

    public abstract static class AsDouble extends PNodeWithContext {
        @Child private LookupAndCallUnaryNode callFloatFunc;

        public abstract double execute(boolean arg);

        public abstract double execute(int arg);

        public abstract double execute(long arg);

        public abstract double execute(double arg);

        public abstract double execute(Object arg);

        public static AsDouble create() {
            return AsDoubleNodeGen.create();
        }

        @Specialization
        double run(boolean value) {
            return value ? 1.0 : 0.0;
        }

        @Specialization
        double run(int value) {
            return value;
        }

        @Specialization
        double run(long value) {
            return value;
        }

        @Specialization
        double run(double value) {
            return value;
        }

        @Specialization
        double run(PInt value) {
            return value.doubleValue();
        }

        @Specialization
        double run(PFloat value) {
            return value.getValue();
        }

        @Specialization(guards = "!object.isDouble()")
        double doLongNativeWrapper(PrimitiveNativeWrapper object) {
            return object.getLong();
        }

        @Specialization(guards = "object.isDouble()")
        double doDoubleNativeWrapper(PrimitiveNativeWrapper object) {
            return object.getDouble();
        }

        // TODO: this should just use the builtin constructor node so we don't duplicate the corner
        // cases
        @Fallback
        double runGeneric(Object value) {
            if (callFloatFunc == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                callFloatFunc = insert(LookupAndCallUnaryNode.create(SpecialMethodNames.__FLOAT__));
            }
            Object result = callFloatFunc.executeObject(value);
            if (PGuards.isPFloat(result)) {
                return ((PFloat) result).getValue();
            } else if (result instanceof Double) {
                return (double) result;
            } else {
                throw raise(PythonErrorType.TypeError, "%p.%s returned non-float (type %p)", value, SpecialMethodNames.__FLOAT__, result);
            }
        }
    }

    public abstract static class AsLong extends PNodeWithContext {
        @Child private CastToIndexNode intNode;

        public abstract long execute(boolean arg);

        public abstract long execute(int arg);

        public abstract long execute(long arg);

        public abstract long execute(double arg);

        public abstract long execute(Object arg);

        @Specialization
        long run(boolean value) {
            return value ? 1 : 0;
        }

        @Specialization
        long run(int value) {
            return value;
        }

        @Specialization
        long run(long value) {
            return value;
        }

        @Specialization
        long run(double value) {
            return (long) value;
        }

        @Specialization
        long run(PInt value) {
            // TODO(fa) longValueExact ?
            return value.longValue();
        }

        @Specialization
        long run(PFloat value) {
            return (long) value.getValue();
        }

        @Specialization(guards = "!object.isDouble()")
        long doLongNativeWrapper(PrimitiveNativeWrapper object) {
            return object.getLong();
        }

        @Specialization(guards = "object.isDouble()")
        long doDoubleNativeWrapper(PrimitiveNativeWrapper object) {
            return (long) object.getDouble();
        }

        @Specialization
        long run(PythonNativeWrapper value,
                        @Cached("create()") AsLong recursive) {
            // TODO(fa) this specialization should eventually go away
            return recursive.execute(value.getDelegate());
        }

        @Fallback
        long runGeneric(Object value) {
            return getIntNode().execute(value);
        }

        private CastToIndexNode getIntNode() {
            if (intNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                intNode = insert(CastToIndexNode.createOverflow());
            }
            return intNode;
        }

        public static AsLong create() {
            return AsLongNodeGen.create();
        }
    }

    public static class PCallBinaryCapiFunction extends CExtBaseNode {

        @Child private Node callNode;

        private final String name;
        private final BranchProfile profile = BranchProfile.create();

        @CompilationFinal TruffleObject receiver;

        public PCallBinaryCapiFunction(String name) {
            this.name = name;
        }

        public Object execute(Object arg0, Object arg1) {
            try {
                return ForeignAccess.sendExecute(getCallNode(), getFunction(), arg0, arg1);
            } catch (UnsupportedTypeException | ArityException | UnsupportedMessageException e) {
                profile.enter();
                throw e.raise();
            }
        }

        private Node getCallNode() {
            if (callNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                callNode = insert(Message.EXECUTE.createNode());
            }
            return callNode;
        }

        private TruffleObject getFunction() {
            if (receiver == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                receiver = importCAPISymbol(name);
            }
            return receiver;
        }

        public static PCallBinaryCapiFunction create(String name) {
            return new PCallBinaryCapiFunction(name);
        }
    }

    public static class MayRaiseNodeFactory<T extends PythonBuiltinBaseNode> implements NodeFactory<T> {
        private final T node;

        public MayRaiseNodeFactory(T node) {
            this.node = node;
        }

        public T createNode(Object... arguments) {
            return NodeUtil.cloneNode(node);
        }

        @SuppressWarnings("unchecked")
        public Class<T> getNodeClass() {
            return (Class<T>) node.getClass();
        }

        public List<List<Class<?>>> getNodeSignatures() {
            throw new IllegalAccessError();
        }

        public List<Class<? extends Node>> getExecutionSignature() {
            throw new IllegalAccessError();
        }
    }

    @Builtin(fixedNumOfPositionalArgs = 1)
    public static abstract class MayRaiseUnaryNode extends PythonUnaryBuiltinNode {
        @Child private CreateArgumentsNode createArgsNode;
        @Child private InvokeNode invokeNode;
        private final Object errorResult;

        public MayRaiseUnaryNode(PFunction func, Object errorResult) {
            this.createArgsNode = CreateArgumentsNode.create();
            this.invokeNode = InvokeNode.create(func);
            this.errorResult = errorResult;
        }

        @Specialization
        Object doit(Object argument) {
            try {
                Object[] arguments = createArgsNode.execute(argument);
                return invokeNode.execute(null, arguments, new PKeyword[0]);
            } catch (PException e) {
                // getContext() acts as a branch profile
                getContext().setCurrentException(e);
                return errorResult;
            }
        }
    }

    @Builtin(fixedNumOfPositionalArgs = 2)
    public static abstract class MayRaiseBinaryNode extends PythonBinaryBuiltinNode {
        @Child private CreateArgumentsNode createArgsNode;
        @Child private InvokeNode invokeNode;
        private final Object errorResult;

        public MayRaiseBinaryNode(PFunction func, Object errorResult) {
            this.createArgsNode = CreateArgumentsNode.create();
            this.invokeNode = InvokeNode.create(func);
            this.errorResult = errorResult;
        }

        @Specialization
        Object doit(Object arg1, Object arg2) {
            try {
                Object[] arguments = createArgsNode.execute(arg1, arg2);
                return invokeNode.execute(null, arguments, new PKeyword[0]);
            } catch (PException e) {
                // getContext() acts as a branch profile
                getContext().setCurrentException(e);
                return errorResult;
            }
        }
    }

    @Builtin(fixedNumOfPositionalArgs = 3)
    public static abstract class MayRaiseTernaryNode extends PythonTernaryBuiltinNode {
        @Child private CreateArgumentsNode createArgsNode;
        @Child private InvokeNode invokeNode;
        private final Object errorResult;

        public MayRaiseTernaryNode(PFunction func, Object errorResult) {
            this.createArgsNode = CreateArgumentsNode.create();
            this.invokeNode = InvokeNode.create(func);
            this.errorResult = errorResult;
        }

        @Specialization
        Object doit(Object arg1, Object arg2, Object arg3) {
            try {
                Object[] arguments = createArgsNode.execute(arg1, arg2, arg3);
                return invokeNode.execute(null, arguments, new PKeyword[0]);
            } catch (PException e) {
                // getContext() acts as a branch profile
                getContext().setCurrentException(e);
                return errorResult;
            }
        }
    }

    @Builtin(takesVarArgs = true)
    public static class MayRaiseNode extends PythonBuiltinNode {
        @Child private InvokeNode invokeNode;
        @Child private ReadVarArgsNode readVarargsNode;
        @Child private CreateArgumentsNode createArgsNode;
        @Child private PythonObjectFactory factory;
        private final Object errorResult;

        public MayRaiseNode(PythonCallable callable, Object errorResult) {
            this.readVarargsNode = ReadVarArgsNode.create(0, true);
            this.createArgsNode = CreateArgumentsNode.create();
            this.invokeNode = InvokeNode.create(callable);
            this.errorResult = errorResult;
        }

        @Override
        public final Object execute(VirtualFrame frame) {
            Object[] args = readVarargsNode.executeObjectArray(frame);
            try {
                Object[] arguments = createArgsNode.execute(args);
                return invokeNode.execute(null, arguments, new PKeyword[0]);
            } catch (PException e) {
                // getContext() acts as a branch profile
                getContext().setCurrentException(e);
                return errorResult;
            }
        }

        @Override
        protected ReadArgumentNode[] getArguments() {
            throw new IllegalAccessError();
        }
    }

    public abstract static class IsPointerNode extends PNodeWithContext {

        public abstract boolean execute(PythonNativeWrapper obj);

        @Specialization(assumptions = {"singleContextAssumption()", "nativeObjectsAllManagedAssumption()"})
        boolean doFalse(@SuppressWarnings("unused") PythonNativeWrapper obj) {
            return false;
        }

        @Specialization
        boolean doGeneric(PythonNativeWrapper obj) {
            return obj.isNative();
        }

        protected Assumption nativeObjectsAllManagedAssumption() {
            return getContext().getNativeObjectsAllManagedAssumption();
        }

        public static IsPointerNode create() {
            return IsPointerNodeGen.create();
        }
    }
}
