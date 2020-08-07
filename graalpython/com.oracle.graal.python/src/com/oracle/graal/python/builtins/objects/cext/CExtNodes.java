/*
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.graal.python.builtins.objects.cext.NativeCAPISymbols.FUN_PTR_COMPARE;
import static com.oracle.graal.python.builtins.objects.cext.NativeCAPISymbols.FUN_PY_FLOAT_AS_DOUBLE;
import static com.oracle.graal.python.builtins.objects.cext.NativeCAPISymbols.FUN_PY_TRUFFLE_BYTE_ARRAY_TO_NATIVE;
import static com.oracle.graal.python.builtins.objects.cext.NativeCAPISymbols.FUN_PY_TRUFFLE_STRING_TO_CSTR;
import static com.oracle.graal.python.builtins.objects.cext.NativeCAPISymbols.FUN_WHCAR_SIZE;
import static com.oracle.graal.python.builtins.objects.cext.NativeMember.OB_REFCNT;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__COMPLEX__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__FLOAT__;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.TypeError;

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
import com.oracle.graal.python.builtins.objects.cext.CExtNodesFactory.AsPythonObjectNodeGen;
import com.oracle.graal.python.builtins.objects.cext.CExtNodesFactory.BinaryFirstToSulongNodeGen;
import com.oracle.graal.python.builtins.objects.cext.CExtNodesFactory.CextUpcallNodeGen;
import com.oracle.graal.python.builtins.objects.cext.CExtNodesFactory.DirectUpcallNodeGen;
import com.oracle.graal.python.builtins.objects.cext.CExtNodesFactory.FastCallArgsToSulongNodeGen;
import com.oracle.graal.python.builtins.objects.cext.CExtNodesFactory.FastCallWithKeywordsArgsToSulongNodeGen;
import com.oracle.graal.python.builtins.objects.cext.CExtNodesFactory.GetTypeMemberNodeGen;
import com.oracle.graal.python.builtins.objects.cext.CExtNodesFactory.IsPointerNodeGen;
import com.oracle.graal.python.builtins.objects.cext.CExtNodesFactory.ObjectUpcallNodeGen;
import com.oracle.graal.python.builtins.objects.cext.CExtNodesFactory.PointerCompareNodeGen;
import com.oracle.graal.python.builtins.objects.cext.CExtNodesFactory.TernaryFirstSecondToSulongNodeGen;
import com.oracle.graal.python.builtins.objects.cext.CExtNodesFactory.TernaryFirstThirdToSulongNodeGen;
import com.oracle.graal.python.builtins.objects.cext.CExtNodesFactory.TransformExceptionToNativeNodeGen;
import com.oracle.graal.python.builtins.objects.cext.CExtNodesFactory.WrapVoidPtrNodeGen;
import com.oracle.graal.python.builtins.objects.cext.DynamicObjectNativeWrapper.PrimitiveNativeWrapper;
import com.oracle.graal.python.builtins.objects.cext.DynamicObjectNativeWrapper.PythonObjectNativeWrapper;
import com.oracle.graal.python.builtins.objects.cext.capi.CApiContext;
import com.oracle.graal.python.builtins.objects.cext.capi.NativeReferenceCache.ResolveNativeReferenceNode;
import com.oracle.graal.python.builtins.objects.cext.capi.PyTruffleObjectFree.FreeNode;
import com.oracle.graal.python.builtins.objects.cext.common.CExtAsPythonObjectNode;
import com.oracle.graal.python.builtins.objects.cext.common.CExtCommonNodes.ImportCExtSymbolNode;
import com.oracle.graal.python.builtins.objects.cext.common.CExtContext;
import com.oracle.graal.python.builtins.objects.cext.common.CExtToJavaNode;
import com.oracle.graal.python.builtins.objects.cext.common.CExtToNativeNode;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes;
import com.oracle.graal.python.builtins.objects.complex.PComplex;
import com.oracle.graal.python.builtins.objects.floats.PFloat;
import com.oracle.graal.python.builtins.objects.function.PFunction;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.builtins.objects.getsetdescriptor.DescriptorDeleteMarker;
import com.oracle.graal.python.builtins.objects.ints.PInt;
import com.oracle.graal.python.builtins.objects.module.PythonModule;
import com.oracle.graal.python.builtins.objects.object.PythonObjectLibrary;
import com.oracle.graal.python.builtins.objects.str.NativeCharSequence;
import com.oracle.graal.python.builtins.objects.str.PString;
import com.oracle.graal.python.builtins.objects.type.PythonAbstractClass;
import com.oracle.graal.python.builtins.objects.type.PythonManagedClass;
import com.oracle.graal.python.builtins.objects.type.TypeNodes;
import com.oracle.graal.python.builtins.objects.type.TypeNodes.GetMroStorageNode;
import com.oracle.graal.python.builtins.objects.type.TypeNodes.GetNameNode;
import com.oracle.graal.python.nodes.ErrorMessages;
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
import com.oracle.graal.python.nodes.frame.GetCurrentFrameRef;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonTernaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.nodes.object.IsBuiltinClassProfile;
import com.oracle.graal.python.nodes.truffle.PythonTypes;
import com.oracle.graal.python.nodes.util.CannotCastException;
import com.oracle.graal.python.nodes.util.CastToJavaLongLossyNode;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.PythonCore;
import com.oracle.graal.python.runtime.PythonOptions;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.exception.PythonErrorType;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.graal.python.runtime.sequence.storage.MroSequenceStorage;
import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.TruffleLanguage.ContextReference;
import com.oracle.truffle.api.TruffleLogger;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.CachedContext;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.GeneratedBy;
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
import com.oracle.truffle.api.nodes.InvalidAssumptionException;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.NodeUtil;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.api.profiles.ConditionProfile;

public abstract class CExtNodes {

    @GenerateUncached
    abstract static class ImportCAPISymbolNode extends PNodeWithContext {

        public abstract Object execute(String name);

        @Specialization
        static Object doGeneric(String name,
                        @CachedContext(PythonLanguage.class) PythonContext context,
                        @Cached ImportCExtSymbolNode importCExtSymbolNode) {
            return importCExtSymbolNode.execute(context.getCApiContext(), name);
        }
    }

    // -----------------------------------------------------------------------------------------------------------------

    /**
     * For some builtin classes, the CPython approach to creating a subclass instance is to just
     * call the alloc function and then assign some fields. This needs to be done in C. This node
     * will call that subtype C function with two arguments, the C type object and an object
     * argument to fill in from.
     */
    @ImportStatic({PGuards.class})
    public abstract static class SubtypeNew extends Node {
        /**
         * typenamePrefix the <code>typename</code> in <code>typename_subtype_new</code>
         */
        protected String getTypenamePrefix() {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw new IllegalStateException();
        }

        protected abstract Object execute(Object object, Object arg);

        protected String getFunctionName() {
            return getTypenamePrefix() + "_subtype_new";
        }

        @Specialization(guards = "isNativeClass(object)")
        protected Object callNativeConstructor(Object object, Object arg,
                        @Exclusive @Cached("getFunctionName()") String functionName,
                        @Exclusive @Cached ToSulongNode toSulongNode,
                        @Exclusive @Cached ToJavaNode toJavaNode,
                        @CachedLibrary(limit = "1") InteropLibrary interopLibrary,
                        @Exclusive @Cached ImportCAPISymbolNode importCAPISymbolNode) {
            try {
                Object result = interopLibrary.execute(importCAPISymbolNode.execute(functionName), toSulongNode.execute(object), arg);
                return toJavaNode.execute(result);
            } catch (UnsupportedMessageException | UnsupportedTypeException | ArityException e) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw new IllegalStateException("C subtype_new function failed", e);
            }
        }
    }

    public abstract static class FloatSubtypeNew extends SubtypeNew {
        @Override
        protected final String getTypenamePrefix() {
            return "float";
        }

        public final Object call(Object object, double arg) {
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

        public final Object call(Object object, Object arg) {
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

        public final Object call(Object object, Object arg) {
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
    public abstract static class FromNativeSubclassNode extends Node {

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
                    CompilerDirectives.transferToInterpreterAndInvalidate();
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
    @ImportStatic({PGuards.class, CApiGuards.class})
    public abstract static class ToSulongNode extends CExtToNativeNode {

        @Specialization
        static Object doString(@SuppressWarnings("unused") CExtContext cextContext, String str,
                        @Cached PythonObjectFactory factory,
                        @Cached("createBinaryProfile()") ConditionProfile noWrapperProfile) {
            return PythonObjectNativeWrapper.wrap(factory.createString(str), noWrapperProfile);
        }

        @Specialization
        static Object doBoolean(@SuppressWarnings("unused") CExtContext cextContext, boolean b,
                        @Shared("contextRef") @CachedContext(PythonLanguage.class) ContextReference<PythonContext> contextRef,
                        @Cached("createBinaryProfile()") ConditionProfile profile) {
            PythonCore core = contextRef.get().getCore();
            PInt boxed = b ? core.getTrue() : core.getFalse();
            DynamicObjectNativeWrapper nativeWrapper = boxed.getNativeWrapper();
            if (profile.profile(nativeWrapper == null)) {
                nativeWrapper = PrimitiveNativeWrapper.createBool(b);
                boxed.setNativeWrapper(nativeWrapper);
            }
            return nativeWrapper;
        }

        @Specialization(guards = "isSmallInteger(i)")
        static PrimitiveNativeWrapper doIntegerSmall(@SuppressWarnings("unused") CExtContext cextContext, int i,
                        @Shared("contextRef") @CachedContext(PythonLanguage.class) ContextReference<PythonContext> contextRef) {
            PythonContext context = contextRef.get();
            if (context.getCApiContext() != null) {
                return context.getCApiContext().getCachedPrimitiveNativeWrapper(i);
            }
            return PrimitiveNativeWrapper.createInt(i);
        }

        @Specialization(guards = "!isSmallInteger(i)", replaces = "doIntegerSmall")
        static PrimitiveNativeWrapper doInteger(@SuppressWarnings("unused") CExtContext cextContext, int i) {
            return PrimitiveNativeWrapper.createInt(i);
        }

        @Specialization(replaces = {"doIntegerSmall", "doInteger"})
        static PrimitiveNativeWrapper doIntegerGeneric(@SuppressWarnings("unused") CExtContext cextContext, int i,
                        @Shared("contextRef") @CachedContext(PythonLanguage.class) ContextReference<PythonContext> contextRef) {
            if (CApiGuards.isSmallInteger(i)) {
                return doIntegerSmall(cextContext, i, contextRef);
            }
            return PrimitiveNativeWrapper.createInt(i);
        }

        @Specialization(guards = "isSmallLong(l)")
        static PrimitiveNativeWrapper doLongSmall(@SuppressWarnings("unused") CExtContext cextContext, long l,
                        @Shared("contextRef") @CachedContext(PythonLanguage.class) ContextReference<PythonContext> contextRef) {
            PythonContext context = contextRef.get();
            if (context.getCApiContext() != null) {
                return context.getCApiContext().getCachedPrimitiveNativeWrapper(l);
            }
            return PrimitiveNativeWrapper.createLong(l);
        }

        @Specialization(guards = "!isSmallLong(l)", replaces = "doLongSmall")
        static PrimitiveNativeWrapper doLong(@SuppressWarnings("unused") CExtContext cextContext, long l) {
            return PrimitiveNativeWrapper.createLong(l);
        }

        @Specialization(replaces = {"doLongSmall", "doLong"})
        static PrimitiveNativeWrapper doLongGeneric(@SuppressWarnings("unused") CExtContext cextContext, long l,
                        @Shared("contextRef") @CachedContext(PythonLanguage.class) ContextReference<PythonContext> contextRef) {
            if (CApiGuards.isSmallLong(l)) {
                return doLongSmall(cextContext, l, contextRef);
            }
            return PrimitiveNativeWrapper.createLong(l);
        }

        @Specialization(guards = "!isNaN(d)")
        static Object doDouble(@SuppressWarnings("unused") CExtContext cextContext, double d) {
            return PrimitiveNativeWrapper.createDouble(d);
        }

        @Specialization(guards = "isNaN(d)")
        static Object doDouble(@SuppressWarnings("unused") CExtContext cextContext, @SuppressWarnings("unused") double d,
                        @Shared("contextRef") @CachedContext(PythonLanguage.class) ContextReference<PythonContext> contextRef,
                        @Cached("createCountingProfile()") ConditionProfile noWrapperProfile) {
            PFloat boxed = contextRef.get().getCore().getNaN();
            DynamicObjectNativeWrapper nativeWrapper = boxed.getNativeWrapper();
            // Use a counting profile since we should enter the branch just once per context.
            if (noWrapperProfile.profile(nativeWrapper == null)) {
                // This deliberately uses 'CompilerDirectives.transferToInterpreter()' because this
                // code will happen just once per context.
                CompilerDirectives.transferToInterpreter();
                nativeWrapper = PrimitiveNativeWrapper.createDouble(Double.NaN);
                boxed.setNativeWrapper(nativeWrapper);
            }
            return nativeWrapper;
        }

        @Specialization
        static Object doNativeObject(@SuppressWarnings("unused") CExtContext cextContext, PythonAbstractNativeObject nativeObject) {
            return nativeObject.getPtr();
        }

        @Specialization
        static Object doNativeNull(@SuppressWarnings("unused") CExtContext cextContext, PythonNativeNull object) {
            return object.getPtr();
        }

        @Specialization
        static Object doDeleteMarker(@SuppressWarnings("unused") CExtContext cextContext, DescriptorDeleteMarker marker,
                        @Cached GetNativeNullNode getNativeNullNode) {
            assert marker == DescriptorDeleteMarker.INSTANCE;
            PythonNativeNull nativeNull = (PythonNativeNull) getNativeNullNode.execute();
            return nativeNull.getPtr();
        }

        @Specialization(guards = {"object == cachedObject", "isSpecialSingleton(cachedObject)"})
        static Object doSingletonCached(CExtContext cextContext, @SuppressWarnings("unused") PythonAbstractObject object,
                        @Cached("object") PythonAbstractObject cachedObject,
                        @Shared("contextRef") @CachedContext(PythonLanguage.class) ContextReference<PythonContext> contextRef) {
            return doSingleton(cextContext, cachedObject, contextRef);
        }

        @Specialization(guards = "isSpecialSingleton(object)", replaces = "doSingletonCached")
        static Object doSingleton(@SuppressWarnings("unused") CExtContext cextContext, @SuppressWarnings("unused") PythonAbstractObject object,
                        @Shared("contextRef") @CachedContext(PythonLanguage.class) ContextReference<PythonContext> contextRef) {
            PythonContext context = contextRef.get();
            PythonNativeWrapper nativeWrapper = context.getSingletonNativeWrapper(object);
            if (nativeWrapper == null) {
                // this will happen just once per context and special singleton
                CompilerDirectives.transferToInterpreterAndInvalidate();
                nativeWrapper = new PythonObjectNativeWrapper(object);
                // this should keep the native wrapper alive forever
                nativeWrapper.increaseRefCount();
                context.setSingletonNativeWrapper(object, nativeWrapper);
            }
            return nativeWrapper;
        }

        @Specialization(guards = "object == cachedObject", limit = "3")
        static Object doPythonClass(@SuppressWarnings("unused") CExtContext cextContext, @SuppressWarnings("unused") PythonManagedClass object,
                        @SuppressWarnings("unused") @Cached("object") PythonManagedClass cachedObject,
                        @Cached("wrapNativeClass(object)") PythonClassNativeWrapper wrapper) {
            return wrapper;
        }

        @Specialization(replaces = "doPythonClass")
        static Object doPythonClassUncached(@SuppressWarnings("unused") CExtContext cextContext, PythonManagedClass object,
                        @Cached TypeNodes.GetNameNode getNameNode) {
            return PythonClassNativeWrapper.wrap(object, getNameNode.execute(object));
        }

        @Specialization(guards = {"cachedClass == object.getClass()", "!isClass(object, lib)", "!isNativeObject(object)", "!isSpecialSingleton(object)"}, limit = "3")
        static Object runAbstractObjectCached(@SuppressWarnings("unused") CExtContext cextContext, PythonAbstractObject object,
                        @Cached("createBinaryProfile()") ConditionProfile noWrapperProfile,
                        @Cached("object.getClass()") Class<? extends PythonAbstractObject> cachedClass,
                        @SuppressWarnings("unused") @CachedLibrary("object") InteropLibrary lib) {
            assert object != PNone.NO_VALUE;
            return PythonObjectNativeWrapper.wrap(CompilerDirectives.castExact(object, cachedClass), noWrapperProfile);
        }

        @Specialization(guards = {"!isClass(object, lib)", "!isNativeObject(object)", "!isSpecialSingleton(object)"}, replaces = "runAbstractObjectCached", limit = "3")
        static Object runAbstractObject(@SuppressWarnings("unused") CExtContext cextContext, PythonAbstractObject object,
                        @Cached("createBinaryProfile()") ConditionProfile noWrapperProfile,
                        @SuppressWarnings("unused") @CachedLibrary("object") InteropLibrary lib) {
            assert object != PNone.NO_VALUE;
            return PythonObjectNativeWrapper.wrap(object, noWrapperProfile);
        }

        @Specialization(guards = {"lib.isForeignObject(object)", "!isNativeWrapper(object)", "!isNativeNull(object)"}, limit = "3")
        static Object doForeignObject(@SuppressWarnings("unused") CExtContext cextContext, TruffleObject object,
                        @SuppressWarnings("unused") @CachedLibrary("object") PythonObjectLibrary lib) {
            return TruffleObjectNativeWrapper.wrap(object);
        }

        @Specialization(guards = "isFallback(object, lib)", limit = "1")
        static Object run(@SuppressWarnings("unused") CExtContext cextContext, Object object,
                        @SuppressWarnings("unused") @CachedLibrary("object") PythonObjectLibrary lib) {
            assert object != null : "Java 'null' cannot be a Sulong value";
            assert CApiGuards.isNativeWrapper(object) : "unknown object cannot be a Sulong value";
            return object;
        }

        protected static PythonClassNativeWrapper wrapNativeClass(PythonManagedClass object) {
            return PythonClassNativeWrapper.wrap(object, GetNameNode.doSlowPath(object));
        }

        static boolean isFallback(Object object, PythonObjectLibrary lib) {
            return !(object instanceof String || object instanceof Boolean || object instanceof Integer || object instanceof Long || object instanceof Double ||
                            object instanceof PythonNativeNull || object == DescriptorDeleteMarker.INSTANCE || object instanceof PythonAbstractObject) &&
                            !lib.isReflectedObject(object, object) && !(lib.isForeignObject(object) && !CApiGuards.isNativeWrapper(object));
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

    /**
     * Same as {@code ToSulongNode} but ensures that a new Python reference is returned.<br/>
     * Concept:<br/>
     * <p>
     * If the value to convert is a managed object or a Java primitive, we will (1) do nothing if a
     * fresh wrapper is created, or (2) increase the reference count by 1 if the wrapper already
     * exists.
     * </p>
     * <p>
     * If the value to convert is a {@link PythonAbstractNativeObject} (i.e. a wrapped native
     * pointer), the reference count will be increased by 1. This is necessary because if the
     * currently returning upcall function already got a new reference, it won't have increased the
     * refcnt but will eventually decreases it.<br/>
     * Consider following example:<br/>
     *
     * <pre>
     *     some.py: nativeLong0 * nativeLong1
     * </pre>
     *
     * Assume that {@code nativeLong0} is a native object with a native type. It will call
     * {@code nativeType->tp_as_number.nb_multiply}. This one then often uses
     * {@code PyNumber_Multiply} which should just pass through the newly created native reference.
     * But it will decrease the reference count since it wraps the gained native pointer. So, the
     * intermediate upcall should effectively not alter the refcnt which means that we need to
     * increase it since it will finally decrease it.
     * </p>
     */
    @GenerateUncached
    @ImportStatic({PGuards.class, CApiGuards.class})
    public abstract static class ToNewRefNode extends CExtToNativeNode {

        public final Object executeInt(int i) {
            return executeInt(CExtContext.LAZY_CONTEXT, i);
        }

        public final Object executeLong(long l) {
            return executeLong(CExtContext.LAZY_CONTEXT, l);
        }

        public abstract Object executeInt(CExtContext cExtContext, int i);

        public abstract Object executeLong(CExtContext cExtContext, long l);

        @Specialization
        static Object doString(CExtContext cextContext, String str,
                        @Cached PythonObjectFactory factory,
                        @Cached("createBinaryProfile()") ConditionProfile noWrapperProfile) {
            return ToSulongNode.doString(cextContext, str, factory, noWrapperProfile);
        }

        @Specialization
        static Object doBoolean(@SuppressWarnings("unused") CExtContext cextContext, boolean b,
                        @Shared("contextRef") @CachedContext(PythonLanguage.class) ContextReference<PythonContext> contextRef,
                        @Cached("createBinaryProfile()") ConditionProfile profile) {
            PythonCore core = contextRef.get().getCore();
            PInt boxed = b ? core.getTrue() : core.getFalse();
            DynamicObjectNativeWrapper nativeWrapper = boxed.getNativeWrapper();
            if (profile.profile(nativeWrapper == null)) {
                nativeWrapper = PrimitiveNativeWrapper.createBool(b);
                boxed.setNativeWrapper(nativeWrapper);
            } else {
                nativeWrapper.increaseRefCount();
            }
            return nativeWrapper;
        }

        @Specialization(guards = "isSmallInteger(i)")
        static PrimitiveNativeWrapper doIntegerSmall(@SuppressWarnings("unused") CExtContext cextContext, int i,
                        @Shared("contextRef") @CachedContext(PythonLanguage.class) ContextReference<PythonContext> contextRef) {
            PythonContext context = contextRef.get();
            if (context.getCApiContext() != null) {
                PrimitiveNativeWrapper cachedPrimitiveNativeWrapper = context.getCApiContext().getCachedPrimitiveNativeWrapper(i);
                cachedPrimitiveNativeWrapper.increaseRefCount();
                return cachedPrimitiveNativeWrapper;
            }
            return PrimitiveNativeWrapper.createInt(i);
        }

        @Specialization(guards = "!isSmallInteger(i)", replaces = "doIntegerSmall")
        static PrimitiveNativeWrapper doInteger(@SuppressWarnings("unused") CExtContext cextContext, int i) {
            return PrimitiveNativeWrapper.createInt(i);
        }

        @Specialization(replaces = {"doIntegerSmall", "doInteger"})
        static PrimitiveNativeWrapper doIntegerGeneric(CExtContext cextContext, int i,
                        @Shared("contextRef") @CachedContext(PythonLanguage.class) ContextReference<PythonContext> contextRef) {
            if (CApiGuards.isSmallInteger(i)) {
                return doIntegerSmall(cextContext, i, contextRef);
            }
            return PrimitiveNativeWrapper.createInt(i);
        }

        @Specialization(guards = "isSmallLong(l)")
        static PrimitiveNativeWrapper doLongSmall(@SuppressWarnings("unused") CExtContext cextContext, long l,
                        @Shared("contextRef") @CachedContext(PythonLanguage.class) ContextReference<PythonContext> contextRef) {
            PythonContext context = contextRef.get();
            if (context.getCApiContext() != null) {
                PrimitiveNativeWrapper cachedPrimitiveNativeWrapper = context.getCApiContext().getCachedPrimitiveNativeWrapper(l);
                cachedPrimitiveNativeWrapper.increaseRefCount();
                return cachedPrimitiveNativeWrapper;
            }
            return PrimitiveNativeWrapper.createLong(l);
        }

        @Specialization(guards = "!isSmallLong(l)", replaces = "doLongSmall")
        static PrimitiveNativeWrapper doLong(@SuppressWarnings("unused") CExtContext cextContext, long l) {
            return PrimitiveNativeWrapper.createLong(l);
        }

        @Specialization(replaces = {"doLongSmall", "doLong"})
        static PrimitiveNativeWrapper doLongGeneric(CExtContext cextContext, long l,
                        @Shared("contextRef") @CachedContext(PythonLanguage.class) ContextReference<PythonContext> contextRef) {
            if (CApiGuards.isSmallLong(l)) {
                return doLongSmall(cextContext, l, contextRef);
            }
            return PrimitiveNativeWrapper.createLong(l);
        }

        @Specialization(guards = "!isNaN(d)")
        static Object doDouble(CExtContext cextContext, double d) {
            return ToSulongNode.doDouble(cextContext, d);
        }

        @Specialization(guards = "isNaN(d)")
        static Object doDouble(@SuppressWarnings("unused") CExtContext cextContext, @SuppressWarnings("unused") double d,
                        @Shared("contextRef") @CachedContext(PythonLanguage.class) ContextReference<PythonContext> contextRef,
                        @Cached("createCountingProfile()") ConditionProfile noWrapperProfile) {
            PFloat boxed = contextRef.get().getCore().getNaN();
            DynamicObjectNativeWrapper nativeWrapper = boxed.getNativeWrapper();
            // Use a counting profile since we should enter the branch just once per context.
            if (noWrapperProfile.profile(nativeWrapper == null)) {
                // This deliberately uses 'CompilerDirectives.transferToInterpreter()' because this
                // code will happen just once per context.
                CompilerDirectives.transferToInterpreter();
                nativeWrapper = PrimitiveNativeWrapper.createDouble(Double.NaN);
                boxed.setNativeWrapper(nativeWrapper);
            } else {
                nativeWrapper.increaseRefCount();
            }
            return nativeWrapper;
        }

        @Specialization
        static Object doNativeObject(CExtContext cextContext, PythonAbstractNativeObject nativeObject,
                        @Cached AddRefCntNode refCntNode) {
            Object res = ToSulongNode.doNativeObject(cextContext, nativeObject);
            refCntNode.inc(res);
            return res;
        }

        @Specialization
        static Object doNativeNull(CExtContext cextContext, PythonNativeNull object) {
            return ToSulongNode.doNativeNull(cextContext, object);
        }

        @Specialization
        static Object doDeleteMarker(CExtContext cextContext, DescriptorDeleteMarker marker,
                        @Cached GetNativeNullNode getNativeNullNode) {
            return ToSulongNode.doDeleteMarker(cextContext, marker, getNativeNullNode);
        }

        @Specialization(guards = {"object == cachedObject", "isSpecialSingleton(cachedObject)"})
        static Object doSingletonCached(CExtContext cextContext, @SuppressWarnings("unused") PythonAbstractObject object,
                        @Cached("object") PythonAbstractObject cachedObject,
                        @Shared("contextRef") @CachedContext(PythonLanguage.class) ContextReference<PythonContext> contextRef) {
            return doSingleton(cextContext, cachedObject, contextRef);
        }

        @Specialization(guards = "isSpecialSingleton(object)", replaces = "doSingletonCached")
        static Object doSingleton(@SuppressWarnings("unused") CExtContext cextContext, @SuppressWarnings("unused") PythonAbstractObject object,
                        @Shared("contextRef") @CachedContext(PythonLanguage.class) ContextReference<PythonContext> contextRef) {
            PythonContext context = contextRef.get();
            PythonNativeWrapper nativeWrapper = context.getSingletonNativeWrapper(object);
            if (nativeWrapper == null) {
                // this will happen just once per context and special singleton
                CompilerDirectives.transferToInterpreterAndInvalidate();
                nativeWrapper = new PythonObjectNativeWrapper(object);
                // this should keep the native wrapper alive forever
                nativeWrapper.increaseRefCount();
                context.setSingletonNativeWrapper(object, nativeWrapper);
            } else {
                nativeWrapper.increaseRefCount();
            }
            return nativeWrapper;
        }

        @Specialization(guards = "object == cachedObject", limit = "3")
        static Object doPythonClass(@SuppressWarnings("unused") CExtContext cextContext, @SuppressWarnings("unused") PythonManagedClass object,
                        @SuppressWarnings("unused") @Cached("object") PythonManagedClass cachedObject,
                        @Cached("wrapNativeClass(object)") PythonClassNativeWrapper wrapper) {
            wrapper.increaseRefCount();
            return wrapper;
        }

        @Specialization(replaces = "doPythonClass")
        static Object doPythonClassUncached(@SuppressWarnings("unused") CExtContext cextContext, PythonManagedClass object,
                        @Cached TypeNodes.GetNameNode getNameNode) {
            return PythonClassNativeWrapper.wrapNewRef(object, getNameNode.execute(object));
        }

        @Specialization(guards = {"cachedClass == object.getClass()", "!isClass(object, lib)", "!isNativeObject(object)", "!isSpecialSingleton(object)"}, limit = "3")
        static Object runAbstractObjectCached(@SuppressWarnings("unused") CExtContext cextContext, PythonAbstractObject object,
                        @Cached("createBinaryProfile()") ConditionProfile noWrapperProfile,
                        @Cached("object.getClass()") Class<? extends PythonAbstractObject> cachedClass,
                        @SuppressWarnings("unused") @CachedLibrary("object") InteropLibrary lib) {
            assert object != PNone.NO_VALUE;
            return PythonObjectNativeWrapper.wrapNewRef(CompilerDirectives.castExact(object, cachedClass), noWrapperProfile);
        }

        @Specialization(guards = {"!isClass(object, lib)", "!isNativeObject(object)", "!isSpecialSingleton(object)"}, replaces = "runAbstractObjectCached", limit = "3")
        static Object runAbstractObject(@SuppressWarnings("unused") CExtContext cextContext, PythonAbstractObject object,
                        @Cached("createBinaryProfile()") ConditionProfile noWrapperProfile,
                        @SuppressWarnings("unused") @CachedLibrary("object") InteropLibrary lib) {
            assert object != PNone.NO_VALUE;
            return PythonObjectNativeWrapper.wrapNewRef(object, noWrapperProfile);
        }

        @Specialization(guards = {"lib.isForeignObject(object)", "!isNativeWrapper(object)", "!isNativeNull(object)"}, limit = "3")
        static Object doForeignObject(CExtContext cextContext, TruffleObject object,
                        @CachedLibrary("object") PythonObjectLibrary lib) {
            // this will always be a new wrapper; it's implicitly always a new reference in any case
            return ToSulongNode.doForeignObject(cextContext, object, lib);
        }

        @Specialization(guards = "isFallback(object, lib)", limit = "1")
        static Object run(CExtContext cextContext, Object object,
                        @CachedLibrary("object") PythonObjectLibrary lib) {
            return ToSulongNode.run(cextContext, object, lib);
        }

        protected static PythonClassNativeWrapper wrapNativeClass(PythonManagedClass object) {
            return PythonClassNativeWrapper.wrap(object, GetNameNode.doSlowPath(object));
        }

        static boolean isFallback(Object object, PythonObjectLibrary lib) {
            return ToSulongNode.isFallback(object, lib);
        }

        protected static boolean isNaN(double d) {
            return Double.isNaN(d);
        }
    }

    /**
     * Same as {@link ToNewRefNode} but does not create new references for
     * {@link PythonAbstractNativeObject}.<br/>
     * This node should only be used to convert arguments for a native call. It will increase the
     * ref count of all {@link PythonNativeWrapper} (and subclasses) (but not if they are newly
     * created since the ref count is already one in this case). But it does not increase the ref
     * count on {@link PythonAbstractNativeObject}.
     *
     * The reason for this behavior is that after the native function returns, one can decrease the
     * ref count by one and therefore release any allocated handles that would cause a memory leak.
     * This is not necessary for {@link PythonAbstractNativeObject} since they are managed by a weak
     * reference and thus we save certainly expensive access to the native {@code ob_refcnt} member.
     */
    @GenerateUncached
    @ImportStatic({PGuards.class, CApiGuards.class})
    public abstract static class ToBorrowedRefNode extends CExtToNativeNode {

        public final Object executeInt(int i) {
            return executeInt(CExtContext.LAZY_CONTEXT, i);
        }

        public final Object executeLong(long l) {
            return executeLong(CExtContext.LAZY_CONTEXT, l);
        }

        public abstract Object executeInt(CExtContext cExtContext, int i);

        public abstract Object executeLong(CExtContext cExtContext, long l);

        @Specialization
        static Object doString(CExtContext cextContext, String str,
                        @Cached PythonObjectFactory factory,
                        @Cached("createBinaryProfile()") ConditionProfile noWrapperProfile) {
            return ToSulongNode.doString(cextContext, str, factory, noWrapperProfile);
        }

        @Specialization
        static Object doBoolean(CExtContext cextContext, boolean b,
                        @Shared("contextRef") @CachedContext(PythonLanguage.class) ContextReference<PythonContext> contextRef,
                        @Cached("createBinaryProfile()") ConditionProfile profile) {
            return ToNewRefNode.doBoolean(cextContext, b, contextRef, profile);
        }

        @Specialization(guards = "isSmallInteger(i)")
        static PrimitiveNativeWrapper doIntegerSmall(CExtContext cextContext, int i,
                        @Shared("contextRef") @CachedContext(PythonLanguage.class) ContextReference<PythonContext> contextRef) {
            return ToNewRefNode.doIntegerSmall(cextContext, i, contextRef);
        }

        @Specialization(guards = "!isSmallInteger(i)", replaces = "doIntegerSmall")
        static PrimitiveNativeWrapper doInteger(CExtContext cextContext, int i) {
            return ToNewRefNode.doInteger(cextContext, i);
        }

        @Specialization(replaces = {"doIntegerSmall", "doInteger"})
        static PrimitiveNativeWrapper doIntegerGeneric(CExtContext cextContext, int i,
                        @Shared("contextRef") @CachedContext(PythonLanguage.class) ContextReference<PythonContext> contextRef) {
            return ToNewRefNode.doIntegerGeneric(cextContext, i, contextRef);
        }

        @Specialization(guards = "isSmallLong(l)")
        static PrimitiveNativeWrapper doLongSmall(CExtContext cextContext, long l,
                        @Shared("contextRef") @CachedContext(PythonLanguage.class) ContextReference<PythonContext> contextRef) {
            return ToNewRefNode.doLongSmall(cextContext, l, contextRef);
        }

        @Specialization(guards = "!isSmallLong(l)", replaces = "doLongSmall")
        static PrimitiveNativeWrapper doLong(@SuppressWarnings("unused") CExtContext cextContext, long l) {
            return ToNewRefNode.doLong(cextContext, l);
        }

        @Specialization(replaces = {"doLongSmall", "doLong"})
        static PrimitiveNativeWrapper doLongGeneric(CExtContext cextContext, long l,
                        @Shared("contextRef") @CachedContext(PythonLanguage.class) ContextReference<PythonContext> contextRef) {
            return ToNewRefNode.doLongGeneric(cextContext, l, contextRef);
        }

        @Specialization(guards = "!isNaN(d)")
        static Object doDouble(CExtContext cextContext, double d) {
            return ToSulongNode.doDouble(cextContext, d);
        }

        @Specialization(guards = "isNaN(d)")
        static Object doDouble(CExtContext cextContext, double d,
                        @Shared("contextRef") @CachedContext(PythonLanguage.class) ContextReference<PythonContext> contextRef,
                        @Cached("createCountingProfile()") ConditionProfile noWrapperProfile) {
            return ToNewRefNode.doDouble(cextContext, d, contextRef, noWrapperProfile);
        }

        @Specialization
        static Object doNativeObject(CExtContext cextContext, PythonAbstractNativeObject nativeObject) {
            return ToSulongNode.doNativeObject(cextContext, nativeObject);
        }

        @Specialization
        static Object doNativeNull(CExtContext cextContext, PythonNativeNull object) {
            return ToSulongNode.doNativeNull(cextContext, object);
        }

        @Specialization
        static Object doDeleteMarker(CExtContext cextContext, DescriptorDeleteMarker marker,
                        @Cached GetNativeNullNode getNativeNullNode) {
            return ToSulongNode.doDeleteMarker(cextContext, marker, getNativeNullNode);
        }

        @Specialization(guards = {"object == cachedObject", "isSpecialSingleton(cachedObject)"})
        static Object doSingletonCached(CExtContext cextContext, @SuppressWarnings("unused") PythonAbstractObject object,
                        @Cached("object") PythonAbstractObject cachedObject,
                        @Shared("contextRef") @CachedContext(PythonLanguage.class) ContextReference<PythonContext> contextRef) {
            return doSingleton(cextContext, cachedObject, contextRef);
        }

        @Specialization(guards = "isSpecialSingleton(object)", replaces = "doSingletonCached")
        static Object doSingleton(CExtContext cextContext, PythonAbstractObject object,
                        @Shared("contextRef") @CachedContext(PythonLanguage.class) ContextReference<PythonContext> contextRef) {
            return ToNewRefNode.doSingleton(cextContext, object, contextRef);
        }

        @Specialization(guards = "object == cachedObject", limit = "3")
        static Object doPythonClass(@SuppressWarnings("unused") CExtContext cextContext, @SuppressWarnings("unused") PythonManagedClass object,
                        @SuppressWarnings("unused") @Cached("object") PythonManagedClass cachedObject,
                        @Cached("wrapNativeClass(object)") PythonClassNativeWrapper wrapper) {
            wrapper.increaseRefCount();
            return wrapper;
        }

        @Specialization(replaces = "doPythonClass")
        static Object doPythonClassUncached(@SuppressWarnings("unused") CExtContext cextContext, PythonManagedClass object,
                        @Cached TypeNodes.GetNameNode getNameNode) {
            return PythonClassNativeWrapper.wrapNewRef(object, getNameNode.execute(object));
        }

        @Specialization(guards = {"cachedClass == object.getClass()", "!isClass(object, lib)", "!isNativeObject(object)", "!isSpecialSingleton(object)"}, limit = "3")
        static Object runAbstractObjectCached(@SuppressWarnings("unused") CExtContext cextContext, PythonAbstractObject object,
                        @Cached("createBinaryProfile()") ConditionProfile noWrapperProfile,
                        @Cached("object.getClass()") Class<? extends PythonAbstractObject> cachedClass,
                        @SuppressWarnings("unused") @CachedLibrary("object") InteropLibrary lib) {
            assert object != PNone.NO_VALUE;
            return PythonObjectNativeWrapper.wrapNewRef(CompilerDirectives.castExact(object, cachedClass), noWrapperProfile);
        }

        @Specialization(guards = {"!isClass(object, lib)", "!isNativeObject(object)", "!isSpecialSingleton(object)"}, replaces = "runAbstractObjectCached", limit = "3")
        static Object runAbstractObject(@SuppressWarnings("unused") CExtContext cextContext, PythonAbstractObject object,
                        @Cached("createBinaryProfile()") ConditionProfile noWrapperProfile,
                        @SuppressWarnings("unused") @CachedLibrary("object") InteropLibrary lib) {
            assert object != PNone.NO_VALUE;
            return PythonObjectNativeWrapper.wrapNewRef(object, noWrapperProfile);
        }

        @Specialization(guards = {"lib.isForeignObject(object)", "!isNativeWrapper(object)", "!isNativeNull(object)"}, limit = "3")
        static Object doForeignObject(CExtContext cextContext, TruffleObject object,
                        @CachedLibrary("object") PythonObjectLibrary lib) {
            // this will always be a new wrapper; it's implicitly always a new reference in any case
            return ToSulongNode.doForeignObject(cextContext, object, lib);
        }

        @Specialization(guards = "isFallback(object, lib)", limit = "1")
        static Object run(CExtContext cextContext, Object object,
                        @CachedLibrary("object") PythonObjectLibrary lib) {
            return ToSulongNode.run(cextContext, object, lib);
        }

        protected static PythonClassNativeWrapper wrapNativeClass(PythonManagedClass object) {
            return PythonClassNativeWrapper.wrap(object, GetNameNode.doSlowPath(object));
        }

        static boolean isFallback(Object object, PythonObjectLibrary lib) {
            return ToSulongNode.isFallback(object, lib);
        }

        protected static boolean isNaN(double d) {
            return Double.isNaN(d);
        }
    }

    // -----------------------------------------------------------------------------------------------------------------
    /**
     * Unwraps objects contained in {@link PythonObjectNativeWrapper} instances or wraps objects
     * allocated in native code for consumption in Java.
     */
    @GenerateUncached
    @ImportStatic({PGuards.class, CApiGuards.class})
    public abstract static class AsPythonObjectBaseNode extends CExtAsPythonObjectNode {

        @Specialization(guards = "object.isBool()")
        static boolean doBoolNativeWrapper(@SuppressWarnings("unused") CExtContext cextContext, PrimitiveNativeWrapper object) {
            return object.getBool();
        }

        @Specialization(guards = {"object.isByte()", "!isNative(isPointerNode, object)"}, limit = "1")
        static byte doByteNativeWrapper(@SuppressWarnings("unused") CExtContext cextContext, PrimitiveNativeWrapper object,
                        @Shared("isPointerNode") @Cached @SuppressWarnings("unused") IsPointerNode isPointerNode) {
            return object.getByte();
        }

        @Specialization(guards = {"object.isInt()", "mayUsePrimitive(isPointerNode, object)"}, limit = "1")
        static int doIntNativeWrappe(@SuppressWarnings("unused") CExtContext cextContext, PrimitiveNativeWrapper object,
                        @Shared("isPointerNode") @Cached @SuppressWarnings("unused") IsPointerNode isPointerNode) {
            return object.getInt();
        }

        @Specialization(guards = {"object.isLong()", "mayUsePrimitive(isPointerNode, object)"}, limit = "1")
        static long doLongNativeWrapper(@SuppressWarnings("unused") CExtContext cextContext, PrimitiveNativeWrapper object,
                        @Shared("isPointerNode") @Cached @SuppressWarnings("unused") IsPointerNode isPointerNode) {
            return object.getLong();
        }

        @Specialization(guards = {"object.isDouble()", "!isNative(isPointerNode, object)"}, limit = "1")
        static double doDoubleNativeWrapper(@SuppressWarnings("unused") CExtContext cextContext, PrimitiveNativeWrapper object,
                        @Shared("isPointerNode") @Cached @SuppressWarnings("unused") IsPointerNode isPointerNode) {
            return object.getDouble();
        }

        @Specialization(guards = {"!object.isBool()", "isNative(isPointerNode, object)", "!mayUsePrimitive(isPointerNode, object)"}, limit = "1")
        static Object doPrimitiveNativeWrapper(@SuppressWarnings("unused") CExtContext cextContext, PrimitiveNativeWrapper object,
                        @Exclusive @Cached MaterializeDelegateNode materializeNode,
                        @Shared("isPointerNode") @Cached @SuppressWarnings("unused") IsPointerNode isPointerNode) {
            return materializeNode.execute(object);
        }

        @Specialization(guards = "!isPrimitiveNativeWrapper(object)", limit = "1")
        static Object doNativeWrapper(@SuppressWarnings("unused") CExtContext cextContext, PythonNativeWrapper object,
                        @CachedLibrary("object") PythonNativeWrapperLibrary lib) {
            return lib.getDelegate(object);
        }

        @Specialization
        static PythonNativeNull doNativeNull(@SuppressWarnings("unused") CExtContext cextContext, @SuppressWarnings("unused") PythonNativeNull object) {
            return object;
        }

        @Specialization
        static PythonAbstractObject doPythonObject(@SuppressWarnings("unused") CExtContext cextContext, PythonAbstractObject object) {
            return object;
        }

        @Specialization
        static String doString(@SuppressWarnings("unused") CExtContext cextContext, String object) {
            return object;
        }

        @Specialization
        static boolean doBoolean(@SuppressWarnings("unused") CExtContext cextContext, boolean b) {
            return b;
        }

        @Specialization
        static byte doLong(@SuppressWarnings("unused") CExtContext cextContext, byte b) {
            return b;
        }

        @Specialization
        static int doLong(@SuppressWarnings("unused") CExtContext cextContext, int i) {
            return i;
        }

        @Specialization
        static long doLong(@SuppressWarnings("unused") CExtContext cextContext, long l) {
            return l;
        }

        @Specialization
        static double doDouble(@SuppressWarnings("unused") CExtContext cextContext, double d) {
            return d;
        }

        @Specialization(guards = "isFallback(obj, lib, isForeignClassProfile)", limit = "3")
        static Object run(@SuppressWarnings("unused") CExtContext cextContext, Object obj,
                        @SuppressWarnings("unused") @CachedLibrary("obj") PythonObjectLibrary lib,
                        @Cached @SuppressWarnings("unused") IsBuiltinClassProfile isForeignClassProfile,
                        @Cached PRaiseNode raiseNode) {
            throw raiseNode.raise(PythonErrorType.SystemError, ErrorMessages.INVALID_OBJ_FROM_NATIVE, obj);
        }

        protected static boolean isFallback(Object obj, PythonObjectLibrary lib, IsBuiltinClassProfile isForeignClassProfile) {
            if (CApiGuards.isNativeWrapper(obj)) {
                return false;
            }
            if (CApiGuards.isNativeNull(obj)) {
                return false;
            }
            if (obj == DescriptorDeleteMarker.INSTANCE) {
                return false;
            }
            if (PGuards.isAnyPythonObject(obj)) {
                return false;
            }
            if (isForeignObject(obj, lib, isForeignClassProfile)) {
                return false;
            }
            if (PGuards.isString(obj)) {
                return false;
            }
            return !(obj instanceof Boolean || obj instanceof Byte || obj instanceof Integer || obj instanceof Long || obj instanceof Double);
        }

        static boolean mayUsePrimitive(IsPointerNode isPointerNode, PrimitiveNativeWrapper object) {
            // For wrappers around small integers, it does not matter if they received "to-native"
            // because pointer equality is still ensured since they are globally cached in the
            // context.
            return (object.isInt() || object.isLong()) && (CApiGuards.isSmallLong(object.getLong()) || !isPointerNode.execute(object));
        }

        protected static boolean isNative(IsPointerNode isPointerNode, PythonNativeWrapper object) {
            return isPointerNode.execute(object);
        }

        protected static boolean isPrimitiveNativeWrapper(PythonNativeWrapper object) {
            return object instanceof DynamicObjectNativeWrapper.PrimitiveNativeWrapper;
        }

        protected static boolean isForeignObject(Object obj, PythonObjectLibrary lib, IsBuiltinClassProfile isForeignClassProfile) {
            return isForeignClassProfile.profileClass(lib.getLazyPythonClass(obj), PythonBuiltinClassType.ForeignObject);
        }
    }

    /**
     * Unwraps objects contained in {@link DynamicObjectNativeWrapper.PythonObjectNativeWrapper}
     * instances or wraps objects allocated in native code for consumption in Java.
     */
    @GenerateUncached
    @ImportStatic({PGuards.class, CApiGuards.class})
    public abstract static class AsPythonObjectNode extends AsPythonObjectBaseNode {

        @Specialization(guards = {"isForeignObject(object, plib, isForeignClassProfile)", "!isNativeWrapper(object)", "!isNativeNull(object)"}, limit = "2")
        static PythonAbstractObject doNativeObject(@SuppressWarnings("unused") CExtContext cextContext, TruffleObject object,
                        @SuppressWarnings("unused") @CachedLibrary("object") PythonObjectLibrary plib,
                        @Cached @SuppressWarnings("unused") IsBuiltinClassProfile isForeignClassProfile,
                        @CachedContext(PythonLanguage.class) PythonContext context,
                        @Cached("createBinaryProfile()") ConditionProfile newRefProfile,
                        @Cached("createBinaryProfile()") ConditionProfile validRefProfile,
                        @Cached("createBinaryProfile()") ConditionProfile resurrectProfile,
                        @CachedLibrary("object") InteropLibrary lib,
                        @Cached GetRefCntNode getRefCntNode,
                        @Cached AddRefCntNode addRefCntNode) {
            if (lib.isNull(object)) {
                return PNone.NO_VALUE;
            }
            CApiContext cApiContext = context.getCApiContext();
            if (cApiContext != null) {
                return cApiContext.getPythonNativeObject(object, newRefProfile, validRefProfile, resurrectProfile, getRefCntNode, addRefCntNode);
            }
            return new PythonAbstractNativeObject(object);
        }

    }

    @GenerateUncached
    @ImportStatic({PGuards.class, CApiGuards.class})
    public abstract static class AsPythonObjectStealingNode extends AsPythonObjectBaseNode {

        @Specialization(guards = {"isForeignObject(object, plib, isForeignClassProfile)", "!isNativeWrapper(object)", "!isNativeNull(object)"}, limit = "1")
        static PythonAbstractObject doNativeObject(@SuppressWarnings("unused") CExtContext cextContext, TruffleObject object,
                        @SuppressWarnings("unused") @CachedLibrary("object") PythonObjectLibrary plib,
                        @Cached @SuppressWarnings("unused") IsBuiltinClassProfile isForeignClassProfile,
                        @Cached("createBinaryProfile()") ConditionProfile newRefProfile,
                        @Cached("createBinaryProfile()") ConditionProfile validRefProfile,
                        @Cached("createBinaryProfile()") ConditionProfile resurrectProfile,
                        @CachedLibrary("object") InteropLibrary lib,
                        @CachedContext(PythonLanguage.class) PythonContext context,
                        @Cached GetRefCntNode getRefCntNode,
                        @Cached AddRefCntNode addRefCntNode) {
            if (lib.isNull(object)) {
                return PNone.NO_VALUE;
            }
            CApiContext cApiContext = context.getCApiContext();
            if (cApiContext != null) {
                return cApiContext.getPythonNativeObject(object, newRefProfile, validRefProfile, resurrectProfile, getRefCntNode, addRefCntNode, true);
            }
            return new PythonAbstractNativeObject(object);
        }
    }

    @GenerateUncached
    @ImportStatic({PGuards.class, CApiGuards.class})
    public abstract static class WrapVoidPtrNode extends AsPythonObjectBaseNode {

        @Specialization(guards = {"isForeignObject(object, plib, isForeignClassProfile)", "!isNativeWrapper(object)", "!isNativeNull(object)"}, limit = "1")
        static Object doNativeObject(@SuppressWarnings("unused") CExtContext cextContext, TruffleObject object,
                        @SuppressWarnings("unused") @CachedLibrary("object") PythonObjectLibrary plib,
                        @Cached @SuppressWarnings("unused") IsBuiltinClassProfile isForeignClassProfile) {
            // TODO(fa): should we use a different wrapper for non-'PyObject*' pointers; they cannot
            // be used in the user value space but might be passed-through

            // do not modify reference count at all; this is for non-'PyObject*' pointers
            return new PythonAbstractNativeObject(object);
        }

    }

    // -----------------------------------------------------------------------------------------------------------------
    /**
     * Materializes a primitive value of a primitive native wrapper to ensure pointer equality.
     */
    @GenerateUncached
    @ImportStatic(CApiGuards.class)
    public abstract static class MaterializeDelegateNode extends Node {

        public abstract Object execute(PythonNativeWrapper object);

        @Specialization(guards = {"!isMaterialized(object, lib)", "object.isBool()"}, limit = "1")
        static PInt doBoolNativeWrapper(DynamicObjectNativeWrapper.PrimitiveNativeWrapper object,
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
        static PInt doByteNativeWrapper(DynamicObjectNativeWrapper.PrimitiveNativeWrapper object,
                        @SuppressWarnings("unused") @CachedLibrary("object") PythonNativeWrapperLibrary lib,
                        @Shared("factory") @Cached PythonObjectFactory factory) {
            PInt materializedInt = factory.createInt(object.getByte());
            object.setMaterializedObject(materializedInt);
            materializedInt.setNativeWrapper(object);
            return materializedInt;
        }

        @Specialization(guards = {"!isMaterialized(object, lib)", "object.isInt()"}, limit = "1")
        static PInt doIntNativeWrapper(DynamicObjectNativeWrapper.PrimitiveNativeWrapper object,
                        @SuppressWarnings("unused") @CachedLibrary("object") PythonNativeWrapperLibrary lib,
                        @Shared("factory") @Cached PythonObjectFactory factory) {
            PInt materializedInt = factory.createInt(object.getInt());
            object.setMaterializedObject(materializedInt);
            materializedInt.setNativeWrapper(object);
            return materializedInt;
        }

        @Specialization(guards = {"!isMaterialized(object, lib)", "object.isLong()"}, limit = "1")
        static PInt doLongNativeWrapper(DynamicObjectNativeWrapper.PrimitiveNativeWrapper object,
                        @SuppressWarnings("unused") @CachedLibrary("object") PythonNativeWrapperLibrary lib,
                        @Shared("factory") @Cached PythonObjectFactory factory) {
            PInt materializedInt = factory.createInt(object.getLong());
            object.setMaterializedObject(materializedInt);
            materializedInt.setNativeWrapper(object);
            return materializedInt;
        }

        @Specialization(guards = {"!isMaterialized(object, lib)", "object.isDouble()", "!isNaN(object)"}, limit = "1")
        static PFloat doDoubleNativeWrapper(DynamicObjectNativeWrapper.PrimitiveNativeWrapper object,
                        @SuppressWarnings("unused") @CachedLibrary("object") PythonNativeWrapperLibrary lib,
                        @Shared("factory") @Cached PythonObjectFactory factory) {
            PFloat materializedInt = factory.createFloat(object.getDouble());
            materializedInt.setNativeWrapper(object);
            object.setMaterializedObject(materializedInt);
            return materializedInt;
        }

        @Specialization(guards = {"!isMaterialized(object, lib)", "object.isDouble()", "isNaN(object)"}, limit = "1")
        static PFloat doDoubleNativeWrapperNaN(DynamicObjectNativeWrapper.PrimitiveNativeWrapper object,
                        @SuppressWarnings("unused") @CachedLibrary("object") PythonNativeWrapperLibrary lib,
                        @CachedContext(PythonLanguage.class) PythonContext context) {
            // Special case for double NaN: use singleton
            PFloat materializedFloat = context.getCore().getNaN();
            object.setMaterializedObject(materializedFloat);

            // If the NaN singleton already has a native wrapper, we may need to update the
            // pointer
            // of wrapper 'object' since the native code should see the same pointer.
            if (materializedFloat.getNativeWrapper() != null) {
                object.setNativePointer(lib.getNativePointer(materializedFloat.getNativeWrapper()));
            } else {
                materializedFloat.setNativeWrapper(object);
            }
            return materializedFloat;
        }

        @Specialization(guards = {"object.getClass() == cachedClass", "isMaterialized(object, lib)"}, limit = "1")
        static Object doMaterialized(DynamicObjectNativeWrapper.PrimitiveNativeWrapper object,
                        @CachedLibrary("object") PythonNativeWrapperLibrary lib,
                        @SuppressWarnings("unused") @Cached("object.getClass()") Class<? extends DynamicObjectNativeWrapper.PrimitiveNativeWrapper> cachedClass) {
            return lib.getDelegate(CompilerDirectives.castExact(object, cachedClass));
        }

        @Specialization(guards = {"!isPrimitiveNativeWrapper(object)", "object.getClass() == cachedClass"}, limit = "3")
        static Object doNativeWrapper(PythonNativeWrapper object,
                        @CachedLibrary("object") PythonNativeWrapperLibrary lib,
                        @SuppressWarnings("unused") @Cached("object.getClass()") Class<? extends PythonNativeWrapper> cachedClass) {
            return lib.getDelegate(CompilerDirectives.castExact(object, cachedClass));
        }

        @Specialization(guards = "!isPrimitiveNativeWrapper(object)", replaces = "doNativeWrapper", limit = "1")
        static Object doNativeWrapperGeneric(PythonNativeWrapper object,
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

        static boolean isMaterialized(DynamicObjectNativeWrapper.PrimitiveNativeWrapper wrapper, PythonNativeWrapperLibrary lib) {
            return wrapper.getMaterializedObject(lib) != null;
        }
    }

    // -----------------------------------------------------------------------------------------------------------------
    /**
     * use subclasses {@link ToJavaNode} and {@link ToJavaStealingNode}
     */
    abstract static class ToJavaBaseNode extends CExtToJavaNode {

        @Specialization
        static Object doWrapper(@SuppressWarnings("unused") CExtContext nativeContext, PythonNativeWrapper value,
                        @Exclusive @Cached AsPythonObjectNode toJavaNode) {
            return toJavaNode.execute(value);
        }

        @Specialization
        static PythonAbstractObject doPythonObject(@SuppressWarnings("unused") CExtContext nativeContext, PythonAbstractObject value) {
            return value;
        }

        @Specialization
        static String doString(@SuppressWarnings("unused") CExtContext nativeContext, String object) {
            return object;
        }

        @Specialization
        static boolean doBoolean(@SuppressWarnings("unused") CExtContext nativeContext, boolean b) {
            return b;
        }

        @Specialization
        static int doInt(@SuppressWarnings("unused") CExtContext nativeContext, int i) {
            // Note: Sulong guarantees that an integer won't be a pointer
            return i;
        }

        @Specialization
        static long doLong(@SuppressWarnings("unused") CExtContext nativeContext, long l) {
            return l;
        }

        @Specialization
        static byte doByte(@SuppressWarnings("unused") CExtContext nativeContext, byte b) {
            return b;
        }

        @Specialization
        static double doDouble(@SuppressWarnings("unused") CExtContext nativeContext, double d) {
            return d;
        }

        protected static boolean isForeignObject(Object obj) {
            return !(obj instanceof PythonAbstractObject || obj instanceof PythonNativeWrapper || obj instanceof String || obj instanceof Boolean || obj instanceof Integer ||
                            obj instanceof Long || obj instanceof Byte || obj instanceof Double);
        }
    }

    /**
     * Does the same conversion as the native function {@code to_java}. The node tries to avoid
     * calling the native function for resolving native handles.
     */
    @GenerateUncached
    public abstract static class ToJavaNode extends ToJavaBaseNode {

        @Specialization(guards = "isForeignObject(value)", limit = "1")
        static Object doForeign(@SuppressWarnings("unused") CExtContext nativeContext, Object value,
                        @Shared("resolveHandleNode") @Cached ResolveHandleNode resolveHandleNode,
                        @Shared("resolveNativeReferenceNode") @Cached ResolveNativeReferenceNode resolveNativeReferenceNode,
                        @Shared("toJavaNode") @Cached AsPythonObjectNode asPythonObjectNode,
                        @CachedLibrary("value") InteropLibrary interopLibrary,
                        @Cached("createBinaryProfile()") ConditionProfile isNullProfile) {
            // this is just a shortcut
            if (isNullProfile.profile(interopLibrary.isNull(value))) {
                return PNone.NO_VALUE;
            }
            return asPythonObjectNode.execute(resolveNativeReferenceNode.execute(resolveHandleNode.execute(value), false));
        }
    }

    /**
     * Does the same conversion as the native function {@code to_java}. The node tries to avoid
     * calling the native function for resolving native handles.
     */
    @GenerateUncached
    public abstract static class ToJavaStealingNode extends ToJavaBaseNode {

        @Specialization(guards = "isForeignObject(value)", limit = "1")
        static Object doForeign(@SuppressWarnings("unused") CExtContext nativeContext, Object value,
                        @Shared("resolveHandleNode") @Cached ResolveHandleNode resolveHandleNode,
                        @Shared("resolveNativeReferenceNode") @Cached ResolveNativeReferenceNode resolveNativeReferenceNode,
                        @Shared("toJavaStealingNode") @Cached AsPythonObjectStealingNode toJavaNode,
                        @CachedLibrary("value") InteropLibrary interopLibrary,
                        @Cached("createBinaryProfile()") ConditionProfile isNullProfile) {
            if (isNullProfile.profile(interopLibrary.isNull(value))) {
                return PNone.NO_VALUE;
            }
            return toJavaNode.execute(resolveNativeReferenceNode.execute(resolveHandleNode.execute(value), true));
        }
    }

    /**
     * Does the same conversion as the native function {@code native_pointer_to_java}. The node
     * tries to avoid calling the native function for resolving native handles.
     */
    @GenerateUncached
    public abstract static class VoidPtrToJavaNode extends ToJavaBaseNode {

        @Specialization(guards = "isForeignObject(value)", limit = "1")
        static Object doForeign(@SuppressWarnings("unused") CExtContext nativeContext, Object value,
                        @Shared("resolveHandleNode") @Cached ResolveHandleNode resolveHandleNode,
                        @Shared("resolveNativeReferenceNode") @Cached ResolveNativeReferenceNode resolveNativeReferenceNode,
                        @Shared("toJavaNode") @Cached AsPythonObjectNode asPythonObjectNode,
                        @CachedLibrary("value") InteropLibrary interopLibrary,
                        @Cached("createBinaryProfile()") ConditionProfile isNullProfile) {
            // this branch is not a shortcut; it actually returns a different object
            if (isNullProfile.profile(interopLibrary.isNull(value))) {
                return new PythonAbstractNativeObject((TruffleObject) value);
            }
            return asPythonObjectNode.execute(resolveNativeReferenceNode.execute(resolveHandleNode.execute(value), false));
        }
    }

    // -----------------------------------------------------------------------------------------------------------------
    @GenerateUncached
    public abstract static class AsCharPointerNode extends Node {
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
    public abstract static class FromCharPointerNode extends Node {
        public abstract Object execute(Object charPtr);

        // TODO(fa): add a specialization that handles 'PySequenceArrayWrapper' instances

        @Specialization
        PString execute(Object charPtr,
                        @Cached PythonObjectFactory factory) {
            return factory.createString(new NativeCharSequence(charPtr));
        }
    }

    // -----------------------------------------------------------------------------------------------------------------
    @GenerateUncached
    public abstract static class SizeofWCharNode extends Node {

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
    public abstract static class PointerCompareNode extends Node {
        public abstract boolean execute(String opName, Object a, Object b);

        private static boolean executeCFunction(int op, Object a, Object b, InteropLibrary interopLibrary, ImportCAPISymbolNode importCAPISymbolNode) {
            try {
                return (int) interopLibrary.execute(importCAPISymbolNode.execute(FUN_PTR_COMPARE), a, b, op) != 0;
            } catch (UnsupportedTypeException | ArityException | UnsupportedMessageException e) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
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
    @GenerateUncached
    public abstract static class AllToJavaNode extends PNodeWithContext {

        final Object[] execute(Object[] args) {
            return execute(args, 0);
        }

        abstract Object[] execute(Object[] args, int offset);

        @Specialization(guards = { //
                        "args.length == cachedLength", //
                        "offset == cachedOffset", //
                        "effectiveLen(cachedLength, cachedOffset) < 5"}, //
                        limit = "5")
        @ExplodeLoop
        static Object[] cached(Object[] args, @SuppressWarnings("unused") int offset,
                        @Cached("args.length") int cachedLength,
                        @Cached("offset") int cachedOffset,
                        @Cached("createNodes(args.length)") AsPythonObjectNode[] toJavaNodes) {
            int n = cachedLength - cachedOffset;
            Object[] output = new Object[n];
            for (int i = 0; i < n; i++) {
                output[i] = toJavaNodes[i].execute(args[i + cachedOffset]);
            }
            return output;
        }

        @Specialization(replaces = "cached")
        static Object[] uncached(Object[] args, int offset,
                        @Exclusive @Cached AsPythonObjectNode toJavaNode) {
            int len = args.length - offset;
            Object[] output = new Object[len];
            for (int i = 0; i < len; i++) {
                output[i] = toJavaNode.execute(args[i + offset]);
            }
            return output;
        }

        static int effectiveLen(int len, int offset) {
            return len - offset;
        }

        static AsPythonObjectNode[] createNodes(int n) {
            AsPythonObjectNode[] nodes = new AsPythonObjectNode[n];
            for (int i = 0; i < n; i++) {
                nodes[i] = AsPythonObjectNodeGen.create();
            }
            return nodes;
        }

        public static AllToJavaNode create() {
            return AllToJavaNodeGen.create();
        }
    }

    // -----------------------------------------------------------------------------------------------------------------
    public abstract static class ConvertArgsToSulongNode extends PNodeWithContext {

        public abstract void executeInto(Object[] args, int argsOffset, Object[] dest, int destOffset);

        protected static boolean isArgsOffsetPlus(int len, int off, int plus) {
            return len == off + plus;
        }

        protected static boolean isLeArgsOffsetPlus(int len, int off, int plus) {
            return len < plus + off;
        }

    }

    /**
     * Converts all arguments to native values.
     */
    public abstract static class AllToSulongNode extends ConvertArgsToSulongNode {
        @SuppressWarnings("unused")
        @Specialization(guards = {"args.length == argsOffset"})
        static void cached0(Object[] args, int argsOffset, Object[] dest, int destOffset) {
        }

        @Specialization(guards = {"isArgsOffsetPlus(args.length, argsOffset, 1)"})
        static void cached1(Object[] args, int argsOffset, Object[] dest, int destOffset,
                        @Cached ToBorrowedRefNode toSulongNode1) {
            dest[destOffset + 0] = toSulongNode1.execute(args[argsOffset + 0]);
        }

        @Specialization(guards = {"isArgsOffsetPlus(args.length, argsOffset, 2)"})
        static void cached2(Object[] args, int argsOffset, Object[] dest, int destOffset,
                        @Cached ToBorrowedRefNode toSulongNode1,
                        @Cached ToBorrowedRefNode toSulongNode2) {
            dest[destOffset + 0] = toSulongNode1.execute(args[argsOffset + 0]);
            dest[destOffset + 1] = toSulongNode2.execute(args[argsOffset + 1]);
        }

        @Specialization(guards = {"isArgsOffsetPlus(args.length, argsOffset, 3)"})
        static void cached3(Object[] args, int argsOffset, Object[] dest, int destOffset,
                        @Cached ToBorrowedRefNode toSulongNode1,
                        @Cached ToBorrowedRefNode toSulongNode2,
                        @Cached ToBorrowedRefNode toSulongNode3) {
            dest[destOffset + 0] = toSulongNode1.execute(args[argsOffset + 0]);
            dest[destOffset + 1] = toSulongNode2.execute(args[argsOffset + 1]);
            dest[destOffset + 2] = toSulongNode3.execute(args[argsOffset + 2]);
        }

        @Specialization(guards = {"args.length == cachedLength", "isLeArgsOffsetPlus(cachedLength, argsOffset, 8)"}, limit = "1", replaces = {"cached0", "cached1", "cached2", "cached3"})
        @ExplodeLoop
        static void cachedLoop(Object[] args, int argsOffset, Object[] dest, int destOffset,
                        @Cached("args.length") int cachedLength,
                        @Cached ToBorrowedRefNode toSulongNode) {
            for (int i = 0; i < cachedLength - argsOffset; i++) {
                dest[destOffset + i] = toSulongNode.execute(args[argsOffset + i]);
            }
        }

        @Specialization(replaces = {"cached0", "cached1", "cached2", "cached3", "cachedLoop"})
        static void uncached(Object[] args, int argsOffset, Object[] dest, int destOffset,
                        @Cached ToBorrowedRefNode toSulongNode) {
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

    /**
     * Converts the 1st (PyObject* self) and the 2nd (PyObject* const* args) argument to native
     * values as required for {@code METH_FASTCALL}.<br/>
     * Signature:
     * {@code PyObject* meth_fastcall(PyObject* self, PyObject* const* args, Py_ssize_t nargs)}
     */
    public abstract static class FastCallArgsToSulongNode extends ConvertArgsToSulongNode {

        @Specialization(guards = {"isArgsOffsetPlus(args.length, argsOffset, 3)"})
        static void doFastcallCached(Object[] args, int argsOffset, Object[] dest, int destOffset,
                        @Cached ToBorrowedRefNode toSulongNode1) {
            dest[destOffset + 0] = toSulongNode1.execute(args[argsOffset]);
            dest[destOffset + 1] = new PySequenceArrayWrapper(args[argsOffset + 1], Long.BYTES);
            dest[destOffset + 2] = args[argsOffset + 2];
        }

        @Specialization(guards = {"!isArgsOffsetPlus(args.length, argsOffset, 3)"})
        static void doError(Object[] args, int argsOffset, @SuppressWarnings("unused") Object[] dest, @SuppressWarnings("unused") int destOffset,
                        @Cached PRaiseNode raiseNode) {
            throw raiseNode.raise(TypeError, ErrorMessages.INVALID_ARGS_FOR_FASTCALL_METHOD, args.length - argsOffset);
        }

        public static FastCallArgsToSulongNode create() {
            return FastCallArgsToSulongNodeGen.create();
        }
    }

    /**
     * Converts for native signature:
     * {@code PyObject* meth_fastcallWithKeywords(PyObject* self, PyObject* const* args, Py_ssize_t nargs, PyObject* kwnames)}
     */
    public abstract static class FastCallWithKeywordsArgsToSulongNode extends ConvertArgsToSulongNode {

        @Specialization(guards = {"isArgsOffsetPlus(args.length, argsOffset, 4)"})
        static void doFastcallCached(Object[] args, int argsOffset, Object[] dest, int destOffset,
                        @Cached ToBorrowedRefNode toSulongNode1,
                        @Cached ToBorrowedRefNode toSulongNode4) {
            dest[destOffset + 0] = toSulongNode1.execute(args[argsOffset]);
            dest[destOffset + 1] = new PySequenceArrayWrapper(args[argsOffset + 1], Long.BYTES);
            dest[destOffset + 2] = args[argsOffset + 2];
            dest[destOffset + 3] = toSulongNode4.execute(args[argsOffset + 3]);
        }

        @Specialization(guards = {"!isArgsOffsetPlus(args.length, argsOffset, 4)"})
        static void doError(Object[] args, int argsOffset, @SuppressWarnings("unused") Object[] dest, @SuppressWarnings("unused") int destOffset,
                        @Cached PRaiseNode raiseNode) {
            throw raiseNode.raise(TypeError, ErrorMessages.INVALID_ARGS_FOR_FASTCALL_W_KEYWORDS_METHOD, args.length - argsOffset);
        }

        public static FastCallWithKeywordsArgsToSulongNode create() {
            return FastCallWithKeywordsArgsToSulongNodeGen.create();
        }
    }

    /**
     * Converts the 1st argument as required for {@code allocfunc}, {@code getattrfunc}, and
     * {@code ssizeargfunc}.
     */
    public abstract static class BinaryFirstToSulongNode extends ConvertArgsToSulongNode {

        @Specialization(guards = {"isArgsOffsetPlus(args.length, argsOffset, 2)"})
        static void doFastcallCached(Object[] args, int argsOffset, Object[] dest, int destOffset,
                        @Cached ToBorrowedRefNode toSulongNode1) {
            dest[destOffset + 0] = toSulongNode1.execute(args[argsOffset]);
            dest[destOffset + 1] = args[argsOffset + 1];
        }

        @Specialization(guards = {"!isArgsOffsetPlus(args.length, argsOffset, 2)"})
        static void doError(Object[] args, int argsOffset, @SuppressWarnings("unused") Object[] dest, @SuppressWarnings("unused") int destOffset,
                        @Cached PRaiseNode raiseNode) {
            throw raiseNode.raise(TypeError, ErrorMessages.INVALID_ARGS_FOR_ALLOCFUNC, args.length - argsOffset);
        }

        public static BinaryFirstToSulongNode create() {
            return BinaryFirstToSulongNodeGen.create();
        }
    }

    /**
     * Converts the 1st (self/class) and the 3rd argument as required for {@code setattrfunc},
     * {@code ssizeobjargproc}.
     */
    public abstract static class TernaryFirstThirdToSulongNode extends ConvertArgsToSulongNode {

        @Specialization(guards = {"isArgsOffsetPlus(args.length, argsOffset, 3)"})
        static void doFastcallCached(Object[] args, int argsOffset, Object[] dest, int destOffset,
                        @Cached ToBorrowedRefNode toSulongNode1,
                        @Cached ToBorrowedRefNode toSulongNode3) {
            dest[destOffset + 0] = toSulongNode1.execute(args[argsOffset]);
            dest[destOffset + 1] = args[argsOffset + 1];
            dest[destOffset + 2] = toSulongNode3.execute(args[argsOffset + 2]);
        }

        @Specialization(guards = {"!isArgsOffsetPlus(args.length, argsOffset, 3)"})
        static void doError(Object[] args, int argsOffset, @SuppressWarnings("unused") Object[] dest, @SuppressWarnings("unused") int destOffset,
                        @Cached PRaiseNode raiseNode) {
            throw raiseNode.raise(TypeError, ErrorMessages.INVALID_ARGS_FOR_METHOD, args.length - argsOffset);
        }

        public static TernaryFirstThirdToSulongNode create() {
            return TernaryFirstThirdToSulongNodeGen.create();
        }
    }

    /**
     * Converts the 1st (self/class) and the 2rd argument as required for {@code richcmpfunc}.
     */
    public abstract static class TernaryFirstSecondToSulongNode extends ConvertArgsToSulongNode {

        @Specialization(guards = {"isArgsOffsetPlus(args.length, argsOffset, 3)"})
        static void doFastcallCached(Object[] args, int argsOffset, Object[] dest, int destOffset,
                        @Cached ToBorrowedRefNode toSulongNode1,
                        @Cached ToBorrowedRefNode toSulongNode2) {
            dest[destOffset + 0] = toSulongNode1.execute(args[argsOffset]);
            dest[destOffset + 1] = toSulongNode2.execute(args[argsOffset + 1]);
            dest[destOffset + 2] = args[argsOffset + 2];
        }

        @Specialization(guards = {"!isArgsOffsetPlus(args.length, argsOffset, 3)"})
        static void doError(Object[] args, int argsOffset, @SuppressWarnings("unused") Object[] dest, @SuppressWarnings("unused") int destOffset,
                        @Cached PRaiseNode raiseNode) {
            throw raiseNode.raise(TypeError, ErrorMessages.INVALID_ARGS_FOR_METHOD, args.length - argsOffset);
        }

        public static TernaryFirstSecondToSulongNode create() {
            return TernaryFirstSecondToSulongNodeGen.create();
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
            // TODO(fa) according to CPython's 'PyComplex_AsCComplex', they still allow
            // subclasses
            // of PComplex
            if (result == PNone.NO_VALUE) {
                throw raiseNode.raise(PythonErrorType.TypeError, ErrorMessages.COMPLEX_RETURNED_NON_COMPLEX, value);
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
                        @CachedLibrary(limit = "3") PythonObjectLibrary lib,
                        @Cached IsBuiltinClassProfile classProfile,
                        @Cached CastToJavaDoubleNode castToJavaDoubleNode,
                        @Cached PRaiseNode raiseNode) {
            // IMPORTANT: this should implement the behavior like 'PyFloat_AsDouble'. So, if it
            // is a
            // float object, use the value and do *NOT* call '__float__'.
            if (PGuards.isPFloat(value)) {
                return ((PFloat) value).getValue();
            }

            Object result = callFloatFunc.executeObject(value, __FLOAT__);
            // TODO(fa) according to CPython's 'PyFloat_AsDouble', they still allow subclasses
            // of
            // PFloat
            if (classProfile.profileClass(lib.getLazyPythonClass(result), PythonBuiltinClassType.PFloat)) {
                return castToJavaDoubleNode.execute(result);
            }
            throw raiseNode.raise(PythonErrorType.TypeError, ErrorMessages.RETURNED_NON_FLOAT, value, __FLOAT__, result);
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

        public abstract Object execute(Object arg);

        @Specialization(guards = "value.length() == 1")
        static long doString(String value) {
            return value.charAt(0);
        }

        @Specialization
        static long doBoolean(boolean value) {
            return value ? 1 : 0;
        }

        @Specialization
        static long doByte(byte value) {
            return value;
        }

        @Specialization
        static long doInt(int value) {
            return value;
        }

        @Specialization
        static long doLong(long value) {
            return value;
        }

        @Specialization
        static long doDouble(double value) {
            return (long) value;
        }

        @Specialization
        static long doPInt(PInt value) {
            return value.longValue();
        }

        @Specialization
        static long doPFloat(PFloat value) {
            return (long) value.getValue();
        }

        @Specialization
        static Object doPythonNativeVoidPtr(PythonNativeVoidPtr object) {
            return object.object;
        }

        @Specialization(guards = "!object.isDouble()")
        static long doLongNativeWrapper(PrimitiveNativeWrapper object) {
            return object.getLong();
        }

        @Specialization(guards = "object.isDouble()")
        static long doDoubleNativeWrapper(PrimitiveNativeWrapper object) {
            return (long) object.getDouble();
        }

        @Specialization(limit = "1")
        static Object run(PythonNativeWrapper value,
                        @CachedLibrary("value") PythonNativeWrapperLibrary lib,
                        @Cached CastToNativeLongNode recursive) {
            // TODO(fa) this specialization should eventually go away
            return recursive.execute(lib.getDelegate(value));
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
            throw raiseNode.raise(PythonErrorType.SystemError, ErrorMessages.UNSUPPORTED_TARGET_SIZE, targetTypeSize);
        }

        @Specialization(guards = "targetTypeSize == 4")
        @SuppressWarnings("unused")
        static int doLongToInt32(long obj, int signed, int targetTypeSize, boolean exact,
                        @Shared("raiseNode") @Cached PRaiseNode raiseNode) {
            throw raiseNode.raise(PythonErrorType.OverflowError, ErrorMessages.PYTHON_INT_TOO_LARGE_TO_CONV_TO_C_TYPE, targetTypeSize);
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
            throw raiseNode.raise(PythonErrorType.SystemError, ErrorMessages.UNSUPPORTED_TARGET_SIZE, targetTypeSize);
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
            throw raiseNode.raise(PythonErrorType.OverflowError, ErrorMessages.PYTHON_INT_TOO_LARGE_TO_CONV_TO_C_TYPE, targetTypeSize);
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
            throw raiseNode.raise(PythonErrorType.OverflowError, ErrorMessages.PYTHON_INT_TOO_LARGE_TO_CONV_TO_C_TYPE, targetTypeSize);
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
            throw raiseNode.raise(PythonErrorType.SystemError, ErrorMessages.UNSUPPORTED_TARGET_SIZE, targetTypeSize);
        }

        @Specialization(replaces = {"doIntToInt32", "doIntToInt64", "doIntToOther", "doLongToInt32", "doLongToInt64", "doVoidPtrToI64", "doPIntToInt32", "doPIntToInt64"})
        static Object doGeneric(Object obj, @SuppressWarnings("unused") int signed, int targetTypeSize, boolean exact,
                        @Cached LookupAndCallUnaryDynamicNode callIndexNode,
                        @Cached LookupAndCallUnaryDynamicNode callIntNode,
                        @Cached AsNativePrimitiveNode recursive,
                        @Exclusive @Cached BranchProfile noIntProfile,
                        @Shared("raiseNode") @Cached PRaiseNode raiseNode) {

            Object result = callIndexNode.executeObject(obj, SpecialMethodNames.__INDEX__);
            if (result == PNone.NO_VALUE) {
                result = callIntNode.executeObject(obj, SpecialMethodNames.__INT__);
                if (result == PNone.NO_VALUE) {
                    noIntProfile.enter();
                    throw raiseNode.raise(PythonErrorType.TypeError, ErrorMessages.INTEGER_REQUIRED_GOT, result);
                }
            }
            // n.b. this check is important to avoid endless recursions; it will ensure that
            // 'doGeneric' is not triggered in the recursive node
            if (!(isIntegerType(result))) {
                throw raiseNode.raise(PythonErrorType.TypeError, ErrorMessages.INDEX_RETURNED_NON_INT, result);
            }
            return recursive.execute(result, signed, targetTypeSize, exact);
        }

        static boolean isIntegerType(Object obj) {
            return PGuards.isInteger(obj) || PGuards.isPInt(obj) || obj instanceof PythonNativeVoidPtr;
        }
    }

    // -----------------------------------------------------------------------------------------------------------------
    @GenerateUncached
    public abstract static class PCallCapiFunction extends Node {

        public final Object call(String name, Object... args) {
            return execute(name, args);
        }

        public abstract Object execute(String name, Object[] args);

        @Specialization
        static Object doIt(String name, Object[] args,
                        @Cached ImportCExtSymbolNode importCExtSymbolNode,
                        @CachedContext(PythonLanguage.class) PythonContext context,
                        @CachedLibrary(limit = "1") InteropLibrary interopLibrary,
                        @Cached BranchProfile profile,
                        @Cached PRaiseNode raiseNode) {
            try {
                return interopLibrary.execute(importCExtSymbolNode.execute(context.getCApiContext(), name), args);
            } catch (UnsupportedTypeException | ArityException e) {
                profile.enter();
                throw raiseNode.raise(PythonBuiltinClassType.TypeError, e);
            } catch (UnsupportedMessageException e) {
                profile.enter();
                throw raiseNode.raise(PythonBuiltinClassType.TypeError, ErrorMessages.CAPI_SYM_NOT_CALLABLE, name);
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
        private final Class<T> nodeClass;

        public MayRaiseNodeFactory(T node) {
            this.node = node;
            this.nodeClass = determineNodeClass(node);
        }

        @Override
        public T createNode(Object... arguments) {
            return NodeUtil.cloneNode(node);
        }

        @Override
        public Class<T> getNodeClass() {
            return nodeClass;
        }

        @SuppressWarnings("unchecked")
        private static <T> Class<T> determineNodeClass(T node) {
            CompilerAsserts.neverPartOfCompilation();
            Class<T> nodeClass = (Class<T>) node.getClass();
            GeneratedBy genBy = nodeClass.getAnnotation(GeneratedBy.class);
            if (genBy != null) {
                nodeClass = (Class<T>) genBy.value();
                assert nodeClass.isAssignableFrom(node.getClass());
            }
            return nodeClass;
        }

        @Override
        public List<List<Class<?>>> getNodeSignatures() {
            throw new IllegalAccessError();
        }

        @Override
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
        static boolean doFalse(@SuppressWarnings("unused") PythonNativeWrapper obj) {
            return false;
        }

        @Specialization(guards = "lib.isNative(obj)", limit = "1")
        @SuppressWarnings("unused")
        static boolean doNative(PythonNativeWrapper obj,
                        @CachedLibrary("obj") PythonNativeWrapperLibrary lib) {
            return true;
        }

        @Specialization(limit = "1", replaces = {"doFalse", "doNative"})
        static boolean doGeneric(PythonNativeWrapper obj,
                        @CachedLibrary("obj") PythonNativeWrapperLibrary lib) {
            return lib.isNative(obj);
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
    @TypeSystemReference(PythonTypes.class)
    public abstract static class GetTypeMemberNode extends PNodeWithContext {
        public abstract Object execute(Object obj, NativeMember nativeMember);

        /*
         * A note about the logic here, and why this is fine: the cachedObj is from a particular
         * native context, so we can be sure that the "nativeClassStableAssumption" (which is
         * per-context) is from the context in which this native object was created.
         */
        @Specialization(guards = {"lib.isIdentical(cachedObj, obj, lib)", "memberName == cachedMemberName"}, //
                        limit = "1", //
                        assumptions = {"getNativeClassStableAssumption(cachedObj)", "singleContextAssumption()"})
        public Object doCachedObj(@SuppressWarnings("unused") PythonAbstractNativeObject obj, @SuppressWarnings("unused") NativeMember memberName,
                        @Cached("obj") @SuppressWarnings("unused") PythonAbstractNativeObject cachedObj,
                        @CachedLibrary(limit = "2") @SuppressWarnings("unused") InteropLibrary lib,
                        @Cached("memberName") @SuppressWarnings("unused") NativeMember cachedMemberName,
                        @Cached("doSlowPath(obj, memberName)") Object result) {
            return result;
        }

        @Specialization(guards = "memberName == cachedMemberName", limit = "1", replaces = "doCachedObj")
        public Object doCachedMember(Object self, @SuppressWarnings("unused") NativeMember memberName,
                        @SuppressWarnings("unused") @Cached("memberName") NativeMember cachedMemberName,
                        @Cached("getterFuncName(memberName)") String getterName,
                        @Shared("toSulong") @Cached ToSulongNode toSulong,
                        @Cached(value = "createForMember(memberName)", uncached = "getUncachedForMember(memberName)") AsPythonObjectBaseNode asPythonObject,
                        @Shared("callCapi") @Cached PCallCapiFunction callGetTpDictNode) {
            assert isNativeTypeObject(self);
            return asPythonObject.execute(callGetTpDictNode.call(getterName, toSulong.execute(self)));
        }

        @Specialization(replaces = "doCachedMember")
        public Object doUncached(Object self, NativeMember memberName,
                        @Shared("toSulong") @Cached ToSulongNode toSulong,
                        @Cached AsPythonObjectNode asPythonObject,
                        @Cached WrapVoidPtrNode wrapVoidPtrNode,
                        @Shared("callCapi") @Cached PCallCapiFunction callGetTpDictNode) {
            assert isNativeTypeObject(self);
            Object value = callGetTpDictNode.call(getterFuncName(memberName), toSulong.execute(self));
            if (memberName.getType() == NativeMemberType.OBJECT) {
                return asPythonObject.execute(value);
            }
            return wrapVoidPtrNode.execute(value);
        }

        protected Object doSlowPath(Object obj, NativeMember memberName) {
            String getterFuncName = getterFuncName(memberName);
            return getUncachedForMember(memberName).execute(PCallCapiFunction.getUncached().call(getterFuncName, ToSulongNode.getUncached().execute(obj)));
        }

        protected String getterFuncName(NativeMember memberName) {
            String name = "get_" + memberName.getMemberName();
            assert NativeCAPISymbols.isValid(name) : "invalid native member getter function " + name;
            return name;
        }

        static AsPythonObjectBaseNode createForMember(NativeMember member) {
            if (member.getType() == NativeMemberType.OBJECT) {
                return AsPythonObjectNodeGen.create();
            }
            return WrapVoidPtrNodeGen.create();
        }

        static AsPythonObjectBaseNode getUncachedForMember(NativeMember member) {
            if (member.getType() == NativeMemberType.OBJECT) {
                return AsPythonObjectNodeGen.getUncached();
            }
            return WrapVoidPtrNodeGen.getUncached();
        }

        protected Assumption getNativeClassStableAssumption(PythonNativeClass clazz) {
            return PythonLanguage.getContext().getNativeClassStableAssumption(clazz, true).getAssumption();
        }

        private static boolean isNativeTypeObject(Object self) {
            return IsBuiltinClassProfile.profileClassSlowPath(PythonObjectLibrary.getUncached().getLazyPythonClass(self), PythonBuiltinClassType.PythonClass);
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

        public abstract Object execute(Object cls, NativeMember nativeMemberName, Object managedMemberName);

        @Specialization
        static Object doSingleContext(Object cls, NativeMember nativeMemberName, Object managedMemberName,
                        @Cached GetMroStorageNode getMroNode,
                        @Cached SequenceStorageNodes.LenNode lenNode,
                        @Cached SequenceStorageNodes.GetItemDynamicNode getItemNode,
                        @Cached("createForceType()") ReadAttributeFromObjectNode readAttrNode,
                        @Cached GetTypeMemberNode getTypeMemberNode) {

            MroSequenceStorage mroStorage = getMroNode.execute(cls);
            int n = lenNode.execute(mroStorage);

            for (int i = 0; i < n; i++) {
                PythonAbstractClass mroCls = (PythonAbstractClass) getItemNode.execute(mroStorage, i);
                Object result;
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

        public final void execute(PException e) {
            execute(null, e);
        }

        @Specialization
        static void setCurrentException(Frame frame, PException e,
                        @Cached GetCurrentFrameRef getCurrentFrameRef,
                        @Shared("context") @CachedContext(PythonLanguage.class) PythonContext context) {
            // TODO connect f_back
            getCurrentFrameRef.execute(frame).markAsEscaped();
            context.setCurrentException(e);
        }
    }

    @GenerateUncached
    public abstract static class PRaiseNativeNode extends Node {

        public final int raiseInt(Frame frame, int errorValue, Object errType, String format, Object... arguments) {
            return executeInt(frame, errorValue, errType, format, arguments);
        }

        public final Object raise(Frame frame, Object errorValue, Object errType, String format, Object... arguments) {
            return execute(frame, errorValue, errType, format, arguments);
        }

        public final int raiseIntWithoutFrame(int errorValue, Object errType, String format, Object... arguments) {
            return executeInt(null, errorValue, errType, format, arguments);
        }

        public final Object raiseWithoutFrame(Object errorValue, Object errType, String format, Object... arguments) {
            return execute(null, errorValue, errType, format, arguments);
        }

        public abstract Object execute(Frame frame, Object errorValue, Object errType, String format, Object[] arguments);

        public abstract int executeInt(Frame frame, int errorValue, Object errType, String format, Object[] arguments);

        @Specialization
        static int doInt(Frame frame, int errorValue, Object errType, String format, Object[] arguments,
                        @Shared("raiseNode") @Cached PRaiseNode raiseNode,
                        @Shared("transformExceptionToNativeNode") @Cached TransformExceptionToNativeNode transformExceptionToNativeNode) {
            raiseNative(frame, errType, format, arguments, raiseNode, transformExceptionToNativeNode);
            return errorValue;
        }

        @Specialization
        static Object doObject(Frame frame, Object errorValue, Object errType, String format, Object[] arguments,
                        @Shared("raiseNode") @Cached PRaiseNode raiseNode,
                        @Shared("transformExceptionToNativeNode") @Cached TransformExceptionToNativeNode transformExceptionToNativeNode) {
            raiseNative(frame, errType, format, arguments, raiseNode, transformExceptionToNativeNode);
            return errorValue;
        }

        public static void raiseNative(Frame frame, Object errType, String format, Object[] arguments, PRaiseNode raiseNode,
                        TransformExceptionToNativeNode transformExceptionToNativeNode) {
            try {
                throw raiseNode.execute(errType, PNone.NO_VALUE, format, arguments);
            } catch (PException p) {
                transformExceptionToNativeNode.execute(frame, p);
            }
        }
    }

    @GenerateUncached
    @ImportStatic(CApiGuards.class)
    public abstract static class AddRefCntNode extends PNodeWithContext {

        public abstract Object execute(Object object, long value);

        public final Object inc(Object object) {
            return execute(object, 1);
        }

        @Specialization
        static Object doNativeWrapper(PythonNativeWrapper nativeWrapper, long value) {
            assert value >= 0 : "adding negative reference count; dealloc might not happen";
            nativeWrapper.setRefCount(nativeWrapper.getRefCount() + value);
            return nativeWrapper;
        }

        @Specialization(guards = {"!isNativeWrapper(object)", "lib.hasMembers(object)"}, //
                        rewriteOn = {UnknownIdentifierException.class, UnsupportedMessageException.class, UnsupportedTypeException.class, CannotCastException.class}, //
                        limit = "1")
        static Object doNativeObjectByMember(Object object, long value,
                        @CachedContext(PythonLanguage.class) PythonContext context,
                        @Cached CastToJavaLongLossyNode castToJavaLongNode,
                        @CachedLibrary("object") InteropLibrary lib) throws UnknownIdentifierException, UnsupportedMessageException, UnsupportedTypeException, CannotCastException {
            CApiContext cApiContext = context.getCApiContext();
            if (!lib.isNull(object) && cApiContext != null) {
                assert value >= 0 : "adding negative reference count; dealloc might not happen";
                cApiContext.checkAccess(object, lib);
                long refCnt = castToJavaLongNode.execute(lib.readMember(object, OB_REFCNT.getMemberName()));
                lib.writeMember(object, OB_REFCNT.getMemberName(), refCnt + value);
            }
            return object;
        }

        @Specialization(guards = "!isNativeWrapper(object)", limit = "2", replaces = "doNativeObjectByMember")
        static Object doNativeObject(Object object, long value,
                        @CachedContext(PythonLanguage.class) PythonContext context,
                        @Cached PCallCapiFunction callAddRefCntNode,
                        @CachedLibrary("object") InteropLibrary lib) {
            CApiContext cApiContext = context.getCApiContext();
            if (!lib.isNull(object) && cApiContext != null) {
                assert value >= 0 : "adding negative reference count; dealloc might not happen";
                cApiContext.checkAccess(object, lib);
                callAddRefCntNode.call(NativeCAPISymbols.FUN_ADDREF, object, value);
            }
            return object;
        }
    }

    @GenerateUncached
    @ImportStatic(CApiGuards.class)
    public abstract static class SubRefCntNode extends PNodeWithContext {
        private static final TruffleLogger LOGGER = PythonLanguage.getLogger(SubRefCntNode.class);

        public final long dec(Object object) {
            return execute(object, 1);
        }

        public abstract long execute(Object object, long value);

        @Specialization
        static long doNativeWrapper(PythonNativeWrapper nativeWrapper, long value,
                        @Cached FreeNode freeNode,
                        @Cached BranchProfile negativeProfile) {
            long refCount = nativeWrapper.getRefCount() - value;
            nativeWrapper.setRefCount(refCount);
            if (refCount == 0) {
                // 'freeNode' acts as a branch profile
                freeNode.execute(nativeWrapper);
            } else if (refCount < 0) {
                negativeProfile.enter();
                LOGGER.severe(() -> "native wrapper has negative ref count: " + nativeWrapper);
            }
            return refCount;
        }

        @Specialization(guards = "!isNativeWrapper(object)", limit = "2")
        static long doNativeObject(Object object, long value,
                        @CachedContext(PythonLanguage.class) PythonContext context,
                        @Cached PCallCapiFunction callAddRefCntNode,
                        @CachedLibrary("object") InteropLibrary lib) {
            CApiContext cApiContext = context.getCApiContext();
            if (!lib.isNull(object) && cApiContext != null) {
                cApiContext.checkAccess(object, lib);
                long newRefcnt = (long) callAddRefCntNode.call(NativeCAPISymbols.FUN_SUBREF, object, value);
                if (context.getOption(PythonOptions.TraceNativeMemory) && newRefcnt < 0) {
                    LOGGER.severe(() -> "object has negative ref count: " + CApiContext.asHex(object));
                }
                return newRefcnt;
            }
            return 1;
        }
    }

    @GenerateUncached
    @ImportStatic(PGuards.class)
    public abstract static class ClearNativeWrapperNode extends Node {

        public abstract void execute(Object delegate, PythonNativeWrapper nativeWrapper);

        @Specialization(guards = "!isPrimitiveNativeWrapper(nativeWrapper)")
        static void doPythonAbstractObject(PythonAbstractObject delegate, PythonNativeWrapper nativeWrapper,
                        @Cached("createCountingProfile()") ConditionProfile hasHandleValidAssumptionProfile) {
            // For non-temporary wrappers (all wrappers that need to preserve identity):
            // If this assertion fails, it indicates that the native code still uses a free'd native
            // wrapper.
            // TODO(fa): explicitly mark native wrappers to be identity preserving
            assert !(nativeWrapper instanceof PythonObjectNativeWrapper) || delegate.getNativeWrapper() == nativeWrapper : "inconsistent native wrappers";
            delegate.clearNativeWrapper(hasHandleValidAssumptionProfile);
        }

        @Specialization(guards = "delegate == null")
        static void doPrimitiveNativeWrapper(@SuppressWarnings("unused") Object delegate, PrimitiveNativeWrapper nativeWrapper,
                        @Cached("createCountingProfile()") ConditionProfile hasHandleValidAssumptionProfile,
                        @Shared("contextRef") @CachedContext(PythonLanguage.class) ContextReference<PythonContext> contextRef) {
            assert !isSmallIntegerWrapperSingleton(contextRef, nativeWrapper) : "clearing primitive native wrapper singleton of small integer";
            Assumption handleValidAssumption = nativeWrapper.getHandleValidAssumption();
            if (hasHandleValidAssumptionProfile.profile(handleValidAssumption != null)) {
                PythonNativeWrapper.invalidateAssumption(handleValidAssumption);
            }
        }

        @Specialization(guards = "delegate != null")
        static void doPrimitiveNativeWrapperMaterialized(PythonAbstractObject delegate, PrimitiveNativeWrapper nativeWrapper,
                        @Cached("createBinaryProfile()") ConditionProfile profile,
                        @Cached("createCountingProfile()") ConditionProfile hasHandleValidAssumptionProfile,
                        @Shared("contextRef") @CachedContext(PythonLanguage.class) ContextReference<PythonContext> contextRef) {
            if (profile.profile(delegate.getNativeWrapper() == nativeWrapper)) {
                assert !isSmallIntegerWrapperSingleton(contextRef, nativeWrapper) : "clearing primitive native wrapper singleton of small integer";
                delegate.clearNativeWrapper(hasHandleValidAssumptionProfile);
            }
        }

        @Specialization(guards = {"delegate != null", "!isAnyPythonObject(delegate)"})
        static void doOther(@SuppressWarnings("unused") Object delegate, @SuppressWarnings("unused") PythonNativeWrapper nativeWrapper) {
            assert !isPrimitiveNativeWrapper(nativeWrapper);
            // ignore
        }

        static boolean isPrimitiveNativeWrapper(PythonNativeWrapper nativeWrapper) {
            return nativeWrapper instanceof PrimitiveNativeWrapper;
        }

        private static boolean isSmallIntegerWrapperSingleton(ContextReference<PythonContext> contextRef, PrimitiveNativeWrapper nativeWrapper) {
            return CApiGuards.isSmallIntegerWrapper(nativeWrapper) && ToSulongNode.doLongSmall(null, nativeWrapper.getLong(), contextRef) == nativeWrapper;
        }

    }

    @GenerateUncached
    public abstract static class GetRefCntNode extends PNodeWithContext {

        public abstract long execute(Object ptrObject);

        @Specialization(limit = "2", rewriteOn = {UnknownIdentifierException.class, UnsupportedMessageException.class})
        static long doNativeObjectTyped(Object ptrObject,
                        @CachedContext(PythonLanguage.class) PythonContext context,
                        @Cached PCallCapiFunction callGetObRefCntNode,
                        @CachedLibrary("ptrObject") InteropLibrary lib,
                        @Cached CastToJavaLongLossyNode castToJavaLongNode) throws UnknownIdentifierException, UnsupportedMessageException {
            if (!lib.isNull(ptrObject)) {
                CApiContext cApiContext = context.getCApiContext();
                if (cApiContext != null) {
                    cApiContext.checkAccess(ptrObject, lib);
                }

                // directly reading the member is only possible if the pointer object is typed but
                // if so, it is the faster way
                if (lib.hasMembers(ptrObject)) {
                    return castToJavaLongNode.execute(lib.readMember(ptrObject, OB_REFCNT.getMemberName()));
                }
                if (context.getCApiContext() != null) {
                    return castToJavaLongNode.execute(callGetObRefCntNode.call(NativeCAPISymbols.FUN_GET_OB_REFCNT, ptrObject));
                }
            }
            return 0;
        }

        @Specialization(limit = "2", replaces = "doNativeObjectTyped")
        static long doNativeObject(Object ptrObject,
                        @CachedContext(PythonLanguage.class) PythonContext context,
                        @Cached PCallCapiFunction callGetObRefCntNode,
                        @CachedLibrary("ptrObject") InteropLibrary lib,
                        @Cached CastToJavaLongLossyNode castToJavaLongNode) {
            if (!lib.isNull(ptrObject)) {
                CApiContext cApiContext = context.getCApiContext();
                if (cApiContext != null) {
                    cApiContext.checkAccess(ptrObject, lib);
                }
                if (context.getCApiContext() != null) {
                    return castToJavaLongNode.execute(callGetObRefCntNode.call(NativeCAPISymbols.FUN_GET_OB_REFCNT, ptrObject));
                }
            }
            return 0;
        }
    }

    @GenerateUncached
    public abstract static class ResolveHandleNode extends Node {

        public abstract Object execute(Object pointerObject);

        public abstract Object executeLong(long pointer);

        @Specialization(limit = "3", //
                        guards = {"cachedPointer == pointer", "cachedValue != null"}, //
                        assumptions = "singleContextAssumption()", //
                        rewriteOn = InvalidAssumptionException.class)
        static PythonNativeWrapper resolveLongCached(@SuppressWarnings("unused") long pointer,
                        @Cached("pointer") @SuppressWarnings("unused") long cachedPointer,
                        @Cached("resolveHandleUncached(pointer)") PythonNativeWrapper cachedValue,
                        @Cached("getHandleValidAssumption(cachedValue)") Assumption associationValidAssumption) throws InvalidAssumptionException {
            associationValidAssumption.check();
            return cachedValue;
        }

        @Specialization(limit = "3", //
                        guards = {"isSame(lib, cachedPointerObject, pointerObject)", "cachedValue != null"}, //
                        assumptions = "singleContextAssumption()", //
                        rewriteOn = InvalidAssumptionException.class)
        static PythonNativeWrapper resolveObjectCached(@SuppressWarnings("unused") Object pointerObject,
                        @Cached("pointerObject") @SuppressWarnings("unused") Object cachedPointerObject,
                        @CachedLibrary(limit = "3") @SuppressWarnings("unused") InteropLibrary lib,
                        @Cached("resolveHandleUncached(pointerObject)") PythonNativeWrapper cachedValue,
                        @Cached("getHandleValidAssumption(cachedValue)") Assumption associationValidAssumption) throws InvalidAssumptionException {
            associationValidAssumption.check();
            return cachedValue;
        }

        @Specialization(replaces = {"resolveLongCached", "resolveObjectCached"})
        static Object resolveGeneric(Object pointerObject,
                        @Cached PCallCapiFunction callTruffleCannotBeHandleNode,
                        @Cached PCallCapiFunction callTruffleManagedFromHandleNode) {
            if (!((boolean) callTruffleCannotBeHandleNode.call(NativeCAPISymbols.FUN_TRUFFLE_CANNOT_BE_HANDLE, pointerObject))) {
                return callTruffleManagedFromHandleNode.call(NativeCAPISymbols.FUN_TRUFFLE_MANAGED_FROM_HANDLE, pointerObject);
            }
            // In this case, it cannot be a handle so we can just return the pointer object. It
            // could, of course, still be a native pointer.
            return pointerObject;
        }

        static PythonNativeWrapper resolveHandleUncached(Object pointerObject) {
            CompilerAsserts.neverPartOfCompilation();
            if (!((boolean) PCallCapiFunction.getUncached().call(NativeCAPISymbols.FUN_TRUFFLE_CANNOT_BE_HANDLE, pointerObject))) {
                Object resolved = PCallCapiFunction.getUncached().call(NativeCAPISymbols.FUN_TRUFFLE_MANAGED_FROM_HANDLE, pointerObject);
                if (resolved instanceof PythonNativeWrapper) {
                    return (PythonNativeWrapper) resolved;
                }
            }
            // In this case, it cannot be a handle so we return 'null' to indicate that it should
            // not be cached.
            return null;
        }

        static boolean isSame(InteropLibrary lib, Object left, Object right) {
            return lib.isIdentical(left, right, lib);
        }

        static Assumption singleContextAssumption() {
            return PythonLanguage.getCurrent().singleContextAssumption;
        }

        static Assumption getHandleValidAssumption(PythonNativeWrapper nativeWrapper) {
            return nativeWrapper.ensureHandleValidAssumption();
        }
    }

    /**
     * Depending on the object's type, the size may need to be computed in very different ways. E.g.
     * any PyVarObject usually returns the number of contained elements.
     */
    @GenerateUncached
    @ImportStatic(PythonOptions.class)
    abstract static class ObSizeNode extends Node {

        public abstract long execute(Object object);

        @Specialization
        static long doInteger(@SuppressWarnings("unused") int object,
                        @Shared("context") @CachedContext(PythonLanguage.class) PythonContext context) {
            return doLong(object, context);
        }

        @Specialization
        static long doLong(@SuppressWarnings("unused") long object,
                        @Shared("context") @CachedContext(PythonLanguage.class) PythonContext context) {
            long t = PInt.abs(object);
            int sign = object < 0 ? -1 : 1;
            int size = 0;
            while (t != 0) {
                ++size;
                t >>= context.getCApiContext().getPyLongBitsInDigit();
            }
            return size * sign;
        }

        @Specialization
        static long doPInt(PInt object,
                        @Shared("context") @CachedContext(PythonLanguage.class) PythonContext context) {
            return ((PInt.bitLength(object.abs()) - 1) / context.getCApiContext().getPyLongBitsInDigit() + 1) * (object.isNegative() ? -1 : 1);
        }

        @Specialization(limit = "getCallSiteInlineCacheMaxDepth()", guards = "isFallback(object)")
        static long doOther(Object object,
                        @CachedLibrary("object") PythonObjectLibrary lib) {
            try {
                return lib.length(object);
            } catch (PException e) {
                return -1;
            }
        }

        static boolean isFallback(Object object) {
            return !(object instanceof PInt || object instanceof Integer || object instanceof Long);
        }
    }

}
