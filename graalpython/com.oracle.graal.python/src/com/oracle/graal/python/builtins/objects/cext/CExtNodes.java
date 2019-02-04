/*
 * Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.graal.python.PythonLanguage;
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
import com.oracle.graal.python.builtins.objects.cext.CExtNodesFactory.GetTypeMemberNodeGen;
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
import com.oracle.graal.python.builtins.objects.ints.PInt;
import com.oracle.graal.python.builtins.objects.str.PString;
import com.oracle.graal.python.builtins.objects.type.PythonAbstractClass;
import com.oracle.graal.python.builtins.objects.type.PythonManagedClass;
import com.oracle.graal.python.builtins.objects.type.PythonBuiltinClass;
import com.oracle.graal.python.builtins.objects.type.TypeNodes;
import com.oracle.graal.python.builtins.objects.type.TypeNodes.GetNameNode;
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
import com.oracle.graal.python.nodes.truffle.PythonTypes;
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
import com.oracle.truffle.api.dsl.TypeSystemReference;
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

        protected static TruffleObject importCAPISymbolSlowPath(String name) {
            TruffleObject capiLibrary = (TruffleObject) PythonLanguage.getContextRef().get().getCapiLibrary();
            try {
                return (TruffleObject) ForeignAccess.sendRead(Message.READ.createNode(), capiLibrary, name);
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
        Object doNativeClass(PythonAbstractNativeObject nativeClass) {
            return nativeClass.getPtr();
        }

        @Specialization
        Object doNativeNull(PythonNativeNull object) {
            return object.getPtr();
        }

        @Specialization(guards = "object == cachedObject", limit = "3")
        Object doPythonClass(@SuppressWarnings("unused") PythonManagedClass object,
                        @SuppressWarnings("unused") @Cached("object") PythonManagedClass cachedObject,
                        @Cached("wrapNativeClass(object)") PythonClassNativeWrapper wrapper) {
            return wrapper;
        }

        @Specialization(replaces = "doPythonClass")
        Object doPythonClassUncached(PythonManagedClass object,
                        @Cached("create()") TypeNodes.GetNameNode getNameNode) {
            return PythonClassNativeWrapper.wrap(object, getNameNode.execute(object));
        }

        @Specialization(guards = {"cachedClass == object.getClass()", "!isClass(object)", "!isNativeObject(object)", "!isNoValue(object)"}, limit = "3")
        Object runAbstractObjectCached(PythonAbstractObject object,
                        @Cached("createBinaryProfile()") ConditionProfile noWrapperProfile,
                        @Cached("object.getClass()") Class<? extends PythonAbstractObject> cachedClass) {
            assert object != PNone.NO_VALUE;
            return PythonObjectNativeWrapper.wrap(CompilerDirectives.castExact(object, cachedClass), noWrapperProfile);
        }

        @Specialization(guards = {"!isClass(object)", "!isNativeObject(object)", "!isNoValue(object)"}, replaces = "runAbstractObjectCached")
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

        protected static PythonClassNativeWrapper wrapNativeClass(PythonManagedClass object) {
            return PythonClassNativeWrapper.wrap(object, GetNameNode.doSlowPath(object));
        }

        public static ToSulongNode create() {
            return ToSulongNodeGen.create();
        }

        @TruffleBoundary
        public static Object doSlowPath(Object o) {
            if (o instanceof String) {
                PythonObjectNativeWrapper.wrapSlowPath(PythonLanguage.getCore().factory().createString((String) o));
            } else if (o instanceof Integer) {
                return PrimitiveNativeWrapper.createInt((Integer) o);
            } else if (o instanceof Long) {
                return PrimitiveNativeWrapper.createLong((Long) o);
            } else if (o instanceof Double) {
                return PrimitiveNativeWrapper.createDouble((Double) o);
            } else if (PythonNativeClass.isInstance(o)) {
                return ((PythonNativeClass) o).getPtr();
            } else if (PythonNativeObject.isInstance(o)) {
                return PythonNativeObject.cast(o).getPtr();
            } else if (o instanceof PythonNativeNull) {
                return ((PythonNativeNull) o).getPtr();
            } else if (o instanceof PythonManagedClass) {
                return wrapNativeClass((PythonManagedClass) o);
            } else if (o instanceof PythonAbstractObject) {
                assert !PGuards.isClass(o);
                return PythonObjectNativeWrapper.wrapSlowPath((PythonAbstractObject) o);
            } else if (PGuards.isForeignObject(o)) {
                return TruffleObjectNativeWrapper.wrap((TruffleObject) o);
            }
            assert o != null : "Java 'null' cannot be a Sulong value";
            return o;
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
        Object doNativeObject(TruffleObject object,
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
        public static Object doSlowPath(Object object, boolean forceNativeClass) {
            if (object instanceof PythonNativeWrapper) {
                return ((PythonNativeWrapper) object).getDelegate();
            } else if (IsBuiltinClassProfile.profileClassSlowPath(GetClassNode.getItSlowPath(object), PythonBuiltinClassType.TruffleObject)) {
                if (forceNativeClass) {
                    return PythonLanguage.getCore().factory().createNativeClassWrapper((TruffleObject) object);
                }
                return PythonLanguage.getCore().factory().createNativeObjectWrapper((TruffleObject) object);
            } else if (object instanceof String || object instanceof Number || object instanceof Boolean || object instanceof PythonNativeNull || object instanceof PythonAbstractObject) {
                return object;
            }
            throw PythonLanguage.getCore().raise(PythonErrorType.SystemError, "invalid object from native: %s", object);
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

        public static AsPythonObjectNode createForceClass() {
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

        /**
         * This should be set to {@code true} if the target of a value assignment is known to be a
         * {@code PyObject*}. For example, if native code assigns to
         * {@code ((PyTupleObject*)obj)->ob_item[i] = val} where {@code ob_item} is of type
         * {@code PyObject**}, then we know that {@code val} should be interpreted as pointer and
         * must point to a full Python object.
         */
        private final boolean forcePointer;

        protected ToJavaNode(boolean forcePointer) {
            this.forcePointer = forcePointer;
        }

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
        int doInt(int i) {
            // Note: Sulong guarantees that an integer won't be a pointer
            return i;
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
                String funName = forcePointer ? NativeCAPISymbols.FUN_NATIVE_LONG_TO_JAVA : NativeCAPISymbols.FUN_NATIVE_POINTER_TO_JAVA;
                nativePointerToJavaFunction = importCAPISymbol(funName);
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
            return ToJavaNodeGen.create(true);
        }

        public static ToJavaNode create(boolean forcePointer) {
            return ToJavaNodeGen.create(forcePointer);
        }

        @TruffleBoundary
        public static Object doSlowPath(Object value, boolean forcePointer) {
            if (value instanceof PythonAbstractObject || value instanceof String || value instanceof Boolean || value instanceof Integer || value instanceof Byte) {
                return value;
            } else if (value instanceof Long) {
                String funName = forcePointer ? NativeCAPISymbols.FUN_NATIVE_LONG_TO_JAVA : NativeCAPISymbols.FUN_NATIVE_POINTER_TO_JAVA;
                return AsPythonObjectNode.doSlowPath(PCallCapiFunction.doSlowPath(funName, value), false);
            } else if (value instanceof PythonNativeWrapper) {
                return AsPythonObjectNode.doSlowPath(value, false);
            }
            return AsPythonObjectNode.doSlowPath(PCallCapiFunction.doSlowPath(NativeCAPISymbols.FUN_NATIVE_TO_JAVA, value), false);
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

        @Child private PCallCapiFunction callGetObTypeNode;
        @Child private AsPythonObjectNode toJavaNode;

        @CompilationFinal private TruffleObject func;

        public abstract PythonAbstractClass execute(PythonAbstractNativeObject object);

        @Specialization(guards = "cachedObject.equals(object)", limit = "1")
        PythonAbstractClass getNativeClassCached(@SuppressWarnings("unused") PythonAbstractNativeObject object,
                        @SuppressWarnings("unused") @Cached("object") PythonAbstractNativeObject cachedObject,
                        @Cached("getNativeClass(cachedObject)") PythonAbstractClass cachedClass) {
            // TODO: (tfel) is this really something we can do? It's so rare for this class to
            // change that it shouldn't be worth the effort, but in native code, anything can
            // happen. OTOH, CPython also has caches that can become invalid when someone just goes
            // and changes the ob_type of an object.
            return cachedClass;
        }

        @Specialization
        PythonAbstractClass getNativeClass(PythonAbstractNativeObject object) {
            // do not convert wrap 'object.object' since that is really the native pointer object
            return (PythonAbstractClass) getToJavaNode().execute(getCallGetObTypeNode().call(object.object));
        }

        @TruffleBoundary
        public static PythonAbstractClass doSlowPath(PythonAbstractNativeObject object) {
            return (PythonAbstractClass) AsPythonObjectNode.doSlowPath(PCallCapiFunction.doSlowPath(NativeCAPISymbols.FUN_GET_OB_TYPE, object.getPtr()), true);
        }

        private AsPythonObjectNode getToJavaNode() {
            if (toJavaNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                toJavaNode = insert(AsPythonObjectNode.createForceClass());
            }
            return toJavaNode;
        }

        private PCallCapiFunction getCallGetObTypeNode() {
            if (callGetObTypeNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                callGetObTypeNode = insert(PCallCapiFunction.create(NativeCAPISymbols.FUN_GET_OB_TYPE));

            }
            return callGetObTypeNode;
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

    public static final class PointerCompareNode extends CExtBaseNode {
        private final int op;
        @CompilationFinal private TruffleObject isFunc = null;
        @Child Node executeNode = Message.EXECUTE.createNode();

        private PointerCompareNode(int op) {
            this.op = op;
        }

        private boolean executeCFunction(Object a, Object b) {
            try {
                return (int) ForeignAccess.sendExecute(executeNode, getNativeFunction(), a, b, op) != 0;
            } catch (UnsupportedTypeException | ArityException | UnsupportedMessageException e) {
                CompilerDirectives.transferToInterpreter();
                throw new IllegalStateException(NativeCAPISymbols.FUN_PTR_COMPARE + " didn't work!");
            }
        }

        public boolean execute(PythonAbstractNativeObject a, PythonAbstractNativeObject b) {
            return executeCFunction(a.object, b.object);
        }

        public boolean execute(PythonAbstractNativeObject a, long b) {
            return executeCFunction(a.object, b);
        }

        public boolean execute(PythonNativeVoidPtr a, long b) {
            return executeCFunction(a.object, b);
        }

        private TruffleObject getNativeFunction() {
            if (isFunc == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                isFunc = importCAPISymbol(NativeCAPISymbols.FUN_PTR_COMPARE);
            }
            return isFunc;
        }

        public static PointerCompareNode create(String specialMethodName) {
            for (int i = 0; i < SpecialMethodNames.COMPARE_OPNAMES.length; i++) {
                if (SpecialMethodNames.COMPARE_OPNAMES[i].equals(specialMethodName)) {
                    return new CExtNodes.PointerCompareNode(i);
                }
            }
            throw new RuntimeException("The special method used for Python C API pointer comparison must be a constant literal (i.e., interned) string");
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

        @Specialization(guards = "value.length() == 1")
        long run(String value) {
            return value.charAt(0);
        }

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
            // TODO(fa) should we use longValueExact ?
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

    public static class PCallCapiFunction extends CExtBaseNode {
        @Child private Node callNode;

        private final String name;
        private final BranchProfile profile = BranchProfile.create();

        @CompilationFinal TruffleObject receiver;

        public PCallCapiFunction(String name) {
            this.name = name;
        }

        @TruffleBoundary
        public static Object doSlowPath(String funNativeToJava, Object... args) {
            try {
                return ForeignAccess.sendExecute(Message.EXECUTE.createNode(), importCAPISymbolSlowPath(funNativeToJava), args);
            } catch (UnsupportedTypeException | ArityException | UnsupportedMessageException e) {
                throw e.raise();
            }
        }

        public Object call(Object... args) {
            try {
                return ForeignAccess.sendExecute(getCallNode(), getFunction(), args);
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

        public static PCallCapiFunction create(String name) {
            return new PCallCapiFunction(name);
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
                e.getExceptionObject().reifyException();
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
                e.getExceptionObject().reifyException();
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
                e.getExceptionObject().reifyException();
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

        public MayRaiseNode(PFunction callable, Object errorResult) {
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
                e.getExceptionObject().reifyException();
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

    public abstract static class GetNativeDictNode extends CExtBaseNode {
        public abstract Object execute(Object obj);
    }

    public static class GetObjectDictNode extends GetNativeDictNode {
        @CompilationFinal private TruffleObject func;
        @Child private Node exec;
        @Child private ToSulongNode toSulong;
        @Child private ToJavaNode toJava;

        @Override
        public Object execute(Object self) {
            if (func == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                func = importCAPISymbol(NativeCAPISymbols.FUN_PY_OBJECT_GENERIC_GET_DICT);
                exec = insert(Message.EXECUTE.createNode());
                toSulong = insert(ToSulongNode.create());
                toJava = insert(ToJavaNode.create());
            }
            try {
                return toJava.execute(ForeignAccess.sendExecute(exec, func, toSulong.execute(self)));
            } catch (UnsupportedTypeException | ArityException | UnsupportedMessageException e) {
                CompilerDirectives.transferToInterpreter();
                throw e.raise();
            }
        }

        @TruffleBoundary
        public static Object doSlowPath(Object self) {
            try {
                TruffleObject func = importCAPISymbolSlowPath(NativeCAPISymbols.FUN_PY_OBJECT_GENERIC_GET_DICT);
                return ToJavaNode.doSlowPath(ForeignAccess.sendExecute(Message.EXECUTE.createNode(), func, ToSulongNode.doSlowPath(self)), true);
            } catch (UnsupportedTypeException | ArityException | UnsupportedMessageException e) {
                CompilerDirectives.transferToInterpreter();
                throw e.raise();
            }
        }

        public static GetObjectDictNode create() {
            return new GetObjectDictNode();
        }
    }

    @TypeSystemReference(PythonTypes.class)
    public abstract static class GetTypeMemberNode extends GetNativeDictNode {
        @Child private ToSulongNode toSulong;
        @Child private AsPythonObjectNode toJava;
        @Child private PCallCapiFunction callGetTpDictNode;
        @Child private GetLazyClassNode getNativeClassNode;

        @CompilationFinal private IsBuiltinClassProfile isTypeProfile;

        protected GetTypeMemberNode(String memberName) {
            String getterFuncName = "get_" + memberName;
            if (!NativeCAPISymbols.isValid(getterFuncName)) {
                throw new IllegalArgumentException("invalid native member getter function " + getterFuncName);
            }
            callGetTpDictNode = PCallCapiFunction.create(getterFuncName);
        }

        @Specialization(guards = "cachedObj.equals(obj)", limit = "1", assumptions = "getNativeClassStableAssumption(cachedObj)")
        public Object doCached(@SuppressWarnings("unused") PythonNativeClass obj,
                        @Cached("obj") @SuppressWarnings("unused") PythonNativeClass cachedObj,
                        @Cached("doUncached(obj)") Object result) {
            return result;
        }

        @Specialization
        public Object doUncached(Object self) {
            if (toSulong == null || toJava == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                toSulong = insert(ToSulongNode.create());
                toJava = insert(AsPythonObjectNode.create());
            }
            assert isNativeTypeObject(self);
            return toJava.execute(callGetTpDictNode.call(toSulong.execute(self)));
        }

        protected Assumption getNativeClassStableAssumption(PythonNativeClass clazz) {
            return getContext().getNativeClassStableAssumption(clazz, true).getAssumption();
        }

        private boolean isNativeTypeObject(Object self) {
            if (getNativeClassNode == null || isTypeProfile == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                getNativeClassNode = insert(GetLazyClassNode.create());
                isTypeProfile = IsBuiltinClassProfile.create();
            }
            return isTypeProfile.profileClass(getNativeClassNode.execute(self), PythonBuiltinClassType.PythonClass);
        }

        @TruffleBoundary
        public static Object doSlowPath(Object self, String memberName) {
            String getterFuncName = "get_" + memberName;
            if (!NativeCAPISymbols.isValid(getterFuncName)) {
                throw new IllegalArgumentException("invalid native member getter function " + getterFuncName);
            }
            return AsPythonObjectNode.doSlowPath(PCallCapiFunction.doSlowPath(getterFuncName, ToSulongNode.doSlowPath(self)), false);
        }

        public static GetTypeMemberNode create(String typeMember) {
            return GetTypeMemberNodeGen.create(typeMember);
        }
    }
}
