/*
 * Copyright (c) 2018, 2024, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.SystemError;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.TypeError;
import static com.oracle.graal.python.builtins.objects.cext.capi.PythonNativeWrapper.PythonAbstractObjectNativeWrapper.MANAGED_REFCNT;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.CharPtrAsTruffleString;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.InitResult;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.InquiryResult;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.Int;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.IterResult;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.Pointer;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PrimitiveResult32;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PrimitiveResult64;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PyObject;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PyObjectTransfer;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PyTypeObject;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.Py_ssize_t;
import static com.oracle.graal.python.util.PythonUtils.TS_ENCODING;
import static com.oracle.graal.python.util.PythonUtils.tsArray;
import static com.oracle.graal.python.util.PythonUtils.tsLiteral;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.PythonAbstractObject;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.ReleaseNativeWrapperNode;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodesFactory.AsCharPointerNodeGen;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodesFactory.ReleaseNativeWrapperNodeGen;
import com.oracle.graal.python.builtins.objects.cext.capi.ExternalFunctionNodesFactory.CreateArgsTupleNodeGen;
import com.oracle.graal.python.builtins.objects.cext.capi.ExternalFunctionNodesFactory.DefaultCheckFunctionResultNodeGen;
import com.oracle.graal.python.builtins.objects.cext.capi.ExternalFunctionNodesFactory.ExternalFunctionInvokeNodeGen;
import com.oracle.graal.python.builtins.objects.cext.capi.ExternalFunctionNodesFactory.MaterializePrimitiveNodeGen;
import com.oracle.graal.python.builtins.objects.cext.capi.ExternalFunctionNodesFactory.ReleaseNativeSequenceStorageNodeGen;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTiming;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTransitions;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTransitions.NativeToPythonNode;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTransitions.PythonToNativeNode;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTransitionsFactory.PythonToNativeNodeGen;
import com.oracle.graal.python.builtins.objects.cext.common.CExtCommonNodes.CheckFunctionResultNode;
import com.oracle.graal.python.builtins.objects.cext.common.CExtCommonNodes.ClearCurrentExceptionNode;
import com.oracle.graal.python.builtins.objects.cext.common.CExtCommonNodes.ConvertPIntToPrimitiveNode;
import com.oracle.graal.python.builtins.objects.cext.common.CExtCommonNodes.GetIndexNode;
import com.oracle.graal.python.builtins.objects.cext.common.CExtCommonNodes.TransformExceptionFromNativeNode;
import com.oracle.graal.python.builtins.objects.cext.common.CExtCommonNodesFactory.ConvertPIntToPrimitiveNodeGen;
import com.oracle.graal.python.builtins.objects.cext.common.CExtContext;
import com.oracle.graal.python.builtins.objects.cext.common.CExtToJavaNode;
import com.oracle.graal.python.builtins.objects.cext.common.CExtToNativeNode;
import com.oracle.graal.python.builtins.objects.cext.common.NativeCExtSymbol;
import com.oracle.graal.python.builtins.objects.cext.common.NativePointer;
import com.oracle.graal.python.builtins.objects.cext.structs.CStructAccess;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes.StorageToNativeNode;
import com.oracle.graal.python.builtins.objects.floats.PFloat;
import com.oracle.graal.python.builtins.objects.function.BuiltinMethodDescriptor;
import com.oracle.graal.python.builtins.objects.function.PArguments;
import com.oracle.graal.python.builtins.objects.function.PBuiltinFunction;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.builtins.objects.function.Signature;
import com.oracle.graal.python.builtins.objects.ints.PInt;
import com.oracle.graal.python.builtins.objects.str.PString;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlot;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlot.TpSlotCExtNative;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlot.TpSlotNative;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.PRootNode;
import com.oracle.graal.python.nodes.SpecialMethodNames;
import com.oracle.graal.python.nodes.argument.ReadIndexedArgumentNode;
import com.oracle.graal.python.nodes.argument.ReadVarArgsNode;
import com.oracle.graal.python.nodes.argument.ReadVarKeywordsNode;
import com.oracle.graal.python.nodes.call.special.CallVarargsMethodNode;
import com.oracle.graal.python.nodes.function.BuiltinFunctionRootNode;
import com.oracle.graal.python.nodes.interop.PForeignToPTypeNode;
import com.oracle.graal.python.nodes.object.IsForeignObjectNode;
import com.oracle.graal.python.nodes.truffle.PythonTypes;
import com.oracle.graal.python.nodes.util.CastToTruffleStringNode;
import com.oracle.graal.python.runtime.ExecutionContext.CalleeContext;
import com.oracle.graal.python.runtime.ExecutionContext.IndirectCallContext;
import com.oracle.graal.python.runtime.GilNode;
import com.oracle.graal.python.runtime.IndirectCallData;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.PythonContext.GetThreadStateNode;
import com.oracle.graal.python.runtime.PythonContext.PythonThreadState;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
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
import com.oracle.truffle.api.HostCompilerDirectives.InliningCutoff;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.TruffleLogger;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.NeverDefault;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.TypeSystemReference;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
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

    public static PKeyword[] createKwDefaults(Object callable) {
        assert InteropLibrary.getUncached().isExecutable(callable);
        return new PKeyword[]{new PKeyword(KW_CALLABLE, callable)};
    }

    public static PKeyword[] createKwDefaults(Object callable, Object closure) {
        assert InteropLibrary.getUncached().isExecutable(callable);
        return new PKeyword[]{new PKeyword(KW_CALLABLE, callable), new PKeyword(KW_CLOSURE, closure)};
    }

    public static Object tryGetHiddenCallable(PBuiltinFunction function) {
        if (function.getFunctionRootNode() instanceof MethodDescriptorRoot) {
            return getHiddenCallable(function.getKwDefaults());
        }
        return null;
    }

    public static Object getHiddenCallable(PKeyword[] kwDefaults) {
        if (kwDefaults.length >= KEYWORDS_HIDDEN_CALLABLE.length) {
            PKeyword kwDefault = kwDefaults[0];
            assert KW_CALLABLE.equalsUncached(kwDefault.getName(), TS_ENCODING) : "invalid keyword defaults";
            return kwDefault.getValue();
        }
        throw CompilerDirectives.shouldNotReachHere();
    }

    public abstract static class FinishArgNode extends PNodeWithContext {

        public abstract void execute(Object value);
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

        @Child private PythonToNativeNode toNative = PythonToNativeNodeGen.create();

        @Override
        public Object execute(Object object) {
            assert (object instanceof Double && Double.isNaN((double) object)) || !(object instanceof Number || object instanceof TruffleString);
            return toNative.execute(object);
        }
    }

    @GenerateUncached
    @GenerateInline(false)
    public abstract static class ToPythonStringNode extends CExtToJavaNode {
        @Specialization
        static Object doIt(Object object,
                        @Bind("this") Node inliningTarget,
                        @Cached CastToTruffleStringNode castToStringNode,
                        @Cached NativeToPythonNode nativeToPythonNode) {
            Object result = nativeToPythonNode.execute(object);
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
        DIRECT(1, PyObjectTransfer, PyObject, PyObject), // TODO: remove?
        FASTCALL(2, PyObjectTransfer, PyObject, Pointer, Py_ssize_t),
        FASTCALL_WITH_KEYWORDS(3, PyObjectTransfer, PyObject, Pointer, Py_ssize_t, PyObject),
        KEYWORDS(4, PyObjectTransfer, PyObject, PyObject, PyObject), // METH_VARARGS | METH_KEYWORDS
        VARARGS(5, PyObjectTransfer, PyObject, PyObject),            // METH_VARARGS
        NOARGS(6, PyObjectTransfer, PyObject, PyObject),             // METH_NOARGS
        O(7, PyObjectTransfer, PyObject, PyObject),                  // METH_O
        // METH_FASTCALL | METH_KEYWORDS | METH_METHOD:
        METHOD(8, PyObjectTransfer, PyObject, PyTypeObject, Pointer, Py_ssize_t, PyObject),
        ALLOC(10, PyObjectTransfer, PyTypeObject, Py_ssize_t),
        GETATTR(11, PyObjectTransfer, PyObject, CharPtrAsTruffleString),
        SETATTR(12, InitResult, PyObject, CharPtrAsTruffleString, PyObject),
        RICHCMP(13, PyObjectTransfer, PyObject, PyObject, Int),
        SETITEM(14, InitResult, PyObject, Py_ssize_t, PyObject),
        UNARYFUNC(15, PyObjectTransfer, PyObject),
        BINARYFUNC(16, PyObjectTransfer, PyObject, PyObject),
        BINARYFUNC_L(17, PyObjectTransfer, PyObject, PyObject),
        BINARYFUNC_R(18, PyObjectTransfer, PyObject, PyObject),
        TERNARYFUNC(19, PyObjectTransfer, PyObject, PyObject, PyObject),
        TERNARYFUNC_R(20, PyObjectTransfer, PyObject, PyObject, PyObject),
        LT(21, PyObjectTransfer, PyObject, PyObject, Int),
        LE(22, PyObjectTransfer, PyObject, PyObject, Int),
        EQ(23, PyObjectTransfer, PyObject, PyObject, Int),
        NE(24, PyObjectTransfer, PyObject, PyObject, Int),
        GT(25, PyObjectTransfer, PyObject, PyObject, Int),
        GE(26, PyObjectTransfer, PyObject, PyObject, Int),
        ITERNEXT(27, IterResult, PyObject),
        INQUIRY(28, InquiryResult, PyObject),
        DELITEM(29, defaults(1), Int, PyObject, Py_ssize_t, PyObject),
        GETITEM(30, PyObjectTransfer, PyObject, Py_ssize_t),
        GETTER(31, PyObjectTransfer, PyObject, Pointer),
        SETTER(32, InitResult, PyObject, PyObject, Pointer),
        INITPROC(33, InitResult, PyObject, PyObject, PyObject),
        HASHFUNC(34, PrimitiveResult64, PyObject),
        CALL(35, PyObjectTransfer, PyObject, PyObject, PyObject),
        SETATTRO(36, InitResult, PyObject, PyObject, PyObject),
        DESCR_GET(37, defaults(1), PyObjectTransfer, PyObject, PyObject, PyObject),
        DESCR_SET(38, InitResult, PyObject, PyObject, PyObject),
        LENFUNC(39, PrimitiveResult64, PyObject),
        OBJOBJPROC(40, InquiryResult, PyObject, PyObject),
        OBJOBJARGPROC(41, PrimitiveResult32, PyObject, PyObject, PyObject),
        NEW(42, PyObjectTransfer, PyObject, PyObject, PyObject),
        MP_DELITEM(43, PrimitiveResult32, PyObject, PyObject, PyObject),
        TP_STR(44, PyObjectTransfer, PyObject),
        TP_REPR(45, PyObjectTransfer, PyObject),
        DESCR_DELETE(46, InitResult, PyObject, PyObject, PyObject), // the last one is always NULL
        DELATTRO(47, InitResult, PyObject, PyObject, PyObject), // the last one is always NULL
        SSIZE_ARG(48, PyObjectTransfer, PyObject, Py_ssize_t);

        private static int defaults(int x) {
            return x;
        }

        @CompilationFinal(dimensions = 1) private static final PExternalFunctionWrapper[] VALUES = values();
        @CompilationFinal(dimensions = 1) private static final PExternalFunctionWrapper[] BY_ID = new PExternalFunctionWrapper[50];

        public final String signature;
        public final ArgDescriptor returnValue;
        public final ArgDescriptor[] arguments;
        public final int numDefaults;

        PExternalFunctionWrapper(int value, int numDefaults, ArgDescriptor returnValue, ArgDescriptor... arguments) {
            this.value = value;
            this.returnValue = returnValue;
            this.arguments = arguments;

            StringBuilder s = new StringBuilder("(");
            for (int i = 0; i < arguments.length; i++) {
                s.append(i == 0 ? "" : ",");
                s.append(arguments[i].getNFISignature());
            }
            s.append("):").append(returnValue.getNFISignature());
            this.signature = s.toString();
            this.numDefaults = numDefaults;
        }

        PExternalFunctionWrapper(int value, ArgDescriptor returnValue, ArgDescriptor... arguments) {
            this(value, 0, returnValue, arguments);
        }

        private final int value;

        static {
            for (var e : VALUES) {
                assert BY_ID[e.value] == null;
                BY_ID[e.value] = e;
            }
        }

        static PExternalFunctionWrapper fromValue(int value) {
            return value >= 0 && value < BY_ID.length ? BY_ID[value] : null;
        }

        static PExternalFunctionWrapper fromMethodFlags(int flags) {
            if (CExtContext.isMethNoArgs(flags)) {
                return NOARGS;
            } else if (CExtContext.isMethO(flags)) {
                return O;
            } else if (CExtContext.isMethVarargsWithKeywords(flags)) {
                return KEYWORDS;
            } else if (CExtContext.isMethVarargs(flags)) {
                return VARARGS;
            } else if (CExtContext.isMethMethod(flags)) {
                return METHOD;
            } else if (CExtContext.isMethFastcallWithKeywords(flags)) {
                return FASTCALL_WITH_KEYWORDS;
            } else if (CExtContext.isMethFastcall(flags)) {
                return FASTCALL;
            }
            throw CompilerDirectives.shouldNotReachHere("illegal method flags");
        }

        @TruffleBoundary
        static RootCallTarget getOrCreateCallTarget(PExternalFunctionWrapper sig, PythonLanguage language, TruffleString name, boolean doArgAndResultConversion, boolean isStatic) {
            Class<? extends PRootNode> nodeKlass;
            Function<PythonLanguage, RootNode> rootNodeFunction;
            switch (sig) {
                case ALLOC:
                case SSIZE_ARG:
                    nodeKlass = AllocFuncRootNode.class;
                    rootNodeFunction = doArgAndResultConversion ? l -> new AllocFuncRootNode(l, name, sig) : l -> new AllocFuncRootNode(l, name);
                    break;
                case DIRECT:
                case DESCR_SET:
                case LENFUNC:
                case HASHFUNC:
                case SETATTRO:
                case OBJOBJPROC:
                case OBJOBJARGPROC:
                case UNARYFUNC:
                case BINARYFUNC:
                case BINARYFUNC_L:
                case TP_STR:
                case TP_REPR:
                    /*
                     * If no conversion is requested, this means we directly call a managed function
                     * (without argument conversion). Null indicates this
                     */
                    if (!doArgAndResultConversion) {
                        return null;
                    }
                    nodeKlass = MethDirectRoot.class;
                    rootNodeFunction = l -> MethDirectRoot.create(language, name, sig);
                    break;
                case CALL:
                case INITPROC:
                case KEYWORDS:
                case NEW:
                    /*
                     * If no conversion is requested, this means we directly call a managed function
                     * (without argument conversion). Null indicates this
                     */
                    if (!doArgAndResultConversion) {
                        return null;
                    }
                    nodeKlass = MethKeywordsRoot.class;
                    rootNodeFunction = l -> new MethKeywordsRoot(l, name, isStatic, sig);
                    break;
                case VARARGS:
                    nodeKlass = MethVarargsRoot.class;
                    rootNodeFunction = doArgAndResultConversion ? l -> new MethVarargsRoot(l, name, isStatic, sig) : l -> new MethVarargsRoot(l, name, isStatic);
                    break;
                case INQUIRY:
                    nodeKlass = MethInquiryRoot.class;
                    rootNodeFunction = doArgAndResultConversion ? l -> new MethInquiryRoot(l, name, isStatic, sig) : l -> new MethInquiryRoot(l, name, isStatic);
                    break;
                case NOARGS:
                    nodeKlass = MethNoargsRoot.class;
                    rootNodeFunction = doArgAndResultConversion ? l -> new MethNoargsRoot(l, name, isStatic, sig) : l -> new MethNoargsRoot(l, name, isStatic);
                    break;
                case O:
                    nodeKlass = MethORoot.class;
                    rootNodeFunction = doArgAndResultConversion ? l -> new MethORoot(l, name, isStatic, sig) : l -> new MethORoot(l, name, isStatic);
                    break;
                case FASTCALL:
                    nodeKlass = MethFastcallRoot.class;
                    rootNodeFunction = doArgAndResultConversion ? l -> new MethFastcallRoot(l, name, isStatic, sig) : l -> new MethFastcallRoot(l, name, isStatic);
                    break;
                case FASTCALL_WITH_KEYWORDS:
                    nodeKlass = MethFastcallWithKeywordsRoot.class;
                    rootNodeFunction = doArgAndResultConversion ? l -> new MethFastcallWithKeywordsRoot(l, name, isStatic, sig) : l -> new MethFastcallWithKeywordsRoot(l, name, isStatic);
                    break;
                case METHOD:
                    nodeKlass = MethMethodRoot.class;
                    rootNodeFunction = doArgAndResultConversion ? l -> new MethMethodRoot(l, name, isStatic, sig) : l -> new MethMethodRoot(l, name, isStatic);
                    break;
                case GETATTR:
                    nodeKlass = GetAttrFuncRootNode.class;
                    rootNodeFunction = doArgAndResultConversion ? l -> new GetAttrFuncRootNode(l, name, sig) : l -> new GetAttrFuncRootNode(l, name);
                    break;
                case SETATTR:
                    nodeKlass = SetAttrFuncRootNode.class;
                    rootNodeFunction = doArgAndResultConversion ? l -> new SetAttrFuncRootNode(l, name, sig) : l -> new SetAttrFuncRootNode(l, name);
                    break;
                case DESCR_GET:
                    nodeKlass = DescrGetRootNode.class;
                    rootNodeFunction = doArgAndResultConversion ? l -> new DescrGetRootNode(l, name, sig) : l -> new DescrGetRootNode(l, name);
                    break;
                case DESCR_DELETE:
                    nodeKlass = DescrGetRootNode.class;
                    rootNodeFunction = doArgAndResultConversion ? l -> new DescrDeleteRootNode(l, name, sig) : l -> new DescrDeleteRootNode(l, name);
                    break;
                case DELATTRO:
                    nodeKlass = DelAttrRootNode.class;
                    rootNodeFunction = doArgAndResultConversion ? l -> new DelAttrRootNode(l, name, sig) : l -> new DelAttrRootNode(l, name);
                    break;
                case RICHCMP:
                    nodeKlass = RichCmpFuncRootNode.class;
                    rootNodeFunction = doArgAndResultConversion ? l -> new RichCmpFuncRootNode(l, name, sig) : l -> new RichCmpFuncRootNode(l, name);
                    break;
                case SETITEM:
                case DELITEM:
                    nodeKlass = SetItemRootNode.class;
                    rootNodeFunction = doArgAndResultConversion ? l -> new SetItemRootNode(l, name, sig) : l -> new SetItemRootNode(l, name);
                    break;
                case GETITEM:
                    nodeKlass = GetItemRootNode.class;
                    rootNodeFunction = doArgAndResultConversion ? l -> new GetItemRootNode(l, name, sig) : l -> new GetItemRootNode(l, name);
                    break;
                case BINARYFUNC_R:
                    nodeKlass = MethReverseRootNode.class;
                    rootNodeFunction = doArgAndResultConversion ? l -> new MethReverseRootNode(l, name, sig) : l -> new MethReverseRootNode(l, name);
                    break;
                case TERNARYFUNC:
                    nodeKlass = MethPowRootNode.class;
                    rootNodeFunction = doArgAndResultConversion ? l -> new MethPowRootNode(l, name, sig) : l -> new MethPowRootNode(l, name);
                    break;
                case TERNARYFUNC_R:
                    nodeKlass = MethRPowRootNode.class;
                    rootNodeFunction = doArgAndResultConversion ? l -> new MethRPowRootNode(l, name, sig) : l -> new MethRPowRootNode(l, name);
                    break;
                case GT:
                case GE:
                case LE:
                case LT:
                case EQ:
                case NE:
                    nodeKlass = MethRichcmpOpRootNode.class;
                    int op = getCompareOpCode(sig);
                    rootNodeFunction = doArgAndResultConversion ? l -> new MethRichcmpOpRootNode(l, name, sig, op) : l -> new MethRichcmpOpRootNode(l, name, op);
                    break;
                case ITERNEXT:
                    nodeKlass = IterNextFuncRootNode.class;
                    rootNodeFunction = doArgAndResultConversion ? l -> new IterNextFuncRootNode(l, name, sig) : l -> new IterNextFuncRootNode(l, name);
                    break;
                case GETTER:
                    /*
                     * If no conversion is requested, this means we directly call a managed function
                     * (without argument conversion). Null indicates this
                     */
                    if (!doArgAndResultConversion) {
                        return null;
                    }
                    nodeKlass = GetterRoot.class;
                    rootNodeFunction = l -> new GetterRoot(l, name, sig);
                    break;
                case SETTER:
                    /*
                     * If no conversion is requested, this means we directly call a managed function
                     * (without argument conversion). Null indicates this
                     */
                    if (!doArgAndResultConversion) {
                        return null;
                    }
                    nodeKlass = SetterRoot.class;
                    rootNodeFunction = l -> new SetterRoot(l, name, sig);
                    break;
                case MP_DELITEM:
                    nodeKlass = MpDelItemRootNode.class;
                    rootNodeFunction = doArgAndResultConversion ? l -> new MpDelItemRootNode(l, name, sig) : l -> new MpDelItemRootNode(l, name);
                    break;
                default:
                    throw CompilerDirectives.shouldNotReachHere();
            }
            return language.createCachedCallTarget(rootNodeFunction, nodeKlass, sig, name, doArgAndResultConversion);
        }

        public static PBuiltinFunction createWrapperFunction(TruffleString name, Object callable, Object enclosingType, int flags, int sig,
                        PythonLanguage language, PythonObjectFactory factory, boolean doArgAndResultConversion) {
            return createWrapperFunction(name, callable, enclosingType, flags, PExternalFunctionWrapper.fromValue(sig),
                            language, factory, doArgAndResultConversion);
        }

        /**
         * Creates a built-in function for a specific signature. This built-in function also does
         * appropriate argument and result conversion and calls the provided callable.
         *
         * @param language The Python language object.
         * @param sig The wrapper/signature ID as defined in {@link PExternalFunctionWrapper}.
         * @param name The name of the method.
         * @param callable A reference denoting executable code. Currently, there are three
         *            representations for that: (1) a native function pointer, (2) a
         *            {@link RootCallTarget}, and (3) a {@link BuiltinMethodDescriptor}.
         * @param enclosingType The type the function belongs to (needed for checking of
         *            {@code self}).
         * @param factory Just an instance of {@link PythonObjectFactory} to create the function
         *            object.
         * @return A {@link PBuiltinFunction} implementing the semantics of the specified slot
         *         wrapper.
         */
        @TruffleBoundary
        public static PBuiltinFunction createWrapperFunction(TruffleString name, Object callable, Object enclosingType, int flags, PExternalFunctionWrapper sig, PythonLanguage language,
                        PythonObjectFactory factory, boolean doArgAndResultConversion) {
            LOGGER.finer(() -> PythonUtils.formatJString("ExternalFunctions.createWrapperFunction(%s, %s)", name, callable));
            assert !isClosurePointer(PythonContext.get(null), callable, InteropLibrary.getUncached(callable));
            if (flags < 0) {
                flags = 0;
            }
            int numDefaults = -1;
            PKeyword[] kwDefaults;
            RootCallTarget callTarget;
            TpSlot slot = null;
            if (callable instanceof RootCallTarget) {
                callTarget = (RootCallTarget) callable;
                /*
                 * Special case for built-in functions:
                 *
                 * Built-in functions may appear as {@link CExtContext#METH_VARARGS} but we
                 * implement them as nodes where the specializations have a fixed arity and then
                 * sometimes allow optional arguments (specified with {@link
                 * Builtin#minNumOfPositionalArgs()}, {@link Builtin#maxNumOfPositionalArgs()}, and
                 * similar). This is usually not a problem because if the built-in function is
                 * called via the function object, this will provide the correct number of default
                 * values. In this case, the call target of the built-in function will be called
                 * directly. So, we need to manually provide the number of default values (which is
                 * {@link PNone#NO_VALUE} to indicate that the argument is missing).
                 */
                if (callTarget.getRootNode() instanceof BuiltinFunctionRootNode builtinFunctionRootNode) {
                    numDefaults = PythonBuiltins.numDefaults(builtinFunctionRootNode.getBuiltin());
                }
                kwDefaults = PKeyword.EMPTY_KEYWORDS;
            } else if (callable instanceof BuiltinMethodDescriptor builtinMethodDescriptor) {
                /*
                 * If we see a built-in method descriptor here, it was originally retrieved by a
                 * slot lookup. This means, the slot was already properly registered and therefore
                 * also its call target.
                 */
                callTarget = language.getDescriptorCallTarget(builtinMethodDescriptor);
                // again: special case for built-in functions
                numDefaults = PythonBuiltins.numDefaults(builtinMethodDescriptor.getBuiltinAnnotation());
                kwDefaults = PKeyword.EMPTY_KEYWORDS;
            } else {
                callTarget = getOrCreateCallTarget(sig, language, name, doArgAndResultConversion, CExtContext.isMethStatic(flags));
                if (callTarget == null) {
                    return null;
                }

                // ensure that 'callable' is executable via InteropLibrary
                Object boundCallable = CExtContext.ensureExecutable(callable, sig);
                kwDefaults = ExternalFunctionNodes.createKwDefaults(boundCallable);
                slot = TpSlotNative.createCExtSlot(boundCallable);
            }

            // generate default values for positional args (if necessary)
            if (numDefaults == -1) {
                numDefaults = sig.numDefaults;
            }
            Object[] defaults = PBuiltinFunction.generateDefaults(numDefaults);

            Object type = (enclosingType == PNone.NO_VALUE || SpecialMethodNames.T___NEW__.equalsUncached(name, TS_ENCODING)) ? null : enclosingType;
            // TODO(fa): this should eventually go away
            switch (sig) {
                case NOARGS:
                case O:
                case VARARGS:
                case KEYWORDS:
                case FASTCALL:
                case FASTCALL_WITH_KEYWORDS:
                case METHOD:
                    return factory.createBuiltinFunction(name, type, defaults, kwDefaults, flags, callTarget);
            }
            return factory.createWrapperDescriptor(name, type, defaults, kwDefaults, flags, callTarget, slot, sig);
        }

        /**
         * {@link #createWrapperFunction(TruffleString, Object, Object, int, PExternalFunctionWrapper, PythonLanguage, PythonObjectFactory, boolean)}.
         */
        @TruffleBoundary
        public static PBuiltinFunction createWrapperFunction(TruffleString name, TpSlotCExtNative slot, Object enclosingType, PExternalFunctionWrapper sig, PythonLanguage language,
                        PythonObjectFactory factory) {
            RootCallTarget callTarget = getOrCreateCallTarget(sig, language, name, true, false);
            if (callTarget == null) {
                return null;
            }
            var kwDefaults = ExternalFunctionNodes.createKwDefaults(slot.getCallable());
            Object[] defaults = PBuiltinFunction.generateDefaults(sig.numDefaults);
            return factory.createWrapperDescriptor(name, enclosingType, defaults, kwDefaults, 0, callTarget, slot, sig);
        }

        private static boolean isClosurePointer(PythonContext context, Object callable, InteropLibrary lib) {
            if (lib.isPointer(callable)) {
                try {
                    Object delegate = context.getCApiContext().getClosureDelegate(lib.asPointer(callable));
                    return delegate instanceof PBuiltinFunction;
                } catch (UnsupportedMessageException e) {
                    throw CompilerDirectives.shouldNotReachHere(e);
                }
            }
            return false;
        }

        private static int getCompareOpCode(PExternalFunctionWrapper sig) {
            // op codes for binary comparisons (defined in 'object.h')
            switch (sig) {
                case LT:
                    return 0;
                case LE:
                    return 1;
                case EQ:
                    return 2;
                case NE:
                    return 3;
                case GT:
                    return 4;
                case GE:
                    return 5;
            }
            throw CompilerDirectives.shouldNotReachHere();
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

        public String getName() {
            return name();
        }

        public TruffleString getTsName() {
            throw CompilerDirectives.shouldNotReachHere();
        }

        public String getSignature() {
            return signature;
        }
    }

    static final class MethDirectRoot extends MethodDescriptorRoot {
        private static final Signature SIGNATURE = new Signature(-1, true, 0, false, null, KEYWORDS_HIDDEN_CALLABLE);

        private MethDirectRoot(PythonLanguage lang, TruffleString name, PExternalFunctionWrapper provider) {
            super(lang, name, true, provider);
        }

        @Override
        protected Object[] prepareCArguments(VirtualFrame frame) {
            // return a copy of the args array since it will be modified
            Object[] varargs = PArguments.getVariableArguments(frame);
            return PythonUtils.arrayCopyOf(varargs, varargs.length);
        }

        @Override
        protected void postprocessCArguments(VirtualFrame frame, Object[] cArguments) {
            for (int i = 0; i < cArguments.length; i++) {
                ensureReleaseNativeWrapperNode().execute(cArguments[i]);
            }
        }

        @Override
        public Signature getSignature() {
            return SIGNATURE;
        }

        @TruffleBoundary
        public static MethDirectRoot create(PythonLanguage lang, TruffleString name, PExternalFunctionWrapper provider) {
            return new MethDirectRoot(lang, name, provider);
        }
    }

    /**
     * Like {@link com.oracle.graal.python.nodes.call.FunctionInvokeNode} but invokes a C function.
     */
    @GenerateUncached
    @GenerateCached(false)
    @GenerateInline
    public abstract static class ExternalFunctionInvokeNode extends PNodeWithContext {
        abstract Object execute(VirtualFrame frame, Node inliningTarget, PythonThreadState threadState, CApiTiming timing, TruffleString name, Object callable, Object[] cArguments);

        public final Object call(VirtualFrame frame, Node inliningTarget, PythonThreadState threadState, CApiTiming timing, TruffleString name, Object callable, Object... cArguments) {
            return execute(frame, inliningTarget, threadState, timing, name, callable, cArguments);
        }

        @Specialization
        static Object invoke(VirtualFrame frame, Node inliningTarget, PythonThreadState threadState, CApiTiming timing, TruffleString name, Object callable, Object[] cArguments,
                        @Cached(value = "createFor(this)", uncached = "getUncached()") IndirectCallData indirectCallData,
                        @CachedLibrary(limit = "2") InteropLibrary lib) {

            assert threadState.getCurrentException() == null;

            // If any code requested the caught exception (i.e. used 'sys.exc_info()'), we store
            // it to the context since we cannot propagate it through the native frames.
            Object state = IndirectCallContext.enter(frame, threadState, indirectCallData);

            CApiTiming.enter();
            try {
                return lib.execute(callable, cArguments);
            } catch (UnsupportedTypeException | UnsupportedMessageException e) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw PRaiseNode.raiseUncached(inliningTarget, TypeError, ErrorMessages.CALLING_NATIVE_FUNC_FAILED, name, e);
            } catch (ArityException e) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw PRaiseNode.raiseUncached(inliningTarget, TypeError, ErrorMessages.CALLING_NATIVE_FUNC_EXPECTED_ARGS, name, e.getExpectedMinArity(), e.getActualArity());
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
                IndirectCallContext.exit(frame, threadState, state);
            }
        }
    }

    /**
     * Wraps {@link ExternalFunctionInvokeNode} with result checking and conversion according to the
     * passed {@link PExternalFunctionWrapper}. This node assumes that the provider argument is in
     * the cached case a PE constant.
     */
    @GenerateInline(false)
    public abstract static class ExternalFunctionWrapperInvokeNode extends PNodeWithContext {
        public abstract Object execute(VirtualFrame frame, PExternalFunctionWrapper provider, CApiTiming timing, TruffleString name, Object callable, Object[] cArguments);

        @NeverDefault
        static CheckFunctionResultNode createCheckResultNode(PExternalFunctionWrapper provider) {
            CheckFunctionResultNode node = provider.createCheckFunctionResultNode();
            return node != null ? node : DefaultCheckFunctionResultNodeGen.create();
        }

        static CheckFunctionResultNode getUncachedCheckResultNode(PExternalFunctionWrapper provider) {
            CheckFunctionResultNode node = provider.getUncachedCheckFunctionResultNode();
            return node != null ? node : DefaultCheckFunctionResultNodeGen.getUncached();
        }

        @Specialization
        static Object invokeCached(VirtualFrame frame, PExternalFunctionWrapper provider, CApiTiming timing, TruffleString name, Object callable, Object[] cArguments,
                        @Bind("this") Node inliningTarget,
                        @Cached("createCheckResultNode(provider)") CheckFunctionResultNode checkResultNode,
                        @SuppressWarnings("truffle-neverdefault") @Cached("provider.createConvertRetNode()") CExtToJavaNode convertReturnValue,
                        @Cached PForeignToPTypeNode fromForeign,
                        @Cached GetThreadStateNode getThreadStateNode,
                        @Cached ExternalFunctionInvokeNode invokeNode) {
            CompilerAsserts.partialEvaluationConstant(provider);
            PythonContext ctx = PythonContext.get(inliningTarget);
            return invoke(frame, ctx, timing, name, callable, cArguments, inliningTarget, checkResultNode, convertReturnValue, fromForeign, getThreadStateNode, invokeNode);
        }

        private static Object invoke(VirtualFrame frame, PythonContext ctx, CApiTiming timing, TruffleString name, Object callable, Object[] cArguments, Node inliningTarget,
                        CheckFunctionResultNode checkResultNode, CExtToJavaNode convertReturnValue, PForeignToPTypeNode fromForeign, GetThreadStateNode getThreadStateNode,
                        ExternalFunctionInvokeNode invokeNode) {
            PythonThreadState threadState = getThreadStateNode.execute(inliningTarget, ctx);
            Object result = invokeNode.execute(frame, inliningTarget, threadState, timing, name, callable, cArguments);
            result = checkResultNode.execute(threadState, name, result);
            if (convertReturnValue != null) {
                result = convertReturnValue.execute(result);
            }
            return fromForeign.executeConvert(result);
        }

        @GenerateCached(false)
        private static final class ExternalFunctionWrapperInvokeNodeUncached extends ExternalFunctionWrapperInvokeNode {
            private static final ExternalFunctionWrapperInvokeNodeUncached INSTANCE = new ExternalFunctionWrapperInvokeNodeUncached();

            @Override
            public Object execute(VirtualFrame frame, PExternalFunctionWrapper provider, CApiTiming timing, TruffleString name, Object callable, Object[] cArguments) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                PythonContext ctx = PythonContext.get(null);
                return invoke(frame, ctx, timing, name, callable, cArguments, null, getUncachedCheckResultNode(provider), provider.getUncachedConvertRetNode(), PForeignToPTypeNode.getUncached(),
                                GetThreadStateNode.getUncached(), ExternalFunctionInvokeNodeGen.getUncached());
            }
        }

        @NeverDefault
        public static ExternalFunctionWrapperInvokeNode create() {
            return ExternalFunctionNodesFactory.ExternalFunctionWrapperInvokeNodeGen.create();
        }

        public static ExternalFunctionWrapperInvokeNode getUncached() {
            return ExternalFunctionWrapperInvokeNodeUncached.INSTANCE;
        }
    }

    public abstract static class MethodDescriptorRoot extends PRootNode {
        private final PExternalFunctionWrapper provider;
        private final CApiTiming timing;
        @Child private CalleeContext calleeContext = CalleeContext.create();
        @Child private CallVarargsMethodNode invokeNode;
        @Child private ExternalFunctionWrapperInvokeNode externalInvokeNode;
        @Child private ReadIndexedArgumentNode readSelfNode;
        @Child private ReadIndexedArgumentNode readCallableNode;
        @Child private ReleaseNativeWrapperNode releaseNativeWrapperNode;
        @Children private final CExtToNativeNode[] convertArgs;

        private final TruffleString name;

        MethodDescriptorRoot(PythonLanguage language, TruffleString name, boolean isStatic) {
            this(language, name, isStatic, null);
        }

        MethodDescriptorRoot(PythonLanguage language, TruffleString name, boolean isStatic, PExternalFunctionWrapper provider) {
            super(language);
            CompilerAsserts.neverPartOfCompilation();
            this.name = name;
            this.timing = CApiTiming.create(true, name);
            this.provider = provider;
            if (provider != null) {
                this.externalInvokeNode = ExternalFunctionWrapperInvokeNode.create();
                this.convertArgs = provider.createConvertArgNodes();
            } else {
                this.invokeNode = CallVarargsMethodNode.create();
                this.convertArgs = null;
            }
            if (!isStatic) {
                readSelfNode = ReadIndexedArgumentNode.create(0);
            }
        }

        @ExplodeLoop
        private void prepareArguments(Object[] arguments) {
            for (int i = 0; i < convertArgs.length; i++) {
                if (convertArgs[i] != null) {
                    arguments[i] = convertArgs[i].execute(arguments[i]);
                }
            }
        }

        @Override
        public final Object execute(VirtualFrame frame) {
            calleeContext.enter(frame);
            try {
                Object callable = ensureReadCallableNode().execute(frame);
                if (externalInvokeNode != null) {
                    Object[] cArguments = prepareCArguments(frame);
                    prepareArguments(cArguments);
                    try {
                        assert this.provider != null : "the provider cannot be null";
                        return externalInvokeNode.execute(frame, provider, timing, name, callable, cArguments);
                    } finally {
                        postprocessCArguments(frame, cArguments);
                    }
                } else {
                    return invokeNode.execute(frame, callable, preparePArguments(frame), PArguments.getKeywordArguments(frame));
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
        protected void postprocessCArguments(VirtualFrame frame, Object[] cArguments) {
            // default: do nothing
        }

        protected Object[] preparePArguments(VirtualFrame frame) {
            Object[] variableArguments = PArguments.getVariableArguments(frame);

            int variableArgumentsLength = variableArguments != null ? variableArguments.length : 0;
            // we need to subtract 1 due to the hidden default param that carries the callable
            int userArgumentLength = PArguments.getUserArgumentLength(frame) - 1;
            int argumentsLength = userArgumentLength + variableArgumentsLength;
            Object[] arguments = new Object[argumentsLength];

            // first, copy positional arguments
            PythonUtils.arraycopy(frame.getArguments(), PArguments.USER_ARGUMENTS_OFFSET, arguments, 0, userArgumentLength);

            // now, copy variable arguments
            if (variableArguments != null) {
                PythonUtils.arraycopy(variableArguments, 0, arguments, userArgumentLength, variableArgumentsLength);
            }
            return arguments;
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

        protected final ReleaseNativeWrapperNode ensureReleaseNativeWrapperNode() {
            if (releaseNativeWrapperNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                releaseNativeWrapperNode = insert(ReleaseNativeWrapperNodeGen.create());
            }
            return releaseNativeWrapperNode;
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

    public static final class MethKeywordsRoot extends MethodDescriptorRoot {
        private static final Signature SIGNATURE = new Signature(-1, true, 1, false, tsArray("self"), KEYWORDS_HIDDEN_CALLABLE, true);
        @Child private PythonObjectFactory factory;
        @Child private ReadVarArgsNode readVarargsNode;
        @Child private ReadVarKeywordsNode readKwargsNode;
        @Child private CreateArgsTupleNode createArgsTupleNode;
        @Child private ReleaseNativeSequenceStorageNode freeNode;

        private boolean seenNativeArgsTupleStorage;

        public MethKeywordsRoot(PythonLanguage language, TruffleString name, boolean isStatic) {
            super(language, name, isStatic);
        }

        public MethKeywordsRoot(PythonLanguage language, TruffleString name, boolean isStatic, PExternalFunctionWrapper provider) {
            super(language, name, isStatic, provider);
            this.factory = PythonObjectFactory.create();
            this.readVarargsNode = ReadVarArgsNode.create(true);
            this.readKwargsNode = ReadVarKeywordsNode.create(PythonUtils.EMPTY_TRUFFLESTRING_ARRAY);
            this.createArgsTupleNode = CreateArgsTupleNodeGen.create();
            this.freeNode = ReleaseNativeSequenceStorageNodeGen.create();
        }

        @Override
        protected Object[] prepareCArguments(VirtualFrame frame) {
            Object self = readSelf(frame);
            Object[] args = readVarargsNode.executeObjectArray(frame);
            PKeyword[] kwargs = readKwargsNode.executePKeyword(frame);
            return new Object[]{self, createArgsTupleNode.execute(factory, args, seenNativeArgsTupleStorage), factory.createDict(kwargs)};
        }

        @Override
        protected void postprocessCArguments(VirtualFrame frame, Object[] cArguments) {
            ReleaseNativeWrapperNode releaseNativeWrapperNode = ensureReleaseNativeWrapperNode();
            releaseNativeWrapperNode.execute(cArguments[0]);
            boolean freed = MethVarargsRoot.releaseArgsTuple(cArguments[1], freeNode, seenNativeArgsTupleStorage);
            if (!seenNativeArgsTupleStorage && freed) {
                seenNativeArgsTupleStorage = true;
            }
            releaseNativeWrapperNode.execute(cArguments[2]);
        }

        @Override
        public Signature getSignature() {
            return SIGNATURE;
        }
    }

    public static final class MethVarargsRoot extends MethodDescriptorRoot {
        private static final Signature SIGNATURE = new Signature(-1, false, 1, false, tsArray("self"), KEYWORDS_HIDDEN_CALLABLE, true);
        @Child private PythonObjectFactory factory;
        @Child private ReadVarArgsNode readVarargsNode;
        @Child private CreateArgsTupleNode createArgsTupleNode;
        @Child private ReleaseNativeSequenceStorageNode freeNode;

        private boolean seenNativeArgsTupleStorage;

        public MethVarargsRoot(PythonLanguage language, TruffleString name, boolean isStatic) {
            super(language, name, isStatic);
        }

        public MethVarargsRoot(PythonLanguage language, TruffleString name, boolean isStatic, PExternalFunctionWrapper provider) {
            super(language, name, isStatic, provider);
            this.factory = PythonObjectFactory.create();
            this.readVarargsNode = ReadVarArgsNode.create(true);
            this.createArgsTupleNode = CreateArgsTupleNodeGen.create();
            this.freeNode = ReleaseNativeSequenceStorageNodeGen.create();
        }

        @Override
        protected Object[] prepareCArguments(VirtualFrame frame) {
            Object self = readSelf(frame);
            Object[] args = readVarargsNode.executeObjectArray(frame);
            return new Object[]{self, createArgsTupleNode.execute(factory, args, seenNativeArgsTupleStorage)};
        }

        @Override
        protected void postprocessCArguments(VirtualFrame frame, Object[] cArguments) {
            ReleaseNativeWrapperNode releaseNativeWrapperNode = ensureReleaseNativeWrapperNode();
            releaseNativeWrapperNode.execute(cArguments[0]);
            // releaseNativeWrapperNode.execute(cArguments[1]);
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
                assert argsTupleObject instanceof PythonObjectNativeWrapper;
                PythonObjectNativeWrapper argsTupleWrapper = (PythonObjectNativeWrapper) argsTupleObject;
                Object argsTuple = argsTupleWrapper.getDelegate();
                assert argsTuple instanceof PTuple;
                SequenceStorage s = ((PTuple) argsTuple).getSequenceStorage();
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
                    if (argsTupleWrapper.getRefCount() == MANAGED_REFCNT) {
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

    public static final class MethInquiryRoot extends MethodDescriptorRoot {
        private static final Signature SIGNATURE = new Signature(-1, false, -1, false, tsArray("self"), KEYWORDS_HIDDEN_CALLABLE, true);

        public MethInquiryRoot(PythonLanguage language, TruffleString name, boolean isStatic) {
            super(language, name, isStatic);
        }

        public MethInquiryRoot(PythonLanguage language, TruffleString name, boolean isStatic, PExternalFunctionWrapper provider) {
            super(language, name, isStatic, provider);
        }

        @Override
        protected Object[] prepareCArguments(VirtualFrame frame) {
            return new Object[]{readSelf(frame)};
        }

        @Override
        protected void postprocessCArguments(VirtualFrame frame, Object[] cArguments) {
            ensureReleaseNativeWrapperNode().execute(cArguments[0]);
        }

        @Override
        public Signature getSignature() {
            return SIGNATURE;
        }
    }

    public static final class MethNoargsRoot extends MethodDescriptorRoot {
        private static final Signature SIGNATURE = new Signature(-1, false, -1, false, tsArray("self"), KEYWORDS_HIDDEN_CALLABLE, true);

        public MethNoargsRoot(PythonLanguage language, TruffleString name, boolean isStatic) {
            super(language, name, isStatic);
        }

        public MethNoargsRoot(PythonLanguage language, TruffleString name, boolean isStatic, PExternalFunctionWrapper provider) {
            super(language, name, isStatic, provider);
        }

        @Override
        protected Object[] prepareCArguments(VirtualFrame frame) {
            return new Object[]{readSelf(frame), PNone.NONE};
        }

        @Override
        protected void postprocessCArguments(VirtualFrame frame, Object[] cArguments) {
            ensureReleaseNativeWrapperNode().execute(cArguments[0]);
        }

        @Override
        public Signature getSignature() {
            return SIGNATURE;
        }
    }

    public static final class MethORoot extends MethodDescriptorRoot {
        private static final Signature SIGNATURE = new Signature(-1, false, -1, false, tsArray("self", "arg"), KEYWORDS_HIDDEN_CALLABLE, true);
        @Child private ReadIndexedArgumentNode readArgNode;

        public MethORoot(PythonLanguage language, TruffleString name, boolean isStatic) {
            super(language, name, isStatic);
        }

        public MethORoot(PythonLanguage language, TruffleString name, boolean isStatic, PExternalFunctionWrapper provider) {
            super(language, name, isStatic, provider);
            this.readArgNode = ReadIndexedArgumentNode.create(1);
        }

        @Override
        protected Object[] prepareCArguments(VirtualFrame frame) {
            Object self = readSelf(frame);
            Object arg = readArgNode.execute(frame);
            return new Object[]{self, arg};
        }

        @Override
        protected void postprocessCArguments(VirtualFrame frame, Object[] cArguments) {
            ReleaseNativeWrapperNode releaseNativeWrapperNode = ensureReleaseNativeWrapperNode();
            releaseNativeWrapperNode.execute(cArguments[0]);
            releaseNativeWrapperNode.execute(cArguments[1]);
        }

        @Override
        public Signature getSignature() {
            return SIGNATURE;
        }
    }

    public static final class MethFastcallWithKeywordsRoot extends MethodDescriptorRoot {
        private static final Signature SIGNATURE = new Signature(-1, true, 1, false, tsArray("self"), KEYWORDS_HIDDEN_CALLABLE, true);
        @Child private PythonObjectFactory factory;
        @Child private ReadVarArgsNode readVarargsNode;
        @Child private ReadVarKeywordsNode readKwargsNode;

        public MethFastcallWithKeywordsRoot(PythonLanguage language, TruffleString name, boolean isStatic) {
            super(language, name, isStatic);
        }

        public MethFastcallWithKeywordsRoot(PythonLanguage language, TruffleString name, boolean isStatic, PExternalFunctionWrapper provider) {
            super(language, name, isStatic, provider);
            this.factory = PythonObjectFactory.create();
            this.readVarargsNode = ReadVarArgsNode.create(true);
            this.readKwargsNode = ReadVarKeywordsNode.create(PythonUtils.EMPTY_TRUFFLESTRING_ARRAY);
        }

        @Override
        protected Object[] prepareCArguments(VirtualFrame frame) {
            Object self = readSelf(frame);
            Object[] args = readVarargsNode.executeObjectArray(frame);
            PKeyword[] kwargs = readKwargsNode.executePKeyword(frame);
            Object[] fastcallArgs = new Object[args.length + kwargs.length];
            Object[] fastcallKwnames = new Object[kwargs.length];
            PythonUtils.arraycopy(args, 0, fastcallArgs, 0, args.length);
            for (int i = 0; i < kwargs.length; i++) {
                fastcallKwnames[i] = kwargs[i].getName();
                fastcallArgs[args.length + i] = kwargs[i].getValue();
            }
            return new Object[]{self, new CPyObjectArrayWrapper(fastcallArgs), args.length, factory.createTuple(fastcallKwnames)};
        }

        @Override
        protected void postprocessCArguments(VirtualFrame frame, Object[] cArguments) {
            ReleaseNativeWrapperNode releaseNativeWrapperNode = ensureReleaseNativeWrapperNode();
            releaseNativeWrapperNode.execute(cArguments[0]);
            CPyObjectArrayWrapper wrapper = (CPyObjectArrayWrapper) cArguments[1];
            wrapper.free(ensureReleaseNativeWrapperNode());
            releaseNativeWrapperNode.execute(cArguments[3]);
        }

        @Override
        public Signature getSignature() {
            return SIGNATURE;
        }
    }

    public static final class MethMethodRoot extends MethodDescriptorRoot {
        private static final Signature SIGNATURE = new Signature(-1, true, 1, false, tsArray("self", "cls"), KEYWORDS_HIDDEN_CALLABLE, true);
        @Child private PythonObjectFactory factory;
        @Child private ReadIndexedArgumentNode readClsNode;
        @Child private ReadVarArgsNode readVarargsNode;
        @Child private ReadVarKeywordsNode readKwargsNode;

        public MethMethodRoot(PythonLanguage language, TruffleString name, boolean isStatic) {
            super(language, name, isStatic);
        }

        public MethMethodRoot(PythonLanguage language, TruffleString name, boolean isStatic, PExternalFunctionWrapper provider) {
            super(language, name, isStatic, provider);
            this.factory = PythonObjectFactory.create();
            this.readClsNode = ReadIndexedArgumentNode.create(1);
            this.readVarargsNode = ReadVarArgsNode.create(true);
            this.readKwargsNode = ReadVarKeywordsNode.create(PythonUtils.EMPTY_TRUFFLESTRING_ARRAY);
        }

        @Override
        protected Object[] prepareCArguments(VirtualFrame frame) {
            Object self = readSelf(frame);
            Object cls = readClsNode.execute(frame);
            Object[] args = readVarargsNode.executeObjectArray(frame);
            PKeyword[] kwargs = readKwargsNode.executePKeyword(frame);
            Object[] fastcallArgs = new Object[args.length + kwargs.length];
            Object[] fastcallKwnames = new Object[kwargs.length];
            PythonUtils.arraycopy(args, 0, fastcallArgs, 0, args.length);
            for (int i = 0; i < kwargs.length; i++) {
                fastcallKwnames[i] = kwargs[i].getName();
                fastcallArgs[args.length + i] = kwargs[i].getValue();
            }
            return new Object[]{self, cls, new CPyObjectArrayWrapper(fastcallArgs), args.length, factory.createTuple(fastcallKwnames)};
        }

        @Override
        protected void postprocessCArguments(VirtualFrame frame, Object[] cArguments) {
            ReleaseNativeWrapperNode releaseNativeWrapperNode = ensureReleaseNativeWrapperNode();
            releaseNativeWrapperNode.execute(cArguments[0]);
            releaseNativeWrapperNode.execute(cArguments[1]);
            CPyObjectArrayWrapper wrapper = (CPyObjectArrayWrapper) cArguments[2];
            wrapper.free(releaseNativeWrapperNode);
            releaseNativeWrapperNode.execute(cArguments[4]);
        }

        @Override
        public Signature getSignature() {
            return SIGNATURE;
        }
    }

    public static final class MethFastcallRoot extends MethodDescriptorRoot {
        private static final Signature SIGNATURE = new Signature(-1, false, 1, false, tsArray("self"), KEYWORDS_HIDDEN_CALLABLE, true);
        @Child private ReadVarArgsNode readVarargsNode;

        public MethFastcallRoot(PythonLanguage language, TruffleString name, boolean isStatic) {
            super(language, name, isStatic);
        }

        public MethFastcallRoot(PythonLanguage language, TruffleString name, boolean isStatic, PExternalFunctionWrapper provider) {
            super(language, name, isStatic, provider);
            this.readVarargsNode = ReadVarArgsNode.create(true);
        }

        @Override
        protected Object[] prepareCArguments(VirtualFrame frame) {
            Object self = readSelf(frame);
            Object[] args = readVarargsNode.executeObjectArray(frame);
            return new Object[]{self, new CPyObjectArrayWrapper(args), args.length};
        }

        @Override
        protected void postprocessCArguments(VirtualFrame frame, Object[] cArguments) {
            ReleaseNativeWrapperNode releaseNativeWrapperNode = ensureReleaseNativeWrapperNode();
            releaseNativeWrapperNode.execute(cArguments[0]);
            CPyObjectArrayWrapper wrapper = (CPyObjectArrayWrapper) cArguments[1];
            wrapper.free(ensureReleaseNativeWrapperNode());
        }

        @Override
        public Signature getSignature() {
            return SIGNATURE;
        }
    }

    /**
     * Wrapper root node for C function type {@code allocfunc} and {@code ssizeargfunc}.
     */
    static class AllocFuncRootNode extends MethodDescriptorRoot {
        private static final Signature SIGNATURE = new Signature(-1, false, -1, false, tsArray("self", "nitems"), KEYWORDS_HIDDEN_CALLABLE, true);
        @Child private ReadIndexedArgumentNode readArgNode;
        @Child private ConvertPIntToPrimitiveNode asSsizeTNode;

        AllocFuncRootNode(PythonLanguage language, TruffleString name) {
            super(language, name, false);
        }

        AllocFuncRootNode(PythonLanguage language, TruffleString name, PExternalFunctionWrapper provider) {
            super(language, name, false, provider);
            this.readArgNode = ReadIndexedArgumentNode.create(1);
            this.asSsizeTNode = ConvertPIntToPrimitiveNodeGen.create();
        }

        @Override
        protected Object[] prepareCArguments(VirtualFrame frame) {
            Object self = readSelf(frame);
            Object arg = readArgNode.execute(frame);
            try {
                return new Object[]{self, asSsizeTNode.executeLongCached(arg, 1, Long.BYTES)};
            } catch (UnexpectedResultException e) {
                throw CompilerDirectives.shouldNotReachHere();
            }
        }

        @Override
        protected void postprocessCArguments(VirtualFrame frame, Object[] cArguments) {
            ensureReleaseNativeWrapperNode().execute(cArguments[0]);
        }

        @Override
        public Signature getSignature() {
            return SIGNATURE;
        }
    }

    /**
     * Wrapper root node for a get attribute function (C type {@code getattrfunc}).
     */
    static final class GetAttrFuncRootNode extends MethodDescriptorRoot {
        private static final Signature SIGNATURE = new Signature(-1, false, -1, false, tsArray("self", "key"), KEYWORDS_HIDDEN_CALLABLE, true);
        @Child private ReadIndexedArgumentNode readArgNode;
        @Child private CExtNodes.AsCharPointerNode asCharPointerNode;
        @Child private CStructAccess.FreeNode free;

        GetAttrFuncRootNode(PythonLanguage language, TruffleString name) {
            super(language, name, false);
        }

        GetAttrFuncRootNode(PythonLanguage language, TruffleString name, PExternalFunctionWrapper provider) {
            super(language, name, false, provider);
            this.readArgNode = ReadIndexedArgumentNode.create(1);
            this.asCharPointerNode = AsCharPointerNodeGen.create();
            this.free = CStructAccess.FreeNode.create();
        }

        @Override
        protected Object[] prepareCArguments(VirtualFrame frame) {
            Object self = readSelf(frame);
            Object arg = readArgNode.execute(frame);
            // TODO we should use 'CStringWrapper' for 'arg' but it does currently not support
            // PString
            return new Object[]{self, asCharPointerNode.execute(arg)};
        }

        @Override
        protected void postprocessCArguments(VirtualFrame frame, Object[] cArguments) {
            ensureReleaseNativeWrapperNode().execute(cArguments[0]);
            free.free(cArguments[1]);
        }

        @Override
        public Signature getSignature() {
            return SIGNATURE;
        }
    }

    /**
     * Wrapper root node for a set attribute function (C type {@code setattrfunc}).
     */
    static final class SetAttrFuncRootNode extends MethodDescriptorRoot {
        private static final Signature SIGNATURE = new Signature(-1, false, -1, false, tsArray("self", "key", "value"), KEYWORDS_HIDDEN_CALLABLE, true);
        @Child private ReadIndexedArgumentNode readArg1Node;
        @Child private ReadIndexedArgumentNode readArg2Node;
        @Child private CExtNodes.AsCharPointerNode asCharPointerNode;
        @Child private CStructAccess.FreeNode free;

        SetAttrFuncRootNode(PythonLanguage language, TruffleString name) {
            super(language, name, false);
        }

        SetAttrFuncRootNode(PythonLanguage language, TruffleString name, PExternalFunctionWrapper provider) {
            super(language, name, false, provider);
            this.readArg1Node = ReadIndexedArgumentNode.create(1);
            this.readArg2Node = ReadIndexedArgumentNode.create(2);
            this.asCharPointerNode = AsCharPointerNodeGen.create();
            this.free = CStructAccess.FreeNode.create();
        }

        @Override
        protected Object[] prepareCArguments(VirtualFrame frame) {
            Object self = readSelf(frame);
            Object arg1 = readArg1Node.execute(frame);
            Object arg2 = readArg2Node.execute(frame);
            // TODO we should use 'CStringWrapper' for 'arg1' but it does currently not support
            // PString
            return new Object[]{self, asCharPointerNode.execute(arg1), arg2};
        }

        @Override
        protected void postprocessCArguments(VirtualFrame frame, Object[] cArguments) {
            ReleaseNativeWrapperNode releaseNativeWrapperNode = ensureReleaseNativeWrapperNode();
            releaseNativeWrapperNode.execute(cArguments[0]);
            free.free(cArguments[1]);
            releaseNativeWrapperNode.execute(cArguments[2]);
        }

        @Override
        public Signature getSignature() {
            return SIGNATURE;
        }
    }

    /**
     * Wrapper root node for a rich compare function (C type {@code richcmpfunc}).
     */
    static final class RichCmpFuncRootNode extends MethodDescriptorRoot {
        private static final Signature SIGNATURE = new Signature(-1, false, -1, false, tsArray("self", "other", "op"), KEYWORDS_HIDDEN_CALLABLE, true);
        @Child private ReadIndexedArgumentNode readArg1Node;
        @Child private ReadIndexedArgumentNode readArg2Node;
        @Child private ConvertPIntToPrimitiveNode asSsizeTNode;

        RichCmpFuncRootNode(PythonLanguage language, TruffleString name) {
            super(language, name, false);
        }

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
                Object arg1 = readArg1Node.execute(frame);
                Object arg2 = readArg2Node.execute(frame);
                return new Object[]{self, arg1, asSsizeTNode.executeIntCached(arg2, 1, Integer.BYTES)};
            } catch (UnexpectedResultException e) {
                throw CompilerDirectives.shouldNotReachHere();
            }
        }

        @Override
        protected void postprocessCArguments(VirtualFrame frame, Object[] cArguments) {
            ReleaseNativeWrapperNode releaseNativeWrapperNode = ensureReleaseNativeWrapperNode();
            releaseNativeWrapperNode.execute(cArguments[0]);
            releaseNativeWrapperNode.execute(cArguments[1]);
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
    static final class GetItemRootNode extends MethodDescriptorRoot {
        private static final Signature SIGNATURE = new Signature(-1, false, -1, false, tsArray("self", "i"), KEYWORDS_HIDDEN_CALLABLE, true);
        @Child private ReadIndexedArgumentNode readArg1Node;
        @Child private GetIndexNode getIndexNode;

        GetItemRootNode(PythonLanguage language, TruffleString name) {
            super(language, name, false);
        }

        GetItemRootNode(PythonLanguage language, TruffleString name, PExternalFunctionWrapper provider) {
            super(language, name, false, provider);
            this.readArg1Node = ReadIndexedArgumentNode.create(1);
            this.getIndexNode = GetIndexNode.create();
        }

        @Override
        protected Object[] prepareCArguments(VirtualFrame frame) {
            Object self = readSelf(frame);
            Object arg1 = readArg1Node.execute(frame);
            return new Object[]{self, getIndexNode.execute(self, arg1)};
        }

        @Override
        protected void postprocessCArguments(VirtualFrame frame, Object[] cArguments) {
            ensureReleaseNativeWrapperNode().execute(cArguments[0]);
        }

        @Override
        public Signature getSignature() {
            return SIGNATURE;
        }
    }

    /**
     * Implements semantics of {@code typeobject.c: wrap_sq_setitem}.
     */
    static final class SetItemRootNode extends MethodDescriptorRoot {
        private static final Signature SIGNATURE = new Signature(-1, false, -1, false, tsArray("self", "i", "value"), KEYWORDS_HIDDEN_CALLABLE, true);
        @Child private ReadIndexedArgumentNode readArg1Node;
        @Child private ReadIndexedArgumentNode readArg2Node;
        @Child private GetIndexNode getIndexNode;

        SetItemRootNode(PythonLanguage language, TruffleString name) {
            super(language, name, false);
        }

        SetItemRootNode(PythonLanguage language, TruffleString name, PExternalFunctionWrapper provider) {
            super(language, name, false, provider);
            this.readArg1Node = ReadIndexedArgumentNode.create(1);
            this.readArg2Node = ReadIndexedArgumentNode.create(2);
            this.getIndexNode = GetIndexNode.create();
        }

        @Override
        protected Object[] prepareCArguments(VirtualFrame frame) {
            Object self = readSelf(frame);
            Object arg1 = readArg1Node.execute(frame);
            Object arg2 = readArg2Node.execute(frame);
            return new Object[]{self, getIndexNode.execute(self, arg1), arg2};
        }

        @Override
        protected void postprocessCArguments(VirtualFrame frame, Object[] cArguments) {
            ReleaseNativeWrapperNode releaseNativeWrapperNode = ensureReleaseNativeWrapperNode();
            releaseNativeWrapperNode.execute(cArguments[0]);
            releaseNativeWrapperNode.execute(cArguments[2]);
        }

        @Override
        public Signature getSignature() {
            return SIGNATURE;
        }
    }

    /**
     * Implements semantics of {@code typeobject.c:wrap_descr_get}
     */
    public static final class DescrGetRootNode extends MethodDescriptorRoot {
        private static final Signature SIGNATURE = new Signature(-1, false, -1, false, tsArray("self", "obj", "type"), KEYWORDS_HIDDEN_CALLABLE, true);
        @Child private ReadIndexedArgumentNode readObj;
        @Child private ReadIndexedArgumentNode readType;

        public DescrGetRootNode(PythonLanguage language, TruffleString name) {
            super(language, name, false);
        }

        public DescrGetRootNode(PythonLanguage language, TruffleString name, PExternalFunctionWrapper provider) {
            super(language, name, false, provider);
            this.readObj = ReadIndexedArgumentNode.create(1);
            this.readType = ReadIndexedArgumentNode.create(2);
        }

        @Override
        protected Object[] prepareCArguments(VirtualFrame frame) {
            Object self = readSelf(frame);
            Object obj = readObj.execute(frame);
            Object type = readType.execute(frame);
            return new Object[]{self, obj == PNone.NONE ? PNone.NO_VALUE : obj, type == PNone.NONE ? PNone.NO_VALUE : type};
        }

        @Override
        protected void postprocessCArguments(VirtualFrame frame, Object[] cArguments) {
            ReleaseNativeWrapperNode releaseNativeWrapperNode = ensureReleaseNativeWrapperNode();
            releaseNativeWrapperNode.execute(cArguments[0]);
            releaseNativeWrapperNode.execute(cArguments[1]);
            releaseNativeWrapperNode.execute(cArguments[2]);
        }

        @Override
        public Signature getSignature() {
            return SIGNATURE;
        }
    }

    /**
     * Implements semantics of {@code typeobject.c:wrap_descr_delete}
     */
    public static final class DescrDeleteRootNode extends MethodDescriptorRoot {
        private static final Signature SIGNATURE = new Signature(-1, false, -1, false, tsArray("self", "obj"), KEYWORDS_HIDDEN_CALLABLE, true);
        @Child private ReadIndexedArgumentNode readObj;

        public DescrDeleteRootNode(PythonLanguage language, TruffleString name) {
            super(language, name, false);
        }

        public DescrDeleteRootNode(PythonLanguage language, TruffleString name, PExternalFunctionWrapper provider) {
            super(language, name, false, provider);
            this.readObj = ReadIndexedArgumentNode.create(1);
        }

        @Override
        protected Object[] prepareCArguments(VirtualFrame frame) {
            Object self = readSelf(frame);
            Object obj = readObj.execute(frame);
            return new Object[]{self, obj, PNone.NO_VALUE};
        }

        @Override
        protected void postprocessCArguments(VirtualFrame frame, Object[] cArguments) {
            ReleaseNativeWrapperNode releaseNativeWrapperNode = ensureReleaseNativeWrapperNode();
            releaseNativeWrapperNode.execute(cArguments[0]);
            releaseNativeWrapperNode.execute(cArguments[1]);
        }

        @Override
        public Signature getSignature() {
            return SIGNATURE;
        }
    }

    /**
     * Implements semantics of {@code typeobject.c:wrap_delattr}
     */
    public static final class DelAttrRootNode extends MethodDescriptorRoot {
        private static final Signature SIGNATURE = new Signature(-1, false, -1, false, tsArray("self", "obj"), KEYWORDS_HIDDEN_CALLABLE, true);
        @Child private ReadIndexedArgumentNode readObj;

        public DelAttrRootNode(PythonLanguage language, TruffleString name) {
            super(language, name, false);
        }

        public DelAttrRootNode(PythonLanguage language, TruffleString name, PExternalFunctionWrapper provider) {
            super(language, name, false, provider);
            this.readObj = ReadIndexedArgumentNode.create(1);
        }

        @Override
        protected Object[] prepareCArguments(VirtualFrame frame) {
            Object self = readSelf(frame);
            Object obj = readObj.execute(frame);
            // TODO: check if we need Carlo Verre hack here (see typeobject.c:hackcheck)
            return new Object[]{self, obj, PNone.NO_VALUE};
        }

        @Override
        protected void postprocessCArguments(VirtualFrame frame, Object[] cArguments) {
            ReleaseNativeWrapperNode releaseNativeWrapperNode = ensureReleaseNativeWrapperNode();
            releaseNativeWrapperNode.execute(cArguments[0]);
            releaseNativeWrapperNode.execute(cArguments[1]);
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
    static final class MpDelItemRootNode extends MethodDescriptorRoot {
        private static final Signature SIGNATURE = new Signature(-1, false, -1, false, tsArray("self", "i"), KEYWORDS_HIDDEN_CALLABLE, true);
        @Child private ReadIndexedArgumentNode readArg1Node;

        MpDelItemRootNode(PythonLanguage language, TruffleString name) {
            super(language, name, false);
        }

        MpDelItemRootNode(PythonLanguage language, TruffleString name, PExternalFunctionWrapper provider) {
            super(language, name, false, provider);
            this.readArg1Node = ReadIndexedArgumentNode.create(1);
        }

        @Override
        protected Object[] prepareCArguments(VirtualFrame frame) {
            Object self = readSelf(frame);
            Object arg1 = readArg1Node.execute(frame);
            return new Object[]{self, arg1, PNone.NO_VALUE};
        }

        @Override
        protected void postprocessCArguments(VirtualFrame frame, Object[] cArguments) {
            ReleaseNativeWrapperNode releaseNativeWrapperNode = ensureReleaseNativeWrapperNode();
            releaseNativeWrapperNode.execute(cArguments[0]);
            releaseNativeWrapperNode.execute(cArguments[1]);
            releaseNativeWrapperNode.execute(cArguments[2]);
        }

        @Override
        public Signature getSignature() {
            return SIGNATURE;
        }
    }

    /**
     * Wrapper root node for reverse binary operations.
     */
    static final class MethReverseRootNode extends MethodDescriptorRoot {
        private static final Signature SIGNATURE = new Signature(-1, false, -1, false, tsArray("self", "obj"), KEYWORDS_HIDDEN_CALLABLE, true);
        @Child private ReadIndexedArgumentNode readArg0Node;
        @Child private ReadIndexedArgumentNode readArg1Node;

        MethReverseRootNode(PythonLanguage language, TruffleString name) {
            super(language, name, false);
            this.readArg0Node = ReadIndexedArgumentNode.create(0);
            this.readArg1Node = ReadIndexedArgumentNode.create(1);
        }

        MethReverseRootNode(PythonLanguage language, TruffleString name, PExternalFunctionWrapper provider) {
            super(language, name, false, provider);
            this.readArg0Node = ReadIndexedArgumentNode.create(0);
            this.readArg1Node = ReadIndexedArgumentNode.create(1);
        }

        @Override
        protected Object[] prepareCArguments(VirtualFrame frame) {
            Object arg0 = readArg0Node.execute(frame);
            Object arg1 = readArg1Node.execute(frame);
            return new Object[]{arg1, arg0};
        }

        @Override
        protected Object[] preparePArguments(VirtualFrame frame) {
            Object arg0 = readArg0Node.execute(frame);
            Object arg1 = readArg1Node.execute(frame);
            return new Object[]{arg1, arg0};
        }

        @Override
        protected void postprocessCArguments(VirtualFrame frame, Object[] cArguments) {
            ReleaseNativeWrapperNode releaseNativeWrapperNode = ensureReleaseNativeWrapperNode();
            releaseNativeWrapperNode.execute(cArguments[0]);
            releaseNativeWrapperNode.execute(cArguments[1]);
        }

        @Override
        public Signature getSignature() {
            return SIGNATURE;
        }
    }

    /**
     * Wrapper root node for native power function (with an optional third argument).
     */
    static class MethPowRootNode extends MethodDescriptorRoot {
        private static final Signature SIGNATURE = new Signature(false, 0, false, tsArray("args"), KEYWORDS_HIDDEN_CALLABLE);

        @Child private ReadVarArgsNode readVarargsNode;

        private final ConditionProfile profile;

        MethPowRootNode(PythonLanguage language, TruffleString name) {
            super(language, name, false);
            this.profile = null;
        }

        MethPowRootNode(PythonLanguage language, TruffleString name, PExternalFunctionWrapper provider) {
            super(language, name, false, provider);
            this.readVarargsNode = ReadVarArgsNode.create(true);
            this.profile = ConditionProfile.create();
        }

        @Override
        protected final Object[] prepareCArguments(VirtualFrame frame) {
            Object self = readSelf(frame);
            Object[] varargs = readVarargsNode.executeObjectArray(frame);
            Object arg0 = varargs[0];
            Object arg1 = profile.profile(varargs.length > 1) ? varargs[1] : PNone.NONE;
            return getArguments(self, arg0, arg1);
        }

        Object[] getArguments(Object arg0, Object arg1, Object arg2) {
            return new Object[]{arg0, arg1, arg2};
        }

        @Override
        protected void postprocessCArguments(VirtualFrame frame, Object[] cArguments) {
            ReleaseNativeWrapperNode releaseNativeWrapperNode = ensureReleaseNativeWrapperNode();
            releaseNativeWrapperNode.execute(cArguments[0]);
            releaseNativeWrapperNode.execute(cArguments[1]);
            releaseNativeWrapperNode.execute(cArguments[2]);
        }

        @Override
        public Signature getSignature() {
            return SIGNATURE;
        }
    }

    /**
     * Wrapper root node for native reverse power function (with an optional third argument).
     */
    static final class MethRPowRootNode extends MethPowRootNode {

        MethRPowRootNode(PythonLanguage language, TruffleString name) {
            super(language, name);
        }

        MethRPowRootNode(PythonLanguage language, TruffleString name, PExternalFunctionWrapper provider) {
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
    static final class MethRichcmpOpRootNode extends MethodDescriptorRoot {
        private static final Signature SIGNATURE = new Signature(-1, false, -1, false, tsArray("self", "other"), KEYWORDS_HIDDEN_CALLABLE, true);
        @Child private ReadIndexedArgumentNode readArgNode;

        private final int op;

        MethRichcmpOpRootNode(PythonLanguage language, TruffleString name, int op) {
            super(language, name, false);
            this.readArgNode = ReadIndexedArgumentNode.create(1);
            this.op = op;
        }

        MethRichcmpOpRootNode(PythonLanguage language, TruffleString name, PExternalFunctionWrapper provider, int op) {
            super(language, name, false, provider);
            this.readArgNode = ReadIndexedArgumentNode.create(1);
            this.op = op;
        }

        @Override
        protected Object[] prepareCArguments(VirtualFrame frame) {
            Object self = readSelf(frame);
            Object arg = readArgNode.execute(frame);
            return new Object[]{self, arg, op};
        }

        @Override
        protected Object[] preparePArguments(VirtualFrame frame) {
            Object self = readSelf(frame);
            Object arg = readArgNode.execute(frame);
            return new Object[]{self, arg, op};
        }

        @Override
        protected void postprocessCArguments(VirtualFrame frame, Object[] cArguments) {
            ReleaseNativeWrapperNode releaseNativeWrapperNode = ensureReleaseNativeWrapperNode();
            releaseNativeWrapperNode.execute(cArguments[0]);
            releaseNativeWrapperNode.execute(cArguments[1]);
        }

        @Override
        public Signature getSignature() {
            return SIGNATURE;
        }
    }

    /**
     * Wrapper root node for C function type {@code iternextfunc}.
     */
    static class IterNextFuncRootNode extends MethodDescriptorRoot {

        IterNextFuncRootNode(PythonLanguage language, TruffleString name) {
            super(language, name, false);
        }

        IterNextFuncRootNode(PythonLanguage language, TruffleString name, PExternalFunctionWrapper provider) {
            super(language, name, false, provider);
        }

        @Override
        protected Object[] prepareCArguments(VirtualFrame frame) {
            return new Object[]{readSelf(frame)};
        }

        @Override
        protected void postprocessCArguments(VirtualFrame frame, Object[] cArguments) {
            ensureReleaseNativeWrapperNode().execute(cArguments[0]);
        }

        @Override
        public Signature getSignature() {
            // same signature as a method without arguments (just the self)
            return MethNoargsRoot.SIGNATURE;
        }
    }

    abstract static class GetSetRootNode extends MethodDescriptorRoot {

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
        private static final Signature SIGNATURE = new Signature(-1, false, -1, false, tsArray("self"), KEYWORDS_HIDDEN_CALLABLE_AND_CLOSURE, true);

        public GetterRoot(PythonLanguage language, TruffleString name, PExternalFunctionWrapper provider) {
            super(language, name, provider);
        }

        @Override
        protected Object[] prepareCArguments(VirtualFrame frame) {
            Object self = readSelf(frame);
            return new Object[]{self, readClosure(frame)};
        }

        @Override
        protected void postprocessCArguments(VirtualFrame frame, Object[] cArguments) {
            ensureReleaseNativeWrapperNode().execute(cArguments[0]);
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
        private static final Signature SIGNATURE = new Signature(-1, false, -1, false, tsArray("self", "value"), KEYWORDS_HIDDEN_CALLABLE_AND_CLOSURE, true);

        @Child private ReadIndexedArgumentNode readArgNode;

        public SetterRoot(PythonLanguage language, TruffleString name, PExternalFunctionWrapper provider) {
            super(language, name, provider);
        }

        @Override
        protected Object[] prepareCArguments(VirtualFrame frame) {
            Object self = readSelf(frame);
            Object arg = ensureReadArgNode().execute(frame);
            return new Object[]{self, arg, readClosure(frame)};
        }

        @Override
        protected void postprocessCArguments(VirtualFrame frame, Object[] cArguments) {
            ReleaseNativeWrapperNode releaseNativeWrapperNode = ensureReleaseNativeWrapperNode();
            releaseNativeWrapperNode.execute(cArguments[0]);
            releaseNativeWrapperNode.execute(cArguments[1]);
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

    /**
     * We need to inflate all primitives in order to avoid memory leaks. Explanation: Primitives
     * would currently be wrapped into a PrimitiveNativeWrapper. If any of those will receive a
     * toNative message, the managed code will be the only owner of those wrappers. But we will
     * never be able to reach the wrapper from the arguments if they are just primitive. So, we
     * inflate the primitives and we can then traverse the tuple and reach the wrappers of its
     * arguments after the call returned.
     */
    @GenerateInline(false)
    abstract static class CreateArgsTupleNode extends Node {
        public abstract PTuple execute(PythonObjectFactory factory, Object[] args, boolean eagerNative);

        @Specialization(guards = {"args.length == cachedLen", "cachedLen <= 8", "!eagerNative"}, limit = "1")
        @ExplodeLoop(kind = LoopExplosionKind.FULL_UNROLL)
        static PTuple doCachedLen(PythonObjectFactory factory, Object[] args, @SuppressWarnings("unused") boolean eagerNative,
                        @Cached("args.length") int cachedLen,
                        @Cached("createMaterializeNodes(args.length)") MaterializePrimitiveNode[] materializePrimitiveNodes) {

            for (int i = 0; i < cachedLen; i++) {
                args[i] = materializePrimitiveNodes[i].execute(factory, args[i]);
            }
            return factory.createTuple(args);
        }

        @Specialization(guards = {"args.length == cachedLen", "cachedLen <= 8", "eagerNative"}, limit = "1", replaces = "doCachedLen")
        @ExplodeLoop(kind = LoopExplosionKind.FULL_UNROLL)
        static PTuple doCachedLenEagerNative(PythonObjectFactory factory, Object[] args, @SuppressWarnings("unused") boolean eagerNative,
                        @Bind("this") Node inliningTarget,
                        @Cached("args.length") int cachedLen,
                        @Cached("createMaterializeNodes(args.length)") MaterializePrimitiveNode[] materializePrimitiveNodes,
                        @Exclusive @Cached StorageToNativeNode storageToNativeNode) {

            for (int i = 0; i < cachedLen; i++) {
                args[i] = materializePrimitiveNodes[i].execute(factory, args[i]);
            }
            return factory.createTuple(storageToNativeNode.execute(inliningTarget, args, cachedLen, true));
        }

        @Specialization(replaces = {"doCachedLen", "doCachedLenEagerNative"})
        static PTuple doGeneric(PythonObjectFactory factory, Object[] args, boolean eagerNative,
                        @Bind("this") Node inliningTarget,
                        @Cached MaterializePrimitiveNode materializePrimitiveNode,
                        @Exclusive @Cached StorageToNativeNode storageToNativeNode) {

            int n = args.length;
            for (int i = 0; i < n; i++) {
                args[i] = materializePrimitiveNode.execute(factory, args[i]);
            }
            SequenceStorage storage;
            if (eagerNative) {
                storage = storageToNativeNode.execute(inliningTarget, args, n, true);
            } else {
                storage = new ObjectSequenceStorage(args);
            }
            return factory.createTuple(storage);
        }

        static MaterializePrimitiveNode[] createMaterializeNodes(int length) {
            MaterializePrimitiveNode[] materializePrimitiveNodes = new MaterializePrimitiveNode[length];
            for (int i = 0; i < length; i++) {
                materializePrimitiveNodes[i] = MaterializePrimitiveNodeGen.create();
            }
            return materializePrimitiveNodes;
        }

        static PythonToNativeNode[] createPythonToNativeNodes(int length) {
            PythonToNativeNode[] pythonToNativeNodes = new PythonToNativeNode[length];
            for (int i = 0; i < length; i++) {
                pythonToNativeNodes[i] = PythonToNativeNodeGen.create();
            }
            return pythonToNativeNodes;
        }
    }

    @GenerateInline(false)
    @ImportStatic(PythonUtils.class)
    abstract static class ReleaseNativeSequenceStorageNode extends Node {

        abstract void execute(NativeSequenceStorage storage);

        @Specialization(guards = {"storage.length() == cachedLen", "cachedLen <= 8"}, limit = "1")
        @ExplodeLoop(kind = LoopExplosionKind.FULL_UNROLL)
        static void doObjectCachedLen(NativeObjectSequenceStorage storage,
                        @Bind("this") Node inliningTarget,
                        @Cached("storage.length()") int cachedLen,
                        @Shared @Cached CStructAccess.ReadPointerNode readNode,
                        @Shared @Cached CExtNodes.XDecRefPointerNode decRefPointerNode,
                        @Shared @Cached CStructAccess.FreeNode freeNode) {
            for (int i = 0; i < cachedLen; i++) {
                Object elementPointer = readNode.readArrayElement(storage.getPtr(), i);
                decRefPointerNode.execute(inliningTarget, elementPointer);
            }
            // in this case, the runtime still exclusively owns the memory
            freeNode.free(storage.getPtr());
        }

        @Specialization(replaces = "doObjectCachedLen")
        static void doObjectGeneric(NativeObjectSequenceStorage storage,
                        @Bind("this") Node inliningTarget,
                        @Shared @Cached CStructAccess.ReadPointerNode readNode,
                        @Shared @Cached CExtNodes.XDecRefPointerNode decRefPointerNode,
                        @Shared @Cached CStructAccess.FreeNode freeNode) {
            for (int i = 0; i < storage.length(); i++) {
                Object elementPointer = readNode.readArrayElement(storage.getPtr(), i);
                decRefPointerNode.execute(inliningTarget, elementPointer);
            }
            // in this case, the runtime still exclusively owns the memory
            freeNode.free(storage.getPtr());
        }
    }

    /**
     * Special helper nodes that materializes any primitive that would leak the wrapper if the
     * reference is owned by managed code only.
     */
    @TypeSystemReference(PythonTypes.class)
    @GenerateInline(false)
    abstract static class MaterializePrimitiveNode extends Node {

        public abstract Object execute(PythonObjectFactory factory, Object object);

        // NOTE: Booleans don't need to be materialized because they are singletons.

        @Specialization
        static PInt doInteger(PythonObjectFactory factory, int i) {
            return factory.createInt(i);
        }

        @Specialization(replaces = "doInteger")
        static PInt doLong(PythonObjectFactory factory, long l) {
            return factory.createInt(l);
        }

        @Specialization
        static PFloat doDouble(PythonObjectFactory factory, double d) {
            return factory.createFloat(d);
        }

        @Specialization
        static PString doString(PythonObjectFactory factory, TruffleString s) {
            return factory.createString(s);
        }

        @Specialization(guards = "!needsMaterialization(object)")
        static Object doObject(@SuppressWarnings("unused") PythonObjectFactory factory, Object object) {
            return object;
        }

        static boolean needsMaterialization(Object object) {
            return object instanceof Integer || object instanceof Long || PGuards.isDouble(object) || object instanceof TruffleString;
        }
    }

    // roughly equivalent to _Py_CheckFunctionResult in Objects/call.c
    @ImportStatic(PGuards.class)
    @GenerateUncached
    @GenerateInline(false)
    public abstract static class DefaultCheckFunctionResultNode extends CheckFunctionResultNode {

        @Specialization
        static Object doNativeWrapper(PythonThreadState state, TruffleString name, @SuppressWarnings("unused") PythonNativeWrapper result,
                        @Bind("this") Node inliningTarget,
                        @Shared @Cached TransformExceptionFromNativeNode transformExceptionFromNativeNode) {
            transformExceptionFromNativeNode.execute(inliningTarget, state, name, false, true);
            return result;
        }

        @Specialization(guards = "isNoValue(result)")
        static Object doNoValue(PythonThreadState state, TruffleString name, @SuppressWarnings("unused") PNone result,
                        @Bind("this") Node inliningTarget,
                        @Shared @Cached TransformExceptionFromNativeNode transformExceptionFromNativeNode) {
            transformExceptionFromNativeNode.execute(inliningTarget, state, name, true, true);
            return PNone.NO_VALUE;
        }

        @Specialization(guards = "!isNoValue(result)")
        static Object doPythonObject(PythonThreadState state, TruffleString name, @SuppressWarnings("unused") PythonAbstractObject result,
                        @Bind("this") Node inliningTarget,
                        @Shared @Cached TransformExceptionFromNativeNode transformExceptionFromNativeNode) {
            transformExceptionFromNativeNode.execute(inliningTarget, state, name, false, true);
            return result;
        }

        @Specialization
        static Object doNativePointer(PythonThreadState state, TruffleString name, NativePointer result,
                        @Bind("this") Node inliningTarget,
                        @Shared @Cached TransformExceptionFromNativeNode transformExceptionFromNativeNode) {
            transformExceptionFromNativeNode.execute(inliningTarget, state, name, result.isNull(), true);
            return result;
        }

        @Specialization
        static int doInteger(PythonThreadState state, TruffleString name, int result,
                        @Bind("this") Node inliningTarget,
                        @Shared @Cached TransformExceptionFromNativeNode transformExceptionFromNativeNode) {
            /*
             * If the native functions returns a primitive int, only a value '-1' indicates an
             * error. However, '-1' may also be a valid return value. So, don't be strict.
             */
            transformExceptionFromNativeNode.execute(inliningTarget, state, name, result == -1, false);
            return result;
        }

        @Specialization
        static long doLong(PythonThreadState state, TruffleString name, long result,
                        @Bind("this") Node inliningTarget,
                        @Shared @Cached TransformExceptionFromNativeNode transformExceptionFromNativeNode) {
            /*
             * If the native functions returns a primitive long, only a value '-1' indicates an
             * error. However, '-1' may also be a valid return value. So, don't be strict.
             */
            transformExceptionFromNativeNode.execute(inliningTarget, state, name, result == -1, false);
            return result;
        }

        /*
         * Our fallback case, but with some cached params. PythonNativeWrapper results should be
         * unwrapped and recursively delegated (see #doNativeWrapper) and PNone is treated
         * specially, because we consider it as null in #doNoValue and as not null in
         * #doPythonObject
         */
        @Specialization(guards = {"!isPythonNativeWrapper(result)", "!isPNone(result)"})
        static Object doForeign(PythonThreadState state, TruffleString name, Object result,
                        @Bind("this") Node inliningTarget,
                        @Shared @Cached TransformExceptionFromNativeNode transformExceptionFromNativeNode,
                        @Exclusive @CachedLibrary(limit = "3") InteropLibrary lib) {
            transformExceptionFromNativeNode.execute(inliningTarget, state, name, lib.isNull(result), true);
            return result;
        }

        protected static boolean isPythonNativeWrapper(Object object) {
            return object instanceof PythonNativeWrapper;
        }
    }

    // roughly equivalent to _Py_CheckFunctionResult in Objects/call.c
    @ImportStatic(PGuards.class)
    @GenerateUncached
    @GenerateInline(false)
    public abstract static class PyObjectCheckFunctionResultNode extends CheckFunctionResultNode {
        @Specialization(guards = "!isForeignObject.execute(inliningTarget, result)")
        static Object doPythonObject(PythonThreadState state, TruffleString name, Object result,
                        @Bind("this") Node inliningTarget,
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
                        @Bind("this") Node inliningTarget,
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

        @Specialization(limit = "3")
        static Object doGeneric(PythonThreadState state, @SuppressWarnings("unused") TruffleString name, Object result,
                        @Bind("this") Node inliningTarget,
                        @CachedLibrary("result") InteropLibrary lib,
                        @Cached ClearCurrentExceptionNode clearCurrentExceptionNode,
                        @Cached PRaiseNode raiseNode) {
            if (lib.isNull(result)) {
                PException currentException = state.getCurrentException();
                // if no exception occurred, the iterator is exhausted -> raise StopIteration
                if (currentException == null) {
                    throw raiseNode.raiseStopIteration();
                } else {
                    throw clearCurrentExceptionNode.getCurrentExceptionForReraise(inliningTarget, state);
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
        @Specialization
        @SuppressWarnings("unused")
        static Object doInt(PythonThreadState state, TruffleString name, int result,
                        @Bind("this") Node inliningTarget,
                        @Shared @Cached TransformExceptionFromNativeNode transformExceptionFromNativeNode) {
            transformExceptionFromNativeNode.execute(inliningTarget, state, name, result < 0, true);
            return PNone.NONE;
        }

        // Slow path
        @Specialization(replaces = "doInt")
        @InliningCutoff
        static Object notNumber(PythonThreadState state, @SuppressWarnings("unused") TruffleString name, Object result,
                        @Bind("this") Node inliningTarget,
                        @CachedLibrary(limit = "2") InteropLibrary lib,
                        @Shared @Cached TransformExceptionFromNativeNode transformExceptionFromNativeNode) {
            int ret = 0;
            if (lib.isNumber(result)) {
                try {
                    ret = lib.asInt(result);
                    if (ret >= 0) {
                        return PNone.NONE;
                    }
                } catch (UnsupportedMessageException e) {
                    throw CompilerDirectives.shouldNotReachHere(e);
                }
            }
            transformExceptionFromNativeNode.execute(inliningTarget, state, name, ret < 0, true);
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
    @ImportStatic(PGuards.class)
    @GenerateUncached
    @GenerateInline(false)
    public abstract static class CheckPrimitiveFunctionResultNode extends CheckFunctionResultNode {
        public abstract long executeLong(PythonThreadState threadState, TruffleString name, Object result);

        @Specialization
        static long doLong(PythonThreadState threadState, TruffleString name, long result,
                        @Bind("this") Node inliningTarget,
                        @Shared @Cached TransformExceptionFromNativeNode transformExceptionFromNativeNode) {
            transformExceptionFromNativeNode.execute(inliningTarget, threadState, name, result == -1, false);
            return result;
        }

        @Specialization(replaces = "doLong")
        @InliningCutoff
        static long doGeneric(PythonThreadState threadState, TruffleString name, Object result,
                        @Bind("this") Node inliningTarget,
                        @CachedLibrary(limit = "2") InteropLibrary lib,
                        @Shared @Cached TransformExceptionFromNativeNode transformExceptionFromNativeNode) {
            if (lib.fitsInLong(result)) {
                try {
                    long ret = lib.asLong(result);
                    transformExceptionFromNativeNode.execute(inliningTarget, threadState, name, ret == -1, false);
                    return ret;
                } catch (UnsupportedMessageException e) {
                    throw CompilerDirectives.shouldNotReachHere(e);
                }
            }
            throw CompilerDirectives.shouldNotReachHere("expected primitive function result but does not fit into Java long");
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

        public abstract boolean executeBool(PythonThreadState threadState, TruffleString name, Object result);

        @Specialization
        static boolean doLong(PythonThreadState threadState, TruffleString name, long result,
                        @Bind("this") Node inliningTarget,
                        @Shared @Cached InlinedConditionProfile resultProfile,
                        @Shared @Cached TransformExceptionFromNativeNode transformExceptionFromNativeNode) {
            transformExceptionFromNativeNode.execute(inliningTarget, threadState, name, result == -1, false);
            return resultProfile.profile(inliningTarget, result != 0);
        }

        @Specialization(replaces = "doLong")
        @InliningCutoff
        static boolean doGeneric(PythonThreadState threadState, TruffleString name, Object result,
                        @Bind("this") Node inliningTarget,
                        @Shared @Cached InlinedConditionProfile resultProfile,
                        @CachedLibrary(limit = "3") InteropLibrary lib,
                        @Shared @Cached TransformExceptionFromNativeNode transformExceptionFromNativeNode) {
            if (lib.fitsInLong(result)) {
                try {
                    long lresult = lib.asLong(result);
                    transformExceptionFromNativeNode.execute(inliningTarget, threadState, name, lresult == -1, false);
                    return resultProfile.profile(inliningTarget, lresult != 0);
                } catch (UnsupportedMessageException e) {
                    throw CompilerDirectives.shouldNotReachHere();
                }
            }
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw PRaiseNode.raiseUncached(inliningTarget, SystemError, ErrorMessages.FUNC_DIDNT_RETURN_INT, name);
        }
    }
}
