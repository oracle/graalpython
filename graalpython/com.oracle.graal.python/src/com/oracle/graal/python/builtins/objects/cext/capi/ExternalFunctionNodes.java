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

import static com.oracle.graal.python.builtins.objects.cext.capi.ExternalFunctionNodes.PExternalFunctionWrapper.BINARYFUNC;
import static com.oracle.graal.python.builtins.objects.cext.capi.ExternalFunctionNodes.PExternalFunctionWrapper.BINARYFUNC_L;
import static com.oracle.graal.python.builtins.objects.cext.capi.ExternalFunctionNodes.PExternalFunctionWrapper.BINARYFUNC_R;
import static com.oracle.graal.python.builtins.objects.cext.capi.ExternalFunctionNodes.PExternalFunctionWrapper.CALL;
import static com.oracle.graal.python.builtins.objects.cext.capi.ExternalFunctionNodes.PExternalFunctionWrapper.DELATTRO;
import static com.oracle.graal.python.builtins.objects.cext.capi.ExternalFunctionNodes.PExternalFunctionWrapper.DESCR_DELETE;
import static com.oracle.graal.python.builtins.objects.cext.capi.ExternalFunctionNodes.PExternalFunctionWrapper.DESCR_GET;
import static com.oracle.graal.python.builtins.objects.cext.capi.ExternalFunctionNodes.PExternalFunctionWrapper.DESCR_SET;
import static com.oracle.graal.python.builtins.objects.cext.capi.ExternalFunctionNodes.PExternalFunctionWrapper.EQ;
import static com.oracle.graal.python.builtins.objects.cext.capi.ExternalFunctionNodes.PExternalFunctionWrapper.GE;
import static com.oracle.graal.python.builtins.objects.cext.capi.ExternalFunctionNodes.PExternalFunctionWrapper.GETATTR;
import static com.oracle.graal.python.builtins.objects.cext.capi.ExternalFunctionNodes.PExternalFunctionWrapper.GETTER;
import static com.oracle.graal.python.builtins.objects.cext.capi.ExternalFunctionNodes.PExternalFunctionWrapper.GT;
import static com.oracle.graal.python.builtins.objects.cext.capi.ExternalFunctionNodes.PExternalFunctionWrapper.HASHFUNC;
import static com.oracle.graal.python.builtins.objects.cext.capi.ExternalFunctionNodes.PExternalFunctionWrapper.INDEXARGFUNC;
import static com.oracle.graal.python.builtins.objects.cext.capi.ExternalFunctionNodes.PExternalFunctionWrapper.INIT;
import static com.oracle.graal.python.builtins.objects.cext.capi.ExternalFunctionNodes.PExternalFunctionWrapper.INQUIRYPRED;
import static com.oracle.graal.python.builtins.objects.cext.capi.ExternalFunctionNodes.PExternalFunctionWrapper.ITERNEXT;
import static com.oracle.graal.python.builtins.objects.cext.capi.ExternalFunctionNodes.PExternalFunctionWrapper.LE;
import static com.oracle.graal.python.builtins.objects.cext.capi.ExternalFunctionNodes.PExternalFunctionWrapper.LENFUNC;
import static com.oracle.graal.python.builtins.objects.cext.capi.ExternalFunctionNodes.PExternalFunctionWrapper.LT;
import static com.oracle.graal.python.builtins.objects.cext.capi.ExternalFunctionNodes.PExternalFunctionWrapper.MP_DELITEM;
import static com.oracle.graal.python.builtins.objects.cext.capi.ExternalFunctionNodes.PExternalFunctionWrapper.NE;
import static com.oracle.graal.python.builtins.objects.cext.capi.ExternalFunctionNodes.PExternalFunctionWrapper.NEW;
import static com.oracle.graal.python.builtins.objects.cext.capi.ExternalFunctionNodes.PExternalFunctionWrapper.OBJOBJARGPROC;
import static com.oracle.graal.python.builtins.objects.cext.capi.ExternalFunctionNodes.PExternalFunctionWrapper.OBJOBJPROC;
import static com.oracle.graal.python.builtins.objects.cext.capi.ExternalFunctionNodes.PExternalFunctionWrapper.RICHCMP;
import static com.oracle.graal.python.builtins.objects.cext.capi.ExternalFunctionNodes.PExternalFunctionWrapper.SETATTR;
import static com.oracle.graal.python.builtins.objects.cext.capi.ExternalFunctionNodes.PExternalFunctionWrapper.SETATTRO;
import static com.oracle.graal.python.builtins.objects.cext.capi.ExternalFunctionNodes.PExternalFunctionWrapper.SETTER;
import static com.oracle.graal.python.builtins.objects.cext.capi.ExternalFunctionNodes.PExternalFunctionWrapper.SQ_DELITEM;
import static com.oracle.graal.python.builtins.objects.cext.capi.ExternalFunctionNodes.PExternalFunctionWrapper.SQ_ITEM;
import static com.oracle.graal.python.builtins.objects.cext.capi.ExternalFunctionNodes.PExternalFunctionWrapper.SQ_SETITEM;
import static com.oracle.graal.python.builtins.objects.cext.capi.ExternalFunctionNodes.PExternalFunctionWrapper.TERNARYFUNC;
import static com.oracle.graal.python.builtins.objects.cext.capi.ExternalFunctionNodes.PExternalFunctionWrapper.TERNARYFUNC_R;
import static com.oracle.graal.python.builtins.objects.cext.capi.ExternalFunctionNodes.PExternalFunctionWrapper.UNARYFUNC;
import static com.oracle.graal.python.builtins.objects.cext.capi.NativeCAPISymbol.FUN_PY_DEALLOC;
import static com.oracle.graal.python.builtins.objects.cext.capi.NativeCAPISymbol.FUN_PY_TYPE_GENERIC_ALLOC;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PyObjectReturn;
import static com.oracle.graal.python.builtins.objects.cext.structs.CStructAccess.readLongField;
import static com.oracle.graal.python.nfi2.NativeMemory.NULLPTR;
import static com.oracle.graal.python.nfi2.NativeMemory.free;
import static com.oracle.graal.python.nfi2.NativeMemory.readPtrArrayElement;
import static com.oracle.graal.python.nfi2.NativeMemory.writePtrArrayElement;
import static com.oracle.graal.python.nodes.StringLiterals.T_EMPTY_STRING;
import static com.oracle.graal.python.util.PythonUtils.tsArray;
import static com.oracle.graal.python.util.PythonUtils.tsLiteral;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.ref.Reference;
import java.util.logging.Level;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.cext.PythonAbstractNativeObject;
import com.oracle.graal.python.builtins.objects.cext.capi.CApiGCSupport.PyObjectGCTrackNode;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.AsCharPointerNode;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.EnsurePythonObjectNode;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodesFactory.AsCharPointerNodeGen;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodesFactory.GetNativeClassNodeGen;
import com.oracle.graal.python.builtins.objects.cext.capi.ExternalFunctionNodesFactory.CheckIterNextResultNodeGen;
import com.oracle.graal.python.builtins.objects.cext.capi.ExternalFunctionNodesFactory.CreateNativeArgsTupleNodeGen;
import com.oracle.graal.python.builtins.objects.cext.capi.ExternalFunctionNodesFactory.FromLongNodeGen;
import com.oracle.graal.python.builtins.objects.cext.capi.ExternalFunctionNodesFactory.FromUInt32NodeGen;
import com.oracle.graal.python.builtins.objects.cext.capi.ExternalFunctionNodesFactory.PyObjectCheckFunctionResultNodeGen;
import com.oracle.graal.python.builtins.objects.cext.capi.ExternalFunctionNodesFactory.ReleaseNativeArgsTupleNodeGen;
import com.oracle.graal.python.builtins.objects.cext.capi.ExternalFunctionNodesFactory.ToInt32NodeGen;
import com.oracle.graal.python.builtins.objects.cext.capi.ExternalFunctionNodesFactory.ToInt64NodeGen;
import com.oracle.graal.python.builtins.objects.cext.capi.ExternalFunctionNodesFactory.ToPythonStringNodeGen;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTiming;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTransitions;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTransitions.HandlePointerConverter;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTransitions.NativeToPythonNode;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTransitions.NativeToPythonReturnNode;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTransitions.PythonToNativeInternalNode;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTransitions.PythonToNativeNode;
import com.oracle.graal.python.builtins.objects.cext.common.CExtCommonNodes.ConvertPIntToPrimitiveNode;
import com.oracle.graal.python.builtins.objects.cext.common.CExtCommonNodes.GetIndexNode;
import com.oracle.graal.python.builtins.objects.cext.common.CExtCommonNodes.ReadAndClearNativeException;
import com.oracle.graal.python.builtins.objects.cext.common.CExtCommonNodes.TransformExceptionFromNativeNode;
import com.oracle.graal.python.builtins.objects.cext.common.CExtCommonNodesFactory.ConvertPIntToPrimitiveNodeGen;
import com.oracle.graal.python.builtins.objects.cext.common.CExtToJavaNode;
import com.oracle.graal.python.builtins.objects.cext.common.CExtToNativeNode;
import com.oracle.graal.python.builtins.objects.cext.common.NativeCExtSymbol;
import com.oracle.graal.python.builtins.objects.cext.structs.CFields;
import com.oracle.graal.python.builtins.objects.cext.structs.CStructAccess;
import com.oracle.graal.python.builtins.objects.function.PBuiltinFunction;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.builtins.objects.function.Signature;
import com.oracle.graal.python.builtins.objects.object.PythonBuiltinObject;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.builtins.objects.type.PythonBuiltinClass;
import com.oracle.graal.python.builtins.objects.type.TypeFlags;
import com.oracle.graal.python.builtins.objects.type.TypeNodes.GetTypeFlagsNode;
import com.oracle.graal.python.builtins.objects.type.TypeNodes.IsSameTypeNode;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlot;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlot.TpSlotNative;
import com.oracle.graal.python.lib.PyNumberAsSizeNode;
import com.oracle.graal.python.lib.RichCmpOp;
import com.oracle.graal.python.nfi2.NativeMemory;
import com.oracle.graal.python.nfi2.NfiBoundFunction;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.PRootNode;
import com.oracle.graal.python.nodes.argument.ReadIndexedArgumentNode;
import com.oracle.graal.python.nodes.argument.ReadVarArgsNode;
import com.oracle.graal.python.nodes.argument.ReadVarKeywordsNode;
import com.oracle.graal.python.nodes.object.IsForeignObjectNode;
import com.oracle.graal.python.nodes.util.CastToTruffleStringNode;
import com.oracle.graal.python.runtime.ExecutionContext.CalleeContext;
import com.oracle.graal.python.runtime.IndirectCallData.BoundaryCallData;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.PythonContext.GetThreadStateNode;
import com.oracle.graal.python.runtime.PythonContext.PythonThreadState;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.object.PFactory;
import com.oracle.graal.python.runtime.sequence.storage.NativeSequenceStorage;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.TruffleLogger;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.InlineSupport.InlineTarget;
import com.oracle.truffle.api.dsl.InlineSupport.RequiredField;
import com.oracle.truffle.api.dsl.InlineSupport.StateField;
import com.oracle.truffle.api.dsl.NeverDefault;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.UnexpectedResultException;
import com.oracle.truffle.api.profiles.BranchProfile;
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
            return FromLongNodeGen.create();
        }

        public static FromLongNode getUncached() {
            return FromLongNodeGen.getUncached();
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
            return FromUInt32NodeGen.create();
        }

        public static FromUInt32Node getUncached() {
            return FromUInt32NodeGen.getUncached();
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
            return ToInt64NodeGen.create();
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
            return ToInt32NodeGen.create();
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

        @TruffleBoundary(allowInlining = true)
        public static long executeUncached(Object object) {
            Object promoted = EnsurePythonObjectNode.executeUncached(PythonContext.get(null), object, false);
            return PythonToNativeNode.executeLongUncached(promoted);
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
            return ToPythonStringNodeGen.create();
        }

        public static ToPythonStringNode getUncached() {
            return ToPythonStringNodeGen.getUncached();
        }
    }

    /** Enum of all slot wrapper functions in {@code typeobject.c}. */
    public enum PExternalFunctionWrapper implements NativeCExtSymbol {
        GETATTR(GetAttrFuncRootNode.class, ExternalFunctionSignature.GETATTRFUNC),
        SETATTR(SetAttrFuncRootNode.class, ExternalFunctionSignature.SETATTRFUNC),

        RICHCMP(RichCmpFuncRootNode.class, ExternalFunctionSignature.RICHCMPFUNC),

        // wrap_sq_setitem
        SQ_SETITEM(SetItemRootNode.class, ExternalFunctionSignature.SSIZEOBJARGPROC),

        // wrap_unaryfunc
        UNARYFUNC(MethUnaryFunc.class, ExternalFunctionSignature.UNARYFUNC),

        // wrap_binaryfunc
        BINARYFUNC(MethBinaryRoot.class, ExternalFunctionSignature.BINARYFUNC),

        // wrap_binaryfunc_l
        BINARYFUNC_L(MethBinaryRoot.class, ExternalFunctionSignature.BINARYFUNC),

        // wrap_binaryfunc_r
        BINARYFUNC_R(MethBinaryRoot.class, ExternalFunctionSignature.BINARYFUNC),

        // wrap_ternaryfunc
        TERNARYFUNC(MethTernaryFuncRoot.class, ExternalFunctionSignature.TERNARYFUNC, 1),

        // wrap_ternaryfunc_r
        TERNARYFUNC_R(MethTernaryFuncRoot.class, ExternalFunctionSignature.TERNARYFUNC, 1),

        // richcmp_lt
        LT(MethRichcmpOpRootNode.class, ExternalFunctionSignature.RICHCMPFUNC),

        // richcmp_le
        LE(MethRichcmpOpRootNode.class, ExternalFunctionSignature.RICHCMPFUNC),

        // richcmp_eq
        EQ(MethRichcmpOpRootNode.class, ExternalFunctionSignature.RICHCMPFUNC),

        // richcmp_ne
        NE(MethRichcmpOpRootNode.class, ExternalFunctionSignature.RICHCMPFUNC),

        // richcmp_gt
        GT(MethRichcmpOpRootNode.class, ExternalFunctionSignature.RICHCMPFUNC),

        // richcmp_ge
        GE(MethRichcmpOpRootNode.class, ExternalFunctionSignature.RICHCMPFUNC),

        // wrap_next
        ITERNEXT(IterNextFuncRootNode.class, ExternalFunctionSignature.UNARYFUNC),

        // wrap_inquirypred
        INQUIRYPRED(MethInquiryRoot.class, ExternalFunctionSignature.INQUIRY),

        // wrap_sq_delitem
        SQ_DELITEM(SqDelItemRootNode.class, ExternalFunctionSignature.SSIZEOBJARGPROC),

        // wrap_sq_item
        SQ_ITEM(GetItemRootNode.class, ExternalFunctionSignature.SSIZEARGFUNC),

        // wrap_init
        INIT(MethInitRoot.class, ExternalFunctionSignature.INITPROC),

        // wrap_hashfunc
        HASHFUNC(MethLenfuncRoot.class, ExternalFunctionSignature.HASHFUNC),

        // wrap_call
        CALL(MethInitRoot.class, ExternalFunctionSignature.TERNARYFUNC),

        // wrap_setattr
        SETATTRO(SetAttrOFuncRootNode.class, ExternalFunctionSignature.SETATTROFUNC),

        DESCR_GET(DescrGetRootNode.class, ExternalFunctionSignature.DESCRGETFUNC, 1),

        // wrap_descr_set
        DESCR_SET(MethDescrSetRoot.class, ExternalFunctionSignature.DESCRSETFUNC),

        // wrap_lenfunc
        LENFUNC(MethLenfuncRoot.class, ExternalFunctionSignature.LENFUNC),

        // wrap_objobjproc
        OBJOBJPROC(MethObjObjProcRoot.class, ExternalFunctionSignature.OBJOBJPROC),

        // wrap_objobjargproc
        OBJOBJARGPROC(MethObjObjArgProcRoot.class, ExternalFunctionSignature.OBJOBJARGPROC),

        // tp_new_wrapper
        NEW(MethNewRoot.class, ExternalFunctionSignature.NEWFUNC),

        // wrap_delitem
        MP_DELITEM(MpDelItemRootNode.class, ExternalFunctionSignature.OBJOBJARGPROC),

        // wrap_descr_delete
        DESCR_DELETE(DescrDeleteRootNode.class, ExternalFunctionSignature.DESCRSETFUNC),

        // wrap_delattr
        DELATTRO(DelAttrRootNode.class, ExternalFunctionSignature.SETATTROFUNC),

        INDEXARGFUNC(IndexArgFuncRootNode.class, ExternalFunctionSignature.SSIZEARGFUNC),

        GETTER(GetterRoot.class, ExternalFunctionSignature.GETTER),
        SETTER(SetterRoot.class, ExternalFunctionSignature.SETTER),

        // TRAVERSEPROC(null, Int, PyObject, Pointer, Pointer);
        TRAVERSEPROC(null, null);

        final Class<? extends WrapperDescriptorRoot> rootNodeClass;

        public final ExternalFunctionSignature signature;
        public final int numDefaults;

        PExternalFunctionWrapper(Class<? extends WrapperDescriptorRoot> rootNodeClass, ExternalFunctionSignature signature) {
            this(rootNodeClass, signature, 0);
        }

        PExternalFunctionWrapper(Class<? extends WrapperDescriptorRoot> rootNodeClass, ExternalFunctionSignature signature, int numDefaults) {
            this.rootNodeClass = rootNodeClass;
            this.signature = signature;
            this.numDefaults = numDefaults;
        }

        @TruffleBoundary
        static RootCallTarget getOrCreateCallTarget(PExternalFunctionWrapper sig, PythonLanguage language, TruffleString name) {
            return language.createCachedExternalFunWrapperCallTarget(l -> WrapperDescriptorRootNodesGen.create(l, name, sig), sig.rootNodeClass, sig, name, true, false);
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

        @Override
        public String getName() {
            return name();
        }

        @Override
        public TruffleString getTsName() {
            throw CompilerDirectives.shouldNotReachHere();
        }

        @Override
        public ArgDescriptor getReturnValue() {
            return signature.returnValue;
        }

        @Override
        public ArgDescriptor[] getArguments() {
            return signature.arguments;
        }
    }

    /**
     * A marker annotation used to denote root nodes that perform external function invocation. The
     * annotated elements need to be extendable and are expected to have an abstract method
     * {@code protected abstract <returnType> invokeExternalFunction(VirtualFrame frame, PythonContext context, NfiBoundFunction boundFunction, <arg0Type>, <arg1Type>, ..., <argNType>)}
     * where the {@code returnType} matches the {@link ExternalFunctionSignature#returnValue} Java
     * type and same for the arguments {@link ExternalFunctionSignature#arguments}.
     */
    @Retention(RetentionPolicy.SOURCE)
    @Target(ElementType.TYPE)
    public @interface CApiWrapperDescriptor {
        PExternalFunctionWrapper[] value();
    }

    /**
     * A marker annotation used to denote root nodes that perform external function invocation. The
     * annotated elements need to be extendable and are expected to have an abstract method
     * {@code protected abstract <returnType> invokeExternalFunction(VirtualFrame frame, PythonContext context, NfiBoundFunction boundFunction, <arg0Type>, <arg1Type>, ..., <argNType>)}
     * where the {@code returnType} matches the {@link ExternalFunctionSignature#returnValue} Java
     * type and same for the arguments {@link ExternalFunctionSignature#arguments}.
     */
    @Retention(RetentionPolicy.SOURCE)
    @Target(ElementType.METHOD)
    public @interface InvokeExternalFunction {
        ExternalFunctionSignature value();

        Class<?> retConversion() default long.class;

        Class<?>[] argConversions();
    }

    private static Signature createSignature(boolean takesVarKeywordArgs, int varArgIndex, TruffleString[] parameters, boolean checkEnclosingType, boolean hidden) {
        return new Signature(-1, takesVarKeywordArgs, varArgIndex, parameters, KEYWORDS_HIDDEN_CALLABLE, checkEnclosingType, T_EMPTY_STRING, hidden);
    }

    private static Signature createSignatureWithClosure(boolean takesVarKeywordArgs, int varArgIndex, TruffleString[] parameters, boolean checkEnclosingType, boolean hidden) {
        return new Signature(-1, takesVarKeywordArgs, varArgIndex, parameters, KEYWORDS_HIDDEN_CALLABLE_AND_CLOSURE, checkEnclosingType, T_EMPTY_STRING, hidden);
    }

    public abstract static class WrapperBaseRoot extends PRootNode {
        @Child private CalleeContext calleeContext;
        @Child private ReadIndexedArgumentNode readSelfNode;
        @Child private ReadIndexedArgumentNode readCallableNode;
        @Child private EnsurePythonObjectNode ensurePythonObjectNode;

        protected final TruffleString name;

        WrapperBaseRoot(PythonLanguage language, TruffleString name, boolean isStatic) {
            super(language);
            CompilerAsserts.neverPartOfCompilation();
            this.name = name;
            if (!isStatic) {
                readSelfNode = ReadIndexedArgumentNode.create(0);
            }
        }

        protected abstract Object readArgumentsAndInvokeExternalFunction(VirtualFrame frame, NfiBoundFunction boundFunction);

        @Override
        public final Object execute(VirtualFrame frame) {
            if (calleeContext == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                assert readCallableNode == null;
                assert calleeContext == null;
                // we insert a hidden argument at the end of the positional arguments
                int hiddenArg = getSignature().getParameterIds().length;
                readCallableNode = insert(ReadIndexedArgumentNode.create(hiddenArg));
                calleeContext = insert(CalleeContext.create());
            }
            calleeContext.enter(frame, this);
            try {
                Object callable = readCallableNode.execute(frame);
                if (!(callable instanceof NfiBoundFunction boundFunction)) {
                    throw CompilerDirectives.shouldNotReachHere();
                }
                return readArgumentsAndInvokeExternalFunction(frame, boundFunction);
            } finally {
                calleeContext.exit(frame, this);
            }
        }

        final Object ensurePythonObject(Object object) {
            if (ensurePythonObjectNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                ensurePythonObjectNode = insert(EnsurePythonObjectNode.create());
            }
            return ensurePythonObjectNode.execute(PythonContext.get(this), object, false);
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
        @Child private GetThreadStateNode getThreadStateNode;
        @Child private NativeToPythonReturnNode nativeToPythonReturnNode;
        @Child private PyObjectCheckFunctionResultNode checkResultNode;

        final CApiTiming timing;

        MethodDescriptorRoot(PythonLanguage language, TruffleString name, boolean isStatic, MethodDescriptorWrapper wrapper) {
            super(language, name, isStatic);
            assert wrapper.returnValue == PyObjectReturn;
            this.timing = CApiTiming.create(true, name);
        }

        final GetThreadStateNode ensureGetThreadStateNode() {
            if (getThreadStateNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                getThreadStateNode = insert(GetThreadStateNode.create());
            }
            return getThreadStateNode;
        }

        final Object nativeToPython(PythonContext context, long lresult) {
            if (nativeToPythonReturnNode == null || checkResultNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                nativeToPythonReturnNode = insert(NativeToPythonReturnNode.create());
                checkResultNode = insert(PyObjectCheckFunctionResultNodeGen.create());
            }
            return checkResultNode.execute(context, name, nativeToPythonReturnNode.executeRaw(lresult));
        }
    }

    /**
     * Base class for all native {@link PythonBuiltinClassType#WrapperDescriptor} (CPython type
     * {@code PyWrapperDescr_Type}) functions. Those are used for the slot wrapper functions of C
     * function type {@code wrapperfunc}.
     */
    public abstract static class WrapperDescriptorRoot extends WrapperBaseRoot {

        private final BranchProfile exceptionProfile = BranchProfile.create();

        WrapperDescriptorRoot(PythonLanguage language, TruffleString name, @SuppressWarnings("unused") PExternalFunctionWrapper wrapper) {
            super(language, name, false);
        }

        protected final void transformExceptionFromNative() {
            transformExceptionFromNative(true);
        }

        protected final void transformExceptionFromNative(boolean strict) {
            exceptionProfile.enter();
            transformExceptionFromNative(PythonContext.get(this), strict);
            if (strict) {
                throw CompilerDirectives.shouldNotReachHere();
            }
        }

        @TruffleBoundary
        private void transformExceptionFromNative(PythonContext context, boolean strict) {
            PythonThreadState threadState = GetThreadStateNode.getUncached().executeCached(context);
            TransformExceptionFromNativeNode.executeUncached(threadState, name, true, strict);
        }
    }

    /**
     * Base class for wrapper functions that return an object (i.e. {@code PyObject*}).
     */
    public abstract static class ObjectWrapperDescriptorRoot extends WrapperDescriptorRoot {

        @Child private NativeToPythonReturnNode nativeToPythonReturnNode;

        ObjectWrapperDescriptorRoot(PythonLanguage language, TruffleString name, PExternalFunctionWrapper wrapper) {
            super(language, name, wrapper);
            assert wrapper.signature.returnValue == PyObjectReturn;
        }

        final NativeToPythonReturnNode ensureNativeToPythonReturnNode() {
            if (nativeToPythonReturnNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                nativeToPythonReturnNode = insert(NativeToPythonReturnNode.create());
            }
            return nativeToPythonReturnNode;
        }

        final Object returnNativeObjectToPython(long lresult) {
            Object result = ensureNativeToPythonReturnNode().executeRaw(lresult);
            if (result == PNone.NO_VALUE) {
                transformExceptionFromNative();
            }
            return result;
        }
    }

    public static final class MethVarargsRoot extends PyCFunctionRootNode {
        private static final Signature SIGNATURE = createSignature(false, 1, tsArray("self"), true, true);
        @Child private ReadVarArgsNode readVarargsNode;
        @Child private PythonToNativeNode argsTupleToNativeNode;
        @Child private CreateNativeArgsTupleNode createNativeArgsTupleNode;
        @Child private ReleaseNativeArgsTupleNode freeNode;

        @CompilationFinal private boolean seenNativeArgsTupleStorage;

        public MethVarargsRoot(PythonLanguage language, TruffleString name, boolean isStatic, MethodDescriptorWrapper provider) {
            super(language, name, isStatic, provider);
            this.readVarargsNode = ReadVarArgsNode.create(SIGNATURE.varArgsPArgumentsIndex());
            this.argsTupleToNativeNode = PythonToNativeNode.create();
            this.createNativeArgsTupleNode = CreateNativeArgsTupleNodeGen.create();
            this.freeNode = ReleaseNativeArgsTupleNodeGen.create();
        }

        @Override
        protected Object readArgumentsAndInvokeExternalFunction(VirtualFrame frame, NfiBoundFunction boundFunction) {
            PythonContext context = PythonContext.get(this);
            Object self = readSelf(frame);
            Object[] args = readVarargsNode.execute(frame);

            PTuple managedArgsTuple;
            long argsTuplePtr;
            if (seenNativeArgsTupleStorage) {
                managedArgsTuple = null;
                argsTuplePtr = createNativeArgsTupleNode.execute(context, args);
            } else {
                managedArgsTuple = PFactory.createTuple(context.getLanguage(this), args);
                assert EnsurePythonObjectNode.doesNotNeedPromotion(managedArgsTuple);
                argsTuplePtr = argsTupleToNativeNode.executeLong(managedArgsTuple);
            }

            try {
                return invokeExternalFunction(frame, boundFunction, self, argsTuplePtr);
            } finally {
                assert managedArgsTuple != null || seenNativeArgsTupleStorage;
                boolean hadNativeSequenceStorage = postprocessArgsTuple(managedArgsTuple, argsTuplePtr, args, freeNode);
                if (!seenNativeArgsTupleStorage || hadNativeSequenceStorage) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    seenNativeArgsTupleStorage = true;
                }
                Reference.reachabilityFence(args);
            }
        }

        public static boolean postprocessArgsTuple(PTuple managedArgsTuple, long argsTuplePtr, Object[] args,
                        ReleaseNativeArgsTupleNode releaseNativeArgsTupleNode) {
            if (managedArgsTuple == null) {
                releaseNativeArgsTupleNode.execute(argsTuplePtr, args);
                return false;
            }
            /*
             * Note: 'seenNativeArgsTupleStorage' is not necessarily 'false' in this case because a
             * recursive invocation may have already set it to 'true' in the meantime.
             */
            assert !(managedArgsTuple.getSequenceStorage() instanceof NativeSequenceStorage nativeSequenceStorage) || nativeSequenceStorage.hasReference();
            return managedArgsTuple.getSequenceStorage() instanceof NativeSequenceStorage;
        }

        @Override
        public Signature getSignature() {
            return SIGNATURE;
        }
    }

    static final class MethKeywordsRoot extends MethodDescriptorRoot {
        private static final Signature SIGNATURE = createSignature(true, 1, tsArray("self"), true, true);

        @Child private ReadVarArgsNode readVarargsNode;
        @Child private ReadVarKeywordsNode readKwargsNode;
        @Child private CreateNativeArgsTupleNode createNativeArgsTupleNode;
        @Child private ReleaseNativeArgsTupleNode freeNode;
        @Child private CalleeContext calleeContext;
        @Child private BoundaryCallData boundaryCallData;

        @Child private PythonToNativeNode selfToNativeNode;
        @Child private PythonToNativeNode argsToNativeNode;
        @Child private PythonToNativeNode kwargsToNativeNode;

        @CompilationFinal private boolean seenNativeArgsTupleStorage;

        public MethKeywordsRoot(PythonLanguage language, TruffleString name, boolean isStatic, MethodDescriptorWrapper provider) {
            super(language, name, isStatic, provider);
        }

        @Override
        protected Object readArgumentsAndInvokeExternalFunction(VirtualFrame frame, NfiBoundFunction boundFunction) {
            if (calleeContext == null || boundaryCallData == null || selfToNativeNode == null || argsToNativeNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                createNodes();
            }
            PythonContext context = PythonContext.get(this);

            Object self = readSelf(frame);
            assert EnsurePythonObjectNode.doesNotNeedPromotion(self);

            Object[] args = readVarargsNode.execute(frame);
            PTuple managedArgsTuple;
            long argsTuplePtr;
            if (seenNativeArgsTupleStorage) {
                managedArgsTuple = null;
                argsTuplePtr = createNativeArgsTupleNode.execute(context, args);
            } else {
                managedArgsTuple = PFactory.createTuple(context.getLanguage(this), args);
                assert EnsurePythonObjectNode.doesNotNeedPromotion(managedArgsTuple);
                argsTuplePtr = argsToNativeNode.executeLong(managedArgsTuple);
            }

            PKeyword[] kwargs = readKwargsNode.execute(frame);
            Object kwargsDict = kwargs.length > 0 ? PFactory.createDict(context.getLanguage(), kwargs) : PNone.NO_VALUE;
            assert EnsurePythonObjectNode.doesNotNeedPromotion(kwargsDict);

            try {
                long l = ExternalFunctionInvoker.invokePYCFUNCTION_WITH_KEYWORDS(frame, timing, context.ensureNfiContext(), boundaryCallData, ensureGetThreadStateNode().executeCached(context),
                                boundFunction, selfToNativeNode.executeLong(self), argsTuplePtr, kwargsToNativeNode.executeLong(kwargsDict));
                return nativeToPython(context, l);
            } finally {
                Reference.reachabilityFence(self);
                boolean hadNativeSequenceStorage = MethVarargsRoot.postprocessArgsTuple(managedArgsTuple, argsTuplePtr, args, freeNode);
                if (!seenNativeArgsTupleStorage && hadNativeSequenceStorage) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    seenNativeArgsTupleStorage = true;
                }
                Reference.reachabilityFence(args);
                Reference.reachabilityFence(kwargsDict);
            }
        }

        private void createNodes() {
            CompilerAsserts.neverPartOfCompilation();
            readVarargsNode = insert(ReadVarArgsNode.create(SIGNATURE.varArgsPArgumentsIndex()));
            readKwargsNode = insert(ReadVarKeywordsNode.create(SIGNATURE.varKeywordsPArgumentsIndex()));
            createNativeArgsTupleNode = insert(CreateNativeArgsTupleNodeGen.create());
            freeNode = insert(ReleaseNativeArgsTupleNodeGen.create());
            calleeContext = insert(CalleeContext.create());
            boundaryCallData = insert(BoundaryCallData.createFor(this));
            selfToNativeNode = insert(PythonToNativeNode.create());
            argsToNativeNode = insert(PythonToNativeNode.create());
            kwargsToNativeNode = insert(PythonToNativeNode.create());
        }

        @Override
        public Signature getSignature() {
            return SIGNATURE;
        }
    }

    /** Implements semantics of {@code typeobject.c: wrap_unaryfunc}. */
    @CApiWrapperDescriptor(value = UNARYFUNC)
    abstract static class MethUnaryFunc extends ObjectWrapperDescriptorRoot {
        private static final Signature SIGNATURE = createSignature(false, -1, tsArray("self"), true, false);

        protected MethUnaryFunc(PythonLanguage lang, TruffleString name, PExternalFunctionWrapper provider) {
            super(lang, name, provider);
        }

        @InvokeExternalFunction(value = ExternalFunctionSignature.UNARYFUNC, argConversions = {PythonToNativeNode.class})
        protected abstract long invokeExternalFunction(VirtualFrame frame, NfiBoundFunction boundFunction, Object self);

        @Override
        protected Object readArgumentsAndInvokeExternalFunction(VirtualFrame frame, NfiBoundFunction boundFunction) {
            Object self = readSelf(frame);
            return returnNativeObjectToPython(invokeExternalFunction(frame, boundFunction, self));
        }

        @Override
        public Signature getSignature() {
            return SIGNATURE;
        }
    }

    public abstract static class MethNewOrCallRoot extends ObjectWrapperDescriptorRoot {
        private static final Signature SIGNATURE = MethKeywordsRoot.SIGNATURE;
        @Child ReadVarArgsNode readVarargsNode;
        @Child ReadVarKeywordsNode readKwargsNode;
        @Child PythonToNativeNode argsTupleToNativeNode;
        @Child CreateNativeArgsTupleNode createNativeArgsTupleNode;
        @Child ReleaseNativeArgsTupleNode freeNode;

        @CompilationFinal boolean seenNativeArgsTupleStorage;

        public MethNewOrCallRoot(PythonLanguage language, TruffleString name, PExternalFunctionWrapper provider) {
            super(language, name, provider);
            this.readVarargsNode = ReadVarArgsNode.create(SIGNATURE.varArgsPArgumentsIndex());
            this.readKwargsNode = ReadVarKeywordsNode.create(SIGNATURE.varKeywordsPArgumentsIndex());
            this.argsTupleToNativeNode = PythonToNativeNode.create();
            this.createNativeArgsTupleNode = CreateNativeArgsTupleNodeGen.create();
            this.freeNode = ReleaseNativeArgsTupleNodeGen.create();
        }

        @Override
        public Signature getSignature() {
            return SIGNATURE;
        }
    }

    @CApiWrapperDescriptor(value = NEW)
    abstract static class MethNewRoot extends MethNewOrCallRoot {

        public MethNewRoot(PythonLanguage language, TruffleString name, PExternalFunctionWrapper provider) {
            super(language, name, provider);
        }

        @InvokeExternalFunction(value = ExternalFunctionSignature.NEWFUNC, argConversions = {PythonToNativeNode.class, long.class, PythonToNativeNode.class})
        protected abstract long invokeExternalFunction(VirtualFrame frame, NfiBoundFunction boundFunction, Object self, long argsTuplePtr, Object kwds);

        @Override
        protected Object readArgumentsAndInvokeExternalFunction(VirtualFrame frame, NfiBoundFunction boundFunction) {
            PythonContext context = PythonContext.get(this);
            Object[] args = readVarargsNode.execute(frame);

            // TODO checks
            Object self = args[0];

            args = PythonUtils.arrayCopyOfRange(args, 1, args.length);
            PTuple managedArgsTuple;
            long argsTuplePtr;
            if (seenNativeArgsTupleStorage) {
                managedArgsTuple = null;
                argsTuplePtr = createNativeArgsTupleNode.execute(context, args);
            } else {
                managedArgsTuple = PFactory.createTuple(context.getLanguage(this), args);
                assert EnsurePythonObjectNode.doesNotNeedPromotion(managedArgsTuple);
                argsTuplePtr = argsTupleToNativeNode.executeLong(managedArgsTuple);
            }

            PKeyword[] kwargs = readKwargsNode.execute(frame);
            PythonLanguage language = getLanguage(PythonLanguage.class);
            Object kwargsDict = kwargs.length > 0 ? PFactory.createDict(language, kwargs) : PNone.NO_VALUE;

            try {
                return returnNativeObjectToPython(invokeExternalFunction(frame, boundFunction, self, argsTuplePtr, kwargsDict));
            } finally {
                boolean hadNativeSequenceStorage = MethVarargsRoot.postprocessArgsTuple(managedArgsTuple, argsTuplePtr, args, freeNode);
                if (!seenNativeArgsTupleStorage && hadNativeSequenceStorage) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    seenNativeArgsTupleStorage = true;
                }
                Reference.reachabilityFence(args);
            }
        }
    }

    @CApiWrapperDescriptor(value = CALL)
    abstract static class MethCallRoot extends MethNewOrCallRoot {

        public MethCallRoot(PythonLanguage language, TruffleString name, PExternalFunctionWrapper provider) {
            super(language, name, provider);
        }

        @InvokeExternalFunction(value = ExternalFunctionSignature.TERNARYFUNC, argConversions = {PythonToNativeNode.class, long.class, PythonToNativeNode.class})
        protected abstract long invokeExternalFunction(VirtualFrame frame, NfiBoundFunction boundFunction, Object self, long args, Object kwds);

        @Override
        protected Object readArgumentsAndInvokeExternalFunction(VirtualFrame frame, NfiBoundFunction boundFunction) {
            PythonContext context = PythonContext.get(this);

            Object self = readSelf(frame);

            Object[] args = readVarargsNode.execute(frame);
            PTuple managedArgsTuple;
            long argsTuplePtr;
            if (seenNativeArgsTupleStorage) {
                managedArgsTuple = null;
                argsTuplePtr = createNativeArgsTupleNode.execute(context, args);
            } else {
                managedArgsTuple = PFactory.createTuple(context.getLanguage(this), args);
                assert EnsurePythonObjectNode.doesNotNeedPromotion(managedArgsTuple);
                argsTuplePtr = argsTupleToNativeNode.executeLong(managedArgsTuple);
            }

            PKeyword[] kwargs = readKwargsNode.execute(frame);
            Object kwargsDict = kwargs.length > 0 ? PFactory.createDict(context.getLanguage(), kwargs) : PNone.NO_VALUE;

            try {
                return returnNativeObjectToPython(invokeExternalFunction(frame, boundFunction, self, argsTuplePtr, kwargsDict));
            } finally {
                boolean hadNativeSequenceStorage = MethVarargsRoot.postprocessArgsTuple(managedArgsTuple, argsTuplePtr, args, freeNode);
                if (!seenNativeArgsTupleStorage && hadNativeSequenceStorage) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    seenNativeArgsTupleStorage = true;
                }
                Reference.reachabilityFence(args);
            }
        }
    }

    @CApiWrapperDescriptor(value = INIT)
    abstract static class MethInitRoot extends WrapperDescriptorRoot {
        private static final Signature SIGNATURE = MethKeywordsRoot.SIGNATURE;

        @Child private ReadVarArgsNode readVarargsNode;
        @Child private ReadVarKeywordsNode readKwargsNode;
        @Child private PythonToNativeNode argsTupleToNativeNode;
        @Child private CreateNativeArgsTupleNode createNativeArgsTupleNode;
        @Child private ReleaseNativeArgsTupleNode freeNode;

        @CompilationFinal boolean seenNativeArgsTupleStorage;

        public MethInitRoot(PythonLanguage language, TruffleString name, PExternalFunctionWrapper provider) {
            super(language, name, provider);
            this.readVarargsNode = ReadVarArgsNode.create(SIGNATURE.varArgsPArgumentsIndex());
            this.readKwargsNode = ReadVarKeywordsNode.create(SIGNATURE.varKeywordsPArgumentsIndex());
            this.argsTupleToNativeNode = PythonToNativeNode.create();
            this.createNativeArgsTupleNode = CreateNativeArgsTupleNodeGen.create();
            this.freeNode = ReleaseNativeArgsTupleNodeGen.create();
        }

        @InvokeExternalFunction(value = ExternalFunctionSignature.INITPROC, retConversion = int.class, argConversions = {PythonToNativeNode.class, long.class, PythonToNativeNode.class})
        protected abstract int invokeExternalFunction(VirtualFrame frame, NfiBoundFunction boundFunction, Object self, long argsTuplePtr, Object kwds);

        @Override
        protected Object readArgumentsAndInvokeExternalFunction(VirtualFrame frame, NfiBoundFunction boundFunction) {
            PythonContext context = PythonContext.get(this);

            Object self = readSelf(frame);

            Object[] args = readVarargsNode.execute(frame);
            PTuple managedArgsTuple;
            long argsTuplePtr;
            if (seenNativeArgsTupleStorage) {
                managedArgsTuple = null;
                argsTuplePtr = createNativeArgsTupleNode.execute(context, args);
            } else {
                managedArgsTuple = PFactory.createTuple(context.getLanguage(this), args);
                assert EnsurePythonObjectNode.doesNotNeedPromotion(managedArgsTuple);
                argsTuplePtr = argsTupleToNativeNode.executeLong(managedArgsTuple);
            }

            PKeyword[] kwargs = readKwargsNode.execute(frame);
            Object kwargsDict = kwargs.length > 0 ? PFactory.createDict(context.getLanguage(), kwargs) : PNone.NO_VALUE;

            try {
                if (invokeExternalFunction(frame, boundFunction, self, argsTuplePtr, kwargsDict) < 0) {
                    transformExceptionFromNative();
                }
                return PNone.NONE;
            } finally {
                boolean hadNativeSequenceStorage = MethVarargsRoot.postprocessArgsTuple(managedArgsTuple, argsTuplePtr, args, freeNode);
                if (!seenNativeArgsTupleStorage && hadNativeSequenceStorage) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    seenNativeArgsTupleStorage = true;
                }
                Reference.reachabilityFence(args);
            }
        }

        @Override
        public Signature getSignature() {
            return SIGNATURE;
        }
    }

    /** Implements semantics of {@code typeobject.c: wrap_inquirypred}. */
    @CApiWrapperDescriptor(value = INQUIRYPRED)
    public abstract static class MethInquiryRoot extends WrapperDescriptorRoot {
        private static final Signature SIGNATURE = createSignature(false, -1, tsArray("self"), true, false);

        public MethInquiryRoot(PythonLanguage language, TruffleString name, PExternalFunctionWrapper provider) {
            super(language, name, provider);
        }

        @InvokeExternalFunction(value = ExternalFunctionSignature.INQUIRY, retConversion = int.class, argConversions = {PythonToNativeNode.class})
        protected abstract int invokeExternalFunction(VirtualFrame frame, NfiBoundFunction boundFunction, Object self);

        @Override
        protected Object readArgumentsAndInvokeExternalFunction(VirtualFrame frame, NfiBoundFunction boundFunction) {
            Object self = readSelf(frame);
            int result = invokeExternalFunction(frame, boundFunction, self);
            if (result == -1) {
                transformExceptionFromNative(false);
            }
            return result != 0;
        }

        @Override
        public Signature getSignature() {
            return SIGNATURE;
        }
    }

    static final class MethNoargsRoot extends PyCFunctionRootNode {
        private static final Signature SIGNATURE = createSignature(false, -1, tsArray("self"), true, true);

        public MethNoargsRoot(PythonLanguage language, TruffleString name, boolean isStatic, MethodDescriptorWrapper provider) {
            super(language, name, isStatic, provider);
        }

        @Override
        protected Object readArgumentsAndInvokeExternalFunction(VirtualFrame frame, NfiBoundFunction boundFunction) {
            Object self = readSelf(frame);
            assert EnsurePythonObjectNode.doesNotNeedPromotion(self);
            return invokeExternalFunction(frame, boundFunction, self, NULLPTR);
        }

        @Override
        public Signature getSignature() {
            return SIGNATURE;
        }
    }

    abstract static class PyCFunctionRootNode extends MethodDescriptorRoot {
        @Child private CalleeContext calleeContext;
        @Child private BoundaryCallData boundaryCallData;

        @Child private PythonToNativeNode selfToNativeNode;

        public PyCFunctionRootNode(PythonLanguage language, TruffleString name, boolean isStatic, MethodDescriptorWrapper provider) {
            super(language, name, isStatic, provider);
        }

        final Object invokeExternalFunction(VirtualFrame frame, NfiBoundFunction boundFunction, Object self, long arg) {
            assert EnsurePythonObjectNode.doesNotNeedPromotion(self);
            if (calleeContext == null || boundaryCallData == null || selfToNativeNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                createNodes();
            }
            PythonContext context = PythonContext.get(this);
            try {
                long l = ExternalFunctionInvoker.invokePYCFUNCTION(frame, timing, context.ensureNfiContext(), boundaryCallData, ensureGetThreadStateNode().executeCached(context), boundFunction,
                                selfToNativeNode.executeLong(self), arg);
                return nativeToPython(context, l);
            } finally {
                Reference.reachabilityFence(self);
            }
        }

        private void createNodes() {
            CompilerAsserts.neverPartOfCompilation();
            calleeContext = insert(CalleeContext.create());
            boundaryCallData = insert(BoundaryCallData.createFor(this));
            selfToNativeNode = insert(PythonToNativeNode.create());
        }
    }

    public static final class MethORoot extends PyCFunctionRootNode {
        private static final Signature SIGNATURE = createSignature(false, -1, tsArray("self", "arg"), true, true);
        @Child private ReadIndexedArgumentNode readArgNode;
        @Child private PythonToNativeNode argToNativeNode;

        public MethORoot(PythonLanguage language, TruffleString name, boolean isStatic, MethodDescriptorWrapper provider) {
            super(language, name, isStatic, provider);
            this.readArgNode = ReadIndexedArgumentNode.create(1);
            this.argToNativeNode = PythonToNativeNode.create();
        }

        @Override
        protected Object readArgumentsAndInvokeExternalFunction(VirtualFrame frame, NfiBoundFunction boundFunction) {
            Object self = readSelf(frame);
            Object arg = ensurePythonObject(readArgNode.execute(frame));
            try {
                return invokeExternalFunction(frame, boundFunction, self, argToNativeNode.executeLong(arg));
            } finally {
                Reference.reachabilityFence(arg);
            }
        }

        @Override
        public Signature getSignature() {
            return SIGNATURE;
        }
    }

    static final class MethFastcallWithKeywordsRoot extends MethodDescriptorRoot {
        /*
         * METH_KEYWORDS and METH_FASTCALL|METH_KEYWORDS have the same Python-level signature but
         * invoke a different C function signature.
         */
        private static final Signature SIGNATURE = MethKeywordsRoot.SIGNATURE;

        @Child private ReadVarArgsNode readVarargsNode;
        @Child private ReadVarKeywordsNode readKwargsNode;
        @Child private BoundaryCallData boundaryCallData;

        @Child private PythonToNativeNode argToNativeNode;
        @Child private PythonToNativeNode kwNamesToNativeNode;

        public MethFastcallWithKeywordsRoot(PythonLanguage language, TruffleString name, boolean isStatic, MethodDescriptorWrapper provider) {
            super(language, name, isStatic, provider);
        }

        @Override
        protected Object readArgumentsAndInvokeExternalFunction(VirtualFrame frame, NfiBoundFunction boundFunction) {
            if (readVarargsNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                assert readKwargsNode == null;
                assert boundaryCallData == null;
                assert argToNativeNode == null;
                assert kwNamesToNativeNode == null;
                createNodes();
            }
            PythonContext context = PythonContext.get(this);

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
                kwnamesTuple = PFactory.createTuple(context.getLanguage(), fastcallKwnames);
            }
            long nativeFastcallArgs = MethFastcallRoot.createFastcallArgsArray(fastcallArgs, argToNativeNode);

            try {
                long l = ExternalFunctionInvoker.invokePYCFUNCTION_FAST_WITH_KEYWORDS(frame, timing, context.ensureNfiContext(), boundaryCallData, ensureGetThreadStateNode().executeCached(context),
                                boundFunction, argToNativeNode.executeLong(self), nativeFastcallArgs, args.length, kwNamesToNativeNode.executeLong(kwnamesTuple));
                return nativeToPython(context, l);
            } finally {
                MethFastcallRoot.freeFastcallArgsArray(nativeFastcallArgs);
                Reference.reachabilityFence(self);
                Reference.reachabilityFence(fastcallArgs);
                Reference.reachabilityFence(kwnamesTuple);
            }
        }

        private void createNodes() {
            CompilerAsserts.neverPartOfCompilation();
            readVarargsNode = insert(ReadVarArgsNode.create(SIGNATURE.varArgsPArgumentsIndex()));
            readKwargsNode = insert(ReadVarKeywordsNode.create(SIGNATURE.varKeywordsPArgumentsIndex()));
            boundaryCallData = insert(BoundaryCallData.createFor(this));
            argToNativeNode = insert(PythonToNativeNode.create());
            kwNamesToNativeNode = insert(PythonToNativeNode.create());
        }

        @Override
        public Signature getSignature() {
            return SIGNATURE;
        }
    }

    static final class MethMethodRoot extends MethodDescriptorRoot {
        private static final Signature SIGNATURE = createSignature(true, 1, tsArray("self", "cls"), true, true);

        @Child private ReadIndexedArgumentNode readClsNode;
        @Child private ReadVarArgsNode readVarargsNode;
        @Child private ReadVarKeywordsNode readKwargsNode;

        @Child private BoundaryCallData boundaryCallData;
        @Child private PythonToNativeNode argToNativeNode;
        @Child private PythonToNativeNode kwNamesToNativeNode;

        public MethMethodRoot(PythonLanguage language, TruffleString name, boolean isStatic, MethodDescriptorWrapper provider) {
            super(language, name, isStatic, provider);
        }

        @Override
        protected Object readArgumentsAndInvokeExternalFunction(VirtualFrame frame, NfiBoundFunction boundFunction) {
            if (readClsNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                assert readVarargsNode == null;
                assert readKwargsNode == null;
                assert boundaryCallData == null;
                assert argToNativeNode == null;
                assert kwNamesToNativeNode == null;
                createNodes();
            }
            PythonContext context = PythonContext.get(this);

            Object self = readSelf(frame);
            assert EnsurePythonObjectNode.doesNotNeedPromotion(self);

            Object cls = readClsNode.execute(frame);
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
                kwnamesTuple = PFactory.createTuple(context.getLanguage(), fastcallKwnames);
            }
            long nativeFastcallArgs = MethFastcallRoot.createFastcallArgsArray(fastcallArgs, argToNativeNode);

            try {
                long l = ExternalFunctionInvoker.invokePYCMETHOD(frame, timing, context.ensureNfiContext(), boundaryCallData, ensureGetThreadStateNode().executeCached(context),
                                boundFunction, argToNativeNode.executeLong(self), argToNativeNode.executeLong(cls), nativeFastcallArgs, args.length, kwNamesToNativeNode.executeLong(kwnamesTuple));
                return nativeToPython(context, l);
            } finally {
                MethFastcallRoot.freeFastcallArgsArray(nativeFastcallArgs);
                Reference.reachabilityFence(self);
                Reference.reachabilityFence(fastcallArgs);
                Reference.reachabilityFence(kwnamesTuple);
            }
        }

        private void createNodes() {
            CompilerAsserts.neverPartOfCompilation();
            readClsNode = insert(ReadIndexedArgumentNode.create(1));
            readVarargsNode = insert(ReadVarArgsNode.create(SIGNATURE.varArgsPArgumentsIndex()));
            readKwargsNode = insert(ReadVarKeywordsNode.create(SIGNATURE.varKeywordsPArgumentsIndex()));
            boundaryCallData = insert(BoundaryCallData.createFor(this));
            argToNativeNode = insert(PythonToNativeNode.create());
            kwNamesToNativeNode = insert(PythonToNativeNode.create());
        }

        @Override
        public Signature getSignature() {
            return SIGNATURE;
        }
    }

    static final class MethFastcallRoot extends MethodDescriptorRoot {
        /*
         * METH_VARARGS and METH_FASTCALL have the same Python-level signature but invoke a
         * different C function signature.
         */
        private static final Signature SIGNATURE = MethVarargsRoot.SIGNATURE;

        @Child private ReadVarArgsNode readVarargsNode;
        @Child private BoundaryCallData boundaryCallData;

        @Child private PythonToNativeNode argToNativeNode;

        public MethFastcallRoot(PythonLanguage language, TruffleString name, boolean isStatic, MethodDescriptorWrapper provider) {
            super(language, name, isStatic, provider);
        }

        @Override
        protected Object readArgumentsAndInvokeExternalFunction(VirtualFrame frame, NfiBoundFunction boundFunction) {
            if (readVarargsNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                assert boundaryCallData == null;
                assert argToNativeNode == null;
                createNodes();
            }
            PythonContext context = PythonContext.get(this);

            Object self = readSelf(frame);
            assert EnsurePythonObjectNode.doesNotNeedPromotion(self);

            Object[] args = readVarargsNode.execute(frame);
            Object[] promotedArgs = new Object[args.length];
            for (int i = 0; i < args.length; i++) {
                promotedArgs[i] = ensurePythonObject(args[i]);
            }
            long argsArray = createFastcallArgsArray(promotedArgs, argToNativeNode);

            try {
                long l = ExternalFunctionInvoker.invokePYCFUNCTION_FAST(frame, timing, context.ensureNfiContext(), boundaryCallData, ensureGetThreadStateNode().executeCached(context),
                                boundFunction, argToNativeNode.executeLong(self), argsArray, promotedArgs.length);
                return nativeToPython(context, l);
            } finally {
                freeFastcallArgsArray(argsArray);
                Reference.reachabilityFence(self);
                Reference.reachabilityFence(promotedArgs);
            }
        }

        /**
         * Transforms an {@code Object[]} containing Python objects to a native
         * {@code PyObject *arr[]}. This will not create new {@code PyObject *} references (i.e.
         * refcount is not increased).
         */
        static long createFastcallArgsArray(Object[] data, PythonToNativeNode argToNativeNode) {
            if (data.length == 0) {
                return NULLPTR;
            }
            assert PythonContext.get(null).isNativeAccessAllowed();
            long ptr = NativeMemory.malloc((long) data.length * NativeMemory.POINTER_SIZE);
            for (int i = 0; i < data.length; i++) {
                assert EnsurePythonObjectNode.doesNotNeedPromotion(data[i]);
                NativeMemory.writePtrArrayElement(ptr, i, argToNativeNode.executeLong(data[i]));
            }
            return ptr;
        }

        static void freeFastcallArgsArray(long pointer) {
            if (pointer == NULLPTR) {
                return;
            }

            // TODO we currently don't implement immediate releases of native objects.
            assert PythonContext.get(null).isNativeAccessAllowed();
            NativeMemory.free(pointer);
        }

        private void createNodes() {
            CompilerAsserts.neverPartOfCompilation();
            readVarargsNode = insert(ReadVarArgsNode.create(SIGNATURE.varArgsPArgumentsIndex()));
            boundaryCallData = insert(BoundaryCallData.createFor(this));
            argToNativeNode = insert(PythonToNativeNode.create());
        }

        @Override
        public Signature getSignature() {
            return SIGNATURE;
        }
    }

    /** Implements semantics of {@code typeobject.c: wrap_indexargfunc}. */
    @CApiWrapperDescriptor(value = INDEXARGFUNC)
    public abstract static class IndexArgFuncRootNode extends ObjectWrapperDescriptorRoot {
        private static final Signature SIGNATURE = createSignature(false, -1, tsArray("self", "i"), true, false);
        @Child private ReadIndexedArgumentNode readINode;
        @Child private PyNumberAsSizeNode asSizeNode;

        IndexArgFuncRootNode(PythonLanguage language, TruffleString name, PExternalFunctionWrapper provider) {
            super(language, name, provider);
            this.readINode = ReadIndexedArgumentNode.create(1);
            this.asSizeNode = PyNumberAsSizeNode.create();
        }

        @InvokeExternalFunction(value = ExternalFunctionSignature.SSIZEARGFUNC, argConversions = {PythonToNativeNode.class, long.class})
        protected abstract long invokeExternalFunction(VirtualFrame frame, NfiBoundFunction boundFunction, Object self, long i);

        @Override
        protected Object readArgumentsAndInvokeExternalFunction(VirtualFrame frame, NfiBoundFunction boundFunction) {
            Object self = readSelf(frame);
            long i = asSizeNode.executeExactCached(frame, readINode.execute(frame));
            return returnNativeObjectToPython(invokeExternalFunction(frame, boundFunction, self, i));
        }

        @Override
        public Signature getSignature() {
            return SIGNATURE;
        }
    }

    /**
     * Wrapper root node for a get attribute function (C type {@code getattrfunc}).
     */
    @CApiWrapperDescriptor(value = GETATTR)
    public abstract static class GetAttrFuncRootNode extends ObjectWrapperDescriptorRoot {
        private static final TruffleLogger LOGGER = CApiContext.getLogger(GetAttrFuncRootNode.class);
        private static final Signature SIGNATURE = createSignature(false, -1, tsArray("self", "key"), true, false);
        @Child private ReadIndexedArgumentNode readArgNode;
        @Child private AsCharPointerNode asCharPointerNode;

        GetAttrFuncRootNode(PythonLanguage language, TruffleString name, PExternalFunctionWrapper provider) {
            super(language, name, provider);
            this.readArgNode = ReadIndexedArgumentNode.create(1);
            this.asCharPointerNode = AsCharPointerNodeGen.create();
        }

        @InvokeExternalFunction(value = ExternalFunctionSignature.GETATTRFUNC, argConversions = {PythonToNativeNode.class, long.class})
        protected abstract long invokeExternalFunction(VirtualFrame frame, NfiBoundFunction boundFunction, Object self, long key);

        @Override
        protected Object readArgumentsAndInvokeExternalFunction(VirtualFrame frame, NfiBoundFunction boundFunction) {
            Object self = readSelf(frame);
            long key = asCharPointerNode.execute(readArgNode.execute(frame));
            try {
                return returnNativeObjectToPython(invokeExternalFunction(frame, boundFunction, self, key));
            } finally {
                if (LOGGER.isLoggable(Level.FINE)) {
                    LOGGER.fine(PythonUtils.formatJString("Freeing name (const char *)0x%x", key));
                }
                free(key);
            }
        }

        @Override
        public Signature getSignature() {
            return SIGNATURE;
        }
    }

    /**
     * Wrapper root node for a set attribute function (C type {@code setattrfunc}).
     */
    @CApiWrapperDescriptor(value = SETATTR)
    public abstract static class SetAttrFuncRootNode extends ObjectWrapperDescriptorRoot {
        private static final TruffleLogger LOGGER = CApiContext.getLogger(SetAttrFuncRootNode.class);
        private static final Signature SIGNATURE = createSignature(false, -1, tsArray("self", "key", "value"), true, false);
        @Child private ReadIndexedArgumentNode readArg1Node;
        @Child private ReadIndexedArgumentNode readArg2Node;
        @Child private AsCharPointerNode asCharPointerNode;

        SetAttrFuncRootNode(PythonLanguage language, TruffleString name, PExternalFunctionWrapper provider) {
            super(language, name, provider);
            this.readArg1Node = ReadIndexedArgumentNode.create(1);
            this.readArg2Node = ReadIndexedArgumentNode.create(2);
            this.asCharPointerNode = AsCharPointerNodeGen.create();
        }

        @InvokeExternalFunction(value = ExternalFunctionSignature.SETATTRFUNC, retConversion = int.class, argConversions = {PythonToNativeNode.class, long.class, PythonToNativeNode.class})
        protected abstract int invokeExternalFunction(VirtualFrame frame, NfiBoundFunction boundFunction, Object self, long key, Object value);

        @Override
        protected Object readArgumentsAndInvokeExternalFunction(VirtualFrame frame, NfiBoundFunction boundFunction) {
            Object self = readSelf(frame);
            long key = asCharPointerNode.execute(readArg1Node.execute(frame));
            Object value = ensurePythonObject(readArg2Node.execute(frame));

            try {
                return returnNativeObjectToPython(invokeExternalFunction(frame, boundFunction, self, key, value));
            } finally {
                if (LOGGER.isLoggable(Level.FINE)) {
                    LOGGER.fine(PythonUtils.formatJString("Freeing name (const char *)0x%x", key));
                }
                free(key);
            }
        }

        @Override
        public Signature getSignature() {
            return SIGNATURE;
        }
    }

    /** Implements semantics of {@code typeobject.c: wrap_setattr} */
    @CApiWrapperDescriptor(value = SETATTRO)
    public abstract static class SetAttrOFuncRootNode extends WrapperDescriptorRoot {
        private static final Signature SIGNATURE = createSignature(false, -1, tsArray("self", "name", "value"), true, false);
        @Child private ReadIndexedArgumentNode readNameNode;
        @Child private ReadIndexedArgumentNode readValueNode;

        SetAttrOFuncRootNode(PythonLanguage language, TruffleString name, PExternalFunctionWrapper provider) {
            super(language, name, provider);
            this.readNameNode = ReadIndexedArgumentNode.create(1);
            this.readValueNode = ReadIndexedArgumentNode.create(2);
        }

        @InvokeExternalFunction(value = ExternalFunctionSignature.SETATTROFUNC, retConversion = int.class, argConversions = {PythonToNativeNode.class, PythonToNativeNode.class,
                        PythonToNativeNode.class})
        protected abstract int invokeExternalFunction(VirtualFrame frame, NfiBoundFunction boundFunction, Object self, Object name, Object value);

        @Override
        protected Object readArgumentsAndInvokeExternalFunction(VirtualFrame frame, NfiBoundFunction boundFunction) {
            Object self = readSelf(frame);
            assert EnsurePythonObjectNode.doesNotNeedPromotion(self);
            Object name = ensurePythonObject(readNameNode.execute(frame));
            Object value = ensurePythonObject(readValueNode.execute(frame));

            if (invokeExternalFunction(frame, boundFunction, self, name, value) < 0) {
                transformExceptionFromNative();
            }
            return PNone.NONE;
        }

        @Override
        public Signature getSignature() {
            return SIGNATURE;
        }
    }

    /**
     * Wrapper root node for a rich compare function (C type {@code richcmpfunc}). There is no
     * equivalent wrapper function in CPython but this is needed to be able to call
     * {@code tp_richcompare}.
     */
    @CApiWrapperDescriptor(value = RICHCMP)
    public abstract static class RichCmpFuncRootNode extends ObjectWrapperDescriptorRoot {
        private static final Signature SIGNATURE = createSignature(false, -1, tsArray("self", "other", "op"), true, false);
        @Child private ReadIndexedArgumentNode readOtherNode;
        @Child private ReadIndexedArgumentNode readOpNode;
        @Child private ConvertPIntToPrimitiveNode asSsizeTNode;

        RichCmpFuncRootNode(PythonLanguage language, TruffleString name, PExternalFunctionWrapper provider) {
            super(language, name, provider);
            this.readOtherNode = ReadIndexedArgumentNode.create(1);
            this.readOpNode = ReadIndexedArgumentNode.create(2);
            this.asSsizeTNode = ConvertPIntToPrimitiveNodeGen.create();
        }

        @InvokeExternalFunction(value = ExternalFunctionSignature.RICHCMPFUNC, argConversions = {PythonToNativeNode.class, PythonToNativeNode.class, int.class})
        protected abstract long invokeExternalFunction(VirtualFrame frame, NfiBoundFunction boundFunction, Object self, Object name, int op);

        @Override
        protected Object readArgumentsAndInvokeExternalFunction(VirtualFrame frame, NfiBoundFunction boundFunction) {
            try {
                Object self = readSelf(frame);
                assert EnsurePythonObjectNode.doesNotNeedPromotion(self);
                Object arg1 = ensurePythonObject(readOtherNode.execute(frame));
                Object arg2 = ensurePythonObject(readOpNode.execute(frame));
                return returnNativeObjectToPython(invokeExternalFunction(frame, boundFunction, self, arg1, asSsizeTNode.executeIntCached(arg2, 1, Integer.BYTES)));
            } catch (UnexpectedResultException e) {
                throw CompilerDirectives.shouldNotReachHere();
            }
        }

        @Override
        public Signature getSignature() {
            return SIGNATURE;
        }
    }

    /** Implements semantics of {@code typeobject.c: wrap_sq_item}. */
    @CApiWrapperDescriptor(value = SQ_ITEM)
    public abstract static class GetItemRootNode extends ObjectWrapperDescriptorRoot {
        private static final Signature SIGNATURE = createSignature(false, -1, tsArray("self", "i"), true, false);
        @Child private ReadIndexedArgumentNode readArg1Node;
        @Child private GetIndexNode getIndexNode;

        GetItemRootNode(PythonLanguage language, TruffleString name, PExternalFunctionWrapper provider) {
            super(language, name, provider);
            this.readArg1Node = ReadIndexedArgumentNode.create(1);
            this.getIndexNode = GetIndexNode.create();
        }

        @InvokeExternalFunction(value = ExternalFunctionSignature.SSIZEARGFUNC, argConversions = {PythonToNativeNode.class, long.class})
        protected abstract long invokeExternalFunction(VirtualFrame frame, NfiBoundFunction boundFunction, Object self, long i);

        @Override
        protected Object readArgumentsAndInvokeExternalFunction(VirtualFrame frame, NfiBoundFunction boundFunction) {
            Object self = readSelf(frame);
            assert EnsurePythonObjectNode.doesNotNeedPromotion(self);
            Object arg1 = readArg1Node.execute(frame);
            return returnNativeObjectToPython(invokeExternalFunction(frame, boundFunction, self, getIndexNode.execute(self, arg1)));
        }

        @Override
        public Signature getSignature() {
            return SIGNATURE;
        }
    }

    /** Implements semantics of {@code typeobject.c: wrap_sq_setitem}. */
    @CApiWrapperDescriptor(value = SQ_SETITEM)
    public abstract static class SetItemRootNode extends WrapperDescriptorRoot {
        private static final Signature SIGNATURE = createSignature(false, -1, tsArray("self", "i", "value"), true, false);
        @Child private ReadIndexedArgumentNode readArg1Node;
        @Child private ReadIndexedArgumentNode readArg2Node;
        @Child private GetIndexNode getIndexNode;

        SetItemRootNode(PythonLanguage language, TruffleString name, PExternalFunctionWrapper provider) {
            super(language, name, provider);
            this.readArg1Node = ReadIndexedArgumentNode.create(1);
            this.readArg2Node = ReadIndexedArgumentNode.create(2);
            this.getIndexNode = GetIndexNode.create();
        }

        @InvokeExternalFunction(value = ExternalFunctionSignature.SSIZEOBJARGPROC, retConversion = int.class, argConversions = {PythonToNativeNode.class, long.class, PythonToNativeNode.class})
        protected abstract int invokeExternalFunction(VirtualFrame frame, NfiBoundFunction boundFunction, Object self, long i, Object value);

        @Override
        protected Object readArgumentsAndInvokeExternalFunction(VirtualFrame frame, NfiBoundFunction boundFunction) {
            Object self = readSelf(frame);
            Object arg1 = readArg1Node.execute(frame);
            Object arg2 = ensurePythonObject(readArg2Node.execute(frame));

            if (invokeExternalFunction(frame, boundFunction, self, (long) getIndexNode.execute(self, arg1), arg2) < 0) {
                transformExceptionFromNative();
            }
            return PNone.NONE;
        }

        @Override
        public Signature getSignature() {
            return SIGNATURE;
        }
    }

    /** Implements semantics of {@code typeobject.c: wrap_sq_delitem}. */
    @CApiWrapperDescriptor(value = SQ_DELITEM)
    public abstract static class SqDelItemRootNode extends WrapperDescriptorRoot {
        private static final Signature SIGNATURE = createSignature(false, -1, tsArray("self", "key"), true, false);
        @Child private ReadIndexedArgumentNode readKeyNode;
        @Child private GetIndexNode getIndexNode;

        SqDelItemRootNode(PythonLanguage language, TruffleString name, PExternalFunctionWrapper provider) {
            super(language, name, provider);
            this.readKeyNode = ReadIndexedArgumentNode.create(1);
            this.getIndexNode = GetIndexNode.create();
        }

        @InvokeExternalFunction(value = ExternalFunctionSignature.SSIZEOBJARGPROC, retConversion = int.class, argConversions = {PythonToNativeNode.class, long.class, PythonToNativeNode.class})
        protected abstract int invokeExternalFunction(VirtualFrame frame, NfiBoundFunction boundFunction, Object self, long key, Object value);

        @Override
        protected Object readArgumentsAndInvokeExternalFunction(VirtualFrame frame, NfiBoundFunction boundFunction) {
            Object self = readSelf(frame);
            Object key = readKeyNode.execute(frame);

            if (invokeExternalFunction(frame, boundFunction, self, getIndexNode.execute(self, key), PNone.NO_VALUE) < 0) {
                transformExceptionFromNative();
            }
            return PNone.NONE;
        }

        @Override
        public Signature getSignature() {
            return SIGNATURE;
        }
    }

    /**
     * Implements semantics of {@code typeobject.c:wrap_descr_get}
     */
    @CApiWrapperDescriptor(value = DESCR_GET)
    public abstract static class DescrGetRootNode extends ObjectWrapperDescriptorRoot {
        private static final Signature SIGNATURE = createSignature(false, -1, tsArray("self", "obj", "type"), true, false);
        @Child private ReadIndexedArgumentNode readObj;
        @Child private ReadIndexedArgumentNode readType;

        public DescrGetRootNode(PythonLanguage language, TruffleString name, PExternalFunctionWrapper provider) {
            super(language, name, provider);
            this.readObj = ReadIndexedArgumentNode.create(1);
            this.readType = ReadIndexedArgumentNode.create(2);
        }

        @InvokeExternalFunction(value = ExternalFunctionSignature.DESCRGETFUNC, argConversions = {PythonToNativeNode.class, PythonToNativeNode.class, PythonToNativeNode.class})
        protected abstract long invokeExternalFunction(VirtualFrame frame, NfiBoundFunction boundFunction, Object self, Object obj, Object type);

        @Override
        protected Object readArgumentsAndInvokeExternalFunction(VirtualFrame frame, NfiBoundFunction boundFunction) {
            Object self = readSelf(frame);
            Object obj = PythonUtils.normalizeNone(ConditionProfile.getUncached(), ensurePythonObject(readObj.execute(frame)));
            Object type = PythonUtils.normalizeNone(ConditionProfile.getUncached(), ensurePythonObject(readType.execute(frame)));
            if (obj == PNone.NO_VALUE && type == PNone.NO_VALUE) {
                throw PRaiseNode.raiseStatic(this, PythonBuiltinClassType.TypeError, ErrorMessages.GET_NONE_NONE_IS_INVALID);
            }
            return returnNativeObjectToPython(invokeExternalFunction(frame, boundFunction, self, obj, type));
        }

        @Override
        public Signature getSignature() {
            return SIGNATURE;
        }
    }

    /** Implements semantics of {@code typeobject.c: wrap_descr_delete} */
    @CApiWrapperDescriptor(value = DESCR_DELETE)
    public abstract static class DescrDeleteRootNode extends WrapperDescriptorRoot {
        private static final Signature SIGNATURE = createSignature(false, -1, tsArray("self", "obj"), true, false);
        @Child private ReadIndexedArgumentNode readObj;

        public DescrDeleteRootNode(PythonLanguage language, TruffleString name, PExternalFunctionWrapper provider) {
            super(language, name, provider);
            this.readObj = ReadIndexedArgumentNode.create(1);
        }

        @InvokeExternalFunction(value = ExternalFunctionSignature.DESCRSETFUNC, retConversion = int.class, argConversions = {PythonToNativeNode.class, PythonToNativeNode.class,
                        PythonToNativeNode.class})
        protected abstract int invokeExternalFunction(VirtualFrame frame, NfiBoundFunction boundFunction, Object self, Object obj, Object value);

        @Override
        protected Object readArgumentsAndInvokeExternalFunction(VirtualFrame frame, NfiBoundFunction boundFunction) {
            Object self = readSelf(frame);
            Object obj = ensurePythonObject(readObj.execute(frame));
            if (invokeExternalFunction(frame, boundFunction, self, obj, PNone.NO_VALUE) < 0) {
                transformExceptionFromNative();
            }
            return PNone.NONE;
        }

        @Override
        public Signature getSignature() {
            return SIGNATURE;
        }
    }

    /** Implements semantics of {@code typeobject.c: wrap_delattr}. */
    @CApiWrapperDescriptor(value = DELATTRO)
    public abstract static class DelAttrRootNode extends WrapperDescriptorRoot {
        private static final Signature SIGNATURE = createSignature(false, -1, tsArray("self", "obj"), true, false);
        @Child private ReadIndexedArgumentNode readObj;

        public DelAttrRootNode(PythonLanguage language, TruffleString name, PExternalFunctionWrapper provider) {
            super(language, name, provider);
            this.readObj = ReadIndexedArgumentNode.create(1);
        }

        @InvokeExternalFunction(value = ExternalFunctionSignature.SETATTROFUNC, retConversion = int.class, //
                        argConversions = {PythonToNativeNode.class, PythonToNativeNode.class, PythonToNativeNode.class})
        protected abstract int invokeExternalFunction(VirtualFrame frame, NfiBoundFunction boundFunction, Object self, Object obj, Object value);

        @Override
        protected Object readArgumentsAndInvokeExternalFunction(VirtualFrame frame, NfiBoundFunction boundFunction) {
            Object self = readSelf(frame);
            Object obj = ensurePythonObject(readObj.execute(frame));
            // TODO: check if we need Carlo Verre hack here (see typeobject.c:hackcheck)
            if (invokeExternalFunction(frame, boundFunction, self, obj, PNone.NO_VALUE) < 0) {
                transformExceptionFromNative();
            }
            return PNone.NONE;
        }

        @Override
        public Signature getSignature() {
            return SIGNATURE;
        }
    }

    /** Implements semantics of {@code typeobject.c: wrap_delitem}. */
    @CApiWrapperDescriptor(value = MP_DELITEM)
    public abstract static class MpDelItemRootNode extends WrapperDescriptorRoot {
        private static final Signature SIGNATURE = createSignature(false, -1, tsArray("self", "key"), true, false);
        @Child private ReadIndexedArgumentNode readKeyNode;

        MpDelItemRootNode(PythonLanguage language, TruffleString name, PExternalFunctionWrapper provider) {
            super(language, name, provider);
            this.readKeyNode = ReadIndexedArgumentNode.create(1);
        }

        @InvokeExternalFunction(value = ExternalFunctionSignature.OBJOBJARGPROC, retConversion = int.class, //
                        argConversions = {PythonToNativeNode.class, PythonToNativeNode.class, PythonToNativeNode.class})
        protected abstract int invokeExternalFunction(VirtualFrame frame, NfiBoundFunction boundFunction, Object self, Object key, Object value);

        @Override
        protected Object readArgumentsAndInvokeExternalFunction(VirtualFrame frame, NfiBoundFunction boundFunction) {
            Object self = readSelf(frame);
            Object key = ensurePythonObject(readKeyNode.execute(frame));
            if (invokeExternalFunction(frame, boundFunction, self, key, PNone.NO_VALUE) < 0) {
                transformExceptionFromNative();
            }
            return PNone.NONE;
        }

        @Override
        public Signature getSignature() {
            return SIGNATURE;
        }
    }

    /**
     * Implements semantics of {@code typeobject.c: wrap_ternaryfunc} and
     * {@code typeobject.c: wrap_ternaryfunc_r}.
     */
    @CApiWrapperDescriptor(value = {TERNARYFUNC, TERNARYFUNC_R})
    public abstract static class MethTernaryFuncRoot extends ObjectWrapperDescriptorRoot {
        private static final Signature SIGNATURE = createSignature(false, -1, tsArray("self", "other", "third"), false, false);

        @Child private ReadIndexedArgumentNode readArg1Node;
        @Child private ReadIndexedArgumentNode readArg2Node;

        private final boolean reverse;

        MethTernaryFuncRoot(PythonLanguage language, TruffleString name, PExternalFunctionWrapper provider) {
            super(language, name, provider);
            this.readArg1Node = ReadIndexedArgumentNode.create(1);
            this.readArg2Node = ReadIndexedArgumentNode.create(2);
            this.reverse = provider == TERNARYFUNC_R;
        }

        @InvokeExternalFunction(value = ExternalFunctionSignature.TERNARYFUNC, argConversions = {PythonToNativeNode.class, PythonToNativeNode.class, PythonToNativeNode.class})
        protected abstract long invokeExternalFunction(VirtualFrame frame, NfiBoundFunction boundFunction, Object self, Object other, Object third);

        @Override
        protected Object readArgumentsAndInvokeExternalFunction(VirtualFrame frame, NfiBoundFunction boundFunction) {
            Object self = readSelf(frame);
            Object other = ensurePythonObject(readArg1Node.execute(frame));
            Object third = ensurePythonObject(readArg2Node.execute(frame));

            // normalize NO_VALUE to NONE
            if (third == PNone.NO_VALUE) {
                third = PNone.NONE;
            }

            // flip 'self' and 'other' in case of reverse operation
            Object first, second;
            if (reverse) {
                first = other;
                second = self;
            } else {
                first = self;
                second = other;
            }

            return returnNativeObjectToPython(invokeExternalFunction(frame, boundFunction, first, second, third));
        }

        @Override
        public Signature getSignature() {
            return SIGNATURE;
        }
    }

    /** Implements semantics of {@code typeobject.c: wrap_richcmpfunc} */
    @CApiWrapperDescriptor(value = {GT, GE, LE, LT, EQ, NE})
    public abstract static class MethRichcmpOpRootNode extends ObjectWrapperDescriptorRoot {
        private static final Signature SIGNATURE = createSignature(false, -1, tsArray("self", "other"), true, false);

        @Child private ReadIndexedArgumentNode readArgNode;

        private final int op;

        MethRichcmpOpRootNode(PythonLanguage language, TruffleString name, PExternalFunctionWrapper provider) {
            super(language, name, provider);
            this.readArgNode = ReadIndexedArgumentNode.create(1);
            this.op = getCompareOpCode(provider);
        }

        @InvokeExternalFunction(value = ExternalFunctionSignature.RICHCMPFUNC, argConversions = {PythonToNativeNode.class, PythonToNativeNode.class, int.class})
        protected abstract long invokeExternalFunction(VirtualFrame frame, NfiBoundFunction boundFunction, Object self, Object other, int op);

        @Override
        protected Object readArgumentsAndInvokeExternalFunction(VirtualFrame frame, NfiBoundFunction boundFunction) {
            Object self = readSelf(frame);
            Object other = ensurePythonObject(readArgNode.execute(frame));
            return returnNativeObjectToPython(invokeExternalFunction(frame, boundFunction, self, other, op));
        }

        @Override
        public Signature getSignature() {
            return SIGNATURE;
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
    }

    /** Implements semantics of {@code typeobject.c: wrap_next}. */
    @CApiWrapperDescriptor(value = ITERNEXT)
    public abstract static class IterNextFuncRootNode extends ObjectWrapperDescriptorRoot {

        @Child private CheckIterNextResultNode checkIterNextResultNode;

        IterNextFuncRootNode(PythonLanguage language, TruffleString name, PExternalFunctionWrapper provider) {
            super(language, name, provider);
            this.checkIterNextResultNode = CheckIterNextResultNodeGen.create();
        }

        @InvokeExternalFunction(value = ExternalFunctionSignature.UNARYFUNC, argConversions = {PythonToNativeNode.class})
        protected abstract long invokeExternalFunction(VirtualFrame frame, NfiBoundFunction boundFunction, Object self);

        @Override
        protected Object readArgumentsAndInvokeExternalFunction(VirtualFrame frame, NfiBoundFunction boundFunction) {
            Object self = readSelf(frame);
            long lresult = invokeExternalFunction(frame, boundFunction, self);
            PythonContext context = PythonContext.get(this);
            return checkIterNextResultNode.execute(context.getThreadState(context.getLanguage()), ensureNativeToPythonReturnNode().executeRaw(lresult));
        }

        @Override
        public Signature getSignature() {
            // same signature as a method without arguments (just the self)
            return MethNoargsRoot.SIGNATURE;
        }
    }

    /**
     * Wrapper root node for C function type {@code getter}.
     */
    @CApiWrapperDescriptor(value = GETTER)
    public abstract static class GetterRoot extends ObjectWrapperDescriptorRoot {
        private static final Signature SIGNATURE = createSignatureWithClosure(false, -1, tsArray("self"), true, false);

        @Child private ReadIndexedArgumentNode readClosureNode;

        public GetterRoot(PythonLanguage language, TruffleString name, PExternalFunctionWrapper provider) {
            super(language, name, provider);
        }

        @InvokeExternalFunction(value = ExternalFunctionSignature.GETTER, argConversions = {PythonToNativeNode.class, long.class})
        protected abstract long invokeExternalFunction(VirtualFrame frame, NfiBoundFunction boundFunction, Object self, long closure);

        @Override
        protected Object readArgumentsAndInvokeExternalFunction(VirtualFrame frame, NfiBoundFunction boundFunction) {
            Object self = readSelf(frame);
            if (readClosureNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                readClosureNode = insert(createReadClosureNode(SIGNATURE));
            }
            long closure = (long) readClosureNode.execute(frame);
            return returnNativeObjectToPython(invokeExternalFunction(frame, boundFunction, self, closure));
        }

        static ReadIndexedArgumentNode createReadClosureNode(Signature signature) {
            // we insert a hidden argument after the hidden callable arg
            int hiddenArg = signature.getParameterIds().length + 1;
            return ReadIndexedArgumentNode.create(hiddenArg);
        }

        @Override
        public Signature getSignature() {
            return SIGNATURE;
        }
    }

    /**
     * Wrapper root node for C function type {@code setter}.
     */
    @CApiWrapperDescriptor(value = SETTER)
    public abstract static class SetterRoot extends WrapperDescriptorRoot {
        private static final Signature SIGNATURE = createSignatureWithClosure(false, -1, tsArray("self", "value"), true, false);

        @Child private ReadIndexedArgumentNode readClosureNode;
        @Child private ReadIndexedArgumentNode readArgNode;

        public SetterRoot(PythonLanguage language, TruffleString name, PExternalFunctionWrapper provider) {
            super(language, name, provider);
        }

        @InvokeExternalFunction(value = ExternalFunctionSignature.SETTER, retConversion = int.class, argConversions = {PythonToNativeNode.class, PythonToNativeNode.class, long.class})
        protected abstract int invokeExternalFunction(VirtualFrame frame, NfiBoundFunction boundFunction, Object self, Object value, long closure);

        @Override
        protected Object readArgumentsAndInvokeExternalFunction(VirtualFrame frame, NfiBoundFunction boundFunction) {
            Object self = readSelf(frame);
            Object arg = ensurePythonObject(ensureReadArgNode().execute(frame));
            if (readClosureNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                readClosureNode = insert(GetterRoot.createReadClosureNode(SIGNATURE));
            }
            long closure = (long) readClosureNode.execute(frame);
            if (invokeExternalFunction(frame, boundFunction, self, arg, closure) < 0) {
                transformExceptionFromNative();
            }
            return PNone.NONE;
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

    /** Implements semantics of {@code typeobject.c: wrap_lenfunc} */
    @CApiWrapperDescriptor(value = {LENFUNC, HASHFUNC})
    public abstract static class MethLenfuncRoot extends WrapperDescriptorRoot {
        private static final Signature SIGNATURE = createSignature(false, -1, tsArray("self"), true, false);

        public MethLenfuncRoot(PythonLanguage language, TruffleString name, PExternalFunctionWrapper provider) {
            super(language, name, provider);
        }

        @InvokeExternalFunction(value = ExternalFunctionSignature.LENFUNC, argConversions = {PythonToNativeNode.class})
        protected abstract long invokeExternalFunction(VirtualFrame frame, NfiBoundFunction boundFunction, Object self);

        @Override
        protected Object readArgumentsAndInvokeExternalFunction(VirtualFrame frame, NfiBoundFunction boundFunction) {
            Object self = readSelf(frame);
            long result = invokeExternalFunction(frame, boundFunction, self);
            if (result == -1) {
                transformExceptionFromNative(false);
            }
            return result;
        }

        @Override
        public Signature getSignature() {
            return SIGNATURE;
        }
    }

    /** Implements semantics of {@code typeobject.c: wrap_objobjproc}. */
    @CApiWrapperDescriptor(value = OBJOBJPROC)
    public abstract static class MethObjObjProcRoot extends WrapperDescriptorRoot {
        private static final Signature SIGNATURE = createSignature(false, -1, tsArray("self", "value"), true, false);

        @Child private ReadIndexedArgumentNode readValueNode;

        MethObjObjProcRoot(PythonLanguage lang, TruffleString name, PExternalFunctionWrapper provider) {
            super(lang, name, provider);
            this.readValueNode = ReadIndexedArgumentNode.create(1);
        }

        @InvokeExternalFunction(value = ExternalFunctionSignature.OBJOBJPROC, retConversion = int.class, argConversions = {PythonToNativeNode.class, PythonToNativeNode.class})
        protected abstract int invokeExternalFunction(VirtualFrame frame, NfiBoundFunction boundFunction, Object self, Object value);

        @Override
        protected Object readArgumentsAndInvokeExternalFunction(VirtualFrame frame, NfiBoundFunction boundFunction) {
            Object self = readSelf(frame);
            Object value = ensurePythonObject(readValueNode.execute(frame));

            int result = invokeExternalFunction(frame, boundFunction, self, value);
            if (result == -1) {
                transformExceptionFromNative(false);
            }
            return result != 0;
        }

        @Override
        public Signature getSignature() {
            return SIGNATURE;
        }
    }

    /** Implements semantics of {@code typeobject.c: wrap_objobjargproc}. */
    @CApiWrapperDescriptor(value = OBJOBJARGPROC)
    public abstract static class MethObjObjArgProcRoot extends WrapperDescriptorRoot {
        private static final Signature SIGNATURE = createSignature(false, -1, tsArray("self", "key", "value"), true, false);

        @Child private ReadIndexedArgumentNode readKeyNode;
        @Child private ReadIndexedArgumentNode readValueNode;

        MethObjObjArgProcRoot(PythonLanguage lang, TruffleString name, PExternalFunctionWrapper provider) {
            super(lang, name, provider);
            this.readKeyNode = ReadIndexedArgumentNode.create(1);
            this.readValueNode = ReadIndexedArgumentNode.create(2);
        }

        @InvokeExternalFunction(value = ExternalFunctionSignature.OBJOBJARGPROC, retConversion = int.class, //
                        argConversions = {PythonToNativeNode.class, PythonToNativeNode.class, PythonToNativeNode.class})
        protected abstract int invokeExternalFunction(VirtualFrame frame, NfiBoundFunction boundFunction, Object self, Object key, Object value);

        @Override
        protected Object readArgumentsAndInvokeExternalFunction(VirtualFrame frame, NfiBoundFunction boundFunction) {
            Object self = readSelf(frame);
            Object key = ensurePythonObject(readKeyNode.execute(frame));
            Object value = ensurePythonObject(readValueNode.execute(frame));

            if (invokeExternalFunction(frame, boundFunction, self, key, value) == -1) {
                transformExceptionFromNative(false);
            }
            return PNone.NONE;
        }

        @Override
        public Signature getSignature() {
            return SIGNATURE;
        }
    }

    /**
     * Implements semantics of {@code typeobject.c: wrap_binaryfunc} and
     * {@code typeobject.c: wrap_binaryfunc_r}.
     */
    @CApiWrapperDescriptor(value = {BINARYFUNC, BINARYFUNC_L, BINARYFUNC_R})
    public abstract static class MethBinaryRoot extends ObjectWrapperDescriptorRoot {
        private static final Signature SIGNATURE = createSignature(false, -1, tsArray("self", "other"), true, false);

        @Child private ReadIndexedArgumentNode readOtherNode;

        private final boolean reverse;

        MethBinaryRoot(PythonLanguage lang, TruffleString name, PExternalFunctionWrapper provider) {
            super(lang, name, provider);
            this.readOtherNode = ReadIndexedArgumentNode.create(1);
            this.reverse = provider == BINARYFUNC_R;
        }

        @InvokeExternalFunction(value = ExternalFunctionSignature.BINARYFUNC, argConversions = {PythonToNativeNode.class, PythonToNativeNode.class})
        protected abstract long invokeExternalFunction(VirtualFrame frame, NfiBoundFunction boundFunction, Object self, Object other);

        @Override
        protected Object readArgumentsAndInvokeExternalFunction(VirtualFrame frame, NfiBoundFunction boundFunction) {
            Object self = readSelf(frame);
            Object other = ensurePythonObject(readOtherNode.execute(frame));

            // flip arguments 'self' and 'other' in case of reverse operation
            Object arg0, arg1;
            if (reverse) {
                arg0 = other;
                arg1 = self;
            } else {
                arg0 = self;
                arg1 = other;
            }

            return returnNativeObjectToPython(invokeExternalFunction(frame, boundFunction, arg0, arg1));
        }

        @Override
        public Signature getSignature() {
            return SIGNATURE;
        }
    }

    /** Implements semantics of {@code typeobject.c: wrap_descr_set} */
    @CApiWrapperDescriptor(value = DESCR_SET)
    public abstract static class MethDescrSetRoot extends WrapperDescriptorRoot {
        private static final Signature SIGNATURE = createSignature(false, -1, tsArray("self", "instance", "value"), true, false);
        @Child private ReadIndexedArgumentNode readInstanceNode;
        @Child private ReadIndexedArgumentNode readValueNode;

        MethDescrSetRoot(PythonLanguage language, TruffleString name, PExternalFunctionWrapper provider) {
            super(language, name, provider);
            this.readInstanceNode = ReadIndexedArgumentNode.create(1);
            this.readValueNode = ReadIndexedArgumentNode.create(2);
        }

        @InvokeExternalFunction(value = ExternalFunctionSignature.DESCRSETFUNC, retConversion = int.class, //
                        argConversions = {PythonToNativeNode.class, PythonToNativeNode.class, PythonToNativeNode.class})
        protected abstract int invokeExternalFunction(VirtualFrame frame, NfiBoundFunction boundFunction, Object self, Object instance, Object value);

        @Override
        protected Object readArgumentsAndInvokeExternalFunction(VirtualFrame frame, NfiBoundFunction boundFunction) {
            Object self = ensurePythonObject(readSelf(frame));
            Object instance = ensurePythonObject(readInstanceNode.execute(frame));
            Object value = ensurePythonObject(readValueNode.execute(frame));
            if (invokeExternalFunction(frame, boundFunction, self, instance, value) < 0) {
                transformExceptionFromNative();
            }
            return PNone.NONE;
        }

        @Override
        public Signature getSignature() {
            return SIGNATURE;
        }
    }

    /**
     * An inlined node-like object for keeping track of eager native allocation state bit. Should be
     * {@code @Cached} and passed into {@link CreateNativeArgsTupleNode#execute}. Then the
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
     * Allocates a native tuple and initializes it with the given elements. The elements will be
     * promoted to Python objects using {@link EnsurePythonObjectNode} (not promoting boxable
     * primitives) and written into the passed array.
     *
     * For performance reasons, this node takes some shortcuts: It allocates the native tuple using
     * {@link NativeCAPISymbol#FUN_PY_TYPE_GENERIC_ALLOC PyType_GenericAlloc} and initializes the
     * elements by writing them directly to {@link CFields#PyTupleObject__ob_item}.
     *
     * Also, this node will not register the tuple to the
     * {@link com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTransitions.HandleContext#nativeLookup
     * nativeLookup} and thus won't create a {@link PythonAbstractNativeObject native wrapper} for
     * it. In this way, the {@link ReleaseNativeArgsTupleNode release node} can detect if the tuple
     * escaped to managed.
     */
    @GenerateInline(false)
    @GenerateUncached
    public abstract static class CreateNativeArgsTupleNode extends Node {
        private static final CApiTiming TIMING_invokeTypeGenericAlloc = CApiTiming.create(true, "PyType_GenericAlloc");

        static final TruffleLogger LOGGER = CApiContext.getLogger(CreateNativeArgsTupleNode.class);

        public abstract long execute(PythonContext context, Object[] args);

        @Specialization
        static long doGeneric(PythonContext context, Object[] args,
                        @Bind Node inliningTarget,
                        @Cached EnsurePythonObjectNode materializePrimitiveNode,
                        @Cached PythonToNativeInternalNode pythonToNativeNode) {
            if (!context.isNativeAccessAllowed()) {
                throw CompilerDirectives.shouldNotReachHere();
            }

            int n = args.length;

            assert (GetTypeFlagsNode.executeUncached(PythonBuiltinClassType.PTuple) & TypeFlags.HAVE_GC) != 0;

            PythonBuiltinClass argsTupleClass = context.lookupType(PythonBuiltinClassType.PTuple);
            NfiBoundFunction callable = CApiContext.getNativeSymbol(inliningTarget, FUN_PY_TYPE_GENERIC_ALLOC);
            long op = ExternalFunctionInvoker.invokeTYPE_GENERIC_ALLOC(null, TIMING_invokeTypeGenericAlloc, context.ensureNfiContext(),
                            BoundaryCallData.getUncached(), context.getThreadState(context.getLanguage(inliningTarget)), callable,
                            pythonToNativeNode.execute(inliningTarget, argsTupleClass, false), n);

            long obItem = CStructAccess.getFieldPtr(op, CFields.PyTupleObject__ob_item);

            for (int i = 0; i < n; i++) {
                Object promoted = materializePrimitiveNode.execute(context, args[i], false);
                args[i] = promoted;
                writePtrArrayElement(obItem, i, pythonToNativeNode.execute(inliningTarget, promoted, true));
            }

            if (LOGGER.isLoggable(Level.FINE)) {
                LOGGER.fine(PythonUtils.formatJString("Created native args tuple %x (size=%d)", op, n));
            }

            assert !HandlePointerConverter.pointsToPyHandleSpace(op);
            assert PyObjectGCTrackNode.isGcTracked(op);
            return op;
        }
    }

    /**
     * Attempts to eagerly release the native tuple previously created with
     * {@link CreateNativeArgsTupleNode} if it did not escape.
     */
    @GenerateInline(false)
    @GenerateUncached
    public abstract static class ReleaseNativeArgsTupleNode extends Node {
        private static final CApiTiming TIMING_invokePyDealloc = CApiTiming.create(true, "_Py_Dealloc");

        public abstract void execute(long argsTuplePtr, Object[] managedArgs);

        @Specialization
        static void doGeneric(long argsTuplePtr, Object[] managedArgs,
                        @Bind Node inliningTarget) {
            assert !HandlePointerConverter.pointsToPyHandleSpace(argsTuplePtr);
            assert IsSameTypeNode.executeUncached(GetNativeClassNodeGen.getUncached().execute(null, new PythonAbstractNativeObject(argsTuplePtr)), PythonBuiltinClassType.PTuple);
            long refCount = CApiTransitions.readNativeRefCount(argsTuplePtr);
            assert refCount >= 1;
            if (refCount > 1) {
                if (LOGGER.isLoggable(Level.FINE)) {
                    LOGGER.fine(PythonUtils.formatJString("Native args tuple %x escaped (refcount = %d). Decref'ing by 1.", argsTuplePtr, refCount));
                }
                // The args tuple escaped. We cannot do anything and just give away our reference.
                CApiTransitions.subNativeRefCount(argsTuplePtr, 1);
                return;
            }
            assert readLongField(argsTuplePtr, CFields.PyVarObject__ob_size) == managedArgs.length;
            assert verifyElements(argsTuplePtr, managedArgs);
            if (LOGGER.isLoggable(Level.FINE)) {
                LOGGER.fine(PythonUtils.formatJString("Releasing native args tuple %x.", argsTuplePtr));
            }
            CApiTransitions.subNativeRefCount(argsTuplePtr, 1);
            PythonContext context = PythonContext.get(inliningTarget);
            NfiBoundFunction callable = CApiContext.getNativeSymbol(inliningTarget, FUN_PY_DEALLOC);
            ExternalFunctionInvoker.invokePY_DEALLOC(null, TIMING_invokePyDealloc, context.ensureNfiContext(),
                            BoundaryCallData.getUncached(), context.getThreadState(context.getLanguage(inliningTarget)), callable,
                            argsTuplePtr);
        }

        private static boolean verifyElements(long op, Object[] managedArgs) {
            long obItem = CStructAccess.getFieldPtr(op, CFields.PyTupleObject__ob_item);
            for (int i = 0; i < managedArgs.length; i++) {
                if (readPtrArrayElement(obItem, i) != PythonToNativeInternalNode.executeUncached(managedArgs[i], false)) {
                    return false;
                }
            }
            return true;
        }
    }

    // roughly equivalent to _Py_CheckFunctionResult in Objects/call.c
    @ImportStatic(PGuards.class)
    @GenerateUncached
    @GenerateInline(false)
    public abstract static class PyObjectCheckFunctionResultNode extends Node {

        public final Object execute(PythonContext context, TruffleString name, Object result) {
            PythonLanguage language = context.getLanguage(this);
            return execute(context.getThreadState(language), name, result);
        }

        public abstract Object execute(PythonThreadState threadState, TruffleString name, Object result);

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
    abstract static class CheckIterNextResultNode extends Node {

        abstract Object execute(PythonThreadState state, Object result);

        @Specialization
        static Object doGeneric(PythonThreadState state, Object result,
                        @Bind Node inliningTarget,
                        @Cached ReadAndClearNativeException readAndClearNativeException,
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
     *     Py_ssize_t res = func(...);
     *     if (res == -1 && PyErr_Occurred())
     *         return NULL;
     * </pre>
     *
     * This is the case for {@code wrap_delitem}, {@code wrap_objobjargproc},
     * {@code wrap_sq_delitem}, {@code wrap_sq_setitem}, {@code asdf}.
     */
    @GenerateUncached
    @GenerateInline
    @GenerateCached(false)
    public abstract static class CheckPrimitiveFunctionResultNode extends Node {
        public abstract long executeLong(Node inliningTarget, PythonThreadState threadState, TruffleString name, long result);

        @Specialization
        static long doLong(Node inliningTarget, PythonThreadState threadState, TruffleString name, long result,
                        @Cached TransformExceptionFromNativeNode transformExceptionFromNativeNode) {
            transformExceptionFromNativeNode.execute(inliningTarget, threadState, name, result == -1, false);
            return result;
        }
    }

}
