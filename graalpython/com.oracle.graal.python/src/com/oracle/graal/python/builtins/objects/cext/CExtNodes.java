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

import static com.oracle.graal.python.builtins.objects.cext.NativeCAPISymbols.FUN_NATIVE_LONG_TO_JAVA;
import static com.oracle.graal.python.builtins.objects.cext.NativeCAPISymbols.FUN_NATIVE_TO_JAVA;
import static com.oracle.graal.python.builtins.objects.cext.NativeCAPISymbols.FUN_PTR_COMPARE;
import static com.oracle.graal.python.builtins.objects.cext.NativeCAPISymbols.FUN_PY_FLOAT_AS_DOUBLE;
import static com.oracle.graal.python.builtins.objects.cext.NativeCAPISymbols.FUN_PY_TRUFFLE_BYTE_ARRAY_TO_NATIVE;
import static com.oracle.graal.python.builtins.objects.cext.NativeCAPISymbols.FUN_PY_TRUFFLE_STRING_TO_CSTR;
import static com.oracle.graal.python.builtins.objects.cext.NativeCAPISymbols.FUN_WHCAR_SIZE;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__COMPLEX__;
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
import com.oracle.graal.python.builtins.objects.cext.CExtNodesFactory.CextUpcallNodeGen;
import com.oracle.graal.python.builtins.objects.cext.CExtNodesFactory.DirectUpcallNodeGen;
import com.oracle.graal.python.builtins.objects.cext.CExtNodesFactory.GetTypeMemberNodeGen;
import com.oracle.graal.python.builtins.objects.cext.CExtNodesFactory.IsPointerNodeGen;
import com.oracle.graal.python.builtins.objects.cext.CExtNodesFactory.ObjectUpcallNodeGen;
import com.oracle.graal.python.builtins.objects.cext.CExtNodesFactory.PointerCompareNodeGen;
import com.oracle.graal.python.builtins.objects.cext.CExtNodesFactory.ToJavaNodeFactory.ToJavaCachedNodeGen;
import com.oracle.graal.python.builtins.objects.cext.CExtNodesFactory.TransformExceptionToNativeNodeGen;
import com.oracle.graal.python.builtins.objects.cext.DynamicObjectNativeWrapper.PrimitiveNativeWrapper;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes;
import com.oracle.graal.python.builtins.objects.complex.PComplex;
import com.oracle.graal.python.builtins.objects.floats.PFloat;
import com.oracle.graal.python.builtins.objects.frame.PFrame;
import com.oracle.graal.python.builtins.objects.function.PArguments;
import com.oracle.graal.python.builtins.objects.function.PFunction;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.builtins.objects.ints.PInt;
import com.oracle.graal.python.builtins.objects.module.PythonModule;
import com.oracle.graal.python.builtins.objects.str.NativeCharSequence;
import com.oracle.graal.python.builtins.objects.str.PString;
import com.oracle.graal.python.builtins.objects.type.PythonAbstractClass;
import com.oracle.graal.python.builtins.objects.type.PythonManagedClass;
import com.oracle.graal.python.builtins.objects.type.TypeNodes;
import com.oracle.graal.python.builtins.objects.type.TypeNodes.GetMroStorageNode;
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
import com.oracle.graal.python.nodes.call.FunctionInvokeNode;
import com.oracle.graal.python.nodes.call.special.CallBinaryMethodNode;
import com.oracle.graal.python.nodes.call.special.CallTernaryMethodNode;
import com.oracle.graal.python.nodes.call.special.CallUnaryMethodNode;
import com.oracle.graal.python.nodes.call.special.LookupAndCallUnaryNode.LookupAndCallUnaryDynamicNode;
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
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.PythonCore;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.exception.PythonErrorType;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.graal.python.runtime.sequence.storage.MroSequenceStorage;
import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
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
import com.oracle.truffle.api.frame.Frame;
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
import com.oracle.truffle.llvm.spi.ReferenceLibrary;

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

        protected static boolean isMaterialized(DynamicObjectNativeWrapper.PrimitiveNativeWrapper wrapper, PythonNativeWrapperLibrary lib) {
            return wrapper.getMaterializedObject(lib) != null;
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

        protected abstract Object execute(PythonNativeClass object, Object arg);

        protected String getFunctionName() {
            return getTypenamePrefix() + "_subtype_new";
        }

        @Specialization
        protected Object callNativeConstructor(PythonNativeClass object, Object arg,
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

        public final Object call(PythonNativeClass object, double arg) {
            return execute(object, arg);
        }

        public static FloatSubtypeNew create() {
            return CExtNodesFactory.FloatSubtypeNewNodeGen.create();
        }
    }

    public abstract static class TupleSubtypeNew extends SubtypeNew {

        @Child private ToSulongNode toSulongNode;

        @Override
        protected final String getTypenamePrefix() {
            return "tuple";
        }

        public final Object call(PythonNativeClass object, Object arg) {
            if (toSulongNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                toSulongNode = insert(ToSulongNode.create());
            }
            return execute(object, toSulongNode.execute(arg));
        }

        public static TupleSubtypeNew create() {
            return CExtNodesFactory.TupleSubtypeNewNodeGen.create();
        }
    }

    public abstract static class StringSubtypeNew extends SubtypeNew {
        @Child private ToSulongNode toSulongNode;

        @Override
        protected final String getTypenamePrefix() {
            return "unicode";
        }

        public final Object call(PythonNativeClass object, Object arg) {
            if (toSulongNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                toSulongNode = insert(ToSulongNode.create());
            }
            return execute(object, toSulongNode.execute(arg));
        }

        public static StringSubtypeNew create() {
            return CExtNodesFactory.StringSubtypeNewNodeGen.create();
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

        @Specialization(guards = "!isNaN(d)")
        Object doDouble(double d) {
            return DynamicObjectNativeWrapper.PrimitiveNativeWrapper.createDouble(d);
        }

        @Specialization(guards = "isNaN(d)")
        Object doDouble(@SuppressWarnings("unused") double d,
                        @CachedContext(PythonLanguage.class) PythonContext context,
                        @Cached("createCountingProfile()") ConditionProfile noWrapperProfile) {
            PFloat boxed = context.getCore().getNaN();
            DynamicObjectNativeWrapper nativeWrapper = boxed.getNativeWrapper();
            // Use a counting profile since we should enter the branch just once per context.
            if (noWrapperProfile.profile(nativeWrapper == null)) {
                // This deliberately uses 'CompilerDirectives.transferToInterpreter()' because this
                // code will happen just once per context.
                CompilerDirectives.transferToInterpreter();
                nativeWrapper = DynamicObjectNativeWrapper.PrimitiveNativeWrapper.createDouble(Double.NaN);
                boxed.setNativeWrapper(nativeWrapper);
            }
            return nativeWrapper;
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

        protected static boolean isNaN(double d) {
            return Double.isNaN(d);
        }

        public static ToSulongNode create() {
            return CExtNodesFactory.ToSulongNodeGen.create();
        }

        public static ToSulongNode getUncached() {
            return CExtNodesFactory.ToSulongNodeGen.getUncached();
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

        @Specialization(guards = "!isPrimitiveNativeWrapper(object)", limit = "1")
        Object doNativeWrapper(PythonNativeWrapper object,
                        @CachedLibrary("object") PythonNativeWrapperLibrary lib) {
            return lib.getDelegate(object);
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
                return PythonNativeWrapperLibrary.getUncached().getDelegate((PythonNativeWrapper) object);
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

        @Specialization(guards = {"!isMaterialized(object, lib)", "object.isBool()"}, limit = "1")
        PInt doBoolNativeWrapper(DynamicObjectNativeWrapper.PrimitiveNativeWrapper object,
                        @SuppressWarnings("unused") @CachedLibrary("object") PythonNativeWrapperLibrary lib,
                        @CachedContext(PythonLanguage.class) PythonContext context) {
            // Special case for True and False: use singletons
            PythonCore core = context.getCore();
            PInt materializedInt = object.getBool() ? core.getTrue() : core.getFalse();
            object.setMaterializedObject(materializedInt);

            // If the singleton already has a native wrapper, we may need to update the pointer
            // of wrapper 'object' since the native could code see the same pointer.
            if (materializedInt.getNativeWrapper() != null) {
                object.setNativePointer(lib.getNativePointer(materializedInt.getNativeWrapper()));
            } else {
                materializedInt.setNativeWrapper(object);
            }
            return materializedInt;
        }

        @Specialization(guards = {"!isMaterialized(object, lib)", "object.isByte()"}, limit = "1")
        PInt doByteNativeWrapper(DynamicObjectNativeWrapper.PrimitiveNativeWrapper object,
                        @SuppressWarnings("unused") @CachedLibrary("object") PythonNativeWrapperLibrary lib,
                        @Shared("factory") @Cached PythonObjectFactory factory) {
            PInt materializedInt = factory.createInt(object.getByte());
            object.setMaterializedObject(materializedInt);
            materializedInt.setNativeWrapper(object);
            return materializedInt;
        }

        @Specialization(guards = {"!isMaterialized(object, lib)", "object.isInt()"}, limit = "1")
        PInt doIntNativeWrapper(DynamicObjectNativeWrapper.PrimitiveNativeWrapper object,
                        @SuppressWarnings("unused") @CachedLibrary("object") PythonNativeWrapperLibrary lib,
                        @Shared("factory") @Cached PythonObjectFactory factory) {
            PInt materializedInt = factory.createInt(object.getInt());
            object.setMaterializedObject(materializedInt);
            materializedInt.setNativeWrapper(object);
            return materializedInt;
        }

        @Specialization(guards = {"!isMaterialized(object, lib)", "object.isLong()"}, limit = "1")
        PInt doLongNativeWrapper(DynamicObjectNativeWrapper.PrimitiveNativeWrapper object,
                        @SuppressWarnings("unused") @CachedLibrary("object") PythonNativeWrapperLibrary lib,
                        @Shared("factory") @Cached PythonObjectFactory factory) {
            PInt materializedInt = factory.createInt(object.getLong());
            object.setMaterializedObject(materializedInt);
            materializedInt.setNativeWrapper(object);
            return materializedInt;
        }

        @Specialization(guards = {"!isMaterialized(object, lib)", "object.isDouble()", "!isNaN(object)"}, limit = "1")
        PFloat doDoubleNativeWrapper(DynamicObjectNativeWrapper.PrimitiveNativeWrapper object,
                        @SuppressWarnings("unused") @CachedLibrary("object") PythonNativeWrapperLibrary lib,
                        @Shared("factory") @Cached PythonObjectFactory factory) {
            PFloat materializedInt = factory.createFloat(object.getDouble());
            materializedInt.setNativeWrapper(object);
            object.setMaterializedObject(materializedInt);
            return materializedInt;
        }

        @Specialization(guards = {"!isMaterialized(object, lib)", "object.isDouble()", "isNaN(object)"}, limit = "1")
        PFloat doDoubleNativeWrapperNaN(DynamicObjectNativeWrapper.PrimitiveNativeWrapper object,
                        @SuppressWarnings("unused") @CachedLibrary("object") PythonNativeWrapperLibrary lib,
                        @CachedContext(PythonLanguage.class) PythonContext context) {
            // Special case for double NaN: use singleton
            PFloat materializedFloat = context.getCore().getNaN();
            object.setMaterializedObject(materializedFloat);

            // If the NaN singleton already has a native wrapper, we may need to update the pointer
            // of wrapper 'object' since the native code should see the same pointer.
            if (materializedFloat.getNativeWrapper() != null) {
                object.setNativePointer(lib.getNativePointer(materializedFloat.getNativeWrapper()));
            } else {
                materializedFloat.setNativeWrapper(object);
            }
            return materializedFloat;
        }

        @Specialization(guards = {"object.getClass() == cachedClass", "isMaterialized(object, lib)"}, limit = "1")
        Object doMaterialized(DynamicObjectNativeWrapper.PrimitiveNativeWrapper object,
                        @CachedLibrary("object") PythonNativeWrapperLibrary lib,
                        @SuppressWarnings("unused") @Cached("object.getClass()") Class<? extends DynamicObjectNativeWrapper.PrimitiveNativeWrapper> cachedClass) {
            return lib.getDelegate(CompilerDirectives.castExact(object, cachedClass));
        }

        @Specialization(guards = {"!isPrimitiveNativeWrapper(object)", "object.getClass() == cachedClass"}, limit = "3")
        Object doNativeWrapper(PythonNativeWrapper object,
                        @CachedLibrary("object") PythonNativeWrapperLibrary lib,
                        @SuppressWarnings("unused") @Cached("object.getClass()") Class<? extends PythonNativeWrapper> cachedClass) {
            return lib.getDelegate(CompilerDirectives.castExact(object, cachedClass));
        }

        @Specialization(guards = "!isPrimitiveNativeWrapper(object)", replaces = "doNativeWrapper", limit = "1")
        Object doNativeWrapperGeneric(PythonNativeWrapper object,
                        @CachedLibrary("object") PythonNativeWrapperLibrary lib) {
            return lib.getDelegate(object);
        }

        protected static boolean isPrimitiveNativeWrapper(PythonNativeWrapper object) {
            return object instanceof DynamicObjectNativeWrapper.PrimitiveNativeWrapper;
        }

        protected static boolean isNaN(PrimitiveNativeWrapper object) {
            assert object.isDouble();
            return Double.isNaN(object.getDouble());
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
        public abstract Object execute(Object charPtr);

        @Specialization
        PString execute(Object charPtr,
                        @Cached PythonObjectFactory factory) {
            return factory.createString(new NativeCharSequence(charPtr));
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

        final Object[] execute(Object[] args) {
            return execute(args, 0);
        }

        abstract Object[] execute(Object[] args, int offset);

        @Specialization(guards = {"args.length == cachedLength", "cachedLength < 5"}, limit = "5")
        @ExplodeLoop
        Object[] cached(Object[] args, @SuppressWarnings("unused") int offset,
                        @Cached("args.length") int cachedLength,
                        @Cached("offset") int cachedOffset,
                        @Exclusive @Cached AsPythonObjectNode toJavaNode) {
            Object[] output = new Object[cachedLength - cachedOffset];
            for (int i = 0; i < cachedLength - cachedOffset; i++) {
                output[i] = toJavaNode.execute(args[i + cachedOffset]);
            }
            return output;
        }

        @Specialization(replaces = "cached")
        Object[] uncached(Object[] args, int offset,
                        @Exclusive @Cached AsPythonObjectNode toJavaNode) {
            int len = args.length - offset;
            Object[] output = new Object[len];
            for (int i = 0; i < len; i++) {
                output[i] = toJavaNode.execute(args[i + offset]);
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
        public abstract Object execute(VirtualFrame frame, Object[] args);

        @Specialization(guards = "args.length == 1")
        Object upcall0(VirtualFrame frame, Object[] args,
                        @Cached("create()") CallNode callNode) {
            return callNode.execute(frame, args[0], new Object[0], new PKeyword[0]);
        }

        @Specialization(guards = "args.length == 2")
        Object upcall1(VirtualFrame frame, Object[] args,
                        @Cached CallUnaryMethodNode callNode,
                        @Cached CExtNodes.AsPythonObjectNode toJavaNode) {
            return callNode.executeObject(frame, args[0], toJavaNode.execute(args[1]));
        }

        @Specialization(guards = "args.length == 3")
        Object upcall2(VirtualFrame frame, Object[] args,
                        @Cached CallBinaryMethodNode callNode,
                        @Shared("allToJavaNode") @Cached AllToJavaNode allToJavaNode) {
            Object[] converted = allToJavaNode.execute(args, 1);
            return callNode.executeObject(frame, args[0], converted[0], converted[1]);
        }

        @Specialization(guards = "args.length == 4")
        Object upcall3(VirtualFrame frame, Object[] args,
                        @Cached CallTernaryMethodNode callNode,
                        @Shared("allToJavaNode") @Cached AllToJavaNode allToJavaNode) {
            Object[] converted = allToJavaNode.execute(args, 1);
            return callNode.execute(frame, args[0], converted[0], converted[1], converted[2]);
        }

        @Specialization(replaces = {"upcall0", "upcall1", "upcall2", "upcall3"})
        Object upcall(VirtualFrame frame, Object[] args,
                        @Cached CallNode callNode,
                        @Shared("allToJavaNode") @Cached AllToJavaNode allToJavaNode) {
            Object[] converted = allToJavaNode.execute(args, 1);
            return callNode.execute(frame, args[0], converted, new PKeyword[0]);
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

        public abstract Object execute(VirtualFrame frame, Object cextModule, Object[] args);

        @Specialization(guards = "args.length == 1")
        Object upcall0(VirtualFrame frame, Object cextModule, Object[] args,
                        @Cached CallNode callNode,
                        @Shared("getAttrNode") @Cached ReadAttributeFromObjectNode getAttrNode) {
            assert args[0] instanceof String;
            Object callable = getAttrNode.execute(cextModule, args[0]);
            return callNode.execute(frame, callable, new Object[0], PKeyword.EMPTY_KEYWORDS);
        }

        @Specialization(guards = "args.length == 2")
        Object upcall1(VirtualFrame frame, Object cextModule, Object[] args,
                        @Cached CallUnaryMethodNode callNode,
                        @Cached CExtNodes.AsPythonObjectNode toJavaNode,
                        @Shared("getAttrNode") @Cached ReadAttributeFromObjectNode getAttrNode) {
            assert args[0] instanceof String;
            Object callable = getAttrNode.execute(cextModule, args[0]);
            return callNode.executeObject(frame, callable, toJavaNode.execute(args[1]));
        }

        @Specialization(guards = "args.length == 3")
        Object upcall2(VirtualFrame frame, Object cextModule, Object[] args,
                        @Cached CallBinaryMethodNode callNode,
                        @Shared("allToJavaNode") @Cached AllToJavaNode allToJavaNode,
                        @Shared("getAttrNode") @Cached ReadAttributeFromObjectNode getAttrNode) {
            Object[] converted = allToJavaNode.execute(args, 1);
            assert args[0] instanceof String;
            Object callable = getAttrNode.execute(cextModule, args[0]);
            return callNode.executeObject(frame, callable, converted[0], converted[1]);
        }

        @Specialization(guards = "args.length == 4")
        Object upcall3(VirtualFrame frame, Object cextModule, Object[] args,
                        @Cached CallTernaryMethodNode callNode,
                        @Shared("allToJavaNode") @Cached AllToJavaNode allToJavaNode,
                        @Shared("getAttrNode") @Cached ReadAttributeFromObjectNode getAttrNode) {
            Object[] converted = allToJavaNode.execute(args, 1);
            assert args[0] instanceof String;
            Object callable = getAttrNode.execute(cextModule, args[0]);
            return callNode.execute(frame, callable, converted[0], converted[1], converted[2]);
        }

        @Specialization(replaces = {"upcall0", "upcall1", "upcall2", "upcall3"})
        Object upcall(VirtualFrame frame, Object cextModule, Object[] args,
                        @Cached CallNode callNode,
                        @Shared("allToJavaNode") @Cached AllToJavaNode allToJavaNode,
                        @Shared("getAttrNode") @Cached ReadAttributeFromObjectNode getAttrNode) {
            Object[] converted = allToJavaNode.execute(args, 1);
            assert args[0] instanceof String;
            Object callable = getAttrNode.execute(cextModule, args[0]);
            return callNode.execute(frame, callable, converted, PKeyword.EMPTY_KEYWORDS);
        }
    }

    // -----------------------------------------------------------------------------------------------------------------

    /**
     * Specializes on the arity of the call and tries to do a builtin call if possible, otherwise a
     * generic call is done. The arguments array must have at least two element: {@code args[0]} is
     * the receiver (e.g. the module) and {@code args[1]} is the member to call.
     */
    public abstract static class ObjectUpcallNode extends PNodeWithContext {
        public static ObjectUpcallNode create() {
            return ObjectUpcallNodeGen.create();
        }

        /**
         * The {@code args} array must contain the receiver at {@code args[0]} and the member at
         * {@code args[1]}.
         */
        public abstract Object execute(VirtualFrame frame, Object[] args);

        @Specialization(guards = "args.length == 2")
        Object upcall0(VirtualFrame frame, Object[] args,
                        @Cached CallNode callNode,
                        @Cached CExtNodes.AsPythonObjectNode receiverToJavaNode,
                        @Shared("getAttrNode") @Cached GetAttrNode getAttrNode) {
            Object receiver = receiverToJavaNode.execute(args[0]);
            assert PGuards.isString(args[1]);
            Object callable = getAttrNode.execute(frame, receiver, args[1], PNone.NO_VALUE);
            return callNode.execute(frame, callable, new Object[0], PKeyword.EMPTY_KEYWORDS);
        }

        @Specialization(guards = "args.length == 3")
        Object upcall1(VirtualFrame frame, Object[] args,
                        @Cached CallUnaryMethodNode callNode,
                        @Cached CExtNodes.AsPythonObjectNode receiverToJavaNode,
                        @Cached CExtNodes.AsPythonObjectNode argToJavaNode,
                        @Shared("getAttrNode") @Cached GetAttrNode getAttrNode) {
            Object receiver = receiverToJavaNode.execute(args[0]);
            assert PGuards.isString(args[1]);
            Object callable = getAttrNode.execute(frame, receiver, args[1], PNone.NO_VALUE);
            return callNode.executeObject(frame, callable, argToJavaNode.execute(args[2]));
        }

        @Specialization(guards = "args.length == 4")
        Object upcall2(VirtualFrame frame, Object[] args,
                        @Cached CallBinaryMethodNode callNode,
                        @Cached CExtNodes.AsPythonObjectNode receiverToJavaNode,
                        @Shared("allToJavaNode") @Cached AllToJavaNode allToJavaNode,
                        @Shared("getAttrNode") @Cached GetAttrNode getAttrNode) {
            Object[] converted = allToJavaNode.execute(args, 2);
            Object receiver = receiverToJavaNode.execute(args[0]);
            assert PGuards.isString(args[1]);
            Object callable = getAttrNode.execute(frame, receiver, args[1], PNone.NO_VALUE);
            return callNode.executeObject(frame, callable, converted[0], converted[1]);
        }

        @Specialization(guards = "args.length == 5")
        Object upcall3(VirtualFrame frame, Object[] args,
                        @Cached CallTernaryMethodNode callNode,
                        @Cached CExtNodes.AsPythonObjectNode receiverToJavaNode,
                        @Shared("allToJavaNode") @Cached AllToJavaNode allToJavaNode,
                        @Shared("getAttrNode") @Cached GetAttrNode getAttrNode) {
            Object[] converted = allToJavaNode.execute(args, 2);
            Object receiver = receiverToJavaNode.execute(args[0]);
            assert PGuards.isString(args[1]);
            Object callable = getAttrNode.execute(frame, receiver, args[1], PNone.NO_VALUE);
            return callNode.execute(frame, callable, converted[0], converted[1], converted[2]);
        }

        @Specialization(replaces = {"upcall0", "upcall1", "upcall2", "upcall3"})
        Object upcall(VirtualFrame frame, Object[] args,
                        @Cached CallNode callNode,
                        @Cached CExtNodes.AsPythonObjectNode receiverToJavaNode,
                        @Shared("allToJavaNode") @Cached AllToJavaNode allToJavaNode,
                        @Shared("getAttrNode") @Cached GetAttrNode getAttrNode) {
            // we needs at least a receiver and a member name
            assert args.length >= 2;
            Object[] converted = allToJavaNode.execute(args, 2);
            Object receiver = receiverToJavaNode.execute(args[0]);
            assert PGuards.isString(args[1]);
            Object callable = getAttrNode.execute(frame, receiver, args[1], PNone.NO_VALUE);
            return callNode.execute(frame, callable, converted, PKeyword.EMPTY_KEYWORDS);
        }
    }

    // -----------------------------------------------------------------------------------------------------------------

    /**
     * Converts a Python object to a
     * {@link com.oracle.graal.python.builtins.objects.complex.PComplex} .<br/>
     * This node is, for example, used to implement {@code PyComplex_AsCComplex} and does coercion
     * and may raise a Python exception if coercion fails.
     */
    @GenerateUncached
    @ImportStatic(SpecialMethodNames.class)
    public abstract static class AsNativeComplexNode extends PNodeWithContext {
        public abstract PComplex execute(boolean arg);

        public abstract PComplex execute(int arg);

        public abstract PComplex execute(long arg);

        public abstract PComplex execute(double arg);

        public abstract PComplex execute(Object arg);

        @Specialization
        PComplex doPComplex(PComplex value) {
            return value;
        }

        @Specialization
        PComplex doBoolean(boolean value,
                        @Shared("factory") @Cached PythonObjectFactory factory) {
            return factory.createComplex(value ? 1.0 : 0.0, 0.0);
        }

        @Specialization
        PComplex doInt(int value,
                        @Shared("factory") @Cached PythonObjectFactory factory) {
            return factory.createComplex(value, 0.0);
        }

        @Specialization
        PComplex doLong(long value,
                        @Shared("factory") @Cached PythonObjectFactory factory) {
            return factory.createComplex(value, 0.0);
        }

        @Specialization
        PComplex doDouble(double value,
                        @Shared("factory") @Cached PythonObjectFactory factory) {
            return factory.createComplex(value, 0.0);
        }

        @Specialization
        PComplex doPInt(PInt value,
                        @Shared("factory") @Cached PythonObjectFactory factory) {
            return factory.createComplex(value.doubleValue(), 0.0);
        }

        @Specialization
        PComplex doPFloat(PFloat value,
                        @Shared("factory") @Cached PythonObjectFactory factory) {
            return factory.createComplex(value.getValue(), 0.0);
        }

        @Specialization(replaces = {"doPComplex", "doBoolean", "doInt", "doLong", "doDouble", "doPInt", "doPFloat"})
        PComplex runGeneric(Object value,
                        @Cached LookupAndCallUnaryDynamicNode callFloatFunc,
                        @Cached AsNativeDoubleNode asDoubleNode,
                        @Cached PythonObjectFactory factory,
                        @Cached PRaiseNode raiseNode) {
            Object result = callFloatFunc.executeObject(value, __COMPLEX__);
            // TODO(fa) according to CPython's 'PyComplex_AsCComplex', they still allow subclasses
            // of PComplex
            if (result == PNone.NO_VALUE) {
                throw raiseNode.raise(PythonErrorType.TypeError, "__complex__ returned non-complex (type %p)", value);
            } else if (result instanceof PComplex) {
                return (PComplex) result;
            }
            return factory.createComplex(asDoubleNode.execute(value), 0.0);
        }
    }

    // -----------------------------------------------------------------------------------------------------------------

    /**
     * Casts a Python object to a Java double value without doing any coercion, i.e., it does not
     * call any magic method like {@code __float__}.<br/>
     * The semantics is like a Java type cast and therefore lossy.<br/>
     * As an optimization, this node can also unwrap {@code PrimitiveNativeWrapper} instances to
     * avoid eager and explicit conversion.
     */
    @GenerateUncached
    public abstract static class CastToJavaDoubleNode extends PNodeWithContext {
        public abstract double execute(boolean arg);

        public abstract double execute(int arg);

        public abstract double execute(long arg);

        public abstract double execute(double arg);

        public abstract double execute(Object arg);

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
    }

    /**
     * Converts a Python object to a Java double value (which is compatible to a C double).<br/>
     * This node is, for example, used to implement {@code PyFloat_AsDouble} or similar C API
     * functions and does coercion and may raise a Python exception if coercion fails.
     */
    @GenerateUncached
    @ImportStatic(SpecialMethodNames.class)
    public abstract static class AsNativeDoubleNode extends PNodeWithContext {
        public abstract double execute(boolean arg);

        public abstract double execute(int arg);

        public abstract double execute(long arg);

        public abstract double execute(double arg);

        public abstract double execute(Object arg);

        @Specialization
        double doBooleam(boolean value) {
            return value ? 1.0 : 0.0;
        }

        @Specialization
        double doInt(int value) {
            return value;
        }

        @Specialization
        double doLong(long value) {
            return value;
        }

        @Specialization
        double doDouble(double value) {
            return value;
        }

        @Specialization
        double doPInt(PInt value) {
            return value.doubleValue();
        }

        @Specialization
        double doPFloat(PFloat value) {
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

        @Specialization
        double runGeneric(PythonAbstractObject value,
                        @Cached LookupAndCallUnaryDynamicNode callFloatFunc,
                        @Cached GetLazyClassNode getClassNode,
                        @Cached IsBuiltinClassProfile classProfile,
                        @Cached CastToJavaDoubleNode castToJavaDoubleNode,
                        @Cached PRaiseNode raiseNode) {
            // IMPORTANT: this should implement the behavior like 'PyFloat_AsDouble'. So, if it is a
            // float object, use the value and do *NOT* call '__float__'.
            if (PGuards.isPFloat(value)) {
                return ((PFloat) value).getValue();
            }

            Object result = callFloatFunc.executeObject(value, __FLOAT__);
            // TODO(fa) according to CPython's 'PyFloat_AsDouble', they still allow subclasses of
            // PFloat
            if (classProfile.profileClass(getClassNode.execute(result), PythonBuiltinClassType.PFloat)) {
                return castToJavaDoubleNode.execute(result);
            }
            throw raiseNode.raise(PythonErrorType.TypeError, "%p.%s returned non-float (type %p)", value, __FLOAT__, result);
        }
    }

    // -----------------------------------------------------------------------------------------------------------------

    /**
     * Casts a Python object to a Java long value without doing any coercion, i.e., it does not call
     * any magic method like {@code __index__} or {@code __int__}.<br/>
     * The semantics is like a Java type cast and therefore lossy.<br/>
     * As an optimization, this node can also unwrap {@code PrimitiveNativeWrapper} instances to
     * avoid eager and explicit conversion.
     */
    @GenerateUncached
    public abstract static class CastToNativeLongNode extends PNodeWithContext {
        public abstract long execute(boolean arg);

        public abstract long execute(byte arg);

        public abstract long execute(int arg);

        public abstract long execute(long arg);

        public abstract long execute(double arg);

        public abstract long execute(Object arg);

        @Specialization(guards = "value.length() == 1")
        long doString(String value) {
            return value.charAt(0);
        }

        @Specialization
        long doBoolean(boolean value) {
            return value ? 1 : 0;
        }

        @Specialization
        long doByte(byte value) {
            return value;
        }

        @Specialization
        long doInt(int value) {
            return value;
        }

        @Specialization
        long doLong(long value) {
            return value;
        }

        @Specialization
        long doDouble(double value) {
            return (long) value;
        }

        @Specialization
        long doPInt(PInt value) {
            return value.longValue();
        }

        @Specialization
        long doPFloat(PFloat value) {
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

        @Specialization(limit = "1")
        long run(PythonNativeWrapper value,
                        @CachedLibrary("value") PythonNativeWrapperLibrary lib,
                        @Cached CastToNativeLongNode recursive) {
            // TODO(fa) this specialization should eventually go away
            return recursive.execute(lib.getDelegate(value));
        }

        static boolean isNativeWrapper(Object object) {
            return object instanceof PythonNativeWrapper;
        }
    }

    /**
     * Converts a Python object (i.e. {@code PyObject*}) to a C integer value ({@code int} or
     * {@code long}).<br/>
     * This node is used to implement {@code PyLong_AsLong} or similar C API functions and does
     * coercion and may raise a Python exception if coercion fails.
     */
    @GenerateUncached
    @ImportStatic(PGuards.class)
    public abstract static class AsNativePrimitiveNode extends Node {

        public final int toInt32(Object value, boolean exact) {
            return (int) execute(value, 1, 4, exact);
        }

        public final int toUInt32(Object value, boolean exact) {
            return (int) execute(value, 0, 4, exact);
        }

        public final long toInt64(Object value, boolean exact) {
            return (long) execute(value, 1, 8, exact);
        }

        public final long toUInt64(Object value, boolean exact) {
            return (long) execute(value, 0, 8, exact);
        }

        public abstract Object execute(byte value, int signed, int targetTypeSize, boolean exact);

        public abstract Object execute(int value, int signed, int targetTypeSize, boolean exact);

        public abstract Object execute(long value, int signed, int targetTypeSize, boolean exact);

        public abstract Object execute(Object value, int signed, int targetTypeSize, boolean exact);

        @Specialization(guards = "targetTypeSize == 4")
        @SuppressWarnings("unused")
        static int doIntToInt32(int obj, int signed, int targetTypeSize, boolean exact) {
            // n.b. even if an unsigned is requested, it does not matter because the unsigned
            // interpretation is done in C code.
            return obj;
        }

        @Specialization(guards = "targetTypeSize == 8")
        @SuppressWarnings("unused")
        static long doIntToInt64(int obj, int signed, int targetTypeSize, boolean exact) {
            return obj;
        }

        @Specialization(guards = {"targetTypeSize != 4", "targetTypeSize != 8"})
        @SuppressWarnings("unused")
        static int doIntToOther(int obj, int signed, int targetTypeSize, boolean exact,
                        @Shared("raiseNode") @Cached PRaiseNode raiseNode) {
            throw raiseNode.raise(PythonErrorType.SystemError, "Unsupported target size: %d", targetTypeSize);
        }

        @Specialization(guards = "targetTypeSize == 4")
        @SuppressWarnings("unused")
        static int doLongToInt32(long obj, int signed, int targetTypeSize, boolean exact,
                        @Shared("raiseNode") @Cached PRaiseNode raiseNode) {
            throw raiseNode.raise(PythonErrorType.OverflowError, "Python int too large to convert to %s-byte C type", targetTypeSize);
        }

        @Specialization(guards = "targetTypeSize == 8")
        @SuppressWarnings("unused")
        static long doLongToInt64(long obj, int signed, int targetTypeSize, boolean exact) {
            return obj;
        }

        @Specialization(guards = "targetTypeSize == 8")
        @SuppressWarnings("unused")
        static Object doVoidPtrToI64(PythonNativeVoidPtr obj, int signed, int targetTypeSize, boolean exact) {
            return obj;
        }

        @Specialization(guards = {"targetTypeSize != 4", "targetTypeSize != 8"})
        @SuppressWarnings("unused")
        static int doPInt(long obj, int signed, int targetTypeSize, boolean exact,
                        @Shared("raiseNode") @Cached PRaiseNode raiseNode) {
            throw raiseNode.raise(PythonErrorType.SystemError, "Unsupported target size: %d", targetTypeSize);
        }

        @Specialization(guards = {"exact", "targetTypeSize == 4"})
        static int doPIntToInt32(PInt obj, int signed, @SuppressWarnings("unused") int targetTypeSize, boolean exact,
                        @Exclusive @Cached BranchProfile errorProfile,
                        @Shared("raiseNode") @Cached PRaiseNode raiseNode) {
            if (signed != 0) {
                try {
                    return obj.intValueExact();
                } catch (ArithmeticException e) {
                    // fall through
                }
            } else if (!exact || obj.bitCount() <= 32) {
                return obj.intValue();
            }
            errorProfile.enter();
            throw raiseNode.raise(PythonErrorType.OverflowError, "Python int too large to convert to %s-byte C type", targetTypeSize);
        }

        @Specialization(guards = {"exact", "targetTypeSize == 8"})
        static long doPIntToInt64(PInt obj, int signed, @SuppressWarnings("unused") int targetTypeSize, boolean exact,
                        @Exclusive @Cached BranchProfile errorProfile,
                        @Shared("raiseNode") @Cached PRaiseNode raiseNode) {
            if (signed != 0) {
                try {
                    return obj.longValueExact();
                } catch (ArithmeticException e) {
                    // fall through
                }
            } else if (!exact || obj.bitCount() <= 64) {
                return obj.longValue();
            }
            errorProfile.enter();
            throw raiseNode.raise(PythonErrorType.OverflowError, "Python int too large to convert to %s-byte C type", targetTypeSize);
        }

        @Specialization(guards = {"!exact", "targetTypeSize == 4"})
        @SuppressWarnings("unused")
        static int doPIntToInt32Lossy(PInt obj, int signed, int targetTypeSize, boolean exact) {
            return obj.intValue();
        }

        @Specialization(guards = {"!exact", "targetTypeSize == 8"})
        @SuppressWarnings("unused")
        static long doPIntToInt64Lossy(PInt obj, int signed, int targetTypeSize, boolean exact) {
            return obj.longValue();
        }

        @Specialization(guards = {"isIntegerType(obj)", "targetTypeSize != 4", "targetTypeSize != 8"})
        @SuppressWarnings("unused")
        static int doError(Object obj, int signed, int targetTypeSize, boolean exact,
                        @Shared("raiseNode") @Cached PRaiseNode raiseNode) {
            throw raiseNode.raise(PythonErrorType.SystemError, "Unsupported target size: %d", targetTypeSize);
        }

        @Specialization(replaces = {"doIntToInt32", "doIntToInt64", "doIntToOther", "doLongToInt32", "doLongToInt64", "doVoidPtrToI64", "doPIntToInt32", "doPIntToInt64"})
        static Object doGeneric(Object obj, @SuppressWarnings("unused") int signed, int targetTypeSize, boolean exact,
                        @Cached LookupAndCallUnaryDynamicNode callIntNode,
                        @Cached AsNativePrimitiveNode recursive,
                        @Shared("raiseNode") @Cached PRaiseNode raiseNode) {

            Object result = callIntNode.executeObject(obj, SpecialMethodNames.__INT__);
            if (result == PNone.NO_VALUE) {
                throw raiseNode.raise(PythonErrorType.TypeError, "an integer is required (got type %p)", result);
            }
            // n.b. this check is important to avoid endless recursions; it will ensure that
            // 'doGeneric' is not triggered in the recursive node
            if (!(isIntegerType(result))) {
                throw raiseNode.raise(PythonErrorType.TypeError, "__int__ returned non-int (type %p)", result);
            }
            return recursive.execute(result, signed, targetTypeSize, exact);
        }

        static boolean isIntegerType(Object obj) {
            return PGuards.isInteger(obj) || PGuards.isPInt(obj) || obj instanceof PythonNativeVoidPtr;
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
        @Child private FunctionInvokeNode invokeNode;
        @Child private TransformExceptionToNativeNode transformExceptionToNativeNode;
        @CompilationFinal private ConditionProfile frameProfile;

        private final PFunction func;
        private final Object errorResult;

        public MayRaiseUnaryNode(PFunction func, Object errorResult) {
            this.createArgsNode = CreateArgumentsNode.create();
            this.func = func;
            this.invokeNode = FunctionInvokeNode.create(func);
            this.errorResult = errorResult;
        }

        @Specialization
        Object doit(VirtualFrame frame, Object argument) {
            try {
                Object[] arguments = createArgsNode.execute(func, new Object[]{argument});
                return invokeNode.execute(frame, arguments);
            } catch (PException e) {
                // transformExceptionToNativeNode acts as a branch profile
                ensureTransformExceptionToNativeNode().execute(frame, e);
                return errorResult;
            }
        }

        private TransformExceptionToNativeNode ensureTransformExceptionToNativeNode() {
            if (transformExceptionToNativeNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                transformExceptionToNativeNode = insert(TransformExceptionToNativeNodeGen.create());
            }
            return transformExceptionToNativeNode;
        }
    }

    // -----------------------------------------------------------------------------------------------------------------
    @Builtin(minNumOfPositionalArgs = 2)
    public abstract static class MayRaiseBinaryNode extends PythonBinaryBuiltinNode {
        @Child private CreateArgumentsNode createArgsNode;
        @Child private FunctionInvokeNode invokeNode;
        @Child private TransformExceptionToNativeNode transformExceptionToNativeNode;
        @CompilationFinal private ConditionProfile frameProfile;

        private final PFunction func;
        private final Object errorResult;

        public MayRaiseBinaryNode(PFunction func, Object errorResult) {
            this.createArgsNode = CreateArgumentsNode.create();
            this.func = func;
            this.invokeNode = FunctionInvokeNode.create(func);
            this.errorResult = errorResult;
        }

        @Specialization
        Object doit(VirtualFrame frame, Object arg1, Object arg2) {
            try {
                Object[] arguments = createArgsNode.execute(func, new Object[]{arg1, arg2});
                return invokeNode.execute(frame, arguments);
            } catch (PException e) {
                // transformExceptionToNativeNode acts as a branch profile
                ensureTransformExceptionToNativeNode().execute(frame, e);
                return errorResult;
            }
        }

        private TransformExceptionToNativeNode ensureTransformExceptionToNativeNode() {
            if (transformExceptionToNativeNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                transformExceptionToNativeNode = insert(TransformExceptionToNativeNodeGen.create());
            }
            return transformExceptionToNativeNode;
        }
    }

    // -----------------------------------------------------------------------------------------------------------------
    @Builtin(minNumOfPositionalArgs = 3)
    public abstract static class MayRaiseTernaryNode extends PythonTernaryBuiltinNode {
        @Child private CreateArgumentsNode createArgsNode;
        @Child private FunctionInvokeNode invokeNode;
        @Child private TransformExceptionToNativeNode transformExceptionToNativeNode;
        @CompilationFinal private ConditionProfile frameProfile;

        private final PFunction func;
        private final Object errorResult;

        public MayRaiseTernaryNode(PFunction func, Object errorResult) {
            this.createArgsNode = CreateArgumentsNode.create();
            this.func = func;
            this.invokeNode = FunctionInvokeNode.create(func);
            this.errorResult = errorResult;
        }

        @Specialization
        Object doit(VirtualFrame frame, Object arg1, Object arg2, Object arg3) {
            try {
                Object[] arguments = createArgsNode.execute(func, new Object[]{arg1, arg2, arg3});
                return invokeNode.execute(frame, arguments);
            } catch (PException e) {
                // transformExceptionToNativeNode acts as a branch profile
                ensureTransformExceptionToNativeNode().execute(frame, e);
                return errorResult;
            }
        }

        private TransformExceptionToNativeNode ensureTransformExceptionToNativeNode() {
            if (transformExceptionToNativeNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                transformExceptionToNativeNode = insert(TransformExceptionToNativeNodeGen.create());
            }
            return transformExceptionToNativeNode;
        }
    }

    // -----------------------------------------------------------------------------------------------------------------
    @Builtin(takesVarArgs = true)
    public static class MayRaiseNode extends PythonBuiltinNode {
        @Child private FunctionInvokeNode invokeNode;
        @Child private ReadVarArgsNode readVarargsNode;
        @Child private CreateArgumentsNode createArgsNode;
        @Child private TransformExceptionToNativeNode transformExceptionToNativeNode;
        @CompilationFinal private ConditionProfile frameProfile;

        private final PFunction func;
        private final Object errorResult;

        public MayRaiseNode(PFunction callable, Object errorResult) {
            this.readVarargsNode = ReadVarArgsNode.create(0, true);
            this.createArgsNode = CreateArgumentsNode.create();
            this.func = callable;
            this.invokeNode = FunctionInvokeNode.create(callable);
            this.errorResult = errorResult;
        }

        @Override
        public final Object execute(VirtualFrame frame) {
            Object[] args = readVarargsNode.executeObjectArray(frame);
            try {
                Object[] arguments = createArgsNode.execute(func, args);
                return invokeNode.execute(frame, arguments);
            } catch (PException e) {
                // transformExceptionToNativeNode acts as a branch profile
                ensureTransformExceptionToNativeNode().execute(frame, e);
                return errorResult;
            }
        }

        private TransformExceptionToNativeNode ensureTransformExceptionToNativeNode() {
            if (transformExceptionToNativeNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                transformExceptionToNativeNode = insert(TransformExceptionToNativeNodeGen.create());
            }
            return transformExceptionToNativeNode;
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

        @Specialization(guards = "lib.isNative(obj)", limit = "1")
        @SuppressWarnings("unused")
        boolean doNative(PythonNativeWrapper obj,
                        @CachedLibrary("obj") PythonNativeWrapperLibrary lib) {
            return true;
        }

        @Specialization(guards = {"!lib.isNative(obj)", "isSpecialSingleton(lib.getDelegate(obj))"}, limit = "1")
        boolean doSpecial(PythonNativeWrapper obj,
                        @CachedLibrary("obj") PythonNativeWrapperLibrary lib,
                        @Cached GetSpecialSingletonPtrNode getSpecialSingletonPtrNode) {
            return getSpecialSingletonPtrNode.execute(lib.getDelegate(obj)) != null;
        }

        @Specialization(limit = "1", replaces = {"doNative", "doSpecial"})
        boolean doGeneric(PythonNativeWrapper obj,
                        @CachedLibrary("obj") PythonNativeWrapperLibrary lib,
                        @Cached GetSpecialSingletonPtrNode getSpecialSingletonPtrNode,
                        @Cached("createClassProfile()") ValueProfile singletonProfile) {
            if (lib.isNative(obj)) {
                return true;
            } else {
                Object delegate = singletonProfile.profile(lib.getDelegate(obj));
                if (isSpecialSingleton(delegate)) {
                    return getSpecialSingletonPtrNode.execute(delegate) != null;
                } else {
                    return false;
                }
            }
        }

        protected static boolean isSpecialSingleton(Object delegate) {
            return PythonLanguage.getSingletonNativePtrIdx(delegate) != -1;
        }

        protected static Assumption nativeObjectsAllManagedAssumption() {
            return PythonLanguage.getContext().getNativeObjectsAllManagedAssumption();
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

        protected static Assumption singleContextAssumption() {
            return PythonLanguage.getCurrent().singleContextAssumption;
        }

        // n.b.: since we guard that there is a pointer, we can be sure that
        // this is a singleton and we can cache it directly
        @Specialization(guards = {"cachedObj == obj", "ptr != null"}, assumptions = "singleContextAssumption()")
        @SuppressWarnings("unused")
        Object doCached(PythonAbstractObject obj,
                        @CachedContext(PythonLanguage.class) PythonContext context,
                        @Cached("obj") PythonAbstractObject cachedObj,
                        @Cached("context.getSingletonNativePtr(cachedObj)") Object ptr) {
            return ptr;
        }

        @Specialization(replaces = "doCached")
        Object doAbstract(PythonAbstractObject obj,
                        @CachedContext(PythonLanguage.class) PythonContext context) {
            return context.getSingletonNativePtr(obj);
        }

        @Fallback
        Object doGeneric(@SuppressWarnings("unused") Object obj) {
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

    @GenerateUncached
    @TypeSystemReference(PythonTypes.class)
    public abstract static class GetTypeMemberNode extends CExtBaseNode {
        public abstract Object execute(Object obj, String getterFuncName);

        /*
         * A note about the logic here, and why this is fine: the cachedObj is from a particular
         * native context, so we can be sure that the "nativeClassStableAssumption" (which is
         * per-context) is from the context in which this native object was created.
         */
        @Specialization(guards = {"referenceLibrary.isSame(cachedObj, obj)", "memberName == cachedMemberName"}, //
                        limit = "1", //
                        assumptions = {"getNativeClassStableAssumption(cachedObj)", "singleContextAssumption()"})
        public Object doCachedObj(@SuppressWarnings("unused") PythonAbstractNativeObject obj, @SuppressWarnings("unused") String memberName,
                        @Cached("obj") @SuppressWarnings("unused") PythonAbstractNativeObject cachedObj,
                        @CachedLibrary("cachedObj") @SuppressWarnings("unused") ReferenceLibrary referenceLibrary,
                        @Cached("memberName") @SuppressWarnings("unused") String cachedMemberName,
                        @Cached("getterFuncName(memberName)") @SuppressWarnings("unused") String getterFuncName,
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
            return PythonLanguage.getContext().getNativeClassStableAssumption(clazz, true).getAssumption();
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

    /**
     * Use this node to lookup a native type member like {@code tp_alloc}.<br>
     * <p>
     * This node basically implements the native member inheritance that is done by
     * {@code inherit_special} or other code in {@code PyType_Ready}.
     * </p>
     * <p>
     * Since it may be that a managed types needs to emulate such members but there is no
     * corresponding Python attribute (e.g. {@code tp_alloc}), such members are stored as hidden
     * keys on the managed type. However, the MRO may contain native types and in this case, we need
     * to access the native member.
     * </p>
     */
    @GenerateUncached
    public abstract static class LookupNativeMemberInMRONode extends Node {

        public abstract Object execute(PythonAbstractClass cls, String nativeMemberName, Object managedMemberName);

        @Specialization
        Object doSingleContext(PythonAbstractClass cls, String nativeMemberName, Object managedMemberName,
                        @Cached GetMroStorageNode getMroNode,
                        @Cached SequenceStorageNodes.LenNode lenNode,
                        @Cached SequenceStorageNodes.GetItemDynamicNode getItemNode,
                        @Cached("createForceType()") ReadAttributeFromObjectNode readAttrNode,
                        @Cached GetTypeMemberNode getTypeMemberNode) {

            MroSequenceStorage mroStorage = getMroNode.execute(cls);
            int n = lenNode.execute(mroStorage);

            for (int i = 0; i < n; i++) {
                PythonAbstractClass mroCls = (PythonAbstractClass) getItemNode.execute(mroStorage, i);
                Object result = PNone.NO_VALUE;
                if (PGuards.isManagedClass(mroCls)) {
                    result = readAttrNode.execute(mroCls, managedMemberName);
                } else {
                    assert PGuards.isNativeClass(mroCls) : "invalid class inheritance structure; expected native class";
                    result = getTypeMemberNode.execute(mroCls, nativeMemberName);
                }
                if (result != PNone.NO_VALUE) {
                    return result;
                }
            }

            return PNone.NO_VALUE;
        }
    }

    /**
     * Use this node to transform an exception to native if a Python exception was thrown during an
     * upcall and before returning to native code. This node will reify the exception appropriately
     * and register the exception as the current exception.
     */
    @GenerateUncached
    public abstract static class TransformExceptionToNativeNode extends Node {

        public abstract void execute(Frame frame, PException e);

        @Specialization(guards = "frame != null")
        void doWithFrame(Frame frame, PException e,
                        @Shared("context") @CachedContext(PythonLanguage.class) PythonContext context) {
            transformToNative(context, PArguments.getCurrentFrameInfo(frame), e);
        }

        @Specialization(guards = "frame == null")
        void doWithoutFrame(@SuppressWarnings("unused") Frame frame, PException e,
                        @Shared("context") @CachedContext(PythonLanguage.class) PythonContext context) {
            transformToNative(context, context.peekTopFrameInfo(), e);
        }

        @Specialization(replaces = {"doWithFrame", "doWithoutFrame"})
        void doGeneric(Frame frame, PException e,
                        @Shared("context") @CachedContext(PythonLanguage.class) PythonContext context) {
            PFrame.Reference ref = frame == null ? context.peekTopFrameInfo() : PArguments.getCurrentFrameInfo(frame);
            transformToNative(context, ref, e);
        }

        public static void transformToNative(PythonContext context, PFrame.Reference frameInfo, PException p) {
            p.getExceptionObject().reifyException(frameInfo);
            context.setCurrentException(p);
        }
    }

    @GenerateUncached
    public abstract static class PRaiseNativeNode extends Node {

        public final int raiseInt(Frame frame, int errorValue, PythonBuiltinClassType errType, String format, Object... arguments) {
            return executeInt(frame, errorValue, errType, format, arguments);
        }

        public final Object raise(Frame frame, Object errorValue, PythonBuiltinClassType errType, String format, Object... arguments) {
            return execute(frame, errorValue, errType, format, arguments);
        }

        public final int raiseIntWithoutFrame(int errorValue, PythonBuiltinClassType errType, String format, Object... arguments) {
            return executeInt(null, errorValue, errType, format, arguments);
        }

        public final Object raiseWithoutFrame(Object errorValue, PythonBuiltinClassType errType, String format, Object... arguments) {
            return execute(null, errorValue, errType, format, arguments);
        }

        public abstract Object execute(Frame frame, Object errorValue, PythonBuiltinClassType errType, String format, Object[] arguments);

        public abstract int executeInt(Frame frame, int errorValue, PythonBuiltinClassType errType, String format, Object[] arguments);

        @Specialization
        int doInt(Frame frame, int errorValue, PythonBuiltinClassType errType, String format, Object[] arguments,
                        @Shared("raiseNode") @Cached PRaiseNode raiseNode,
                        @Shared("transformExceptionToNativeNode") @Cached TransformExceptionToNativeNode transformExceptionToNativeNode) {
            raiseNative(frame, errType, format, arguments, raiseNode, transformExceptionToNativeNode);
            return errorValue;
        }

        @Specialization
        Object doObject(Frame frame, Object errorValue, PythonBuiltinClassType errType, String format, Object[] arguments,
                        @Shared("raiseNode") @Cached PRaiseNode raiseNode,
                        @Shared("transformExceptionToNativeNode") @Cached TransformExceptionToNativeNode transformExceptionToNativeNode) {
            raiseNative(frame, errType, format, arguments, raiseNode, transformExceptionToNativeNode);
            return errorValue;
        }

        public static void raiseNative(Frame frame, PythonBuiltinClassType errType, String format, Object[] arguments, PRaiseNode raiseNode,
                        TransformExceptionToNativeNode transformExceptionToNativeNode) {
            try {
                throw raiseNode.execute(errType, PNone.NO_VALUE, format, arguments);
            } catch (PException p) {
                transformExceptionToNativeNode.execute(frame, p);
            }
        }
    }
}
