/*
 * Copyright (c) 2018, 2026, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.CharPtrAsTruffleString;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.InitResult;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.InquiryResult;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.Int;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.IterResult;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.Pointer;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PrimitiveResult32;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PrimitiveResult64;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PyObject;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PyObjectReturn;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PyObjectTransfer;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PyTypeObject;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.Py_ssize_t;
import static com.oracle.graal.python.builtins.objects.cext.structs.CStructAccess.wrapPointer;
import static com.oracle.graal.python.builtins.objects.object.PythonObject.MANAGED_REFCNT;
import static com.oracle.graal.python.nfi2.NativeMemory.free;
import static com.oracle.graal.python.nfi2.NativeMemory.readPtrArrayElement;
import static com.oracle.graal.python.nodes.StringLiterals.T_EMPTY_STRING;
import static com.oracle.graal.python.util.PythonUtils.tsArray;
import static com.oracle.graal.python.util.PythonUtils.tsLiteral;

import java.lang.ref.Reference;
import java.util.logging.Level;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.EnsurePythonObjectNode;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.PythonObjectArrayCreateNode;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.PythonObjectArrayFreeNode;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodesFactory.AsCharPointerNodeGen;
import com.oracle.graal.python.builtins.objects.cext.capi.ExternalFunctionNodesFactory.CreateArgsTupleNodeGen;
import com.oracle.graal.python.builtins.objects.cext.capi.ExternalFunctionNodesFactory.PyObjectCheckFunctionResultNodeGen;
import com.oracle.graal.python.builtins.objects.cext.capi.ExternalFunctionNodesFactory.ReleaseNativeSequenceStorageNodeGen;
import com.oracle.graal.python.builtins.objects.cext.capi.ExternalFunctionNodesFactory.ExternalFunctionWrapperInvokeNodeGen;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTiming;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTransitions;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTransitions.NativeToPythonNode;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTransitions.NativeToPythonReturnNode;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTransitions.PythonToNativeInternalNode;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTransitions.PythonToNativeNode;
import com.oracle.graal.python.builtins.objects.cext.common.CExtCommonNodes;
import com.oracle.graal.python.builtins.objects.cext.common.CExtCommonNodes.CheckFunctionResultNode;
import com.oracle.graal.python.builtins.objects.cext.common.CExtCommonNodes.ConvertPIntToPrimitiveNode;
import com.oracle.graal.python.builtins.objects.cext.common.CExtCommonNodes.GetIndexNode;
import com.oracle.graal.python.builtins.objects.cext.common.CExtCommonNodes.TransformExceptionFromNativeNode;
import com.oracle.graal.python.builtins.objects.cext.common.CExtCommonNodesFactory.ConvertPIntToPrimitiveNodeGen;
import com.oracle.graal.python.builtins.objects.cext.common.CExtToJavaNode;
import com.oracle.graal.python.builtins.objects.cext.common.CExtToNativeNode;
import com.oracle.graal.python.builtins.objects.cext.common.NativeCExtSymbol;
import com.oracle.graal.python.builtins.objects.cext.common.NativePointer;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes.StorageToNativeNode;
import com.oracle.graal.python.builtins.objects.function.PArguments;
import com.oracle.graal.python.builtins.objects.function.PBuiltinFunction;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.builtins.objects.function.Signature;
import com.oracle.graal.python.builtins.objects.object.PythonBuiltinObject;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlot;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlot.TpSlotNative;
import com.oracle.graal.python.lib.RichCmpOp;
import com.oracle.graal.python.nfi2.Nfi;
import com.oracle.graal.python.nfi2.NfiBoundFunction;
import com.oracle.graal.python.nfi2.NfiDowncallSignature;
import com.oracle.graal.python.nfi2.NfiType;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.PRootNode;
import com.oracle.graal.python.nodes.argument.ReadIndexedArgumentNode;
import com.oracle.graal.python.nodes.argument.ReadVarArgsNode;
import com.oracle.graal.python.nodes.argument.ReadVarKeywordsNode;
import com.oracle.graal.python.nodes.interop.PForeignToPTypeNode;
import com.oracle.graal.python.nodes.object.IsForeignObjectNode;
import com.oracle.graal.python.nodes.truffle.PythonIntegerTypes;
import com.oracle.graal.python.nodes.util.CastToTruffleStringNode;
import com.oracle.graal.python.runtime.ExecutionContext.BoundaryCallContext;
import com.oracle.graal.python.runtime.ExecutionContext.CalleeContext;
import com.oracle.graal.python.runtime.GilNode;
import com.oracle.graal.python.runtime.IndirectCallData.BoundaryCallData;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.PythonContext.GetThreadStateNode;
import com.oracle.graal.python.runtime.PythonContext.PythonThreadState;
import com.oracle.graal.python.runtime.PythonContextFactory.GetThreadStateNodeGen;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.object.PFactory;
import com.oracle.graal.python.runtime.sequence.storage.NativeObjectSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.NativeSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.ObjectSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.SequenceStorage;
import com.oracle.graal.python.util.Function;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.TruffleLogger;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.InlineSupport.InlineTarget;
import com.oracle.truffle.api.dsl.InlineSupport.RequiredField;
import com.oracle.truffle.api.dsl.InlineSupport.StateField;
import com.oracle.truffle.api.dsl.NeverDefault;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.TypeSystemReference;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.ExplodeLoop.LoopExplosionKind;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.nodes.UnexpectedResultException;
import com.oracle.truffle.api.profiles.ConditionProfile;
import com.oracle.truffle.api.profiles.InlinedConditionProfile;
import com.oracle.truffle.api.strings.TruffleString;

public abstract class ExternalFunctionNodes {

    private static final TruffleLogger LOGGER = CApiContext.getLogger(ExternalFunctionNodes.class);

    public static final TruffleString KW_CALLABLE = tsLiteral("$callable");
    public static final TruffleString KW_CLOSURE = tsLiteral("$closure");
    static final TruffleString[] KEYWORDS_HIDDEN_CALLABLE = new TruffleString[]{KW_CALLABLE};
    static final TruffleString[] KEYWORDS_HIDDEN_CALLABLE_AND_CLOSURE = new TruffleString[]{KW_CALLABLE, KW_CLOSURE};

    public static PKeyword[] createKwDefaults(NfiBoundFunction callable) {
        return new PKeyword[]{new PKeyword(KW_CALLABLE, callable)};
    }

    public static PKeyword[] createKwDefaults(NfiBoundFunction callable, long closure) {
        return new PKeyword[]{new PKeyword(KW_CALLABLE, callable), new PKeyword(KW_CLOSURE, closure)};
    }

    /**
     * On Windows, "long" is 32 bits, so that we might need to convert int to long for consistency.
     */
    @GenerateInline(false)
    @GenerateUncached
    public abstract static class FromLongNode extends CExtToJavaNode {

        @Specialization
        static long doInt(int value) {
            return value & 0xFFFFFFFFL;
        }

        @Specialization
        static long doLong(long value) {
            return value;
        }

        @Fallback
        static Object doOther(Object value) {
            assert CApiTransitions.isBackendPointerObject(value);
            return value;
        }

        @NeverDefault
        public static FromLongNode create() {
            return ExternalFunctionNodesFactory.FromLongNodeGen.create();
        }

        public static FromLongNode getUncached() {
            return ExternalFunctionNodesFactory.FromLongNodeGen.getUncached();
        }
    }

    @GenerateInline(false)
    @GenerateUncached
    public abstract static class FromUInt32Node extends CExtToJavaNode {

        @Specialization
        static int doInt(int value) {
            return value;
        }

        @Specialization
        static int doLong(long value) {
            assert value < (1L << 32);
            return (int) value;
        }

        @NeverDefault
        public static FromUInt32Node create() {
            return ExternalFunctionNodesFactory.FromUInt32NodeGen.create();
        }

        public static FromUInt32Node getUncached() {
            return ExternalFunctionNodesFactory.FromUInt32NodeGen.getUncached();
        }
    }

    @GenerateInline(false)
    public abstract static class ToInt64Node extends CExtToNativeNode {

        @Specialization
        static long doInt(int value) {
            return value;
        }

        @Specialization
        static long doLong(long value) {
            return value;
        }

        @Fallback
        static Object doOther(Object value) {
            assert CApiTransitions.isBackendPointerObject(value);
            return value;
        }

        @NeverDefault
        public static ToInt64Node create() {
            return ExternalFunctionNodesFactory.ToInt64NodeGen.create();
        }
    }

    @GenerateInline(false)
    public abstract static class ToInt32Node extends CExtToNativeNode {

        @Specialization
        static int doInt(int value) {
            return value;
        }

        @NeverDefault
        public static ToInt32Node create() {
            return ExternalFunctionNodesFactory.ToInt32NodeGen.create();
        }
    }

    @GenerateInline(false)
    public static final class ToNativeBorrowedNode extends CExtToNativeNode {

        @Child private EnsurePythonObjectNode ensurePythonObjectNode = EnsurePythonObjectNode.create();
        @Child private PythonToNativeNode toNative = PythonToNativeNode.create();

        @Override
        public Object execute(Object object) {
            assert (object instanceof Double && Double.isNaN((double) object)) || !(object instanceof Number || object instanceof TruffleString);
            /*
             * In this case, it is not necessary to explicitly keep the promoted object alive
             * because this node is only used to hand out borrowed references which means that the
             * returned object must be owned by a container object (e.g. a list) and we already
             * promote the elements of such container objects at the time when the container object
             * is handed out to native. We still need to promote the object because it could be,
             * e.g., Java primitive 'true' which will be promoted to an immortal object.
             */
            PythonContext ctx = PythonContext.get(this);
            Object promoted = ensurePythonObjectNode.execute(ctx, object, false);
            assert promoted == object || PythonToNativeInternalNode.isImmortal(ctx, promoted);
            return toNative.executeLong(promoted);
        }
    }

    @GenerateUncached
    @GenerateInline(false)
    public abstract static class ToPythonStringNode extends CExtToJavaNode {
        @Specialization
        static Object doIt(long pointer,
                        @Bind Node inliningTarget,
                        @Cached CastToTruffleStringNode castToStringNode,
                        @Cached NativeToPythonNode nativeToPythonNode) {
            Object result = nativeToPythonNode.executeRaw(pointer);
            if (result == PNone.NO_VALUE) {
                return result;
            }
            return castToStringNode.castKnownString(inliningTarget, result);
        }

        @NeverDefault
        public static ToPythonStringNode create() {
            return ExternalFunctionNodesFactory.ToPythonStringNodeGen.create();
        }

        public static ToPythonStringNode getUncached() {
            return ExternalFunctionNodesFactory.ToPythonStringNodeGen.getUncached();
        }
    }

    /**
     * Enum of well-known function and slot signatures. The integer values must stay in sync with
     * the definition in {code capi.h}.
     */
    public enum PExternalFunctionWrapper implements NativeCExtSymbol {
        ALLOC(PyObjectTransfer, PyTypeObject, Py_ssize_t),
        GETATTR(PyObjectReturn, PyObject, CharPtrAsTruffleString),
        SETATTR(InitResult, PyObject, CharPtrAsTruffleString, PyObject),
        RICHCMP(PyObjectReturn, PyObject, PyObject, Int),
        SETITEM(InitResult, PyObject, Py_ssize_t, PyObject),

        // wrap_unaryfunc
        UNARYFUNC(PyObjectReturn, PyObject),

        // wrap_binaryfunc
        BINARYFUNC(PyObjectReturn, PyObject, PyObject),
        // wrap_binaryfunc_l
        BINARYFUNC_L(PyObjectReturn, PyObject, PyObject),
        // wrap_binaryfunc_r
        BINARYFUNC_R(PyObjectReturn, PyObject, PyObject),
        // wrap_ternaryfunc
        TERNARYFUNC(PyObjectReturn, PyObject, PyObject, PyObject),
        // wrap_ternaryfunc_r
        TERNARYFUNC_R(PyObjectReturn, PyObject, PyObject, PyObject),
        LT(PyObjectReturn, PyObject, PyObject, Int),
        LE(PyObjectReturn, PyObject, PyObject, Int),
        EQ(PyObjectReturn, PyObject, PyObject, Int),
        NE(PyObjectReturn, PyObject, PyObject, Int),
        GT(PyObjectReturn, PyObject, PyObject, Int),
        GE(PyObjectReturn, PyObject, PyObject, Int),
        ITERNEXT(IterResult, PyObject),
        INQUIRY(InquiryResult, PyObject),
        DELITEM(defaults(1), Int, PyObject, Py_ssize_t, PyObject),
        GETITEM(PyObjectReturn, PyObject, Py_ssize_t),
        GETTER(PyObjectReturn, PyObject, Pointer),
        SETTER(InitResult, PyObject, PyObject, Pointer),
        // wrap_initproc
        INITPROC(InitResult, PyObject, PyObject, PyObject),
        // wrap_hashfunc
        HASHFUNC(PrimitiveResult64, PyObject),
        // wrap_call
        CALL(PyObjectReturn, PyObject, PyObject, PyObject),

        // wrap_setattr
        SETATTRO(InitResult, PyObject, PyObject, PyObject),
        DESCR_GET(defaults(1), PyObjectTransfer, PyObject, PyObject, PyObject),

        // wrap_descrsetfunc
        DESCR_SET(InitResult, PyObject, PyObject, PyObject),

        // wrap_lenfunc
        LENFUNC(PrimitiveResult64, PyObject),

        // wrap_objobjproc
        OBJOBJPROC(InquiryResult, PyObject, PyObject),

        // wrap_objobjargproc
        OBJOBJARGPROC(PrimitiveResult32, PyObject, PyObject, PyObject),
        NEW(PyObjectReturn, PyObject, PyObject, PyObject),
        MP_DELITEM(PrimitiveResult32, PyObject, PyObject, PyObject),
        TP_STR(PyObjectReturn, PyObject),
        TP_REPR(PyObjectReturn, PyObject),
        DESCR_DELETE(InitResult, PyObject, PyObject, PyObject), // the last one is
                                                                // always NULL
        DELATTRO(InitResult, PyObject, PyObject, PyObject), // the last one is always
                                                            // NULL
        SSIZE_ARG(PyObjectReturn, PyObject, Py_ssize_t),
        VISITPROC(Int, PyObject, Pointer),
        TRAVERSEPROC(Int, PyObject, Pointer, Pointer);

        private static int defaults(int x) {
            return x;
        }

        @CompilationFinal(dimensions = 1) private static final PExternalFunctionWrapper[] VALUES = values();
        @CompilationFinal(dimensions = 1) private static final PExternalFunctionWrapper[] BY_ID = new PExternalFunctionWrapper[51];

        public final NfiDowncallSignature signature;
        public final ArgDescriptor returnValue;
        public final ArgDescriptor[] arguments;
        public final int numDefaults;

        PExternalFunctionWrapper(int numDefaults, ArgDescriptor returnValue, ArgDescriptor... arguments) {
            this.returnValue = returnValue;
            this.arguments = arguments;

            NfiType[] nfiTypes = new NfiType[arguments.length];
            for (int i = 0; i < arguments.length; i++) {
                nfiTypes[i] = arguments[i].getNFI2Type();
            }
            this.signature = Nfi.createDowncallSignature(returnValue.getNFI2Type(), nfiTypes);
            this.numDefaults = numDefaults;
        }

        PExternalFunctionWrapper(ArgDescriptor returnValue, ArgDescriptor... arguments) {
            this(0, returnValue, arguments);
        }

        @TruffleBoundary
        static RootCallTarget getOrCreateCallTarget(PExternalFunctionWrapper sig, PythonLanguage language, TruffleString name) {
            Class<? extends PRootNode> nodeKlass;
            Function<PythonLanguage, RootNode> rootNodeFunction;
            switch (sig) {
                case ALLOC:
                case SSIZE_ARG:
                    nodeKlass = AllocFuncRootNode.class;
                    rootNodeFunction = (l -> new AllocFuncRootNode(l, name, sig));
                    break;
                case TP_REPR:
                case TP_STR:
                case UNARYFUNC:
                    // TP_ITER
                    // AM_AWAIT
                    // AM_AITER
                    // AM_ANEXT
                    // NB_NEGATIVE
                    // NB_POSITIVE
                    // NB_ABSOLUTE
                    // NB_INVERT
                    // NB_INT
                    // NB_FLOAT
                    // NB_INDEX
                    nodeKlass = MethUnaryFunc.class;
                    rootNodeFunction = l -> MethUnaryFunc.create(language, name, sig);
                    break;
                case DESCR_SET:
                    nodeKlass = MethDescrSetRoot.class;
                    rootNodeFunction = l -> new MethDescrSetRoot(language, name, sig);
                    break;
                case LENFUNC:
                case HASHFUNC:
                    /*
                     * wrap_lenfunc, wrap_hashfunc; they are equivalent and only differ in the
                     * return type (Py_ssize_t vs. Py_hash_t; both map to Java long)
                     */
                    nodeKlass = MethLenfuncRoot.class;
                    rootNodeFunction = l -> new MethLenfuncRoot(language, name, sig);
                    break;
                case OBJOBJPROC:
                    nodeKlass = MethObjObjProcRoot.class;
                    rootNodeFunction = l -> new MethObjObjProcRoot(language, name, sig);
                    break;
                case OBJOBJARGPROC:
                    nodeKlass = MethObjObjArgProcRoot.class;
                    rootNodeFunction = l -> new MethObjObjArgProcRoot(language, name, sig);
                    break;
                case BINARYFUNC:
                case BINARYFUNC_L:
                    // wrap_binaryfunc and wrap_binaryfunc_l are exactly the same
                    nodeKlass = MethUnaryFunc.class;
                    rootNodeFunction = l -> new MethBinaryRoot(language, name, sig);
                    break;
                case CALL:
                case INITPROC:
                    nodeKlass = MethInitRoot.class;
                    rootNodeFunction = l -> new MethInitRoot(l, name, sig);
                    break;
                case NEW:
                    nodeKlass = MethNewRoot.class;
                    rootNodeFunction = l -> new MethNewRoot(l, name, sig);
                    break;
                case INQUIRY:
                    nodeKlass = MethInquiryRoot.class;
                    rootNodeFunction = (l -> new MethInquiryRoot(l, name, sig));
                    break;
                case GETATTR:
                    nodeKlass = GetAttrFuncRootNode.class;
                    rootNodeFunction = (l -> new GetAttrFuncRootNode(l, name, sig));
                    break;
                case SETATTR:
                    nodeKlass = SetAttrFuncRootNode.class;
                    rootNodeFunction = (l -> new SetAttrFuncRootNode(l, name, sig));
                    break;
                case SETATTRO:
                    nodeKlass = SetAttrOFuncRootNode.class;
                    rootNodeFunction = (l -> new SetAttrOFuncRootNode(l, name, sig));
                    break;
                case DESCR_GET:
                    nodeKlass = DescrGetRootNode.class;
                    rootNodeFunction = (l -> new DescrGetRootNode(l, name, sig));
                    break;
                case DESCR_DELETE:
                    nodeKlass = DescrGetRootNode.class;
                    rootNodeFunction = (l -> new DescrDeleteRootNode(l, name, sig));
                    break;
                case DELATTRO:
                    nodeKlass = DelAttrRootNode.class;
                    rootNodeFunction = (l -> new DelAttrRootNode(l, name, sig));
                    break;
                case RICHCMP:
                    nodeKlass = RichCmpFuncRootNode.class;
                    rootNodeFunction = (l -> new RichCmpFuncRootNode(l, name, sig));
                    break;
                case SETITEM:
                case DELITEM:
                    nodeKlass = SetItemRootNode.class;
                    rootNodeFunction = (l -> new SetItemRootNode(l, name, sig));
                    break;
                case GETITEM:
                    nodeKlass = GetItemRootNode.class;
                    rootNodeFunction = (l -> new GetItemRootNode(l, name, sig));
                    break;
                case BINARYFUNC_R:
                    nodeKlass = MethBinaryFuncRRoot.class;
                    rootNodeFunction = (l -> new MethBinaryFuncRRoot(l, name, sig));
                    break;
                case TERNARYFUNC:
                    nodeKlass = MethTernaryFuncRoot.class;
                    rootNodeFunction = (l -> new MethTernaryFuncRoot(l, name, sig));
                    break;
                case TERNARYFUNC_R:
                    nodeKlass = MethTernaryFuncRRoot.class;
                    rootNodeFunction = (l -> new MethTernaryFuncRRoot(l, name, sig));
                    break;
                case GT:
                case GE:
                case LE:
                case LT:
                case EQ:
                case NE:
                    nodeKlass = MethRichcmpOpRootNode.class;
                    rootNodeFunction = (l -> new MethRichcmpOpRootNode(l, name, sig, getCompareOpCode(sig)));
                    break;
                case ITERNEXT:
                    nodeKlass = IterNextFuncRootNode.class;
                    rootNodeFunction = (l -> new IterNextFuncRootNode(l, name, sig));
                    break;
                case GETTER:
                    nodeKlass = GetterRoot.class;
                    rootNodeFunction = l -> new GetterRoot(l, name, sig);
                    break;
                case SETTER:
                    nodeKlass = SetterRoot.class;
                    rootNodeFunction = l -> new SetterRoot(l, name, sig);
                    break;
                case MP_DELITEM:
                    nodeKlass = MpDelItemRootNode.class;
                    rootNodeFunction = (l -> new MpDelItemRootNode(l, name, sig));
                    break;
                default:
                    throw CompilerDirectives.shouldNotReachHere();
            }
            return language.createCachedExternalFunWrapperCallTarget(rootNodeFunction, nodeKlass, sig, name, true, false);
        }

        /**
         * Similar to Python API function {@code PyDescr_NewWrapper}, creates a built-in function of
         * type {@link PythonBuiltinClassType#WrapperDescriptor} (usually for a slot). This built-in
         * function also does appropriate argument and result conversion and calls the provided
         * native function.
         *
         * @param name The name of the method.
         * @param callable A reference denoting executable code. Currently, there are two
         *            representations for that: a native function pointer or a
         *            {@link RootCallTarget}
         * @param enclosingType The type the function belongs to (needed for checking of
         *            {@code self}).
         * @param sig The wrapper/signature ID as defined in {@link PExternalFunctionWrapper}.
         * @param language The Python language object.
         * @return A {@link PBuiltinFunction} implementing the semantics of the specified slot
         *         wrapper.
         */
        @TruffleBoundary
        public static PythonBuiltinObject createDescrWrapperFunction(TruffleString name, NfiBoundFunction callable, Object enclosingType, PExternalFunctionWrapper sig, PythonLanguage language) {
            LOGGER.finer(() -> PythonUtils.formatJString("ExternalFunctions.createDescrWrapperFunction(%s, %s)", name, callable));
            RootCallTarget callTarget = getOrCreateCallTarget(sig, language, name);

            // ensure that 'callable' is executable via InteropLibrary
            PKeyword[] kwDefaults = ExternalFunctionNodes.createKwDefaults(callable);
            TpSlot slot = TpSlotNative.createCExtSlot(callable);

            // generate default values for positional args (if necessary)
            Object[] defaults = PBuiltinFunction.generateDefaults(sig.numDefaults);

            Object type = enclosingType == PNone.NO_VALUE ? null : enclosingType;
            if (sig == NEW) {
                return PFactory.createNewWrapper(language, type, defaults, kwDefaults, callTarget, slot);
            }
            return PFactory.createWrapperDescriptor(language, name, type, defaults, kwDefaults, 0, callTarget, slot, sig);
        }

        private static int getCompareOpCode(PExternalFunctionWrapper sig) {
            // op codes for binary comparisons (defined in 'object.h')
            return switch (sig) {
                case LT -> RichCmpOp.Py_LT.asNative();
                case LE -> RichCmpOp.Py_LE.asNative();
                case EQ -> RichCmpOp.Py_EQ.asNative();
                case NE -> RichCmpOp.Py_NE.asNative();
                case GT -> RichCmpOp.Py_GT.asNative();
                case GE -> RichCmpOp.Py_GE.asNative();
                default -> throw CompilerDirectives.shouldNotReachHere(sig.getName());
            };
        }

        CheckFunctionResultNode createCheckFunctionResultNode() {
            return returnValue.createCheckResultNode();
        }

        CheckFunctionResultNode getUncachedCheckFunctionResultNode() {
            return returnValue.getUncachedCheckResultNode();
        }

        CExtToJavaNode createConvertRetNode() {
            return returnValue.createNativeToPythonNode();
        }

        CExtToJavaNode getUncachedConvertRetNode() {
            return returnValue.getUncachedNativeToPythonNode();
        }

        CExtToNativeNode[] createConvertArgNodes() {
            return createConvertArgNodes(arguments);
        }

        public static CExtToNativeNode[] createConvertArgNodes(ArgDescriptor[] descriptors) {
            CExtToNativeNode[] result = new CExtToNativeNode[descriptors.length];
            for (int i = 0; i < descriptors.length; i++) {
                result[i] = descriptors[i].createPythonToNativeNode();
            }
            return result;
        }

        @Override
        public String getName() {
            return name();
        }

        @Override
        public TruffleString getTsName() {
            throw CompilerDirectives.shouldNotReachHere();
        }

        @Override
        public NfiDowncallSignature getSignature() {
            return signature;
        }
    }

    private static Signature createSignature(boolean takesVarKeywordArgs, int varArgIndex, TruffleString[] parameters, boolean checkEnclosingType, boolean hidden) {
        return new Signature(-1, takesVarKeywordArgs, varArgIndex, parameters, KEYWORDS_HIDDEN_CALLABLE, checkEnclosingType, T_EMPTY_STRING, hidden);
    }

    private static Signature createSignatureWithClosure(boolean takesVarKeywordArgs, int varArgIndex, TruffleString[] parameters, boolean checkEnclosingType, boolean hidden) {
        return new Signature(-1, takesVarKeywordArgs, varArgIndex, parameters, KEYWORDS_HIDDEN_CALLABLE_AND_CLOSURE, checkEnclosingType, T_EMPTY_STRING, hidden);
    }

    @GenerateInline(false)
    public abstract static class ExternalFunctionWrapperInvokeNode extends PNodeWithContext {

        public abstract Object execute(VirtualFrame frame, CApiTiming timing, PythonThreadState threadState, NfiBoundFunction callable, Object[] cArguments);

        @Specialization
        static Object invokeCached(VirtualFrame frame, CApiTiming timing, PythonThreadState threadState, NfiBoundFunction callable, Object[] cArguments,
                        @Cached("createFor($node)") BoundaryCallData boundaryCallData) {

            // If any code requested the caught exception (i.e. used 'sys.exc_info()'), we store
            // it to the context since we cannot propagate it through the native frames.
            Object state = BoundaryCallContext.enter(frame, threadState, boundaryCallData);

            CApiTiming.enter();
            try {
                return callable.invoke(cArguments);
            } catch (Throwable exception) {
                /*
                 * Always re-acquire the GIL here. This is necessary because it could happen that C
                 * extensions are releasing the GIL and if then an LLVM exception occurs, C code
                 * wouldn't re-acquire it (unexpectedly).
                 */
                CompilerDirectives.transferToInterpreterAndInvalidate();
                GilNode.uncachedAcquire();
                throw exception;
            } finally {
                CApiTiming.exit(timing);
                /*
                 * Special case after calling a C function: transfer caught exception back to frame
                 * to simulate the global state semantics.
                 */
                if (frame != null && threadState.getCaughtException() != null) {
                    PArguments.setException(frame, threadState.getCaughtException());
                }
                BoundaryCallContext.exit(frame, threadState, state);
            }
        }
    }

    public abstract static class WrapperBaseRoot extends PRootNode {
        @Child private CalleeContext calleeContext = CalleeContext.create();
        @Child private CheckFunctionResultNode checkResultNode;
        @Child private ExternalFunctionWrapperInvokeNode externalInvokeNode;
        @Child private ReadIndexedArgumentNode readSelfNode;
        @Child private ReadIndexedArgumentNode readCallableNode;
        @Child private EnsurePythonObjectNode ensurePythonObjectNode;
        @Child private PythonObjectArrayCreateNode pythonObjectArrayCreateNode;
        @Child private PythonObjectArrayFreeNode pythonObjectArrayFreeNode;
        @Child private GetThreadStateNode getThreadStateNode = GetThreadStateNodeGen.create();
        @Children protected final CExtToNativeNode[] convertArgs;
        @Child CExtToJavaNode convertReturnValue;

        private final TruffleString name;
        private final CApiTiming timing;

        WrapperBaseRoot(PythonLanguage language, TruffleString name, boolean isStatic, CExtToJavaNode convertReturnValue, CheckFunctionResultNode checkFunctionResultNode,
                        CExtToNativeNode[] convertArgs) {
            super(language);
            CompilerAsserts.neverPartOfCompilation();
            this.name = name;
            this.timing = CApiTiming.create(true, name);
            this.externalInvokeNode = ExternalFunctionWrapperInvokeNodeGen.create();
            this.checkResultNode = checkFunctionResultNode != null ? checkFunctionResultNode : PyObjectCheckFunctionResultNodeGen.create();
            this.convertArgs = convertArgs;
            this.convertReturnValue = convertReturnValue;
            if (!isStatic) {
                readSelfNode = ReadIndexedArgumentNode.create(0);
            }
        }

        @ExplodeLoop
        protected Object[] cArgumentsToNative(Object[] arguments) {
            Object[] nativeArgs = new Object[arguments.length];
            for (int i = 0; i < convertArgs.length; i++) {
                if (convertArgs[i] != null) {
                    nativeArgs[i] = convertArgs[i].execute(arguments[i]);
                } else {
                    nativeArgs[i] = arguments[i];
                }
            }
            return nativeArgs;
        }

        @Override
        public final Object execute(VirtualFrame frame) {
            calleeContext.enter(frame, this);
            try {
                Object callable = ensureReadCallableNode().execute(frame);
                if (!(callable instanceof NfiBoundFunction boundFunction)) {
                    throw CompilerDirectives.shouldNotReachHere();
                }
                Object[] preparedCArguments = prepareCArguments(frame);
                Object[] nativeArguments = cArgumentsToNative(preparedCArguments);
                try {
                    PythonContext ctx = PythonContext.get(this);
                    PythonThreadState threadState = getThreadStateNode.executeCached(ctx);
                    Object result = externalInvokeNode.execute(frame, timing, threadState, boundFunction, nativeArguments);
                    if (convertReturnValue != null) {
                        result = convertReturnValue.execute(result);
                    }
                    /*
                     * Note: Result checking needs to be done on the converted result. This is
                     * because in case of a non-NULL object return value with an exception, the
                     * ownership must first been taken to avoid leaks.
                     */
                    result = checkResultNode.execute(threadState, name, result);
                    assert PForeignToPTypeNode.getUncached().executeConvert(result) == result;

                    return result;
                } finally {
                    postprocessCArguments(frame, preparedCArguments, nativeArguments);
                    Reference.reachabilityFence(preparedCArguments);
                }
            } finally {
                calleeContext.exit(frame, this);
            }
        }

        /**
         * Prepare the arguments for calling the C function. The arguments will then be converted to
         * LLVM arguments using the {@link ArgDescriptor#createPythonToNativeNode()}. This will
         * modify the returned array.
         */
        protected abstract Object[] prepareCArguments(VirtualFrame frame);

        @SuppressWarnings("unused")
        protected void postprocessCArguments(VirtualFrame frame, Object[] cArguments, Object[] nativeArguments) {
            // default: do nothing
        }

        private ReadIndexedArgumentNode ensureReadCallableNode() {
            if (readCallableNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                // we insert a hidden argument at the end of the positional arguments
                int hiddenArg = getSignature().getParameterIds().length;
                readCallableNode = insert(ReadIndexedArgumentNode.create(hiddenArg));
            }
            return readCallableNode;
        }

        protected final Object ensurePythonObject(Object object) {
            if (ensurePythonObjectNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                ensurePythonObjectNode = insert(EnsurePythonObjectNode.create());
            }
            return ensurePythonObjectNode.execute(PythonContext.get(this), object, false);
        }

        protected final PythonObjectArrayCreateNode ensureArrayCreateNode() {
            if (pythonObjectArrayCreateNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                pythonObjectArrayCreateNode = insert(PythonObjectArrayCreateNode.create());
            }
            return pythonObjectArrayCreateNode;
        }

        protected final PythonObjectArrayFreeNode ensureArrayFreeNode() {
            if (pythonObjectArrayFreeNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                pythonObjectArrayFreeNode = insert(PythonObjectArrayFreeNode.create());
            }
            return pythonObjectArrayFreeNode;
        }

        @Override
        public boolean isCloningAllowed() {
            return true;
        }

        @Override
        public String getName() {
            return name.toJavaStringUncached();
        }

        @Override
        public String toString() {
            return "<METH root " + name + ">";
        }

        @Override
        public boolean isPythonInternal() {
            return true;
        }

        @Override
        public boolean isInternal() {
            return true;
        }

        @Override
        public boolean setsUpCalleeContext() {
            return true;
        }

        protected final Object readSelf(VirtualFrame frame) {
            if (readSelfNode != null) {
                return readSelfNode.execute(frame);
            }
            return PNone.NO_VALUE;
        }
    }

    /**
     * Base class for all native {@link PythonBuiltinClassType#PBuiltinFunction} (CPython type
     * {@code PyMethodDescr_Type}) functions.
     */
    public abstract static class MethodDescriptorRoot extends WrapperBaseRoot {

        MethodDescriptorRoot(PythonLanguage language, TruffleString name, boolean isStatic, MethodDescriptorWrapper wrapper) {
            super(language, name, isStatic, NativeToPythonReturnNode.create(), null, PExternalFunctionWrapper.createConvertArgNodes(wrapper.arguments));
            assert wrapper.returnValue == PyObjectReturn;
        }
    }

    /**
     * Base class for all native {@link PythonBuiltinClassType#WrapperDescriptor} (CPython type
     * {@code PyWrapperDescr_Type}) functions. Those are used for the slot wrapper functions of C
     * function type {@code wrapperfunc}.
     */
    public abstract static class WrapperDescriptorRoot extends WrapperBaseRoot {
        WrapperDescriptorRoot(PythonLanguage language, TruffleString name, boolean isStatic, PExternalFunctionWrapper wrapper) {
            super(language, name, isStatic, wrapper.createConvertRetNode(), wrapper.createCheckFunctionResultNode(), wrapper.createConvertArgNodes());
        }
    }

    public static class MethKeywordsRoot extends MethodDescriptorRoot {
        private static final Signature SIGNATURE = createSignature(true, 1, tsArray("self"), true, true);
        @Child protected ReadVarArgsNode readVarargsNode;
        @Child protected ReadVarKeywordsNode readKwargsNode;
        @Child protected CreateArgsTupleNode createArgsTupleNode;
        @Child protected ReleaseNativeSequenceStorageNode freeNode;

        protected boolean seenNativeArgsTupleStorage;

        public MethKeywordsRoot(PythonLanguage language, TruffleString name, boolean isStatic, MethodDescriptorWrapper provider) {
            super(language, name, isStatic, provider);
            this.readVarargsNode = ReadVarArgsNode.create(SIGNATURE.varArgsPArgumentsIndex());
            this.readKwargsNode = ReadVarKeywordsNode.create(SIGNATURE.varKeywordsPArgumentsIndex());
            this.createArgsTupleNode = CreateArgsTupleNodeGen.create();
            this.freeNode = ReleaseNativeSequenceStorageNodeGen.create();
        }

        @Override
        protected Object[] prepareCArguments(VirtualFrame frame) {
            Object self = readSelf(frame);
            assert EnsurePythonObjectNode.doesNotNeedPromotion(self);

            Object[] args = readVarargsNode.execute(frame);
            PTuple argsTuple = createArgsTupleNode.execute(PythonContext.get(this), args, seenNativeArgsTupleStorage);
            assert EnsurePythonObjectNode.doesNotNeedPromotion(argsTuple);

            PKeyword[] kwargs = readKwargsNode.execute(frame);
            PythonLanguage language = getLanguage(PythonLanguage.class);
            Object kwargsDict = kwargs.length > 0 ? PFactory.createDict(language, kwargs) : PNone.NO_VALUE;
            assert EnsurePythonObjectNode.doesNotNeedPromotion(kwargsDict);

            return new Object[]{self, argsTuple, kwargsDict};
        }

        @Override
        protected void postprocessCArguments(VirtualFrame frame, Object[] cArguments, Object[] nativeArguments) {
            boolean freed = MethVarargsRoot.releaseArgsTuple(cArguments[1], freeNode, seenNativeArgsTupleStorage);
            if (!seenNativeArgsTupleStorage && freed) {
                seenNativeArgsTupleStorage = true;
            }
        }

        @Override
        public Signature getSignature() {
            return SIGNATURE;
        }
    }

    public static final class MethVarargsRoot extends MethodDescriptorRoot {
        private static final Signature SIGNATURE = createSignature(false, 1, tsArray("self"), true, true);
        @Child private ReadVarArgsNode readVarargsNode;
        @Child private CreateArgsTupleNode createArgsTupleNode;
        @Child private ReleaseNativeSequenceStorageNode freeNode;

        private boolean seenNativeArgsTupleStorage;

        public MethVarargsRoot(PythonLanguage language, TruffleString name, boolean isStatic, MethodDescriptorWrapper provider) {
            super(language, name, isStatic, provider);
            this.readVarargsNode = ReadVarArgsNode.create(SIGNATURE.varArgsPArgumentsIndex());
            this.createArgsTupleNode = CreateArgsTupleNodeGen.create();
            this.freeNode = ReleaseNativeSequenceStorageNodeGen.create();
        }

        @Override
        protected Object[] prepareCArguments(VirtualFrame frame) {
            Object self = readSelf(frame);
            assert EnsurePythonObjectNode.doesNotNeedPromotion(self);
            Object[] args = readVarargsNode.execute(frame);
            PTuple argsTuple = createArgsTupleNode.execute(PythonContext.get(this), args, seenNativeArgsTupleStorage);
            assert EnsurePythonObjectNode.doesNotNeedPromotion(argsTuple);
            return new Object[]{self, argsTuple};
        }

        @Override
        protected void postprocessCArguments(VirtualFrame frame, Object[] cArguments, Object[] nativeArguments) {
            boolean freed = releaseArgsTuple(cArguments[1], freeNode, seenNativeArgsTupleStorage);
            if (!seenNativeArgsTupleStorage && freed) {
                seenNativeArgsTupleStorage = true;
            }
        }

        static boolean releaseArgsTuple(Object argsTupleObject, ReleaseNativeSequenceStorageNode freeNode, boolean eagerNativeStorage) {
            if (!PythonContext.get(freeNode).isNativeAccessAllowed()) {
                return false;
            }
            try {
                assert argsTupleObject instanceof PTuple;
                PTuple argsTuple = (PTuple) argsTupleObject;
                SequenceStorage s = argsTuple.getSequenceStorage();
                /*
                 * This assumes that the common case is that the args tuple is still owned by the
                 * runtime. However, it could be that the C extension does 'Py_INCREF(argsTuple)'
                 * and in this case, we must not free the memory. Further, since we assumed that we
                 * may free the memory after the call returned, we also need to create a
                 * NativeSequenceStorageReference such that the NativeSequenceStorage will not leak.
                 */
                if (s instanceof NativeSequenceStorage nativeSequenceStorage) {
                    /*
                     * TODO we would like to release the memory already, but we currently can't tell
                     * if the args tuple escaped back to managed. So we always create the native
                     * storage with an ownership reference and the following condition is always
                     * true.
                     */
                    if (nativeSequenceStorage.hasReference()) {
                        /*
                         * Not allocated by this root. Note that this can happen even when
                         * seenNativeArgsTupleStorage is true, because it could have been set by a
                         * recursive invocation of this root.
                         */
                        return true;
                    }
                    assert eagerNativeStorage;
                    if (argsTuple.getRefCount() == MANAGED_REFCNT) {
                        // in this case, the runtime still exclusively owns the memory
                        freeNode.execute(nativeSequenceStorage);
                    } else {
                        // the C ext also created a reference; no exclusive ownership
                        CApiTransitions.registerNativeSequenceStorage(nativeSequenceStorage);
                    }
                    return true;
                }
                return false;
            } catch (ClassCastException e) {
                // cut exception edge
                throw CompilerDirectives.shouldNotReachHere(e);
            }
        }

        @Override
        public Signature getSignature() {
            return SIGNATURE;
        }
    }

    static final class MethUnaryFunc extends WrapperDescriptorRoot {
        private static final Signature SIGNATURE = createSignature(false, -1, tsArray("self"), true, false);

        private MethUnaryFunc(PythonLanguage lang, TruffleString name, PExternalFunctionWrapper provider) {
            super(lang, name, false, provider);
        }

        @Override
        protected Object[] prepareCArguments(VirtualFrame frame) {
            Object self = readSelf(frame);
            assert EnsurePythonObjectNode.doesNotNeedPromotion(self);
            return new Object[]{self};
        }

        @Override
        public Signature getSignature() {
            return SIGNATURE;
        }

        @TruffleBoundary
        public static MethUnaryFunc create(PythonLanguage lang, TruffleString name, PExternalFunctionWrapper provider) {
            return new MethUnaryFunc(lang, name, provider);
        }
    }

    abstract static class MethNewOrInitRoot extends WrapperDescriptorRoot {
        private static final Signature SIGNATURE = MethKeywordsRoot.SIGNATURE;
        @Child ReadVarArgsNode readVarargsNode;
        @Child ReadVarKeywordsNode readKwargsNode;
        @Child CreateArgsTupleNode createArgsTupleNode;
        @Child ReleaseNativeSequenceStorageNode freeNode;

        @CompilationFinal boolean seenNativeArgsTupleStorage;

        public MethNewOrInitRoot(PythonLanguage language, TruffleString name, boolean isStatic, PExternalFunctionWrapper provider) {
            super(language, name, isStatic, provider);
            this.readVarargsNode = ReadVarArgsNode.create(SIGNATURE.varArgsPArgumentsIndex());
            this.readKwargsNode = ReadVarKeywordsNode.create(SIGNATURE.varKeywordsPArgumentsIndex());
            this.createArgsTupleNode = CreateArgsTupleNodeGen.create();
            this.freeNode = ReleaseNativeSequenceStorageNodeGen.create();
        }

        @Override
        protected void postprocessCArguments(VirtualFrame frame, Object[] cArguments, Object[] nativeArguments) {
            boolean freed = MethVarargsRoot.releaseArgsTuple(cArguments[1], freeNode, seenNativeArgsTupleStorage);
            if (!seenNativeArgsTupleStorage && freed) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                seenNativeArgsTupleStorage = true;
            }
        }

        @Override
        public Signature getSignature() {
            return SIGNATURE;
        }
    }

    static final class MethNewRoot extends MethNewOrInitRoot {

        public MethNewRoot(PythonLanguage language, TruffleString name, PExternalFunctionWrapper provider) {
            super(language, name, false, provider);
        }

        @Override
        protected Object[] prepareCArguments(VirtualFrame frame) {
            Object methodSelf = readSelf(frame);
            Object[] args = readVarargsNode.execute(frame);

            // TODO checks
            Object self = args[0];
            assert EnsurePythonObjectNode.doesNotNeedPromotion(self);

            args = PythonUtils.arrayCopyOfRange(args, 1, args.length);
            PTuple argsTuple = createArgsTupleNode.execute(PythonContext.get(this), args, seenNativeArgsTupleStorage);
            assert EnsurePythonObjectNode.doesNotNeedPromotion(argsTuple);

            PKeyword[] kwargs = readKwargsNode.execute(frame);
            PythonLanguage language = getLanguage(PythonLanguage.class);
            Object kwargsDict = kwargs.length > 0 ? PFactory.createDict(language, kwargs) : PNone.NO_VALUE;
            assert EnsurePythonObjectNode.doesNotNeedPromotion(kwargsDict);

            return new Object[]{self, argsTuple, kwargsDict};
        }
    }

    static final class MethInitRoot extends MethNewOrInitRoot {

        public MethInitRoot(PythonLanguage language, TruffleString name, PExternalFunctionWrapper provider) {
            super(language, name, false, provider);
        }

        @Override
        protected Object[] prepareCArguments(VirtualFrame frame) {
            Object self = readSelf(frame);
            assert EnsurePythonObjectNode.doesNotNeedPromotion(self);

            Object[] args = readVarargsNode.execute(frame);
            PTuple argsTuple = createArgsTupleNode.execute(PythonContext.get(this), args, seenNativeArgsTupleStorage);
            assert EnsurePythonObjectNode.doesNotNeedPromotion(argsTuple);

            PKeyword[] kwargs = readKwargsNode.execute(frame);
            PythonLanguage language = getLanguage(PythonLanguage.class);
            Object kwargsDict = kwargs.length > 0 ? PFactory.createDict(language, kwargs) : PNone.NO_VALUE;
            assert EnsurePythonObjectNode.doesNotNeedPromotion(kwargsDict);

            return new Object[]{self, argsTuple, kwargsDict};
        }

    }

    public static final class MethInquiryRoot extends WrapperDescriptorRoot {
        private static final Signature SIGNATURE = createSignature(false, -1, tsArray("self"), true, false);

        public MethInquiryRoot(PythonLanguage language, TruffleString name, PExternalFunctionWrapper provider) {
            super(language, name, false, provider);
        }

        @Override
        protected Object[] prepareCArguments(VirtualFrame frame) {
            Object self = readSelf(frame);
            assert EnsurePythonObjectNode.doesNotNeedPromotion(self);
            return new Object[]{self};
        }

        @Override
        public Signature getSignature() {
            return SIGNATURE;
        }
    }

    public static final class MethNoargsRoot extends MethodDescriptorRoot {
        private static final Signature SIGNATURE = createSignature(false, -1, tsArray("self"), true, true);

        public MethNoargsRoot(PythonLanguage language, TruffleString name, boolean isStatic, MethodDescriptorWrapper provider) {
            super(language, name, isStatic, provider);
        }

        @Override
        protected Object[] prepareCArguments(VirtualFrame frame) {
            Object self = readSelf(frame);
            assert EnsurePythonObjectNode.doesNotNeedPromotion(self);
            return new Object[]{self, PNone.NO_VALUE};
        }

        @Override
        public Signature getSignature() {
            return SIGNATURE;
        }
    }

    public static final class MethORoot extends MethodDescriptorRoot {
        private static final Signature SIGNATURE = createSignature(false, -1, tsArray("self", "arg"), true, true);
        @Child private ReadIndexedArgumentNode readArgNode;

        public MethORoot(PythonLanguage language, TruffleString name, boolean isStatic, MethodDescriptorWrapper provider) {
            super(language, name, isStatic, provider);
            this.readArgNode = ReadIndexedArgumentNode.create(1);
        }

        @Override
        protected Object[] prepareCArguments(VirtualFrame frame) {
            Object self = readSelf(frame);
            assert EnsurePythonObjectNode.doesNotNeedPromotion(self);
            Object arg = ensurePythonObject(readArgNode.execute(frame));
            return new Object[]{self, arg};
        }

        @Override
        public Signature getSignature() {
            return SIGNATURE;
        }
    }

    public static final class MethFastcallWithKeywordsRoot extends MethodDescriptorRoot {
        private static final Signature SIGNATURE = createSignature(true, 1, tsArray("self"), true, true);
        @Child private ReadVarArgsNode readVarargsNode;
        @Child private ReadVarKeywordsNode readKwargsNode;

        public MethFastcallWithKeywordsRoot(PythonLanguage language, TruffleString name, boolean isStatic, MethodDescriptorWrapper provider) {
            super(language, name, isStatic, provider);
            this.readVarargsNode = ReadVarArgsNode.create(SIGNATURE.varArgsPArgumentsIndex());
            this.readKwargsNode = ReadVarKeywordsNode.create(SIGNATURE.varKeywordsPArgumentsIndex());
        }

        @Override
        protected Object[] prepareCArguments(VirtualFrame frame) {
            Object self = readSelf(frame);
            assert EnsurePythonObjectNode.doesNotNeedPromotion(self);
            Object[] args = readVarargsNode.execute(frame);
            PKeyword[] kwargs = readKwargsNode.execute(frame);
            Object[] fastcallArgs = new Object[args.length + kwargs.length];
            Object kwnamesTuple = PNone.NO_VALUE;
            for (int i = 0; i < args.length; i++) {
                fastcallArgs[i] = ensurePythonObject(args[i]);
            }
            // Note: PyO3 doesn't like it when we put an empty tuple there if there are no args
            if (kwargs.length > 0) {
                Object[] fastcallKwnames = new Object[kwargs.length];
                for (int i = 0; i < kwargs.length; i++) {
                    fastcallKwnames[i] = kwargs[i].getName();
                    fastcallArgs[args.length + i] = ensurePythonObject(kwargs[i].getValue());
                }
                kwnamesTuple = PFactory.createTuple(PythonLanguage.get(this), fastcallKwnames);
            }
            return new Object[]{self, fastcallArgs, (long) args.length, kwnamesTuple};
        }

        @Override
        protected Object[] cArgumentsToNative(Object[] arguments) {
            Object[] objects = super.cArgumentsToNative(arguments);
            assert arguments[1] instanceof Object[];
            assert arguments[1] == objects[1];
            assert arguments[2] instanceof Long;
            objects[1] = ensureArrayCreateNode().execute((Object[]) arguments[1]);
            return objects;
        }

        @Override
        protected void postprocessCArguments(VirtualFrame frame, Object[] cArguments, Object[] nativeArguments) {
            assert cArguments[1] instanceof Object[];
            assert cArguments[2] instanceof Long;
            assert cArguments[3] == PNone.NO_VALUE || cArguments[3] instanceof PTuple;
            assert ((Object[]) cArguments[1]).length == (Long) cArguments[2] + (cArguments[3] != PNone.NO_VALUE ? ((PTuple) cArguments[3]).getSequenceStorage().length() : 0);
            ensureArrayFreeNode().execute((long) nativeArguments[1]);
        }

        @Override
        public Signature getSignature() {
            return SIGNATURE;
        }
    }

    public static final class MethMethodRoot extends MethodDescriptorRoot {
        private static final Signature SIGNATURE = createSignature(true, 1, tsArray("self", "cls"), true, true);
        @Child private ReadIndexedArgumentNode readClsNode;
        @Child private ReadVarArgsNode readVarargsNode;
        @Child private ReadVarKeywordsNode readKwargsNode;

        public MethMethodRoot(PythonLanguage language, TruffleString name, boolean isStatic, MethodDescriptorWrapper provider) {
            super(language, name, isStatic, provider);
            this.readClsNode = ReadIndexedArgumentNode.create(1);
            this.readVarargsNode = ReadVarArgsNode.create(SIGNATURE.varArgsPArgumentsIndex());
            this.readKwargsNode = ReadVarKeywordsNode.create(SIGNATURE.varKeywordsPArgumentsIndex());
        }

        @Override
        protected Object[] prepareCArguments(VirtualFrame frame) {
            Object self = readSelf(frame);
            assert EnsurePythonObjectNode.doesNotNeedPromotion(self);
            Object cls = readClsNode.execute(frame);
            Object[] args = readVarargsNode.execute(frame);
            PKeyword[] kwargs = readKwargsNode.execute(frame);
            Object[] fastcallArgs = new Object[args.length + kwargs.length];
            Object[] fastcallKwnames = new Object[kwargs.length];
            for (int i = 0; i < args.length; i++) {
                fastcallArgs[i] = ensurePythonObject(args[i]);
            }
            for (int i = 0; i < kwargs.length; i++) {
                fastcallKwnames[i] = kwargs[i].getName();
                fastcallArgs[args.length + i] = ensurePythonObject(kwargs[i].getValue());
            }
            return new Object[]{self, cls, fastcallArgs, (long) args.length, PFactory.createTuple(PythonLanguage.get(this), fastcallKwnames)};
        }

        @Override
        protected Object[] cArgumentsToNative(Object[] arguments) {
            Object[] objects = super.cArgumentsToNative(arguments);
            assert arguments[2] instanceof Object[];
            assert arguments[2] == objects[2];
            assert arguments[3] instanceof Long;
            objects[2] = ensureArrayCreateNode().execute((Object[]) arguments[2]);
            return objects;
        }

        @Override
        protected void postprocessCArguments(VirtualFrame frame, Object[] cArguments, Object[] nativeArguments) {
            assert cArguments[2] instanceof Object[];
            assert cArguments[3] instanceof Long;
            assert cArguments[4] instanceof PTuple;
            assert ((Object[]) cArguments[2]).length == (Long) cArguments[3] + ((PTuple) cArguments[4]).getSequenceStorage().length();
            ensureArrayFreeNode().execute((long) nativeArguments[2]);
        }

        @Override
        public Signature getSignature() {
            return SIGNATURE;
        }
    }

    public static final class MethFastcallRoot extends MethodDescriptorRoot {
        private static final Signature SIGNATURE = createSignature(false, 1, tsArray("self"), true, true);
        @Child private ReadVarArgsNode readVarargsNode;

        public MethFastcallRoot(PythonLanguage language, TruffleString name, boolean isStatic, MethodDescriptorWrapper provider) {
            super(language, name, isStatic, provider);
            this.readVarargsNode = ReadVarArgsNode.create(SIGNATURE.varArgsPArgumentsIndex());
        }

        @Override
        protected Object[] prepareCArguments(VirtualFrame frame) {
            Object self = readSelf(frame);
            assert EnsurePythonObjectNode.doesNotNeedPromotion(self);
            Object[] args = readVarargsNode.execute(frame);
            Object[] promotedArgs = new Object[args.length];
            for (int i = 0; i < args.length; i++) {
                promotedArgs[i] = ensurePythonObject(args[i]);
            }
            return new Object[]{self, promotedArgs, (long) promotedArgs.length};
        }

        @Override
        protected Object[] cArgumentsToNative(Object[] arguments) {
            Object[] objects = super.cArgumentsToNative(arguments);
            assert arguments[1] instanceof Object[];
            assert arguments[1] == objects[1];
            assert arguments[2] instanceof Long;
            objects[1] = ensureArrayCreateNode().execute((Object[]) arguments[1]);
            return objects;
        }

        @Override
        protected void postprocessCArguments(VirtualFrame frame, Object[] cArguments, Object[] nativeArguments) {
            assert cArguments[1] instanceof Object[];
            assert cArguments[2] instanceof Long;
            assert ((Object[]) cArguments[1]).length == (long) cArguments[2];
            ensureArrayFreeNode().execute((long) nativeArguments[1]);
        }

        @Override
        public Signature getSignature() {
            return SIGNATURE;
        }
    }

    /**
     * Wrapper root node for C function type {@code allocfunc} and {@code ssizeargfunc}.
     */
    static class AllocFuncRootNode extends WrapperDescriptorRoot {
        private static final Signature SIGNATURE = createSignature(false, -1, tsArray("self", "nitems"), true, false);
        @Child private ReadIndexedArgumentNode readArgNode;
        @Child private ConvertPIntToPrimitiveNode asSsizeTNode;

        AllocFuncRootNode(PythonLanguage language, TruffleString name, PExternalFunctionWrapper provider) {
            super(language, name, false, provider);
            this.readArgNode = ReadIndexedArgumentNode.create(1);
            this.asSsizeTNode = ConvertPIntToPrimitiveNodeGen.create();
        }

        @Override
        protected Object[] prepareCArguments(VirtualFrame frame) {
            Object self = readSelf(frame);
            assert EnsurePythonObjectNode.doesNotNeedPromotion(self);
            Object arg = readArgNode.execute(frame);
            try {
                return new Object[]{self, asSsizeTNode.executeLongCached(arg, 1, Long.BYTES)};
            } catch (UnexpectedResultException e) {
                throw CompilerDirectives.shouldNotReachHere();
            }
        }

        @Override
        public Signature getSignature() {
            return SIGNATURE;
        }
    }

    /**
     * Wrapper root node for a get attribute function (C type {@code getattrfunc}).
     */
    static final class GetAttrFuncRootNode extends WrapperDescriptorRoot {
        private static final TruffleLogger LOGGER = CApiContext.getLogger(GetAttrFuncRootNode.class);
        private static final Signature SIGNATURE = createSignature(false, -1, tsArray("self", "key"), true, false);
        @Child private ReadIndexedArgumentNode readArgNode;
        @Child private CExtNodes.AsCharPointerNode asCharPointerNode;

        GetAttrFuncRootNode(PythonLanguage language, TruffleString name, PExternalFunctionWrapper provider) {
            super(language, name, false, provider);
            this.readArgNode = ReadIndexedArgumentNode.create(1);
            this.asCharPointerNode = AsCharPointerNodeGen.create();
        }

        @Override
        protected Object[] prepareCArguments(VirtualFrame frame) {
            Object self = readSelf(frame);
            assert EnsurePythonObjectNode.doesNotNeedPromotion(self);
            Object arg = readArgNode.execute(frame);
            return new Object[]{self, asCharPointerNode.execute(arg)};
        }

        @Override
        protected void postprocessCArguments(VirtualFrame frame, Object[] cArguments, Object[] nativeArguments) {
            long nameArg = ((NativePointer) cArguments[1]).asPointer();
            if (LOGGER.isLoggable(Level.FINE)) {
                LOGGER.fine(PythonUtils.formatJString("Freeing name (const char *)0x%x", nameArg));
            }
            free(nameArg);
        }

        @Override
        public Signature getSignature() {
            return SIGNATURE;
        }
    }

    /**
     * Wrapper root node for a set attribute function (C type {@code setattrfunc}).
     */
    static final class SetAttrFuncRootNode extends WrapperDescriptorRoot {
        private static final TruffleLogger LOGGER = CApiContext.getLogger(SetAttrFuncRootNode.class);
        private static final Signature SIGNATURE = createSignature(false, -1, tsArray("self", "key", "value"), true, false);
        @Child private ReadIndexedArgumentNode readArg1Node;
        @Child private ReadIndexedArgumentNode readArg2Node;
        @Child private CExtNodes.AsCharPointerNode asCharPointerNode;

        SetAttrFuncRootNode(PythonLanguage language, TruffleString name, PExternalFunctionWrapper provider) {
            super(language, name, false, provider);
            this.readArg1Node = ReadIndexedArgumentNode.create(1);
            this.readArg2Node = ReadIndexedArgumentNode.create(2);
            this.asCharPointerNode = AsCharPointerNodeGen.create();
        }

        @Override
        protected Object[] prepareCArguments(VirtualFrame frame) {
            Object self = readSelf(frame);
            assert EnsurePythonObjectNode.doesNotNeedPromotion(self);
            Object arg1 = readArg1Node.execute(frame);
            Object arg2 = ensurePythonObject(readArg2Node.execute(frame));
            return new Object[]{self, arg1, arg2};
        }

        @Override
        protected Object[] cArgumentsToNative(Object[] arguments) {
            Object[] objects = super.cArgumentsToNative(arguments);
            objects[1] = wrapPointer(asCharPointerNode.execute(arguments[1]));
            return objects;
        }

        @Override
        protected void postprocessCArguments(VirtualFrame frame, Object[] cArguments, Object[] nativeArguments) {
            long nameArg = ((NativePointer) nativeArguments[1]).asPointer();
            if (LOGGER.isLoggable(Level.FINE)) {
                LOGGER.fine(PythonUtils.formatJString("Freeing name (const char *)0x%x", nameArg));
            }
            free(nameArg);
        }

        @Override
        public Signature getSignature() {
            return SIGNATURE;
        }
    }

    /**
     * Wrapper root node for a set attribute function (C type {@code setattrofunc}).
     */
    static final class SetAttrOFuncRootNode extends WrapperDescriptorRoot {
        private static final Signature SIGNATURE = createSignature(false, -1, tsArray("self", "name", "value"), true, false);
        @Child private ReadIndexedArgumentNode readNameNode;
        @Child private ReadIndexedArgumentNode readValueNode;

        SetAttrOFuncRootNode(PythonLanguage language, TruffleString name, PExternalFunctionWrapper provider) {
            super(language, name, false, provider);
            this.readNameNode = ReadIndexedArgumentNode.create(1);
            this.readValueNode = ReadIndexedArgumentNode.create(2);
        }

        @Override
        protected Object[] prepareCArguments(VirtualFrame frame) {
            Object self = readSelf(frame);
            assert EnsurePythonObjectNode.doesNotNeedPromotion(self);
            Object name = ensurePythonObject(readNameNode.execute(frame));
            Object value = ensurePythonObject(readValueNode.execute(frame));
            return new Object[]{self, name, value};
        }

        @Override
        public Signature getSignature() {
            return SIGNATURE;
        }
    }

    /**
     * Wrapper root node for a rich compare function (C type {@code richcmpfunc}).
     */
    static final class RichCmpFuncRootNode extends WrapperDescriptorRoot {
        private static final Signature SIGNATURE = createSignature(false, -1, tsArray("self", "other", "op"), true, false);
        @Child private ReadIndexedArgumentNode readArg1Node;
        @Child private ReadIndexedArgumentNode readArg2Node;
        @Child private ConvertPIntToPrimitiveNode asSsizeTNode;

        RichCmpFuncRootNode(PythonLanguage language, TruffleString name, PExternalFunctionWrapper provider) {
            super(language, name, false, provider);
            this.readArg1Node = ReadIndexedArgumentNode.create(1);
            this.readArg2Node = ReadIndexedArgumentNode.create(2);
            this.asSsizeTNode = ConvertPIntToPrimitiveNodeGen.create();
        }

        @Override
        protected Object[] prepareCArguments(VirtualFrame frame) {
            try {
                Object self = readSelf(frame);
                assert EnsurePythonObjectNode.doesNotNeedPromotion(self);
                Object arg1 = ensurePythonObject(readArg1Node.execute(frame));
                Object arg2 = ensurePythonObject(readArg2Node.execute(frame));
                return new Object[]{self, arg1, asSsizeTNode.executeIntCached(arg2, 1, Integer.BYTES)};
            } catch (UnexpectedResultException e) {
                throw CompilerDirectives.shouldNotReachHere();
            }
        }

        @Override
        public Signature getSignature() {
            return SIGNATURE;
        }
    }

    /**
     * Implements semantics of {@code typeobject.c: wrap_sq_item}.
     */
    // TODO: can we remove this???
    static final class GetItemRootNode extends WrapperDescriptorRoot {
        private static final Signature SIGNATURE = createSignature(false, -1, tsArray("self", "i"), true, false);
        @Child private ReadIndexedArgumentNode readArg1Node;
        @Child private GetIndexNode getIndexNode;

        GetItemRootNode(PythonLanguage language, TruffleString name, PExternalFunctionWrapper provider) {
            super(language, name, false, provider);
            this.readArg1Node = ReadIndexedArgumentNode.create(1);
            this.getIndexNode = GetIndexNode.create();
        }

        @Override
        protected Object[] prepareCArguments(VirtualFrame frame) {
            Object self = readSelf(frame);
            assert EnsurePythonObjectNode.doesNotNeedPromotion(self);
            Object arg1 = readArg1Node.execute(frame);
            return new Object[]{self, (long) getIndexNode.execute(self, arg1)};
        }

        @Override
        public Signature getSignature() {
            return SIGNATURE;
        }
    }

    /**
     * Implements semantics of {@code typeobject.c: wrap_sq_setitem}.
     */
    static final class SetItemRootNode extends WrapperDescriptorRoot {
        private static final Signature SIGNATURE = createSignature(false, -1, tsArray("self", "i", "value"), true, false);
        @Child private ReadIndexedArgumentNode readArg1Node;
        @Child private ReadIndexedArgumentNode readArg2Node;
        @Child private GetIndexNode getIndexNode;

        SetItemRootNode(PythonLanguage language, TruffleString name, PExternalFunctionWrapper provider) {
            super(language, name, false, provider);
            this.readArg1Node = ReadIndexedArgumentNode.create(1);
            this.readArg2Node = ReadIndexedArgumentNode.create(2);
            this.getIndexNode = GetIndexNode.create();
        }

        @Override
        protected Object[] prepareCArguments(VirtualFrame frame) {
            Object self = readSelf(frame);
            assert EnsurePythonObjectNode.doesNotNeedPromotion(self);
            Object arg1 = readArg1Node.execute(frame);
            Object arg2 = ensurePythonObject(readArg2Node.execute(frame));
            return new Object[]{self, (long) getIndexNode.execute(self, arg1), arg2};
        }

        @Override
        public Signature getSignature() {
            return SIGNATURE;
        }
    }

    /**
     * Implements semantics of {@code typeobject.c:wrap_descr_get}
     */
    public static final class DescrGetRootNode extends WrapperDescriptorRoot {
        private static final Signature SIGNATURE = createSignature(false, -1, tsArray("self", "obj", "type"), true, false);
        @Child private ReadIndexedArgumentNode readObj;
        @Child private ReadIndexedArgumentNode readType;

        public DescrGetRootNode(PythonLanguage language, TruffleString name, PExternalFunctionWrapper provider) {
            super(language, name, false, provider);
            this.readObj = ReadIndexedArgumentNode.create(1);
            this.readType = ReadIndexedArgumentNode.create(2);
        }

        @Override
        protected Object[] prepareCArguments(VirtualFrame frame) {
            Object self = readSelf(frame);
            assert EnsurePythonObjectNode.doesNotNeedPromotion(self);
            Object obj = ensurePythonObject(readObj.execute(frame));
            Object type = ensurePythonObject(readType.execute(frame));
            return new Object[]{self, obj == PNone.NONE ? PNone.NO_VALUE : obj, type == PNone.NONE ? PNone.NO_VALUE : type};
        }

        @Override
        public Signature getSignature() {
            return SIGNATURE;
        }
    }

    /**
     * Implements semantics of {@code typeobject.c:wrap_descr_delete}
     */
    public static final class DescrDeleteRootNode extends WrapperDescriptorRoot {
        private static final Signature SIGNATURE = createSignature(false, -1, tsArray("self", "obj"), true, false);
        @Child private ReadIndexedArgumentNode readObj;

        public DescrDeleteRootNode(PythonLanguage language, TruffleString name, PExternalFunctionWrapper provider) {
            super(language, name, false, provider);
            this.readObj = ReadIndexedArgumentNode.create(1);
        }

        @Override
        protected Object[] prepareCArguments(VirtualFrame frame) {
            Object self = readSelf(frame);
            assert EnsurePythonObjectNode.doesNotNeedPromotion(self);
            Object obj = ensurePythonObject(readObj.execute(frame));
            return new Object[]{self, obj, PNone.NO_VALUE};
        }

        @Override
        public Signature getSignature() {
            return SIGNATURE;
        }
    }

    /**
     * Implements semantics of {@code typeobject.c:wrap_delattr}
     */
    public static final class DelAttrRootNode extends WrapperDescriptorRoot {
        private static final Signature SIGNATURE = createSignature(false, -1, tsArray("self", "obj"), true, false);
        @Child private ReadIndexedArgumentNode readObj;

        public DelAttrRootNode(PythonLanguage language, TruffleString name, PExternalFunctionWrapper provider) {
            super(language, name, false, provider);
            this.readObj = ReadIndexedArgumentNode.create(1);
        }

        @Override
        protected Object[] prepareCArguments(VirtualFrame frame) {
            Object self = readSelf(frame);
            assert EnsurePythonObjectNode.doesNotNeedPromotion(self);
            Object obj = ensurePythonObject(readObj.execute(frame));
            // TODO: check if we need Carlo Verre hack here (see typeobject.c:hackcheck)
            return new Object[]{self, obj, PNone.NO_VALUE};
        }

        @Override
        public Signature getSignature() {
            return SIGNATURE;
        }
    }

    /**
     * Implement mapping of {@code __delitem__} to {@code mp_ass_subscript}. It handles adding the
     * NULL 3rd argument.
     */
    static final class MpDelItemRootNode extends WrapperDescriptorRoot {
        private static final Signature SIGNATURE = createSignature(false, -1, tsArray("self", "i"), true, false);
        @Child private ReadIndexedArgumentNode readArg1Node;

        MpDelItemRootNode(PythonLanguage language, TruffleString name, PExternalFunctionWrapper provider) {
            super(language, name, false, provider);
            this.readArg1Node = ReadIndexedArgumentNode.create(1);
        }

        @Override
        protected Object[] prepareCArguments(VirtualFrame frame) {
            Object self = readSelf(frame);
            assert EnsurePythonObjectNode.doesNotNeedPromotion(self);
            Object arg1 = ensurePythonObject(readArg1Node.execute(frame));
            return new Object[]{self, arg1, PNone.NO_VALUE};
        }

        @Override
        public Signature getSignature() {
            return SIGNATURE;
        }
    }

    /**
     * Wrapper root node for reverse binary operations.
     */
    static final class MethBinaryFuncRRoot extends WrapperDescriptorRoot {
        private static final Signature SIGNATURE = createSignature(false, -1, tsArray("self", "obj"), true, false);
        @Child private ReadIndexedArgumentNode readArg1Node;

        MethBinaryFuncRRoot(PythonLanguage language, TruffleString name, PExternalFunctionWrapper provider) {
            super(language, name, false, provider);
            this.readArg1Node = ReadIndexedArgumentNode.create(1);
        }

        @Override
        protected Object[] prepareCArguments(VirtualFrame frame) {
            Object arg0 = ensurePythonObject(readSelf(frame));
            Object arg1 = ensurePythonObject(readArg1Node.execute(frame));
            return new Object[]{arg1, arg0};
        }

        @Override
        public Signature getSignature() {
            return SIGNATURE;
        }
    }

    /**
     * Wrapper root node for native power function (with an optional third argument).
     */
    static class MethTernaryFuncRoot extends WrapperDescriptorRoot {
        private static final Signature SIGNATURE = createSignature(false, 0, tsArray("args"), false, false);

        @Child private ReadVarArgsNode readVarargsNode;

        private final ConditionProfile profile;

        MethTernaryFuncRoot(PythonLanguage language, TruffleString name, PExternalFunctionWrapper provider) {
            super(language, name, false, provider);
            this.readVarargsNode = ReadVarArgsNode.create(SIGNATURE.varArgsPArgumentsIndex());
            this.profile = ConditionProfile.create();
        }

        @Override
        protected final Object[] prepareCArguments(VirtualFrame frame) {
            Object self = readSelf(frame);
            assert EnsurePythonObjectNode.doesNotNeedPromotion(self);
            Object[] varargs = readVarargsNode.execute(frame);
            Object arg0 = ensurePythonObject(varargs[0]);
            Object arg1 = ensurePythonObject(profile.profile(varargs.length > 1) ? varargs[1] : PNone.NONE);
            return getArguments(self, arg0, arg1);
        }

        Object[] getArguments(Object arg0, Object arg1, Object arg2) {
            return new Object[]{arg0, arg1, arg2};
        }

        @Override
        public Signature getSignature() {
            return SIGNATURE;
        }
    }

    /**
     * Wrapper root node for native reverse power function (with an optional third argument).
     */
    static final class MethTernaryFuncRRoot extends MethTernaryFuncRoot {

        MethTernaryFuncRRoot(PythonLanguage language, TruffleString name, PExternalFunctionWrapper provider) {
            super(language, name, provider);
        }

        @Override
        Object[] getArguments(Object arg0, Object arg1, Object arg2) {
            return new Object[]{arg1, arg0, arg2};
        }
    }

    /**
     * Wrapper root node for native power function (with an optional third argument).
     */
    static final class MethRichcmpOpRootNode extends WrapperDescriptorRoot {
        private static final Signature SIGNATURE = createSignature(false, -1, tsArray("self", "other"), true, false);
        @Child private ReadIndexedArgumentNode readArgNode;

        private final int op;

        MethRichcmpOpRootNode(PythonLanguage language, TruffleString name, PExternalFunctionWrapper provider, int op) {
            super(language, name, false, provider);
            this.readArgNode = ReadIndexedArgumentNode.create(1);
            this.op = op;
        }

        @Override
        protected Object[] prepareCArguments(VirtualFrame frame) {
            Object self = readSelf(frame);
            assert EnsurePythonObjectNode.doesNotNeedPromotion(self);
            Object arg = ensurePythonObject(readArgNode.execute(frame));
            return new Object[]{self, arg, op};
        }

        @Override
        public Signature getSignature() {
            return SIGNATURE;
        }
    }

    /**
     * Wrapper root node for C function type {@code iternextfunc}.
     */
    static class IterNextFuncRootNode extends WrapperDescriptorRoot {

        IterNextFuncRootNode(PythonLanguage language, TruffleString name, PExternalFunctionWrapper provider) {
            super(language, name, false, provider);
        }

        @Override
        protected Object[] prepareCArguments(VirtualFrame frame) {
            return new Object[]{readSelf(frame)};
        }

        @Override
        public Signature getSignature() {
            // same signature as a method without arguments (just the self)
            return MethNoargsRoot.SIGNATURE;
        }
    }

    abstract static class GetSetRootNode extends WrapperDescriptorRoot {

        @Child private ReadIndexedArgumentNode readClosureNode;

        GetSetRootNode(PythonLanguage language, TruffleString name, PExternalFunctionWrapper provider) {
            super(language, name, false, provider);
        }

        protected final Object readClosure(VirtualFrame frame) {
            if (readClosureNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                // we insert a hidden argument after the hidden callable arg
                int hiddenArg = getSignature().getParameterIds().length + 1;
                readClosureNode = insert(ReadIndexedArgumentNode.create(hiddenArg));
            }
            return readClosureNode.execute(frame);
        }

    }

    /**
     * Wrapper root node for C function type {@code getter}.
     */
    public static class GetterRoot extends GetSetRootNode {
        private static final Signature SIGNATURE = createSignatureWithClosure(false, -1, tsArray("self"), true, false);

        public GetterRoot(PythonLanguage language, TruffleString name, PExternalFunctionWrapper provider) {
            super(language, name, provider);
        }

        @Override
        protected Object[] prepareCArguments(VirtualFrame frame) {
            Object self = readSelf(frame);
            assert EnsurePythonObjectNode.doesNotNeedPromotion(self);
            return new Object[]{self, readClosure(frame)};
        }

        @Override
        public Signature getSignature() {
            return SIGNATURE;
        }
    }

    /**
     * Wrapper root node for C function type {@code setter}.
     */
    public static class SetterRoot extends GetSetRootNode {
        private static final Signature SIGNATURE = createSignatureWithClosure(false, -1, tsArray("self", "value"), true, false);

        @Child private ReadIndexedArgumentNode readArgNode;

        public SetterRoot(PythonLanguage language, TruffleString name, PExternalFunctionWrapper provider) {
            super(language, name, provider);
        }

        @Override
        protected Object[] prepareCArguments(VirtualFrame frame) {
            Object self = readSelf(frame);
            assert EnsurePythonObjectNode.doesNotNeedPromotion(self);
            Object arg = ensurePythonObject(ensureReadArgNode().execute(frame));
            return new Object[]{self, arg, readClosure(frame)};
        }

        @Override
        public Signature getSignature() {
            return SIGNATURE;
        }

        private ReadIndexedArgumentNode ensureReadArgNode() {
            if (readArgNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                readArgNode = insert(ReadIndexedArgumentNode.create(1));
            }
            return readArgNode;
        }
    }

    public static final class MethLenfuncRoot extends WrapperDescriptorRoot {
        private static final Signature SIGNATURE = createSignature(false, -1, tsArray("self"), true, false);

        public MethLenfuncRoot(PythonLanguage language, TruffleString name, PExternalFunctionWrapper provider) {
            super(language, name, false, provider);
        }

        @Override
        protected Object[] prepareCArguments(VirtualFrame frame) {
            Object self = readSelf(frame);
            assert EnsurePythonObjectNode.doesNotNeedPromotion(self);
            return new Object[]{self};
        }

        @Override
        public Signature getSignature() {
            return SIGNATURE;
        }
    }

    static final class MethObjObjProcRoot extends WrapperDescriptorRoot {
        private static final Signature SIGNATURE = createSignature(false, -1, tsArray("self", "value"), true, false);

        @Child private ReadIndexedArgumentNode readValueNode;

        private MethObjObjProcRoot(PythonLanguage lang, TruffleString name, PExternalFunctionWrapper provider) {
            super(lang, name, false, provider);
            this.readValueNode = ReadIndexedArgumentNode.create(1);
        }

        @Override
        protected Object[] prepareCArguments(VirtualFrame frame) {
            Object self = readSelf(frame);
            assert EnsurePythonObjectNode.doesNotNeedPromotion(self);
            Object value = ensurePythonObject(readValueNode.execute(frame));
            return new Object[]{self, value};
        }

        @Override
        public Signature getSignature() {
            return SIGNATURE;
        }
    }

    static final class MethObjObjArgProcRoot extends WrapperDescriptorRoot {
        private static final Signature SIGNATURE = createSignature(false, -1, tsArray("self", "key", "value"), true, false);

        @Child private ReadIndexedArgumentNode readKeyNode;
        @Child private ReadIndexedArgumentNode readValueNode;

        private MethObjObjArgProcRoot(PythonLanguage lang, TruffleString name, PExternalFunctionWrapper provider) {
            super(lang, name, false, provider);
            this.readKeyNode = ReadIndexedArgumentNode.create(1);
            this.readValueNode = ReadIndexedArgumentNode.create(2);
        }

        @Override
        protected Object[] prepareCArguments(VirtualFrame frame) {
            Object self = readSelf(frame);
            assert EnsurePythonObjectNode.doesNotNeedPromotion(self);
            Object key = ensurePythonObject(readKeyNode.execute(frame));
            Object value = ensurePythonObject(readValueNode.execute(frame));
            return new Object[]{self, key, value};
        }

        @Override
        public Signature getSignature() {
            return SIGNATURE;
        }
    }

    static final class MethBinaryRoot extends WrapperDescriptorRoot {
        private static final Signature SIGNATURE = createSignature(false, -1, tsArray("self", "name"), true, false);

        @Child private ReadIndexedArgumentNode readNameNode;

        private MethBinaryRoot(PythonLanguage lang, TruffleString name, PExternalFunctionWrapper provider) {
            super(lang, name, false, provider);
            this.readNameNode = ReadIndexedArgumentNode.create(1);
        }

        @Override
        protected Object[] prepareCArguments(VirtualFrame frame) {
            Object self = readSelf(frame);
            assert EnsurePythonObjectNode.doesNotNeedPromotion(self);
            Object name = ensurePythonObject(readNameNode.execute(frame));
            return new Object[]{self, name};
        }

        @Override
        public Signature getSignature() {
            return SIGNATURE;
        }
    }

    static final class MethDescrSetRoot extends WrapperDescriptorRoot {
        private static final Signature SIGNATURE = createSignature(false, -1, tsArray("self", "instance", "value"), true, false);
        @Child private ReadIndexedArgumentNode readInstanceNode;
        @Child private ReadIndexedArgumentNode readValueNode;

        MethDescrSetRoot(PythonLanguage language, TruffleString name, PExternalFunctionWrapper provider) {
            super(language, name, false, provider);
            this.readInstanceNode = ReadIndexedArgumentNode.create(1);
            this.readValueNode = ReadIndexedArgumentNode.create(2);
        }

        @Override
        protected Object[] prepareCArguments(VirtualFrame frame) {
            Object self = ensurePythonObject(readSelf(frame));
            Object instance = ensurePythonObject(readInstanceNode.execute(frame));
            Object value = ensurePythonObject(readValueNode.execute(frame));
            return new Object[]{self, instance, value};
        }

        @Override
        public Signature getSignature() {
            return SIGNATURE;
        }
    }

    /**
     * An inlined node-like object for keeping track of eager native allocation state bit. Should be
     * {@code @Cached} and passed into {@link CreateArgsTupleNode#execute}. Then the
     * {@link #report(Node, PTuple)} method should be called with the tuple after the native call
     * returns.
     */
    public static final class EagerTupleState {
        private final StateField state;

        private static final EagerTupleState UNCACHED = new EagerTupleState();

        private EagerTupleState() {
            this.state = null;
        }

        private EagerTupleState(InlineTarget target) {
            this.state = target.getState(0, 1);
        }

        public boolean isEager(Node inliningTarget) {
            if (state == null) {
                return false;
            }
            return state.get(inliningTarget) != 0;
        }

        public void report(Node inliningTarget, PTuple tuple) {
            if (state != null) {
                if (!isEager(inliningTarget) && tuple.getSequenceStorage() instanceof NativeSequenceStorage) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    state.set(inliningTarget, 1);
                }
            }
        }

        public static EagerTupleState inline(
                        @RequiredField(value = StateField.class, bits = 1) InlineTarget target) {
            return new EagerTupleState(target);
        }

        public static EagerTupleState getUncached() {
            return UNCACHED;
        }
    }

    /**
     * We need to inflate all primitives in order to avoid memory leaks. Explanation: Primitives
     * would currently be wrapped into a PrimitiveNativeWrapper. If any of those will receive a
     * toNative message, the managed code will be the only owner of those wrappers. But we will
     * never be able to reach the wrapper from the arguments if they are just primitive. So, we
     * inflate the primitives and we can then traverse the tuple and reach the wrappers of its
     * arguments after the call returned.
     */
    @GenerateInline(false)
    @GenerateUncached
    public abstract static class CreateArgsTupleNode extends Node {
        public abstract PTuple execute(PythonContext context, Object[] args, boolean eagerNative);

        public final PTuple execute(Node inliningTarget, PythonContext context, Object[] args, EagerTupleState state) {
            return execute(context, args, state.isEager(inliningTarget));
        }

        @Specialization(guards = {"args.length == cachedLen", "cachedLen <= 8", "!eagerNative"}, limit = "1")
        @ExplodeLoop(kind = LoopExplosionKind.FULL_UNROLL)
        static PTuple doCachedLen(PythonContext context, Object[] args, @SuppressWarnings("unused") boolean eagerNative,
                        @Cached("args.length") int cachedLen,
                        @Cached("createMaterializeNodes(args.length)") EnsurePythonObjectNode[] materializePrimitiveNodes) {

            for (int i = 0; i < cachedLen; i++) {
                args[i] = materializePrimitiveNodes[i].execute(context, args[i], false);
            }
            return PFactory.createTuple(context.getLanguage(), args);
        }

        @Specialization(guards = {"args.length == cachedLen", "cachedLen <= 8", "eagerNative"}, limit = "1", replaces = "doCachedLen")
        @ExplodeLoop(kind = LoopExplosionKind.FULL_UNROLL)
        static PTuple doCachedLenEagerNative(PythonContext context, Object[] args, @SuppressWarnings("unused") boolean eagerNative,
                        @Bind Node inliningTarget,
                        @Cached("args.length") int cachedLen,
                        @Cached("createMaterializeNodes(args.length)") EnsurePythonObjectNode[] materializePrimitiveNodes,
                        @Exclusive @Cached StorageToNativeNode storageToNativeNode) {

            for (int i = 0; i < cachedLen; i++) {
                args[i] = materializePrimitiveNodes[i].execute(context, args[i], false);
            }
            return PFactory.createTuple(context.getLanguage(), storageToNativeNode.execute(inliningTarget, args, cachedLen, true));
        }

        @Specialization(replaces = {"doCachedLen", "doCachedLenEagerNative"})
        static PTuple doGeneric(PythonContext context, Object[] args, boolean eagerNative,
                        @Bind Node inliningTarget,
                        @Cached EnsurePythonObjectNode materializePrimitiveNode,
                        @Exclusive @Cached StorageToNativeNode storageToNativeNode) {

            int n = args.length;
            for (int i = 0; i < n; i++) {
                args[i] = materializePrimitiveNode.execute(context, args[i], false);
            }
            SequenceStorage storage;
            if (eagerNative) {
                storage = storageToNativeNode.execute(inliningTarget, args, n, true);
            } else {
                storage = new ObjectSequenceStorage(args);
            }
            return PFactory.createTuple(context.getLanguage(), storage);
        }

        static EnsurePythonObjectNode[] createMaterializeNodes(int length) {
            EnsurePythonObjectNode[] materializePrimitiveNodes = new EnsurePythonObjectNode[length];
            for (int i = 0; i < length; i++) {
                materializePrimitiveNodes[i] = EnsurePythonObjectNode.create();
            }
            return materializePrimitiveNodes;
        }
    }

    @GenerateInline(false)
    @ImportStatic(PythonUtils.class)
    abstract static class ReleaseNativeSequenceStorageNode extends Node {

        abstract void execute(NativeSequenceStorage storage);

        @Specialization(guards = {"storage.length() == cachedLen", "cachedLen <= 8"}, limit = "1")
        @ExplodeLoop(kind = LoopExplosionKind.FULL_UNROLL)
        static void doObjectCachedLen(NativeObjectSequenceStorage storage,
                        @Bind Node inliningTarget,
                        @Cached("storage.length()") int cachedLen,
                        @Shared @Cached CExtNodes.XDecRefPointerNode decRefPointerNode) {
            for (int i = 0; i < cachedLen; i++) {
                long elementPointer = readPtrArrayElement(storage.getPtr(), i);
                decRefPointerNode.execute(inliningTarget, elementPointer);
            }
            // in this case, the runtime still exclusively owns the memory
            free(storage.getPtr());
        }

        @Specialization(replaces = "doObjectCachedLen")
        static void doObjectGeneric(NativeObjectSequenceStorage storage,
                        @Bind Node inliningTarget,
                        @Shared @Cached CExtNodes.XDecRefPointerNode decRefPointerNode) {
            for (int i = 0; i < storage.length(); i++) {
                long elementPointer = readPtrArrayElement(storage.getPtr(), i);
                decRefPointerNode.execute(inliningTarget, elementPointer);
            }
            // in this case, the runtime still exclusively owns the memory
            free(storage.getPtr());
        }
    }

    // roughly equivalent to _Py_CheckFunctionResult in Objects/call.c
    @ImportStatic(PGuards.class)
    @GenerateUncached
    @GenerateInline(false)
    public abstract static class PyObjectCheckFunctionResultNode extends CheckFunctionResultNode {

        @TruffleBoundary
        public static Object executeUncached(TruffleString name, Object result) {
            return PyObjectCheckFunctionResultNodeGen.getUncached().execute(PythonContext.get(null), name, result);
        }

        @Specialization(guards = "!isForeignObject.execute(inliningTarget, result)")
        static Object doPythonObject(PythonThreadState state, TruffleString name, Object result,
                        @Bind Node inliningTarget,
                        @SuppressWarnings("unused") @Shared @Cached IsForeignObjectNode isForeignObject,
                        @Shared @Cached InlinedConditionProfile indicatesErrorProfile,
                        @Shared @Cached TransformExceptionFromNativeNode transformExceptionFromNativeNode) {
            boolean indicatesError = indicatesErrorProfile.profile(inliningTarget, PGuards.isNoValue(result));
            transformExceptionFromNativeNode.execute(inliningTarget, state, name, indicatesError, true);
            assert !indicatesError; // otherwise we should not reach here
            return result;
        }

        @Specialization(guards = "isForeignObject.execute(inliningTarget, result)")
        static Object doForeign(PythonThreadState state, TruffleString name, Object result,
                        @Bind Node inliningTarget,
                        @SuppressWarnings("unused") @Shared @Cached IsForeignObjectNode isForeignObject,
                        @Shared @Cached InlinedConditionProfile indicatesErrorProfile,
                        @Shared @Cached TransformExceptionFromNativeNode transformExceptionFromNativeNode,
                        @CachedLibrary(limit = "3") InteropLibrary lib) {
            boolean indicatesError = indicatesErrorProfile.profile(inliningTarget, lib.isNull(result));
            transformExceptionFromNativeNode.execute(inliningTarget, state, name, indicatesError, true);
            assert !indicatesError; // otherwise we should not reach here
            return result;
        }
    }

    /**
     * Equivalent of the result processing part in {@code Objects/typeobject.c: wrap_next}.
     */
    @GenerateInline(false)
    @GenerateUncached
    public abstract static class CheckIterNextResultNode extends CheckFunctionResultNode {

        @Specialization
        static Object doGeneric(PythonThreadState state, @SuppressWarnings("unused") TruffleString name, Object result,
                        @Bind Node inliningTarget,
                        @Cached CExtCommonNodes.ReadAndClearNativeException readAndClearNativeException,
                        @Cached PRaiseNode raiseNode) {
            if (result == PNone.NO_VALUE) {
                Object currentException = readAndClearNativeException.execute(inliningTarget, state);
                // if no exception occurred, the iterator is exhausted -> raise StopIteration
                if (currentException == PNone.NO_VALUE) {
                    throw raiseNode.raise(inliningTarget, PythonBuiltinClassType.StopIteration);
                } else {
                    throw PException.fromObjectFixUncachedLocation(currentException, inliningTarget, false);
                }
            }
            return result;
        }
    }

    /**
     * Processes the function result with CPython semantics:
     *
     * <pre>
     *     if (func(self, args, kwds) < 0)
     *         return NULL;
     *     Py_RETURN_NONE;
     * </pre>
     *
     * This is the case for {@code wrap_init}, {@code wrap_descr_delete}, {@code wrap_descr_set},
     * {@code wrap_delattr}, {@code wrap_setattr}.
     */
    @ImportStatic(PGuards.class)
    @GenerateInline(false)
    @GenerateUncached
    public abstract static class InitCheckFunctionResultNode extends CheckFunctionResultNode {

        public abstract PNone executeInt(PythonThreadState threadState, TruffleString name, int result);

        @Specialization
        @SuppressWarnings("unused")
        static PNone doInt(PythonThreadState state, TruffleString name, int result,
                        @Bind Node inliningTarget,
                        @Cached TransformExceptionFromNativeNode transformExceptionFromNativeNode) {
            transformExceptionFromNativeNode.execute(inliningTarget, state, name, result < 0, true);
            return PNone.NONE;
        }
    }

    /**
     * Processes the function result with CPython semantics:
     *
     * <pre>
     *     Py_ssize_t res = func(...);
     *     if (res == -1 && PyErr_Occurred())
     *         return NULL;
     * </pre>
     *
     * This is the case for {@code wrap_delitem}, {@code wrap_objobjargproc},
     * {@code wrap_sq_delitem}, {@code wrap_sq_setitem}, {@code asdf}.
     */
    @GenerateUncached
    @GenerateInline(false)
    @TypeSystemReference(PythonIntegerTypes.class)
    public abstract static class CheckPrimitiveFunctionResultNode extends CheckFunctionResultNode {
        public abstract long executeLong(PythonThreadState threadState, TruffleString name, long result);

        @Specialization
        static long doLong(PythonThreadState threadState, TruffleString name, long result,
                        @Bind Node inliningTarget,
                        @Cached TransformExceptionFromNativeNode transformExceptionFromNativeNode) {
            transformExceptionFromNativeNode.execute(inliningTarget, threadState, name, result == -1, false);
            return result;
        }
    }

    /**
     * Tests if the primitive result of the called function is {@code -1} and if an error occurred.
     * In this case, the error is re-raised. Otherwise, it converts the result to a Boolean. This is
     * equivalent to the result processing part in {@code Object/typeobject.c: wrap_inquirypred} and
     * {@code Object/typeobject.c: wrap_objobjproc}.
     */
    @GenerateInline(false)
    @GenerateUncached
    public abstract static class CheckInquiryResultNode extends CheckFunctionResultNode {

        public abstract boolean executeBool(PythonThreadState threadState, TruffleString name, int result);

        @Specialization
        static boolean doLong(PythonThreadState threadState, TruffleString name, int result,
                        @Bind Node inliningTarget,
                        @Cached InlinedConditionProfile resultProfile,
                        @Cached TransformExceptionFromNativeNode transformExceptionFromNativeNode) {
            transformExceptionFromNativeNode.execute(inliningTarget, threadState, name, result == -1, false);
            return resultProfile.profile(inliningTarget, result != 0);
        }
    }
}
