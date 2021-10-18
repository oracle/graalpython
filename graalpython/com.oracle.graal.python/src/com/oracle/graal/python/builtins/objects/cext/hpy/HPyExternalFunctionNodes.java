/*
 * Copyright (c) 2019, 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.objects.cext.hpy;

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.SystemError;
import static com.oracle.graal.python.util.PythonUtils.EMPTY_STRING_ARRAY;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.cext.PythonAbstractNativeObject;
import com.oracle.graal.python.builtins.objects.cext.capi.CApiContext.LLVMType;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.FromCharPointerNode;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.PCallCapiFunction;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodesFactory.FromCharPointerNodeGen;
import com.oracle.graal.python.builtins.objects.cext.capi.ExternalFunctionNodes;
import com.oracle.graal.python.builtins.objects.cext.capi.ExternalFunctionNodes.GetterRoot;
import com.oracle.graal.python.builtins.objects.cext.capi.ExternalFunctionNodes.PExternalFunctionWrapper;
import com.oracle.graal.python.builtins.objects.cext.capi.ExternalFunctionNodes.SetterRoot;
import com.oracle.graal.python.builtins.objects.cext.capi.NativeCAPISymbol;
import com.oracle.graal.python.builtins.objects.cext.common.CExtCommonNodes;
import com.oracle.graal.python.builtins.objects.cext.common.CExtCommonNodes.CheckFunctionResultNode;
import com.oracle.graal.python.builtins.objects.cext.common.CExtCommonNodes.GetIndexNode;
import com.oracle.graal.python.builtins.objects.cext.common.CExtCommonNodes.GetIntArrayNode;
import com.oracle.graal.python.builtins.objects.cext.common.CExtCommonNodesFactory.GetIntArrayNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyDef.HPyFuncSignature;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyDef.HPySlotWrapper;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNodes.HPyCloseAndGetHandleNode;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNodes.HPyCloseArgHandlesNode;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNodes.HPyConvertArgsToSulongNode;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNodes.HPyGetNativeSpacePointerNode;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNodes.PCallHPyFunction;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNodesFactory.HPyAllAsHandleNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNodesFactory.HPyCloseAndGetHandleNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNodesFactory.HPyGetBufferProcToSulongNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNodesFactory.HPyGetNativeSpacePointerNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNodesFactory.HPyGetSetGetterToSulongNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNodesFactory.HPyGetSetSetterToSulongNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNodesFactory.HPyKeywordsToSulongNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNodesFactory.HPyReleaseBufferProcToSulongNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNodesFactory.HPyRichcmpFuncArgsToSulongNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNodesFactory.HPySSizeArgFuncToSulongNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNodesFactory.HPySSizeObjArgProcToSulongNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNodesFactory.HPyVarargsToSulongNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNodesFactory.PCallHPyFunctionNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.HPyArrayWrappers.HPyArrayWrapper;
import com.oracle.graal.python.builtins.objects.cext.hpy.HPyExternalFunctionNodesFactory.HPyCheckHandleResultNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.HPyExternalFunctionNodesFactory.HPyCheckPrimitiveResultNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.HPyExternalFunctionNodesFactory.HPyCheckVoidResultNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.HPyExternalFunctionNodesFactory.HPyExternalFunctionInvokeNodeGen;
import com.oracle.graal.python.builtins.objects.exception.PBaseException;
import com.oracle.graal.python.builtins.objects.function.PArguments;
import com.oracle.graal.python.builtins.objects.function.PBuiltinFunction;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.builtins.objects.function.Signature;
import com.oracle.graal.python.builtins.objects.memoryview.CExtPyBuffer;
import com.oracle.graal.python.builtins.objects.type.TypeNodes.GetNameNode;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.IndirectCallNode;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.PRootNode;
import com.oracle.graal.python.nodes.argument.ReadIndexedArgumentNode;
import com.oracle.graal.python.nodes.argument.ReadVarArgsNode;
import com.oracle.graal.python.nodes.argument.ReadVarKeywordsNode;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.nodes.util.CastToJavaStringNode;
import com.oracle.graal.python.runtime.ExecutionContext.CalleeContext;
import com.oracle.graal.python.runtime.ExecutionContext.IndirectCallContext;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.PythonContext.GetThreadStateNode;
import com.oracle.graal.python.runtime.PythonContext.PythonThreadState;
import com.oracle.graal.python.runtime.PythonContextFactory.GetThreadStateNodeGen;
import com.oracle.graal.python.runtime.PythonOptions;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.exception.PythonErrorType;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.NodeCost;
import com.oracle.truffle.api.profiles.ConditionProfile;

public abstract class HPyExternalFunctionNodes {

    private static final String KW_CALLABLE = "$callable";
    private static final String KW_CLOSURE = "$closure";
    private static final String KW_CONTEXT = "$context";
    private static final String[] KEYWORDS_HIDDEN_CALLABLE = {KW_CALLABLE, KW_CONTEXT};
    private static final String[] KEYWORDS_HIDDEN_CALLABLE_AND_CLOSURE = {KW_CALLABLE, KW_CONTEXT, KW_CLOSURE};
    private static final Object[] KW_DEFAULTS = {PNone.NO_VALUE};

    private static PKeyword[] createKwDefaults(Object callable, GraalHPyContext context) {
        return new PKeyword[]{new PKeyword(KW_CALLABLE, callable), new PKeyword(KW_CONTEXT, context)};
    }

    public static PKeyword[] createKwDefaults(Object callable, Object closure, GraalHPyContext context) {
        return new PKeyword[]{new PKeyword(KW_CALLABLE, callable), new PKeyword(KW_CONTEXT, context), new PKeyword(KW_CLOSURE, closure)};
    }

    /**
     * Creates a built-in function that accepts the specified signatures, does appropriate argument
     * and result conversion and calls the provided callable.
     *
     * @param language The Python language object.
     * @param context The HPy context the new function object belongs to. This will also be the
     *            context that is passed to the native functions when they are called.
     * @param signature The signature ID as defined in {@link GraalHPyDef}.
     * @param name The name of the method.
     * @param callable The native function pointer.
     * @param enclosingType The type the function belongs to (needed for checking of {@code self}).
     * @param factory Just an instance of {@link PythonObjectFactory} to create the function object.
     *            We could also use the uncached version but this way, the allocations are reported
     *            for the caller.
     * @return A {@link PBuiltinFunction} that accepts the given signature.
     */
    @TruffleBoundary
    static PBuiltinFunction createWrapperFunction(PythonLanguage language, GraalHPyContext context, HPyFuncSignature signature, String name, Object callable, Object enclosingType,
                    PythonObjectFactory factory) {
        assert InteropLibrary.getUncached(callable).isExecutable(callable) : "object is not callable";
        RootCallTarget callTarget = language.createCachedCallTarget(l -> createRootNode(l, signature, name), signature, name);

        Object[] defaults;
        if (signature == HPyFuncSignature.TERNARYFUNC) {
            // the third argument is optional
            // so it has a default value (this implicitly is 'None')
            defaults = KW_DEFAULTS;
        } else {
            defaults = PythonUtils.EMPTY_OBJECT_ARRAY;
        }
        int flags = HPyFuncSignature.getFlags(signature);
        return factory.createBuiltinFunction(name, enclosingType, defaults, createKwDefaults(callable, context), flags, callTarget);
    }

    private static PRootNode createRootNode(PythonLanguage language, HPyFuncSignature signature, String name) {
        switch (signature) {
            case NOARGS:
            case UNARYFUNC:
            case REPRFUNC:
            case GETITERFUNC:
            case ITERNEXTFUNC:
            case DESTROYFUNC:
                return new HPyMethNoargsRoot(language, name, false);
            case O:
            case BINARYFUNC:
                return new HPyMethORoot(language, name, false);
            case KEYWORDS:
                return new HPyMethKeywordsRoot(language, name);
            case INITPROC:
                return new HPyMethInitProcRoot(language, name);
            case VARARGS:
                return new HPyMethVarargsRoot(language, name);
            case TERNARYFUNC:
                return new HPyMethTernaryRoot(language, name);
            case LENFUNC:
                return new HPyMethNoargsRoot(language, name, true);
            case SSIZEOBJARGPROC:
                return new HPyMethSSizeObjArgProcRoot(language, name);
            case INQUIRY:
                return new HPyMethInquiryRoot(language, name);
            case SSIZEARGFUNC:
                return new HPyMethSSizeArgFuncRoot(language, name);
            case OBJOBJARGPROC:
                return new HPyMethObjObjArgProcRoot(language, name);
            case OBJOBJPROC:
                return new HPyMethObjObjProcRoot(language, name);
            default:
                // TODO(fa): support remaining signatures
                throw CompilerDirectives.shouldNotReachHere("unsupported HPy method signature: " + signature.name());
        }
    }

    /**
     * Creates a built-in function for a specific slot. This built-in function also does appropriate
     * argument and result conversion and calls the provided callable.
     *
     * @param language The Python language object.
     * @param wrapper The wrapper ID as defined in {@link HPySlotWrapper}.
     * @param name The name of the method.
     * @param callable The native function pointer.
     * @param enclosingType The type the function belongs to (needed for checking of {@code self}).
     * @param factory Just an instance of {@link PythonObjectFactory} to create the function object.
     * @return A {@link PBuiltinFunction} implementing the semantics of the specified slot wrapper.
     */
    @TruffleBoundary
    static PBuiltinFunction createWrapperFunction(PythonLanguage language, GraalHPyContext context, HPySlotWrapper wrapper, String name, Object callable, Object enclosingType,
                    PythonObjectFactory factory) {
        assert InteropLibrary.getUncached(callable).isExecutable(callable) : "object is not callable";
        RootCallTarget callTarget = language.createCachedCallTarget(l -> createSlotRootNode(l, wrapper, name), wrapper, name);
        Object[] defaults;
        if (wrapper == HPySlotWrapper.TERNARYFUNC || wrapper == HPySlotWrapper.SQ_DELITEM) {
            /*
             * For TERNARYFUNC: The third argument is optional. So it has a default value (this
             * implicitly is 'None'). For SQ_DELITEM: it's really the same as SQ_SETITEM but with a
             * default.
             */
            defaults = new Object[]{PNone.NO_VALUE};
        } else {
            defaults = PythonUtils.EMPTY_OBJECT_ARRAY;
        }
        return factory.createGetSetBuiltinFunction(name, enclosingType, defaults, createKwDefaults(callable, context), callTarget);
    }

    private static PRootNode createSlotRootNode(PythonLanguage language, HPySlotWrapper wrapper, String name) {
        switch (wrapper) {
            case NULL:
                return new HPyMethKeywordsRoot(language, name);
            case UNARYFUNC:
                return new HPyMethNoargsRoot(language, name, false);
            case BINARYFUNC:
            case BINARYFUNC_L:
                return new HPyMethORoot(language, name, false);
            case BINARYFUNC_R:
                return new HPyMethReverseBinaryRoot(language, name, false);
            case INIT:
                return new HPyMethInitProcRoot(language, name);
            case TERNARYFUNC:
                return new HPyMethTernaryRoot(language, name);
            case LENFUNC:
                return new HPyMethNoargsRoot(language, name, true);
            case INQUIRYPRED:
                return new HPyMethInquiryRoot(language, name);
            case INDEXARGFUNC:
                return new HPyMethSSizeArgFuncRoot(language, name);
            case OBJOBJARGPROC:
                return new HPyMethObjObjArgProcRoot(language, name);
            case OBJOBJPROC:
                return new HPyMethObjObjProcRoot(language, name);
            case SQ_ITEM:
                return new HPyMethSqItemWrapperRoot(language, name);
            case SQ_SETITEM:
            case SQ_DELITEM:
                // SQ_DELITEM is really the same as SQ_SETITEM but with a default
                return new HPyMethSqSetitemWrapperRoot(language, name);
            case RICHCMP_LT:
            case RICHCMP_LE:
            case RICHCMP_EQ:
            case RICHCMP_NE:
            case RICHCMP_GT:
            case RICHCMP_GE:
                return new HPyMethRichcmpOpRootNode(language, name, getCompareOpCode(wrapper));
            case GETBUFFER:
                return new HPyGetBufferRootNode(language, name);
            case RELEASEBUFFER:
                return new HPyReleaseBufferRootNode(language, name);
            default:
                // TODO(fa): support remaining slot wrappers
                throw CompilerDirectives.shouldNotReachHere("unsupported HPy slot wrapper: wrap_" + wrapper.name().toLowerCase());
        }

    }

    /**
     * Resolve the requested slot wrapper to the numeric op code as defined by HPy's enum
     * {@code HPy_RichCmpOp}.
     */
    private static int getCompareOpCode(HPySlotWrapper sig) {
        // op codes for binary comparisons (defined in 'object.h')
        switch (sig) {
            case RICHCMP_LT:
                return 0;
            case RICHCMP_LE:
                return 1;
            case RICHCMP_EQ:
                return 2;
            case RICHCMP_NE:
                return 3;
            case RICHCMP_GT:
                return 4;
            case RICHCMP_GE:
                return 5;
        }
        throw CompilerDirectives.shouldNotReachHere();
    }

    /**
     * Invokes an HPy C function. It takes care of argument and result conversion and always passes
     * the HPy context as a first parameter.
     */
    abstract static class HPyExternalFunctionInvokeNode extends Node implements IndirectCallNode {

        @Child private HPyConvertArgsToSulongNode toSulongNode;
        @Child private HPyCheckFunctionResultNode checkFunctionResultNode;
        @Child private HPyCloseArgHandlesNode handleCloseNode;

        @CompilationFinal private Assumption nativeCodeDoesntNeedExceptionState = Truffle.getRuntime().createAssumption();
        @CompilationFinal private Assumption nativeCodeDoesntNeedMyFrame = Truffle.getRuntime().createAssumption();

        HPyExternalFunctionInvokeNode() {
            CompilerAsserts.neverPartOfCompilation();
            this.toSulongNode = HPyAllAsHandleNodeGen.create();
            this.checkFunctionResultNode = HPyCheckHandleResultNodeGen.create();
            this.handleCloseNode = this.toSulongNode.createCloseHandleNode();
        }

        HPyExternalFunctionInvokeNode(HPyConvertArgsToSulongNode convertArgsNode) {
            CompilerAsserts.neverPartOfCompilation();
            this.toSulongNode = convertArgsNode != null ? convertArgsNode : HPyAllAsHandleNodeGen.create();
            this.checkFunctionResultNode = HPyCheckHandleResultNodeGen.create();
            this.handleCloseNode = this.toSulongNode.createCloseHandleNode();
        }

        HPyExternalFunctionInvokeNode(HPyCheckFunctionResultNode checkFunctionResultNode, HPyConvertArgsToSulongNode convertArgsNode) {
            CompilerAsserts.neverPartOfCompilation();
            this.toSulongNode = convertArgsNode != null ? convertArgsNode : HPyAllAsHandleNodeGen.create();
            this.checkFunctionResultNode = checkFunctionResultNode != null ? checkFunctionResultNode : HPyCheckHandleResultNodeGen.create();
            this.handleCloseNode = this.toSulongNode.createCloseHandleNode();
        }

        public abstract Object execute(VirtualFrame frame, String name, Object callable, GraalHPyContext hPyContext, Object[] frameArgs);

        @Specialization(limit = "1")
        Object doIt(VirtualFrame frame, String name, Object callable, GraalHPyContext hPyContext, Object[] arguments,
                        @CachedLibrary("callable") InteropLibrary lib,
                        @Cached PRaiseNode raiseNode) {
            Object[] convertedArguments = new Object[arguments.length + 1];
            toSulongNode.executeInto(frame, hPyContext, arguments, 0, convertedArguments, 1);

            // first arg is always the HPyContext
            convertedArguments[0] = hPyContext;

            PythonLanguage language = PythonLanguage.get(this);
            PythonContext ctx = hPyContext.getContext();
            PythonThreadState pythonThreadState = ctx.getThreadState(language);

            // If any code requested the caught exception (i.e. used 'sys.exc_info()'), we store
            // it to the context since we cannot propagate it through the native frames.
            Object state = IndirectCallContext.enter(frame, pythonThreadState, this);

            try {
                return checkFunctionResultNode.execute(pythonThreadState, hPyContext, name, lib.execute(callable, convertedArguments));
            } catch (UnsupportedTypeException | UnsupportedMessageException e) {
                throw raiseNode.raise(PythonBuiltinClassType.TypeError, "Calling native function %s failed: %m", name, e);
            } catch (ArityException e) {
                throw raiseNode.raise(PythonBuiltinClassType.TypeError, "Calling native function %s expected %d arguments but got %d.", name, e.getExpectedMinArity(), e.getActualArity());
            } finally {
                // special case after calling a C function: transfer caught exception back to frame
                // to simulate the global state semantics
                PArguments.setException(frame, pythonThreadState.getCaughtException());
                IndirectCallContext.exit(frame, pythonThreadState, state);

                // close all handles (if necessary)
                if (handleCloseNode != null) {
                    handleCloseNode.executeInto(frame, hPyContext, convertedArguments, 1);
                }
            }
        }

        @Override
        public Assumption needNotPassFrameAssumption() {
            return nativeCodeDoesntNeedMyFrame;
        }

        @Override
        public Assumption needNotPassExceptionAssumption() {
            return nativeCodeDoesntNeedExceptionState;
        }

        @Override
        public Node copy() {
            HPyExternalFunctionInvokeNode node = (HPyExternalFunctionInvokeNode) super.copy();
            node.nativeCodeDoesntNeedMyFrame = Truffle.getRuntime().createAssumption();
            node.nativeCodeDoesntNeedExceptionState = Truffle.getRuntime().createAssumption();
            return node;
        }
    }

    abstract static class HPyMethodDescriptorRootNode extends PRootNode {
        @Child private CalleeContext calleeContext;
        @Child private HPyExternalFunctionInvokeNode invokeNode;
        @Child private ReadIndexedArgumentNode readSelfNode;
        @Child private ReadIndexedArgumentNode readCallableNode;
        @Child private ReadIndexedArgumentNode readContextNode;

        @Child private InteropLibrary interop = InteropLibrary.getFactory().createDispatched(5);

        private final String name;

        @TruffleBoundary
        public HPyMethodDescriptorRootNode(PythonLanguage language, String name, HPyConvertArgsToSulongNode convertArgsToSulongNode) {
            super(language);
            this.name = name;
            this.invokeNode = HPyExternalFunctionInvokeNodeGen.create(convertArgsToSulongNode);
        }

        @TruffleBoundary
        public HPyMethodDescriptorRootNode(PythonLanguage language, String name, HPyCheckFunctionResultNode checkFunctionResultNode, HPyConvertArgsToSulongNode convertArgsToSulongNode) {
            super(language);
            this.name = name;
            this.invokeNode = HPyExternalFunctionInvokeNodeGen.create(checkFunctionResultNode, convertArgsToSulongNode);
        }

        protected static Object intToBoolean(Object result) {
            if (result instanceof Integer) {
                return ((Integer) result) != 0;
            } else if (result instanceof Long) {
                return ((Long) result) != 0;
            }
            throw CompilerDirectives.shouldNotReachHere();
        }

        @Override
        public Object execute(VirtualFrame frame) {
            getCalleeContext().enter(frame);
            try {
                Object callable = ensureReadCallableNode().execute(frame);
                GraalHPyContext hpyContext = readContext(frame);
                return processResult(frame, invokeNode.execute(frame, name, callable, hpyContext, prepareCArguments(frame, hpyContext)));
            } finally {
                getCalleeContext().exit(frame, this);
            }
        }

        protected abstract Object[] prepareCArguments(VirtualFrame frame, GraalHPyContext hpyContext);

        protected Object processResult(@SuppressWarnings("unused") VirtualFrame frame, Object result) {
            return result;
        }

        protected final HPyExternalFunctionInvokeNode getInvokeNode() {
            return invokeNode;
        }

        protected final Object getSelf(VirtualFrame frame) {
            if (readSelfNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                readSelfNode = insert(ReadIndexedArgumentNode.create(0));
            }
            return readSelfNode.execute(frame);
        }

        protected final CalleeContext getCalleeContext() {
            if (calleeContext == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                calleeContext = insert(CalleeContext.create());
            }
            return calleeContext;
        }

        protected final ReadIndexedArgumentNode ensureReadCallableNode() {
            if (readCallableNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                // we insert a hidden argument at the end of the positional arguments
                int hiddenArg = getSignature().getParameterIds().length;
                readCallableNode = insert(ReadIndexedArgumentNode.create(hiddenArg));
            }
            return readCallableNode;
        }

        protected final GraalHPyContext readContext(VirtualFrame frame) {
            if (readContextNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                // we insert a hidden argument after the hidden callable argument
                int hiddenArg = getSignature().getParameterIds().length + 1;
                readContextNode = insert(ReadIndexedArgumentNode.create(hiddenArg));
            }
            Object hpyContext = readContextNode.execute(frame);
            if (hpyContext instanceof GraalHPyContext) {
                return (GraalHPyContext) hpyContext;
            }
            throw CompilerDirectives.shouldNotReachHere("invalid HPy context");
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

    static final class HPyMethNoargsRoot extends HPyMethodDescriptorRootNode {
        private static final Signature SIGNATURE = new Signature(1, false, -1, false, new String[]{"self"}, KEYWORDS_HIDDEN_CALLABLE, true);

        public HPyMethNoargsRoot(PythonLanguage language, String name, boolean nativePrimitiveResult) {
            super(language, name, nativePrimitiveResult ? HPyCheckPrimitiveResultNodeGen.create() : HPyCheckHandleResultNodeGen.create(), HPyAllAsHandleNodeGen.create());
        }

        @Override
        protected Object[] prepareCArguments(VirtualFrame frame, @SuppressWarnings("unused") GraalHPyContext hpyContext) {
            return new Object[]{getSelf(frame)};
        }

        @Override
        public Signature getSignature() {
            return SIGNATURE;
        }
    }

    static final class HPyMethORoot extends HPyMethodDescriptorRootNode {
        private static final Signature SIGNATURE = new Signature(-1, false, -1, false, new String[]{"self", "arg"}, KEYWORDS_HIDDEN_CALLABLE, true);

        @Child private ReadIndexedArgumentNode readArgNode;

        public HPyMethORoot(PythonLanguage language, String name, boolean nativePrimitiveResult) {
            super(language, name, nativePrimitiveResult ? HPyCheckPrimitiveResultNodeGen.create() : HPyCheckHandleResultNodeGen.create(), HPyAllAsHandleNodeGen.create());
        }

        @Override
        protected Object[] prepareCArguments(VirtualFrame frame, @SuppressWarnings("unused") GraalHPyContext hpyContext) {
            return new Object[]{getSelf(frame), getArg(frame)};
        }

        private Object getArg(VirtualFrame frame) {
            if (readArgNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                readArgNode = insert(ReadIndexedArgumentNode.create(1));
            }
            return readArgNode.execute(frame);
        }

        @Override
        public Signature getSignature() {
            return SIGNATURE;
        }
    }

    static final class HPyMethVarargsRoot extends HPyMethodDescriptorRootNode {
        private static final Signature SIGNATURE = new Signature(-1, false, 1, false, new String[]{"self"}, KEYWORDS_HIDDEN_CALLABLE, true);

        @Child private ReadVarArgsNode readVarargsNode;

        @TruffleBoundary
        public HPyMethVarargsRoot(PythonLanguage language, String name) {
            super(language, name, HPyVarargsToSulongNodeGen.create());
        }

        @Override
        protected Object[] prepareCArguments(VirtualFrame frame, GraalHPyContext hpyContext) {
            Object[] args = getVarargs(frame);
            return new Object[]{getSelf(frame), new HPyArrayWrapper(hpyContext, args), (long) args.length};
        }

        private Object[] getVarargs(VirtualFrame frame) {
            if (readVarargsNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                readVarargsNode = insert(ReadVarArgsNode.create(true));
            }
            return readVarargsNode.executeObjectArray(frame);
        }

        @Override
        public Signature getSignature() {
            return SIGNATURE;
        }
    }

    static final class HPyMethKeywordsRoot extends HPyMethodDescriptorRootNode {
        private static final Signature SIGNATURE = new Signature(-1, true, 1, false, new String[]{"self"}, KEYWORDS_HIDDEN_CALLABLE, true);

        @Child private ReadVarArgsNode readVarargsNode;
        @Child private ReadVarKeywordsNode readKwargsNode;

        @TruffleBoundary
        public HPyMethKeywordsRoot(PythonLanguage language, String name) {
            super(language, name, HPyKeywordsToSulongNodeGen.create());
        }

        @Override
        protected Object[] prepareCArguments(VirtualFrame frame, GraalHPyContext hpyContext) {
            Object[] args = getVarargs(frame);
            return new Object[]{getSelf(frame), new HPyArrayWrapper(hpyContext, args), (long) args.length, getKwargs(frame)};
        }

        private Object[] getVarargs(VirtualFrame frame) {
            if (readVarargsNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                readVarargsNode = insert(ReadVarArgsNode.create(true));
            }
            return readVarargsNode.executeObjectArray(frame);
        }

        private Object getKwargs(VirtualFrame frame) {
            if (readKwargsNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                readKwargsNode = insert(ReadVarKeywordsNode.createForUserFunction(EMPTY_STRING_ARRAY));
            }
            return readKwargsNode.execute(frame);
        }

        @Override
        public Signature getSignature() {
            return SIGNATURE;
        }
    }

    static final class HPyMethInitProcRoot extends HPyMethodDescriptorRootNode {
        private static final Signature SIGNATURE = new Signature(-1, true, 1, false, new String[]{"self"}, KEYWORDS_HIDDEN_CALLABLE, true);

        @Child private ReadVarArgsNode readVarargsNode;
        @Child private ReadVarKeywordsNode readKwargsNode;

        @TruffleBoundary
        public HPyMethInitProcRoot(PythonLanguage language, String name) {
            super(language, name, HPyCheckPrimitiveResultNodeGen.create(), HPyKeywordsToSulongNodeGen.create());
        }

        @Override
        protected Object[] prepareCArguments(VirtualFrame frame, GraalHPyContext hpyContext) {
            Object[] args = getVarargs(frame);
            return new Object[]{getSelf(frame), new HPyArrayWrapper(hpyContext, args), (long) args.length, getKwargs(frame)};
        }

        @Override
        @SuppressWarnings("unused")
        protected Object processResult(VirtualFrame frame, Object result) {
            // If no error occurred, the init function always returns None.
            // Possible errors are already handled in the HPyExternalFunctionInvokeNode.
            return PNone.NONE;
        }

        private Object[] getVarargs(VirtualFrame frame) {
            if (readVarargsNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                readVarargsNode = insert(ReadVarArgsNode.create(true));
            }
            return readVarargsNode.executeObjectArray(frame);
        }

        private Object getKwargs(VirtualFrame frame) {
            if (readKwargsNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                readKwargsNode = insert(ReadVarKeywordsNode.createForUserFunction(EMPTY_STRING_ARRAY));
            }
            return readKwargsNode.execute(frame);
        }

        @Override
        public Signature getSignature() {
            return SIGNATURE;
        }
    }

    static final class HPyMethTernaryRoot extends HPyMethodDescriptorRootNode {
        private static final Signature SIGNATURE = new Signature(3, false, -1, false, new String[]{"x", "y", "z"}, KEYWORDS_HIDDEN_CALLABLE, true);

        @Child private ReadIndexedArgumentNode readArg1Node;
        @Child private ReadIndexedArgumentNode readArg2Node;

        public HPyMethTernaryRoot(PythonLanguage language, String name) {
            super(language, name, HPyAllAsHandleNodeGen.create());
        }

        @Override
        protected Object[] prepareCArguments(VirtualFrame frame, @SuppressWarnings("unused") GraalHPyContext hpyContext) {
            return new Object[]{getSelf(frame), getArg1(frame), getArg2(frame)};
        }

        private Object getArg1(VirtualFrame frame) {
            if (readArg1Node == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                readArg1Node = insert(ReadIndexedArgumentNode.create(1));
            }
            return readArg1Node.execute(frame);
        }

        private Object getArg2(VirtualFrame frame) {
            if (readArg2Node == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                readArg2Node = insert(ReadIndexedArgumentNode.create(2));
            }
            Object arg2 = readArg2Node.execute(frame);
            return arg2 != PNone.NO_VALUE ? arg2 : PNone.NONE;
        }

        @Override
        public Signature getSignature() {
            return SIGNATURE;
        }
    }

    static class HPyMethSSizeArgFuncRoot extends HPyMethodDescriptorRootNode {
        private static final Signature SIGNATURE = new Signature(2, false, -1, false, new String[]{"$self", "n"}, KEYWORDS_HIDDEN_CALLABLE, true);

        @Child private ReadIndexedArgumentNode readArg1Node;

        public HPyMethSSizeArgFuncRoot(PythonLanguage language, String name) {
            super(language, name, HPySSizeArgFuncToSulongNodeGen.create());
        }

        @Override
        protected Object[] prepareCArguments(VirtualFrame frame, @SuppressWarnings("unused") GraalHPyContext hpyContext) {
            return new Object[]{getSelf(frame), getArg1(frame)};
        }

        protected Object getArg1(VirtualFrame frame) {
            if (readArg1Node == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                readArg1Node = insert(ReadIndexedArgumentNode.create(1));
            }
            return readArg1Node.execute(frame);
        }

        @Override
        public Signature getSignature() {
            return SIGNATURE;
        }
    }

    /**
     * Implements semantics of {@code typeobject.c: wrap_sq_item}.
     */
    static final class HPyMethSqItemWrapperRoot extends HPyMethSSizeArgFuncRoot {

        @Child private GetIndexNode getIndexNode;

        public HPyMethSqItemWrapperRoot(PythonLanguage language, String name) {
            super(language, name);
        }

        @Override
        protected Object[] prepareCArguments(VirtualFrame frame, @SuppressWarnings("unused") GraalHPyContext hpyContext) {
            Object self = getSelf(frame);
            return new Object[]{self, getIndex(self, getArg1(frame))};
        }

        private int getIndex(Object self, Object index) {
            if (getIndexNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                getIndexNode = insert(CExtCommonNodes.GetIndexNode.create());
            }
            return getIndexNode.execute(self, index);
        }
    }

    /**
     * Implements semantics of {@code typeobject.c: wrap_sq_setitem}.
     */
    static final class HPyMethSqSetitemWrapperRoot extends HPyMethSSizeObjArgProcRoot {

        @Child private GetIndexNode getIndexNode;

        public HPyMethSqSetitemWrapperRoot(PythonLanguage language, String name) {
            super(language, name);
        }

        @Override
        protected Object[] prepareCArguments(VirtualFrame frame, @SuppressWarnings("unused") GraalHPyContext hpyContext) {
            Object self = getSelf(frame);
            return new Object[]{self, getIndex(self, getArg1(frame)), getArg2(frame)};
        }

        private int getIndex(Object self, Object index) {
            if (getIndexNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                getIndexNode = insert(CExtCommonNodes.GetIndexNode.create());
            }
            return getIndexNode.execute(self, index);
        }
    }

    static final class HPyMethSSizeSSizeArgFuncRoot extends HPyMethodDescriptorRootNode {
        private static final Signature SIGNATURE = new Signature(3, false, -1, false, new String[]{"$self", "n", "m"}, KEYWORDS_HIDDEN_CALLABLE, true);

        @Child private ReadIndexedArgumentNode readArg1Node;
        @Child private ReadIndexedArgumentNode readArg2Node;

        public HPyMethSSizeSSizeArgFuncRoot(PythonLanguage language, String name) {
            super(language, name, HPySSizeArgFuncToSulongNodeGen.create());
        }

        @Override
        protected Object[] prepareCArguments(VirtualFrame frame, @SuppressWarnings("unused") GraalHPyContext hpyContext) {
            return new Object[]{getSelf(frame), getArg1(frame), getArg2(frame)};
        }

        private Object getArg1(VirtualFrame frame) {
            if (readArg1Node == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                readArg1Node = insert(ReadIndexedArgumentNode.create(1));
            }
            return readArg1Node.execute(frame);
        }

        private Object getArg2(VirtualFrame frame) {
            if (readArg2Node == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                readArg2Node = insert(ReadIndexedArgumentNode.create(2));
            }
            return readArg2Node.execute(frame);
        }

        @Override
        public Signature getSignature() {
            return SIGNATURE;
        }
    }

    /**
     * Very similar to {@link HPyMethNoargsRoot} but converts the result to a boolean.
     */
    static final class HPyMethInquiryRoot extends HPyMethodDescriptorRootNode {
        private static final Signature SIGNATURE = new Signature(-1, false, -1, false, new String[]{"self"}, KEYWORDS_HIDDEN_CALLABLE);

        public HPyMethInquiryRoot(PythonLanguage language, String name) {
            super(language, name, HPyCheckPrimitiveResultNodeGen.create(), HPyAllAsHandleNodeGen.create());
        }

        @Override
        protected Object[] prepareCArguments(VirtualFrame frame, @SuppressWarnings("unused") GraalHPyContext hpyContext) {
            return new Object[]{getSelf(frame)};
        }

        @Override
        protected Object processResult(VirtualFrame frame, Object result) {
            // 'HPyCheckPrimitiveResultNode' already guarantees that the result is 'int' or 'long'.
            return intToBoolean(result);
        }

        @Override
        public Signature getSignature() {
            return SIGNATURE;
        }
    }

    static final class HPyMethObjObjArgProcRoot extends HPyMethodDescriptorRootNode {
        private static final Signature SIGNATURE = new Signature(3, false, -1, false, new String[]{"$self", "x", "y"}, KEYWORDS_HIDDEN_CALLABLE, true);

        @Child private ReadIndexedArgumentNode readArg1Node;
        @Child private ReadIndexedArgumentNode readArg2Node;

        public HPyMethObjObjArgProcRoot(PythonLanguage language, String name) {
            super(language, name, HPyCheckPrimitiveResultNodeGen.create(), HPyAllAsHandleNodeGen.create());
        }

        @Override
        protected Object[] prepareCArguments(VirtualFrame frame, @SuppressWarnings("unused") GraalHPyContext hpyContext) {
            return new Object[]{getSelf(frame), getArg1(frame), getArg2(frame)};
        }

        private Object getArg1(VirtualFrame frame) {
            if (readArg1Node == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                readArg1Node = insert(ReadIndexedArgumentNode.create(1));
            }
            return readArg1Node.execute(frame);
        }

        private Object getArg2(VirtualFrame frame) {
            if (readArg2Node == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                readArg2Node = insert(ReadIndexedArgumentNode.create(2));
            }
            return readArg2Node.execute(frame);
        }

        @Override
        protected Object processResult(VirtualFrame frame, Object result) {
            // 'HPyCheckPrimitiveResultNode' already guarantees that the result is 'int' or 'long'.
            return intToBoolean(result);
        }

        @Override
        public Signature getSignature() {
            return SIGNATURE;
        }
    }

    static final class HPyMethObjObjProcRoot extends HPyMethodDescriptorRootNode {
        private static final Signature SIGNATURE = new Signature(2, false, -1, false, new String[]{"$self", "other"}, KEYWORDS_HIDDEN_CALLABLE, true);

        @Child private ReadIndexedArgumentNode readArg1Node;

        public HPyMethObjObjProcRoot(PythonLanguage language, String name) {
            super(language, name, HPyCheckPrimitiveResultNodeGen.create(), HPyAllAsHandleNodeGen.create());
        }

        @Override
        protected Object[] prepareCArguments(VirtualFrame frame, @SuppressWarnings("unused") GraalHPyContext hpyContext) {
            return new Object[]{getSelf(frame), getArg1(frame)};
        }

        private Object getArg1(VirtualFrame frame) {
            if (readArg1Node == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                readArg1Node = insert(ReadIndexedArgumentNode.create(1));
            }
            return readArg1Node.execute(frame);
        }

        @Override
        protected Object processResult(VirtualFrame frame, Object result) {
            // 'HPyCheckPrimitiveResultNode' already guarantees that the result is 'int' or 'long'.
            return intToBoolean(result);
        }

        @Override
        public Signature getSignature() {
            return SIGNATURE;
        }
    }

    static class HPyMethSSizeObjArgProcRoot extends HPyMethodDescriptorRootNode {
        private static final Signature SIGNATURE = new Signature(3, false, -1, false, new String[]{"$self", "arg0", "arg1"}, KEYWORDS_HIDDEN_CALLABLE, true);

        @Child private ReadIndexedArgumentNode readArg1Node;
        @Child private ReadIndexedArgumentNode readArg2Node;

        public HPyMethSSizeObjArgProcRoot(PythonLanguage language, String name) {
            super(language, name, HPyCheckPrimitiveResultNodeGen.create(), HPySSizeObjArgProcToSulongNodeGen.create());
        }

        @Override
        protected Object[] prepareCArguments(VirtualFrame frame, @SuppressWarnings("unused") GraalHPyContext hpyContext) {
            return new Object[]{getSelf(frame), getArg1(frame), getArg2(frame)};
        }

        protected Object getArg1(VirtualFrame frame) {
            if (readArg1Node == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                readArg1Node = insert(ReadIndexedArgumentNode.create(1));
            }
            return readArg1Node.execute(frame);
        }

        protected Object getArg2(VirtualFrame frame) {
            if (readArg2Node == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                readArg2Node = insert(ReadIndexedArgumentNode.create(2));
            }
            return readArg2Node.execute(frame);
        }

        @Override
        public Signature getSignature() {
            return SIGNATURE;
        }
    }

    static final class HPyMethReverseBinaryRoot extends HPyMethodDescriptorRootNode {
        private static final Signature SIGNATURE = new Signature(-1, false, -1, false, new String[]{"self", "other"}, KEYWORDS_HIDDEN_CALLABLE, true);

        @Child private ReadIndexedArgumentNode readOtherNode;

        public HPyMethReverseBinaryRoot(PythonLanguage language, String name, boolean nativePrimitiveResult) {
            super(language, name, nativePrimitiveResult ? HPyCheckPrimitiveResultNodeGen.create() : HPyCheckHandleResultNodeGen.create(), HPyAllAsHandleNodeGen.create());
        }

        @Override
        protected Object[] prepareCArguments(VirtualFrame frame, @SuppressWarnings("unused") GraalHPyContext hpyContext) {
            return new Object[]{getOther(frame), getSelf(frame)};
        }

        private Object getOther(VirtualFrame frame) {
            if (readOtherNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                readOtherNode = insert(ReadIndexedArgumentNode.create(1));
            }
            return readOtherNode.execute(frame);
        }

        @Override
        public Signature getSignature() {
            return SIGNATURE;
        }
    }

    public abstract static class HPyCheckFunctionResultNode extends CheckFunctionResultNode {
        @Child private GetThreadStateNode getThreadStateNode;

        /**
         * Compatibility method to satisfy the generic interface.
         */
        @Override
        public final Object execute(PythonContext context, String name, Object result) {
            return execute(getThreadState(context), context.getHPyContext(), name, result);
        }

        /**
         * This is the preferred way for executing the node since it avoids unnecessary field reads
         * in the interpreter or multi-context mode.
         */
        public abstract Object execute(PythonThreadState pythonThreadState, GraalHPyContext nativeContext, String name, Object value);

        protected final void checkFunctionResult(String name, boolean indicatesError, PythonThreadState pythonThreadState, PRaiseNode raise, PythonObjectFactory factory) {
            PException currentException = pythonThreadState.getCurrentException();
            boolean errOccurred = currentException != null;
            if (indicatesError) {
                // consume exception
                pythonThreadState.setCurrentException(null);
                if (!errOccurred) {
                    throw raise.raise(PythonErrorType.SystemError, ErrorMessages.RETURNED_NULL_WO_SETTING_ERROR, name);
                } else {
                    throw currentException.getExceptionForReraise();
                }
            } else if (errOccurred) {
                // consume exception
                pythonThreadState.setCurrentException(null);
                PBaseException sysExc = factory.createBaseException(PythonErrorType.SystemError, ErrorMessages.RETURNED_RESULT_WITH_ERROR_SET, new Object[]{name});
                sysExc.setCause(currentException.getEscapedException());
                PythonLanguage language = PythonLanguage.get(this);
                throw PException.fromObject(sysExc, this, PythonOptions.isPExceptionWithJavaStacktrace(language));
            }
        }

        private PythonThreadState getThreadState(PythonContext context) {
            if (getThreadStateNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                getThreadStateNode = insert(GetThreadStateNodeGen.create());
            }
            return getThreadStateNode.execute(context);
        }
    }

    // roughly equivalent to _Py_CheckFunctionResult in Objects/call.c
    @ImportStatic(PGuards.class)
    public abstract static class HPyCheckHandleResultNode extends HPyCheckFunctionResultNode {

        @Specialization
        Object doLongNull(PythonThreadState pythonThreadState, @SuppressWarnings("unused") GraalHPyContext nativeContext, String name, Object value,
                        @Cached HPyCloseAndGetHandleNode closeAndGetHandleNode,
                        @Cached ConditionProfile isNullProfile,
                        @Cached PythonObjectFactory factory,
                        @Cached PRaiseNode raiseNode) {
            Object delegate = closeAndGetHandleNode.execute(nativeContext, value);
            checkFunctionResult(name, isNullProfile.profile(delegate == GraalHPyHandle.NULL_HANDLE_DELEGATE), pythonThreadState, raiseNode, factory);
            return delegate;
        }
    }

    /**
     * Similar to {@link HPyCheckFunctionResultNode}, this node checks a primitive result of a
     * native function. This node guarantees that an {@code int} or {@code long} is returned.
     */
    @ImportStatic(PGuards.class)
    abstract static class HPyCheckPrimitiveResultNode extends HPyCheckFunctionResultNode {
        public abstract int executeInt(PythonThreadState context, GraalHPyContext nativeContext, String name, int value);

        public abstract long executeLong(PythonThreadState context, GraalHPyContext nativeContext, String name, long value);

        @Specialization
        int doInteger(PythonThreadState pythonThreadState, @SuppressWarnings("unused") GraalHPyContext nativeContext, String name, int value,
                        @Shared("fact") @Cached PythonObjectFactory factory,
                        @Shared("raiseNode") @Cached PRaiseNode raiseNode) {
            checkFunctionResult(name, value == -1, pythonThreadState, raiseNode, factory);
            return value;
        }

        @Specialization(replaces = "doInteger")
        long doLong(PythonThreadState pythonThreadState, @SuppressWarnings("unused") GraalHPyContext nativeContext, String name, long value,
                        @Shared("fact") @Cached PythonObjectFactory factory,
                        @Shared("raiseNode") @Cached PRaiseNode raiseNode) {
            checkFunctionResult(name, value == -1, pythonThreadState, raiseNode, factory);
            return value;
        }

        @Specialization(limit = "1")
        Object doObject(PythonThreadState pythonThreadState, @SuppressWarnings("unused") GraalHPyContext nativeContext, String name, Object value,
                        @Shared("fact") @Cached PythonObjectFactory factory,
                        @CachedLibrary("value") InteropLibrary lib,
                        @Shared("raiseNode") @Cached PRaiseNode raiseNode) {
            if (lib.fitsInLong(value)) {
                try {
                    long lvalue = lib.asLong(value);
                    checkFunctionResult(name, lvalue == -1, pythonThreadState, raiseNode, factory);
                    return lvalue;
                } catch (UnsupportedMessageException e) {
                    throw CompilerDirectives.shouldNotReachHere();
                }
            }
            throw raiseNode.raise(SystemError, "function '%s' did not return an integer.", name);
        }
    }

    /**
     * Does not actually check the result of a function (since this is used when {@code void}
     * functions are called) but checks if an error occurred during execution of the function.
     */
    @ImportStatic(PGuards.class)
    abstract static class HPyCheckVoidResultNode extends HPyCheckFunctionResultNode {

        @Specialization
        Object doGeneric(PythonThreadState threadState, @SuppressWarnings("unused") GraalHPyContext nativeContext, String name, Object value,
                        @Cached PythonObjectFactory factory,
                        @Cached PRaiseNode raiseNode) {
            /*
             * A 'void' function never indicates an error but an error could still happen. So this
             * must also be checked. The actual result value (which will be something like NULL or
             * 0) is not used.
             */
            checkFunctionResult(name, false, threadState, raiseNode, factory);
            return value;
        }
    }

    static final class HPyMethRichcmpOpRootNode extends HPyMethodDescriptorRootNode {
        private static final Signature SIGNATURE = new Signature(-1, false, -1, false, new String[]{"self", "other"}, KEYWORDS_HIDDEN_CALLABLE, true);
        @Child private ReadIndexedArgumentNode readArgNode;

        private final int op;

        HPyMethRichcmpOpRootNode(PythonLanguage language, String name, int op) {
            super(language, name, HPyRichcmpFuncArgsToSulongNodeGen.create());
            this.readArgNode = ReadIndexedArgumentNode.create(1);
            this.op = op;
        }

        @Override
        protected Object[] prepareCArguments(VirtualFrame frame, @SuppressWarnings("unused") GraalHPyContext hpyContext) {
            return new Object[]{getSelf(frame), readArgNode.execute(frame), op};
        }

        @Override
        public Signature getSignature() {
            return SIGNATURE;
        }
    }

    /**
     * A simple and lightweight Python root node that invokes a native getter function. The native
     * call target and the native closure pointer are passed as Python closure.
     */
    abstract static class HPyGetSetDescriptorRootNode extends PRootNode {

        @Child private CalleeContext calleeContext;
        @Child private HPyExternalFunctionInvokeNode invokeNode;
        @Child private ReadIndexedArgumentNode readCallableNode;
        @Child private ReadIndexedArgumentNode readContextNode;
        @Child private ReadIndexedArgumentNode readClosureNode;

        private final String name;

        HPyGetSetDescriptorRootNode(PythonLanguage language, String name) {
            super(language);
            this.name = name;
        }

        @Override
        public Object execute(VirtualFrame frame) {
            getCalleeContext().enter(frame);
            try {
                Object target = readCallable(frame);
                GraalHPyContext hpyContext = readContext(frame);
                Object closure = readClosure(frame);
                return ensureInvokeNode().execute(frame, name, target, hpyContext, createArguments(frame, closure));
            } finally {
                getCalleeContext().exit(frame, this);
            }
        }

        protected abstract HPyConvertArgsToSulongNode createArgumentConversionNode();

        protected abstract HPyCheckFunctionResultNode createResultConversionNode();

        protected abstract Object[] createArguments(VirtualFrame frame, Object closure);

        @Override
        public String getName() {
            return name;
        }

        private CalleeContext getCalleeContext() {
            if (calleeContext == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                calleeContext = insert(CalleeContext.create());
            }
            return calleeContext;
        }

        private HPyExternalFunctionInvokeNode ensureInvokeNode() {
            if (invokeNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                invokeNode = insert(HPyExternalFunctionInvokeNodeGen.create(createResultConversionNode(), createArgumentConversionNode()));
            }
            return invokeNode;
        }

        protected final Object readCallable(VirtualFrame frame) {
            if (readCallableNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                // we insert a hidden argument at the end of the positional arguments
                int hiddenArg = getSignature().getParameterIds().length;
                readCallableNode = insert(ReadIndexedArgumentNode.create(hiddenArg));
            }
            return readCallableNode.execute(frame);
        }

        private GraalHPyContext readContext(VirtualFrame frame) {
            if (readContextNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                // we insert a hidden argument after the hidden callable argument
                int hiddenArg = getSignature().getParameterIds().length + 1;
                readContextNode = insert(ReadIndexedArgumentNode.create(hiddenArg));
            }
            Object hpyContext = readContextNode.execute(frame);
            if (hpyContext instanceof GraalHPyContext) {
                return (GraalHPyContext) hpyContext;
            }
            throw CompilerDirectives.shouldNotReachHere("invalid HPy context");
        }

        protected final Object readClosure(VirtualFrame frame) {
            if (readClosureNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                // we insert a hidden closure argument after the hidden context arg
                int hiddenArg = getSignature().getParameterIds().length + 2;
                readClosureNode = insert(ReadIndexedArgumentNode.create(hiddenArg));
            }
            return readClosureNode.execute(frame);
        }

        @Override
        public boolean isPythonInternal() {
            return true;
        }

        @Override
        public boolean isInternal() {
            return true;
        }
    }

    /**
     * A simple and lightweight Python root node that invokes a native getter function. The native
     * call target and the native closure pointer are passed as Python closure.
     */
    static final class HPyGetSetDescriptorGetterRootNode extends HPyGetSetDescriptorRootNode {
        private static final Signature SIGNATURE = new Signature(-1, false, -1, false, new String[]{"$self"}, KEYWORDS_HIDDEN_CALLABLE_AND_CLOSURE, true);

        HPyGetSetDescriptorGetterRootNode(PythonLanguage language, String name) {
            super(language, name);
        }

        @Override
        protected Object[] createArguments(VirtualFrame frame, Object closure) {
            return new Object[]{PArguments.getArgument(frame, 0), closure};
        }

        @Override
        protected HPyConvertArgsToSulongNode createArgumentConversionNode() {
            return HPyGetSetGetterToSulongNodeGen.create();
        }

        @Override
        protected HPyCheckFunctionResultNode createResultConversionNode() {
            return HPyCheckHandleResultNodeGen.create();
        }

        @Override
        public Signature getSignature() {
            return SIGNATURE;
        }

        @TruffleBoundary
        public static PBuiltinFunction createFunction(GraalHPyContext hpyContext, Object enclosingType, String propertyName, Object target, Object closure) {
            PythonLanguage lang = hpyContext.getContext().getLanguage();
            RootCallTarget callTarget = lang.createCachedCallTarget(l -> new HPyGetSetDescriptorGetterRootNode(l, propertyName), HPyGetSetDescriptorGetterRootNode.class, propertyName);
            PythonObjectFactory factory = hpyContext.getSlowPathFactory();
            return factory.createGetSetBuiltinFunction(propertyName, enclosingType, PythonUtils.EMPTY_OBJECT_ARRAY, createKwDefaults(target, closure, hpyContext), callTarget);
        }
    }

    static final class HPyLegacyGetSetDescriptorGetterRoot extends GetterRoot {

        @Child private HPyGetNativeSpacePointerNode getNativeSpacePointerNode;

        protected HPyLegacyGetSetDescriptorGetterRoot(PythonLanguage language, String name, PExternalFunctionWrapper provider) {
            super(language, name, provider);
        }

        /*
         * TODO(fa): It's still unclear how to handle HPy native space pointers when passed to an
         * 'AsPythonObjectNode'. This can happen when, e.g., the getter returns the 'self' pointer.
         */
        @Override
        protected Object[] prepareCArguments(VirtualFrame frame) {
            Object[] objects = super.prepareCArguments(frame);
            if (getNativeSpacePointerNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                getNativeSpacePointerNode = insert(HPyGetNativeSpacePointerNodeGen.create());
            }
            /*
             * We now need to pass the native space pointer in a way that the ToSulongNode correctly
             * exposes the bare pointer object. For this, we pack the pointer into a
             * PythonAbstractNativeObject which will just be unwrapped.
             */
            Object nativeSpacePtr = getNativeSpacePointerNode.execute(objects[0]);
            if (nativeSpacePtr == PNone.NO_VALUE) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw PRaiseNode.raiseUncached(this, SystemError, "Attempting to getter function but object has no associated native space.");
            }
            objects[0] = new PythonAbstractNativeObject(nativeSpacePtr);
            return objects;
        }

        @TruffleBoundary
        public static PBuiltinFunction createLegacyFunction(GraalHPyContext context, PythonLanguage lang, Object owner, String propertyName, Object target, Object closure) {
            PythonObjectFactory factory = context.getSlowPathFactory();
            RootCallTarget rootCallTarget = lang.createCachedCallTarget(l -> new HPyLegacyGetSetDescriptorGetterRoot(l, propertyName, PExternalFunctionWrapper.GETTER),
                            HPyLegacyGetSetDescriptorGetterRoot.class, propertyName);
            if (rootCallTarget == null) {
                throw CompilerDirectives.shouldNotReachHere("Calling non-native get descriptor functions is not support in HPy");
            }
            return factory.createGetSetBuiltinFunction(propertyName, owner, PythonUtils.EMPTY_OBJECT_ARRAY, ExternalFunctionNodes.createKwDefaults(target, closure), rootCallTarget);
        }
    }

    /**
     * A simple and lightweight Python root node that invokes a native setter function. The native
     * call target and the native closure pointer are passed as Python closure.
     */
    static final class HPyGetSetDescriptorSetterRootNode extends HPyGetSetDescriptorRootNode {
        private static final Signature SIGNATURE = new Signature(-1, false, -1, false, new String[]{"$self", "value"}, KEYWORDS_HIDDEN_CALLABLE_AND_CLOSURE, true);

        private HPyGetSetDescriptorSetterRootNode(PythonLanguage language, String name) {
            super(language, name);
        }

        @Override
        protected Object[] createArguments(VirtualFrame frame, Object closure) {
            return new Object[]{PArguments.getArgument(frame, 0), PArguments.getArgument(frame, 1), closure};
        }

        @Override
        protected HPyConvertArgsToSulongNode createArgumentConversionNode() {
            return HPyGetSetSetterToSulongNodeGen.create();
        }

        @Override
        protected HPyCheckFunctionResultNode createResultConversionNode() {
            return HPyCheckPrimitiveResultNodeGen.create();
        }

        @Override
        public Signature getSignature() {
            return SIGNATURE;
        }

        @TruffleBoundary
        public static PBuiltinFunction createFunction(GraalHPyContext hpyContext, Object enclosingType, String propertyName, Object target, Object closure) {
            PythonLanguage lang = hpyContext.getContext().getLanguage();
            RootCallTarget callTarget = lang.createCachedCallTarget(l -> new HPyGetSetDescriptorSetterRootNode(l, propertyName), HPyGetSetDescriptorSetterRootNode.class, propertyName);
            PythonObjectFactory factory = hpyContext.getSlowPathFactory();
            return factory.createGetSetBuiltinFunction(propertyName, enclosingType, PythonUtils.EMPTY_OBJECT_ARRAY, createKwDefaults(target, closure, hpyContext), callTarget);
        }

    }

    static final class HPyLegacyGetSetDescriptorSetterRoot extends SetterRoot {

        @Child private HPyGetNativeSpacePointerNode getNativeSpacePointerNode;

        protected HPyLegacyGetSetDescriptorSetterRoot(PythonLanguage language, String name, PExternalFunctionWrapper provider) {
            super(language, name, provider);
        }

        @Override
        protected Object[] prepareCArguments(VirtualFrame frame) {
            Object[] objects = super.prepareCArguments(frame);
            if (getNativeSpacePointerNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                getNativeSpacePointerNode = insert(HPyGetNativeSpacePointerNodeGen.create());
            }
            /*
             * We now need to pass the native space pointer in a way that the ToSulongNode correctly
             * exposes the bare pointer object. For this, we pack the pointer into a
             * PythonAbstractNativeObject which will just be unwrapped.
             */
            Object nativeSpacePtr = getNativeSpacePointerNode.execute(objects[0]);
            if (nativeSpacePtr == PNone.NO_VALUE) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw PRaiseNode.raiseUncached(this, SystemError, "Attempting to setter function but object has no associated native space.");
            }
            objects[0] = new PythonAbstractNativeObject(nativeSpacePtr);
            return objects;
        }

        @TruffleBoundary
        public static PBuiltinFunction createLegacyFunction(GraalHPyContext context, PythonLanguage lang, Object owner, String propertyName, Object target, Object closure) {
            PythonObjectFactory factory = context.getSlowPathFactory();
            RootCallTarget rootCallTarget = lang.createCachedCallTarget(l -> new HPyLegacyGetSetDescriptorSetterRoot(l, propertyName, PExternalFunctionWrapper.SETTER),
                            HPyLegacyGetSetDescriptorSetterRoot.class, propertyName);
            if (rootCallTarget == null) {
                throw CompilerDirectives.shouldNotReachHere("Calling non-native get descriptor functions is not support in HPy");
            }
            return factory.createGetSetBuiltinFunction(propertyName, owner, PythonUtils.EMPTY_OBJECT_ARRAY, ExternalFunctionNodes.createKwDefaults(target, closure), rootCallTarget);
        }
    }

    static final class HPyGetSetDescriptorNotWritableRootNode extends HPyGetSetDescriptorRootNode {
        private static final Signature SIGNATURE = new Signature(-1, false, -1, false, new String[]{"$self", "value"}, null, true);

        @Child private PRaiseNode raiseNode;
        @Child private GetClassNode getClassNode;
        @Child private GetNameNode getNameNode;

        private HPyGetSetDescriptorNotWritableRootNode(PythonLanguage language, String name) {
            super(language, name);
        }

        @Override
        protected Object[] createArguments(VirtualFrame frame, Object closure) {
            if (raiseNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                raiseNode = insert(PRaiseNode.create());
            }
            if (getClassNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                getClassNode = insert(GetClassNode.create());
            }
            if (getNameNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                getNameNode = insert(GetNameNode.create());
            }
            Object type = getClassNode.execute(PArguments.getArgument(frame, 0));
            throw raiseNode.raise(PythonBuiltinClassType.AttributeError, ErrorMessages.ATTR_S_OF_S_IS_NOT_WRITABLE, getName(), getNameNode.execute(type));
        }

        @Override
        protected HPyConvertArgsToSulongNode createArgumentConversionNode() {
            // not required since the 'createArguments' method will throw an error
            return null;
        }

        @Override
        protected HPyCheckFunctionResultNode createResultConversionNode() {
            // not required since the 'createArguments' method will throw an error
            return null;
        }

        @Override
        public Signature getSignature() {
            return SIGNATURE;
        }

        @TruffleBoundary
        public static PBuiltinFunction createFunction(PythonContext context, Object enclosingType, String propertyName) {
            PythonLanguage lang = context.getLanguage();
            RootCallTarget callTarget = lang.createCachedCallTarget(l -> new HPyGetSetDescriptorNotWritableRootNode(l, propertyName), HPyGetSetDescriptorNotWritableRootNode.class, propertyName);
            PythonObjectFactory factory = context.factory();
            return factory.createGetSetBuiltinFunction(propertyName, enclosingType, 0, callTarget);
        }
    }

    /**
     * Root node to call a C functions with signature
     * {@code int (*HPyFunc_getbufferproc)(HPyContext ctx, HPy self, HPy_buffer *buffer, int flags)}
     * . The {@code buffer} arguments will be created by this root node since it needs the C
     * extension context and the result of a call to this function is an instance of
     * {@link CExtPyBuffer}.
     */
    static final class HPyGetBufferRootNode extends HPyMethodDescriptorRootNode {
        private static final Signature SIGNATURE = new Signature(-1, false, 1, false, new String[]{"self", "flags"}, KEYWORDS_HIDDEN_CALLABLE);

        @Child private ReadIndexedArgumentNode readArg1Node;
        @Child private PCallHPyFunction callAllocateBufferNode;
        @Child private PCallHPyFunction callFreeNode;
        @Child private InteropLibrary ptrLib;
        @Child private InteropLibrary valueLib;
        @Child private PCallCapiFunction callGetByteArrayTypeId;
        @Child private PCallCapiFunction callFromTyped;
        @Child private HPyCloseAndGetHandleNode closeAndGetHandleNode;
        @Child private FromCharPointerNode fromCharPointerNode;
        @Child private CastToJavaStringNode castToJavaStringNode;
        @Child private GetIntArrayNode getIntArrayNode;
        @Child private PRaiseNode raiseNode;

        @CompilationFinal private ConditionProfile isAllocatedProfile;

        @TruffleBoundary
        public HPyGetBufferRootNode(PythonLanguage language, String name) {
            super(language, name, HPyCheckPrimitiveResultNodeGen.create(), HPyGetBufferProcToSulongNodeGen.create());
        }

        @Override
        public CExtPyBuffer execute(VirtualFrame frame) {
            getCalleeContext().enter(frame);
            Object bufferPtr = null;
            GraalHPyContext hpyContext = null;
            try {
                Object callable = ensureReadCallableNode().execute(frame);
                hpyContext = readContext(frame);
                bufferPtr = ensureCallAllocateBufferNode().call(hpyContext, GraalHPyNativeSymbol.GRAAL_HPY_ALLOCATE_BUFFER);
                Object[] cArguments = new Object[]{getSelf(frame), bufferPtr, getArg1(frame)};
                getInvokeNode().execute(frame, getName(), callable, hpyContext, cArguments);
                return createPyBuffer(hpyContext, bufferPtr);
            } finally {
                if (hpyContext != null && bufferPtr != null) {
                    ensureCallFreeNode().call(hpyContext, GraalHPyNativeSymbol.GRAAL_HPY_FREE, bufferPtr);
                }
                getCalleeContext().exit(frame, this);
            }
        }

        /**
         * Reads the values from C struct {@code HPy_buffer}, converts them appropriately and
         * creates an instance of {@link CExtPyBuffer}.
         *
         * <pre>
         *     typedef struct {
         *         void *buf;
         *         HPy obj;
         *         Py_ssize_t len;
         *         Py_ssize_t itemsize;
         *         int readonly;
         *         int ndim;
         *         char *format;
         *         Py_ssize_t *shape;
         *         Py_ssize_t *strides;
         *         Py_ssize_t *suboffsets;
         *         void *internal;
         * } HPy_buffer;
         * </pre>
         *
         */
        private CExtPyBuffer createPyBuffer(GraalHPyContext hpyContext, Object bufferPtr) {
            if (ptrLib == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                ptrLib = insert(InteropLibrary.getFactory().createDispatched(2));
            }
            if (callGetByteArrayTypeId == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                callGetByteArrayTypeId = insert(PCallCapiFunction.create());
            }
            if (callFromTyped == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                callFromTyped = insert(PCallCapiFunction.create());
            }
            if (closeAndGetHandleNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                closeAndGetHandleNode = insert(HPyCloseAndGetHandleNodeGen.create());
            }
            if (fromCharPointerNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                fromCharPointerNode = insert(FromCharPointerNodeGen.create());
            }
            if (castToJavaStringNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                castToJavaStringNode = insert(CastToJavaStringNode.create());
            }
            if (valueLib == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                valueLib = insert(InteropLibrary.getFactory().createDispatched(3));
            }
            try {
                int len = castToInt(ptrLib.readMember(bufferPtr, "len"));
                Object buf = ptrLib.readMember(bufferPtr, "buf");
                /*
                 * Ensure that the 'buf' pointer is typed because later on someone will try to read
                 * bytes from the pointer via interop.
                 */
                Object typeId = callGetByteArrayTypeId.call(NativeCAPISymbol.FUN_GET_BYTE_ARRAY_TYPE_ID, len);
                buf = callFromTyped.call(NativeCAPISymbol.FUN_POLYGLOT_FROM_TYPED, buf, typeId);
                Object ownerObj = ptrLib.readMember(bufferPtr, "obj");
                /*
                 * Note: Reading 'obj' from 'HPy_buffer *' will just return 'bufferPtr +
                 * offsetof(obj)' because member 'obj' is a struct. So we need to further read the
                 * content of the HPy handle to get the real handle value.
                 */
                ownerObj = ptrLib.readMember(ownerObj, GraalHPyHandle.I);
                Object owner = null;
                if (!valueLib.isNull(ownerObj)) {
                    // Since we are now the owner of the handle and no one else will ever use it, we
                    // need to close it.
                    owner = closeAndGetHandleNode.execute(hpyContext, ownerObj);
                }

                int ndim = castToInt(ptrLib.readMember(bufferPtr, "ndim"));
                int itemSize = castToInt(ptrLib.readMember(bufferPtr, "itemsize"));
                boolean readonly = castToInt(ptrLib.readMember(bufferPtr, "readonly")) != 0;
                String format = castToJavaStringNode.execute(fromCharPointerNode.execute(ptrLib.readMember(bufferPtr, "format")));
                Object shapePtr = ptrLib.readMember(bufferPtr, "shape");
                Object stridesPtr = ptrLib.readMember(bufferPtr, "strides");
                Object suboffsetsPtr = ptrLib.readMember(bufferPtr, "suboffsets");
                Object internal = ptrLib.readMember(bufferPtr, "internal");
                int[] shape = null;
                int[] strides = null;
                int[] subOffsets = null;
                if (ndim > 0) {
                    if (!ptrLib.isNull(shapePtr)) {
                        shape = ensureGetIntArrayNode().execute(shapePtr, ndim, LLVMType.Py_ssize_t);
                    }
                    if (!ptrLib.isNull(stridesPtr)) {
                        strides = ensureGetIntArrayNode().execute(stridesPtr, ndim, LLVMType.Py_ssize_t);
                    }
                    if (!ptrLib.isNull(suboffsetsPtr)) {
                        subOffsets = ensureGetIntArrayNode().execute(suboffsetsPtr, ndim, LLVMType.Py_ssize_t);
                    }
                }
                return new CExtPyBuffer(buf, owner, len, itemSize, readonly, ndim, format, shape, strides, subOffsets, internal);
            } catch (UnsupportedMessageException | UnknownIdentifierException e) {
                // that's clearly an internal error
                throw CompilerDirectives.shouldNotReachHere();
            } catch (UnsupportedTypeException e) {
                /*
                 * This exception is thrown by GetIntArrayNode to indicate that an element cannot be
                 * casted to a Java integer. We would usually consider that to be an internal error
                 * but since the values are provided by a user C function, we cannot be sure and
                 * thus we treat that as a run-time error.
                 */
                throw ensureRaiseNode().raise(PythonErrorType.SystemError, "Cannot read C array");
            }
        }

        private int castToInt(Object value) {
            if (valueLib.fitsInInt(value)) {
                try {
                    return valueLib.asInt(value);
                } catch (UnsupportedMessageException e) {
                    throw CompilerDirectives.shouldNotReachHere();
                }
            }
            throw ensureRaiseNode().raise(PythonErrorType.SystemError, "Cannot read");
        }

        private PCallHPyFunction ensureCallAllocateBufferNode() {
            if (callAllocateBufferNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                callAllocateBufferNode = insert(PCallHPyFunctionNodeGen.create());
            }
            return callAllocateBufferNode;
        }

        private PCallHPyFunction ensureCallFreeNode() {
            if (callFreeNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                callFreeNode = insert(PCallHPyFunctionNodeGen.create());
            }
            return callFreeNode;
        }

        private GetIntArrayNode ensureGetIntArrayNode() {
            if (getIntArrayNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                getIntArrayNode = insert(GetIntArrayNodeGen.create());
            }
            return getIntArrayNode;
        }

        private PRaiseNode ensureRaiseNode() {
            if (raiseNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                raiseNode = insert(PRaiseNode.create());
            }
            return raiseNode;
        }

        @Override
        protected Object[] prepareCArguments(VirtualFrame frame, GraalHPyContext hpyContext) {
            throw CompilerDirectives.shouldNotReachHere();
        }

        private Object getArg1(VirtualFrame frame) {
            if (readArg1Node == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                readArg1Node = insert(ReadIndexedArgumentNode.create(1));
            }
            return readArg1Node.execute(frame);
        }

        @Override
        public Signature getSignature() {
            return SIGNATURE;
        }
    }

    /**
     * Root node to call a C functions with signature
     * {@code void (*HPyFunc_releasebufferproc)(HPyContext ctx, HPy self, HPy_buffer *buffer)} .
     */
    static final class HPyReleaseBufferRootNode extends HPyMethodDescriptorRootNode {
        private static final Signature SIGNATURE = new Signature(-1, false, 1, false, new String[]{"self", "buffer"}, KEYWORDS_HIDDEN_CALLABLE);

        @Child private ReadIndexedArgumentNode readArg1Node;
        @Child private PCallHPyFunction callBufferFreeNode;

        @TruffleBoundary
        public HPyReleaseBufferRootNode(PythonLanguage language, String name) {
            super(language, name, HPyCheckVoidResultNodeGen.create(), HPyReleaseBufferProcToSulongNodeGen.create());
        }

        @Override
        public Object execute(VirtualFrame frame) {
            getCalleeContext().enter(frame);
            try {
                Object callable = ensureReadCallableNode().execute(frame);
                GraalHPyContext hpyContext = readContext(frame);
                Object arg1 = getArg1(frame);
                if (!(arg1 instanceof CExtPyBuffer)) {
                    throw CompilerDirectives.shouldNotReachHere("invalid argument");
                }
                CExtPyBuffer buffer = (CExtPyBuffer) arg1;
                GraalHPyBuffer hpyBuffer = new GraalHPyBuffer(hpyContext, buffer);
                try {
                    getInvokeNode().execute(frame, getName(), callable, hpyContext, new Object[]{getSelf(frame), hpyBuffer});
                } finally {
                    if (hpyBuffer.isPointer()) {
                        hpyBuffer.free(ensureCallBufferFreeNode());
                    }
                }
                return PNone.NONE;
            } finally {
                getCalleeContext().exit(frame, this);
            }
        }

        @Override
        protected Object[] prepareCArguments(VirtualFrame frame, GraalHPyContext hpyContext) {
            throw CompilerDirectives.shouldNotReachHere();
        }

        @Override
        protected Object processResult(VirtualFrame frame, Object result) {
            throw CompilerDirectives.shouldNotReachHere();
        }

        private Object getArg1(VirtualFrame frame) {
            if (readArg1Node == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                readArg1Node = insert(ReadIndexedArgumentNode.create(1));
            }
            return readArg1Node.execute(frame);
        }

        private PCallHPyFunction ensureCallBufferFreeNode() {
            if (callBufferFreeNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                callBufferFreeNode = insert(PCallHPyFunctionNodeGen.create());
            }
            return callBufferFreeNode;
        }

        @Override
        public Signature getSignature() {
            return SIGNATURE;
        }
    }
}
