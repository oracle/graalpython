/*
 * Copyright (c) 2019, 2020, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNodes.HPyAsPythonObjectNode;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNodes.HPyConvertArgsToSulongNode;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNodes.HPyEnsureHandleNode;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNodesFactory.HPyAllAsHandleNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNodesFactory.HPyKeywordsToSulongNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNodesFactory.HPyVarargsToSulongNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.HPyArrayWrappers.HPyArrayWrapper;
import com.oracle.graal.python.builtins.objects.cext.hpy.HPyExternalFunctionNodesFactory.HPyCheckHandleResultNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.HPyExternalFunctionNodesFactory.HPyExternalFunctionInvokeNodeGen;
import com.oracle.graal.python.builtins.objects.exception.PBaseException;
import com.oracle.graal.python.builtins.objects.function.PArguments;
import com.oracle.graal.python.builtins.objects.function.PBuiltinFunction;
import com.oracle.graal.python.builtins.objects.function.Signature;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.IndirectCallNode;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.PRootNode;
import com.oracle.graal.python.nodes.argument.ReadIndexedArgumentNode;
import com.oracle.graal.python.nodes.argument.ReadVarArgsNode;
import com.oracle.graal.python.nodes.argument.ReadVarKeywordsNode;
import com.oracle.graal.python.runtime.ExecutionContext.CalleeContext;
import com.oracle.graal.python.runtime.ExecutionContext.ForeignCallContext;
import com.oracle.graal.python.runtime.PythonContext;
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
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.CachedContext;
import com.oracle.truffle.api.dsl.CachedLanguage;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.NodeCost;
import com.oracle.truffle.api.profiles.ConditionProfile;

public abstract class HPyExternalFunctionNodes {

    /**
     * Creates a root node that accepts the specified signatures, does appropriate argument and
     * result conversion and calls the provided callable.
     * 
     * @param language The Python language object.
     * @param signature The signature ID as defined in {@link GraalHPyDef}.
     * @param name The name of the method.
     * @param callable The native function pointer.
     * @return A {@link PRootNode} that accepts the given signature.
     */
    @TruffleBoundary
    static PRootNode createHPyWrapperRootNode(PythonLanguage language, int signature, String name, Object callable) {
        switch (signature) {
            case GraalHPyDef.HPyFunc_NOARGS:
                return new HPyMethNoargsRoot(language, name, callable);
            case GraalHPyDef.HPyFunc_O:
                return new HPyMethORoot(language, name, callable);
            case GraalHPyDef.HPyFunc_UNARYFUNC:
            case GraalHPyDef.HPyFunc_REPRFUNC:
                return new HPyMethUnaryRoot(language, name, callable);
            case GraalHPyDef.HPyFunc_KEYWORDS:
                return new HPyMethKeywordsRoot(language, name, callable);
            case GraalHPyDef.HPyFunc_VARARGS:
                return new HPyMethVarargsRoot(language, name, callable);
        }
        // TODO(fa): support remaining signatures
        if (signature <= GraalHPyDef.HPyFunc_SETTER) {
            throw new IllegalStateException("unsupported HPy method signature: " + signature);
        }
        throw new IllegalStateException("illegal HPy method signature");
    }

    /**
     * Helper function to create a built-in function.
     */
    @TruffleBoundary
    static PBuiltinFunction createWrapperFunction(PythonObjectFactory factory, String name, Object enclosingType, PRootNode rootNode) {
        return factory.createBuiltinFunction(name, enclosingType, 0, PythonUtils.getOrCreateCallTarget(rootNode));
    }

    /**
     * Invokes an HPy C function. It takes care of argument and result conversion and always passes
     * the HPy context as a first parameter.
     */
    abstract static class HPyExternalFunctionInvokeNode extends Node implements IndirectCallNode {

        @Child private HPyConvertArgsToSulongNode toSulongNode;
        @Child private HPyCheckFunctionResultNode checkFunctionResultNode;

        @CompilationFinal private Assumption nativeCodeDoesntNeedExceptionState = Truffle.getRuntime().createAssumption();
        @CompilationFinal private Assumption nativeCodeDoesntNeedMyFrame = Truffle.getRuntime().createAssumption();

        HPyExternalFunctionInvokeNode() {
            this.toSulongNode = HPyAllAsHandleNodeGen.create();
            this.checkFunctionResultNode = HPyCheckHandleResultNodeGen.create();
        }

        HPyExternalFunctionInvokeNode(HPyConvertArgsToSulongNode convertArgsNode) {
            CompilerAsserts.neverPartOfCompilation();
            this.toSulongNode = convertArgsNode != null ? convertArgsNode : HPyAllAsHandleNodeGen.create();
            this.checkFunctionResultNode = HPyCheckHandleResultNodeGen.create();
        }

        HPyExternalFunctionInvokeNode(HPyCheckFunctionResultNode checkFunctionResultNode, HPyConvertArgsToSulongNode convertArgsNode) {
            CompilerAsserts.neverPartOfCompilation();
            this.toSulongNode = convertArgsNode != null ? convertArgsNode : HPyAllAsHandleNodeGen.create();
            this.checkFunctionResultNode = checkFunctionResultNode != null ? checkFunctionResultNode : HPyCheckHandleResultNodeGen.create();
        }

        public abstract Object execute(VirtualFrame frame, String name, Object callable, Object[] frameArgs);

        @Specialization(limit = "1")
        Object doIt(VirtualFrame frame, String name, Object callable, Object[] frameArgs,
                        @CachedLibrary("callable") InteropLibrary lib,
                        @CachedContext(PythonLanguage.class) PythonContext ctx,
                        @Cached PRaiseNode raiseNode) {
            Object[] arguments = new Object[frameArgs.length + 1];
            GraalHPyContext hPyContext = ctx.getHPyContext();
            toSulongNode.executeInto(hPyContext, frameArgs, 0, arguments, 1);

            // first arg is always the HPyContext
            arguments[0] = hPyContext;

            // If any code requested the caught exception (i.e. used 'sys.exc_info()'), we store
            // it to the context since we cannot propagate it through the native frames.
            Object state = ForeignCallContext.enter(frame, ctx, this);

            try {
                return checkFunctionResultNode.execute(hPyContext, name, lib.execute(callable, arguments));
            } catch (UnsupportedTypeException | UnsupportedMessageException e) {
                throw raiseNode.raise(PythonBuiltinClassType.TypeError, "Calling native function %s failed: %m", name, e);
            } catch (ArityException e) {
                throw raiseNode.raise(PythonBuiltinClassType.TypeError, "Calling native function %s expected %d arguments but got %d.", name, e.getExpectedArity(), e.getActualArity());
            } finally {
                // special case after calling a C function: transfer caught exception back to frame
                // to simulate the global state semantics
                PArguments.setException(frame, ctx.getCaughtException());
                ForeignCallContext.exit(frame, ctx, state);
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

        @CompilationFinal private ConditionProfile customLocalsProfile;

        private final String name;
        private final Object callable;

        @TruffleBoundary
        public HPyMethodDescriptorRootNode(PythonLanguage language, String name, Object callable, HPyConvertArgsToSulongNode convertArgsToSulongNode) {
            super(language);
            assert InteropLibrary.getUncached(callable).isExecutable(callable) : "object is not callable";
            this.name = name;
            this.callable = callable;
            this.invokeNode = HPyExternalFunctionInvokeNodeGen.create(convertArgsToSulongNode);
        }

        @Override
        public Object execute(VirtualFrame frame) {
            CalleeContext.enter(frame, getCustomLocalsProfile());
            try {
                return invokeNode.execute(frame, name, callable, prepareCArguments(frame));
            } finally {
                getCalleeContext().exit(frame, this);
            }
        }

        protected abstract Object[] prepareCArguments(VirtualFrame frame);

        protected final Object getSelf(VirtualFrame frame) {
            if (readSelfNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                readSelfNode = insert(ReadIndexedArgumentNode.create(0));
            }
            return readSelfNode.execute(frame);
        }

        private ConditionProfile getCustomLocalsProfile() {
            if (customLocalsProfile == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                customLocalsProfile = ConditionProfile.createCountingProfile();
            }
            return customLocalsProfile;
        }

        private CalleeContext getCalleeContext() {
            if (calleeContext == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                calleeContext = insert(CalleeContext.create());
            }
            return calleeContext;
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
        private static final Signature SIGNATURE = new Signature(-1, false, -1, false, new String[]{"self"}, new String[0], true);

        public HPyMethNoargsRoot(PythonLanguage language, String name, Object callable) {
            super(language, name, callable, HPyAllAsHandleNodeGen.create());
        }

        @Override
        protected Object[] prepareCArguments(VirtualFrame frame) {
            return new Object[]{getSelf(frame), PNone.NONE};
        }

        @Override
        public Signature getSignature() {
            return SIGNATURE;
        }
    }

    static final class HPyMethORoot extends HPyMethodDescriptorRootNode {
        private static final Signature SIGNATURE = new Signature(-1, false, -1, false, new String[]{"self", "arg"}, new String[0], true);

        @Child private ReadIndexedArgumentNode readArgNode;

        public HPyMethORoot(PythonLanguage language, String name, Object callable) {
            super(language, name, callable, HPyAllAsHandleNodeGen.create());
        }

        @Override
        protected Object[] prepareCArguments(VirtualFrame frame) {
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
        private static final Signature SIGNATURE = new Signature(-1, false, 1, false, new String[]{"self"}, new String[0], true);

        @Child private ReadVarArgsNode readVarargsNode;

        @TruffleBoundary
        public HPyMethVarargsRoot(PythonLanguage language, String name, Object callable) {
            super(language, name, callable, HPyVarargsToSulongNodeGen.create());
        }

        @Override
        protected Object[] prepareCArguments(VirtualFrame frame) {
            Object[] args = getVarargs(frame);
            return new Object[]{getSelf(frame), new HPyArrayWrapper(args), (long) args.length};
        }

        private Object[] getVarargs(VirtualFrame frame) {
            if (readVarargsNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                readVarargsNode = insert(ReadVarArgsNode.create(1, true));
            }
            return readVarargsNode.executeObjectArray(frame);
        }

        @Override
        public Signature getSignature() {
            return SIGNATURE;
        }
    }

    static final class HPyMethKeywordsRoot extends HPyMethodDescriptorRootNode {
        private static final Signature SIGNATURE = new Signature(-1, true, 1, false, new String[]{"self"}, new String[0], true);

        @Child private ReadVarArgsNode readVarargsNode;
        @Child private ReadVarKeywordsNode readKwargsNode;

        @TruffleBoundary
        public HPyMethKeywordsRoot(PythonLanguage language, String name, Object callable) {
            super(language, name, callable, HPyKeywordsToSulongNodeGen.create());
        }

        @Override
        protected Object[] prepareCArguments(VirtualFrame frame) {
            Object[] args = getVarargs(frame);
            return new Object[]{getSelf(frame), new HPyArrayWrapper(args), (long) args.length, getKwargs(frame)};
        }

        private Object[] getVarargs(VirtualFrame frame) {
            if (readVarargsNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                readVarargsNode = insert(ReadVarArgsNode.create(1, true));
            }
            return readVarargsNode.executeObjectArray(frame);
        }

        private Object getKwargs(VirtualFrame frame) {
            if (readKwargsNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                readKwargsNode = insert(ReadVarKeywordsNode.createForUserFunction(new String[0]));
            }
            return readKwargsNode.execute(frame);
        }

        @Override
        public Signature getSignature() {
            return SIGNATURE;
        }
    }

    static final class HPyMethUnaryRoot extends HPyMethodDescriptorRootNode {
        private static final Signature SIGNATURE = new Signature(-1, false, -1, false, new String[]{"self"}, new String[0], true);

        public HPyMethUnaryRoot(PythonLanguage language, String name, Object callable) {
            super(language, name, callable, HPyAllAsHandleNodeGen.create());
        }

        @Override
        protected Object[] prepareCArguments(VirtualFrame frame) {
            return new Object[]{getSelf(frame)};
        }

        @Override
        public Signature getSignature() {
            return SIGNATURE;
        }
    }

    abstract static class HPyCheckFunctionResultNode extends PNodeWithContext {

        public abstract Object execute(GraalHPyContext nativeContext, String name, Object value);

        protected final void checkFunctionResult(String name, boolean indicatesError, GraalHPyContext context, PRaiseNode raise, PythonObjectFactory factory, PythonLanguage language) {
            PException currentException = context.getCurrentException();
            boolean errOccurred = currentException != null;
            if (indicatesError) {
                // consume exception
                context.setCurrentException(null);
                if (!errOccurred) {
                    throw raise.raise(PythonErrorType.SystemError, ErrorMessages.RETURNED_NULL_WO_SETTING_ERROR, name);
                } else {
                    throw currentException.getExceptionForReraise();
                }
            } else if (errOccurred) {
                // consume exception
                context.setCurrentException(null);
                PBaseException sysExc = factory.createBaseException(PythonErrorType.SystemError, ErrorMessages.RETURNED_RESULT_WITH_ERROR_SET, new Object[]{name});
                sysExc.setCause(currentException.getEscapedException());
                throw PException.fromObject(sysExc, this, PythonOptions.isPExceptionWithJavaStacktrace(language));
            }
        }
    }

    // roughly equivalent to _Py_CheckFunctionResult in Objects/call.c
    @ImportStatic(PGuards.class)
    abstract static class HPyCheckHandleResultNode extends HPyCheckFunctionResultNode {

        @Specialization(guards = "value == 0")
        Object doIntegerNull(GraalHPyContext nativeContext, String name, @SuppressWarnings("unused") int value,
                        @Shared("language") @CachedLanguage PythonLanguage language,
                        @Shared("fact") @Cached PythonObjectFactory factory,
                        @Shared("raiseNode") @Cached PRaiseNode raiseNode) {
            checkFunctionResult(name, true, nativeContext, raiseNode, factory, language);
            throw CompilerDirectives.shouldNotReachHere("an exception should have been thrown");
        }

        @Specialization(replaces = "doIntegerNull")
        Object doInteger(GraalHPyContext nativeContext, String name, int value,
                        @Exclusive @Cached HPyAsPythonObjectNode asPythonObjectNode,
                        @Shared("language") @CachedLanguage PythonLanguage language,
                        @Shared("fact") @Cached PythonObjectFactory factory,
                        @Shared("raiseNode") @Cached PRaiseNode raiseNode) {
            checkFunctionResult(name, value == 0, nativeContext, raiseNode, factory, language);
            return asPythonObjectNode.execute(nativeContext, value);
        }

        @Specialization(guards = "value == 0")
        Object doLongNull(GraalHPyContext nativeContext, String name, @SuppressWarnings("unused") long value,
                        @Shared("language") @CachedLanguage PythonLanguage language,
                        @Shared("fact") @Cached PythonObjectFactory factory,
                        @Shared("raiseNode") @Cached PRaiseNode raiseNode) {
            checkFunctionResult(name, true, nativeContext, raiseNode, factory, language);
            throw CompilerDirectives.shouldNotReachHere("an exception should have been thrown");
        }

        @Specialization(replaces = "doLongNull")
        Object doLong(GraalHPyContext nativeContext, String name, long value,
                        @Exclusive @Cached HPyAsPythonObjectNode asPythonObjectNode,
                        @Shared("language") @CachedLanguage PythonLanguage language,
                        @Shared("fact") @Cached PythonObjectFactory factory,
                        @Shared("raiseNode") @Cached PRaiseNode raiseNode) {
            checkFunctionResult(name, value == 0, nativeContext, raiseNode, factory, language);
            return asPythonObjectNode.execute(nativeContext, value);
        }

        @Specialization(guards = "isNullHandle(nativeContext, handle)")
        Object doNullHandle(GraalHPyContext nativeContext, String name, GraalHPyHandle handle,
                        @Shared("language") @CachedLanguage PythonLanguage language,
                        @Shared("fact") @Cached PythonObjectFactory factory,
                        @Shared("raiseNode") @Cached PRaiseNode raiseNode) {
            checkFunctionResult(name, true, nativeContext, raiseNode, factory, language);
            return handle;
        }

        @Specialization(guards = "!isNullHandle(nativeContext, handle)", replaces = "doNullHandle")
        Object doNonNullHandle(GraalHPyContext nativeContext, String name, @SuppressWarnings("unused") GraalHPyHandle handle,
                        @Shared("language") @CachedLanguage PythonLanguage language,
                        @Shared("fact") @Cached PythonObjectFactory factory,
                        @Shared("raiseNode") @Cached PRaiseNode raiseNode) {
            checkFunctionResult(name, false, nativeContext, raiseNode, factory, language);
            throw CompilerDirectives.shouldNotReachHere("an exception should have been thrown");
        }

        @Specialization(replaces = {"doIntegerNull", "doNonNullHandle"})
        Object doHandle(GraalHPyContext nativeContext, String name, GraalHPyHandle handle,
                        @Exclusive @Cached HPyAsPythonObjectNode asPythonObjectNode,
                        @Shared("language") @CachedLanguage PythonLanguage language,
                        @Shared("fact") @Cached PythonObjectFactory factory,
                        @Shared("raiseNode") @Cached PRaiseNode raiseNode) {
            checkFunctionResult(name, isNullHandle(nativeContext, handle), nativeContext, raiseNode, factory, language);
            return asPythonObjectNode.execute(nativeContext, handle);
        }

        @Specialization(replaces = {"doIntegerNull", "doInteger", "doLongNull", "doLong", "doNullHandle", "doNonNullHandle", "doHandle"})
        Object doGeneric(GraalHPyContext nativeContext, String name, Object value,
                        @Cached HPyEnsureHandleNode ensureHandleNode,
                        @Cached HPyAsPythonObjectNode asPythonObjectNode,
                        @Shared("language") @CachedLanguage PythonLanguage language,
                        @Shared("fact") @Cached PythonObjectFactory factory,
                        @Shared("raiseNode") @Cached PRaiseNode raiseNode) {
            GraalHPyHandle handle = ensureHandleNode.execute(nativeContext, value);
            checkFunctionResult(name, isNullHandle(nativeContext, handle), nativeContext, raiseNode, factory, language);
            return asPythonObjectNode.execute(nativeContext, handle);
        }

        protected static boolean isNullHandle(GraalHPyContext nativeContext, GraalHPyHandle handle) {
            return handle == nativeContext.getNullHandle();
        }
    }

    /**
     * Similar to {@link HPyCheckFunctionResultNode}, this node checks a primitive result of a
     * native function.
     */
    @ImportStatic(PGuards.class)
    abstract static class HPyCheckPrimitiveResultNode extends HPyCheckFunctionResultNode {
        public abstract int executeInt(GraalHPyContext nativeContext, String name, int value);

        public abstract long executeLong(GraalHPyContext nativeContext, String name, long value);

        @Specialization
        int doInteger(GraalHPyContext nativeContext, String name, int value,
                        @Shared("language") @CachedLanguage PythonLanguage language,
                        @Shared("fact") @Cached PythonObjectFactory factory,
                        @Shared("raiseNode") @Cached PRaiseNode raiseNode) {
            checkFunctionResult(name, value == -1, nativeContext, raiseNode, factory, language);
            return value;
        }

        @Specialization(replaces = "doInteger")
        long doLong(GraalHPyContext nativeContext, String name, long value,
                        @Shared("language") @CachedLanguage PythonLanguage language,
                        @Shared("fact") @Cached PythonObjectFactory factory,
                        @Shared("raiseNode") @Cached PRaiseNode raiseNode) {
            checkFunctionResult(name, value == -1, nativeContext, raiseNode, factory, language);
            return value;
        }

        @Specialization(guards = "!isIntOrLong(value)")
        Object doPointer(GraalHPyContext nativeContext, String name, Object value,
                        @Shared("language") @CachedLanguage PythonLanguage language,
                        @Shared("fact") @Cached PythonObjectFactory factory,
                        @CachedLibrary(limit = "3") InteropLibrary lib,
                        @Shared("raiseNode") @Cached PRaiseNode raiseNode) {
            checkFunctionResult(name, lib.isNull(value), nativeContext, raiseNode, factory, language);
            return value;
        }

        static boolean isIntOrLong(Object value) {
            return value instanceof Integer || value instanceof Long;
        }
    }

}
