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
package com.oracle.graal.python.builtins.modules;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.modules.ExternalFunctionNodesFactory.ExternalFunctionNodeGen;
import com.oracle.graal.python.builtins.modules.PythonCextBuiltins.CheckFunctionResultNode;
import com.oracle.graal.python.builtins.modules.PythonCextBuiltins.ConvertPIntToPrimitiveNode;
import com.oracle.graal.python.builtins.modules.PythonCextBuiltinsFactory.ConvertPIntToPrimitiveNodeGen;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.cext.CApiGuards;
import com.oracle.graal.python.builtins.objects.cext.CExtNodes;
import com.oracle.graal.python.builtins.objects.cext.CExtNodes.ConvertArgsToSulongNode;
import com.oracle.graal.python.builtins.objects.cext.CExtNodes.SubRefCntNode;
import com.oracle.graal.python.builtins.objects.function.PArguments;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.builtins.objects.function.Signature;
import com.oracle.graal.python.nodes.IndirectCallNode;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.PRootNode;
import com.oracle.graal.python.nodes.argument.ReadIndexedArgumentNode;
import com.oracle.graal.python.nodes.argument.ReadVarArgsNode;
import com.oracle.graal.python.nodes.argument.ReadVarKeywordsNode;
import com.oracle.graal.python.nodes.call.FunctionInvokeNode;
import com.oracle.graal.python.nodes.interop.PForeignToPTypeNode;
import com.oracle.graal.python.runtime.ExecutionContext.CalleeContext;
import com.oracle.graal.python.runtime.ExecutionContext.ForeignCallContext;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.CachedContext;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.UnexpectedResultException;
import com.oracle.truffle.api.profiles.ConditionProfile;
import com.oracle.truffle.api.profiles.ValueProfile;

public abstract class ExternalFunctionNodes {

    abstract static class ExternalFunctionNode extends PRootNode implements IndirectCallNode {
        private final Signature signature;
        private final Object callable;
        private final String name;

        @Child private CExtNodes.ConvertArgsToSulongNode toSulongNode;
        @Child private CheckFunctionResultNode checkResultNode = CheckFunctionResultNode.create();
        @Child private PForeignToPTypeNode fromForeign = PForeignToPTypeNode.create();
        @Child private InteropLibrary lib;
        @Child private CalleeContext calleeContext = CalleeContext.create();
        @CompilationFinal private Assumption nativeCodeDoesntNeedExceptionState = Truffle.getRuntime().createAssumption();
        @CompilationFinal private Assumption nativeCodeDoesntNeedMyFrame = Truffle.getRuntime().createAssumption();

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
            ExternalFunctionNode node = (ExternalFunctionNode) super.copy();
            node.nativeCodeDoesntNeedMyFrame = Truffle.getRuntime().createAssumption();
            node.nativeCodeDoesntNeedExceptionState = Truffle.getRuntime().createAssumption();
            return node;
        }

        ExternalFunctionNode(PythonLanguage lang, String name, Object callable, Signature signature, CExtNodes.ConvertArgsToSulongNode convertArgsNode) {
            super(lang);
            this.name = name;
            this.callable = callable;
            this.signature = signature;
            this.toSulongNode = convertArgsNode != null ? convertArgsNode : CExtNodes.AllToSulongNode.create();
            this.lib = InteropLibrary.getFactory().create(callable);
        }

