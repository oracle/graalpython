/*
 * Copyright (c) 2019, 2024, Oracle and/or its affiliates. All rights reserved.
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
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.TypeError;
import static com.oracle.graal.python.util.PythonUtils.EMPTY_TRUFFLESTRING_ARRAY;
import static com.oracle.graal.python.util.PythonUtils.tsArray;
import static com.oracle.graal.python.util.PythonUtils.tsLiteral;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.cext.PythonAbstractNativeObject;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.FromCharPointerNode;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodesFactory.FromCharPointerNodeGen;
import com.oracle.graal.python.builtins.objects.cext.capi.ExternalFunctionNodes;
import com.oracle.graal.python.builtins.objects.cext.capi.ExternalFunctionNodes.GetterRoot;
import com.oracle.graal.python.builtins.objects.cext.capi.ExternalFunctionNodes.PExternalFunctionWrapper;
import com.oracle.graal.python.builtins.objects.cext.capi.ExternalFunctionNodes.SetterRoot;
import com.oracle.graal.python.builtins.objects.cext.common.CExtCommonNodes;
import com.oracle.graal.python.builtins.objects.cext.common.CExtCommonNodes.GetIndexNode;
import com.oracle.graal.python.builtins.objects.cext.common.CExtCommonNodes.TransformExceptionFromNativeNode;
import com.oracle.graal.python.builtins.objects.cext.common.CExtContext;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyDef.HPyFuncSignature;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyDef.HPySlotWrapper;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNodes.HPyCloseAndGetHandleNode;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNodes.HPyCloseArgHandlesNode;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNodes.HPyConvertArgsToSulongNode;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNodes.HPyGetNativeSpacePointerNode;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNodesFactory.HPyAllAsHandleNodeGen;
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
import com.oracle.graal.python.builtins.objects.cext.hpy.HPyExternalFunctionNodesFactory.HPyCheckHandleResultNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.HPyExternalFunctionNodesFactory.HPyCheckPrimitiveResultNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.HPyExternalFunctionNodesFactory.HPyCheckVoidResultNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.HPyExternalFunctionNodesFactory.HPyExternalFunctionInvokeNodeGen;
import com.oracle.graal.python.builtins.objects.function.PArguments;
import com.oracle.graal.python.builtins.objects.function.PBuiltinFunction;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.builtins.objects.function.Signature;
import com.oracle.graal.python.builtins.objects.memoryview.CExtPyBuffer;
import com.oracle.graal.python.builtins.objects.object.PythonObject;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.PRootNode;
import com.oracle.graal.python.nodes.argument.ReadIndexedArgumentNode;
import com.oracle.graal.python.nodes.argument.ReadVarArgsNode;
import com.oracle.graal.python.nodes.argument.ReadVarKeywordsNode;
import com.oracle.graal.python.runtime.ExecutionContext.CalleeContext;
import com.oracle.graal.python.runtime.ExecutionContext.IndirectCallContext;
import com.oracle.graal.python.runtime.IndirectCallData;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.PythonContext.PythonThreadState;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.strings.TruffleString;

public abstract class HPyExternalFunctionNodes {

    public static final TruffleString KW_CALLABLE = tsLiteral("$callable");
    private static final TruffleString KW_CLOSURE = tsLiteral("$closure");
    private static final TruffleString KW_CONTEXT = tsLiteral("$context");
    private static final TruffleString[] KEYWORDS_HIDDEN_CONTEXT = {KW_CONTEXT};
    private static final TruffleString[] KEYWORDS_HIDDEN_CALLABLE = {KW_CONTEXT, KW_CALLABLE};
    private static final TruffleString[] KEYWORDS_HIDDEN_CALLABLE_AND_CLOSURE = {KW_CONTEXT, KW_CALLABLE, KW_CLOSURE};
    private static final Object[] KW_DEFAULTS = {PNone.NO_VALUE};

    private static PKeyword[] createKwDefaults(GraalHPyContext context) {
        return new PKeyword[]{new PKeyword(KW_CONTEXT, context)};
    }

    private static PKeyword[] createKwDefaults(Object callable, GraalHPyContext context) {
        // return new PKeyword[]{new PKeyword(KW_CALLABLE, callable), new PKeyword(KW_CONTEXT,
        // context)};
        return new PKeyword[]{new PKeyword(KW_CONTEXT, context), new PKeyword(KW_CALLABLE, callable)};
    }

    public static PKeyword[] createKwDefaults(Object callable, Object closure, GraalHPyContext context) {
        // return new PKeyword[]{new PKeyword(KW_CALLABLE, callable), new PKeyword(KW_CONTEXT,
        // context), new PKeyword(KW_CLOSURE, closure)};
        return new PKeyword[]{new PKeyword(KW_CONTEXT, context), new PKeyword(KW_CALLABLE, callable), new PKeyword(KW_CLOSURE, closure)};
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
    static PBuiltinFunction createWrapperFunction(PythonLanguage language, GraalHPyContext context, HPyFuncSignature signature, TruffleString name, Object callable, Object enclosingType,
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

    private static PRootNode createRootNode(PythonLanguage language, HPyFuncSignature signature, TruffleString name) {
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
    static PBuiltinFunction createWrapperFunction(PythonLanguage language, GraalHPyContext context, HPySlotWrapper wrapper, TruffleString name, Object callable, Object enclosingType,
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
        PKeyword[] kwDefaults;
        if (wrapper == HPySlotWrapper.CALL) {
            kwDefaults = createKwDefaults(context);
        } else {
            kwDefaults = createKwDefaults(callable, context);

        }
        return factory.createBuiltinFunction(name, enclosingType, defaults, kwDefaults, 0, callTarget);
    }

    private static PRootNode createSlotRootNode(PythonLanguage language, HPySlotWrapper wrapper, TruffleString name) {
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
            case HASHFUNC:
                return new HPyMethHashRoot(language, name);
            case CALL:
                return new HPyMethCallRoot(language, name);
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
    abstract static class HPyExternalFunctionInvokeNode extends Node {

        @Child private HPyConvertArgsToSulongNode toSulongNode;
        @Child private HPyCheckFunctionResultNode checkFunctionResultNode;
        @Child private HPyCloseArgHandlesNode handleCloseNode;

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

        public abstract Object execute(VirtualFrame frame, TruffleString name, Object callable, GraalHPyContext hPyContext, Object[] frameArgs);

        @Specialization(limit = "1")
        Object doIt(VirtualFrame frame, TruffleString name, Object callable, GraalHPyContext hPyContext, Object[] arguments,
                        @Cached("createFor(this)") IndirectCallData indirectCallData,
                        @CachedLibrary("callable") InteropLibrary lib,
                        @Cached PRaiseNode raiseNode) {
            Object[] convertedArguments = new Object[arguments.length + 1];
            toSulongNode.executeInto(frame, arguments, 0, convertedArguments, 1);

            // first arg is always the HPyContext
            convertedArguments[0] = hPyContext.getBackend();

            PythonLanguage language = PythonLanguage.get(this);
            PythonContext ctx = hPyContext.getContext();
            PythonThreadState pythonThreadState = ctx.getThreadState(language);

            // If any code requested the caught exception (i.e. used 'sys.exc_info()'), we store
            // it to the context since we cannot propagate it through the native frames.
            Object state = IndirectCallContext.enter(frame, pythonThreadState, indirectCallData);

            try {
                return checkFunctionResultNode.execute(pythonThreadState, name, lib.execute(callable, convertedArguments));
            } catch (UnsupportedTypeException | UnsupportedMessageException e) {
                throw raiseNode.raise(PythonBuiltinClassType.TypeError, ErrorMessages.CALLING_NATIVE_FUNC_FAILED, name, e);
            } catch (ArityException e) {
                throw raiseNode.raise(PythonBuiltinClassType.TypeError, ErrorMessages.CALLING_NATIVE_FUNC_EXPECTED_ARGS, name, e.getExpectedMinArity(), e.getActualArity());
            } finally {
                // special case after calling a C function: transfer caught exception back to frame
                // to simulate the global state semantics
                PArguments.setException(frame, pythonThreadState.getCaughtException());
                IndirectCallContext.exit(frame, pythonThreadState, state);

                // close all handles (if necessary)
                if (handleCloseNode != null) {
                    handleCloseNode.executeInto(frame, convertedArguments, 1);
                }
            }
        }
    }

    abstract static class HPyMethodDescriptorRootNode extends PRootNode {
        @Child private CalleeContext calleeContext;
        @Child private HPyExternalFunctionInvokeNode invokeNode;
        @Child private ReadIndexedArgumentNode readSelfNode;
        @Child private ReadIndexedArgumentNode readCallableNode;
        @Child private ReadIndexedArgumentNode readContextNode;

        private final TruffleString name;

        @TruffleBoundary
        public HPyMethodDescriptorRootNode(PythonLanguage language, TruffleString name, HPyConvertArgsToSulongNode convertArgsToSulongNode) {
            super(language);
            this.name = name;
            this.invokeNode = HPyExternalFunctionInvokeNodeGen.create(convertArgsToSulongNode);
        }

        @TruffleBoundary
        public HPyMethodDescriptorRootNode(PythonLanguage language, TruffleString name, HPyCheckFunctionResultNode checkFunctionResultNode, HPyConvertArgsToSulongNode convertArgsToSulongNode) {
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
            Object callable = ensureReadCallableNode().execute(frame);
            GraalHPyContext hpyContext = readContext(frame);
            Object[] cArguments = prepareCArguments(frame, hpyContext);
            getCalleeContext().enter(frame);
            try {
                return processResult(frame, invokeNode.execute(frame, name, callable, hpyContext, cArguments));
            } finally {
                getCalleeContext().exit(frame, this);
                closeCArguments(frame, hpyContext, cArguments);
            }
        }

        protected abstract Object[] prepareCArguments(VirtualFrame frame, GraalHPyContext hpyContext);

        protected Object processResult(@SuppressWarnings("unused") VirtualFrame frame, Object result) {
            return result;
        }

        @SuppressWarnings("unused")
        protected void closeCArguments(VirtualFrame frame, GraalHPyContext hpyContext, Object[] cArguments) {
            // nothing to do by default
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
                // we insert a hidden argument after the hidden context argument
                int hiddenArg = getSignature().getParameterIds().length + 1;
                readCallableNode = insert(ReadIndexedArgumentNode.create(hiddenArg));
            }
            return readCallableNode;
        }

        protected final GraalHPyContext readContext(VirtualFrame frame) {
            if (readContextNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                // we insert a hidden argument at the end of the positional arguments
                int hiddenArg = getSignature().getParameterIds().length;
                readContextNode = insert(ReadIndexedArgumentNode.create(hiddenArg));
            }
            Object hpyContext = readContextNode.execute(frame);
            if (hpyContext instanceof GraalHPyContext) {
                return (GraalHPyContext) hpyContext;
            }
            throw CompilerDirectives.shouldNotReachHere("invalid HPy context");
        }

        @Override
        public String getName() {
            return name.toJavaStringUncached();
        }

        public TruffleString getTSName() {
            return name;
        }

        @Override
        public String toString() {
            return "<METH root " + name.toJavaStringUncached() + ">";
        }

        @Override
        public boolean isInternal() {
            return true;
        }

        @Override
        public boolean isPythonInternal() {
            return true;
        }

        @Override
        public boolean setsUpCalleeContext() {
            return true;
        }
    }

    static final class HPyMethNoargsRoot extends HPyMethodDescriptorRootNode {
        private static final Signature SIGNATURE = new Signature(1, false, -1, false, tsArray("self"), KEYWORDS_HIDDEN_CALLABLE, true);

        public HPyMethNoargsRoot(PythonLanguage language, TruffleString name, boolean nativePrimitiveResult) {
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
        private static final Signature SIGNATURE = new Signature(-1, false, -1, false, tsArray("self", "arg"), KEYWORDS_HIDDEN_CALLABLE, true);

        @Child private ReadIndexedArgumentNode readArgNode;

        public HPyMethORoot(PythonLanguage language, TruffleString name, boolean nativePrimitiveResult) {
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
        private static final Signature SIGNATURE = new Signature(-1, false, 1, false, tsArray("self"), KEYWORDS_HIDDEN_CALLABLE, true);

        @Child private ReadVarArgsNode readVarargsNode;

        @TruffleBoundary
        public HPyMethVarargsRoot(PythonLanguage language, TruffleString name) {
            super(language, name, HPyVarargsToSulongNodeGen.create());
        }

        @Override
        protected Object[] prepareCArguments(VirtualFrame frame, GraalHPyContext hpyContext) {
            Object[] args = getVarargs(frame);
            return new Object[]{getSelf(frame), hpyContext.createArgumentsArray(args), (long) args.length};
        }

        @Override
        protected void closeCArguments(VirtualFrame frame, GraalHPyContext hpyContext, Object[] cArguments) {
            hpyContext.freeArgumentsArray(cArguments[1]);
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
        private static final Signature SIGNATURE = new Signature(-1, true, 1, false, tsArray("self"), KEYWORDS_HIDDEN_CALLABLE, true);

        @Child private ReadVarArgsNode readVarargsNode;
        @Child private ReadVarKeywordsNode readKwargsNode;
        @Child private PythonObjectFactory factory;

        @TruffleBoundary
        public HPyMethKeywordsRoot(PythonLanguage language, TruffleString name) {
            super(language, name, HPyKeywordsToSulongNodeGen.create());
        }

        @Override
        protected Object[] prepareCArguments(VirtualFrame frame, GraalHPyContext hpyContext) {
            Object[] positionalArgs = getVarargs(frame);
            PKeyword[] keywords = getKwargs(frame);
            long nPositionalArgs = positionalArgs.length;

            Object[] args;
            Object kwnamesTuple;
            // this condition is implicitly profiled by 'getKwnamesTuple'
            if (keywords.length > 0) {
                args = PythonUtils.arrayCopyOf(positionalArgs, positionalArgs.length + keywords.length);
                TruffleString[] kwnames = new TruffleString[keywords.length];
                for (int i = 0; i < keywords.length; i++) {
                    args[positionalArgs.length + i] = keywords[i].getValue();
                    kwnames[i] = keywords[i].getName();
                }
                kwnamesTuple = getKwnamesTuple(kwnames);
            } else {
                args = positionalArgs;
                kwnamesTuple = GraalHPyHandle.NULL_HANDLE_DELEGATE;
            }
            return new Object[]{getSelf(frame), createArgumentsArray(hpyContext, args), nPositionalArgs, kwnamesTuple};
        }

        @Override
        protected void closeCArguments(VirtualFrame frame, GraalHPyContext hpyContext, Object[] cArguments) {
            hpyContext.freeArgumentsArray(cArguments[1]);
        }

        private Object createArgumentsArray(GraalHPyContext hpyContext, Object[] args) {
            return hpyContext.createArgumentsArray(args);
        }

        private Object[] getVarargs(VirtualFrame frame) {
            if (readVarargsNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                readVarargsNode = insert(ReadVarArgsNode.create(true));
            }
            return readVarargsNode.executeObjectArray(frame);
        }

        private PKeyword[] getKwargs(VirtualFrame frame) {
            if (PArguments.getKeywordArguments(frame).length == 0) {
                return PKeyword.EMPTY_KEYWORDS;
            }
            if (readKwargsNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                readKwargsNode = insert(ReadVarKeywordsNode.create());
            }
            return (PKeyword[]) readKwargsNode.execute(frame);
        }

        private PTuple getKwnamesTuple(TruffleString[] kwnames) {
            if (factory == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                factory = insert(PythonObjectFactory.create());
            }
            return factory.createTuple(kwnames);
        }

        @Override
        public Signature getSignature() {
            return SIGNATURE;
        }
    }

    static final class HPyMethInitProcRoot extends HPyMethodDescriptorRootNode {
        private static final Signature SIGNATURE = new Signature(-1, true, 1, false, tsArray("self"), KEYWORDS_HIDDEN_CALLABLE, true);

        @Child private ReadVarArgsNode readVarargsNode;
        @Child private ReadVarKeywordsNode readKwargsNode;

        @TruffleBoundary
        public HPyMethInitProcRoot(PythonLanguage language, TruffleString name) {
            super(language, name, HPyCheckPrimitiveResultNodeGen.create(), HPyKeywordsToSulongNodeGen.create());
        }

        @Override
        protected Object[] prepareCArguments(VirtualFrame frame, GraalHPyContext hpyContext) {
            Object[] args = getVarargs(frame);
            return new Object[]{getSelf(frame), hpyContext.createArgumentsArray(args), (long) args.length, getKwargs(frame)};
        }

        @Override
        protected void closeCArguments(VirtualFrame frame, GraalHPyContext hpyContext, Object[] cArguments) {
            hpyContext.freeArgumentsArray(cArguments[1]);
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
                readKwargsNode = insert(ReadVarKeywordsNode.createForUserFunction(EMPTY_TRUFFLESTRING_ARRAY));
            }
            return readKwargsNode.execute(frame);
        }

        @Override
        public Signature getSignature() {
            return SIGNATURE;
        }
    }

    static final class HPyMethTernaryRoot extends HPyMethodDescriptorRootNode {
        private static final Signature SIGNATURE = new Signature(3, false, -1, false, tsArray("x", "y", "z"), KEYWORDS_HIDDEN_CALLABLE, true);

        @Child private ReadIndexedArgumentNode readArg1Node;
        @Child private ReadIndexedArgumentNode readArg2Node;

        public HPyMethTernaryRoot(PythonLanguage language, TruffleString name) {
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
        private static final Signature SIGNATURE = new Signature(2, false, -1, false, tsArray("$self", "n"), KEYWORDS_HIDDEN_CALLABLE, true);

        @Child private ReadIndexedArgumentNode readArg1Node;

        public HPyMethSSizeArgFuncRoot(PythonLanguage language, TruffleString name) {
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

        public HPyMethSqItemWrapperRoot(PythonLanguage language, TruffleString name) {
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

        public HPyMethSqSetitemWrapperRoot(PythonLanguage language, TruffleString name) {
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
        private static final Signature SIGNATURE = new Signature(3, false, -1, false, tsArray("$self", "n", "m"), KEYWORDS_HIDDEN_CALLABLE, true);

        @Child private ReadIndexedArgumentNode readArg1Node;
        @Child private ReadIndexedArgumentNode readArg2Node;

        public HPyMethSSizeSSizeArgFuncRoot(PythonLanguage language, TruffleString name) {
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
        private static final Signature SIGNATURE = new Signature(-1, false, -1, false, tsArray("self"), KEYWORDS_HIDDEN_CALLABLE);

        public HPyMethInquiryRoot(PythonLanguage language, TruffleString name) {
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
        private static final Signature SIGNATURE = new Signature(-1, false, 1, false, tsArray("$self", "x"), KEYWORDS_HIDDEN_CALLABLE, true);

        @Child private ReadIndexedArgumentNode readArg1Node;
        @Child private ReadVarArgsNode readVarargsNode;
        @Child private PRaiseNode raiseNode;

        public HPyMethObjObjArgProcRoot(PythonLanguage language, TruffleString name) {
            super(language, name, HPyCheckPrimitiveResultNodeGen.create(), HPyAllAsHandleNodeGen.create());
        }

        @Override
        protected Object[] prepareCArguments(VirtualFrame frame, @SuppressWarnings("unused") GraalHPyContext hpyContext) {
            Object[] varargs = getVarargs(frame);
            if (varargs.length == 0) {
                return new Object[]{getSelf(frame), getArg1(frame), PNone.NO_VALUE};
            } else if (varargs.length == 1) {
                return new Object[]{getSelf(frame), getArg1(frame), varargs[0]};
            } else {
                throw getRaiseNode().raise(PythonBuiltinClassType.TypeError,
                                ErrorMessages.TAKES_FROM_D_TO_D_POS_ARG_S_BUT_D_S_GIVEN_S,
                                getName(), 2, 3, "s", 1 + varargs.length, "were", "");
            }
        }

        private PRaiseNode getRaiseNode() {
            if (raiseNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                raiseNode = insert(PRaiseNode.create());
            }
            return raiseNode;
        }

        private Object[] getVarargs(VirtualFrame frame) {
            if (readVarargsNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                readVarargsNode = insert(ReadVarArgsNode.create(true));
            }
            return readVarargsNode.executeObjectArray(frame);
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

    static final class HPyMethObjObjProcRoot extends HPyMethodDescriptorRootNode {
        private static final Signature SIGNATURE = new Signature(2, false, -1, false, tsArray("$self", "other"), KEYWORDS_HIDDEN_CALLABLE, true);

        @Child private ReadIndexedArgumentNode readArg1Node;

        public HPyMethObjObjProcRoot(PythonLanguage language, TruffleString name) {
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
        private static final Signature SIGNATURE = new Signature(3, false, -1, false, tsArray("$self", "arg0", "arg1"), KEYWORDS_HIDDEN_CALLABLE, true);

        @Child private ReadIndexedArgumentNode readArg1Node;
        @Child private ReadIndexedArgumentNode readArg2Node;

        public HPyMethSSizeObjArgProcRoot(PythonLanguage language, TruffleString name) {
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
        private static final Signature SIGNATURE = new Signature(-1, false, -1, false, tsArray("self", "other"), KEYWORDS_HIDDEN_CALLABLE, true);

        @Child private ReadIndexedArgumentNode readOtherNode;

        public HPyMethReverseBinaryRoot(PythonLanguage language, TruffleString name, boolean nativePrimitiveResult) {
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

    public abstract static class HPyCheckFunctionResultNode extends Node {

        public abstract Object execute(PythonThreadState pythonThreadState, TruffleString name, Object value);
    }

    // roughly equivalent to _Py_CheckFunctionResult in Objects/call.c
    @GenerateUncached
    @GenerateInline(false)
    @ImportStatic(PGuards.class)
    public abstract static class HPyCheckHandleResultNode extends HPyCheckFunctionResultNode {

        @Specialization
        static Object doLongNull(PythonThreadState pythonThreadState, TruffleString name, Object value,
                        @Bind("this") Node inliningTarget,
                        @Cached HPyCloseAndGetHandleNode closeAndGetHandleNode,
                        @Cached TransformExceptionFromNativeNode transformExceptionFromNativeNode) {
            Object delegate = closeAndGetHandleNode.execute(inliningTarget, value);
            transformExceptionFromNativeNode.execute(inliningTarget, pythonThreadState, name, delegate == GraalHPyHandle.NULL_HANDLE_DELEGATE, true);
            return delegate;
        }
    }

    /**
     * Similar to {@link HPyCheckFunctionResultNode}, this node checks a primitive result of a
     * native function. This node guarantees that an {@code int} or {@code long} is returned.
     */
    @GenerateUncached
    @GenerateInline(false)
    @ImportStatic(PGuards.class)
    abstract static class HPyCheckPrimitiveResultNode extends HPyCheckFunctionResultNode {
        public abstract int executeInt(PythonThreadState context, TruffleString name, int value);

        public abstract long executeLong(PythonThreadState context, TruffleString name, long value);

        @Specialization
        static int doInteger(PythonThreadState pythonThreadState, TruffleString name, int value,
                        @Bind("this") Node inliningTarget,
                        @Shared @Cached TransformExceptionFromNativeNode transformExceptionFromNativeNode) {
            transformExceptionFromNativeNode.execute(inliningTarget, pythonThreadState, name, value == -1, false);
            return value;
        }

        @Specialization(replaces = "doInteger")
        static long doLong(PythonThreadState pythonThreadState, TruffleString name, long value,
                        @Bind("this") Node inliningTarget,
                        @Shared @Cached TransformExceptionFromNativeNode transformExceptionFromNativeNode) {
            transformExceptionFromNativeNode.execute(inliningTarget, pythonThreadState, name, value == -1, false);
            return value;
        }

        @Specialization(limit = "1")
        static Object doObject(PythonThreadState pythonThreadState, TruffleString name, Object value,
                        @Bind("this") Node inliningTarget,
                        @CachedLibrary("value") InteropLibrary lib,
                        @Shared @Cached PRaiseNode.Lazy raiseNode,
                        @Shared @Cached TransformExceptionFromNativeNode transformExceptionFromNativeNode) {
            if (lib.fitsInLong(value)) {
                try {
                    long lvalue = lib.asLong(value);
                    transformExceptionFromNativeNode.execute(inliningTarget, pythonThreadState, name, lvalue == -1, false);
                    return lvalue;
                } catch (UnsupportedMessageException e) {
                    throw CompilerDirectives.shouldNotReachHere();
                }
            }
            throw raiseNode.get(inliningTarget).raise(SystemError, ErrorMessages.FUNC_S_DIDNT_RETURN_INT, name);
        }
    }

    /**
     * Does not actually check the result of a function (since this is used when {@code void}
     * functions are called) but checks if an error occurred during execution of the function.
     */
    @GenerateUncached
    @GenerateInline(false)
    @ImportStatic(PGuards.class)
    abstract static class HPyCheckVoidResultNode extends HPyCheckFunctionResultNode {

        @Specialization
        static Object doGeneric(PythonThreadState threadState, TruffleString name, Object value,
                        @Bind("this") Node inliningTarget,
                        @Cached TransformExceptionFromNativeNode transformExceptionFromNativeNode) {
            /*
             * A 'void' function never indicates an error but an error could still happen. So this
             * must also be checked. The actual result value (which will be something like NULL or
             * 0) is not used.
             */
            transformExceptionFromNativeNode.execute(inliningTarget, threadState, name, false, true);
            return value;
        }
    }

    static final class HPyMethRichcmpOpRootNode extends HPyMethodDescriptorRootNode {
        private static final Signature SIGNATURE = new Signature(-1, false, -1, false, tsArray("self", "other"), KEYWORDS_HIDDEN_CALLABLE, true);
        @Child private ReadIndexedArgumentNode readArgNode;

        private final int op;

        HPyMethRichcmpOpRootNode(PythonLanguage language, TruffleString name, int op) {
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

        private final TruffleString name;

        HPyGetSetDescriptorRootNode(PythonLanguage language, TruffleString name) {
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
            return name.toJavaStringUncached();
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
                // we insert a hidden argument after the hidden context argument
                int hiddenArg = getSignature().getParameterIds().length + 1;
                readCallableNode = insert(ReadIndexedArgumentNode.create(hiddenArg));
            }
            return readCallableNode.execute(frame);
        }

        private GraalHPyContext readContext(VirtualFrame frame) {
            if (readContextNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                // we insert a hidden argument at the end of the positional arguments
                int hiddenArg = getSignature().getParameterIds().length;
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

        @Override
        public boolean setsUpCalleeContext() {
            return true;
        }
    }

    /**
     * A simple and lightweight Python root node that invokes a native getter function. The native
     * call target and the native closure pointer are passed as Python closure.
     */
    static final class HPyGetSetDescriptorGetterRootNode extends HPyGetSetDescriptorRootNode {
        private static final Signature SIGNATURE = new Signature(-1, false, -1, false, tsArray("$self"), KEYWORDS_HIDDEN_CALLABLE_AND_CLOSURE, true);

        HPyGetSetDescriptorGetterRootNode(PythonLanguage language, TruffleString name) {
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
        public static PBuiltinFunction createFunction(GraalHPyContext hpyContext, Object enclosingType, TruffleString propertyName, Object target, Object closure) {
            PythonContext pythonContext = hpyContext.getContext();
            PythonLanguage lang = pythonContext.getLanguage();
            RootCallTarget callTarget = lang.createCachedCallTarget(l -> new HPyGetSetDescriptorGetterRootNode(l, propertyName), HPyGetSetDescriptorGetterRootNode.class, propertyName);
            PythonObjectFactory factory = pythonContext.getCore().factory();
            return factory.createBuiltinFunction(propertyName, enclosingType, PythonUtils.EMPTY_OBJECT_ARRAY, createKwDefaults(target, closure, hpyContext), 0, callTarget);
        }
    }

    static final class HPyLegacyGetSetDescriptorGetterRoot extends GetterRoot {

        @Child private HPyGetNativeSpacePointerNode getNativeSpacePointerNode;

        protected HPyLegacyGetSetDescriptorGetterRoot(PythonLanguage language, TruffleString name, PExternalFunctionWrapper provider) {
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
             * We now need to pass the native space pointer in a way that the PythonToNativeNode
             * correctly exposes the bare pointer object. For this, we pack the pointer into a
             * PythonAbstractNativeObject which will just be unwrapped.
             */
            Object nativeSpacePtr = getNativeSpacePointerNode.executeCached(objects[0]);
            if (nativeSpacePtr == PNone.NO_VALUE) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw PRaiseNode.raiseUncached(this, SystemError, ErrorMessages.ATTEMPTING_GETTER_NO_NATIVE_SPACE);
            }
            objects[0] = new PythonAbstractNativeObject(nativeSpacePtr);
            return objects;
        }

        @TruffleBoundary
        public static PBuiltinFunction createLegacyFunction(GraalHPyContext context, PythonLanguage lang, Object owner, TruffleString propertyName, Object target, Object closure) {
            PythonContext pythonContext = context.getContext();
            PythonObjectFactory factory = pythonContext.factory();
            RootCallTarget rootCallTarget = lang.createCachedCallTarget(l -> new HPyLegacyGetSetDescriptorGetterRoot(l, propertyName, PExternalFunctionWrapper.GETTER),
                            HPyLegacyGetSetDescriptorGetterRoot.class, propertyName);
            if (rootCallTarget == null) {
                throw CompilerDirectives.shouldNotReachHere("Calling non-native get descriptor functions is not support in HPy");
            }
            target = CExtContext.ensureExecutable(target, PExternalFunctionWrapper.GETTER);
            return factory.createBuiltinFunction(propertyName, owner, PythonUtils.EMPTY_OBJECT_ARRAY, ExternalFunctionNodes.createKwDefaults(target, closure), 0, rootCallTarget);
        }
    }

    /**
     * A simple and lightweight Python root node that invokes a native setter function. The native
     * call target and the native closure pointer are passed as Python closure.
     */
    static final class HPyGetSetDescriptorSetterRootNode extends HPyGetSetDescriptorRootNode {
        private static final Signature SIGNATURE = new Signature(-1, false, -1, false, tsArray("$self", "value"), KEYWORDS_HIDDEN_CALLABLE_AND_CLOSURE, true);

        private HPyGetSetDescriptorSetterRootNode(PythonLanguage language, TruffleString name) {
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
        public static PBuiltinFunction createFunction(GraalHPyContext hpyContext, Object enclosingType, TruffleString propertyName, Object target, Object closure) {
            PythonContext pythonContext = hpyContext.getContext();
            PythonLanguage lang = pythonContext.getLanguage();
            RootCallTarget callTarget = lang.createCachedCallTarget(l -> new HPyGetSetDescriptorSetterRootNode(l, propertyName), HPyGetSetDescriptorSetterRootNode.class, propertyName);
            PythonObjectFactory factory = pythonContext.factory();
            return factory.createBuiltinFunction(propertyName, enclosingType, PythonUtils.EMPTY_OBJECT_ARRAY, createKwDefaults(target, closure, hpyContext), 0, callTarget);
        }

    }

    static final class HPyLegacyGetSetDescriptorSetterRoot extends SetterRoot {

        @Child private HPyGetNativeSpacePointerNode getNativeSpacePointerNode;

        private HPyLegacyGetSetDescriptorSetterRoot(PythonLanguage language, TruffleString name, PExternalFunctionWrapper provider) {
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
             * We now need to pass the native space pointer in a way that the PythonToNativeNode
             * correctly exposes the bare pointer object. For this, we pack the pointer into a
             * PythonAbstractNativeObject which will just be unwrapped.
             */
            Object nativeSpacePtr = getNativeSpacePointerNode.executeCached(objects[0]);
            if (nativeSpacePtr == PNone.NO_VALUE) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw PRaiseNode.raiseUncached(this, SystemError, ErrorMessages.ATTEMPTING_SETTER_NO_NATIVE_SPACE);
            }
            objects[0] = new PythonAbstractNativeObject(nativeSpacePtr);
            return objects;
        }

        @TruffleBoundary
        public static PBuiltinFunction createLegacyFunction(GraalHPyContext context, PythonLanguage lang, Object owner, TruffleString propertyName, Object target, Object closure) {
            PythonContext pythonContext = context.getContext();
            PythonObjectFactory factory = pythonContext.factory();
            RootCallTarget rootCallTarget = lang.createCachedCallTarget(l -> new HPyLegacyGetSetDescriptorSetterRoot(l, propertyName, PExternalFunctionWrapper.SETTER),
                            HPyLegacyGetSetDescriptorSetterRoot.class, propertyName);
            if (rootCallTarget == null) {
                throw CompilerDirectives.shouldNotReachHere("Calling non-native get descriptor functions is not support in HPy");
            }
            target = CExtContext.ensureExecutable(target, PExternalFunctionWrapper.SETTER);
            return factory.createBuiltinFunction(propertyName, owner, PythonUtils.EMPTY_OBJECT_ARRAY, ExternalFunctionNodes.createKwDefaults(target, closure), 0, rootCallTarget);
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
        private static final Signature SIGNATURE = new Signature(-1, false, 1, false, tsArray("self", "flags"), KEYWORDS_HIDDEN_CALLABLE);

        @Child private ReadIndexedArgumentNode readArg1Node;
        @Child private FromCharPointerNode fromCharPointerNode;
        @Child private GraalHPyCAccess.AllocateNode allocateNode;
        @Child private GraalHPyCAccess.FreeNode freeNode;
        @Child private GraalHPyCAccess.ReadPointerNode readPointerNode;
        @Child private GraalHPyCAccess.ReadGenericNode readGenericNode;
        @Child private GraalHPyCAccess.ReadHPyNode readHPyNode;
        @Child private GraalHPyCAccess.IsNullNode isNullNode;

        @TruffleBoundary
        public HPyGetBufferRootNode(PythonLanguage language, TruffleString name) {
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
                bufferPtr = ensureAllocateNode(hpyContext).malloc(hpyContext, HPyContextSignatureType.HPy_buffer);
                Object[] cArguments = new Object[]{getSelf(frame), bufferPtr, getArg1(frame)};
                getInvokeNode().execute(frame, getTSName(), callable, hpyContext, cArguments);
                return createPyBuffer(hpyContext, bufferPtr);
            } finally {
                if (hpyContext != null && bufferPtr != null) {
                    ensureFreeNode(hpyContext).free(hpyContext, bufferPtr);
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
        private CExtPyBuffer createPyBuffer(GraalHPyContext ctx, Object bufferPtr) {
            if (readGenericNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                readGenericNode = insert(GraalHPyCAccess.ReadGenericNode.create(ctx));
            }
            if (readPointerNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                readPointerNode = insert(GraalHPyCAccess.ReadPointerNode.create(ctx));
            }
            if (readHPyNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                readHPyNode = insert(GraalHPyCAccess.ReadHPyNode.create(ctx));
            }
            if (isNullNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                isNullNode = insert(GraalHPyCAccess.IsNullNode.create(ctx));
            }
            if (fromCharPointerNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                fromCharPointerNode = insert(FromCharPointerNodeGen.create());
            }
            int len = readGenericNode.readInt(ctx, bufferPtr, GraalHPyCField.HPy_buffer__len);
            Object buf = readPointerNode.read(ctx, bufferPtr, GraalHPyCField.HPy_buffer__buf);
            /*
             * Since we are now the owner of the handle and no one else will ever use it, we need to
             * close it.
             */
            Object owner = readHPyNode.readAndClose(ctx, bufferPtr, GraalHPyCField.HPy_buffer__obj);

            int ndim = readGenericNode.readInt(ctx, bufferPtr, GraalHPyCField.HPy_buffer__ndim);
            int itemSize = readGenericNode.readInt(ctx, bufferPtr, GraalHPyCField.HPy_buffer__itemsize);
            boolean readonly = readGenericNode.readInt(ctx, bufferPtr, GraalHPyCField.HPy_buffer__readonly) != 0;
            TruffleString format = fromCharPointerNode.execute(readPointerNode.read(ctx, bufferPtr, GraalHPyCField.HPy_buffer__format));
            Object shapePtr = readPointerNode.read(ctx, bufferPtr, GraalHPyCField.HPy_buffer__shape);
            Object stridesPtr = readPointerNode.read(ctx, bufferPtr, GraalHPyCField.HPy_buffer__strides);
            Object suboffsetsPtr = readPointerNode.read(ctx, bufferPtr, GraalHPyCField.HPy_buffer__suboffsets);
            Object internal = readPointerNode.read(ctx, bufferPtr, GraalHPyCField.HPy_buffer__internal);
            int[] shape = null;
            int[] strides = null;
            int[] subOffsets = null;
            if (ndim > 0) {
                if (!isNullNode.execute(ctx, shapePtr)) {
                    shape = readLongAsIntArray(ctx, shapePtr, ndim);
                }
                if (!isNullNode.execute(ctx, stridesPtr)) {
                    strides = readLongAsIntArray(ctx, stridesPtr, ndim);
                }
                if (!isNullNode.execute(ctx, suboffsetsPtr)) {
                    subOffsets = readLongAsIntArray(ctx, suboffsetsPtr, ndim);
                }
            }
            return new CExtPyBuffer(buf, owner, len, itemSize, readonly, ndim, format, shape, strides, subOffsets, internal);
        }

        private int[] readLongAsIntArray(GraalHPyContext ctx, Object pointer, int elements) {
            GraalHPyCAccess.ReadGenericNode readI64Node = ensureReadGenericNode(ctx);
            int elemSize = ctx.getCTypeSize(HPyContextSignatureType.HPy_ssize_t);
            int[] result = new int[elements];
            for (int i = 0; i < result.length; i++) {
                result[i] = readI64Node.executeInt(ctx, pointer, (long) i * elemSize, HPyContextSignatureType.HPy_ssize_t);
            }
            return result;
        }

        private GraalHPyCAccess.AllocateNode ensureAllocateNode(GraalHPyContext ctx) {
            if (allocateNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                allocateNode = insert(GraalHPyCAccess.AllocateNode.create(ctx));
            }
            return allocateNode;
        }

        private GraalHPyCAccess.FreeNode ensureFreeNode(GraalHPyContext ctx) {
            if (freeNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                freeNode = insert(GraalHPyCAccess.FreeNode.create(ctx));
            }
            return freeNode;
        }

        private GraalHPyCAccess.ReadGenericNode ensureReadGenericNode(GraalHPyContext ctx) {
            if (readGenericNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                readGenericNode = insert(GraalHPyCAccess.ReadGenericNode.create(ctx));
            }
            return readGenericNode;
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
        private static final Signature SIGNATURE = new Signature(-1, false, 1, false, tsArray("self", "buffer"), KEYWORDS_HIDDEN_CALLABLE);

        @Child private ReadIndexedArgumentNode readArg1Node;
        @Child private GraalHPyCAccess.FreeNode freeNode;
        @Child private GraalHPyCAccess.ReadPointerNode readPointerNode;
        @Child private GraalHPyCAccess.ReadHPyNode readHPyNode;

        @TruffleBoundary
        public HPyReleaseBufferRootNode(PythonLanguage language, TruffleString name) {
            super(language, name, HPyCheckVoidResultNodeGen.create(), HPyReleaseBufferProcToSulongNodeGen.create());
        }

        @Override
        public Object execute(VirtualFrame frame) {
            getCalleeContext().enter(frame);
            try {
                Object callable = ensureReadCallableNode().execute(frame);
                GraalHPyContext hpyContext = readContext(frame);
                Object arg1 = getArg1(frame);
                if (!(arg1 instanceof CExtPyBuffer buffer)) {
                    throw CompilerDirectives.shouldNotReachHere("invalid argument");
                }
                GraalHPyBuffer hpyBuffer = new GraalHPyBuffer(hpyContext, buffer);
                try {
                    getInvokeNode().execute(frame, getTSName(), callable, hpyContext, new Object[]{getSelf(frame), hpyBuffer});
                } finally {
                    if (hpyBuffer.isPointer()) {
                        hpyBuffer.free(hpyContext, ensureFreeNode(hpyContext), ensureReadPointerNode(hpyContext), ensureReadHPyNode(hpyContext));
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

        private GraalHPyCAccess.FreeNode ensureFreeNode(GraalHPyContext ctx) {
            if (freeNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                freeNode = insert(GraalHPyCAccess.FreeNode.create(ctx));
            }
            return freeNode;
        }

        private GraalHPyCAccess.ReadPointerNode ensureReadPointerNode(GraalHPyContext ctx) {
            if (readPointerNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                readPointerNode = insert(GraalHPyCAccess.ReadPointerNode.create(ctx));
            }
            return readPointerNode;
        }

        private GraalHPyCAccess.ReadHPyNode ensureReadHPyNode(GraalHPyContext ctx) {
            if (readHPyNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                readHPyNode = insert(GraalHPyCAccess.ReadHPyNode.create(ctx));
            }
            return readHPyNode;
        }

        @Override
        public Signature getSignature() {
            return SIGNATURE;
        }
    }

    /**
     * Very similar to {@link HPyMethNoargsRoot} but converts the result to a boolean.
     */
    static final class HPyMethHashRoot extends HPyMethodDescriptorRootNode {
        private static final Signature SIGNATURE = new Signature(-1, false, -1, false, tsArray("self"), KEYWORDS_HIDDEN_CALLABLE);

        public HPyMethHashRoot(PythonLanguage language, TruffleString name) {
            super(language, name, HPyCheckPrimitiveResultNodeGen.create(), HPyAllAsHandleNodeGen.create());
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

    static final class HPyMethCallRoot extends HPyMethodDescriptorRootNode {
        private static final Signature SIGNATURE = new Signature(-1, true, 1, false, tsArray("self"), KEYWORDS_HIDDEN_CONTEXT, true);

        @Child private ReadVarArgsNode readVarargsNode;
        @Child private ReadVarKeywordsNode readKwargsNode;
        @Child private PythonObjectFactory factory;

        @TruffleBoundary
        public HPyMethCallRoot(PythonLanguage language, TruffleString name) {
            super(language, name, HPyKeywordsToSulongNodeGen.create());
        }

        @Override
        public Object execute(VirtualFrame frame) {
            GraalHPyContext hpyContext = readContext(frame);
            Object[] cArguments = prepareCArguments(frame, hpyContext);
            Object self = cArguments[0];
            Object callable;
            if (self instanceof PythonObject pythonObject) {
                callable = GraalHPyData.getHPyCallFunction(pythonObject);
            } else {
                callable = null;
            }
            if (callable == null) {
                throw PRaiseNode.raiseUncached(this, TypeError, ErrorMessages.HPY_OBJECT_DOES_NOT_SUPPORT_CALL, self);
            }

            getCalleeContext().enter(frame);
            try {
                return getInvokeNode().execute(frame, getTSName(), callable, hpyContext, cArguments);
            } finally {
                getCalleeContext().exit(frame, this);
                closeCArguments(frame, hpyContext, cArguments);
            }
        }

        @Override
        protected Object[] prepareCArguments(VirtualFrame frame, GraalHPyContext hpyContext) {
            Object[] positionalArgs = getVarargs(frame);
            PKeyword[] keywords = getKwargs(frame);
            long nPositionalArgs = positionalArgs.length;

            Object[] args;
            Object kwnamesTuple;
            // this condition is implicitly profiled by 'getKwnamesTuple'
            if (keywords.length > 0) {
                args = PythonUtils.arrayCopyOf(positionalArgs, positionalArgs.length + keywords.length);
                TruffleString[] kwnames = new TruffleString[keywords.length];
                for (int i = 0; i < keywords.length; i++) {
                    args[positionalArgs.length + i] = keywords[i].getValue();
                    kwnames[i] = keywords[i].getName();
                }
                kwnamesTuple = getKwnamesTuple(kwnames);
            } else {
                args = positionalArgs;
                kwnamesTuple = GraalHPyHandle.NULL_HANDLE_DELEGATE;
            }
            return new Object[]{getSelf(frame), createArgumentsArray(hpyContext, args), nPositionalArgs, kwnamesTuple};
        }

        @Override
        protected void closeCArguments(VirtualFrame frame, GraalHPyContext hpyContext, Object[] cArguments) {
            hpyContext.freeArgumentsArray(cArguments[1]);
        }

        private Object createArgumentsArray(GraalHPyContext hpyContext, Object[] args) {
            return hpyContext.createArgumentsArray(args);
        }

        private Object[] getVarargs(VirtualFrame frame) {
            if (readVarargsNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                readVarargsNode = insert(ReadVarArgsNode.create(true));
            }
            return readVarargsNode.executeObjectArray(frame);
        }

        private PKeyword[] getKwargs(VirtualFrame frame) {
            if (PArguments.getKeywordArguments(frame).length == 0) {
                return PKeyword.EMPTY_KEYWORDS;
            }
            if (readKwargsNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                readKwargsNode = insert(ReadVarKeywordsNode.create());
            }
            return (PKeyword[]) readKwargsNode.execute(frame);
        }

        private PTuple getKwnamesTuple(TruffleString[] kwnames) {
            if (factory == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                factory = insert(PythonObjectFactory.create());
            }
            return factory.createTuple(kwnames);
        }

        @Override
        public Signature getSignature() {
            return SIGNATURE;
        }
    }
}
