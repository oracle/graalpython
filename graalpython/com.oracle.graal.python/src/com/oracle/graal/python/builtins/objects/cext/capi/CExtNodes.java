/*
 * Copyright (c) 2018, 2022, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.objects.cext.capi;

import static com.oracle.graal.python.builtins.objects.cext.capi.NativeCAPISymbol.FUN_GET_OB_TYPE;
import static com.oracle.graal.python.builtins.objects.cext.capi.NativeCAPISymbol.FUN_GET_PYMODULEDEF_M_METHODS;
import static com.oracle.graal.python.builtins.objects.cext.capi.NativeCAPISymbol.FUN_GET_PYMODULEDEF_M_SLOTS;
import static com.oracle.graal.python.builtins.objects.cext.capi.NativeCAPISymbol.FUN_POLYGLOT_FROM_TYPED;
import static com.oracle.graal.python.builtins.objects.cext.capi.NativeCAPISymbol.FUN_PTR_ADD;
import static com.oracle.graal.python.builtins.objects.cext.capi.NativeCAPISymbol.FUN_PTR_COMPARE;
import static com.oracle.graal.python.builtins.objects.cext.capi.NativeCAPISymbol.FUN_PY_FLOAT_AS_DOUBLE;
import static com.oracle.graal.python.builtins.objects.cext.capi.NativeCAPISymbol.FUN_PY_TRUFFLE_BYTE_ARRAY_TO_NATIVE;
import static com.oracle.graal.python.builtins.objects.cext.capi.NativeCAPISymbol.FUN_PY_TRUFFLE_MEMORYVIEW_FROM_OBJECT;
import static com.oracle.graal.python.builtins.objects.cext.capi.NativeCAPISymbol.FUN_PY_TRUFFLE_STRING_TO_CSTR;
import static com.oracle.graal.python.builtins.objects.cext.capi.NativeMember.MD_STATE;
import static com.oracle.graal.python.builtins.objects.cext.capi.NativeMember.OB_REFCNT;
import static com.oracle.graal.python.nodes.PGuards.isTruffleString;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___COMPLEX__;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.SystemError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.TypeError;
import static com.oracle.graal.python.util.PythonUtils.TS_ENCODING;
import static com.oracle.graal.python.util.PythonUtils.toTruffleStringUncached;
import static com.oracle.graal.python.util.PythonUtils.tsLiteral;

import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.Python3Core;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.modules.BuiltinFunctions.GetAttrNode;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.PythonAbstractObject;
import com.oracle.graal.python.builtins.objects.bytes.PByteArray;
import com.oracle.graal.python.builtins.objects.bytes.PBytes;
import com.oracle.graal.python.builtins.objects.bytes.PBytesLike;
import com.oracle.graal.python.builtins.objects.cext.PythonAbstractNativeObject;
import com.oracle.graal.python.builtins.objects.cext.PythonNativeClass;
import com.oracle.graal.python.builtins.objects.cext.PythonNativeObject;
import com.oracle.graal.python.builtins.objects.cext.PythonNativeVoidPtr;
import com.oracle.graal.python.builtins.objects.cext.capi.CApiContext.LLVMType;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodesFactory.AllToSulongNodeGen;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodesFactory.AsPythonObjectNodeGen;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodesFactory.BinaryFirstToSulongNodeGen;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodesFactory.CextUpcallNodeGen;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodesFactory.CharPtrToJavaNodeGen;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodesFactory.DirectUpcallNodeGen;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodesFactory.FastCallArgsToSulongNodeGen;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodesFactory.FastCallWithKeywordsArgsToSulongNodeGen;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodesFactory.FromCharPointerNodeGen;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodesFactory.GetTypeMemberNodeGen;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodesFactory.IsPointerNodeGen;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodesFactory.ObjectUpcallNodeGen;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodesFactory.SSizeArgProcToSulongNodeGen;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodesFactory.SSizeObjArgProcToSulongNodeGen;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodesFactory.TernaryFirstSecondToSulongNodeGen;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodesFactory.TernaryFirstThirdToSulongNodeGen;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodesFactory.ToJavaNodeGen;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodesFactory.TransformExceptionToNativeNodeGen;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodesFactory.VoidPtrToJavaNodeGen;
import com.oracle.graal.python.builtins.objects.cext.capi.DynamicObjectNativeWrapper.PrimitiveNativeWrapper;
import com.oracle.graal.python.builtins.objects.cext.capi.DynamicObjectNativeWrapper.PythonObjectNativeWrapper;
import com.oracle.graal.python.builtins.objects.cext.capi.DynamicObjectNativeWrapper.WriteNativeMemberNode;
import com.oracle.graal.python.builtins.objects.cext.capi.ExternalFunctionNodes.DefaultCheckFunctionResultNode;
import com.oracle.graal.python.builtins.objects.cext.capi.ExternalFunctionNodes.MethFastcallRoot;
import com.oracle.graal.python.builtins.objects.cext.capi.ExternalFunctionNodes.MethFastcallWithKeywordsRoot;
import com.oracle.graal.python.builtins.objects.cext.capi.ExternalFunctionNodes.MethKeywordsRoot;
import com.oracle.graal.python.builtins.objects.cext.capi.ExternalFunctionNodes.MethMethodRoot;
import com.oracle.graal.python.builtins.objects.cext.capi.ExternalFunctionNodes.MethNoargsRoot;
import com.oracle.graal.python.builtins.objects.cext.capi.ExternalFunctionNodes.MethORoot;
import com.oracle.graal.python.builtins.objects.cext.capi.ExternalFunctionNodes.MethVarargsRoot;
import com.oracle.graal.python.builtins.objects.cext.capi.ExternalFunctionNodes.PExternalFunctionWrapper;
import com.oracle.graal.python.builtins.objects.cext.capi.NativeReferenceCache.ResolveNativeReferenceNode;
import com.oracle.graal.python.builtins.objects.cext.capi.PGetDynamicTypeNode.GetSulongTypeNode;
import com.oracle.graal.python.builtins.objects.cext.capi.PyTruffleObjectFree.FreeNode;
import com.oracle.graal.python.builtins.objects.cext.common.CArrayWrappers.CArrayWrapper;
import com.oracle.graal.python.builtins.objects.cext.common.CArrayWrappers.CByteArrayWrapper;
import com.oracle.graal.python.builtins.objects.cext.common.CArrayWrappers.CStringWrapper;
import com.oracle.graal.python.builtins.objects.cext.common.CExtAsPythonObjectNode;
import com.oracle.graal.python.builtins.objects.cext.common.CExtCommonNodes.ConvertPIntToPrimitiveNode;
import com.oracle.graal.python.builtins.objects.cext.common.CExtCommonNodes.EnsureTruffleStringNode;
import com.oracle.graal.python.builtins.objects.cext.common.CExtCommonNodes.ImportCExtSymbolNode;
import com.oracle.graal.python.builtins.objects.cext.common.CExtContext;
import com.oracle.graal.python.builtins.objects.cext.common.CExtContext.ModuleSpec;
import com.oracle.graal.python.builtins.objects.cext.common.CExtToJavaNode;
import com.oracle.graal.python.builtins.objects.cext.common.CExtToNativeNode;
import com.oracle.graal.python.builtins.objects.cext.common.GetNextVaArgNode;
import com.oracle.graal.python.builtins.objects.cext.common.GetNextVaArgNodeGen;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes.ToArrayNode;
import com.oracle.graal.python.builtins.objects.complex.PComplex;
import com.oracle.graal.python.builtins.objects.floats.PFloat;
import com.oracle.graal.python.builtins.objects.function.PBuiltinFunction;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.builtins.objects.getsetdescriptor.DescriptorDeleteMarker;
import com.oracle.graal.python.builtins.objects.ints.PInt;
import com.oracle.graal.python.builtins.objects.memoryview.PMemoryView;
import com.oracle.graal.python.builtins.objects.method.PBuiltinMethod;
import com.oracle.graal.python.builtins.objects.module.ModuleGetNameNode;
import com.oracle.graal.python.builtins.objects.module.PythonModule;
import com.oracle.graal.python.builtins.objects.object.PythonObject;
import com.oracle.graal.python.builtins.objects.str.NativeCharSequence;
import com.oracle.graal.python.builtins.objects.str.PString;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.builtins.objects.type.PythonAbstractClass;
import com.oracle.graal.python.builtins.objects.type.PythonManagedClass;
import com.oracle.graal.python.builtins.objects.type.TypeNodes;
import com.oracle.graal.python.builtins.objects.type.TypeNodes.GetMroStorageNode;
import com.oracle.graal.python.builtins.objects.type.TypeNodes.GetNameNode;
import com.oracle.graal.python.builtins.objects.type.TypeNodes.ProfileClassNode;
import com.oracle.graal.python.lib.PyFloatAsDoubleNode;
import com.oracle.graal.python.lib.PyObjectLookupAttr;
import com.oracle.graal.python.lib.PyObjectSizeNode;
import com.oracle.graal.python.nodes.BuiltinNames;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.PRootNode;
import com.oracle.graal.python.nodes.SpecialAttributeNames;
import com.oracle.graal.python.nodes.SpecialMethodNames;
import com.oracle.graal.python.nodes.attributes.ReadAttributeFromObjectNode;
import com.oracle.graal.python.nodes.attributes.WriteAttributeToDynamicObjectNode;
import com.oracle.graal.python.nodes.attributes.WriteAttributeToObjectNode;
import com.oracle.graal.python.nodes.call.CallNode;
import com.oracle.graal.python.nodes.call.special.CallBinaryMethodNode;
import com.oracle.graal.python.nodes.call.special.CallTernaryMethodNode;
import com.oracle.graal.python.nodes.call.special.CallUnaryMethodNode;
import com.oracle.graal.python.nodes.call.special.LookupAndCallUnaryNode.LookupAndCallUnaryDynamicNode;
import com.oracle.graal.python.nodes.classes.IsSubtypeNode;
import com.oracle.graal.python.nodes.expression.ExpressionNode;
import com.oracle.graal.python.nodes.frame.GetCurrentFrameRef;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.nodes.object.IsForeignObjectNode;
import com.oracle.graal.python.nodes.truffle.PythonTypes;
import com.oracle.graal.python.nodes.util.CannotCastException;
import com.oracle.graal.python.nodes.util.CastToJavaLongLossyNode;
import com.oracle.graal.python.nodes.util.CastToJavaStringNode;
import com.oracle.graal.python.nodes.util.CastToJavaStringNodeGen;
import com.oracle.graal.python.nodes.util.CastToTruffleStringNode;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.PythonContext.GetThreadStateNode;
import com.oracle.graal.python.runtime.PythonOptions;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.exception.PythonErrorType;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.graal.python.runtime.sequence.storage.MroSequenceStorage;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleLogger;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.TypeSystemReference;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.InteropException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.InvalidArrayIndexException;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.EncapsulatingNodeReference;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.InvalidAssumptionException;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.api.profiles.ConditionProfile;
import com.oracle.truffle.api.strings.TruffleString;
import com.oracle.truffle.api.strings.TruffleString.Encoding;

public abstract class CExtNodes {

    private static final String J_UNICODE = "unicode";
    private static final String J_SUBTYPE_NEW = "_subtype_new";

    @GenerateUncached
    abstract static class ImportCAPISymbolNode extends PNodeWithContext {

        public abstract Object execute(NativeCAPISymbol symbol);

        @Specialization
        Object doGeneric(NativeCAPISymbol name,
                        @Cached ImportCExtSymbolNode importCExtSymbolNode) {
            return importCExtSymbolNode.execute(PythonContext.get(this).getCApiContext(), name);
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
         * tget the <code>typename_subtype_new</code> function
         */
        protected NativeCAPISymbol getFunction() {
            throw CompilerDirectives.shouldNotReachHere();
        }

        protected abstract Object execute(Object object, Object arg);

        protected static NativeCAPISymbol getFunction(String typenamePrefix) {
            CompilerAsserts.neverPartOfCompilation();
            String subtypeNewFunctionName = typenamePrefix + J_SUBTYPE_NEW;
            NativeCAPISymbol result = NativeCAPISymbol.getByName(subtypeNewFunctionName);
            assert result != null : "SubtypeNew function not found: " + subtypeNewFunctionName;
            return result;
        }

        @Specialization(guards = "isNativeClass(object)")
        protected Object callNativeConstructor(Object object, Object arg,
                        @Cached ToSulongNode toSulongNode,
                        @Cached ToJavaNode toJavaNode,
                        @CachedLibrary(limit = "1") InteropLibrary interopLibrary,
                        @Cached ImportCAPISymbolNode importCAPISymbolNode) {
            try {
                Object result = interopLibrary.execute(importCAPISymbolNode.execute(getFunction()), toSulongNode.execute(object), arg);
                return toJavaNode.execute(result);
            } catch (UnsupportedMessageException | UnsupportedTypeException | ArityException e) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw new IllegalStateException("C subtype_new function failed", e);
            }
        }
    }

    public abstract static class FloatSubtypeNew extends SubtypeNew {

        private static final NativeCAPISymbol NEW_FUNCTION = getFunction(BuiltinNames.J_FLOAT);

        @Override
        protected final NativeCAPISymbol getFunction() {
            return NEW_FUNCTION;
        }

        public final Object call(Object object, double arg) {
            return execute(object, arg);
        }

        public static FloatSubtypeNew create() {
            return CExtNodesFactory.FloatSubtypeNewNodeGen.create();
        }
    }

    public abstract static class TupleSubtypeNew extends SubtypeNew {

        private static final NativeCAPISymbol NEW_FUNCTION = getFunction(BuiltinNames.J_TUPLE);

        @Child private ToSulongNode toSulongNode;

        @Override
        protected final NativeCAPISymbol getFunction() {
            return NEW_FUNCTION;
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

        private static final NativeCAPISymbol NEW_FUNCTION = getFunction(J_UNICODE);

        @Child private ToSulongNode toSulongNode;

        @Override
        protected final NativeCAPISymbol getFunction() {
            return NEW_FUNCTION;
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
                        @Exclusive @Cached ImportCAPISymbolNode importCAPISymbolNode) {
            if (isFloatSubtype(frame, object, getClass, isSubtype)) {
                try {
                    return (Double) interopLibrary.execute(importCAPISymbolNode.execute(FUN_PY_FLOAT_AS_DOUBLE), toSulongNode.execute(object));
                } catch (UnsupportedMessageException | UnsupportedTypeException | ArityException e) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    throw new IllegalStateException("C object conversion function failed", e);
                }
            }
            return null;
        }

        public boolean isFloatSubtype(VirtualFrame frame, PythonNativeObject object, GetClassNode getClass, IsSubtypeNode isSubtype) {
            return isSubtype.execute(frame, getClass.execute(object), PythonContext.get(this).lookupType(PythonBuiltinClassType.PFloat));
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
        static Object doString(@SuppressWarnings("unused") CExtContext cextContext, TruffleString str,
                        @Cached PythonObjectFactory factory,
                        @Cached ConditionProfile noWrapperProfile) {
            return PythonObjectNativeWrapper.wrap(factory.createString(str), noWrapperProfile);
        }

        @Specialization
        Object doBoolean(@SuppressWarnings("unused") CExtContext cextContext, boolean b,
                        @Cached ConditionProfile profile) {
            Python3Core core = PythonContext.get(this);
            PInt boxed = b ? core.getTrue() : core.getFalse();
            DynamicObjectNativeWrapper nativeWrapper = boxed.getNativeWrapper();
            if (profile.profile(nativeWrapper == null)) {
                nativeWrapper = PrimitiveNativeWrapper.createBool(b);
                boxed.setNativeWrapper(nativeWrapper);
            }
            return nativeWrapper;
        }

        @Specialization(guards = "isSmallInteger(i)")
        PrimitiveNativeWrapper doIntegerSmall(@SuppressWarnings("unused") CExtContext cextContext, int i) {
            PythonContext context = getContext();
            if (context.getCApiContext() != null) {
                return context.getCApiContext().getCachedPrimitiveNativeWrapper(i);
            }
            return PrimitiveNativeWrapper.createInt(i);
        }

        @Specialization(guards = "!isSmallInteger(i)")
        static PrimitiveNativeWrapper doInteger(@SuppressWarnings("unused") CExtContext cextContext, int i) {
            return PrimitiveNativeWrapper.createInt(i);
        }

        static PrimitiveNativeWrapper doLongSmall(@SuppressWarnings("unused") CExtContext cextContext, long l, PythonContext context) {
            if (context.getCApiContext() != null) {
                return context.getCApiContext().getCachedPrimitiveNativeWrapper(l);
            }
            return PrimitiveNativeWrapper.createLong(l);
        }

        @Specialization(guards = "isSmallLong(l)")
        PrimitiveNativeWrapper doLongSmall(CExtContext cextContext, long l) {
            return doLongSmall(cextContext, l, getContext());
        }

        @Specialization(guards = "!isSmallLong(l)")
        static PrimitiveNativeWrapper doLong(@SuppressWarnings("unused") CExtContext cextContext, long l) {
            return PrimitiveNativeWrapper.createLong(l);
        }

        @Specialization(guards = "!isNaN(d)")
        static Object doDouble(@SuppressWarnings("unused") CExtContext cextContext, double d) {
            return PrimitiveNativeWrapper.createDouble(d);
        }

        @Specialization(guards = "isNaN(d)")
        Object doDouble(@SuppressWarnings("unused") CExtContext cextContext, @SuppressWarnings("unused") double d,
                        @Cached("createCountingProfile()") ConditionProfile noWrapperProfile) {
            PFloat boxed = getContext().getNaN();
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
        Object doDeleteMarker(@SuppressWarnings("unused") CExtContext cextContext, DescriptorDeleteMarker marker) {
            assert marker == DescriptorDeleteMarker.INSTANCE;
            return getNativeNullPtr(this);
        }

        static Object doSingleton(@SuppressWarnings("unused") CExtContext cextContext, @SuppressWarnings("unused") PythonAbstractObject object, PythonContext context) {
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

        @Specialization(guards = {"object == cachedObject", "isSpecialSingleton(cachedObject)"})
        Object doSingletonCached(CExtContext cextContext, @SuppressWarnings("unused") PythonAbstractObject object,
                        @Cached("object") PythonAbstractObject cachedObject) {
            return doSingleton(cextContext, cachedObject, getContext());
        }

        @Specialization(guards = "isSpecialSingleton(object)", replaces = "doSingletonCached")
        Object doSingleton(CExtContext cextContext, PythonAbstractObject object) {
            return doSingleton(cextContext, object, getContext());
        }

        @Specialization(guards = {"isSingleContext()", "object == cachedObject"}, limit = "3")
        static Object doPythonClass(@SuppressWarnings("unused") CExtContext cextContext, @SuppressWarnings("unused") PythonManagedClass object,
                        @SuppressWarnings("unused") @Cached(value = "object", weak = true) PythonManagedClass cachedObject,
                        @Cached(value = "wrapNativeClass(object)", weak = true) PythonClassNativeWrapper wrapper) {
            return wrapper;
        }

        @Specialization(replaces = "doPythonClass")
        static Object doPythonClassUncached(@SuppressWarnings("unused") CExtContext cextContext, PythonManagedClass object,
                        @Cached TypeNodes.GetNameNode getNameNode) {
            return PythonClassNativeWrapper.wrap(object, getNameNode.execute(object));
        }

        @Specialization(guards = {"isSingleContext()", "object == cachedObject"}, limit = "3")
        static Object doPythonType(@SuppressWarnings("unused") CExtContext cextContext, @SuppressWarnings("unused") PythonBuiltinClassType object,
                        @SuppressWarnings("unused") @Cached("object") PythonBuiltinClassType cachedObject,
                        @Cached("wrapNativeClassFast(object, getContext())") PythonClassNativeWrapper wrapper) {
            return wrapper;
        }

        @Specialization(replaces = "doPythonType")
        static Object doPythonTypeUncached(@SuppressWarnings("unused") CExtContext cextContext, PythonBuiltinClassType object,
                        @Cached TypeNodes.GetNameNode getNameNode) {
            return PythonClassNativeWrapper.wrap(PythonContext.get(getNameNode).lookupType(object), getNameNode.execute(object));
        }

        @Specialization(guards = {"cachedClass == object.getClass()", "!isClass(object, lib)", "!isNativeObject(object)", "!isSpecialSingleton(object)"})
        static Object runAbstractObjectCached(@SuppressWarnings("unused") CExtContext cextContext, PythonAbstractObject object,
                        @Cached ConditionProfile noWrapperProfile,
                        @Cached("object.getClass()") Class<? extends PythonAbstractObject> cachedClass,
                        @SuppressWarnings("unused") @CachedLibrary(limit = "3") InteropLibrary lib) {
            assert object != PNone.NO_VALUE;
            return PythonObjectNativeWrapper.wrap(CompilerDirectives.castExact(object, cachedClass), noWrapperProfile);
        }

        @Specialization(guards = {"!isClass(object, lib)", "!isNativeObject(object)", "!isSpecialSingleton(object)"}, replaces = "runAbstractObjectCached")
        static Object runAbstractObject(@SuppressWarnings("unused") CExtContext cextContext, PythonAbstractObject object,
                        @Cached ConditionProfile noWrapperProfile,
                        @SuppressWarnings("unused") @CachedLibrary(limit = "3") InteropLibrary lib) {
            assert object != PNone.NO_VALUE;
            return PythonObjectNativeWrapper.wrap(object, noWrapperProfile);
        }

        @Specialization(guards = {"isForeignObjectNode.execute(object)", "!isNativeWrapper(object)", "!isNativeNull(object)"})
        static Object doForeignObject(@SuppressWarnings("unused") CExtContext cextContext, Object object,
                        @SuppressWarnings("unused") @Cached IsForeignObjectNode isForeignObjectNode) {
            return TruffleObjectNativeWrapper.wrap(object);
        }

        @Specialization(guards = "isFallback(object, isForeignObjectNode)")
        static Object run(@SuppressWarnings("unused") CExtContext cextContext, Object object,
                        @SuppressWarnings("unused") @Cached IsForeignObjectNode isForeignObjectNode) {
            assert object != null : "Java 'null' cannot be a Sulong value";
            assert CApiGuards.isNativeWrapper(object) : "unknown object cannot be a Sulong value";
            return object;
        }

        protected static PythonClassNativeWrapper wrapNativeClass(PythonManagedClass object) {
            return PythonClassNativeWrapper.wrap(object, GetNameNode.doSlowPath(object));
        }

        protected static PythonClassNativeWrapper wrapNativeClassFast(PythonBuiltinClassType object, PythonContext context) {
            return PythonClassNativeWrapper.wrap(context.lookupType(object), GetNameNode.doSlowPath(object));
        }

        static boolean isFallback(Object object, IsForeignObjectNode isForeignObjectNode) {
            return !(object instanceof TruffleString || object instanceof Boolean || object instanceof Integer || object instanceof Long || object instanceof Double ||
                            object instanceof PythonBuiltinClassType || object instanceof PythonNativeNull || object == DescriptorDeleteMarker.INSTANCE ||
                            object instanceof PythonAbstractObject) && !(isForeignObjectNode.execute(object) && !CApiGuards.isNativeWrapper(object));
        }

        protected static boolean isNaN(double d) {
            return Double.isNaN(d);
        }

        static Object getNativeNullPtr(Node node) {
            return PythonContext.get(node).getNativeNull().getPtr();
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
        static Object doString(CExtContext cextContext, TruffleString str,
                        @Cached PythonObjectFactory factory,
                        @Cached ConditionProfile noWrapperProfile) {
            return ToSulongNode.doString(cextContext, str, factory, noWrapperProfile);
        }

        static Object doBoolean(@SuppressWarnings("unused") CExtContext cextContext, boolean b,
                        @Cached ConditionProfile profile, PythonContext context) {
            PInt boxed = b ? context.getTrue() : context.getFalse();
            DynamicObjectNativeWrapper nativeWrapper = boxed.getNativeWrapper();
            if (profile.profile(nativeWrapper == null)) {
                nativeWrapper = PrimitiveNativeWrapper.createBool(b);
                boxed.setNativeWrapper(nativeWrapper);
            } else {
                nativeWrapper.increaseRefCount();
            }
            return nativeWrapper;
        }

        @Specialization
        Object doBoolean(CExtContext cextContext, boolean b, @Cached ConditionProfile profile) {
            return doBoolean(cextContext, b, profile, getContext());
        }

        static PrimitiveNativeWrapper doIntegerSmall(@SuppressWarnings("unused") CExtContext cextContext, int i, PythonContext context) {
            if (context.getCApiContext() != null) {
                PrimitiveNativeWrapper cachedPrimitiveNativeWrapper = context.getCApiContext().getCachedPrimitiveNativeWrapper(i);
                cachedPrimitiveNativeWrapper.increaseRefCount();
                return cachedPrimitiveNativeWrapper;
            }
            return PrimitiveNativeWrapper.createInt(i);
        }

        @Specialization(guards = "isSmallInteger(i)")
        PrimitiveNativeWrapper doIntegerSmall(CExtContext cextContext, int i) {
            return doIntegerSmall(cextContext, i, getContext());
        }

        @Specialization(guards = "!isSmallInteger(i)")
        static PrimitiveNativeWrapper doInteger(@SuppressWarnings("unused") CExtContext cextContext, int i) {
            return PrimitiveNativeWrapper.createInt(i);
        }

        static PrimitiveNativeWrapper doLongSmall(@SuppressWarnings("unused") CExtContext cextContext, long l, PythonContext context) {
            if (context.getCApiContext() != null) {
                PrimitiveNativeWrapper cachedPrimitiveNativeWrapper = context.getCApiContext().getCachedPrimitiveNativeWrapper(l);
                cachedPrimitiveNativeWrapper.increaseRefCount();
                return cachedPrimitiveNativeWrapper;
            }
            return PrimitiveNativeWrapper.createLong(l);
        }

        @Specialization(guards = "isSmallLong(l)")
        PrimitiveNativeWrapper doLongSmall(CExtContext cextContext, long l) {
            return doLongSmall(cextContext, l, getContext());
        }

        @Specialization(guards = "!isSmallLong(l)")
        static PrimitiveNativeWrapper doLong(@SuppressWarnings("unused") CExtContext cextContext, long l) {
            return PrimitiveNativeWrapper.createLong(l);
        }

        static Object doDouble(@SuppressWarnings("unused") CExtContext cextContext, @SuppressWarnings("unused") double d,
                        @Cached("createCountingProfile()") ConditionProfile noWrapperProfile, PythonContext context) {
            PFloat boxed = context.getNaN();
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

        @Specialization(guards = "!isNaN(d)")
        static Object doDouble(CExtContext cextContext, double d) {
            return ToSulongNode.doDouble(cextContext, d);
        }

        @Specialization(guards = "isNaN(d)")
        Object doDouble(CExtContext cextContext, double d,
                        @Cached("createCountingProfile()") ConditionProfile noWrapperProfile) {
            return doDouble(cextContext, d, noWrapperProfile, getContext());
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

        @SuppressWarnings("unused")
        @Specialization
        Object doDeleteMarker(CExtContext cextContext, DescriptorDeleteMarker marker) {
            return ToSulongNode.getNativeNullPtr(this);
        }

        static Object doSingleton(@SuppressWarnings("unused") CExtContext cextContext, @SuppressWarnings("unused") PythonAbstractObject object, PythonContext context) {
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

        @Specialization(guards = {"object == cachedObject", "isSpecialSingleton(cachedObject)"})
        Object doSingletonCached(CExtContext cextContext, @SuppressWarnings("unused") PythonAbstractObject object,
                        @Cached("object") PythonAbstractObject cachedObject) {
            return doSingleton(cextContext, cachedObject);
        }

        @Specialization(guards = "isSpecialSingleton(object)", replaces = "doSingletonCached")
        Object doSingleton(CExtContext cextContext, PythonAbstractObject object) {
            return doSingleton(cextContext, object, getContext());
        }

        @Specialization(guards = {"isSingleContext()", "object == cachedObject"}, limit = "3")
        static Object doPythonClass(@SuppressWarnings("unused") CExtContext cextContext, @SuppressWarnings("unused") PythonManagedClass object,
                        @SuppressWarnings("unused") @Cached(value = "object", weak = true) PythonManagedClass cachedObject,
                        @Cached(value = "wrapNativeClass(object)", weak = true) PythonClassNativeWrapper wrapper) {
            wrapper.increaseRefCount();
            return wrapper;
        }

        @Specialization(replaces = "doPythonClass")
        static Object doPythonClassUncached(@SuppressWarnings("unused") CExtContext cextContext, PythonManagedClass object,
                        @Cached TypeNodes.GetNameNode getNameNode) {
            return PythonClassNativeWrapper.wrapNewRef(object, getNameNode.execute(object));
        }

        @Specialization(guards = {"isSingleContext()", "object == cachedObject"}, limit = "3")
        static Object doPythonType(@SuppressWarnings("unused") CExtContext cextContext, @SuppressWarnings("unused") PythonBuiltinClassType object,
                        @SuppressWarnings("unused") @Cached("object") PythonBuiltinClassType cachedObject,
                        @Cached("wrapNativeClassFast(getContext(), object)") PythonClassNativeWrapper wrapper) {
            wrapper.increaseRefCount();
            return wrapper;
        }

        @Specialization(replaces = "doPythonType")
        static Object doPythonTypeUncached(@SuppressWarnings("unused") CExtContext cextContext, PythonBuiltinClassType object,
                        @Cached TypeNodes.GetNameNode getNameNode) {
            return PythonClassNativeWrapper.wrapNewRef(PythonContext.get(getNameNode).lookupType(object), getNameNode.execute(object));
        }

        @Specialization(guards = {"cachedClass == object.getClass()", "!isClass(object, lib)", "!isNativeObject(object)", "!isSpecialSingleton(object)"})
        static Object runAbstractObjectCached(@SuppressWarnings("unused") CExtContext cextContext, PythonAbstractObject object,
                        @Cached ConditionProfile noWrapperProfile,
                        @Cached("object.getClass()") Class<? extends PythonAbstractObject> cachedClass,
                        @SuppressWarnings("unused") @CachedLibrary(limit = "3") InteropLibrary lib) {
            assert object != PNone.NO_VALUE;
            return PythonObjectNativeWrapper.wrapNewRef(CompilerDirectives.castExact(object, cachedClass), noWrapperProfile);
        }

        @Specialization(guards = {"!isClass(object, lib)", "!isNativeObject(object)", "!isSpecialSingleton(object)"}, replaces = "runAbstractObjectCached")
        static Object runAbstractObject(@SuppressWarnings("unused") CExtContext cextContext, PythonAbstractObject object,
                        @Cached ConditionProfile noWrapperProfile,
                        @SuppressWarnings("unused") @CachedLibrary(limit = "3") InteropLibrary lib) {
            assert object != PNone.NO_VALUE;
            return PythonObjectNativeWrapper.wrapNewRef(object, noWrapperProfile);
        }

        @Specialization(guards = {"isForeignObjectNode.execute(object)", "!isNativeWrapper(object)", "!isNativeNull(object)"})
        static Object doForeignObject(CExtContext cextContext, Object object,
                        @Cached IsForeignObjectNode isForeignObjectNode) {
            // this will always be a new wrapper; it's implicitly always a new reference in any case
            return ToSulongNode.doForeignObject(cextContext, object, isForeignObjectNode);
        }

        @Specialization(guards = "isFallback(object, isForeignObjectNode)")
        static Object run(CExtContext cextContext, Object object,
                        @Cached IsForeignObjectNode isForeignObjectNode) {
            return ToSulongNode.run(cextContext, object, isForeignObjectNode);
        }

        protected static PythonClassNativeWrapper wrapNativeClass(PythonManagedClass object) {
            return PythonClassNativeWrapper.wrap(object, GetNameNode.doSlowPath(object));
        }

        protected static PythonClassNativeWrapper wrapNativeClassFast(PythonContext context, PythonBuiltinClassType object) {
            return PythonClassNativeWrapper.wrap(context.lookupType(object), GetNameNode.doSlowPath(object));
        }

        static boolean isFallback(Object object, IsForeignObjectNode isForeignObjectNode) {
            return ToSulongNode.isFallback(object, isForeignObjectNode);
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
        static Object doString(CExtContext cextContext, TruffleString str,
                        @Cached PythonObjectFactory factory,
                        @Cached ConditionProfile noWrapperProfile) {
            return ToSulongNode.doString(cextContext, str, factory, noWrapperProfile);
        }

        @Specialization
        Object doBoolean(CExtContext cextContext, boolean b,
                        @Cached ConditionProfile profile) {
            return ToNewRefNode.doBoolean(cextContext, b, profile, getContext());
        }

        @Specialization(guards = "isSmallInteger(i)")
        PrimitiveNativeWrapper doIntegerSmall(CExtContext cextContext, int i) {
            return ToNewRefNode.doIntegerSmall(cextContext, i, getContext());
        }

        @Specialization(guards = "!isSmallInteger(i)")
        static PrimitiveNativeWrapper doInteger(CExtContext cextContext, int i) {
            return ToNewRefNode.doInteger(cextContext, i);
        }

        @Specialization(guards = "isSmallLong(l)")
        PrimitiveNativeWrapper doLongSmall(CExtContext cextContext, long l) {
            return ToNewRefNode.doLongSmall(cextContext, l, getContext());
        }

        @Specialization(guards = "!isSmallLong(l)")
        static PrimitiveNativeWrapper doLong(@SuppressWarnings("unused") CExtContext cextContext, long l) {
            return ToNewRefNode.doLong(cextContext, l);
        }

        @Specialization(guards = "!isNaN(d)")
        static Object doDouble(CExtContext cextContext, double d) {
            return ToSulongNode.doDouble(cextContext, d);
        }

        @Specialization(guards = "isNaN(d)")
        Object doDouble(CExtContext cextContext, double d,
                        @Cached("createCountingProfile()") ConditionProfile noWrapperProfile) {
            return ToNewRefNode.doDouble(cextContext, d, noWrapperProfile, getContext());
        }

        @Specialization
        static Object doNativeObject(CExtContext cextContext, PythonAbstractNativeObject nativeObject) {
            return ToSulongNode.doNativeObject(cextContext, nativeObject);
        }

        @Specialization
        static Object doNativeNull(CExtContext cextContext, PythonNativeNull object) {
            return ToSulongNode.doNativeNull(cextContext, object);
        }

        @SuppressWarnings("unused")
        @Specialization
        Object doDeleteMarker(CExtContext cextContext, DescriptorDeleteMarker marker) {
            return ToSulongNode.getNativeNullPtr(this);
        }

        @Specialization(guards = {"object == cachedObject", "isSpecialSingleton(cachedObject)"})
        Object doSingletonCached(CExtContext cextContext, @SuppressWarnings("unused") PythonAbstractObject object,
                        @Cached("object") PythonAbstractObject cachedObject) {
            return doSingleton(cextContext, cachedObject);
        }

        @Specialization(guards = "isSpecialSingleton(object)", replaces = "doSingletonCached")
        Object doSingleton(CExtContext cextContext, PythonAbstractObject object) {
            return ToNewRefNode.doSingleton(cextContext, object, getContext());
        }

        @Specialization(guards = {"isSingleContext()", "object == cachedObject"}, limit = "3")
        static Object doPythonClass(@SuppressWarnings("unused") CExtContext cextContext, @SuppressWarnings("unused") PythonManagedClass object,
                        @SuppressWarnings("unused") @Cached(value = "object", weak = true) PythonManagedClass cachedObject,
                        @Cached(value = "wrapNativeClass(object)", weak = true) PythonClassNativeWrapper wrapper) {
            wrapper.increaseRefCount();
            return wrapper;
        }

        @Specialization(replaces = "doPythonClass")
        static Object doPythonClassUncached(@SuppressWarnings("unused") CExtContext cextContext, PythonManagedClass object,
                        @Cached TypeNodes.GetNameNode getNameNode) {
            return PythonClassNativeWrapper.wrapNewRef(object, getNameNode.execute(object));
        }

        @Specialization(guards = {"isSingleContext()", "object == cachedObject"}, limit = "3")
        static Object doPythonType(@SuppressWarnings("unused") CExtContext cextContext, @SuppressWarnings("unused") PythonBuiltinClassType object,
                        @SuppressWarnings("unused") @Cached("object") PythonBuiltinClassType cachedObject,
                        @Cached("wrapNativeClassFast(getContext(), object)") PythonClassNativeWrapper wrapper) {
            wrapper.increaseRefCount();
            return wrapper;
        }

        @Specialization(replaces = "doPythonType")
        static Object doPythonTypeUncached(@SuppressWarnings("unused") CExtContext cextContext, PythonBuiltinClassType object,
                        @Cached TypeNodes.GetNameNode getNameNode) {
            return PythonClassNativeWrapper.wrapNewRef(PythonContext.get(getNameNode).lookupType(object), getNameNode.execute(object));
        }

        @Specialization(guards = {"cachedClass == object.getClass()", "!isClass(object, lib)", "!isNativeObject(object)", "!isSpecialSingleton(object)"})
        static Object runAbstractObjectCached(@SuppressWarnings("unused") CExtContext cextContext, PythonAbstractObject object,
                        @Cached ConditionProfile noWrapperProfile,
                        @Cached("object.getClass()") Class<? extends PythonAbstractObject> cachedClass,
                        @SuppressWarnings("unused") @CachedLibrary(limit = "3") InteropLibrary lib) {
            assert object != PNone.NO_VALUE;
            return PythonObjectNativeWrapper.wrapNewRef(CompilerDirectives.castExact(object, cachedClass), noWrapperProfile);
        }

        @Specialization(guards = {"!isClass(object, lib)", "!isNativeObject(object)", "!isSpecialSingleton(object)"}, replaces = "runAbstractObjectCached")
        static Object runAbstractObject(@SuppressWarnings("unused") CExtContext cextContext, PythonAbstractObject object,
                        @Cached ConditionProfile noWrapperProfile,
                        @SuppressWarnings("unused") @CachedLibrary(limit = "3") InteropLibrary lib) {
            assert object != PNone.NO_VALUE;
            return PythonObjectNativeWrapper.wrapNewRef(object, noWrapperProfile);
        }

        @Specialization(guards = {"isForeignObjectNode.execute(object)", "!isNativeWrapper(object)", "!isNativeNull(object)"})
        static Object doForeignObject(CExtContext cextContext, Object object,
                        @Cached IsForeignObjectNode isForeignObjectNode) {
            // this will always be a new wrapper; it's implicitly always a new reference in any case
            return ToSulongNode.doForeignObject(cextContext, object, isForeignObjectNode);
        }

        @Specialization(guards = "isFallback(object, isForeignObjectNode)")
        static Object run(CExtContext cextContext, Object object,
                        @Cached IsForeignObjectNode isForeignObjectNode) {
            return ToSulongNode.run(cextContext, object, isForeignObjectNode);
        }

        protected static PythonClassNativeWrapper wrapNativeClass(PythonManagedClass object) {
            return PythonClassNativeWrapper.wrap(object, GetNameNode.doSlowPath(object));
        }

        protected static PythonClassNativeWrapper wrapNativeClassFast(PythonContext context, PythonBuiltinClassType object) {
            return PythonClassNativeWrapper.wrap(context.lookupType(object), GetNameNode.doSlowPath(object));
        }

        static boolean isFallback(Object object, IsForeignObjectNode isForeignObjectNode) {
            return ToSulongNode.isFallback(object, isForeignObjectNode);
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
        static int doIntNativeWrapper(@SuppressWarnings("unused") CExtContext cextContext, PrimitiveNativeWrapper object,
                        @Shared("isPointerNode") @Cached @SuppressWarnings("unused") IsPointerNode isPointerNode) {
            return object.getInt();
        }

        @Specialization(guards = {"object.isInt() || object.isLong()", "mayUsePrimitive(isPointerNode, object)"}, //
                        limit = "1", //
                        replaces = "doIntNativeWrapper")
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
        static TruffleString doString(@SuppressWarnings("unused") CExtContext cextContext, TruffleString object) {
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

        @Specialization(guards = "isFallback(obj, isForeignObjectNode)", limit = "1")
        static Object run(@SuppressWarnings("unused") CExtContext cextContext, Object obj,
                        @SuppressWarnings("unused") @Cached IsForeignObjectNode isForeignObjectNode,
                        @Cached PRaiseNode raiseNode) {
            throw raiseNode.raise(PythonErrorType.SystemError, ErrorMessages.INVALID_OBJ_FROM_NATIVE, obj);
        }

        protected static boolean isFallback(Object obj, IsForeignObjectNode isForeignObjectNode) {
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
            if (isForeignObjectNode.execute(obj)) {
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

    }

    /**
     * Unwraps objects contained in {@link DynamicObjectNativeWrapper.PythonObjectNativeWrapper}
     * instances or wraps objects allocated in native code for consumption in Java.
     */
    @GenerateUncached
    @ImportStatic({PGuards.class, CApiGuards.class})
    public abstract static class AsPythonObjectNode extends AsPythonObjectBaseNode {

        @Specialization(guards = {"isForeignObjectNode.execute(object)", "!isNativeWrapper(object)", "!isNativeNull(object)"}, limit = "2")
        static PythonAbstractObject doNativeObject(@SuppressWarnings("unused") CExtContext cextContext, Object object,
                        @SuppressWarnings("unused") @Cached IsForeignObjectNode isForeignObjectNode,
                        @Cached ConditionProfile newRefProfile,
                        @Cached ConditionProfile validRefProfile,
                        @Cached ConditionProfile resurrectProfile,
                        @CachedLibrary("object") InteropLibrary lib,
                        @Cached GetRefCntNode getRefCntNode,
                        @Cached AddRefCntNode addRefCntNode,
                        @Cached AttachLLVMTypeNode attachLLVMTypeNode) {
            if (lib.isNull(object)) {
                return PNone.NO_VALUE;
            }
            CApiContext cApiContext = PythonContext.get(isForeignObjectNode).getCApiContext();
            if (cApiContext != null) {
                return cApiContext.getPythonNativeObject(object, newRefProfile, validRefProfile, resurrectProfile, getRefCntNode, addRefCntNode, attachLLVMTypeNode);
            }
            return new PythonAbstractNativeObject(object);
        }

    }

    @GenerateUncached
    @ImportStatic({PGuards.class, CApiGuards.class})
    public abstract static class AsPythonObjectStealingNode extends AsPythonObjectBaseNode {

        @Specialization(guards = {"isForeignObjectNode.execute(object)", "!isNativeWrapper(object)", "!isNativeNull(object)"}, limit = "1")
        static PythonAbstractObject doNativeObject(@SuppressWarnings("unused") CExtContext cextContext, Object object,
                        @SuppressWarnings("unused") @Cached IsForeignObjectNode isForeignObjectNode,
                        @Cached ConditionProfile newRefProfile,
                        @Cached ConditionProfile validRefProfile,
                        @Cached ConditionProfile resurrectProfile,
                        @CachedLibrary("object") InteropLibrary lib,
                        @Cached GetRefCntNode getRefCntNode,
                        @Cached AddRefCntNode addRefCntNode,
                        @Cached AttachLLVMTypeNode attachLLVMTypeNode) {
            if (lib.isNull(object)) {
                return PNone.NO_VALUE;
            }
            CApiContext cApiContext = PythonContext.get(isForeignObjectNode).getCApiContext();
            if (cApiContext != null) {
                return cApiContext.getPythonNativeObject(object, newRefProfile, validRefProfile, resurrectProfile, getRefCntNode, addRefCntNode, true, attachLLVMTypeNode);
            }
            return new PythonAbstractNativeObject(object);
        }
    }

    @GenerateUncached
    @ImportStatic({PGuards.class, CApiGuards.class})
    public abstract static class WrapVoidPtrNode extends AsPythonObjectBaseNode {

        @Specialization(guards = {"isForeignObjectNode.execute(object)", "!isNativeWrapper(object)", "!isNativeNull(object)"}, limit = "1")
        static Object doNativeObject(@SuppressWarnings("unused") CExtContext cextContext, Object object,
                        @SuppressWarnings("unused") @Cached IsForeignObjectNode isForeignObjectNode) {
            // TODO(fa): should we use a different wrapper for non-'PyObject*' pointers; they cannot
            // be used in the user value space but might be passed-through

            // do not modify reference count at all; this is for non-'PyObject*' pointers
            return new PythonAbstractNativeObject(object);
        }

    }

    @GenerateUncached
    @ImportStatic({PGuards.class, CApiGuards.class})
    public abstract static class WrapCharPtrNode extends AsPythonObjectBaseNode {

        @Specialization(guards = {"isForeignObjectNode.execute(object)", "!isNativeWrapper(object)", "!isNativeNull(object)"}, limit = "1")
        static Object doNativeObject(@SuppressWarnings("unused") CExtContext cextContext, Object object,
                        @SuppressWarnings("unused") @Cached IsForeignObjectNode isForeignObjectNode,
                        @Cached FromCharPointerNode fromCharPointerNode) {
            return fromCharPointerNode.execute(object);
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
                        @SuppressWarnings("unused") @CachedLibrary("object") PythonNativeWrapperLibrary lib) {
            // Special case for True and False: use singletons
            Python3Core core = PythonContext.get(lib);
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
                        @SuppressWarnings("unused") @CachedLibrary("object") PythonNativeWrapperLibrary lib) {
            // Special case for double NaN: use singleton
            PFloat materializedFloat = PythonContext.get(lib).getNaN();
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
        static TruffleString doString(@SuppressWarnings("unused") CExtContext nativeContext, TruffleString object) {
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
            return !(obj instanceof PythonAbstractObject || obj instanceof PythonNativeWrapper || obj instanceof TruffleString || obj instanceof Boolean || obj instanceof Integer ||
                            obj instanceof Long || obj instanceof Byte || obj instanceof Double || isTruffleString(obj));
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
                        @Cached ConditionProfile isNullProfile) {
            // this is just a shortcut
            if (isNullProfile.profile(interopLibrary.isNull(value))) {
                return PNone.NO_VALUE;
            }
            return asPythonObjectNode.execute(resolveNativeReferenceNode.execute(resolveHandleNode.execute(value), false));
        }

        public static ToJavaNode create() {
            return ToJavaNodeGen.create();
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
                        @Cached ConditionProfile isNullProfile) {
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
                        @Shared("toJavaNode") @Cached WrapVoidPtrNode asPythonObjectNode,
                        @CachedLibrary("value") InteropLibrary interopLibrary,
                        @Cached ConditionProfile isNullProfile) {
            // this branch is not a shortcut; it actually returns a different object
            if (isNullProfile.profile(interopLibrary.isNull(value))) {
                return new PythonAbstractNativeObject(value);
            }
            return asPythonObjectNode.execute(resolveHandleNode.execute(value));
        }
    }

    /**
     * Does the same conversion as the native function {@code native_pointer_to_java}. The node
     * tries to avoid calling the native function for resolving native handles.
     */
    @GenerateUncached
    public abstract static class CharPtrToJavaNode extends ToJavaBaseNode {

        @Specialization(guards = "isForeignObject(value)", limit = "1")
        static Object doForeign(@SuppressWarnings("unused") CExtContext nativeContext, Object value,
                        @Shared("resolveHandleNode") @Cached ResolveHandleNode resolveHandleNode,
                        @Shared("toJavaNode") @Cached WrapCharPtrNode asPythonObjectNode,
                        @CachedLibrary("value") InteropLibrary interopLibrary,
                        @Cached ConditionProfile isNullProfile) {
            // this branch is not a shortcut; it actually returns a different object
            if (isNullProfile.profile(interopLibrary.isNull(value))) {
                return asPythonObjectNode.execute(value);
            }
            return asPythonObjectNode.execute(resolveHandleNode.execute(value));
        }
    }

    // -----------------------------------------------------------------------------------------------------------------
    @GenerateUncached
    public abstract static class AsCharPointerNode extends Node {
        public abstract Object execute(Object obj);

        @Specialization
        Object doPString(PString str,
                        @Cached CastToTruffleStringNode castToStringNode,
                        @Shared("cpLen") @Cached TruffleString.CodePointLengthNode codePointLengthNode,
                        @Shared("callStringToCstrNode") @Cached PCallCapiFunction callStringToCstrNode) {
            TruffleString value = castToStringNode.execute(str);
            return callStringToCstrNode.call(FUN_PY_TRUFFLE_STRING_TO_CSTR, value, codePointLengthNode.execute(value, TS_ENCODING));
        }

        @Specialization
        Object doString(TruffleString str,
                        @Shared("cpLen") @Cached TruffleString.CodePointLengthNode codePointLengthNode,
                        @Shared("callStringToCstrNode") @Cached PCallCapiFunction callStringToCstrNode) {
            return callStringToCstrNode.call(FUN_PY_TRUFFLE_STRING_TO_CSTR, str, codePointLengthNode.execute(str, TS_ENCODING));
        }

        @Specialization
        Object doBytes(PBytes bytes,
                        @Shared("toBytes") @Cached SequenceStorageNodes.ToByteArrayNode toBytesNode,
                        @Shared("callByteArrayToNativeNode") @Cached PCallCapiFunction callByteArrayToNativeNode) {
            return doByteArray(toBytesNode.execute(bytes.getSequenceStorage()), callByteArrayToNativeNode);
        }

        @Specialization
        Object doBytes(PByteArray bytes,
                        @Shared("toBytes") @Cached SequenceStorageNodes.ToByteArrayNode toBytesNode,
                        @Shared("callByteArrayToNativeNode") @Cached PCallCapiFunction callByteArrayToNativeNode) {
            return doByteArray(toBytesNode.execute(bytes.getSequenceStorage()), callByteArrayToNativeNode);
        }

        @Specialization
        Object doByteArray(byte[] arr,
                        @Shared("callByteArrayToNativeNode") @Cached PCallCapiFunction callByteArrayToNativeNode) {
            return callByteArrayToNativeNode.call(FUN_PY_TRUFFLE_BYTE_ARRAY_TO_NATIVE, PythonContext.get(this).getEnv().asGuestValue(arr), arr.length);
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

        @Specialization(limit = "1")
        static TruffleString doCStringWrapper(CStringWrapper cStringWrapper,
                        @CachedLibrary("cStringWrapper") PythonNativeWrapperLibrary lib) {
            return cStringWrapper.getString(lib);
        }

        @Specialization(limit = "1")
        static TruffleString doCByteArrayWrapper(CByteArrayWrapper cByteArrayWrapper,
                        @CachedLibrary("cByteArrayWrapper") PythonNativeWrapperLibrary lib,
                        @Shared("fromByteArray") @Cached TruffleString.FromByteArrayNode fromByteArrayNode,
                        @Shared("switchEncoding") @Cached TruffleString.SwitchEncodingNode switchEncodingNode) {
            byte[] byteArray = cByteArrayWrapper.getByteArray(lib);
            // TODO(fa): what is the encoding ? ASCII only ?
            return switchEncodingNode.execute(fromByteArrayNode.execute(byteArray, 0, byteArray.length, Encoding.US_ASCII, true), TS_ENCODING);
        }

        @Specialization(limit = "1")
        static TruffleString doSequenceArrayWrapper(PySequenceArrayWrapper obj,
                        @CachedLibrary("obj") PythonNativeWrapperLibrary lib,
                        @Cached SequenceStorageNodes.ToByteArrayNode toByteArrayNode,
                        @Shared("fromByteArray") @Cached TruffleString.FromByteArrayNode fromByteArrayNode,
                        @Shared("switchEncoding") @Cached TruffleString.SwitchEncodingNode switchEncodingNode) {
            Object delegate = lib.getDelegate(obj);
            if (delegate instanceof PBytesLike) {
                byte[] bytes = toByteArrayNode.execute(((PBytesLike) delegate).getSequenceStorage());
                // TODO(fa): what is the encoding ? ASCII only ?
                return switchEncodingNode.execute(fromByteArrayNode.execute(bytes, 0, bytes.length, Encoding.US_ASCII, true), TS_ENCODING);
            }
            throw CompilerDirectives.shouldNotReachHere();
        }

        @Specialization(guards = "!isCArrayWrapper(charPtr)")
        static PString doPointer(Object charPtr,
                        @Cached PythonObjectFactory factory) {
            return factory.createString(new NativeCharSequence(charPtr, 1, false));
        }

        static boolean isCArrayWrapper(Object object) {
            return object instanceof CArrayWrapper || object instanceof PySequenceArrayWrapper;
        }
    }

    /**
     * Very similar to {@link FromCharPointerNode}. Converts a C character pointer into a Python
     * string where decoding is done lazily. Additionally, if the provided pointer denotes a
     * {@code NULL} pointer, this will be converted to {@code None}.
     */
    public abstract static class CharPtrToJavaObjectNode extends PNodeWithContext {

        public abstract Object execute(Object object);

        @Specialization(limit = "2")
        public static Object run(Object object,
                        @Cached FromCharPointerNode fromCharPointerNode,
                        @CachedLibrary("object") InteropLibrary interopLibrary) {
            if (!interopLibrary.isNull(object)) {
                return fromCharPointerNode.execute(object);
            }
            return PNone.NONE;
        }
    }

    @GenerateUncached
    public abstract static class GetNativeClassNode extends PNodeWithContext {
        public abstract Object execute(PythonAbstractNativeObject object);

        @Specialization(guards = {"isSingleContext()", "object == cachedObject"}, limit = "1")
        @SuppressWarnings("unused")
        static Object getNativeClassCachedIdentity(PythonAbstractNativeObject object,
                        @Cached(value = "object", weak = true) PythonAbstractNativeObject cachedObject,
                        @Cached("getNativeClassUncached(object)") Object cachedClass) {
            // TODO: (tfel) is this really something we can do? It's so rare for this class to
            // change that it shouldn't be worth the effort, but in native code, anything can
            // happen. OTOH, CPython also has caches that can become invalid when someone just
            // goes and changes the ob_type of an object.
            return cachedClass;
        }

        @Specialization(guards = {"isSingleContext()", "isSame(lib, cachedObject, object)"})
        @SuppressWarnings("unused")
        static Object getNativeClassCached(PythonAbstractNativeObject object,
                        @Cached(value = "object", weak = true) PythonAbstractNativeObject cachedObject,
                        @Cached("getNativeClassUncached(object)") Object cachedClass,
                        @CachedLibrary(limit = "3") @SuppressWarnings("unused") InteropLibrary lib) {
            // TODO same as for 'getNativeClassCachedIdentity'
            return cachedClass;
        }

        @Specialization(guards = {"lib.hasMembers(object.getPtr())"}, //
                        replaces = {"getNativeClassCached", "getNativeClassCachedIdentity"}, //
                        limit = "1", //
                        rewriteOn = {UnknownIdentifierException.class, UnsupportedMessageException.class})
        static Object getNativeClassByMember(PythonAbstractNativeObject object,
                        @CachedLibrary("object.getPtr()") InteropLibrary lib,
                        @Cached ToJavaNode toJavaNode,
                        @Cached ProfileClassNode classProfile) throws UnknownIdentifierException, UnsupportedMessageException {
            // do not convert wrap 'object.object' since that is really the native pointer object
            return classProfile.profile(toJavaNode.execute(lib.readMember(object.getPtr(), NativeMember.OB_TYPE.getMemberNameJavaString())));
        }

        @Specialization(guards = {"!lib.hasMembers(object.getPtr())"}, //
                        replaces = {"getNativeClassCached", "getNativeClassCachedIdentity", "getNativeClassByMember"}, //
                        limit = "1", //
                        rewriteOn = {UnknownIdentifierException.class, UnsupportedMessageException.class})
        static Object getNativeClassByMemberAttachType(PythonAbstractNativeObject object,
                        @CachedLibrary("object.getPtr()") InteropLibrary lib,
                        @Cached PCallCapiFunction callGetObTypeNode,
                        @Cached CExtNodes.GetLLVMType getLLVMType,
                        @Cached ToJavaNode toJavaNode,
                        @Cached ProfileClassNode classProfile) throws UnknownIdentifierException, UnsupportedMessageException {
            Object typedPtr = callGetObTypeNode.call(NativeCAPISymbol.FUN_POLYGLOT_FROM_TYPED, object.getPtr(), getLLVMType.execute(CApiContext.LLVMType.PyObject));
            return classProfile.profile(toJavaNode.execute(lib.readMember(typedPtr, NativeMember.OB_TYPE.getMemberNameJavaString())));
        }

        @Specialization(replaces = {"getNativeClassCached", "getNativeClassCachedIdentity", "getNativeClassByMember", "getNativeClassByMemberAttachType"})
        static Object getNativeClass(PythonAbstractNativeObject object,
                        @Cached PCallCapiFunction callGetObTypeNode,
                        @Cached AsPythonObjectNode toJavaNode,
                        @Cached ProfileClassNode classProfile) {
            // do not convert wrap 'object.object' since that is really the native pointer object
            return classProfile.profile(toJavaNode.execute(callGetObTypeNode.call(FUN_GET_OB_TYPE, object.getPtr())));
        }

        static boolean isSame(InteropLibrary lib, PythonAbstractNativeObject cachedObject, PythonAbstractNativeObject object) {
            return lib.isIdentical(cachedObject.object, object.object, lib);
        }

        public static Object getNativeClassUncached(PythonAbstractNativeObject object) {
            // do not wrap 'object.object' since that is really the native pointer object
            return getNativeClass(object, PCallCapiFunction.getUncached(), AsPythonObjectNodeGen.getUncached(), ProfileClassNode.getUncached());
        }

    }

    // -----------------------------------------------------------------------------------------------------------------
    @GenerateUncached
    public abstract static class PointerCompareNode extends Node {
        public abstract boolean execute(TruffleString opName, Object a, Object b);

        private static boolean executeCFunction(int op, Object a, Object b, InteropLibrary interopLibrary, ImportCAPISymbolNode importCAPISymbolNode) {
            try {
                return (int) interopLibrary.execute(importCAPISymbolNode.execute(FUN_PTR_COMPARE), a, b, op) != 0;
            } catch (UnsupportedTypeException | ArityException | UnsupportedMessageException e) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw new IllegalStateException(FUN_PTR_COMPARE + " didn't work!");
            }
        }

        @Specialization(guards = "isEq(opName, equalNode)", limit = "2")
        static boolean doEq(@SuppressWarnings("unused") TruffleString opName, PythonAbstractNativeObject a, PythonAbstractNativeObject b,
                        @CachedLibrary("a.getPtr()") InteropLibrary aLib,
                        @CachedLibrary(limit = "3") InteropLibrary bLib,
                        @Cached @SuppressWarnings("unused") TruffleString.EqualNode equalNode) {
            return aLib.isIdentical(a.getPtr(), b.getPtr(), bLib);
        }

        @Specialization(guards = "isNe(opName, equalNode)", limit = "2")
        static boolean doNe(@SuppressWarnings("unused") TruffleString opName, PythonAbstractNativeObject a, PythonAbstractNativeObject b,
                        @CachedLibrary("a.getPtr()") InteropLibrary aLib,
                        @CachedLibrary(limit = "3") InteropLibrary bLib,
                        @Cached @SuppressWarnings("unused") TruffleString.EqualNode equalNode) {
            return !aLib.isIdentical(a.getPtr(), b.getPtr(), bLib);
        }

        @Specialization(guards = "cachedOpName.equals(opName)", limit = "1")
        static boolean execute(@SuppressWarnings("unused") TruffleString opName, PythonNativeObject a, PythonNativeObject b,
                        @Shared("tsEqual") @Cached @SuppressWarnings("unused") TruffleString.EqualNode equalNode,
                        @Shared("cachedOpName") @Cached("opName") @SuppressWarnings("unused") TruffleString cachedOpName,
                        @Cached(value = "findOp(opName, equalNode)", allowUncached = true) int op,
                        @CachedLibrary(limit = "1") InteropLibrary interopLibrary,
                        @Shared("importCAPISymbolNode") @Cached ImportCAPISymbolNode importCAPISymbolNode) {
            return executeCFunction(op, a.getPtr(), b.getPtr(), interopLibrary, importCAPISymbolNode);
        }

        @Specialization(guards = "cachedOpName.equals(opName)", limit = "1")
        static boolean execute(@SuppressWarnings("unused") TruffleString opName, PythonNativeObject a, long b,
                        @Shared("tsEqual") @Cached @SuppressWarnings("unused") TruffleString.EqualNode equalNode,
                        @Shared("cachedOpName") @Cached("opName") @SuppressWarnings("unused") TruffleString cachedOpName,
                        @Cached(value = "findOp(opName, equalNode)", allowUncached = true) int op,
                        @CachedLibrary(limit = "1") InteropLibrary interopLibrary,
                        @Shared("importCAPISymbolNode") @Cached ImportCAPISymbolNode importCAPISymbolNode) {
            return executeCFunction(op, a.getPtr(), b, interopLibrary, importCAPISymbolNode);
        }

        @Specialization(guards = "cachedOpName.equals(opName)", limit = "1")
        static boolean execute(@SuppressWarnings("unused") TruffleString opName, PythonNativeVoidPtr a, long b,
                        @Shared("tsEqual") @Cached @SuppressWarnings("unused") TruffleString.EqualNode equalNode,
                        @Shared("cachedOpName") @Cached("opName") @SuppressWarnings("unused") TruffleString cachedOpName,
                        @Cached(value = "findOp(opName, equalNode)", allowUncached = true) int op,
                        @CachedLibrary(limit = "1") InteropLibrary interopLibrary,
                        @Shared("importCAPISymbolNode") @Cached ImportCAPISymbolNode importCAPISymbolNode) {
            return executeCFunction(op, a.getPointerObject(), b, interopLibrary, importCAPISymbolNode);
        }

        static int findOp(TruffleString specialMethodName, TruffleString.EqualNode equalNode) {
            for (int i = 0; i < SpecialMethodNames.COMPARE_OP_COUNT; i++) {
                if (equalNode.execute(SpecialMethodNames.getCompareName(i), specialMethodName, TS_ENCODING)) {
                    return i;
                }
            }
            throw new RuntimeException("The special method used for Python C API pointer comparison must be a constant literal (i.e., interned) string");
        }

        static boolean isEq(TruffleString opName, TruffleString.EqualNode equalNode) {
            return equalNode.execute(SpecialMethodNames.T___EQ__, opName, TS_ENCODING);
        }

        static boolean isNe(TruffleString opName, TruffleString.EqualNode equalNode) {
            return equalNode.execute(SpecialMethodNames.T___NE__, opName, TS_ENCODING);
        }
    }

    @GenerateUncached
    public abstract static class PointerAddNode extends Node {
        public abstract Object execute(Object pointer, long offset);

        @Specialization
        Object add(Object pointer, long offset,
                        @Cached PCallCapiFunction callCapiFunction) {
            return callCapiFunction.call(FUN_PTR_ADD, pointer, offset);
        }
    }

    // -----------------------------------------------------------------------------------------------------------------
    @GenerateUncached
    public abstract static class AllToPythonNode extends PNodeWithContext {

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

        public static AllToPythonNode create() {
            return CExtNodesFactory.AllToPythonNodeGen.create();
        }
    }

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
                        @Cached("createNodes(args.length)") ToJavaNode[] toJavaNodes) {
            int n = cachedLength - cachedOffset;
            Object[] output = new Object[n];
            for (int i = 0; i < n; i++) {
                output[i] = toJavaNodes[i].execute(args[i + cachedOffset]);
            }
            return output;
        }

        @Specialization(replaces = "cached")
        static Object[] uncached(Object[] args, int offset,
                        @Exclusive @Cached ToJavaNode toJavaNode) {
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

        static ToJavaNode[] createNodes(int n) {
            ToJavaNode[] nodes = new ToJavaNode[n];
            for (int i = 0; i < n; i++) {
                nodes[i] = ToJavaNode.create();
            }
            return nodes;
        }

        public static AllToJavaNode create() {
            return CExtNodesFactory.AllToJavaNodeGen.create();
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
    @GenerateUncached
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
                        @Cached CallNode callNode) {
            return callNode.execute(frame, args[0], PythonUtils.EMPTY_OBJECT_ARRAY, PKeyword.EMPTY_KEYWORDS);
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
                        @Shared("allToJavaNode") @Cached AllToPythonNode allToPythonNode) {
            Object[] converted = allToPythonNode.execute(args, 1);
            return callNode.executeObject(frame, args[0], converted[0], converted[1]);
        }

        @Specialization(guards = "args.length == 4")
        Object upcall3(VirtualFrame frame, Object[] args,
                        @Cached CallTernaryMethodNode callNode,
                        @Shared("allToJavaNode") @Cached AllToPythonNode allToPythonNode) {
            Object[] converted = allToPythonNode.execute(args, 1);
            return callNode.execute(frame, args[0], converted[0], converted[1], converted[2]);
        }

        @Specialization(replaces = {"upcall0", "upcall1", "upcall2", "upcall3"})
        Object upcall(VirtualFrame frame, Object[] args,
                        @Cached CallNode callNode,
                        @Shared("allToJavaNode") @Cached AllToPythonNode allToPythonNode) {
            Object[] converted = allToPythonNode.execute(args, 1);
            return callNode.execute(frame, args[0], converted, PKeyword.EMPTY_KEYWORDS);
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
            dest[destOffset + 1] = args[argsOffset + 1];
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
            dest[destOffset + 1] = args[argsOffset + 1];
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
     * Converts for native signature:
     * {@code PyObject* meth_method(PyObject* self, PyTypeObject* defining_class, PyObject* const* args, Py_ssize_t nargs, PyObject* kwnames)}.
     * Used with {@code METH_FASTCALL | METH_KEYWORDS | METH_METHOD} flags.
     */
    public abstract static class MethodArgsToSulongNode extends ConvertArgsToSulongNode {

        @Specialization(guards = {"isArgsOffsetPlus(args.length, argsOffset, 5)"})
        static void doFastcallCached(Object[] args, int argsOffset, Object[] dest, int destOffset,
                        @Cached ToBorrowedRefNode toSulongNode1,
                        @Cached ToBorrowedRefNode toSulongNode2,
                        @Cached ToBorrowedRefNode toSulongNode4) {
            dest[destOffset + 0] = toSulongNode1.execute(args[argsOffset]);
            dest[destOffset + 1] = toSulongNode2.execute(args[argsOffset + 1]);
            dest[destOffset + 2] = args[argsOffset + 2];
            dest[destOffset + 3] = args[argsOffset + 3];
            dest[destOffset + 4] = toSulongNode4.execute(args[argsOffset + 4]);
        }

        @Specialization(guards = {"!isArgsOffsetPlus(args.length, argsOffset, 5)"})
        static void doError(Object[] args, int argsOffset, @SuppressWarnings("unused") Object[] dest, @SuppressWarnings("unused") int destOffset,
                        @Cached PRaiseNode raiseNode) {
            throw raiseNode.raise(TypeError, ErrorMessages.INVALID_ARGS_FOR_METHOD, 5, args.length - argsOffset);
        }

        public static MethodArgsToSulongNode create() {
            return CExtNodesFactory.MethodArgsToSulongNodeGen.create();
        }
    }

    /**
     * Converts the 1st argument as required for {@code allocfunc}, {@code getattrfunc},
     * {@code ssizeargfunc}, and {@code getter}.
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
            throw raiseNode.raise(TypeError, ErrorMessages.INVALID_ARGS_FOR_METHOD, 3, args.length - argsOffset);
        }

        public static TernaryFirstThirdToSulongNode create() {
            return TernaryFirstThirdToSulongNodeGen.create();
        }
    }

    /**
     * Converts arguments for C function signature
     * {@code int (*ssizeobjargproc)(PyObject *, Py_ssize_t, PyObject *)}.
     */
    public abstract static class SSizeObjArgProcToSulongNode extends ConvertArgsToSulongNode {

        @Specialization
        static void doConvert(Object[] args, int argsOffset, Object[] dest, int destOffset,
                        @Cached ToBorrowedRefNode toSulongNode1,
                        @Cached ConvertPIntToPrimitiveNode asSsizeTNode,
                        @Cached ToBorrowedRefNode toSulongNode3) {
            CompilerAsserts.partialEvaluationConstant(argsOffset);
            dest[destOffset] = toSulongNode1.execute(args[argsOffset]);
            dest[destOffset + 1] = asSsizeTNode.execute(args[argsOffset + 1], 1, Long.BYTES);
            dest[destOffset + 2] = toSulongNode3.execute(args[argsOffset + 2]);
        }

        public static SSizeObjArgProcToSulongNode create() {
            return SSizeObjArgProcToSulongNodeGen.create();
        }
    }

    /**
     * Converts arguments for C function signature
     * {@code int (*ssizeargproc)(PyObject *, Py_ssize_t)}.
     */
    public abstract static class SSizeArgProcToSulongNode extends ConvertArgsToSulongNode {

        @Specialization
        static void doConvert(Object[] args, int argsOffset, Object[] dest, int destOffset,
                        @Cached ToBorrowedRefNode toSulongNode1,
                        @Cached ConvertPIntToPrimitiveNode asSsizeTNode) {
            CompilerAsserts.partialEvaluationConstant(argsOffset);
            dest[destOffset] = toSulongNode1.execute(args[argsOffset]);
            dest[destOffset + 1] = asSsizeTNode.execute(args[argsOffset + 1], 1, Long.BYTES);
        }

        public static SSizeArgProcToSulongNode create() {
            return SSizeArgProcToSulongNodeGen.create();
        }
    }

    /**
     * Converts the 1st (self/class) and the 2rd argument as required for {@code richcmpfunc} and
     * {@code setter}.
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
            throw raiseNode.raise(TypeError, ErrorMessages.INVALID_ARGS_FOR_METHOD, 3, args.length - argsOffset);
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
            assert args[0] instanceof TruffleString;
            Object callable = getAttrNode.execute(cextModule, args[0]);
            return callNode.execute(frame, callable, PythonUtils.EMPTY_OBJECT_ARRAY, PKeyword.EMPTY_KEYWORDS);
        }

        @Specialization(guards = "args.length == 2")
        Object upcall1(VirtualFrame frame, Object cextModule, Object[] args,
                        @Cached CallUnaryMethodNode callNode,
                        @Cached CExtNodes.AsPythonObjectNode toJavaNode,
                        @Shared("getAttrNode") @Cached ReadAttributeFromObjectNode getAttrNode) {
            assert args[0] instanceof TruffleString;
            Object callable = getAttrNode.execute(cextModule, args[0]);
            return callNode.executeObject(frame, callable, toJavaNode.execute(args[1]));
        }

        @Specialization(guards = "args.length == 3")
        Object upcall2(VirtualFrame frame, Object cextModule, Object[] args,
                        @Cached CallBinaryMethodNode callNode,
                        @Shared("allToJavaNode") @Cached AllToPythonNode allToPythonNode,
                        @Shared("getAttrNode") @Cached ReadAttributeFromObjectNode getAttrNode) {
            Object[] converted = allToPythonNode.execute(args, 1);
            assert args[0] instanceof TruffleString;
            Object callable = getAttrNode.execute(cextModule, args[0]);
            return callNode.executeObject(frame, callable, converted[0], converted[1]);
        }

        @Specialization(guards = "args.length == 4")
        Object upcall3(VirtualFrame frame, Object cextModule, Object[] args,
                        @Cached CallTernaryMethodNode callNode,
                        @Shared("allToJavaNode") @Cached AllToPythonNode allToPythonNode,
                        @Shared("getAttrNode") @Cached ReadAttributeFromObjectNode getAttrNode) {
            Object[] converted = allToPythonNode.execute(args, 1);
            assert args[0] instanceof TruffleString;
            Object callable = getAttrNode.execute(cextModule, args[0]);
            return callNode.execute(frame, callable, converted[0], converted[1], converted[2]);
        }

        @Specialization(replaces = {"upcall0", "upcall1", "upcall2", "upcall3"})
        Object upcall(VirtualFrame frame, Object cextModule, Object[] args,
                        @Cached CallNode callNode,
                        @Shared("allToJavaNode") @Cached AllToPythonNode allToPythonNode,
                        @Shared("getAttrNode") @Cached ReadAttributeFromObjectNode getAttrNode) {
            Object[] converted = allToPythonNode.execute(args, 1);
            assert args[0] instanceof TruffleString;
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
            return callNode.execute(frame, callable, PythonUtils.EMPTY_OBJECT_ARRAY, PKeyword.EMPTY_KEYWORDS);
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
                        @Shared("allToJavaNode") @Cached AllToPythonNode allToPythonNode,
                        @Shared("getAttrNode") @Cached GetAttrNode getAttrNode) {
            Object[] converted = allToPythonNode.execute(args, 2);
            Object receiver = receiverToJavaNode.execute(args[0]);
            assert PGuards.isString(args[1]);
            Object callable = getAttrNode.execute(frame, receiver, args[1], PNone.NO_VALUE);
            return callNode.executeObject(frame, callable, converted[0], converted[1]);
        }

        @Specialization(guards = "args.length == 5")
        Object upcall3(VirtualFrame frame, Object[] args,
                        @Cached CallTernaryMethodNode callNode,
                        @Cached CExtNodes.AsPythonObjectNode receiverToJavaNode,
                        @Shared("allToJavaNode") @Cached AllToPythonNode allToPythonNode,
                        @Shared("getAttrNode") @Cached GetAttrNode getAttrNode) {
            Object[] converted = allToPythonNode.execute(args, 2);
            Object receiver = receiverToJavaNode.execute(args[0]);
            assert PGuards.isString(args[1]);
            Object callable = getAttrNode.execute(frame, receiver, args[1], PNone.NO_VALUE);
            return callNode.execute(frame, callable, converted[0], converted[1], converted[2]);
        }

        @Specialization(replaces = {"upcall0", "upcall1", "upcall2", "upcall3"})
        Object upcall(VirtualFrame frame, Object[] args,
                        @Cached CallNode callNode,
                        @Cached CExtNodes.AsPythonObjectNode receiverToJavaNode,
                        @Shared("allToJavaNode") @Cached AllToPythonNode allToPythonNode,
                        @Shared("getAttrNode") @Cached GetAttrNode getAttrNode) {
            // we needs at least a receiver and a member name
            assert args.length >= 2;
            Object[] converted = allToPythonNode.execute(args, 2);
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
                        @Cached PyFloatAsDoubleNode asDoubleNode,
                        @Cached LookupAndCallUnaryDynamicNode callComplex,
                        @Cached PythonObjectFactory factory,
                        @Cached PRaiseNode raiseNode) {
            Object result = callComplex.executeObject(value, T___COMPLEX__);
            // TODO(fa) according to CPython's 'PyComplex_AsCComplex', they still allow subclasses
            // of PComplex
            if (result != PNone.NO_VALUE) {
                if (result instanceof PComplex) {
                    return (PComplex) result;
                } else {
                    throw raiseNode.raise(PythonErrorType.TypeError, ErrorMessages.COMPLEX_RETURNED_NON_COMPLEX, value);
                }
            } else {
                return factory.createComplex(asDoubleNode.execute(null, value), 0.0);
            }
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
        static double run(boolean value) {
            return value ? 1.0 : 0.0;
        }

        @Specialization
        static double run(int value) {
            return value;
        }

        @Specialization
        static double run(long value) {
            return value;
        }

        @Specialization
        static double run(double value) {
            return value;
        }

        @Specialization
        static double run(PInt value) {
            return value.doubleValue();
        }

        @Specialization
        static double run(PFloat value) {
            return value.getValue();
        }

        @Specialization(guards = "!object.isDouble()")
        static double doLongNativeWrapper(DynamicObjectNativeWrapper.PrimitiveNativeWrapper object) {
            return object.getLong();
        }

        @Specialization(guards = "object.isDouble()")
        static double doDoubleNativeWrapper(DynamicObjectNativeWrapper.PrimitiveNativeWrapper object) {
            return object.getDouble();
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
    @ImportStatic(PythonUtils.class)
    @GenerateUncached
    public abstract static class CastToNativeLongNode extends PNodeWithContext {
        public abstract long execute(boolean arg);

        public abstract long execute(byte arg);

        public abstract long execute(int arg);

        public abstract long execute(long arg);

        public abstract long execute(double arg);

        public abstract Object execute(Object arg);

        @Specialization(guards = "lengthNode.execute(value, TS_ENCODING) == 1", limit = "1")
        static long doString(TruffleString value,
                        @Cached TruffleString.CodePointAtIndexNode codepointAtIndexNode,
                        @SuppressWarnings("unused") @Cached TruffleString.CodePointLengthNode lengthNode) {
            return codepointAtIndexNode.execute(value, 0, TS_ENCODING);
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
            return object.getPointerObject();
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

    // -----------------------------------------------------------------------------------------------------------------
    @GenerateUncached
    public abstract static class PCallCapiFunction extends Node {

        public final Object call(CApiContext context, NativeCAPISymbol symbol, Object... args) {
            return execute(context, symbol, args);
        }

        public final Object call(NativeCAPISymbol symbol, Object... args) {
            return execute(null, symbol, args);
        }

        protected abstract Object execute(CApiContext context, NativeCAPISymbol symbol, Object[] args);

        @Specialization(guards = "capiContext != null")
        static Object doWithContext(CApiContext capiContext, NativeCAPISymbol name, Object[] args,
                        @Shared("importCExtSymbolNode") @Cached ImportCExtSymbolNode importCExtSymbolNode,
                        @CachedLibrary(limit = "1") InteropLibrary interopLibrary,
                        @Cached EnsureTruffleStringNode ensureTruffleStringNode) {
            try {
                // TODO review EnsureTruffleStringNode with GR-37896
                return ensureTruffleStringNode.execute(interopLibrary.execute(importCExtSymbolNode.execute(capiContext, name), args));
            } catch (UnsupportedTypeException | ArityException | UnsupportedMessageException e) {
                // consider these exceptions to be fatal internal errors
                throw CompilerDirectives.shouldNotReachHere(e);
            }
        }

        @Specialization(guards = "capiContext == null")
        static Object doWithoutContext(@SuppressWarnings("unused") CApiContext capiContext, NativeCAPISymbol name, Object[] args,
                        @Shared("importCExtSymbolNode") @Cached ImportCExtSymbolNode importCExtSymbolNode,
                        @CachedLibrary(limit = "1") InteropLibrary interopLibrary,
                        @Cached EnsureTruffleStringNode ensureTruffleStringNode) {
            try {
                // TODO review EnsureTruffleStringNode with GR-37896
                return ensureTruffleStringNode.execute(interopLibrary.execute(importCExtSymbolNode.execute(PythonContext.get(importCExtSymbolNode).getCApiContext(), name), args));
            } catch (UnsupportedTypeException | ArityException | UnsupportedMessageException e) {
                // consider these exceptions to be fatal internal errors
                throw CompilerDirectives.shouldNotReachHere(e);
            }
        }

        public static PCallCapiFunction create() {
            return CExtNodesFactory.PCallCapiFunctionNodeGen.create();
        }

        public static PCallCapiFunction getUncached() {
            return CExtNodesFactory.PCallCapiFunctionNodeGen.getUncached();
        }
    }

    /**
     * Simple enum to abstract over common error indication values used in C extensions. We use this
     * enum instead of concrete values to be able to safely share them between contexts.
     */
    public enum MayRaiseErrorResult {
        NATIVE_NULL,
        NONE,
        INT,
        FLOAT
    }

    /**
     * A fake-expression node that wraps an expression node with a {@code try-catch} and any catched
     * Python exception will be transformed to native and the pre-defined error result (specified
     * with enum {@link MayRaiseErrorResult}) will be returned.
     */
    public static final class MayRaiseNode extends ExpressionNode {
        @Child private ExpressionNode wrappedBody;
        @Child private TransformExceptionToNativeNode transformExceptionToNativeNode;

        private final MayRaiseErrorResult errorResult;

        MayRaiseNode(ExpressionNode wrappedBody, MayRaiseErrorResult errorResult) {
            this.wrappedBody = wrappedBody;
            this.errorResult = errorResult;
        }

        public static MayRaiseNode create(ExpressionNode nodeToWrap, MayRaiseErrorResult errorResult) {
            return new MayRaiseNode(nodeToWrap, errorResult);
        }

        @Override
        public Object execute(VirtualFrame frame) {
            try {
                return wrappedBody.execute(frame);
            } catch (PException e) {
                // transformExceptionToNativeNode acts as a branch profile
                ensureTransformExceptionToNativeNode().execute(frame, e);
                return getErrorResult();
            }
        }

        private TransformExceptionToNativeNode ensureTransformExceptionToNativeNode() {
            if (transformExceptionToNativeNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                transformExceptionToNativeNode = insert(TransformExceptionToNativeNodeGen.create());
            }
            return transformExceptionToNativeNode;
        }

        private Object getErrorResult() {
            switch (errorResult) {
                case INT:
                    return -1;
                case FLOAT:
                    return -1.0;
                case NONE:
                    return PNone.NONE;
                case NATIVE_NULL:
                    return getContext().getNativeNull();
            }
            throw CompilerDirectives.shouldNotReachHere();
        }
    }

    // -----------------------------------------------------------------------------------------------------------------
    @GenerateUncached
    public abstract static class IsPointerNode extends com.oracle.graal.python.nodes.PNodeWithContext {

        public abstract boolean execute(PythonNativeWrapper obj);

        @Specialization(guards = "isSingleContext()", assumptions = "nativeObjectsAllManagedAssumption()")
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

        protected Assumption nativeObjectsAllManagedAssumption() {
            return getContext().getNativeObjectsAllManagedAssumption();
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
        @Specialization(guards = {"isSingleContext()", "lib.isIdentical(cachedObj, obj, lib)", "memberName == cachedMemberName"}, //
                        limit = "1", //
                        assumptions = {"getNativeClassStableAssumption(cachedObj)"})
        public Object doCachedObj(@SuppressWarnings("unused") PythonAbstractNativeObject obj, @SuppressWarnings("unused") NativeMember memberName,
                        @Cached("obj") @SuppressWarnings("unused") PythonAbstractNativeObject cachedObj,
                        @CachedLibrary(limit = "2") @SuppressWarnings("unused") InteropLibrary lib,
                        @Cached("memberName") @SuppressWarnings("unused") NativeMember cachedMemberName,
                        @Cached("doSlowPath(obj, memberName)") Object result) {
            return result;
        }

        @Specialization(guards = {"lib.hasMembers(object.getPtr())", "member.getType() == cachedMemberType"}, //
                        replaces = "doCachedObj", limit = "1", //
                        rewriteOn = {UnknownIdentifierException.class, UnsupportedMessageException.class})
        static Object getByMember(PythonAbstractNativeObject object, NativeMember member,
                        @CachedLibrary("object.getPtr()") InteropLibrary lib,
                        @Cached("member.getType()") @SuppressWarnings("unused") NativeMemberType cachedMemberType,
                        @Cached(value = "createForMember(member)", uncached = "getUncachedForMember(member)") ToJavaBaseNode toJavaNode)
                        throws UnknownIdentifierException, UnsupportedMessageException {
            // do not convert wrap 'object.object' since that is really the native pointer object
            return toJavaNode.execute(lib.readMember(object.getPtr(), member.getMemberNameJavaString()));
        }

        @Specialization(guards = {"!lib.hasMembers(object.getPtr())", "member.getType() == cachedMemberType"}, //
                        replaces = {"doCachedObj", "getByMember"}, limit = "1", //
                        rewriteOn = {UnknownIdentifierException.class, UnsupportedMessageException.class})
        static Object getByMemberAttachType(PythonAbstractNativeObject object, NativeMember member,
                        @CachedLibrary("object.getPtr()") InteropLibrary lib,
                        @Cached("member.getType()") @SuppressWarnings("unused") NativeMemberType cachedMemberType,
                        @Exclusive @Cached PCallCapiFunction callGetObTypeNode,
                        @Exclusive @Cached CExtNodes.GetLLVMType getLLVMType,
                        @Cached(value = "createForMember(member)", uncached = "getUncachedForMember(member)") ToJavaBaseNode toJavaNode)
                        throws UnknownIdentifierException, UnsupportedMessageException {
            Object typedPtr = callGetObTypeNode.call(FUN_POLYGLOT_FROM_TYPED, object.getPtr(), getLLVMType.execute(CApiContext.LLVMType.PyTypeObject));
            return toJavaNode.execute(lib.readMember(typedPtr, member.getMemberNameJavaString()));
        }

        @Specialization(guards = "memberName == cachedMemberName", limit = "1", replaces = {"doCachedObj", "getByMember", "getByMemberAttachType"})
        static Object doCachedMember(Object self, @SuppressWarnings("unused") NativeMember memberName,
                        @SuppressWarnings("unused") @Cached("memberName") NativeMember cachedMemberName,
                        @SuppressWarnings("unused") @Cached TruffleString.ConcatNode concatNode,
                        @Shared("toSulong") @Cached ToSulongNode toSulong,
                        @Cached(value = "createForMember(memberName)", uncached = "getUncachedForMember(memberName)") ToJavaBaseNode toJavaNode,
                        @Shared("callMemberGetterNode") @Cached PCallCapiFunction callMemberGetterNode) {
            return toJavaNode.execute(callMemberGetterNode.call(cachedMemberName.getGetterFunctionName(), toSulong.execute(self)));
        }

        @Specialization(replaces = {"doCachedObj", "getByMember", "getByMemberAttachType", "doCachedMember"})
        static Object doGeneric(Object self, NativeMember memberName,
                        @CachedLibrary(limit = "1") InteropLibrary lib,
                        @Shared("toSulong") @Cached ToSulongNode toSulong,
                        @Cached ToJavaNode toJavaNode,
                        @Cached CharPtrToJavaNode charPtrToJavaNode,
                        @Cached VoidPtrToJavaNode voidPtrToJavaNode,
                        @Shared("callMemberGetterNode") @Cached PCallCapiFunction callMemberGetterNode) {
            if (self instanceof PythonAbstractNativeObject) {
                PythonAbstractNativeObject nativeObject = (PythonAbstractNativeObject) self;
                if (lib.hasMembers(nativeObject.getPtr())) {
                    try {
                        return getByMember(nativeObject, memberName, lib, null, getUncachedForMember(memberName));
                    } catch (UnknownIdentifierException | UnsupportedMessageException e) {
                        // fall through
                    }
                }
            }
            Object value = callMemberGetterNode.call(memberName.getGetterFunctionName(), toSulong.execute(self));
            switch (memberName.getType()) {
                case OBJECT:
                    return toJavaNode.execute(value);
                case CSTRING:
                    return charPtrToJavaNode.execute(value);
                case PRIMITIVE:
                case POINTER:
                    return voidPtrToJavaNode.execute(value);
            }
            throw CompilerDirectives.shouldNotReachHere();
        }

        static Object doSlowPath(Object obj, NativeMember memberName) {
            return getUncachedForMember(memberName).execute(PCallCapiFunction.getUncached().call(memberName.getGetterFunctionName(), ToSulongNode.getUncached().execute(obj)));
        }

        static ToJavaBaseNode createForMember(NativeMember member) {
            switch (member.getType()) {
                case OBJECT:
                    return ToJavaNodeGen.create();
                case CSTRING:
                    return CharPtrToJavaNodeGen.create();
                case PRIMITIVE:
                case POINTER:
                    return VoidPtrToJavaNodeGen.create();
            }
            throw CompilerDirectives.shouldNotReachHere();
        }

        static ToJavaBaseNode getUncachedForMember(NativeMember member) {
            switch (member.getType()) {
                case OBJECT:
                    return ToJavaNodeGen.getUncached();
                case CSTRING:
                    return CharPtrToJavaNodeGen.getUncached();
                case PRIMITIVE:
                case POINTER:
                    return VoidPtrToJavaNodeGen.getUncached();
            }
            throw CompilerDirectives.shouldNotReachHere();
        }

        protected Assumption getNativeClassStableAssumption(PythonNativeClass clazz) {
            return getContext().getNativeClassStableAssumption(clazz, true).getAssumption();
        }

        public static GetTypeMemberNode create() {
            return GetTypeMemberNodeGen.create();
        }

        public static GetTypeMemberNode getUncached() {
            return GetTypeMemberNodeGen.getUncached();
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
                        @Cached GetThreadStateNode getThreadStateNode) {
            // TODO connect f_back
            getCurrentFrameRef.execute(frame).markAsEscaped();
            getThreadStateNode.setCurrentException(e);
        }
    }

    @GenerateUncached
    public abstract static class PRaiseNativeNode extends Node {

        public final int raiseInt(Frame frame, int errorValue, PythonBuiltinClassType errType, TruffleString format, Object... arguments) {
            return executeInt(frame, errorValue, errType, format, arguments);
        }

        public final Object raise(Frame frame, Object errorValue, PythonBuiltinClassType errType, TruffleString format, Object... arguments) {
            return execute(frame, errorValue, errType, format, arguments);
        }

        public final int raiseIntWithoutFrame(int errorValue, PythonBuiltinClassType errType, TruffleString format, Object... arguments) {
            return executeInt(null, errorValue, errType, format, arguments);
        }

        public abstract Object execute(Frame frame, Object errorValue, PythonBuiltinClassType errType, TruffleString format, Object[] arguments);

        public abstract int executeInt(Frame frame, int errorValue, PythonBuiltinClassType errType, TruffleString format, Object[] arguments);

        @Specialization
        static int doInt(Frame frame, int errorValue, PythonBuiltinClassType errType, TruffleString format, Object[] arguments,
                        @Shared("raiseNode") @Cached PRaiseNode raiseNode,
                        @Shared("transformExceptionToNativeNode") @Cached TransformExceptionToNativeNode transformExceptionToNativeNode) {
            raiseNative(frame, errType, format, arguments, raiseNode, transformExceptionToNativeNode);
            return errorValue;
        }

        @Specialization
        static Object doObject(Frame frame, Object errorValue, PythonBuiltinClassType errType, TruffleString format, Object[] arguments,
                        @Shared("raiseNode") @Cached PRaiseNode raiseNode,
                        @Shared("transformExceptionToNativeNode") @Cached TransformExceptionToNativeNode transformExceptionToNativeNode) {
            raiseNative(frame, errType, format, arguments, raiseNode, transformExceptionToNativeNode);
            return errorValue;
        }

        public static void raiseNative(Frame frame, PythonBuiltinClassType errType, TruffleString format, Object[] arguments, PRaiseNode raiseNode,
                        TransformExceptionToNativeNode transformExceptionToNativeNode) {
            try {
                throw raiseNode.raise(errType, format, arguments);
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
                        @Cached CastToJavaLongLossyNode castToJavaLongNode,
                        @CachedLibrary("object") InteropLibrary lib) throws UnknownIdentifierException, UnsupportedMessageException, UnsupportedTypeException, CannotCastException {
            CApiContext cApiContext = PythonContext.get(castToJavaLongNode).getCApiContext();
            if (!lib.isNull(object) && cApiContext != null) {
                assert value >= 0 : "adding negative reference count; dealloc might not happen";
                cApiContext.checkAccess(object, lib);
                String member = OB_REFCNT.getMemberNameJavaString();
                long refCnt = castToJavaLongNode.execute(lib.readMember(object, member));
                lib.writeMember(object, member, refCnt + value);
            }
            return object;
        }

        @Specialization(guards = "!isNativeWrapper(object)", limit = "2", replaces = "doNativeObjectByMember")
        static Object doNativeObject(Object object, long value,
                        @Cached PCallCapiFunction callAddRefCntNode,
                        @CachedLibrary("object") InteropLibrary lib) {
            CApiContext cApiContext = PythonContext.get(callAddRefCntNode).getCApiContext();
            if (!lib.isNull(object) && cApiContext != null) {
                assert value >= 0 : "adding negative reference count; dealloc might not happen";
                cApiContext.checkAccess(object, lib);
                callAddRefCntNode.call(NativeCAPISymbol.FUN_ADDREF, object, value);
            }
            return object;
        }
    }

    @GenerateUncached
    @ImportStatic(CApiGuards.class)
    public abstract static class SubRefCntNode extends PNodeWithContext {
        private static final TruffleLogger LOGGER = CApiContext.getLogger(SubRefCntNode.class);

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
                        @Cached PCallCapiFunction callAddRefCntNode,
                        @CachedLibrary("object") InteropLibrary lib) {
            PythonContext context = PythonContext.get(callAddRefCntNode);
            CApiContext cApiContext = context.getCApiContext();
            if (!lib.isNull(object) && cApiContext != null) {
                cApiContext.checkAccess(object, lib);
                long newRefcnt = (long) callAddRefCntNode.call(NativeCAPISymbol.FUN_SUBREF, object, value);
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
        void doPrimitiveNativeWrapper(@SuppressWarnings("unused") Object delegate, PrimitiveNativeWrapper nativeWrapper,
                        @Cached("createCountingProfile()") ConditionProfile hasHandleValidAssumptionProfile) {
            assert !isSmallIntegerWrapperSingleton(nativeWrapper, PythonContext.get(this)) : "clearing primitive native wrapper singleton of small integer";
            Assumption handleValidAssumption = nativeWrapper.getHandleValidAssumption();
            if (hasHandleValidAssumptionProfile.profile(handleValidAssumption != null)) {
                PythonNativeWrapper.invalidateAssumption(handleValidAssumption);
            }
        }

        @Specialization(guards = "delegate != null")
        void doPrimitiveNativeWrapperMaterialized(PythonAbstractObject delegate, PrimitiveNativeWrapper nativeWrapper,
                        @Cached ConditionProfile profile,
                        @Cached("createCountingProfile()") ConditionProfile hasHandleValidAssumptionProfile) {
            if (profile.profile(delegate.getNativeWrapper() == nativeWrapper)) {
                assert !isSmallIntegerWrapperSingleton(nativeWrapper, PythonContext.get(this)) : "clearing primitive native wrapper singleton of small integer";
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

        private static boolean isSmallIntegerWrapperSingleton(PrimitiveNativeWrapper nativeWrapper, PythonContext context) {
            return CApiGuards.isSmallIntegerWrapper(nativeWrapper) && ToSulongNode.doLongSmall(null, nativeWrapper.getLong(), context) == nativeWrapper;
        }

    }

    @GenerateUncached
    public abstract static class GetRefCntNode extends PNodeWithContext {

        public final long execute(Object ptrObject) {
            return execute(CApiContext.LAZY_CONTEXT, ptrObject);
        }

        public abstract long execute(CApiContext cApiContext, Object ptrObject);

        @Specialization(guards = "!isLazyContext(cApiContext)", limit = "2", rewriteOn = {UnknownIdentifierException.class, UnsupportedMessageException.class})
        static long doNativeObjectTypedWithContext(CApiContext cApiContext, Object ptrObject,
                        @Cached PCallCapiFunction callGetObRefCntNode,
                        @CachedLibrary("ptrObject") InteropLibrary lib,
                        @Cached CastToJavaLongLossyNode castToJavaLongNode) throws UnknownIdentifierException, UnsupportedMessageException {
            if (!lib.isNull(ptrObject)) {
                boolean haveCApiContext = cApiContext != null;
                if (haveCApiContext) {
                    cApiContext.checkAccess(ptrObject, lib);
                }

                // directly reading the member is only possible if the pointer object is typed but
                // if so, it is the faster way
                if (lib.hasMembers(ptrObject)) {
                    return castToJavaLongNode.execute(lib.readMember(ptrObject, OB_REFCNT.getMemberNameJavaString()));
                }
                if (haveCApiContext) {
                    return castToJavaLongNode.execute(callGetObRefCntNode.call(NativeCAPISymbol.FUN_GET_OB_REFCNT, ptrObject));
                }
            }
            return 0;
        }

        @Specialization(guards = "!isLazyContext(cApiContext)", limit = "2", replaces = "doNativeObjectTypedWithContext")
        static long doNativeObjectWithContext(CApiContext cApiContext, Object ptrObject,
                        @Cached PCallCapiFunction callGetObRefCntNode,
                        @CachedLibrary("ptrObject") InteropLibrary lib,
                        @Cached CastToJavaLongLossyNode castToJavaLongNode) {
            if (!lib.isNull(ptrObject) && cApiContext != null) {
                cApiContext.checkAccess(ptrObject, lib);
                return castToJavaLongNode.execute(callGetObRefCntNode.call(NativeCAPISymbol.FUN_GET_OB_REFCNT, ptrObject));
            }
            return 0;
        }

        @Specialization(limit = "2", //
                        rewriteOn = {UnknownIdentifierException.class, UnsupportedMessageException.class}, //
                        replaces = {"doNativeObjectTypedWithContext", "doNativeObjectWithContext"})
        static long doNativeObjectTyped(@SuppressWarnings("unused") CApiContext cApiContext, Object ptrObject,
                        @Cached PCallCapiFunction callGetObRefCntNode,
                        @CachedLibrary("ptrObject") InteropLibrary lib,
                        @Cached CastToJavaLongLossyNode castToJavaLongNode) throws UnknownIdentifierException, UnsupportedMessageException {
            return doNativeObjectTypedWithContext(PythonContext.get(callGetObRefCntNode).getCApiContext(), ptrObject, callGetObRefCntNode, lib, castToJavaLongNode);
        }

        @Specialization(limit = "2", replaces = {"doNativeObjectTypedWithContext", "doNativeObjectWithContext", "doNativeObjectTyped"})
        static long doNativeObject(@SuppressWarnings("unused") CApiContext cApiContext, Object ptrObject,
                        @Cached PCallCapiFunction callGetObRefCntNode,
                        @CachedLibrary("ptrObject") InteropLibrary lib,
                        @Cached CastToJavaLongLossyNode castToJavaLongNode) {
            return doNativeObjectWithContext(PythonContext.get(callGetObRefCntNode).getCApiContext(), ptrObject, callGetObRefCntNode, lib, castToJavaLongNode);
        }

        static boolean isLazyContext(CApiContext cApiContext) {
            return CApiContext.LAZY_CONTEXT == cApiContext;
        }
    }

    @GenerateUncached
    public abstract static class ResolveHandleNode extends PNodeWithContext {

        public abstract Object execute(Object pointerObject);

        public abstract Object executeLong(long pointer);

        @Specialization(limit = "3", //
                        guards = {"isSingleContext()", "cachedPointer == pointer", "cachedValue != null"}, //
                        rewriteOn = InvalidAssumptionException.class)
        static PythonNativeWrapper resolveLongCached(@SuppressWarnings("unused") long pointer,
                        @Cached("pointer") @SuppressWarnings("unused") long cachedPointer,
                        @Cached("resolveHandleUncached(pointer)") PythonNativeWrapper cachedValue,
                        @Cached("getHandleValidAssumption(cachedValue)") Assumption associationValidAssumption) throws InvalidAssumptionException {
            associationValidAssumption.check();
            return cachedValue;
        }

        @Specialization(limit = "3", //
                        guards = {"isSingleContext()", "isSame(lib, cachedPointerObject, pointerObject)", "cachedValue != null"}, //
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
            if (((boolean) callTruffleCannotBeHandleNode.call(NativeCAPISymbol.FUN_POINTS_TO_HANDLE_SPACE, pointerObject))) {
                return callTruffleManagedFromHandleNode.call(NativeCAPISymbol.FUN_RESOLVE_HANDLE, pointerObject);
            }
            // In this case, it cannot be a handle so we can just return the pointer object. It
            // could, of course, still be a native pointer.
            return pointerObject;
        }

        static PythonNativeWrapper resolveHandleUncached(Object pointerObject) {
            CompilerAsserts.neverPartOfCompilation();
            if (((boolean) PCallCapiFunction.getUncached().call(NativeCAPISymbol.FUN_POINTS_TO_HANDLE_SPACE, pointerObject))) {
                Object resolved = PCallCapiFunction.getUncached().call(NativeCAPISymbol.FUN_RESOLVE_HANDLE, pointerObject);
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

        static Assumption getHandleValidAssumption(PythonNativeWrapper nativeWrapper) {
            return nativeWrapper.ensureHandleValidAssumption();
        }
    }

    /**
     * Depending on the object's type, the size may need to be computed in very different ways. E.g.
     * any PyVarObject usually returns the number of contained elements.
     */
    @GenerateUncached
    abstract static class ObSizeNode extends PNodeWithContext {

        public abstract long execute(Object object);

        @Specialization
        static long doBoolean(boolean object) {
            return object ? 1 : 0;
        }

        @Specialization
        long doInteger(int object) {
            return doLong(object);
        }

        @Specialization
        long doLong(long object) {
            long t = PInt.abs(object);
            int sign = object < 0 ? -1 : 1;
            int size = 0;
            while (t != 0) {
                ++size;
                t >>>= getContext().getCApiContext().getPyLongBitsInDigit();
            }
            return size * sign;
        }

        @Specialization
        long doPInt(PInt object) {
            return ((PInt.bitLength(object.abs()) - 1) / getContext().getCApiContext().getPyLongBitsInDigit() + 1) * (object.isNegative() ? -1 : 1);
        }

        @Specialization
        long doPythonNativeVoidPtr(@SuppressWarnings("unused") PythonNativeVoidPtr object) {
            return ((Long.SIZE - 1) / getContext().getCApiContext().getPyLongBitsInDigit() + 1);
        }

        @Fallback
        static long doOther(Object object,
                        @Cached PyObjectSizeNode sizeNode) {
            try {
                return sizeNode.execute(null, object);
            } catch (PException e) {
                return -1;
            }
        }
    }

    @GenerateUncached
    public abstract static class GetLLVMType extends Node {
        public abstract Object execute(LLVMType llvmType);

        @Specialization(guards = "llvmType == cachedType", limit = "typeCount()")
        Object doGeneric(@SuppressWarnings("unused") LLVMType llvmType,
                        @Cached("llvmType") LLVMType cachedType) {

            CApiContext cApiContext = PythonContext.get(this).getCApiContext();
            Object llvmTypeID = cApiContext.getLLVMTypeID(cachedType);

            // TODO(fa): get rid of lazy initialization for better sharing
            if (llvmTypeID == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                NativeCAPISymbol getterFunctionSymbol = llvmType.getGetterFunctionName();
                llvmTypeID = PCallCapiFunction.getUncached().call(getterFunctionSymbol);
                cApiContext.setLLVMTypeID(cachedType, llvmTypeID);
            }
            return llvmTypeID;
        }

        static int typeCount() {
            CompilerAsserts.neverPartOfCompilation();
            return LLVMType.values().length;
        }
    }

    @GenerateUncached
    public abstract static class UnicodeFromFormatNode extends Node {
        private static Pattern pattern;

        private static Matcher match(String formatStr) {
            if (pattern == null) {
                pattern = Pattern.compile("%(?<flags>[-+ #0])?(?<width>\\d+)?(\\.(?<prec>\\d+))?(?<len>(l|ll|z))?(?<spec>[%cduixspAUVSR])");
            }
            return pattern.matcher(formatStr);
        }

        public abstract Object execute(TruffleString format, Object vaList);

        @Specialization
        @TruffleBoundary
        Object doGeneric(TruffleString f, Object vaList) {
            // TODO use TruffleString [GR-38103]
            String format = f.toJavaStringUncached();

            // helper nodes
            GetNextVaArgNode getVaArgsNode = GetNextVaArgNodeGen.getUncached();
            ToJavaNode toJavaNode = ToJavaNodeGen.getUncached();
            CastToJavaStringNode castToJavaStringNode = CastToJavaStringNodeGen.getUncached();
            FromCharPointerNode fromCharPointerNode = FromCharPointerNodeGen.getUncached();
            InteropLibrary interopLibrary = InteropLibrary.getUncached();
            PRaiseNode raiseNode = PRaiseNode.getUncached();

            // set the encapsulating node reference to get a precise error position
            EncapsulatingNodeReference current = EncapsulatingNodeReference.getCurrent();
            current.set(this);
            StringBuilder result = new StringBuilder();
            int vaArgIdx = 0;
            Object unicodeObj;
            try {
                Matcher matcher = match(format);
                int cur = 0;
                while (matcher.find(cur)) {
                    // not all combinations are valid
                    boolean valid = false;

                    // add anything before the match
                    result.append(format, cur, matcher.start());

                    cur = matcher.end();

                    String spec = matcher.group("spec");
                    String len = matcher.group("len");
                    int prec = getPrec(matcher.group("prec"));
                    assert spec.length() == 1;
                    char la = spec.charAt(0);
                    PythonContext context = PythonContext.get(raiseNode);
                    switch (la) {
                        case '%':
                            // %%
                            result.append('%');
                            valid = true;
                            break;
                        case 'c':
                            int ordinal = getAndCastToInt(getVaArgsNode, interopLibrary, raiseNode, vaList, LLVMType.int_t);
                            if (ordinal < 0 || ordinal > 0x110000) {
                                throw raiseNode.raise(PythonBuiltinClassType.OverflowError, ErrorMessages.CHARACTER_ARG_NOT_IN_RANGE);
                            }
                            result.append((char) ordinal);
                            vaArgIdx++;
                            valid = true;
                            break;
                        case 'd':
                        case 'i':
                            // %d, %i, %ld, %li, %lld, %lli, %zd, %zi
                            if (len != null) {
                                LLVMType llvmType = null;
                                switch (len) {
                                    case "ll":
                                        llvmType = LLVMType.longlong_t;
                                        break;
                                    case "l":
                                        llvmType = LLVMType.long_t;
                                        break;
                                    case "z":
                                        llvmType = LLVMType.Py_ssize_t;
                                        break;
                                }
                                if (llvmType != null) {
                                    Object value = getVaArgsNode.execute(vaList, llvmType);
                                    vaArgIdx++;
                                    result.append(castToLong(interopLibrary, raiseNode, value));
                                    valid = true;
                                }
                            } else {
                                result.append(getAndCastToInt(getVaArgsNode, interopLibrary, raiseNode, vaList, LLVMType.int_t));
                                vaArgIdx++;
                                valid = true;
                            }
                            break;
                        case 'u':
                            // %u, %lu, %llu, %zu
                            if (len != null) {
                                LLVMType llvmType = null;
                                switch (len) {
                                    case "ll":
                                        llvmType = LLVMType.ulonglong_t;
                                        break;
                                    case "l":
                                        llvmType = LLVMType.ulong_t;
                                        break;
                                    case "z":
                                        llvmType = LLVMType.size_t;
                                        break;
                                }
                                if (llvmType != null) {
                                    Object value = getVaArgsNode.execute(vaList, llvmType);
                                    vaArgIdx++;
                                    result.append(castToLong(interopLibrary, raiseNode, value));
                                    valid = true;
                                }
                            } else {
                                result.append(Integer.toUnsignedString(getAndCastToInt(getVaArgsNode, interopLibrary, raiseNode, vaList, LLVMType.uint_t)));
                                vaArgIdx++;
                                valid = true;
                            }
                            break;
                        case 'x':
                            // %x
                            result.append(Integer.toHexString(getAndCastToInt(getVaArgsNode, interopLibrary, raiseNode, vaList, LLVMType.int_t)));
                            vaArgIdx++;
                            valid = true;
                            break;
                        case 's':
                            // %s
                            Object charPtr = getVaArgsNode.getCharPtr(vaList);
                            String sValue;
                            if (interopLibrary.isNull(charPtr)) {
                                // CPython would segfault. Let's make debugging easier for ourselves
                                sValue = "(NULL)";
                            } else {
                                unicodeObj = fromCharPointerNode.execute(charPtr);
                                sValue = castToJavaStringNode.execute(unicodeObj);
                            }
                            try {
                                if (prec == -1) {
                                    result.append(sValue);
                                } else {
                                    result.append(sValue, 0, Math.min(sValue.length(), prec));
                                }
                            } catch (CannotCastException e) {
                                // That should really not happen because we created the unicode
                                // object with FromCharPointerNode which guarantees to return a
                                // String/PString.
                                throw CompilerDirectives.shouldNotReachHere();
                            }
                            vaArgIdx++;
                            valid = true;
                            break;
                        case 'p':
                            // %p
                            Object ptr = getVaArgsNode.getVoidPtr(vaList);
                            long value;
                            if (interopLibrary.isPointer(ptr)) {
                                value = interopLibrary.asPointer(ptr);
                            } else if (interopLibrary.hasIdentity(ptr)) {
                                value = interopLibrary.identityHashCode(ptr);
                            } else {
                                value = System.identityHashCode(ptr);
                            }
                            result.append(PythonUtils.formatJString("0x%x", value));
                            vaArgIdx++;
                            valid = true;
                            break;
                        case 'A':
                            // %A
                            result.append(callBuiltin(context, BuiltinNames.T_ASCII, getPyObject(getVaArgsNode, vaList)));
                            vaArgIdx++;
                            valid = true;
                            break;
                        case 'U':
                            // %U
                            result.append(castToJavaStringNode.execute(getPyObject(getVaArgsNode, vaList)));
                            vaArgIdx++;
                            valid = true;
                            break;
                        case 'V':
                            // %V
                            Object pyObjectPtr = getVaArgsNode.getPyObjectPtr(vaList);
                            if (InteropLibrary.getUncached().isNull(pyObjectPtr)) {
                                unicodeObj = fromCharPointerNode.execute(getVaArgsNode.getCharPtr(vaList));
                            } else {
                                unicodeObj = toJavaNode.execute(pyObjectPtr);
                            }
                            result.append(castToJavaStringNode.execute(unicodeObj));
                            vaArgIdx += 2;
                            valid = true;
                            break;
                        case 'S':
                            // %S
                            result.append(callBuiltin(context, BuiltinNames.T_STR, getPyObject(getVaArgsNode, vaList)));
                            vaArgIdx++;
                            valid = true;
                            break;
                        case 'R':
                            // %R
                            result.append(callBuiltin(context, BuiltinNames.T_REPR, getPyObject(getVaArgsNode, vaList)));
                            vaArgIdx++;
                            valid = true;
                            break;
                    }
                    // this means, we did not detect a valid format specifier, so add the whole
                    // group
                    if (!valid) {
                        result.append(matcher.group());
                    }
                }
                // add anything after the last matched group (or the whole format string if nothing
                // matched)
                result.append(format, cur, format.length());
            } catch (InteropException e) {
                throw raiseNode.raise(PythonBuiltinClassType.SystemError, ErrorMessages.ERROR_WHEN_ACCESSING_VAR_ARG_AT_POS, vaArgIdx);
            } finally {
                current.get();
            }
            return toTruffleStringUncached(result.toString());
        }

        private static int getPrec(String prec) {
            if (prec == null) {
                return -1;
            }
            return Integer.parseInt(prec);
        }

        /**
         * Read an element from the {@code va_list} with the specified type and cast it to a Java
         * {@code int}. Throws a {@code SystemError} if this is not possible.
         */
        private static int getAndCastToInt(GetNextVaArgNode getVaArgsNode, InteropLibrary lib, PRaiseNode raiseNode, Object vaList, LLVMType llvmType) throws InteropException {
            Object value = getVaArgsNode.execute(vaList, llvmType);
            if (lib.fitsInInt(value)) {
                try {
                    return lib.asInt(value);
                } catch (UnsupportedMessageException e) {
                    throw CompilerDirectives.shouldNotReachHere();
                }
            }
            throw raiseNode.raise(PythonBuiltinClassType.SystemError, ErrorMessages.P_OBJ_CANT_BE_INTEPRETED_AS_INTEGER, value);
        }

        /**
         * Cast a value to a Java {@code long}. Throws a {@code SystemError} if this is not
         * possible.
         */
        private static long castToLong(InteropLibrary lib, PRaiseNode raiseNode, Object value) {
            if (lib.fitsInLong(value)) {
                try {
                    return lib.asLong(value);
                } catch (UnsupportedMessageException e) {
                    throw CompilerDirectives.shouldNotReachHere();
                }
            }
            throw raiseNode.raise(PythonBuiltinClassType.SystemError, ErrorMessages.P_OBJ_CANT_BE_INTEPRETED_AS_INTEGER, value);
        }

        private static Object getPyObject(GetNextVaArgNode getVaArgsNode, Object vaList) throws InteropException {
            return ToJavaNodeGen.getUncached().execute(getVaArgsNode.getPyObjectPtr(vaList));
        }

        @TruffleBoundary
        private static Object callBuiltin(PythonContext context, TruffleString builtinName, Object object) {
            Object attribute = PyObjectLookupAttr.getUncached().execute(null, context.getBuiltins(), builtinName);
            return CastToJavaStringNodeGen.getUncached().execute(CallNode.getUncached().execute(null, attribute, object));
        }
    }

    /**
     * Attaches the appropriate LLVM type to the provided pointer object making the pointer to be
     * typed (i.e. {@code interopLib.hasMetaObject(ptr) == true}) and thus allows to do direct
     * member access via interop.<br/>
     */
    @GenerateUncached
    public abstract static class AttachLLVMTypeNode extends Node {

        public abstract Object execute(Object ptr);

        @Specialization(guards = "lib.hasMetaObject(ptr)", limit = "1")
        static Object doTyped(Object ptr,
                        @CachedLibrary("ptr") @SuppressWarnings("unused") InteropLibrary lib) {
            return ptr;
        }

        @Specialization(guards = "!lib.hasMetaObject(ptr)", limit = "1", replaces = "doTyped")
        static Object doUntyped(Object ptr,
                        @CachedLibrary("ptr") @SuppressWarnings("unused") InteropLibrary lib,
                        @Shared("getSulongTypeNode") @Cached GetSulongTypeNode getSulongTypeNode,
                        @Shared("getLLVMType") @Cached GetLLVMType getLLVMType,
                        @Shared("callGetObTypeNode") @Cached PCallCapiFunction callGetObTypeNode,
                        @Shared("callPolyglotFromTypedNode") @Cached PCallCapiFunction callPolyglotFromTypedNode,
                        @Shared("asPythonObjectNode") @Cached AsPythonObjectNode asPythonObjectNode) {
            Object type = asPythonObjectNode.execute(callGetObTypeNode.call(FUN_GET_OB_TYPE, ptr));
            Object llvmType = getSulongTypeNode.execute(type);
            if (llvmType == null) {
                llvmType = getLLVMType.execute(LLVMType.PyObject);
            }
            return callPolyglotFromTypedNode.call(FUN_POLYGLOT_FROM_TYPED, ptr, llvmType);
        }

        @Specialization(limit = "1", replaces = {"doTyped", "doUntyped"})
        static Object doGeneric(Object ptr,
                        @CachedLibrary("ptr") InteropLibrary lib,
                        @Shared("getSulongTypeNode") @Cached GetSulongTypeNode getSulongTypeNode,
                        @Shared("getLLVMType") @Cached GetLLVMType getLLVMType,
                        @Shared("callGetObTypeNode") @Cached PCallCapiFunction callGetObTypeNode,
                        @Shared("callPolyglotFromTypedNode") @Cached PCallCapiFunction callPolyglotFromTypedNode,
                        @Shared("asPythonObjectNode") @Cached AsPythonObjectNode asPythonObjectNode) {
            if (!lib.hasMetaObject(ptr)) {
                return doUntyped(ptr, lib, getSulongTypeNode, getLLVMType, callGetObTypeNode, callPolyglotFromTypedNode, asPythonObjectNode);
            }
            return ptr;
        }
    }

    abstract static class MultiPhaseExtensionModuleInitNode extends Node {

        static final String J_M_NAME = "m_name";
        static final String J_M_DOC = "m_doc";
        static final String J_M_METHODS = "m_methods";
        static final TruffleString T_M_METHODS = tsLiteral(J_M_METHODS);
        static final String J_M_SLOTS = "m_slots";
        static final TruffleString T_M_SLOTS = tsLiteral(J_M_SLOTS);
        static final String J_M_SIZE = "m_size";

        // according to definitions in 'moduleobject.h'
        static final int SLOT_PY_MOD_CREATE = 1;
        static final int SLOT_PY_MOD_EXEC = 2;

        // member names of 'PyModuleDef_Slot'
        static final String J_MODULEDEF_SLOT = "slot";
        static final String J_MODULEDEF_VALUE = "value";

    }

    /**
     * Equivalent of {@code PyModule_FromDefAndSpec}. Creates a Python module from a module
     * definition structure:
     *
     * <pre>
     * typedef struct PyModuleDef {
     *     PyModuleDef_Base m_base;
     *     const char* m_name;
     *     const char* m_doc;
     *     Py_ssize_t m_size;
     *     PyMethodDef *m_methods;
     *     struct PyModuleDef_Slot* m_slots;
     *     traverseproc m_traverse;
     *     inquiry m_clear;
     *     freefunc m_free;
     * } PyModuleDef
     * </pre>
     */
    @GenerateUncached
    public abstract static class CreateModuleNode extends MultiPhaseExtensionModuleInitNode {

        @TruffleBoundary
        static boolean checkLayout(Object moduleDef, InteropLibrary moduleDefLib) {
            String[] members = new String[]{"m_base", J_M_NAME, J_M_DOC, J_M_SIZE, J_M_METHODS, J_M_SLOTS, "m_traverse", "m_clear", "m_free"};
            for (String member : members) {
                if (!moduleDefLib.isMemberReadable(moduleDef, member)) {
                    return false;
                }
            }
            return true;
        }

        public abstract Object execute(CApiContext capiContext, ModuleSpec moduleSpec, Object moduleDef);

        @Specialization
        static Object doGeneric(CApiContext capiContext, ModuleSpec moduleSpec, PythonAbstractNativeObject moduleDefWrapper,
                        @Cached PythonObjectFactory factory,
                        @Cached ConditionProfile errOccurredProfile,
                        @Cached GetSulongTypeNode getSulongTypeNode,
                        @Cached PCallCapiFunction callAttachTypeNode,
                        @Cached PCallCapiFunction callGetterNode,
                        @CachedLibrary(limit = "3") InteropLibrary interopLib,
                        @Cached FromCharPointerNode fromCharPointerNode,
                        @Cached WriteAttributeToObjectNode writeAttrNode,
                        @Cached WriteAttributeToDynamicObjectNode writeAttrToMethodNode,
                        @Cached CreateMethodNode addLegacyMethodNode,
                        @Cached ToBorrowedRefNode moduleSpecToNativeNode,
                        @Cached ToJavaStealingNode toJavaNode,
                        @Cached PRaiseNode raiseNode) {
            // call to type the pointer
            Object typeId = getSulongTypeNode.execute(PythonBuiltinClassType.PythonModuleDef);
            Object moduleDef = callAttachTypeNode.call(capiContext, FUN_POLYGLOT_FROM_TYPED, moduleDefWrapper.getPtr(), typeId);

            assert checkLayout(moduleDef, interopLib);

            /*
             * The name of the module is taken from the module spec and *NOT* from the module
             * definition.
             */
            TruffleString mName = moduleSpec.name;
            Object mDoc;
            int mSize;
            try {
                // do not eagerly read the doc string; this turned out to be unnecessarily expensive
                Object docPtr = interopLib.readMember(moduleDef, J_M_DOC);
                if (interopLib.isNull(docPtr)) {
                    mDoc = PNone.NO_VALUE;
                } else {
                    mDoc = fromCharPointerNode.execute(docPtr);
                }

                Object mSizeObj = interopLib.readMember(moduleDef, J_M_SIZE);
                mSize = interopLib.asInt(mSizeObj);
            } catch (UnsupportedMessageException | UnknownIdentifierException e) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw raiseNode.raise(PythonBuiltinClassType.SystemError, ErrorMessages.CANNOT_CREATE_MODULE_FROM_DEFINITION, e);
            }

            if (mSize < 0) {
                throw raiseNode.raise(PythonBuiltinClassType.SystemError, ErrorMessages.M_SIZE_CANNOT_BE_NEGATIVE, mName);
            }

            // parse slot definitions
            Object createFunction = null;
            boolean hasExecutionSlots = false;
            try {
                Object slotDefinitions = callGetterNode.call(capiContext, FUN_GET_PYMODULEDEF_M_SLOTS, moduleDef);
                if (!interopLib.isNull(slotDefinitions)) {
                    if (!interopLib.hasArrayElements(slotDefinitions)) {
                        CompilerDirectives.transferToInterpreterAndInvalidate();
                        throw raiseNode.raise(PythonBuiltinClassType.SystemError, ErrorMessages.FIELD_S_DID_NOT_RETURN_AN_ARRAY, T_M_SLOTS);
                    }
                    long nSlots = interopLib.getArraySize(slotDefinitions);
                    for (long i = 0; i < nSlots; i++) {
                        Object slotDefinition = interopLib.readArrayElement(slotDefinitions, i);

                        Object slotIdObj = interopLib.readMember(slotDefinition, J_MODULEDEF_SLOT);
                        int slotId = interopLib.asInt(slotIdObj);
                        switch (slotId) {
                            case SLOT_PY_MOD_CREATE:
                                if (createFunction != null) {
                                    throw raiseNode.raise(SystemError, ErrorMessages.MODULE_HAS_MULTIPLE_CREATE_SLOTS, mName);
                                }
                                createFunction = interopLib.readMember(slotDefinition, J_MODULEDEF_VALUE);
                                break;
                            case SLOT_PY_MOD_EXEC:
                                hasExecutionSlots = true;
                                break;
                            default:
                                throw raiseNode.raise(SystemError, ErrorMessages.MODULE_USES_UNKNOW_SLOT_ID, mName, slotId);
                        }
                    }
                }
            } catch (UnsupportedMessageException | InvalidArrayIndexException | UnknownIdentifierException e) {
                throw CompilerDirectives.shouldNotReachHere();
            }

            Object module;
            if (createFunction != null && !interopLib.isNull(createFunction)) {
                Object[] cArguments = new Object[]{moduleSpecToNativeNode.execute(capiContext, moduleSpec.originalModuleSpec), moduleDef};
                try {
                    Object result = interopLib.execute(createFunction, cArguments);
                    DefaultCheckFunctionResultNode.checkFunctionResult(raiseNode, mName, interopLib.isNull(result), true, PythonLanguage.get(callGetterNode), capiContext.getContext(),
                                    errOccurredProfile, ErrorMessages.CREATION_FAILD_WITHOUT_EXCEPTION, ErrorMessages.CREATION_RAISED_EXCEPTION);
                    module = toJavaNode.execute(capiContext, result);
                } catch (UnsupportedTypeException | ArityException | UnsupportedMessageException e) {
                    throw CompilerDirectives.shouldNotReachHere();
                }

                /*
                 * We are more strict than CPython and require this to be a PythonModule object.
                 * This means, if the custom 'create' function uses a native subtype of the module
                 * type, then we require it to call our new function.
                 */
                if (!(module instanceof PythonModule)) {
                    if (mSize > 0) {
                        throw raiseNode.raise(SystemError, ErrorMessages.NOT_A_MODULE_OBJECT_BUT_REQUESTS_MODULE_STATE, mName);
                    }
                    if (hasExecutionSlots) {
                        throw raiseNode.raise(SystemError, ErrorMessages.MODULE_SPECIFIES_EXEC_SLOTS_BUT_DIDNT_CREATE_INSTANCE, mName);
                    }
                    // otherwise CPython is just fine
                } else {
                    ((PythonModule) module).setNativeModuleDef(moduleDef);
                }
            } else {
                PythonModule pythonModule = factory.createPythonModule(mName);
                pythonModule.setNativeModuleDef(moduleDef);
                module = pythonModule;
            }

            // parse method definitions
            try {
                Object methodDefinitions = callGetterNode.call(capiContext, FUN_GET_PYMODULEDEF_M_METHODS, moduleDef);
                if (!interopLib.isNull(methodDefinitions)) {
                    if (!interopLib.hasArrayElements(methodDefinitions)) {
                        CompilerDirectives.transferToInterpreterAndInvalidate();
                        throw raiseNode.raise(PythonBuiltinClassType.SystemError, ErrorMessages.FIELD_S_DID_NOT_RETURN_AN_ARRAY, T_M_METHODS);
                    }
                    long nMethods = interopLib.getArraySize(methodDefinitions);
                    for (long i = 0; i < nMethods; i++) {
                        Object methodDefinition = interopLib.readArrayElement(methodDefinitions, i);
                        PBuiltinFunction fun = addLegacyMethodNode.execute(capiContext, methodDefinition);
                        PBuiltinMethod method = factory.createBuiltinMethod(module, fun);
                        writeAttrToMethodNode.execute(method, SpecialAttributeNames.T___MODULE__, mName);
                        writeAttrNode.execute(module, fun.getName(), method);
                    }
                }
            } catch (UnsupportedMessageException | InvalidArrayIndexException e) {
                /*
                 * In general, we should never get these exceptions because the static typing of the
                 * C code guarantees our assumptions.
                 */
                throw CompilerDirectives.shouldNotReachHere();
            }

            writeAttrNode.execute(module, SpecialAttributeNames.T___DOC__, mDoc);
            return module;
        }
    }

    /**
     * Equivalent of {@code PyModule_ExecDef}.
     */
    @GenerateUncached
    public abstract static class ExecModuleNode extends MultiPhaseExtensionModuleInitNode {

        public abstract int execute(CApiContext capiContext, PythonModule module, Object moduleDef);

        @Specialization
        static int doGeneric(CApiContext capiContext, PythonModule module, Object moduleDef,
                        @Cached ConditionProfile errOccurredProfile,
                        @Cached ModuleGetNameNode getNameNode,
                        @Cached PCallCapiFunction callGetterNode,
                        @Cached WriteNativeMemberNode writeNativeMemberNode,
                        @Cached PCallCapiFunction callMallocNode,
                        @CachedLibrary(limit = "3") InteropLibrary interopLib,
                        @Cached ToBorrowedRefNode moduleToNativeNode,
                        @Cached PRaiseNode raiseNode) {
            // call to type the pointer
            assert CreateModuleNode.checkLayout(moduleDef, interopLib);

            TruffleString mName = getNameNode.execute(module);
            int mSize;
            try {
                Object mSizeObj = interopLib.readMember(moduleDef, J_M_SIZE);
                mSize = interopLib.asInt(mSizeObj);
            } catch (UnsupportedMessageException | UnknownIdentifierException e) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw raiseNode.raise(PythonBuiltinClassType.SystemError, ErrorMessages.CANNOT_CREATE_MODULE_FROM_DEFINITION, e);
            }

            try {
                // allocate md_state if necessary
                if (mSize >= 0) {
                    // The cast is not nice but it will at least fail if we change the wrapper type.
                    PythonNativeWrapper moduleWrapper = (PythonNativeWrapper) moduleToNativeNode.execute(capiContext, module);
                    /*
                     * TODO(fa): We currently leak 'md_state' and need to use a shared finalizer or
                     * similar. We ignore that for now since the size will usually be very small
                     * and/or we could also use a Truffle buffer object.
                     */
                    Object moduleStatePtr = callMallocNode.call(capiContext, NativeCAPISymbol.FUN_PYMEM_RAWCALLOC, 1, mSize);
                    writeNativeMemberNode.execute(module, moduleWrapper, MD_STATE.getMemberNameJavaString(), moduleStatePtr);
                }

                // parse slot definitions
                Object slotDefinitions = callGetterNode.call(capiContext, FUN_GET_PYMODULEDEF_M_SLOTS, moduleDef);
                if (interopLib.isNull(slotDefinitions)) {
                    return 0;
                }
                if (!interopLib.hasArrayElements(slotDefinitions)) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    throw raiseNode.raise(PythonBuiltinClassType.SystemError, ErrorMessages.FIELD_S_DID_NOT_RETURN_AN_ARRAY, T_M_SLOTS);
                }
                long nSlots = interopLib.getArraySize(slotDefinitions);
                for (long i = 0; i < nSlots; i++) {
                    Object slotDefinition = interopLib.readArrayElement(slotDefinitions, i);

                    Object slotIdObj = interopLib.readMember(slotDefinition, J_MODULEDEF_SLOT);
                    int slotId = interopLib.asInt(slotIdObj);
                    switch (slotId) {
                        case SLOT_PY_MOD_CREATE:
                            // handled in CreateModuleNode
                            break;
                        case SLOT_PY_MOD_EXEC:
                            Object execFunction = interopLib.readMember(slotDefinition, J_MODULEDEF_VALUE);
                            Object result = interopLib.execute(execFunction, moduleToNativeNode.execute(capiContext, module));
                            int iResult = interopLib.asInt(result);
                            /*
                             * It's a bit counterintuitive that we use 'isPrimitiveValue = false'
                             * but the function's return value is actually not a result but a status
                             * code. So, if the status code is '!=0' we know that an error occurred
                             * and won't ignore this if no error is set. This is then the same
                             * behaviour if we would have a pointer return type and got 'NULL'.
                             */
                            DefaultCheckFunctionResultNode.checkFunctionResult(raiseNode, mName, iResult != 0, true, PythonLanguage.get(callGetterNode), capiContext.getContext(),
                                            errOccurredProfile,
                                            ErrorMessages.EXECUTION_FAILED_WITHOUT_EXCEPTION, ErrorMessages.EXECUTION_RAISED_EXCEPTION);
                            break;
                        default:
                            throw raiseNode.raise(SystemError, ErrorMessages.MODULE_INITIALIZED_WITH_UNKNOWN_SLOT, mName, slotId);
                    }
                }
            } catch (UnsupportedMessageException | InvalidArrayIndexException | UnknownIdentifierException | UnsupportedTypeException | ArityException e) {
                throw CompilerDirectives.shouldNotReachHere();
            }

            return 0;
        }
    }

    /**
     * <pre>
     *     struct PyMethodDef {
     *         const char * ml_name;
     *         PyCFunction  ml_meth;
     *         int          ml_flags;
     *         const char * ml_doc;
     *     };
     * </pre>
     */
    @GenerateUncached
    public abstract static class CreateMethodNode extends PNodeWithContext {
        private static final TruffleLogger LOGGER = PythonLanguage.getLogger(CreateMethodNode.class);

        public static final String J_ML_NAME = "ml_name";
        public static final String J_ML_DOC = "ml_doc";
        public static final String J_ML_FLAGS = "ml_flags";
        public static final String J_ML_METH = "ml_meth";

        public abstract PBuiltinFunction execute(CApiContext context, Object legacyMethodDef);

        @Specialization(limit = "1")
        static PBuiltinFunction doIt(CApiContext context, Object methodDef,
                        @CachedLibrary("methodDef") InteropLibrary interopLibrary,
                        @CachedLibrary(limit = "2") InteropLibrary resultLib,
                        @Cached PCallCapiFunction callGetNameNode,
                        @Cached FromCharPointerNode fromCharPointerNode,
                        @Cached PythonObjectFactory factory,
                        @Cached WriteAttributeToDynamicObjectNode writeAttributeToDynamicObjectNode,
                        @Cached PRaiseNode raiseNode) {

            assert checkLayout(methodDef) : "provided pointer has unexpected structure";

            TruffleString methodName;
            try {
                Object methodNamePtr = interopLibrary.readMember(methodDef, J_ML_NAME);
                methodName = (TruffleString) callGetNameNode.call(context, NativeCAPISymbol.FUN_POLYGLOT_FROM_STRING, methodNamePtr, StandardCharsets.UTF_8.name());
            } catch (UnsupportedMessageException | UnknownIdentifierException e) {
                throw CompilerDirectives.shouldNotReachHere();
            }

            // note: 'ml_doc' may be NULL; in this case, we would store 'None'
            Object methodDoc = PNone.NONE;
            try {
                Object methodDocPtr = interopLibrary.readMember(methodDef, J_ML_DOC);
                if (!resultLib.isNull(methodDocPtr)) {
                    methodDoc = fromCharPointerNode.execute(methodDocPtr);
                }
            } catch (UnsupportedMessageException | UnknownIdentifierException e) {
                // fall through
            }

            Object methodFlagsObj;
            int flags;
            Object mlMethObj;
            try {
                methodFlagsObj = interopLibrary.readMember(methodDef, J_ML_FLAGS);
                if (!resultLib.fitsInInt(methodFlagsObj)) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    throw raiseNode.raise(PythonBuiltinClassType.SystemError, ErrorMessages.ML_FLAGS_IS_NOT_INTEGER, methodName);
                }
                flags = resultLib.asInt(methodFlagsObj);

                mlMethObj = interopLibrary.readMember(methodDef, J_ML_METH);
                if (!resultLib.isExecutable(mlMethObj)) {
                    LOGGER.warning(() -> String.format("ml_meth of %s is not callable", methodName));
                }
            } catch (UnknownIdentifierException e) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw raiseNode.raise(PythonBuiltinClassType.SystemError, ErrorMessages.INVALID_STRUCT_MEMBER, e.getUnknownIdentifier());
            } catch (UnsupportedMessageException e) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw raiseNode.raise(PythonBuiltinClassType.TypeError, ErrorMessages.CANNOT_ACCESS_STRUCT_MEMBER_FLAGS_OR_METH);
            }

            // CPy-style methods
            // TODO(fa) support static and class methods
            PRootNode rootNode = createWrapperRootNode(PythonLanguage.get(callGetNameNode), flags, methodName);
            PKeyword[] kwDefaults = ExternalFunctionNodes.createKwDefaults(mlMethObj);
            PBuiltinFunction function = factory.createBuiltinFunction(methodName, null, PythonUtils.EMPTY_OBJECT_ARRAY, kwDefaults, flags, PythonUtils.getOrCreateCallTarget(rootNode));

            // write doc string; we need to directly write to the storage otherwise it is disallowed
            // writing to builtin types.
            writeAttributeToDynamicObjectNode.execute(function.getStorage(), SpecialAttributeNames.T___DOC__, methodDoc);

            return function;
        }

        @TruffleBoundary
        private static boolean checkLayout(Object methodDef) {
            String[] members = new String[]{J_ML_NAME, J_ML_METH, J_ML_FLAGS, J_ML_DOC};
            InteropLibrary lib = InteropLibrary.getUncached(methodDef);
            for (String member : members) {
                if (!lib.isMemberReadable(methodDef, member)) {
                    return false;
                }
            }
            return true;
        }

        @TruffleBoundary
        private static PRootNode createWrapperRootNode(PythonLanguage language, int flags, TruffleString name) {
            boolean isStatic = CExtContext.isMethStatic(flags);
            if (CExtContext.isMethNoArgs(flags)) {
                return new MethNoargsRoot(language, name, isStatic, PExternalFunctionWrapper.NOARGS);
            } else if (CExtContext.isMethO(flags)) {
                return new MethORoot(language, name, isStatic, PExternalFunctionWrapper.O);
            } else if (CExtContext.isMethVarargsWithKeywords(flags)) {
                return new MethKeywordsRoot(language, name, isStatic, PExternalFunctionWrapper.KEYWORDS);
            } else if (CExtContext.isMethVarargs(flags)) {
                return new MethVarargsRoot(language, name, isStatic, PExternalFunctionWrapper.VARARGS);
            } else if (CExtContext.isMethMethod(flags)) {
                return new MethMethodRoot(language, name, isStatic, PExternalFunctionWrapper.METHOD);
            } else if (CExtContext.isMethFastcallWithKeywords(flags)) {
                return new MethFastcallWithKeywordsRoot(language, name, isStatic, PExternalFunctionWrapper.FASTCALL_WITH_KEYWORDS);
            } else if (CExtContext.isMethFastcall(flags)) {
                return new MethFastcallRoot(language, name, isStatic, PExternalFunctionWrapper.FASTCALL);
            }
            throw new IllegalStateException("illegal method flags");
        }
    }

    @GenerateUncached
    public abstract static class HasNativeBufferNode extends PNodeWithContext {
        public abstract boolean execute(PythonNativeObject object);

        @Specialization
        static boolean readTpAsBuffer(PythonNativeObject object,
                        @CachedLibrary(limit = "1") InteropLibrary lib,
                        @Cached GetClassNode getClassNode,
                        @Cached PCallCapiFunction callCapiFunction,
                        @Cached ToSulongNode toSulongNode) {
            Object type = getClassNode.execute(object);
            Object result = callCapiFunction.call(NativeCAPISymbol.FUN_GET_TP_AS_BUFFER, toSulongNode.execute(type));
            return !lib.isNull(result);
        }
    }

    @GenerateUncached
    public abstract static class CreateMemoryViewFromNativeNode extends PNodeWithContext {
        public abstract PMemoryView execute(PythonNativeObject object, int flags);

        @Specialization
        static PMemoryView fromNative(PythonNativeObject buf, int flags,
                        @Cached ToSulongNode toSulongNode,
                        @Cached AsPythonObjectNode asPythonObjectNode,
                        @Cached PCallCapiFunction callCapiFunction,
                        @Cached DefaultCheckFunctionResultNode checkFunctionResultNode) {
            Object result = callCapiFunction.call(FUN_PY_TRUFFLE_MEMORYVIEW_FROM_OBJECT, toSulongNode.execute(buf), flags);
            checkFunctionResultNode.execute(PythonContext.get(callCapiFunction), FUN_PY_TRUFFLE_MEMORYVIEW_FROM_OBJECT.getTsName(), result);
            return (PMemoryView) asPythonObjectNode.execute(result);
        }
    }

    /**
     * Decrements the ref count by one of any {@link PythonNativeWrapper} object.
     * <p>
     * This node avoids memory leaks for arguments given to native.<br>
     * Problem description:<br>
     * {@link PythonNativeWrapper} objects given to C code may go to native, i.e., a handle will be
     * allocated. In this case, no ref count manipulation is done since the C code considers the
     * reference to be borrowed and the Python code just doesn't do it because we have a GC. This
     * means that the handle will stay allocated and we are leaking the wrapper object.
     * </p>
     */
    @ImportStatic(CApiGuards.class)
    abstract static class ReleaseNativeWrapperNode extends Node {

        public abstract void execute(Object pythonObject);

        @Specialization
        static void doNativeWrapper(PythonNativeWrapper nativeWrapper,
                        @Cached TraverseNativeWrapperNode traverseNativeWrapperNode,
                        @Cached SubRefCntNode subRefCntNode) {
            // in the cached case, refCntNode acts as a branch profile
            if (subRefCntNode.dec(nativeWrapper) == 0) {
                traverseNativeWrapperNode.execute(nativeWrapper.getDelegateSlowPath());
            }
        }

        @Specialization(guards = "!isNativeWrapper(object)")
        @SuppressWarnings("unused")
        static void doOther(Object object) {
            // just do nothing; this is an implicit profile
        }
    }

    /**
     * Traverses the items of a tuple and applies {@link ReleaseNativeWrapperNode} on the items if
     * the tuple is up to be released.
     */
    abstract static class TraverseNativeWrapperNode extends Node {

        public abstract void execute(Object containerObject);

        @Specialization
        static void doTuple(PTuple tuple,
                        @Cached ToArrayNode toArrayNode,
                        @Cached SubRefCntNode subRefCntNode) {

            Object[] values = toArrayNode.execute(tuple.getSequenceStorage());
            for (int i = 0; i < values.length; i++) {
                Object value = values[i];
                if (value instanceof PythonObject) {
                    DynamicObjectNativeWrapper nativeWrapper = ((PythonObject) value).getNativeWrapper();
                    // only traverse if refCnt != 0; this will break the cycle
                    if (nativeWrapper != null) {
                        subRefCntNode.dec(nativeWrapper);
                    }
                }
            }
        }

        @Fallback
        static void doOther(@SuppressWarnings("unused") Object other) {
            // do nothing
        }
    }
}
