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

import static com.oracle.graal.python.builtins.objects.cext.NativeCAPISymbols.FUN_GET_OB_TYPE;
import static com.oracle.graal.python.builtins.objects.cext.NativeCAPISymbols.FUN_NATIVE_LONG_TO_JAVA;
import static com.oracle.graal.python.builtins.objects.cext.NativeCAPISymbols.FUN_NATIVE_TO_JAVA;
import static com.oracle.graal.python.builtins.objects.cext.NativeCAPISymbols.FUN_PTR_COMPARE;
import static com.oracle.graal.python.builtins.objects.cext.NativeCAPISymbols.FUN_PY_FLOAT_AS_DOUBLE;
import static com.oracle.graal.python.builtins.objects.cext.NativeCAPISymbols.FUN_PY_OBJECT_GENERIC_GET_DICT;
import static com.oracle.graal.python.builtins.objects.cext.NativeCAPISymbols.FUN_PY_TRUFFLE_BYTE_ARRAY_TO_NATIVE;
import static com.oracle.graal.python.builtins.objects.cext.NativeCAPISymbols.FUN_PY_TRUFFLE_CSTR_TO_STRING;
import static com.oracle.graal.python.builtins.objects.cext.NativeCAPISymbols.FUN_PY_TRUFFLE_STRING_TO_CSTR;
import static com.oracle.graal.python.builtins.objects.cext.NativeCAPISymbols.FUN_WHCAR_SIZE;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__FLOAT__;

import java.util.List;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.modules.BuiltinFunctions.GetAttrNode;
import com.oracle.graal.python.builtins.modules.PythonCextBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.PythonAbstractObject;
import com.oracle.graal.python.builtins.objects.cext.CExtNodesFactory.AllToJavaNodeGen;
import com.oracle.graal.python.builtins.objects.cext.CExtNodesFactory.AllToSulongNodeGen;
import com.oracle.graal.python.builtins.objects.cext.CExtNodesFactory.AsLongNodeGen;
import com.oracle.graal.python.builtins.objects.cext.CExtNodesFactory.CextUpcallNodeGen;
import com.oracle.graal.python.builtins.objects.cext.CExtNodesFactory.DirectUpcallNodeGen;
import com.oracle.graal.python.builtins.objects.cext.CExtNodesFactory.GetNativeClassNodeFactory.GetNativeClassCachedNodeGen;
import com.oracle.graal.python.builtins.objects.cext.CExtNodesFactory.GetTypeMemberNodeGen;
import com.oracle.graal.python.builtins.objects.cext.CExtNodesFactory.IsPointerNodeGen;
import com.oracle.graal.python.builtins.objects.cext.CExtNodesFactory.ObjectUpcallNodeGen;
import com.oracle.graal.python.builtins.objects.cext.CExtNodesFactory.PointerCompareNodeGen;
import com.oracle.graal.python.builtins.objects.cext.CExtNodesFactory.ToJavaNodeFactory.ToJavaCachedNodeGen;
import com.oracle.graal.python.builtins.objects.cext.DynamicObjectNativeWrapper.PrimitiveNativeWrapper;
import com.oracle.graal.python.builtins.objects.cext.DynamicObjectNativeWrapper.PythonObjectNativeWrapper;
import com.oracle.graal.python.builtins.objects.floats.PFloat;
import com.oracle.graal.python.builtins.objects.function.PFunction;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.builtins.objects.ints.PInt;
import com.oracle.graal.python.builtins.objects.module.PythonModule;
import com.oracle.graal.python.builtins.objects.str.PString;
import com.oracle.graal.python.builtins.objects.type.PythonAbstractClass;
import com.oracle.graal.python.builtins.objects.type.PythonManagedClass;
import com.oracle.graal.python.builtins.objects.type.TypeNodes;
import com.oracle.graal.python.builtins.objects.type.TypeNodes.GetNameNode;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.PRaiseNode;
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
import com.oracle.graal.python.nodes.call.special.LookupAndCallUnaryNode.CallUnaryContextManager;
import com.oracle.graal.python.nodes.call.special.LookupAndCallUnaryNode.LookupAndCallUnaryDynamicNode;
import com.oracle.graal.python.nodes.classes.IsSubtypeNode;
import com.oracle.graal.python.nodes.frame.MaterializeFrameNode;
import com.oracle.graal.python.nodes.frame.MaterializeFrameNodeGen;
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
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.PythonCore;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.exception.PythonErrorType;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleLanguage.ContextReference;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.CachedContext;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.TypeSystemReference;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.NodeCost;
import com.oracle.truffle.api.nodes.NodeUtil;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.api.profiles.ConditionProfile;
import com.oracle.truffle.api.profiles.ValueProfile;

public abstract class CExtNodes {

    // -----------------------------------------------------------------------------------------------------------------
    @com.oracle.truffle.api.dsl.ImportStatic(PGuards.class)
    abstract static class CExtBaseNode extends com.oracle.graal.python.nodes.PNodeWithContext {
        protected static boolean isNativeWrapper(Object obj) {
            return obj instanceof PythonNativeWrapper;
        }

        protected static boolean isNativeNull(Object object) {
            return object instanceof PythonNativeNull;
        }

        protected static boolean isMaterialized(DynamicObjectNativeWrapper.PrimitiveNativeWrapper wrapper) {
            return wrapper.getMaterializedObject() != null;
        }
    }

    @GenerateUncached
    abstract static class ImportCAPISymbolNode extends PNodeWithContext {

        public abstract Object execute(String name);

        @Specialization(guards = "cachedName == name", limit = "1", assumptions = "singleContextAssumption()")
        Object doReceiverCachedIdentity(@SuppressWarnings("unused") String name,
                        @Cached("name") @SuppressWarnings("unused") String cachedName,
                        @Shared("context") @CachedContext(PythonLanguage.class) @SuppressWarnings("unused") PythonContext context,
                        @Cached("importCAPISymbolUncached(context, name)") Object sym) {
            return sym;
        }

        @Specialization(guards = "cachedName.equals(name)", limit = "1", assumptions = "singleContextAssumption()", replaces = "doReceiverCachedIdentity")
        Object doReceiverCached(@SuppressWarnings("unused") String name,
                        @Cached("name") @SuppressWarnings("unused") String cachedName,
                        @Shared("context") @CachedContext(PythonLanguage.class) @SuppressWarnings("unused") PythonContext context,
                        @Cached("importCAPISymbolUncached(context, name)") Object sym) {
            return sym;
        }

        @Specialization(replaces = {"doReceiverCached", "doReceiverCachedIdentity"})
        Object doGeneric(String name,
                        @CachedLibrary(limit = "1") @SuppressWarnings("unused") InteropLibrary interopLib,
                        @Shared("context") @CachedContext(PythonLanguage.class) @SuppressWarnings("unused") PythonContext context,
                        @Cached PRaiseNode raiseNode) {
            return importCAPISymbol(raiseNode, interopLib, context.getCapiLibrary(), name);
        }

        protected static Object importCAPISymbolUncached(PythonContext context, String name) {
            Object capiLibrary = context.getCapiLibrary();
            InteropLibrary uncached = InteropLibrary.getFactory().getUncached(capiLibrary);
            return importCAPISymbol(PRaiseNode.getUncached(), uncached, capiLibrary, name);
        }

        private static Object importCAPISymbol(PRaiseNode raiseNode, InteropLibrary library, Object capiLibrary, String name) {
            try {
                return library.readMember(capiLibrary, name);
            } catch (UnknownIdentifierException e) {
                throw raiseNode.raise(PythonBuiltinClassType.SystemError, "invalid C API function: %s", name);
            } catch (UnsupportedMessageException e) {
                throw raiseNode.raise(PythonBuiltinClassType.SystemError, "corrupted C API library object: %s", capiLibrary);
            }
        }

    }

    // -----------------------------------------------------------------------------------------------------------------
    /**
     * For some builtin classes, the CPython approach to creating a subclass instance is to just
     * call the alloc function and then assign some fields. This needs to be done in C. This node
     * will call that subtype C function with two arguments, the C type object and an object
     * argument to fill in from.
     */
    public abstract static class SubtypeNew extends CExtBaseNode {
        /**
         * typenamePrefix the <code>typename</code> in <code>typename_subtype_new</code>
         */
        protected String getTypenamePrefix() {
            throw new IllegalStateException();
        }