        @Specialization
        Object doIt(VirtualFrame frame,
                        @Cached("createCountingProfile()") ConditionProfile customLocalsProfile,
                        @Cached CExtNodes.AsPythonObjectStealingNode asPythonObjectNode,
                        @Cached ReleaseNativeWrapperNode releaseNativeWrapperNode,
                        @CachedContext(PythonLanguage.class) PythonContext ctx,
                        @Cached PRaiseNode raiseNode) {
            CalleeContext.enter(frame, customLocalsProfile);

            Object[] frameArgs = PArguments.getVariableArguments(frame);
            Object[] arguments = new Object[frameArgs.length];
            toSulongNode.executeInto(frameArgs, 0, arguments, 0);

            // If any code requested the caught exception (i.e. used 'sys.exc_info()'), we store
            // it to the context since we cannot propagate it through the native frames.
            Object state = ForeignCallContext.enter(frame, ctx, this);

            try {
                return fromNative(asPythonObjectNode.execute(checkResultNode.execute(name, lib.execute(callable, arguments))));
            } catch (UnsupportedTypeException | UnsupportedMessageException e) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw raiseNode.raise(PythonBuiltinClassType.TypeError, "Calling native function %s failed: %m", name, e);
            } catch (ArityException e) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw raiseNode.raise(PythonBuiltinClassType.TypeError, "Calling native function %s expected %d arguments but got %d.", name, e.getExpectedArity(), e.getActualArity());
            } finally {
                // special case after calling a C function: transfer caught exception back to frame
                // to simulate the global state semantics
                PArguments.setException(frame, ctx.getCaughtException());
                ForeignCallContext.exit(frame, ctx, state);
                calleeContext.exit(frame, this);

                releaseNativeWrapperNode.execute(arguments);
            }
        }

        private Object fromNative(Object result) {
            return fromForeign.executeConvert(result);
        }

        public final Object getCallable() {
            return callable;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public String toString() {
            return "<external function root " + name + ">";
        }

        @Override
        public boolean isCloningAllowed() {
            return true;
        }

        @Override
        public Signature getSignature() {
            return signature;
        }

        @Override
        public boolean isPythonInternal() {
            // everything that is implemented in C is internal
            return true;
        }

        public static ExternalFunctionNode create(PythonLanguage lang, String name, Object callable, Signature signature) {
            return ExternalFunctionNodeGen.create(lang, name, callable, signature, null);
        }

        public static ExternalFunctionNode create(PythonLanguage lang, String name, Object callable, Signature signature, ConvertArgsToSulongNode convertArgsNode) {
            return ExternalFunctionNodeGen.create(lang, name, callable, signature, convertArgsNode);
        }
    }

    /**
     * Decrements the ref count by one of any
     * {@link com.oracle.graal.python.builtins.objects.cext.PythonNativeWrapper} object.
     * <p>
     * This node avoids memory leaks for arguments given to native.<br>
     * Problem description:<br>
     * {@link com.oracle.graal.python.builtins.objects.cext.PythonNativeWrapper} objects given to C
     * code may go to native, i.e., a handle will be allocated. In this case, no ref count
     * manipulation is done since the C code considers the reference to be borrowed and the Python
     * code just doesn't do it because we have a GC. This means that the handle will stay allocated
     * and we are leaking the wrapper object.
     * </p>
     */
    abstract static class ReleaseNativeWrapperNode extends Node {

        public abstract void execute(Object[] nativeArguments);

        @Specialization(guards = {"nativeArguments.length == cachedLength", "nativeArguments.length < 8"}, limit = "1")
        @ExplodeLoop
        static void doCachedLength(Object[] nativeArguments,
                        @Cached("nativeArguments.length") int cachedLength,
                        @Cached(value = "createClassProfiles(cachedLength)", dimensions = 1) ValueProfile[] classProfiles,
                        @Cached SubRefCntNode subRefCntNode) {

            for (int i = 0; i < cachedLength; i++) {
                doCheck(classProfiles[i].profile(nativeArguments[i]), subRefCntNode);
            }
        }

        @Specialization(replaces = "doCachedLength")
        static void doGeneric(Object[] nativeArguments,
                        @Cached("createClassProfile()") ValueProfile classProfile,
                        @Cached SubRefCntNode freeNode) {

            for (int i = 0; i < nativeArguments.length; i++) {
                doCheck(classProfile.profile(nativeArguments[i]), freeNode);
            }
        }

        private static void doCheck(Object argument, SubRefCntNode refCntNode) {
            if (CApiGuards.isNativeWrapper(argument)) {
                // in the cached case, refCntNode acts as a branch profile
                refCntNode.dec(argument);
            }
        }

        static ValueProfile[] createClassProfiles(int length) {
            ValueProfile[] classProfiles = new ValueProfile[length];
            for (int i = 0; i < classProfiles.length; i++) {
                classProfiles[i] = ValueProfile.createClassProfile();
            }
            return classProfiles;
        }
    }

    abstract static class MethodDescriptorRoot extends PRootNode {
        @Child private CalleeContext calleeContext = CalleeContext.create();
        @Child FunctionInvokeNode invokeNode;
        @Child ReadIndexedArgumentNode readSelfNode;

        private final ConditionProfile customLocalsProfile = ConditionProfile.createCountingProfile();

        @TruffleBoundary
        MethodDescriptorRoot(PythonLanguage language, RootCallTarget callTarget) {
            super(language);
            this.readSelfNode = ReadIndexedArgumentNode.create(0);
            assert callTarget.getRootNode() instanceof ExternalFunctionNode;
            this.invokeNode = FunctionInvokeNode.createBuiltinFunction(callTarget);
        }

        @Override
        public boolean isCloningAllowed() {
            return true;
        }

        @Override
        public String getName() {
            return invokeNode.getCurrentRootNode().getName();
        }

        @Override
        public String toString() {
            return "<METH root " + invokeNode.getCurrentRootNode().getName() + ">";
        }

        @Override
        public boolean isPythonInternal() {
            return true;
        }

        final void enterCalleeContext(VirtualFrame frame) {
            CalleeContext.enter(frame, customLocalsProfile);
        }

        final void exitCalleeContext(VirtualFrame frame) {
            calleeContext.exit(frame, this);
        }
    }

    static class MethKeywordsRoot extends MethodDescriptorRoot {
        private static final Signature SIGNATURE = new Signature(-1, true, 1, false, new String[]{"self"}, new String[0]);
        @Child private PythonObjectFactory factory;
        @Child private ReadVarArgsNode readVarargsNode;
        @Child private ReadVarKeywordsNode readKwargsNode;

        MethKeywordsRoot(PythonLanguage language, RootCallTarget callTarget) {
            super(language, callTarget);
            this.factory = PythonObjectFactory.create();
            this.readVarargsNode = ReadVarArgsNode.create(1, true);
            this.readKwargsNode = ReadVarKeywordsNode.create(new String[0]);
        }

        @Override
        public Object execute(VirtualFrame frame) {
            enterCalleeContext(frame);
            try {
                Object self = readSelfNode.execute(frame);
                Object[] args = readVarargsNode.executeObjectArray(frame);
                PKeyword[] kwargs = readKwargsNode.executePKeyword(frame);
                Object[] arguments = PArguments.create();
                PArguments.setVariableArguments(arguments, self, factory.createTuple(args), factory.createDict(kwargs));
                return invokeNode.execute(frame, arguments);
            } finally {
                exitCalleeContext(frame);
            }
        }

        @Override
        public Signature getSignature() {
            return SIGNATURE;
        }
    }

    static class MethVarargsRoot extends MethodDescriptorRoot {
        private static final Signature SIGNATURE = new Signature(-1, false, 1, false, new String[]{"self"}, new String[0]);
        @Child private PythonObjectFactory factory;
        @Child private ReadVarArgsNode readVarargsNode;

        MethVarargsRoot(PythonLanguage language, RootCallTarget callTarget) {
            super(language, callTarget);
            this.factory = PythonObjectFactory.create();
            this.readVarargsNode = ReadVarArgsNode.create(1, true);
        }

        @Override
        public Object execute(VirtualFrame frame) {
            enterCalleeContext(frame);
            try {
                Object self = readSelfNode.execute(frame);
                Object[] args = readVarargsNode.executeObjectArray(frame);
                Object[] arguments = PArguments.create();
                PArguments.setVariableArguments(arguments, self, factory.createTuple(args));
                return invokeNode.execute(frame, arguments);
            } finally {
                exitCalleeContext(frame);
            }
        }

        @Override
        public Signature getSignature() {
            return SIGNATURE;
        }
    }

    static class MethNoargsRoot extends MethodDescriptorRoot {
        private static final Signature SIGNATURE = new Signature(-1, false, -1, false, new String[]{"self"}, new String[0]);

        MethNoargsRoot(PythonLanguage language, RootCallTarget callTarget) {
            super(language, callTarget);
        }

        @Override
        public Object execute(VirtualFrame frame) {
            enterCalleeContext(frame);
            try {
                Object self = readSelfNode.execute(frame);
                Object[] arguments = PArguments.create();
                PArguments.setVariableArguments(arguments, self, PNone.NONE);
                return invokeNode.execute(frame, arguments);
            } finally {
                exitCalleeContext(frame);
            }
        }

        @Override
        public Signature getSignature() {
            return SIGNATURE;
        }
    }

    static class MethORoot extends MethodDescriptorRoot {
        private static final Signature SIGNATURE = new Signature(-1, false, -1, false, new String[]{"self", "arg"}, new String[0]);
        @Child private ReadIndexedArgumentNode readArgNode;

        MethORoot(PythonLanguage language, RootCallTarget callTarget) {
            super(language, callTarget);
            this.readArgNode = ReadIndexedArgumentNode.create(1);
        }

        @Override
        public Object execute(VirtualFrame frame) {
            enterCalleeContext(frame);
            try {
                Object self = readSelfNode.execute(frame);
                Object arg = readArgNode.execute(frame);
                Object[] arguments = PArguments.create();
                PArguments.setVariableArguments(arguments, self, arg);
                return invokeNode.execute(frame, arguments);
            } finally {
                exitCalleeContext(frame);
            }
        }

        @Override
        public Signature getSignature() {
            return SIGNATURE;
        }
    }

    static class MethFastcallWithKeywordsRoot extends MethodDescriptorRoot {
        private static final Signature SIGNATURE = new Signature(-1, true, 1, false, new String[]{"self"}, new String[0]);
        @Child private PythonObjectFactory factory;
        @Child private ReadVarArgsNode readVarargsNode;
        @Child private ReadVarKeywordsNode readKwargsNode;

        MethFastcallWithKeywordsRoot(PythonLanguage language, RootCallTarget fun) {
            super(language, fun);
            this.factory = PythonObjectFactory.create();
            this.readVarargsNode = ReadVarArgsNode.create(1, true);
            this.readKwargsNode = ReadVarKeywordsNode.create(new String[0]);
        }

        @Override
        public Object execute(VirtualFrame frame) {
            enterCalleeContext(frame);
            try {
                Object self = readSelfNode.execute(frame);
                Object[] args = readVarargsNode.executeObjectArray(frame);
                PKeyword[] kwargs = readKwargsNode.executePKeyword(frame);
                Object[] fastcallArgs = new Object[args.length + kwargs.length];
                Object[] fastcallKwnames = new Object[kwargs.length];
                System.arraycopy(args, 0, fastcallArgs, 0, args.length);
                for (int i = 0; i < kwargs.length; i++) {
                    fastcallKwnames[i] = kwargs[i].getName();
                    fastcallArgs[args.length + i] = kwargs[i].getValue();
                }
                Object[] arguments = PArguments.create();
                PArguments.setVariableArguments(arguments, self, factory.createTuple(fastcallArgs), args.length, factory.createTuple(fastcallKwnames));
                return invokeNode.execute(frame, arguments);
            } finally {
                exitCalleeContext(frame);
            }
        }

        @Override
        public Signature getSignature() {
            return SIGNATURE;
        }
    }

    static class MethFastcallRoot extends MethodDescriptorRoot {
        private static final Signature SIGNATURE = new Signature(-1, false, 1, false, new String[]{"self"}, new String[0]);
        @Child private PythonObjectFactory factory;
        @Child private ReadVarArgsNode readVarargsNode;

        MethFastcallRoot(PythonLanguage language, RootCallTarget callTarget) {
            super(language, callTarget);
            this.factory = PythonObjectFactory.create();
            this.readVarargsNode = ReadVarArgsNode.create(1, true);
        }

        @Override
        public Object execute(VirtualFrame frame) {
            enterCalleeContext(frame);
            try {
                Object self = readSelfNode.execute(frame);
                Object[] args = readVarargsNode.executeObjectArray(frame);
                Object[] arguments = PArguments.create();
                PArguments.setVariableArguments(arguments, self, factory.createTuple(args), args.length);
                return invokeNode.execute(frame, arguments);
            } finally {
                exitCalleeContext(frame);
            }
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
        private static final Signature SIGNATURE = new Signature(false, -1, false, new String[]{"self", "nitems"}, new String[0]);
        @Child private ReadIndexedArgumentNode readArgNode;
        @Child private ConvertPIntToPrimitiveNode asSsizeTNode;

        AllocFuncRootNode(PythonLanguage language, RootCallTarget callTarget) {
            super(language, callTarget);
            this.readArgNode = ReadIndexedArgumentNode.create(1);
            this.asSsizeTNode = ConvertPIntToPrimitiveNodeGen.create();
        }

        @Override
        public Object execute(VirtualFrame frame) {
            enterCalleeContext(frame);
            try {
                Object self = readSelfNode.execute(frame);
                Object arg = readArgNode.execute(frame);
                Object[] arguments = PArguments.create();
                PArguments.setVariableArguments(arguments, self, asSsizeTNode.executeLong(frame, arg, 1, Long.BYTES));
                return invokeNode.execute(frame, arguments);
            } catch (UnexpectedResultException e) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw new IllegalStateException("should not be reached");
            } finally {
                exitCalleeContext(frame);
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
    static class GetAttrFuncRootNode extends MethodDescriptorRoot {
        private static final Signature SIGNATURE = new Signature(false, -1, false, new String[]{"self", "key"}, new String[0]);
        @Child private ReadIndexedArgumentNode readArgNode;
        @Child private CExtNodes.AsCharPointerNode asCharPointerNode;

        GetAttrFuncRootNode(PythonLanguage language, RootCallTarget callTarget) {
            super(language, callTarget);
            this.readArgNode = ReadIndexedArgumentNode.create(1);
            this.asCharPointerNode = CExtNodes.AsCharPointerNode.create();
        }

        @Override
        public Object execute(VirtualFrame frame) {
            enterCalleeContext(frame);
            try {
                Object self = readSelfNode.execute(frame);
                Object arg = readArgNode.execute(frame);
                Object[] arguments = PArguments.create();
                // TODO we should use 'CStringWrapper' for 'arg' but it does currently not support
                // PString
                PArguments.setVariableArguments(arguments, self, asCharPointerNode.execute(arg));
                return invokeNode.execute(frame, arguments);
            } finally {
                exitCalleeContext(frame);
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
    static class SetAttrFuncRootNode extends MethodDescriptorRoot {
        private static final Signature SIGNATURE = new Signature(false, -1, false, new String[]{"self", "key", "value"}, new String[0]);
        @Child private ReadIndexedArgumentNode readArg1Node;
        @Child private ReadIndexedArgumentNode readArg2Node;
        @Child private CExtNodes.AsCharPointerNode asCharPointerNode;

        SetAttrFuncRootNode(PythonLanguage language, RootCallTarget callTarget) {
            super(language, callTarget);
            this.readArg1Node = ReadIndexedArgumentNode.create(1);
            this.readArg2Node = ReadIndexedArgumentNode.create(2);
            this.asCharPointerNode = CExtNodes.AsCharPointerNode.create();
        }

        @Override
        public Object execute(VirtualFrame frame) {
            enterCalleeContext(frame);
            try {
                Object self = readSelfNode.execute(frame);
                Object arg1 = readArg1Node.execute(frame);
                Object arg2 = readArg2Node.execute(frame);
                Object[] arguments = PArguments.create();
                // TODO we should use 'CStringWrapper' for 'arg1' but it does currently not support
                // PString
                PArguments.setVariableArguments(arguments, self, asCharPointerNode.execute(arg1), arg2);
                return invokeNode.execute(frame, arguments);
            } finally {
                exitCalleeContext(frame);
            }
        }

        @Override
        public Signature getSignature() {
            return SIGNATURE;
        }
    }

    /**
     * Wrapper root node for a rich compare function (C type {@code richcmpfunc}).
     */
    static class RichCmpFuncRootNode extends MethodDescriptorRoot {
        private static final Signature SIGNATURE = new Signature(false, -1, false, new String[]{"self", "other", "op"}, new String[0]);
        @Child private ReadIndexedArgumentNode readArg1Node;
        @Child private ReadIndexedArgumentNode readArg2Node;
        @Child private ConvertPIntToPrimitiveNode asSsizeTNode;

        RichCmpFuncRootNode(PythonLanguage language, RootCallTarget callTarget) {
            super(language, callTarget);
            this.readArg1Node = ReadIndexedArgumentNode.create(1);
            this.readArg2Node = ReadIndexedArgumentNode.create(2);
            this.asSsizeTNode = ConvertPIntToPrimitiveNodeGen.create();
        }

        @Override
        public Object execute(VirtualFrame frame) {
            enterCalleeContext(frame);
            try {
                Object self = readSelfNode.execute(frame);
                Object arg1 = readArg1Node.execute(frame);
                Object arg2 = readArg2Node.execute(frame);
                Object[] arguments = PArguments.create();
                PArguments.setVariableArguments(arguments, self, arg1, asSsizeTNode.executeInt(frame, arg2, 1, Integer.BYTES));
                return invokeNode.execute(frame, arguments);
            } catch (UnexpectedResultException e) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw new IllegalStateException("should not be reached");
            } finally {
                exitCalleeContext(frame);
            }
        }

        @Override
        public Signature getSignature() {
            return SIGNATURE;
        }
    }

    /**
     * Wrapper root node for C function type {@code ssizeobjargproc}.
     */
    static class SSizeObjArgProcRootNode extends MethodDescriptorRoot {
        private static final Signature SIGNATURE = new Signature(false, -1, false, new String[]{"self", "i", "value"}, new String[0]);
        @Child private ReadIndexedArgumentNode readArg1Node;
        @Child private ReadIndexedArgumentNode readArg2Node;
        @Child private ConvertPIntToPrimitiveNode asSsizeTNode;

        SSizeObjArgProcRootNode(PythonLanguage language, RootCallTarget callTarget) {
            super(language, callTarget);
            this.readArg1Node = ReadIndexedArgumentNode.create(1);
            this.readArg2Node = ReadIndexedArgumentNode.create(2);
            this.asSsizeTNode = ConvertPIntToPrimitiveNodeGen.create();
        }

        @Override
        public Object execute(VirtualFrame frame) {
            enterCalleeContext(frame);
            try {
                Object self = readSelfNode.execute(frame);
                Object arg1 = readArg1Node.execute(frame);
                Object arg2 = readArg2Node.execute(frame);
                Object[] arguments = PArguments.create();
                PArguments.setVariableArguments(arguments, self, asSsizeTNode.executeLong(frame, arg1, 1, Long.BYTES), arg2);
                return invokeNode.execute(frame, arguments);
            } catch (UnexpectedResultException e) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw new IllegalStateException("should not be reached");
            } finally {
                exitCalleeContext(frame);
            }
        }

        @Override
        public Signature getSignature() {
            return SIGNATURE;
        }
    }

    /**
     * Wrapper root node for reverse binary operations.
     */
    static class MethReverseRootNode extends MethodDescriptorRoot {
        private static final Signature SIGNATURE = new Signature(false, -1, false, new String[]{"self", "obj"}, new String[0]);
        @Child private ReadIndexedArgumentNode readArg0Node;
        @Child private ReadIndexedArgumentNode readArg1Node;

        MethReverseRootNode(PythonLanguage language, RootCallTarget callTarget) {
            super(language, callTarget);
            this.readArg0Node = ReadIndexedArgumentNode.create(0);
            this.readArg1Node = ReadIndexedArgumentNode.create(1);
        }

        @Override
        public Object execute(VirtualFrame frame) {
            enterCalleeContext(frame);
            try {
                Object arg0 = readArg0Node.execute(frame);
                Object arg1 = readArg1Node.execute(frame);
                Object[] arguments = PArguments.create();
                PArguments.setVariableArguments(arguments, arg1, arg0);
                return invokeNode.execute(frame, arguments);
            } finally {
                exitCalleeContext(frame);
            }
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
        private static final Signature SIGNATURE = new Signature(false, 0, false, new String[]{"args"}, new String[0]);
        @Child private ReadVarArgsNode readVarargsNode;

        private final ConditionProfile profile = ConditionProfile.createBinaryProfile();

        MethPowRootNode(PythonLanguage language, RootCallTarget callTarget) {
            super(language, callTarget);
            this.readVarargsNode = ReadVarArgsNode.create(1, true);
        }

        @Override
        public Object execute(VirtualFrame frame) {
            enterCalleeContext(frame);
            try {
                Object self = readSelfNode.execute(frame);
                Object[] varargs = readVarargsNode.executeObjectArray(frame);
                Object arg0 = varargs[0];
                Object arg1 = profile.profile(varargs.length > 1) ? varargs[1] : PNone.NONE;
                Object[] arguments = PArguments.create();
                PArguments.setVariableArguments(arguments, self, arg0, arg1);
                return invokeNode.execute(frame, arguments);
            } finally {
                exitCalleeContext(frame);
            }
        }

        void setArguments(Object[] arguments, Object arg0, Object arg1, Object arg2) {
            PArguments.setVariableArguments(arguments, arg0, arg1, arg2);
        }

        @Override
        public Signature getSignature() {
            return SIGNATURE;
        }
    }

    /**
     * Wrapper root node for native reverse power function (with an optional third argument).
     */
    static class MethRPowRootNode extends MethPowRootNode {

        MethRPowRootNode(PythonLanguage language, RootCallTarget callTarget) {
            super(language, callTarget);
        }

        @Override
        void setArguments(Object[] arguments, Object arg0, Object arg1, Object arg2) {
            PArguments.setVariableArguments(arguments, arg1, arg0, arg2);
        }
    }

    /**
     * Wrapper root node for native power function (with an optional third argument).
     */
    static class MethRichcmpOpRootNode extends MethodDescriptorRoot {
        private static final Signature SIGNATURE = new Signature(false, -1, false, new String[]{"self", "other"}, new String[0]);
        @Child private ReadIndexedArgumentNode readArgNode;

        private final int op;

        MethRichcmpOpRootNode(PythonLanguage language, RootCallTarget callTarget, int op) {
            super(language, callTarget);
            this.readArgNode = ReadIndexedArgumentNode.create(1);
            this.op = op;
        }

        @Override
        public Object execute(VirtualFrame frame) {
            enterCalleeContext(frame);
            try {
                Object self = readSelfNode.execute(frame);
                Object arg = readArgNode.execute(frame);
                Object[] arguments = PArguments.create();
                PArguments.setVariableArguments(arguments, self, arg, op);
                return invokeNode.execute(frame, arguments);
            } finally {
                exitCalleeContext(frame);
            }
        }

        @Override
        public Signature getSignature() {
            return SIGNATURE;
        }
    }
}
