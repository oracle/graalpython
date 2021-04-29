/*
 * Copyright (c) 2018, 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.modules;

import java.util.Arrays;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.modules.ExternalFunctionNodesFactory.CreateArgsTupleNodeGen;
import com.oracle.graal.python.builtins.modules.ExternalFunctionNodesFactory.MaterializePrimitiveNodeGen;
import com.oracle.graal.python.builtins.modules.ExternalFunctionNodesFactory.ReleaseNativeWrapperNodeGen;
import com.oracle.graal.python.builtins.modules.PythonCextBuiltinsFactory.CheckInquiryResultNodeGen;
import com.oracle.graal.python.builtins.modules.PythonCextBuiltinsFactory.CheckIterNextResultNodeGen;
import com.oracle.graal.python.builtins.modules.PythonCextBuiltinsFactory.DefaultCheckFunctionResultNodeGen;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.cext.capi.CApiGuards;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.AllToSulongNode;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.BinaryFirstToSulongNode;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.ConvertArgsToSulongNode;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.FastCallArgsToSulongNode;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.FastCallWithKeywordsArgsToSulongNode;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.PCallCapiFunction;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.SSizeArgProcToSulongNode;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.SSizeObjArgProcToSulongNode;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.SubRefCntNode;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.TernaryFirstSecondToSulongNode;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.TernaryFirstThirdToSulongNode;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.ToBorrowedRefNode;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.ToJavaStealingNode;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodesFactory.ToBorrowedRefNodeGen;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodesFactory.ToJavaStealingNodeGen;
import com.oracle.graal.python.builtins.objects.cext.capi.DynamicObjectNativeWrapper;
import com.oracle.graal.python.builtins.objects.cext.capi.NativeCAPISymbol;
import com.oracle.graal.python.builtins.objects.cext.capi.PythonNativeWrapper;
import com.oracle.graal.python.builtins.objects.cext.common.CExtCommonNodes.CheckFunctionResultNode;
import com.oracle.graal.python.builtins.objects.cext.common.CExtCommonNodes.ConvertPIntToPrimitiveNode;
import com.oracle.graal.python.builtins.objects.cext.common.CExtCommonNodes.GetIndexNode;
import com.oracle.graal.python.builtins.objects.cext.common.CExtCommonNodesFactory.ConvertPIntToPrimitiveNodeGen;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes.ToArrayNode;
import com.oracle.graal.python.builtins.objects.floats.PFloat;
import com.oracle.graal.python.builtins.objects.function.PArguments;
import com.oracle.graal.python.builtins.objects.function.PBuiltinFunction;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.builtins.objects.function.Signature;
import com.oracle.graal.python.builtins.objects.ints.PInt;
import com.oracle.graal.python.builtins.objects.object.PythonObject;
import com.oracle.graal.python.builtins.objects.str.PString;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.IndirectCallNode;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.PRootNode;
import com.oracle.graal.python.nodes.SpecialMethodNames;
import com.oracle.graal.python.nodes.argument.ReadIndexedArgumentNode;
import com.oracle.graal.python.nodes.argument.ReadVarArgsNode;
import com.oracle.graal.python.nodes.argument.ReadVarKeywordsNode;
import com.oracle.graal.python.nodes.call.special.CallVarargsMethodNode;
import com.oracle.graal.python.nodes.interop.PForeignToPTypeNode;
import com.oracle.graal.python.nodes.truffle.PythonTypes;
import com.oracle.graal.python.runtime.ExecutionContext.CalleeContext;
import com.oracle.graal.python.runtime.ExecutionContext.IndirectCallContext;
import com.oracle.graal.python.runtime.GilNode;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.graal.python.util.Function;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.graal.python.util.Supplier;
import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleLanguage.ContextReference;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.TypeSystemReference;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.ExplodeLoop.LoopExplosionKind;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.NodeCost;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.nodes.UnexpectedResultException;
import com.oracle.truffle.api.profiles.ConditionProfile;

public abstract class ExternalFunctionNodes {

    private static final String KW_CALLABLE = "$callable";
    private static final String KW_CLOSURE = "$closure";
    private static final String[] KEYWORDS_HIDDEN_CALLABLE = new String[]{KW_CALLABLE};
    private static final String[] KEYWORDS_HIDDEN_CALLABLE_AND_CLOSURE = new String[]{KW_CALLABLE, KW_CLOSURE};

    public static PKeyword[] createKwDefaults(Object callable) {
        return new PKeyword[]{new PKeyword(ExternalFunctionNodes.KW_CALLABLE, callable)};
    }

    public static PKeyword[] createKwDefaults(Object callable, Object closure) {
        return new PKeyword[]{new PKeyword(ExternalFunctionNodes.KW_CALLABLE, callable), new PKeyword(ExternalFunctionNodes.KW_CLOSURE, closure)};
    }

    /**
     * Enum of well-known function and slot signatures. The integer values must stay in sync with
     * the definition in {code capi.h}.
     */
    public enum PExternalFunctionWrapper {
        DIRECT(1),
        FASTCALL(2, FastCallArgsToSulongNode::create),
        FASTCALL_WITH_KEYWORDS(3, FastCallWithKeywordsArgsToSulongNode::create),
        KEYWORDS(4),               // METH_VARARGS | METH_KEYWORDS
        VARARGS(5),                // METH_VARARGS
        NOARGS(6),                 // METH_NOARGS
        O(7),                      // METH_O
        ALLOC(9, BinaryFirstToSulongNode::create),
        GETATTR(10, BinaryFirstToSulongNode::create),
        SETATTR(11, TernaryFirstThirdToSulongNode::create),
        RICHCMP(12, TernaryFirstSecondToSulongNode::create),
        SETITEM(13, SSizeObjArgProcToSulongNode::create),
        REVERSE(14),
        POW(15),
        REVERSE_POW(16),
        LT(17, TernaryFirstSecondToSulongNode::create),
        LE(18, TernaryFirstSecondToSulongNode::create),
        EQ(19, TernaryFirstSecondToSulongNode::create),
        NE(20, TernaryFirstSecondToSulongNode::create),
        GT(21, TernaryFirstSecondToSulongNode::create),
        GE(22, TernaryFirstSecondToSulongNode::create),
        ITERNEXT(23, 0, AllToSulongNode::create, CheckIterNextResultNodeGen::create),
        INQUIRY(24, 0, AllToSulongNode::create, CheckInquiryResultNodeGen::create),
        DELITEM(25, 1, SSizeObjArgProcToSulongNode::create),
        GETITEM(26, 0, SSizeArgProcToSulongNode::create),
        GETTER(27, BinaryFirstToSulongNode::create),
        SETTER(28, TernaryFirstSecondToSulongNode::create);

        @CompilationFinal(dimensions = 1) private static final PExternalFunctionWrapper[] VALUES = Arrays.copyOf(values(), values().length);

        PExternalFunctionWrapper(int value, int numDefaults, Supplier<ConvertArgsToSulongNode> convertArgsNodeSupplier, Supplier<CheckFunctionResultNode> checkFunctionResultNodeSupplier) {
            this.value = value;
            this.numDefaults = numDefaults;
            this.convertArgsNodeSupplier = convertArgsNodeSupplier;
            this.checkFunctionResultNodeSupplier = checkFunctionResultNodeSupplier;
        }

        PExternalFunctionWrapper(int value, Supplier<ConvertArgsToSulongNode> convertArgsNodeSupplier) {
            this(value, 0, convertArgsNodeSupplier, DefaultCheckFunctionResultNodeGen::create);
        }

        PExternalFunctionWrapper(int value, int numDefaults, Supplier<ConvertArgsToSulongNode> convertArgsNodeSupplier) {
            this(value, numDefaults, convertArgsNodeSupplier, DefaultCheckFunctionResultNodeGen::create);
        }

        PExternalFunctionWrapper(int value) {
            this(value, 0, AllToSulongNode::create, DefaultCheckFunctionResultNodeGen::create);
        }

        @ExplodeLoop
        static PExternalFunctionWrapper fromValue(int value) {
            for (int i = 0; i < VALUES.length; i++) {
                if (VALUES[i].value == value) {
                    return VALUES[i];
                }
            }
            return null;
        }

        private final int value;
        private final int numDefaults;
        private final Supplier<ConvertArgsToSulongNode> convertArgsNodeSupplier;
        private final Supplier<CheckFunctionResultNode> checkFunctionResultNodeSupplier;

        @TruffleBoundary
        static RootCallTarget getOrCreateCallTarget(PExternalFunctionWrapper sig, PythonLanguage language, String name, boolean doArgAndResultConversion) {
            Class<?> nodeKlass;
            Function<PythonLanguage, RootNode> rootNodeFunction;
            switch (sig) {
                case ALLOC:
                    nodeKlass = AllocFuncRootNode.class;
                    rootNodeFunction = doArgAndResultConversion ? l -> new AllocFuncRootNode(l, name, sig) : l -> new AllocFuncRootNode(l, name);
                    break;
                case DIRECT:
                    /*
                     * If no conversion is requested, this means we directly call a managed function
                     * (without argument conversion). Null indicates this
                     */
                    if (!doArgAndResultConversion) {
                        return null;
                    }
                    nodeKlass = MethDirectRoot.class;
                    rootNodeFunction = l -> MethDirectRoot.create(language, name);
                    break;
                case KEYWORDS:
                    nodeKlass = MethKeywordsRoot.class;
                    rootNodeFunction = doArgAndResultConversion ? l -> new MethKeywordsRoot(l, name, sig) : l -> new MethKeywordsRoot(l, name);
                    break;
                case VARARGS:
                    nodeKlass = MethVarargsRoot.class;
                    rootNodeFunction = doArgAndResultConversion ? l -> new MethVarargsRoot(l, name, sig) : l -> new MethVarargsRoot(l, name);
                    break;
                case NOARGS:
                case INQUIRY:
                    nodeKlass = MethNoargsRoot.class;
                    rootNodeFunction = doArgAndResultConversion ? l -> new MethNoargsRoot(l, name, sig) : l -> new MethNoargsRoot(l, name);
                    break;
                case O:
                    nodeKlass = MethORoot.class;
                    rootNodeFunction = doArgAndResultConversion ? l -> new MethORoot(l, name, sig) : l -> new MethORoot(l, name);
                    break;
                case FASTCALL:
                    nodeKlass = MethFastcallRoot.class;
                    rootNodeFunction = doArgAndResultConversion ? l -> new MethFastcallRoot(l, name, sig) : l -> new MethFastcallRoot(l, name);
                    break;
                case FASTCALL_WITH_KEYWORDS:
                    nodeKlass = MethFastcallWithKeywordsRoot.class;
                    rootNodeFunction = doArgAndResultConversion ? l -> new MethFastcallWithKeywordsRoot(l, name, sig) : l -> new MethFastcallWithKeywordsRoot(l, name);
                    break;
                case GETATTR:
                    nodeKlass = GetAttrFuncRootNode.class;
                    rootNodeFunction = doArgAndResultConversion ? l -> new GetAttrFuncRootNode(l, name, sig) : l -> new GetAttrFuncRootNode(l, name);
                    break;
                case SETATTR:
                    nodeKlass = SetAttrFuncRootNode.class;
                    rootNodeFunction = doArgAndResultConversion ? l -> new SetAttrFuncRootNode(l, name, sig) : l -> new SetAttrFuncRootNode(l, name);
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
                case REVERSE:
                    nodeKlass = MethReverseRootNode.class;
                    rootNodeFunction = doArgAndResultConversion ? l -> new MethReverseRootNode(l, name, sig) : l -> new MethReverseRootNode(l, name);
                    break;
                case POW:
                    nodeKlass = MethPowRootNode.class;
                    rootNodeFunction = doArgAndResultConversion ? l -> new MethPowRootNode(l, name, sig) : l -> new MethPowRootNode(l, name);
                    break;
                case REVERSE_POW:
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
                default:
                    throw CompilerDirectives.shouldNotReachHere();
            }
            return language.createCachedCallTarget(rootNodeFunction, nodeKlass, sig, name, doArgAndResultConversion);
        }

        public static PBuiltinFunction createWrapperFunction(int sig, PythonObjectFactory factory, PythonLanguage language, String name, Object callable, Object enclosingType,
                        boolean doArgAndResultConversion) {
            return createWrapperFunction(PExternalFunctionWrapper.fromValue(sig), factory, language, name, callable, enclosingType, doArgAndResultConversion);
        }

        /**
         * Creates a built-in function for a specific signature. This built-in function also does
         * appropriate argument and result conversion and calls the provided callable.
         *
         * @param language The Python language object.
         * @param sig The wrapper/signature ID as defined in {@link PExternalFunctionWrapper}.
         * @param name The name of the method.
         * @param callable The native function pointer.
         * @param enclosingType The type the function belongs to (needed for checking of
         *            {@code self}).
         * @param factory Just an instance of {@link PythonObjectFactory} to create the function
         *            object.
         * @return A {@link PBuiltinFunction} implementing the semantics of the specified slot
         *         wrapper.
         */
        @TruffleBoundary
        public static PBuiltinFunction createWrapperFunction(PExternalFunctionWrapper sig, PythonObjectFactory factory, PythonLanguage language, String name, Object callable, Object enclosingType,
                        boolean doArgAndResultConversion) {
            RootCallTarget callTarget = getOrCreateCallTarget(sig, language, name, doArgAndResultConversion);
            if (callTarget == null) {
                return null;
            }
            Object[] defaults;
            int numDefaults = sig.numDefaults;
            if (numDefaults > 0) {
                defaults = new Object[numDefaults];
                Arrays.fill(defaults, PNone.NO_VALUE);
            } else {
                defaults = PythonUtils.EMPTY_OBJECT_ARRAY;
            }
            Object type = SpecialMethodNames.__NEW__.equals(name) ? null : enclosingType;
            return factory.createBuiltinFunction(name, type, defaults, ExternalFunctionNodes.createKwDefaults(callable), callTarget);
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

        @TruffleBoundary
        ConvertArgsToSulongNode createConvertArgsToSulongNode() {
            return convertArgsNodeSupplier.get();
        }

        @TruffleBoundary
        CheckFunctionResultNode getCheckFunctionResultNode() {
            return checkFunctionResultNodeSupplier.get();
        }
    }

    public static final class MethDirectRoot extends PRootNode {

        private static final Signature SIGNATURE = new Signature(-1, true, 0, false, null, KEYWORDS_HIDDEN_CALLABLE);

        @Child private ExternalFunctionInvokeNode invokeNode;
        @Child private CalleeContext calleeContext = CalleeContext.create();
        @Child private ReadIndexedArgumentNode readCallableNode = ReadIndexedArgumentNode.create(0);

        private final String name;

        private MethDirectRoot(PythonLanguage lang, String name) {
            super(lang);
            this.name = name;
            this.invokeNode = ExternalFunctionInvokeNode.create();
        }

        @Override
        public Object execute(VirtualFrame frame) {
            calleeContext.enter(frame);
            try {
                Object callable = readCallableNode.execute(frame);
                return ensureInvokeNode().execute(frame, name, callable, PArguments.getVariableArguments(frame), 0);
            } finally {
                calleeContext.exit(frame, this);
            }
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public String toString() {
            return "<external function root " + getName() + ">";
        }

        @Override
        public boolean isCloningAllowed() {
            return true;
        }

        @Override
        public Signature getSignature() {
            return SIGNATURE;
        }

        @Override
        public boolean isPythonInternal() {
            // everything that is implemented in C is internal
            return true;
        }

        private ExternalFunctionInvokeNode ensureInvokeNode() {
            if (invokeNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                invokeNode = insert(ExternalFunctionInvokeNode.create());
            }
            return invokeNode;
        }

        @TruffleBoundary
        public static MethDirectRoot create(PythonLanguage lang, String name) {
            return new MethDirectRoot(lang, name);
        }
    }

    /**
     * Like {@link com.oracle.graal.python.nodes.call.FunctionInvokeNode} but invokes a C function.
     */
    static final class ExternalFunctionInvokeNode extends PNodeWithContext implements IndirectCallNode {
        @Child private CExtNodes.ConvertArgsToSulongNode toSulongNode;
        @Child private CheckFunctionResultNode checkResultNode;
        @Child private PForeignToPTypeNode fromForeign = PForeignToPTypeNode.create();
        @Child private ToJavaStealingNode asPythonObjectNode = ToJavaStealingNodeGen.create();
        @Child private InteropLibrary lib;
        @Child private PRaiseNode raiseNode;
        @Child private GilNode gil;

        @CompilationFinal private Assumption nativeCodeDoesntNeedExceptionState = Truffle.getRuntime().createAssumption();
        @CompilationFinal private Assumption nativeCodeDoesntNeedMyFrame = Truffle.getRuntime().createAssumption();
        @CompilationFinal private ContextReference<PythonContext> contextRef;

        @Override
        public final Assumption needNotPassFrameAssumption() {
            return nativeCodeDoesntNeedMyFrame;
        }

        @Override
        public final Assumption needNotPassExceptionAssumption() {
            return nativeCodeDoesntNeedExceptionState;
        }

        @Override
        public Node copy() {
            ExternalFunctionInvokeNode node = (ExternalFunctionInvokeNode) super.copy();
            node.nativeCodeDoesntNeedMyFrame = Truffle.getRuntime().createAssumption();
            node.nativeCodeDoesntNeedExceptionState = Truffle.getRuntime().createAssumption();
            return node;
        }

        @TruffleBoundary
        ExternalFunctionInvokeNode() {
            this.toSulongNode = CExtNodes.AllToSulongNode.create();
            this.checkResultNode = DefaultCheckFunctionResultNodeGen.create();
        }

        @TruffleBoundary
        ExternalFunctionInvokeNode(PExternalFunctionWrapper provider) {
            ConvertArgsToSulongNode convertArgsNode = provider.createConvertArgsToSulongNode();
            this.toSulongNode = convertArgsNode != null ? convertArgsNode : CExtNodes.AllToSulongNode.create();
            CheckFunctionResultNode checkFunctionResultNode = provider.getCheckFunctionResultNode();
            this.checkResultNode = checkFunctionResultNode != null ? checkFunctionResultNode : DefaultCheckFunctionResultNodeGen.create();
        }

        public Object execute(VirtualFrame frame, String name, Object callable, Object[] frameArgs, int argsOffset) {
            if (lib == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                /*
                 * We must use a dispatched library because we cannot be sure that we always see the
                 * same type of callable. For example, in multi-context mode you could see an LLVM
                 * native pointer and an LLVM managed pointer.
                 */
                lib = insert(InteropLibrary.getFactory().createDispatched(2));
            }

            Object[] cArguments = new Object[frameArgs.length - argsOffset];
            toSulongNode.executeInto(frameArgs, argsOffset, cArguments, 0);

            PythonContext ctx = getContext();

            // If any code requested the caught exception (i.e. used 'sys.exc_info()'), we store
            // it to the context since we cannot propagate it through the native frames.
            Object state = IndirectCallContext.enter(frame, ctx, this);

            if (gil == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                gil = insert(GilNode.create());
            }
            try {
                return fromNative(asPythonObjectNode.execute(checkResultNode.execute(ctx, name, lib.execute(callable, cArguments))));
            } catch (UnsupportedTypeException | UnsupportedMessageException e) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw ensureRaiseNode().raise(PythonBuiltinClassType.TypeError, ErrorMessages.CALLING_NATIVE_FUNC_FAILED, name, e);
            } catch (ArityException e) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw ensureRaiseNode().raise(PythonBuiltinClassType.TypeError, ErrorMessages.CALLING_NATIVE_FUNC_EXPECTED_ARGS, name, e.getExpectedMinArity(), e.getActualArity());
            } finally {
                // special case after calling a C function: transfer caught exception back to frame
                // to simulate the global state semantics
                PArguments.setException(frame, ctx.getCaughtException());
                IndirectCallContext.exit(frame, ctx, state);
            }
        }

        private Object fromNative(Object result) {
            return fromForeign.executeConvert(result);
        }

        private PRaiseNode ensureRaiseNode() {
            if (raiseNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                raiseNode = insert(PRaiseNode.create());
            }
            return raiseNode;
        }

        private PythonContext getContext() {
            if (contextRef == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                contextRef = lookupContextReference(PythonLanguage.class);
            }
            return contextRef.get();
        }

        public static ExternalFunctionInvokeNode create() {
            return new ExternalFunctionInvokeNode();
        }

        public static ExternalFunctionInvokeNode create(PExternalFunctionWrapper provider) {
            return new ExternalFunctionInvokeNode(provider);
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
    abstract static class ReleaseNativeWrapperNode extends Node {

        public abstract void execute(Object pythonObject);

        @Specialization(guards = "hasNativeWrapper(pythonObject)")
        static void doPythonObjectWithWrapper(PythonObject pythonObject,
                        @Cached TraverseNativeWrapperNode traverseNativeWrapperNode,
                        @Cached SubRefCntNode subRefCntNode) {

            // in the cached case, refCntNode acts as a branch profile
            PythonNativeWrapper nativeWrapper = pythonObject.getNativeWrapper();
            if (subRefCntNode.dec(nativeWrapper) == 0) {
                traverseNativeWrapperNode.execute(pythonObject);
            }
        }

        @Specialization(guards = "!hasNativeWrapper(object)")
        @SuppressWarnings("unused")
        static void doObjectWithoutWrapper(Object object) {
            // just do nothing; this is an implicit profile
        }

        static boolean hasNativeWrapper(Object object) {
            return object instanceof PythonObject && CApiGuards.isNativeWrapper(((PythonObject) object).getNativeWrapper());
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

    abstract static class MethodDescriptorRoot extends PRootNode {
        @Child private CalleeContext calleeContext = CalleeContext.create();
        @Child private CallVarargsMethodNode invokeNode;
        @Child private ExternalFunctionInvokeNode externalInvokeNode;
        @Child ReadIndexedArgumentNode readSelfNode = ReadIndexedArgumentNode.create(0);
        @Child private ReadIndexedArgumentNode readCallableNode;
        @Child private ReleaseNativeWrapperNode releaseNativeWrapperNode;

        private final String name;

        MethodDescriptorRoot(PythonLanguage language, String name) {
            this(language, name, null);
        }

        @TruffleBoundary
        MethodDescriptorRoot(PythonLanguage language, String name, PExternalFunctionWrapper provider) {
            super(language);
            this.name = name;
            if (provider != null) {
                this.externalInvokeNode = ExternalFunctionInvokeNode.create(provider);
            } else {
                this.invokeNode = CallVarargsMethodNode.create();
            }
        }

        @Override
        public Object execute(VirtualFrame frame) {
            calleeContext.enter(frame);
            try {
                Object callable = ensureReadCallableNode().execute(frame);
                if (externalInvokeNode != null) {
                    Object[] cArguments = prepareCArguments(frame);
                    try {
                        return externalInvokeNode.execute(frame, name, callable, cArguments, 0);
                    } finally {
                        postprocessCArguments(frame, cArguments);
                    }
                } else {
                    assert externalInvokeNode == null;
                    return invokeNode.execute(frame, callable, preparePArguments(frame), PArguments.getKeywordArguments(frame));
                }
            } finally {
                calleeContext.exit(frame, this);
            }
        }

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

        static Object[] copyPArguments(VirtualFrame frame) {
            return copyPArguments(frame, PArguments.getUserArgumentLength(frame));
        }

        static Object[] copyPArguments(VirtualFrame frame, int newUserArgumentLength) {
            Object[] objects = PArguments.create(newUserArgumentLength);
            PArguments.setGlobals(objects, PArguments.getGlobals(frame));
            PArguments.setClosure(objects, PArguments.getClosure(frame));
            PArguments.setSpecialArgument(objects, PArguments.getSpecialArgument(frame));
            return objects;
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

        protected ReleaseNativeWrapperNode ensureReleaseNativeWrapperNode() {
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
            return name;
        }

        @Override
        public NodeCost getCost() {
            // this is just a thin argument shuffling wrapper
            return NodeCost.NONE;
        }

        @Override
        public String toString() {
            return "<METH root " + name + ">";
        }

        @Override
        public boolean isPythonInternal() {
            return true;
        }
    }

    public static final class MethKeywordsRoot extends MethodDescriptorRoot {
        private static final Signature SIGNATURE = new Signature(-1, true, 1, false, new String[]{"self"}, KEYWORDS_HIDDEN_CALLABLE, true);
        @Child private PythonObjectFactory factory;
        @Child private ReadVarArgsNode readVarargsNode;
        @Child private ReadVarKeywordsNode readKwargsNode;
        @Child private CreateArgsTupleNode createArgsTupleNode;

        public MethKeywordsRoot(PythonLanguage language, String name) {
            super(language, name);
        }

        public MethKeywordsRoot(PythonLanguage language, String name, PExternalFunctionWrapper provider) {
            super(language, name, provider);
            this.factory = PythonObjectFactory.create();
            this.readVarargsNode = ReadVarArgsNode.create(1, true);
            this.readKwargsNode = ReadVarKeywordsNode.create(PythonUtils.EMPTY_STRING_ARRAY);
            this.createArgsTupleNode = CreateArgsTupleNodeGen.create();
        }

        @Override
        protected Object[] prepareCArguments(VirtualFrame frame) {
            Object self = readSelfNode.execute(frame);
            Object[] args = readVarargsNode.executeObjectArray(frame);
            PKeyword[] kwargs = readKwargsNode.executePKeyword(frame);
            return new Object[]{self, createArgsTupleNode.execute(factory, args), factory.createDict(kwargs)};
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

    public static final class MethVarargsRoot extends MethodDescriptorRoot {
        private static final Signature SIGNATURE = new Signature(-1, false, 1, false, new String[]{"self"}, KEYWORDS_HIDDEN_CALLABLE, true);
        @Child private PythonObjectFactory factory;
        @Child private ReadVarArgsNode readVarargsNode;
        @Child private CreateArgsTupleNode createArgsTupleNode;

        public MethVarargsRoot(PythonLanguage language, String name) {
            super(language, name);
        }

        public MethVarargsRoot(PythonLanguage language, String name, PExternalFunctionWrapper provider) {
            super(language, name, provider);
            this.factory = PythonObjectFactory.create();
            this.readVarargsNode = ReadVarArgsNode.create(1, true);
            this.createArgsTupleNode = CreateArgsTupleNodeGen.create();
        }

        @Override
        protected Object[] prepareCArguments(VirtualFrame frame) {
            Object self = readSelfNode.execute(frame);
            Object[] args = readVarargsNode.executeObjectArray(frame);
            return new Object[]{self, createArgsTupleNode.execute(factory, args)};
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

    public static final class MethNoargsRoot extends MethodDescriptorRoot {
        private static final Signature SIGNATURE = new Signature(-1, false, -1, false, new String[]{"self"}, KEYWORDS_HIDDEN_CALLABLE, true);

        public MethNoargsRoot(PythonLanguage language, String name) {
            super(language, name);
        }

        public MethNoargsRoot(PythonLanguage language, String name, PExternalFunctionWrapper provider) {
            super(language, name, provider);
        }

        @Override
        protected Object[] prepareCArguments(VirtualFrame frame) {
            Object self = readSelfNode.execute(frame);
            return new Object[]{self, PNone.NONE};
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
        private static final Signature SIGNATURE = new Signature(-1, false, -1, false, new String[]{"self", "arg"}, KEYWORDS_HIDDEN_CALLABLE, true);
        @Child private ReadIndexedArgumentNode readArgNode;

        public MethORoot(PythonLanguage language, String name) {
            super(language, name);
        }

        public MethORoot(PythonLanguage language, String name, PExternalFunctionWrapper provider) {
            super(language, name, provider);
            this.readArgNode = ReadIndexedArgumentNode.create(1);
        }

        @Override
        protected Object[] prepareCArguments(VirtualFrame frame) {
            Object self = readSelfNode.execute(frame);
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
        private static final Signature SIGNATURE = new Signature(-1, true, 1, false, new String[]{"self"}, KEYWORDS_HIDDEN_CALLABLE, true);
        @Child private PythonObjectFactory factory;
        @Child private ReadVarArgsNode readVarargsNode;
        @Child private ReadVarKeywordsNode readKwargsNode;

        public MethFastcallWithKeywordsRoot(PythonLanguage language, String name) {
            super(language, name);
        }

        public MethFastcallWithKeywordsRoot(PythonLanguage language, String name, PExternalFunctionWrapper provider) {
            super(language, name, provider);
            this.factory = PythonObjectFactory.create();
            this.readVarargsNode = ReadVarArgsNode.create(1, true);
            this.readKwargsNode = ReadVarKeywordsNode.create(PythonUtils.EMPTY_STRING_ARRAY);
        }

        @Override
        protected Object[] prepareCArguments(VirtualFrame frame) {
            Object self = readSelfNode.execute(frame);
            Object[] args = readVarargsNode.executeObjectArray(frame);
            PKeyword[] kwargs = readKwargsNode.executePKeyword(frame);
            Object[] fastcallArgs = new Object[args.length + kwargs.length];
            Object[] fastcallKwnames = new Object[kwargs.length];
            PythonUtils.arraycopy(args, 0, fastcallArgs, 0, args.length);
            for (int i = 0; i < kwargs.length; i++) {
                fastcallKwnames[i] = kwargs[i].getName();
                fastcallArgs[args.length + i] = kwargs[i].getValue();
            }
            return new Object[]{self, factory.createTuple(fastcallArgs), args.length, factory.createTuple(fastcallKwnames)};
        }

        @Override
        protected void postprocessCArguments(VirtualFrame frame, Object[] cArguments) {
            ReleaseNativeWrapperNode releaseNativeWrapperNode = ensureReleaseNativeWrapperNode();
            releaseNativeWrapperNode.execute(cArguments[0]);
            releaseNativeWrapperNode.execute(cArguments[1]);
            releaseNativeWrapperNode.execute(cArguments[3]);
        }

        @Override
        public Signature getSignature() {
            return SIGNATURE;
        }
    }

    public static final class MethFastcallRoot extends MethodDescriptorRoot {
        private static final Signature SIGNATURE = new Signature(-1, false, 1, false, new String[]{"self"}, KEYWORDS_HIDDEN_CALLABLE, true);
        @Child private PythonObjectFactory factory;
        @Child private ReadVarArgsNode readVarargsNode;

        public MethFastcallRoot(PythonLanguage language, String name) {
            super(language, name);
        }

        public MethFastcallRoot(PythonLanguage language, String name, PExternalFunctionWrapper provider) {
            super(language, name, provider);
            this.factory = PythonObjectFactory.create();
            this.readVarargsNode = ReadVarArgsNode.create(1, true);
        }

        @Override
        protected Object[] prepareCArguments(VirtualFrame frame) {
            Object self = readSelfNode.execute(frame);
            Object[] args = readVarargsNode.executeObjectArray(frame);
            return new Object[]{self, factory.createTuple(args), args.length};
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
     * Wrapper root node for C function type {@code allocfunc} and {@code ssizeargfunc}.
     */
    static class AllocFuncRootNode extends MethodDescriptorRoot {
        private static final Signature SIGNATURE = new Signature(-1, false, -1, false, new String[]{"self", "nitems"}, KEYWORDS_HIDDEN_CALLABLE, true);
        @Child private ReadIndexedArgumentNode readArgNode;
        @Child private ConvertPIntToPrimitiveNode asSsizeTNode;

        AllocFuncRootNode(PythonLanguage language, String name) {
            super(language, name);
        }

        AllocFuncRootNode(PythonLanguage language, String name, PExternalFunctionWrapper provider) {
            super(language, name, provider);
            this.readArgNode = ReadIndexedArgumentNode.create(1);
            this.asSsizeTNode = ConvertPIntToPrimitiveNodeGen.create();
        }

        @Override
        protected Object[] prepareCArguments(VirtualFrame frame) {
            Object self = readSelfNode.execute(frame);
            Object arg = readArgNode.execute(frame);
            try {
                return new Object[]{self, asSsizeTNode.executeLong(arg, 1, Long.BYTES)};
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
        private static final Signature SIGNATURE = new Signature(-1, false, -1, false, new String[]{"self", "key"}, KEYWORDS_HIDDEN_CALLABLE, true);
        @Child private ReadIndexedArgumentNode readArgNode;
        @Child private CExtNodes.AsCharPointerNode asCharPointerNode;
        @Child private PCallCapiFunction callFreeNode;

        GetAttrFuncRootNode(PythonLanguage language, String name) {
            super(language, name);
        }

        GetAttrFuncRootNode(PythonLanguage language, String name, PExternalFunctionWrapper provider) {
            super(language, name, provider);
            this.readArgNode = ReadIndexedArgumentNode.create(1);
            this.asCharPointerNode = CExtNodes.AsCharPointerNode.create();
        }

        @Override
        protected Object[] prepareCArguments(VirtualFrame frame) {
            Object self = readSelfNode.execute(frame);
            Object arg = readArgNode.execute(frame);
            // TODO we should use 'CStringWrapper' for 'arg' but it does currently not support
            // PString
            return new Object[]{self, asCharPointerNode.execute(arg)};
        }

        @Override
        protected void postprocessCArguments(VirtualFrame frame, Object[] cArguments) {
            ensureReleaseNativeWrapperNode().execute(cArguments[0]);
            ensureCallFreeNode().call(NativeCAPISymbol.FUN_PY_TRUFFLE_FREE, cArguments[1]);
        }

        private PCallCapiFunction ensureCallFreeNode() {
            if (callFreeNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                callFreeNode = insert(PCallCapiFunction.create());
            }
            return callFreeNode;
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
        private static final Signature SIGNATURE = new Signature(-1, false, -1, false, new String[]{"self", "key", "value"}, KEYWORDS_HIDDEN_CALLABLE, true);
        @Child private ReadIndexedArgumentNode readArg1Node;
        @Child private ReadIndexedArgumentNode readArg2Node;
        @Child private CExtNodes.AsCharPointerNode asCharPointerNode;
        @Child private PCallCapiFunction callFreeNode;

        SetAttrFuncRootNode(PythonLanguage language, String name) {
            super(language, name);
        }

        SetAttrFuncRootNode(PythonLanguage language, String name, PExternalFunctionWrapper provider) {
            super(language, name, provider);
            this.readArg1Node = ReadIndexedArgumentNode.create(1);
            this.readArg2Node = ReadIndexedArgumentNode.create(2);
            this.asCharPointerNode = CExtNodes.AsCharPointerNode.create();
        }

        @Override
        protected Object[] prepareCArguments(VirtualFrame frame) {
            Object self = readSelfNode.execute(frame);
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
            ensureCallFreeNode().call(NativeCAPISymbol.FUN_PY_TRUFFLE_FREE, cArguments[1]);
            releaseNativeWrapperNode.execute(cArguments[2]);
        }

        private PCallCapiFunction ensureCallFreeNode() {
            if (callFreeNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                callFreeNode = insert(PCallCapiFunction.create());
            }
            return callFreeNode;
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
        private static final Signature SIGNATURE = new Signature(-1, false, -1, false, new String[]{"self", "other", "op"}, KEYWORDS_HIDDEN_CALLABLE, true);
        @Child private ReadIndexedArgumentNode readArg1Node;
        @Child private ReadIndexedArgumentNode readArg2Node;
        @Child private ConvertPIntToPrimitiveNode asSsizeTNode;

        RichCmpFuncRootNode(PythonLanguage language, String name) {
            super(language, name);
        }

        RichCmpFuncRootNode(PythonLanguage language, String name, PExternalFunctionWrapper provider) {
            super(language, name, provider);
            this.readArg1Node = ReadIndexedArgumentNode.create(1);
            this.readArg2Node = ReadIndexedArgumentNode.create(2);
            this.asSsizeTNode = ConvertPIntToPrimitiveNodeGen.create();
        }

        @Override
        protected Object[] prepareCArguments(VirtualFrame frame) {
            try {
                Object self = readSelfNode.execute(frame);
                Object arg1 = readArg1Node.execute(frame);
                Object arg2 = readArg2Node.execute(frame);
                return new Object[]{self, arg1, asSsizeTNode.executeInt(arg2, 1, Integer.BYTES)};
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
    static final class GetItemRootNode extends MethodDescriptorRoot {
        private static final Signature SIGNATURE = new Signature(-1, false, -1, false, new String[]{"self", "i"}, KEYWORDS_HIDDEN_CALLABLE, true);
        @Child private ReadIndexedArgumentNode readArg1Node;
        @Child private GetIndexNode getIndexNode;

        GetItemRootNode(PythonLanguage language, String name) {
            super(language, name);
        }

        GetItemRootNode(PythonLanguage language, String name, PExternalFunctionWrapper provider) {
            super(language, name, provider);
            this.readArg1Node = ReadIndexedArgumentNode.create(1);
            this.getIndexNode = GetIndexNode.create();
        }

        @Override
        protected Object[] prepareCArguments(VirtualFrame frame) {
            Object self = readSelfNode.execute(frame);
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
        private static final Signature SIGNATURE = new Signature(-1, false, -1, false, new String[]{"self", "i", "value"}, KEYWORDS_HIDDEN_CALLABLE, true);
        @Child private ReadIndexedArgumentNode readArg1Node;
        @Child private ReadIndexedArgumentNode readArg2Node;
        @Child private GetIndexNode getIndexNode;

        SetItemRootNode(PythonLanguage language, String name) {
            super(language, name);
        }

        SetItemRootNode(PythonLanguage language, String name, PExternalFunctionWrapper provider) {
            super(language, name, provider);
            this.readArg1Node = ReadIndexedArgumentNode.create(1);
            this.readArg2Node = ReadIndexedArgumentNode.create(2);
            this.getIndexNode = GetIndexNode.create();
        }

        @Override
        protected Object[] prepareCArguments(VirtualFrame frame) {
            Object self = readSelfNode.execute(frame);
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
     * Wrapper root node for reverse binary operations.
     */
    static final class MethReverseRootNode extends MethodDescriptorRoot {
        private static final Signature SIGNATURE = new Signature(-1, false, -1, false, new String[]{"self", "obj"}, KEYWORDS_HIDDEN_CALLABLE, true);
        @Child private ReadIndexedArgumentNode readArg0Node;
        @Child private ReadIndexedArgumentNode readArg1Node;

        MethReverseRootNode(PythonLanguage language, String name) {
            super(language, name);
            this.readArg0Node = ReadIndexedArgumentNode.create(0);
            this.readArg1Node = ReadIndexedArgumentNode.create(1);
        }

        MethReverseRootNode(PythonLanguage language, String name, PExternalFunctionWrapper provider) {
            super(language, name, provider);
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
        private static final Signature SIGNATURE = new Signature(false, 0, false, new String[]{"args"}, KEYWORDS_HIDDEN_CALLABLE);

        @Child private ReadVarArgsNode readVarargsNode;

        private final ConditionProfile profile;

        MethPowRootNode(PythonLanguage language, String name) {
            super(language, name);
            this.profile = null;
        }

        MethPowRootNode(PythonLanguage language, String name, PExternalFunctionWrapper provider) {
            super(language, name, provider);
            this.readVarargsNode = ReadVarArgsNode.create(1, true);
            this.profile = ConditionProfile.createBinaryProfile();
        }

        @Override
        protected final Object[] prepareCArguments(VirtualFrame frame) {
            Object self = readSelfNode.execute(frame);
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

        MethRPowRootNode(PythonLanguage language, String name) {
            super(language, name);
        }

        MethRPowRootNode(PythonLanguage language, String name, PExternalFunctionWrapper provider) {
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
        private static final Signature SIGNATURE = new Signature(-1, false, -1, false, new String[]{"self", "other"}, KEYWORDS_HIDDEN_CALLABLE, true);
        @Child private ReadIndexedArgumentNode readArgNode;

        private final int op;

        MethRichcmpOpRootNode(PythonLanguage language, String name, int op) {
            super(language, name);
            this.readArgNode = ReadIndexedArgumentNode.create(1);
            this.op = op;
        }

        MethRichcmpOpRootNode(PythonLanguage language, String name, PExternalFunctionWrapper provider, int op) {
            super(language, name, provider);
            this.readArgNode = ReadIndexedArgumentNode.create(1);
            this.op = op;
        }

        @Override
        protected Object[] prepareCArguments(VirtualFrame frame) {
            Object self = readSelfNode.execute(frame);
            Object arg = readArgNode.execute(frame);
            return new Object[]{self, arg, op};
        }

        @Override
        protected Object[] preparePArguments(VirtualFrame frame) {
            Object self = readSelfNode.execute(frame);
            Object arg = readArgNode.execute(frame);
            return new Object[]{self, arg, SpecialMethodNames.getCompareOpString(op)};
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

        IterNextFuncRootNode(PythonLanguage language, String name) {
            super(language, name);
        }

        IterNextFuncRootNode(PythonLanguage language, String name, PExternalFunctionWrapper provider) {
            super(language, name, provider);
        }

        @Override
        protected Object[] prepareCArguments(VirtualFrame frame) {
            return new Object[]{readSelfNode.execute(frame)};
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

        GetSetRootNode(PythonLanguage language, String name) {
            super(language, name);
        }

        GetSetRootNode(PythonLanguage language, String name, PExternalFunctionWrapper provider) {
            super(language, name, provider);
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
        private static final Signature SIGNATURE = new Signature(-1, false, -1, false, new String[]{"self"}, KEYWORDS_HIDDEN_CALLABLE_AND_CLOSURE, true);

        public GetterRoot(PythonLanguage language, String name, PExternalFunctionWrapper provider) {
            super(language, name, provider);
        }

        @Override
        protected Object[] prepareCArguments(VirtualFrame frame) {
            Object self = readSelfNode.execute(frame);
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

        @TruffleBoundary
        public static PBuiltinFunction createFunction(PythonLanguage lang, Object owner, String propertyName, Object target, Object closure) {
            RootCallTarget rootCallTarget = PExternalFunctionWrapper.getOrCreateCallTarget(PExternalFunctionWrapper.GETTER, lang, propertyName, true);
            if (rootCallTarget == null) {
                throw CompilerDirectives.shouldNotReachHere("Calling non-native get descriptor functions is not support");
            }
            PythonObjectFactory factory = PythonObjectFactory.getUncached();
            return factory.createBuiltinFunction(propertyName, owner, PythonUtils.EMPTY_OBJECT_ARRAY, ExternalFunctionNodes.createKwDefaults(target, closure), rootCallTarget);
        }
    }

    /**
     * Wrapper root node for C function type {@code setter}.
     */
    public static class SetterRoot extends GetSetRootNode {
        private static final Signature SIGNATURE = new Signature(-1, false, -1, false, new String[]{"self", "value"}, KEYWORDS_HIDDEN_CALLABLE_AND_CLOSURE, true);

        @Child private ReadIndexedArgumentNode readArgNode;

        protected SetterRoot(PythonLanguage language, String name, PExternalFunctionWrapper provider) {
            super(language, name, provider);
        }

        @Override
        protected Object[] prepareCArguments(VirtualFrame frame) {
            Object self = readSelfNode.execute(frame);
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

        @TruffleBoundary
        public static PBuiltinFunction createFunction(PythonLanguage lang, Object owner, String propertyName, Object target, Object closure) {
            RootCallTarget rootCallTarget = PExternalFunctionWrapper.getOrCreateCallTarget(PExternalFunctionWrapper.SETTER, lang, propertyName, true);
            if (rootCallTarget == null) {
                throw CompilerDirectives.shouldNotReachHere("Calling non-native get descriptor functions is not support");
            }
            PythonObjectFactory factory = PythonObjectFactory.getUncached();
            return factory.createBuiltinFunction(propertyName, owner, PythonUtils.EMPTY_OBJECT_ARRAY, ExternalFunctionNodes.createKwDefaults(target, closure), rootCallTarget);
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
    abstract static class CreateArgsTupleNode extends Node {
        public abstract PTuple execute(PythonObjectFactory factory, Object[] args);

        @Specialization(guards = {"args.length == cachedLen", "cachedLen <= 16"})
        @ExplodeLoop(kind = LoopExplosionKind.FULL_UNROLL)
        static PTuple doCachedLen(PythonObjectFactory factory, Object[] args,
                        @Cached("args.length") int cachedLen,
                        @Cached("createToBorrowedRefNodes(args.length)") ToBorrowedRefNode[] toBorrowedRefNodes,
                        @Cached("createMaterializeNodes(args.length)") MaterializePrimitiveNode[] materializePrimitiveNodes) {

            for (int i = 0; i < cachedLen; i++) {
                args[i] = prepareReference(args[i], factory, materializePrimitiveNodes[i], toBorrowedRefNodes[i]);
            }
            return factory.createTuple(args);
        }

        @Specialization(replaces = "doCachedLen")
        static PTuple doGeneric(PythonObjectFactory factory, Object[] args,
                        @Cached ToBorrowedRefNode toNewRefNode,
                        @Cached MaterializePrimitiveNode materializePrimitiveNode) {

            for (int i = 0; i < args.length; i++) {
                args[i] = prepareReference(args[i], factory, materializePrimitiveNode, toNewRefNode);
            }
            return factory.createTuple(args);
        }

        private static Object prepareReference(Object arg, PythonObjectFactory factory, MaterializePrimitiveNode materializePrimitiveNode, ToBorrowedRefNode toNewRefNode) {
            Object result = materializePrimitiveNode.execute(factory, arg);

            // Tuples are actually stealing the reference of their items. That's why we need to
            // increase the reference count by 1 at this point. However, it could be that the
            // object does not have a native wrapper yet. We use ToNewRefNode to ensure that the
            // object has a native wrapper or to increase the reference count by 1 if a native
            // wrapper already exists.
            toNewRefNode.execute(result);
            return result;
        }

        static ToBorrowedRefNode[] createToBorrowedRefNodes(int length) {
            ToBorrowedRefNode[] newRefNodes = new ToBorrowedRefNode[length];
            for (int i = 0; i < length; i++) {
                newRefNodes[i] = ToBorrowedRefNodeGen.create();
            }
            return newRefNodes;
        }

        static MaterializePrimitiveNode[] createMaterializeNodes(int length) {
            MaterializePrimitiveNode[] materializePrimitiveNodes = new MaterializePrimitiveNode[length];
            for (int i = 0; i < length; i++) {
                materializePrimitiveNodes[i] = MaterializePrimitiveNodeGen.create();
            }
            return materializePrimitiveNodes;
        }
    }

    /**
     * Special helper nodes that materializes any primitive that would leak the wrapper if the
     * reference is owned by managed code only.
     */
    @ImportStatic(CApiGuards.class)
    @TypeSystemReference(PythonTypes.class)
    abstract static class MaterializePrimitiveNode extends Node {

        public abstract Object execute(PythonObjectFactory factory, Object object);

        // NOTE: Booleans don't need to be materialized because they are singletons.

        @Specialization(guards = "!isSmallInteger(i)")
        static PInt doInteger(PythonObjectFactory factory, int i) {
            return factory.createInt(i);
        }

        @Specialization(guards = "!isSmallLong(l)", replaces = "doInteger")
        static PInt doLong(PythonObjectFactory factory, long l) {
            return factory.createInt(l);
        }

        @Specialization
        static PFloat doDouble(PythonObjectFactory factory, double d) {
            return factory.createFloat(d);
        }

        @Specialization
        static PString doString(PythonObjectFactory factory, String s) {
            return factory.createString(s);
        }

        @Specialization(guards = "!needsMaterialization(object)")
        static Object doObject(@SuppressWarnings("unused") PythonObjectFactory factory, Object object) {
            return object;
        }

        static boolean needsMaterialization(Object object) {
            if (object instanceof Integer) {
                return !CApiGuards.isSmallInteger((Integer) object);
            }
            if (object instanceof Long) {
                return !CApiGuards.isSmallLong((Long) object);
            }
            return PGuards.isDouble(object) || object instanceof String;
        }
    }

}