        public abstract Object execute(PythonNativeClass object, Object arg);

        protected String getFunctionName() {
            return getTypenamePrefix() + "_subtype_new";
        }

        @Specialization
        public Object execute(PythonNativeClass object, Object arg,
                        @Exclusive @Cached("getFunctionName()") String functionName,
                        @Exclusive @Cached ToSulongNode toSulongNode,
                        @Exclusive @Cached ToJavaNode toJavaNode,
                        @CachedLibrary(limit = "1") InteropLibrary interopLibrary,
                        @Exclusive @Cached ImportCAPISymbolNode importCAPISymbolNode) {
            try {
                return toJavaNode.execute(interopLibrary.execute(importCAPISymbolNode.execute(functionName), toSulongNode.execute(object), arg));
            } catch (UnsupportedMessageException | UnsupportedTypeException | ArityException e) {
                throw new IllegalStateException("C subtype_new function failed", e);
            }
        }
    }

    public abstract static class FloatSubtypeNew extends SubtypeNew {
        @Override
        protected final String getTypenamePrefix() {
            return "float";
        }

        public static FloatSubtypeNew create() {
            return CExtNodesFactory.FloatSubtypeNewNodeGen.create();
        }
    }

    // -----------------------------------------------------------------------------------------------------------------
    public abstract static class FromNativeSubclassNode extends CExtBaseNode {

        public abstract Double execute(VirtualFrame frame, PythonNativeObject object);

        @Specialization
        @SuppressWarnings("unchecked")
        public Double execute(VirtualFrame frame, PythonNativeObject object,
                        @Exclusive @Cached GetClassNode getClass,
                        @Exclusive @Cached IsSubtypeNode isSubtype,
                        @Exclusive @Cached ToSulongNode toSulongNode,
                        @CachedLibrary(limit = "1") InteropLibrary interopLibrary,
                        @CachedContext(PythonLanguage.class) PythonContext context,
                        @Exclusive @Cached ImportCAPISymbolNode importCAPISymbolNode) {
            if (isFloatSubtype(frame, object, getClass, isSubtype, context)) {
                try {
                    return (Double) interopLibrary.execute(importCAPISymbolNode.execute(FUN_PY_FLOAT_AS_DOUBLE), toSulongNode.execute(object));
                } catch (UnsupportedMessageException | UnsupportedTypeException | ArityException e) {
                    throw new IllegalStateException("C object conversion function failed", e);
                }
            }
            return null;
        }

        public boolean isFloatSubtype(VirtualFrame frame, PythonNativeObject object, GetClassNode getClass, IsSubtypeNode isSubtype, PythonContext context) {
            return isSubtype.execute(frame, getClass.execute(object), context.getCore().lookupType(PythonBuiltinClassType.PFloat));
        }

        public static FromNativeSubclassNode create() {
            return CExtNodesFactory.FromNativeSubclassNodeGen.create();
        }
    }

    // -----------------------------------------------------------------------------------------------------------------
    @GenerateUncached
    public abstract static class ToSulongNode extends CExtBaseNode {

        public abstract Object execute(Object obj);

        @Specialization
        Object doString(String str,
                        @Cached PythonObjectFactory factory,
                        @Cached("createBinaryProfile()") ConditionProfile noWrapperProfile) {
            return DynamicObjectNativeWrapper.PythonObjectNativeWrapper.wrap(factory.createString(str), noWrapperProfile);
        }

        @Specialization
        Object doBoolean(boolean b,
                        @CachedContext(PythonLanguage.class) PythonContext context,
                        @Cached("createBinaryProfile()") ConditionProfile profile) {
            PythonCore core = context.getCore();
            PInt boxed = b ? core.getTrue() : core.getFalse();
            DynamicObjectNativeWrapper nativeWrapper = boxed.getNativeWrapper();
            if (profile.profile(nativeWrapper == null)) {
                nativeWrapper = DynamicObjectNativeWrapper.PrimitiveNativeWrapper.createBool(b);
                boxed.setNativeWrapper(nativeWrapper);
            }
            return nativeWrapper;
        }

        @Specialization
        Object doInteger(int i) {
            return DynamicObjectNativeWrapper.PrimitiveNativeWrapper.createInt(i);
        }

        @Specialization
        Object doLong(long l) {
            return DynamicObjectNativeWrapper.PrimitiveNativeWrapper.createLong(l);
        }

        @Specialization
        Object doDouble(double d) {
            return DynamicObjectNativeWrapper.PrimitiveNativeWrapper.createDouble(d);
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
            return DynamicObjectNativeWrapper.PythonObjectNativeWrapper.wrap(CompilerDirectives.castExact(object, cachedClass), noWrapperProfile);
        }

        @Specialization(guards = {"!isClass(object)", "!isNativeObject(object)", "!isNoValue(object)"}, replaces = "runAbstractObjectCached")
        Object runAbstractObject(PythonAbstractObject object,
                        @Cached("createBinaryProfile()") ConditionProfile noWrapperProfile) {
            assert object != PNone.NO_VALUE;
            return DynamicObjectNativeWrapper.PythonObjectNativeWrapper.wrap(object, noWrapperProfile);
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

        // TODO(fa): Workaround for DSL bug: did not import factory at users
        public static ToSulongNode create() {
            return CExtNodesFactory.ToSulongNodeGen.create();
        }

        // TODO(fa): Workaround for DSL bug: did not import factory at users
        public static ToSulongNode getUncached() {
            return CExtNodesFactory.ToSulongNodeGen.getUncached();
        }

        @TruffleBoundary
        public static Object doSlowPath(Object o) {
            if (o instanceof String) {
                return PythonObjectNativeWrapper.wrapSlowPath(PythonLanguage.getCore().factory().createString((String) o));
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

    // -----------------------------------------------------------------------------------------------------------------
    /**
     * Unwraps objects contained in {@link DynamicObjectNativeWrapper.PythonObjectNativeWrapper}
     * instances or wraps objects allocated in native code for consumption in Java.
     */
    @GenerateUncached
    public abstract static class AsPythonObjectNode extends CExtBaseNode {
        public abstract Object execute(Object value);

        @Specialization(guards = "object.isBool()")
        boolean doBoolNativeWrapper(DynamicObjectNativeWrapper.PrimitiveNativeWrapper object) {
            return object.getBool();
        }

        @Specialization(guards = {"object.isByte()", "!isNative(isPointerNode, object)"}, limit = "1")
        byte doByteNativeWrapper(DynamicObjectNativeWrapper.PrimitiveNativeWrapper object,
                        @Shared("isPointerNode") @Cached @SuppressWarnings("unused") IsPointerNode isPointerNode) {
            return object.getByte();
        }

        @Specialization(guards = {"object.isInt()", "!isNative(isPointerNode, object)"}, limit = "1")
        int doIntNativeWrapper(DynamicObjectNativeWrapper.PrimitiveNativeWrapper object,
                        @Shared("isPointerNode") @Cached @SuppressWarnings("unused") IsPointerNode isPointerNode) {
            return object.getInt();
        }

        @Specialization(guards = {"object.isLong()", "!isNative(isPointerNode, object)"}, limit = "1")
        long doLongNativeWrapper(DynamicObjectNativeWrapper.PrimitiveNativeWrapper object,
                        @Shared("isPointerNode") @Cached @SuppressWarnings("unused") IsPointerNode isPointerNode) {
            return object.getLong();
        }

        @Specialization(guards = {"object.isDouble()", "!isNative(isPointerNode, object)"}, limit = "1")
        double doDoubleNativeWrapper(DynamicObjectNativeWrapper.PrimitiveNativeWrapper object,
                        @Shared("isPointerNode") @Cached @SuppressWarnings("unused") IsPointerNode isPointerNode) {
            return object.getDouble();
        }

        @Specialization(guards = {"!object.isBool()", "isNative(isPointerNode, object)"}, limit = "1")
        Object doPrimitiveNativeWrapper(DynamicObjectNativeWrapper.PrimitiveNativeWrapper object,
                        @Exclusive @Cached MaterializeDelegateNode materializeNode,
                        @Shared("isPointerNode") @Cached @SuppressWarnings("unused") IsPointerNode isPointerNode) {
            return materializeNode.execute(object);
        }

        @Specialization(guards = "!isPrimitiveNativeWrapper(object)")
        Object doNativeWrapper(PythonNativeWrapper object) {
            return object.getDelegate();
        }

        @Specialization(guards = {"isForeignObject(object, getClassNode, isForeignClassProfile)", "!isNativeWrapper(object)", "!isNativeNull(object)"}, limit = "1")
        Object doNativeObject(TruffleObject object,
                        @Cached PythonObjectFactory factory,
                        @SuppressWarnings("unused") @Cached("create()") GetLazyClassNode getClassNode,
                        @SuppressWarnings("unused") @Cached("create()") IsBuiltinClassProfile isForeignClassProfile) {
            return factory.createNativeObjectWrapper(object);
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

        @Specialization
        Object run(Object obj,
                        @Cached PRaiseNode raiseNode) {
            throw raiseNode.raise(PythonErrorType.SystemError, "invalid object from native: %s", obj);
        }

        protected static boolean isNative(IsPointerNode isPointerNode, PythonNativeWrapper object) {
            return isPointerNode.execute(object);
        }

        protected static boolean isPrimitiveNativeWrapper(PythonNativeWrapper object) {
            return object instanceof DynamicObjectNativeWrapper.PrimitiveNativeWrapper;
        }

        protected static boolean isForeignObject(TruffleObject obj, GetLazyClassNode getClassNode, IsBuiltinClassProfile isForeignClassProfile) {
            return isForeignClassProfile.profileClass(getClassNode.execute(obj), PythonBuiltinClassType.ForeignObject);
        }

        @TruffleBoundary
        public static Object doSlowPath(Object object, boolean forceNativeClass) {
            if (object instanceof PythonNativeWrapper) {
                return ((PythonNativeWrapper) object).getDelegate();
            } else if (IsBuiltinClassProfile.profileClassSlowPath(GetClassNode.getUncached().execute(object), PythonBuiltinClassType.ForeignObject)) {
                if (forceNativeClass) {
                    return PythonObjectFactory.getUncached().createNativeClassWrapper((TruffleObject) object);
                }
                return PythonObjectFactory.getUncached().createNativeObjectWrapper((TruffleObject) object);
            } else if (object instanceof String || object instanceof Number || object instanceof Boolean || object instanceof PythonNativeNull || object instanceof PythonAbstractObject) {
                return object;
            }
            throw PRaiseNode.getUncached().raise(PythonErrorType.SystemError, "invalid object from native: %s", object);
        }

        // TODO(fa): Workaround for DSL bug: did not import factory at users
        public static AsPythonObjectNode create() {
            return CExtNodesFactory.AsPythonObjectNodeGen.create();
        }

        // TODO(fa): Workaround for DSL bug: did not import factory at users
        public static AsPythonObjectNode getUncached() {
            return CExtNodesFactory.AsPythonObjectNodeGen.getUncached();
        }

        public static AsPythonObjectNode createForceClass() {
            return CExtNodesFactory.AsPythonObjectNodeGen.create();
        }
    }

    // -----------------------------------------------------------------------------------------------------------------
    /**
     * Materializes a primitive value of a primitive native wrapper to ensure pointer equality.
     */
    @GenerateUncached
    public abstract static class MaterializeDelegateNode extends CExtBaseNode {

        public abstract Object execute(PythonNativeWrapper object);

        @Specialization(guards = {"!isMaterialized(object)", "object.isBool()"})
        PInt doBoolNativeWrapper(DynamicObjectNativeWrapper.PrimitiveNativeWrapper object,
                        @CachedContext(PythonLanguage.class) PythonContext context) {
            PythonCore core = context.getCore();
            PInt materializedInt = object.getBool() ? core.getTrue() : core.getFalse();
            object.setMaterializedObject(materializedInt);
            if (materializedInt.getNativeWrapper() != null) {
                object.setNativePointer(materializedInt.getNativeWrapper().getNativePointer());
            } else {
                materializedInt.setNativeWrapper(object);
            }
            return materializedInt;
        }

        @Specialization(guards = {"!isMaterialized(object)", "object.isByte()"})
        PInt doByteNativeWrapper(DynamicObjectNativeWrapper.PrimitiveNativeWrapper object,
                        @Cached PythonObjectFactory factory) {
            PInt materializedInt = factory.createInt(object.getByte());
            object.setMaterializedObject(materializedInt);
            materializedInt.setNativeWrapper(object);
            return materializedInt;
        }

        @Specialization(guards = {"!isMaterialized(object)", "object.isInt()"})
        PInt doIntNativeWrapper(DynamicObjectNativeWrapper.PrimitiveNativeWrapper object,
                        @Cached PythonObjectFactory factory) {
            PInt materializedInt = factory.createInt(object.getInt());
            object.setMaterializedObject(materializedInt);
            materializedInt.setNativeWrapper(object);
            return materializedInt;
        }

        @Specialization(guards = {"!isMaterialized(object)", "object.isLong()"})
        PInt doLongNativeWrapper(DynamicObjectNativeWrapper.PrimitiveNativeWrapper object,
                        @Cached PythonObjectFactory factory) {
            PInt materializedInt = factory.createInt(object.getLong());
            object.setMaterializedObject(materializedInt);
            materializedInt.setNativeWrapper(object);
            return materializedInt;
        }

        @Specialization(guards = {"!isMaterialized(object)", "object.isDouble()"})
        PFloat doDoubleNativeWrapper(DynamicObjectNativeWrapper.PrimitiveNativeWrapper object,
                        @Cached PythonObjectFactory factory) {
            PFloat materializedInt = factory.createFloat(object.getDouble());
            object.setMaterializedObject(materializedInt);
            materializedInt.setNativeWrapper(object);
            return materializedInt;
        }

        @Specialization(guards = {"object.getClass() == cachedClass", "isMaterialized(object)"})
        Object doMaterialized(DynamicObjectNativeWrapper.PrimitiveNativeWrapper object,
                        @SuppressWarnings("unused") @Cached("object.getClass()") Class<? extends DynamicObjectNativeWrapper.PrimitiveNativeWrapper> cachedClass) {
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
            return object instanceof DynamicObjectNativeWrapper.PrimitiveNativeWrapper;
        }
    }

    // -----------------------------------------------------------------------------------------------------------------
    /**
     * Does the same conversion as the native function {@code to_java}. The node tries to avoid
     * calling the native function for resolving native handles.
     */
    public abstract static class ToJavaNode extends CExtBaseNode {
        public abstract Object execute(Object value);

        public abstract boolean executeBool(boolean value);

        public abstract byte executeByte(byte value);

        public abstract int executeInt(int value);

        public abstract long executeLong(long value);

        public abstract double executeDouble(double value);

        abstract static class ToJavaCachedNode extends ToJavaNode {

            @Specialization
            static PythonAbstractObject doPythonObject(PythonAbstractObject value) {
                return value;
            }

            @Specialization
            static Object doWrapper(PythonNativeWrapper value,
                            @Shared("toJavaNode") @Cached AsPythonObjectNode toJavaNode) {
                return toJavaNode.execute(value);
            }

            @Specialization
            static String doString(String object) {
                return object;
            }

            @Specialization
            static boolean doBoolean(boolean b) {
                return b;
            }

            @Specialization
            static int doInt(int i) {
                // Note: Sulong guarantees that an integer won't be a pointer
                return i;
            }

            @Specialization
            static Object doLong(long l,
                            @Exclusive @Cached PCallCapiFunction callNativeNode,
                            @Shared("toJavaNode") @Cached AsPythonObjectNode toJavaNode) {
                // Unfortunately, a long could be a native pointer and therefore a handle. So, we
                // must try resolving it. At least we know that it's not a native type.
                return toJavaNode.execute(callNativeNode.call(FUN_NATIVE_LONG_TO_JAVA, l));
            }

            @Specialization
            static byte doByte(byte b) {
                return b;
            }

            @Specialization(guards = "isForeignObject(value)")
            static Object doForeign(Object value,
                            @Exclusive @Cached PCallCapiFunction callNativeNode,
                            @Shared("toJavaNode") @Cached AsPythonObjectNode toJavaNode) {
                return toJavaNode.execute(callNativeNode.call(FUN_NATIVE_TO_JAVA, value));
            }

            protected static boolean isForeignObject(Object obj) {
                return !(obj instanceof PythonAbstractObject || obj instanceof PythonNativeWrapper || obj instanceof String || obj instanceof Boolean || obj instanceof Integer ||
                                obj instanceof Long || obj instanceof Byte);
            }

        }

        private static final class ToJavaUncachedNode extends ToJavaNode {
            private static final ToJavaUncachedNode INSTANCE = new ToJavaUncachedNode();

            @Override
            public Object execute(Object arg0Value) {
                if (arg0Value instanceof PythonAbstractObject) {
                    PythonAbstractObject arg0Value_ = (PythonAbstractObject) arg0Value;
                    return ToJavaCachedNode.doPythonObject(arg0Value_);
                }
                if (arg0Value instanceof PythonNativeWrapper) {
                    PythonNativeWrapper arg0Value_ = (PythonNativeWrapper) arg0Value;
                    return ToJavaCachedNode.doWrapper(arg0Value_, (CExtNodesFactory.AsPythonObjectNodeGen.getUncached()));
                }
                if (arg0Value instanceof String) {
                    String arg0Value_ = (String) arg0Value;
                    return ToJavaCachedNode.doString(arg0Value_);
                }
                if (arg0Value instanceof Boolean) {
                    boolean arg0Value_ = (boolean) arg0Value;
                    return ToJavaCachedNode.doBoolean(arg0Value_);
                }
                if (arg0Value instanceof Integer) {
                    int arg0Value_ = (int) arg0Value;
                    return ToJavaCachedNode.doInt(arg0Value_);
                }
                if (arg0Value instanceof Long) {
                    long arg0Value_ = (long) arg0Value;
                    return ToJavaCachedNode.doLong(arg0Value_, (PCallCapiFunction.getUncached()), (CExtNodesFactory.AsPythonObjectNodeGen.getUncached()));
                }
                if (arg0Value instanceof Byte) {
                    byte arg0Value_ = (byte) arg0Value;
                    return ToJavaCachedNode.doByte(arg0Value_);
                }
                return ToJavaCachedNode.doForeign(arg0Value, (PCallCapiFunction.getUncached()), (CExtNodesFactory.AsPythonObjectNodeGen.getUncached()));
            }

            @Override
            public boolean executeBool(boolean arg0Value) {
                return ToJavaCachedNode.doBoolean(arg0Value);
            }

            @Override
            public byte executeByte(byte arg0Value) {
                return ToJavaCachedNode.doByte(arg0Value);
            }

            @Override
            public double executeDouble(double arg0Value) {
                return (double) ToJavaCachedNode.doForeign(arg0Value, (PCallCapiFunction.getUncached()), (CExtNodesFactory.AsPythonObjectNodeGen.getUncached()));
            }

            @Override
            public int executeInt(int arg0Value) {
                return ToJavaCachedNode.doInt(arg0Value);
            }

            @Override
            public long executeLong(long arg0Value) {
                return (long) ToJavaCachedNode.doLong(arg0Value, (PCallCapiFunction.getUncached()), (CExtNodesFactory.AsPythonObjectNodeGen.getUncached()));
            }

            @Override
            public NodeCost getCost() {
                return NodeCost.MEGAMORPHIC;
            }

            @Override
            public boolean isAdoptable() {
                return false;
            }

        }

        public static ToJavaNode create() {
            return ToJavaCachedNodeGen.create();
        }

        public static ToJavaNode getUncached() {
            return ToJavaUncachedNode.INSTANCE;
        }
    }

    // -----------------------------------------------------------------------------------------------------------------
    @GenerateUncached
    public abstract static class AsCharPointerNode extends CExtBaseNode {
        public abstract Object execute(Object obj);

        @Specialization
        Object doPString(PString str,
                        @Shared("callStringToCstrNode") @Cached PCallCapiFunction callStringToCstrNode) {
            String value = str.getValue();
            return callStringToCstrNode.call(FUN_PY_TRUFFLE_STRING_TO_CSTR, value, value.length());
        }

        @Specialization
        Object doString(String str,
                        @Shared("callStringToCstrNode") @Cached PCallCapiFunction callStringToCstrNode) {
            return callStringToCstrNode.call(FUN_PY_TRUFFLE_STRING_TO_CSTR, str, str.length());
        }

        @Specialization
        Object doByteArray(byte[] arr,
                        @CachedContext(PythonLanguage.class) PythonContext context,
                        @Exclusive @Cached PCallCapiFunction callByteArrayToNativeNode) {
            return callByteArrayToNativeNode.call(FUN_PY_TRUFFLE_BYTE_ARRAY_TO_NATIVE, context.getEnv().asGuestValue(arr), arr.length);
        }

        // TODO(fa): Workaround for DSL bug: did not import factory at users
        public static AsCharPointerNode create() {
            return CExtNodesFactory.AsCharPointerNodeGen.create();
        }

        // TODO(fa): Workaround for DSL bug: did not import factory at users
        public static AsCharPointerNode getUncached() {
            return CExtNodesFactory.AsCharPointerNodeGen.getUncached();
        }
    }

    // -----------------------------------------------------------------------------------------------------------------
    @GenerateUncached
    public abstract static class FromCharPointerNode extends CExtBaseNode {
        public abstract String execute(Object charPtr);

        @Specialization
        public String execute(Object charPtr,
                        @Cached PCallCapiFunction callCstrToStringNode) {

            return (String) callCstrToStringNode.call(FUN_PY_TRUFFLE_CSTR_TO_STRING, charPtr);
        }
    }

    // -----------------------------------------------------------------------------------------------------------------
    public abstract static class GetNativeClassNode extends CExtBaseNode {
        public abstract PythonAbstractClass execute(PythonNativeObject object);

        abstract static class GetNativeClassCachedNode extends GetNativeClassNode {
            @Specialization(guards = "object == cachedObject", limit = "1")
            static PythonAbstractClass getNativeClassCachedIdentity(@SuppressWarnings("unused") PythonNativeObject object,
                            @Exclusive @Cached("object") @SuppressWarnings("unused") PythonNativeObject cachedObject,
                            @Exclusive @Cached(value = "getNativeClassUncached(cachedObject)", allowUncached = true) PythonAbstractClass cachedClass) {
                // TODO: (tfel) is this really something we can do? It's so rare for this class to
                // change that it shouldn't be worth the effort, but in native code, anything can
                // happen. OTOH, CPython also has caches that can become invalid when someone just
                // goes and changes the ob_type of an object.
                return cachedClass;
            }

            @Specialization(guards = "cachedObject.equals(object)", limit = "1", assumptions = "singleContextAssumption()")
            static PythonAbstractClass getNativeClassCached(@SuppressWarnings("unused") PythonNativeObject object,
                            @Exclusive @Cached("object") @SuppressWarnings("unused") PythonNativeObject cachedObject,
                            @Exclusive @Cached(value = "getNativeClassUncached(cachedObject)", allowUncached = true) PythonAbstractClass cachedClass) {
                // TODO same as for 'getNativeClassCachedIdentity'
                return cachedClass;
            }

            @Specialization(replaces = "getNativeClassCached")
            static PythonAbstractClass getNativeClass(PythonNativeObject object,
                            @Exclusive @Cached PCallCapiFunction callGetObTypeNode,
                            @Exclusive @Cached ToJavaNode toJavaNode) {
                // do not convert wrap 'object.object' since that is really the native pointer
                // object
                return (PythonAbstractClass) toJavaNode.execute(callGetObTypeNode.call(FUN_GET_OB_TYPE, object.getPtr()));
            }

            protected static PythonAbstractClass getNativeClassUncached(PythonNativeObject object) {
                // do not convert wrap 'object.object' since that is really the native pointer
                // object
                return getNativeClass(object, PCallCapiFunction.getUncached(), ToJavaNode.getUncached());
            }
        }

        private static final class Uncached extends GetNativeClassNode {
            private static final Uncached INSTANCE = new Uncached();

            @TruffleBoundary
            @Override
            public PythonAbstractClass execute(PythonNativeObject object) {
                return GetNativeClassCachedNode.getNativeClassUncached(object);
            }

            @Override
            public NodeCost getCost() {
                return NodeCost.MEGAMORPHIC;
            }

            @Override
            public boolean isAdoptable() {
                return false;
            }

        }

        public static GetNativeClassNode create() {
            return GetNativeClassCachedNodeGen.create();
        }

        public static GetNativeClassNode getUncached() {
            return Uncached.INSTANCE;
        }
    }

    // -----------------------------------------------------------------------------------------------------------------
    @GenerateUncached
    public abstract static class SizeofWCharNode extends CExtBaseNode {

        public abstract long execute();

        @Specialization
        long doCached(
                        @Exclusive @Cached(value = "getWcharSize()", allowUncached = true) long wcharSize) {
            return wcharSize;
        }

        protected static long getWcharSize() {
            long wcharSize = (long) PCallCapiFunction.getUncached().call(FUN_WHCAR_SIZE);
            assert wcharSize >= 0L;
            return wcharSize;
        }

        public static SizeofWCharNode create() {
            return CExtNodesFactory.SizeofWCharNodeGen.create();
        }
    }

    // -----------------------------------------------------------------------------------------------------------------
    @GenerateUncached
    public abstract static class PointerCompareNode extends CExtBaseNode {
        public abstract boolean execute(String opName, Object a, Object b);

        private static boolean executeCFunction(int op, Object a, Object b, InteropLibrary interopLibrary, ImportCAPISymbolNode importCAPISymbolNode) {
            try {
                return (int) interopLibrary.execute(importCAPISymbolNode.execute(FUN_PTR_COMPARE), a, b, op) != 0;
            } catch (UnsupportedTypeException | ArityException | UnsupportedMessageException e) {
                CompilerDirectives.transferToInterpreter();
                throw new IllegalStateException(FUN_PTR_COMPARE + " didn't work!");
            }
        }

        @Specialization(guards = "cachedOpName.equals(opName)", limit = "1")
        public boolean execute(@SuppressWarnings("unused") String opName, PythonNativeObject a, PythonNativeObject b,
                        @Shared("cachedOpName") @Cached("opName") @SuppressWarnings("unused") String cachedOpName,
                        @Shared("op") @Cached(value = "findOp(opName)", allowUncached = true) int op,
                        @CachedLibrary(limit = "1") InteropLibrary interopLibrary,
                        @Shared("importCAPISymbolNode") @Cached ImportCAPISymbolNode importCAPISymbolNode) {
            return executeCFunction(op, a.getPtr(), b.getPtr(), interopLibrary, importCAPISymbolNode);
        }

        @Specialization(guards = "cachedOpName.equals(opName)", limit = "1")
        public boolean execute(@SuppressWarnings("unused") String opName, PythonNativeObject a, long b,
                        @Shared("cachedOpName") @Cached("opName") @SuppressWarnings("unused") String cachedOpName,
                        @Shared("op") @Cached(value = "findOp(opName)", allowUncached = true) int op,
                        @CachedLibrary(limit = "1") InteropLibrary interopLibrary,
                        @Shared("importCAPISymbolNode") @Cached ImportCAPISymbolNode importCAPISymbolNode) {
            return executeCFunction(op, a.getPtr(), b, interopLibrary, importCAPISymbolNode);
        }

        @Specialization(guards = "cachedOpName.equals(opName)", limit = "1")
        public boolean execute(@SuppressWarnings("unused") String opName, PythonNativeVoidPtr a, long b,
                        @Shared("cachedOpName") @Cached("opName") @SuppressWarnings("unused") String cachedOpName,
                        @Shared("op") @Cached(value = "findOp(opName)", allowUncached = true) int op,
                        @CachedLibrary(limit = "1") InteropLibrary interopLibrary,
                        @Shared("importCAPISymbolNode") @Cached ImportCAPISymbolNode importCAPISymbolNode) {
            return executeCFunction(op, a.object, b, interopLibrary, importCAPISymbolNode);
        }

        public static int findOp(String specialMethodName) {
            for (int i = 0; i < SpecialMethodNames.COMPARE_OP_COUNT; i++) {
                if (SpecialMethodNames.getCompareName(i).equals(specialMethodName)) {
                    return i;
                }
            }
            throw new RuntimeException("The special method used for Python C API pointer comparison must be a constant literal (i.e., interned) string");
        }

        public static PointerCompareNode create() {
            return PointerCompareNodeGen.create();
        }

        public static PointerCompareNode getUncached() {
            return PointerCompareNodeGen.getUncached();
        }
    }

    // -----------------------------------------------------------------------------------------------------------------
    public abstract static class AllToJavaNode extends PNodeWithContext {
        abstract Object[] execute(Object[] args);

        @Specialization(guards = {"args.length == cachedLength", "cachedLength < 5"}, limit = "5")
        @ExplodeLoop
        Object[] cached(Object[] args,
                        @Cached("args.length") int cachedLength,
                        @Exclusive @Cached AsPythonObjectNode toJavaNode) {
            Object[] output = new Object[cachedLength];
            for (int i = 0; i < cachedLength; i++) {
                output[i] = toJavaNode.execute(args[i]);
            }
            return output;
        }

        @Specialization(replaces = "cached")
        Object[] uncached(Object[] args,
                        @Exclusive @Cached AsPythonObjectNode toJavaNode) {
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

    // -----------------------------------------------------------------------------------------------------------------
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

    // -----------------------------------------------------------------------------------------------------------------
    public abstract static class DirectUpcallNode extends PNodeWithContext {
        public abstract Object execute(VirtualFrame frame, Object callable, Object[] args);

        @Specialization(guards = "args.length == 0")
        Object upcall0(VirtualFrame frame, Object callable, @SuppressWarnings("unused") Object[] args,
                        @Cached("create()") CallNode callNode) {
            return callNode.execute(frame, callable, new Object[0], new PKeyword[0]);
        }

        @Specialization(guards = "args.length == 1")
        Object upcall1(VirtualFrame frame, Object callable, Object[] args,
                        @Cached("create()") CallUnaryMethodNode callNode,
                        @Cached("create()") CExtNodes.AsPythonObjectNode toJavaNode) {
            return callNode.executeObject(frame, callable, toJavaNode.execute(args[0]));
        }

        @Specialization(guards = "args.length == 2")
        Object upcall2(VirtualFrame frame, Object callable, Object[] args,
                        @Cached("create()") CallBinaryMethodNode callNode,
                        @Shared("allToJavaNode") @Cached AllToJavaNode allToJavaNode) {
            Object[] converted = allToJavaNode.execute(args);
            return callNode.executeObject(frame, callable, converted[0], converted[1]);
        }

        @Specialization(guards = "args.length == 3")
        Object upcall3(VirtualFrame frame, Object callable, Object[] args,
                        @Cached("create()") CallTernaryMethodNode callNode,
                        @Shared("allToJavaNode") @Cached AllToJavaNode allToJavaNode) {
            Object[] converted = allToJavaNode.execute(args);
            return callNode.execute(frame, callable, converted[0], converted[1], converted[2]);
        }

        @Specialization(replaces = {"upcall0", "upcall1", "upcall2", "upcall3"})
        Object upcall(VirtualFrame frame, Object callable, Object[] args,
                        @Cached("create()") CallNode callNode,
                        @Shared("allToJavaNode") @Cached AllToJavaNode allToJavaNode) {
            Object[] converted = allToJavaNode.execute(args);
            return callNode.execute(frame, callable, converted, new PKeyword[0]);
        }

        public static DirectUpcallNode create() {
            return DirectUpcallNodeGen.create();
        }
    }

    // -----------------------------------------------------------------------------------------------------------------
    public abstract static class CextUpcallNode extends PNodeWithContext {
        public static CextUpcallNode create() {
            return CextUpcallNodeGen.create();
        }

        public abstract Object execute(VirtualFrame frame, Object cextModule, String name, Object[] args);

        @Specialization(guards = "args.length == 0")
        Object upcall0(VirtualFrame frame, Object cextModule, String name, @SuppressWarnings("unused") Object[] args,
                        @Cached("create()") CallNode callNode,
                        @Shared("getAttrNode") @Cached ReadAttributeFromObjectNode getAttrNode) {
            Object callable = getAttrNode.execute(cextModule, name);
            return callNode.execute(frame, callable, new Object[0], PKeyword.EMPTY_KEYWORDS);
        }

        @Specialization(guards = "args.length == 1")
        Object upcall1(VirtualFrame frame, Object cextModule, String name, Object[] args,
                        @Cached("create()") CallUnaryMethodNode callNode,
                        @Cached("create()") CExtNodes.AsPythonObjectNode toJavaNode,
                        @Shared("getAttrNode") @Cached ReadAttributeFromObjectNode getAttrNode) {
            Object callable = getAttrNode.execute(cextModule, name);
            return callNode.executeObject(frame, callable, toJavaNode.execute(args[0]));
        }

        @Specialization(guards = "args.length == 2")
        Object upcall2(VirtualFrame frame, Object cextModule, String name, Object[] args,
                        @Cached("create()") CallBinaryMethodNode callNode,
                        @Shared("allToJavaNode") @Cached AllToJavaNode allToJavaNode,
                        @Shared("getAttrNode") @Cached ReadAttributeFromObjectNode getAttrNode) {
            Object[] converted = allToJavaNode.execute(args);
            Object callable = getAttrNode.execute(cextModule, name);
            return callNode.executeObject(frame, callable, converted[0], converted[1]);
        }

        @Specialization(guards = "args.length == 3")
        Object upcall3(VirtualFrame frame, Object cextModule, String name, Object[] args,
                        @Cached("create()") CallTernaryMethodNode callNode,
                        @Shared("allToJavaNode") @Cached AllToJavaNode allToJavaNode,
                        @Shared("getAttrNode") @Cached ReadAttributeFromObjectNode getAttrNode) {
            Object[] converted = allToJavaNode.execute(args);
            Object callable = getAttrNode.execute(cextModule, name);
            return callNode.execute(frame, callable, converted[0], converted[1], converted[2]);
        }

        @Specialization(replaces = {"upcall0", "upcall1", "upcall2", "upcall3"})
        Object upcall(VirtualFrame frame, Object cextModule, String name, Object[] args,
                        @Cached("create()") CallNode callNode,
                        @Shared("allToJavaNode") @Cached AllToJavaNode allToJavaNode,
                        @Shared("getAttrNode") @Cached ReadAttributeFromObjectNode getAttrNode) {
            Object[] converted = allToJavaNode.execute(args);
            Object callable = getAttrNode.execute(cextModule, name);
            return callNode.execute(frame, callable, converted, PKeyword.EMPTY_KEYWORDS);
        }
    }

    // -----------------------------------------------------------------------------------------------------------------
    public abstract static class ObjectUpcallNode extends PNodeWithContext {
        public static ObjectUpcallNode create() {
            return ObjectUpcallNodeGen.create();
        }

        public abstract Object execute(VirtualFrame frame, Object cextModule, String name, Object[] args);

        @Specialization(guards = "args.length == 0")
        Object upcall0(VirtualFrame frame, Object cextModule, String name, @SuppressWarnings("unused") Object[] args,
                        @Cached("create()") CallNode callNode,
                        @Shared("getAttrNode") @Cached GetAttrNode getAttrNode) {
            Object callable = getAttrNode.execute(frame, cextModule, name, PNone.NO_VALUE);
            return callNode.execute(frame, callable, new Object[0], PKeyword.EMPTY_KEYWORDS);
        }

        @Specialization(guards = "args.length == 1")
        Object upcall1(VirtualFrame frame, Object cextModule, String name, Object[] args,
                        @Cached("create()") CallUnaryMethodNode callNode,
                        @Cached("create()") CExtNodes.AsPythonObjectNode toJavaNode,
                        @Shared("getAttrNode") @Cached GetAttrNode getAttrNode) {
            Object callable = getAttrNode.execute(frame, cextModule, name, PNone.NO_VALUE);
            return callNode.executeObject(frame, callable, toJavaNode.execute(args[0]));
        }

        @Specialization(guards = "args.length == 2")
        Object upcall2(VirtualFrame frame, Object cextModule, String name, Object[] args,
                        @Cached("create()") CallBinaryMethodNode callNode,
                        @Shared("allToJavaNode") @Cached AllToJavaNode allToJavaNode,
                        @Shared("getAttrNode") @Cached GetAttrNode getAttrNode) {
            Object[] converted = allToJavaNode.execute(args);
            Object callable = getAttrNode.execute(frame, cextModule, name, PNone.NO_VALUE);
            return callNode.executeObject(frame, callable, converted[0], converted[1]);
        }

        @Specialization(guards = "args.length == 3")
        Object upcall3(VirtualFrame frame, Object cextModule, String name, Object[] args,
                        @Cached("create()") CallTernaryMethodNode callNode,
                        @Shared("allToJavaNode") @Cached AllToJavaNode allToJavaNode,
                        @Shared("getAttrNode") @Cached GetAttrNode getAttrNode) {
            Object[] converted = allToJavaNode.execute(args);
            Object callable = getAttrNode.execute(frame, cextModule, name, PNone.NO_VALUE);
            return callNode.execute(frame, callable, converted[0], converted[1], converted[2]);
        }

        @Specialization(replaces = {"upcall0", "upcall1", "upcall2", "upcall3"})
        Object upcall(VirtualFrame frame, Object cextModule, String name, Object[] args,
                        @Cached("create()") CallNode callNode,
                        @Shared("allToJavaNode") @Cached AllToJavaNode allToJavaNode,
                        @Shared("getAttrNode") @Cached GetAttrNode getAttrNode) {
            Object[] converted = allToJavaNode.execute(args);
            Object callable = getAttrNode.execute(frame, cextModule, name, PNone.NO_VALUE);
            return callNode.execute(frame, callable, converted, PKeyword.EMPTY_KEYWORDS);
        }
    }

    // -----------------------------------------------------------------------------------------------------------------
    @ImportStatic(SpecialMethodNames.class)
    public abstract static class AsDouble extends PNodeWithContext {
        public abstract double execute(VirtualFrame frame, boolean arg);

        public abstract double execute(VirtualFrame frame, int arg);

        public abstract double execute(VirtualFrame frame, long arg);

        public abstract double execute(VirtualFrame frame, double arg);

        public abstract double execute(VirtualFrame frame, Object arg);

        public static AsDouble create() {
            return CExtNodesFactory.AsDoubleNodeGen.create();
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
        double doLongNativeWrapper(DynamicObjectNativeWrapper.PrimitiveNativeWrapper object) {
            return object.getLong();
        }

        @Specialization(guards = "object.isDouble()")
        double doDoubleNativeWrapper(DynamicObjectNativeWrapper.PrimitiveNativeWrapper object) {
            return object.getDouble();
        }

        // TODO: this should just use the builtin constructor node so we don't duplicate the corner
        // cases
        @Specialization
        double runGeneric(VirtualFrame frame, PythonAbstractObject value,
                        @Cached LookupAndCallUnaryDynamicNode callFloatFunc,
                        @Cached PRaiseNode raiseNode,
                        @CachedContext(PythonLanguage.class) ContextReference<PythonContext> contextRef) {
            if (PGuards.isPFloat(value)) {
                return ((PFloat) value).getValue();
            }
            try (CallUnaryContextManager ctxManager = callFloatFunc.withGlobalState(contextRef, frame)) {
                Object result = ctxManager.executeObject(value, __FLOAT__);
                if (PGuards.isPFloat(result)) {
                    return ((PFloat) result).getValue();
                } else if (result instanceof Double) {
                    return (double) result;
                } else {
                    throw raiseNode.raise(PythonErrorType.TypeError, "%p.%s returned non-float (type %p)", value, __FLOAT__, result);
                }
            }
        }
    }

    // -----------------------------------------------------------------------------------------------------------------
    public abstract static class AsLong extends PNodeWithContext {
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
        long doLongNativeWrapper(DynamicObjectNativeWrapper.PrimitiveNativeWrapper object) {
            return object.getLong();
        }

        @Specialization(guards = "object.isDouble()")
        long doDoubleNativeWrapper(DynamicObjectNativeWrapper.PrimitiveNativeWrapper object) {
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
            return CastToIndexNode.getUncached().execute(value);
        }

        public static AsLong create() {
            return AsLongNodeGen.create();
        }
    }

    // -----------------------------------------------------------------------------------------------------------------
    @GenerateUncached
    public abstract static class PCallCapiFunction extends CExtBaseNode {

        public final Object call(String name, Object... args) {
            return execute(name, args);
        }

        public abstract Object execute(String name, Object[] args);

        @Specialization
        Object doIt(String name, Object[] args,
                        @CachedLibrary(limit = "1") InteropLibrary interopLibrary,
                        @Cached ImportCAPISymbolNode importCAPISymbolNode,
                        @Cached BranchProfile profile,
                        @Cached PRaiseNode raiseNode) {
            try {
                return interopLibrary.execute(importCAPISymbolNode.execute(name), args);
            } catch (UnsupportedTypeException | ArityException e) {
                profile.enter();
                throw raiseNode.raise(PythonBuiltinClassType.TypeError, e);
            } catch (UnsupportedMessageException e) {
                profile.enter();
                throw raiseNode.raise(PythonBuiltinClassType.TypeError, "C API symbol %s is not callable", name);
            }
        }

        public static PCallCapiFunction create() {
            return CExtNodesFactory.PCallCapiFunctionNodeGen.create();
        }

        public static PCallCapiFunction getUncached() {
            return CExtNodesFactory.PCallCapiFunctionNodeGen.getUncached();
        }
    }

    // -----------------------------------------------------------------------------------------------------------------
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

    // -----------------------------------------------------------------------------------------------------------------
    @Builtin(minNumOfPositionalArgs = 1)
    public abstract static class MayRaiseUnaryNode extends PythonUnaryBuiltinNode {
        @Child private CreateArgumentsNode createArgsNode;
        @Child private InvokeNode invokeNode;
        @Child private MaterializeFrameNode materializeNode;

        private final PFunction func;
        private final Object errorResult;

        public MayRaiseUnaryNode(PFunction func, Object errorResult) {
            this.createArgsNode = CreateArgumentsNode.create();
            this.func = func;
            this.invokeNode = InvokeNode.create(func);
            this.errorResult = errorResult;
        }

        @Specialization
        Object doit(VirtualFrame frame, Object argument) {
            try {
                Object[] arguments = createArgsNode.execute(func, new Object[]{argument});
                return invokeNode.execute(frame, arguments);
            } catch (PException e) {
                // getContext() acts as a branch profile
                getContext().setCurrentException(e);
                e.getExceptionObject().reifyException(ensureMaterializeNode().execute(frame, this, true, false), factory());
                return errorResult;
            }
        }

        private MaterializeFrameNode ensureMaterializeNode() {
            if (materializeNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                materializeNode = insert(MaterializeFrameNodeGen.create());
            }
            return materializeNode;
        }
    }

    // -----------------------------------------------------------------------------------------------------------------
    @Builtin(minNumOfPositionalArgs = 2)
    public abstract static class MayRaiseBinaryNode extends PythonBinaryBuiltinNode {
        @Child private CreateArgumentsNode createArgsNode;
        @Child private InvokeNode invokeNode;
        @Child private MaterializeFrameNode materializeNode;

        private final PFunction func;
        private final Object errorResult;

        public MayRaiseBinaryNode(PFunction func, Object errorResult) {
            this.createArgsNode = CreateArgumentsNode.create();
            this.func = func;
            this.invokeNode = InvokeNode.create(func);
            this.errorResult = errorResult;
        }

        @Specialization
        Object doit(VirtualFrame frame, Object arg1, Object arg2) {
            try {
                Object[] arguments = createArgsNode.execute(func, new Object[]{arg1, arg2});
                return invokeNode.execute(frame, arguments);
            } catch (PException e) {
                // getContext() acts as a branch profile
                getContext().setCurrentException(e);
                e.getExceptionObject().reifyException(ensureMaterializeNode().execute(frame, this, true, false), factory());
                return errorResult;
            }
        }

        private MaterializeFrameNode ensureMaterializeNode() {
            if (materializeNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                materializeNode = insert(MaterializeFrameNodeGen.create());
            }
            return materializeNode;
        }
    }

    // -----------------------------------------------------------------------------------------------------------------
    @Builtin(minNumOfPositionalArgs = 3)
    public abstract static class MayRaiseTernaryNode extends PythonTernaryBuiltinNode {
        @Child private CreateArgumentsNode createArgsNode;
        @Child private InvokeNode invokeNode;
        @Child private MaterializeFrameNode materializeNode;

        private final PFunction func;
        private final Object errorResult;

        public MayRaiseTernaryNode(PFunction func, Object errorResult) {
            this.createArgsNode = CreateArgumentsNode.create();
            this.func = func;
            this.invokeNode = InvokeNode.create(func);
            this.errorResult = errorResult;
        }

        @Specialization
        Object doit(VirtualFrame frame, Object arg1, Object arg2, Object arg3) {
            try {
                Object[] arguments = createArgsNode.execute(func, new Object[]{arg1, arg2, arg3});
                return invokeNode.execute(frame, arguments);
            } catch (PException e) {
                // getContext() acts as a branch profile
                getContext().setCurrentException(e);
                e.getExceptionObject().reifyException(ensureMaterializeNode().execute(frame, this, true, false), factory());
                return errorResult;
            }
        }

        private MaterializeFrameNode ensureMaterializeNode() {
            if (materializeNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                materializeNode = insert(MaterializeFrameNodeGen.create());
            }
            return materializeNode;
        }
    }

    // -----------------------------------------------------------------------------------------------------------------
    @Builtin(takesVarArgs = true)
    public static class MayRaiseNode extends PythonBuiltinNode {
        @Child private InvokeNode invokeNode;
        @Child private ReadVarArgsNode readVarargsNode;
        @Child private CreateArgumentsNode createArgsNode;
        @Child private MaterializeFrameNode materializeNode;

        private final PFunction func;
        private final Object errorResult;

        public MayRaiseNode(PFunction callable, Object errorResult) {
            this.readVarargsNode = ReadVarArgsNode.create(0, true);
            this.createArgsNode = CreateArgumentsNode.create();
            this.func = callable;
            this.invokeNode = InvokeNode.create(callable);
            this.errorResult = errorResult;
        }

        @Override
        public final Object execute(VirtualFrame frame) {
            Object[] args = readVarargsNode.executeObjectArray(frame);
            try {
                Object[] arguments = createArgsNode.execute(func, args);
                return invokeNode.execute(frame, arguments);
            } catch (PException e) {
                // getContext() acts as a branch profile
                getContext().setCurrentException(e);
                e.getExceptionObject().reifyException(ensureMaterializeNode().execute(frame, this, true, false), factory());
                return errorResult;
            }
        }

        private MaterializeFrameNode ensureMaterializeNode() {
            if (materializeNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                materializeNode = insert(MaterializeFrameNodeGen.create());
            }
            return materializeNode;
        }

        @Override
        protected ReadArgumentNode[] getArguments() {
            throw new IllegalAccessError();
        }
    }

    // -----------------------------------------------------------------------------------------------------------------
    @GenerateUncached
    public abstract static class IsPointerNode extends com.oracle.graal.python.nodes.PNodeWithContext {

        public abstract boolean execute(PythonNativeWrapper obj);

        @Specialization(assumptions = {"singleContextAssumption()", "nativeObjectsAllManagedAssumption()"})
        boolean doFalse(@SuppressWarnings("unused") PythonNativeWrapper obj) {
            return false;
        }

        @Specialization
        boolean doGeneric(PythonNativeWrapper obj,
                        @Cached GetSpecialSingletonPtrNode getSpecialSingletonPtrNode,
                        @Cached("createClassProfile()") ValueProfile singletonProfile) {
            if (obj.isNative()) {
                return true;
            }
            Object delegate = singletonProfile.profile(obj.getDelegate());
            if (isSpecialSingleton(delegate)) {
                return getSpecialSingletonPtrNode.execute(delegate) != null;
            }
            return false;
        }

        private static boolean isSpecialSingleton(Object delegate) {
            return PythonLanguage.getSingletonNativePtrIdx(delegate) != -1;
        }

        protected static Assumption nativeObjectsAllManagedAssumption() {
            return PythonLanguage.getContextRef().get().getNativeObjectsAllManagedAssumption();
        }

        public static IsPointerNode create() {
            return IsPointerNodeGen.create();
        }

        public static IsPointerNode getUncached() {
            return IsPointerNodeGen.getUncached();
        }
    }

    // -----------------------------------------------------------------------------------------------------------------
    @GenerateUncached
    public abstract static class GetSpecialSingletonPtrNode extends Node {

        public abstract Object execute(Object obj);

        @Specialization
        Object doGeneric(Object obj,
                        @CachedContext(PythonLanguage.class) PythonContext context) {
            if (obj instanceof PythonAbstractObject) {
                return context.getSingletonNativePtr((PythonAbstractObject) obj);
            }
            return null;
        }
    }

    // -----------------------------------------------------------------------------------------------------------------
    @GenerateUncached
    public abstract static class SetSpecialSingletonPtrNode extends Node {

        public abstract void execute(Object obj, Object ptr);

        @Specialization
        void doGeneric(PythonAbstractObject obj, Object ptr,
                        @CachedContext(PythonLanguage.class) PythonContext context) {
            context.setSingletonNativePtr(obj, ptr);
        }
    }

    // -----------------------------------------------------------------------------------------------------------------
    @GenerateUncached
    public abstract static class GetObjectDictNode extends CExtBaseNode {
        public abstract Object execute(Object self);

        @Specialization
        public Object execute(Object self,
                        @Exclusive @Cached ToSulongNode toSulong,
                        @Exclusive @Cached ToJavaNode toJava,
                        @CachedLibrary(limit = "1") InteropLibrary interopLibrary,
                        @Exclusive @Cached ImportCAPISymbolNode importCAPISymbolNode) {
            try {
                Object func = importCAPISymbolNode.execute(FUN_PY_OBJECT_GENERIC_GET_DICT);
                return toJava.execute(interopLibrary.execute(func, toSulong.execute(self)));
            } catch (UnsupportedTypeException | ArityException | UnsupportedMessageException e) {
                CompilerDirectives.transferToInterpreter();
                throw new IllegalStateException("could not run our core function to get the dict of a native object", e);
            }
        }

        public static GetObjectDictNode create() {
            return CExtNodesFactory.GetObjectDictNodeGen.create();
        }

        public static GetObjectDictNode getUncached() {
            return CExtNodesFactory.GetObjectDictNodeGen.getUncached();
        }
    }

    @GenerateUncached
    @TypeSystemReference(PythonTypes.class)
    public abstract static class GetTypeMemberNode extends CExtBaseNode {
        public abstract Object execute(Object obj, String getterFuncName);

        /*
         * A note about the logic here, and why this is fine: the cachedObj is from a particular
         * native context, so we can be sure that the "nativeClassStableAssumption" (which is
         * per-context) is from the context in which this native object was created.
         */
        @Specialization(guards = {"isSameNativeObjectNode.execute(cachedObj, obj)", "memberName == cachedMemberName"}, //
                        limit = "1", //
                        assumptions = {"getNativeClassStableAssumption(cachedObj)", "singleContextAssumption()"})
        public Object doCachedObj(@SuppressWarnings("unused") PythonAbstractNativeObject obj, @SuppressWarnings("unused") String memberName,
                        @SuppressWarnings("unused") @Cached IsSameNativeObjectFastNode isSameNativeObjectNode,
                        @SuppressWarnings("unused") @Cached("memberName") String cachedMemberName,
                        @SuppressWarnings("unused") @Cached("getterFuncName(memberName)") String getterFuncName,
                        @Cached("obj") @SuppressWarnings("unused") PythonAbstractNativeObject cachedObj,
                        @Cached("doSlowPath(obj, getterFuncName)") Object result) {
            return result;
        }

        @Specialization(guards = "memberName == cachedMemberName", limit = "1", replaces = "doCachedObj")
        public Object doCachedMember(Object self, @SuppressWarnings("unused") String memberName,
                        @SuppressWarnings("unused") @Cached("memberName") String cachedMemberName,
                        @Cached("getterFuncName(memberName)") String getterName,
                        @Shared("toSulong") @Cached ToSulongNode toSulong,
                        @Shared("asPythonObject") @Cached AsPythonObjectNode asPythonObject,
                        @Shared("callCapi") @Cached PCallCapiFunction callGetTpDictNode) {
            assert isNativeTypeObject(self);
            return asPythonObject.execute(callGetTpDictNode.call(getterName, toSulong.execute(self)));
        }

        @Specialization(replaces = "doCachedMember")
        public Object doUncached(Object self, String memberName,
                        @Shared("toSulong") @Cached ToSulongNode toSulong,
                        @Shared("asPythonObject") @Cached AsPythonObjectNode asPythonObject,
                        @Shared("callCapi") @Cached PCallCapiFunction callGetTpDictNode) {
            assert isNativeTypeObject(self);
            return asPythonObject.execute(callGetTpDictNode.call(getterFuncName(memberName), toSulong.execute(self)));
        }

        protected Object doSlowPath(Object obj, String getterFuncName) {
            return AsPythonObjectNode.getUncached().execute(PCallCapiFunction.getUncached().call(getterFuncName, ToSulongNode.getUncached().execute(obj)));
        }

        protected String getterFuncName(String memberName) {
            String name = "get_" + memberName;
            assert NativeCAPISymbols.isValid(name) : "invalid native member getter function " + name;
            return name;
        }

        protected Assumption getNativeClassStableAssumption(PythonNativeClass clazz) {
            return PythonLanguage.getContextRef().get().getNativeClassStableAssumption(clazz, true).getAssumption();
        }

        private static boolean isNativeTypeObject(Object self) {
            return IsBuiltinClassProfile.profileClassSlowPath(GetLazyClassNode.getUncached().execute(self), PythonBuiltinClassType.PythonClass);
        }

        public static GetTypeMemberNode create() {
            return GetTypeMemberNodeGen.create();
        }

        public static GetTypeMemberNode getUncached() {
            return GetTypeMemberNodeGen.getUncached();
        }
    }

    @GenerateUncached
    public abstract static class GetNativeNullNode extends Node {

        public abstract Object execute(Object module);

        public final Object execute() {
            return execute(null);
        }

        @Specialization(guards = "module != null")
        static Object getNativeNullWithModule(Object module,
                        @Shared("readAttrNode") @Cached ReadAttributeFromObjectNode readAttrNode) {
            Object wrapper = readAttrNode.execute(module, PythonCextBuiltins.NATIVE_NULL);
            assert wrapper instanceof PythonNativeNull;
            return wrapper;
        }

        @Specialization(guards = "module == null")
        static Object getNativeNullWithoutModule(@SuppressWarnings("unused") Object module,
                        @Shared("readAttrNode") @Cached ReadAttributeFromObjectNode readAttrNode,
                        @CachedContext(PythonLanguage.class) PythonContext context) {
            PythonModule pythonCextModule = context.getCore().lookupBuiltinModule(PythonCextBuiltins.PYTHON_CEXT);
            Object wrapper = readAttrNode.execute(pythonCextModule, PythonCextBuiltins.NATIVE_NULL);
            assert wrapper instanceof PythonNativeNull;
            return wrapper;
        }

    }

    public abstract static class IsSameNativeObjectNode extends CExtBaseNode {

        public abstract boolean execute(PythonAbstractNativeObject left, PythonAbstractNativeObject right);

        protected static boolean doNativeFast(PythonAbstractNativeObject left, PythonAbstractNativeObject right, ValueProfile profile) {
            // This check is a bit dangerous since we cannot be sure about the code that is running.
            // Currently, we assume that the pointer object is a Sulong pointer and for this it's
            // fine.
            return left.equalsProfiled(right, profile);
        }

    }

    @GenerateUncached
    public abstract static class IsSameNativeObjectFastNode extends IsSameNativeObjectNode {

        @Specialization
        boolean doSingleContext(PythonAbstractNativeObject left, PythonAbstractNativeObject right,
                        @Cached("createClassProfile()") ValueProfile foreignTypeProfile) {
            return IsSameNativeObjectNode.doNativeFast(left, right, foreignTypeProfile);
        }
    }

    @GenerateUncached
    public abstract static class IsSameNativeObjectSlowNode extends IsSameNativeObjectNode {

        @Specialization
        boolean doSingleContext(PythonAbstractNativeObject left, PythonAbstractNativeObject right,
                        @Cached("createBinaryProfile()") ConditionProfile isEqualProfile,
                        @Cached("createClassProfile()") ValueProfile foreignTypeProfile,
                        @Cached PointerCompareNode pointerCompareNode) {
            if (isEqualProfile.profile(IsSameNativeObjectNode.doNativeFast(left, right, foreignTypeProfile))) {
                return true;
            }
            return pointerCompareNode.execute(SpecialMethodNames.__EQ__, left, right);
        }
    }
}
