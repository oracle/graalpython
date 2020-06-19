/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNodes.HPyAllAsHandleNode;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNodes.HPyAsPythonObjectNode;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNodes.HPyConvertArgsToSulongNode;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNodesFactory.HPyAllAsHandleNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNodesFactory.HPyKeywordsToSulongNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNodesFactory.HPyVarargsToSulongNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.HPyExternalFunctionNodesFactory.HPyExternalFunctionInvokeNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.HPyExternalFunctionNodesFactory.HPyExternalFunctionNodeGen;
import com.oracle.graal.python.builtins.objects.function.PArguments;
import com.oracle.graal.python.builtins.objects.function.Signature;
import com.oracle.graal.python.nodes.IndirectCallNode;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.PRootNode;
import com.oracle.graal.python.nodes.argument.ReadIndexedArgumentNode;
import com.oracle.graal.python.nodes.argument.ReadVarArgsNode;
import com.oracle.graal.python.nodes.argument.ReadVarKeywordsNode;
import com.oracle.graal.python.runtime.ExecutionContext.CalleeContext;
import com.oracle.graal.python.runtime.ExecutionContext.ForeignCallContext;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.CachedContext;
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

    abstract static class HPyExternalFunctionNode extends PRootNode implements IndirectCallNode {

        private final Signature signature;
        private final Object callable;
        private final String name;

        // TODO(fa): this flag is just a temporary solution; remove it and do proper argument
        // conversion in the calling root node
        private final boolean doConversion;

        @CompilationFinal private Assumption nativeCodeDoesntNeedExceptionState = Truffle.getRuntime().createAssumption();
        @CompilationFinal private Assumption nativeCodeDoesntNeedMyFrame = Truffle.getRuntime().createAssumption();

        HPyExternalFunctionNode(PythonLanguage lang, String name, Object callable, Signature signature, boolean doConversion) {
            super(lang);
            this.signature = signature;
            this.callable = callable;
            this.name = name;
            this.doConversion = doConversion;
        }

        public Object getCallable() {
            return callable;
        }

        @Specialization
        Object doIt(VirtualFrame frame,
                        @Cached HPyAllAsHandleNode allAsHandleNode,
                        @CachedLibrary("getCallable()") InteropLibrary lib,
                        @Cached CalleeContext calleeContext,
                        @Cached("createCountingProfile()") ConditionProfile customLocalsProfile,
                        @CachedContext(PythonLanguage.class) PythonContext ctx,
                        @Cached HPyAsPythonObjectNode asPythonObjectNode,
                        @Cached PRaiseNode raiseNode) {
            CalleeContext.enter(frame, customLocalsProfile);

            Object[] frameArgs = PArguments.getVariableArguments(frame);
            Object[] arguments = new Object[frameArgs.length + 1];
            GraalHPyContext hPyContext = ctx.getHPyContext();
            if (doConversion) {
                allAsHandleNode.executeInto(hPyContext, frameArgs, 0, arguments, 1);
            } else {
                System.arraycopy(frameArgs, 0, arguments, 1, frameArgs.length);
            }

            // first arg is always the HPyContext
            arguments[0] = hPyContext;

            // If any code requested the caught exception (i.e. used 'sys.exc_info()'), we store
            // it to the context since we cannot propagate it through the native frames.
            Object state = ForeignCallContext.enter(frame, ctx, this);

            try {
                return asPythonObjectNode.execute(hPyContext, lib.execute(callable, arguments));
            } catch (UnsupportedTypeException | UnsupportedMessageException e) {
                throw raiseNode.raise(PythonBuiltinClassType.TypeError, "Calling native function %s failed: %m", name, e);
            } catch (ArityException e) {
                throw raiseNode.raise(PythonBuiltinClassType.TypeError, "Calling native function %s expected %d arguments but got %d.", name, e.getExpectedArity(), e.getActualArity());
            } finally {
                // special case after calling a C function: transfer caught exception back to frame
                // to simulate the global state semantics
                PArguments.setException(frame, ctx.getCaughtException());
                ForeignCallContext.exit(frame, ctx, state);
                calleeContext.exit(frame, this);
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
            HPyExternalFunctionNode node = (HPyExternalFunctionNode) super.copy();
            node.nativeCodeDoesntNeedMyFrame = Truffle.getRuntime().createAssumption();
            node.nativeCodeDoesntNeedExceptionState = Truffle.getRuntime().createAssumption();
            return node;
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

        public static HPyExternalFunctionNode create(PythonLanguage lang, String name, Object callable, Signature signature, boolean doConversion) {
            return HPyExternalFunctionNodeGen.create(lang, name, callable, signature, doConversion);
        }
    }

    public static final class HPyMethDirectRoot extends PRootNode {
        private static final Signature SIGNATURE = Signature.createVarArgsAndKwArgsOnly();

        @Child private HPyExternalFunctionInvokeNode invokeNode;
        @Child private CalleeContext calleeContext = CalleeContext.create();

        private final String name;
        private final Object callable;

        @CompilationFinal private ConditionProfile customLocalsProfile;

        private HPyMethDirectRoot(PythonLanguage lang, String name, Object callable) {
            super(lang);
            this.name = name;
            this.callable = callable;
        }

        @Override
        public Object execute(VirtualFrame frame) {
            CalleeContext.enter(frame, ensureCustomLocalsProfile());
            try {
                return ensureInvokeNode().execute(frame, name, callable, PArguments.getVariableArguments(frame));
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

        private ConditionProfile ensureCustomLocalsProfile() {
            if (customLocalsProfile == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                customLocalsProfile = ConditionProfile.createBinaryProfile();
            }
            return customLocalsProfile;
        }

        private HPyExternalFunctionInvokeNode ensureInvokeNode() {
            if (invokeNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
// invokeNode = insert(HPyExternalFunctionInvokeNode.create());
            }
            return invokeNode;
        }

        @TruffleBoundary
        public static HPyMethDirectRoot create(PythonLanguage lang, String name, Object callable) {
            return new HPyMethDirectRoot(lang, name, callable);
        }
    }

    abstract static class HPyExternalFunctionInvokeNode extends Node implements IndirectCallNode {

        @Child private HPyConvertArgsToSulongNode toSulongNode;

        @CompilationFinal private Assumption nativeCodeDoesntNeedExceptionState = Truffle.getRuntime().createAssumption();
        @CompilationFinal private Assumption nativeCodeDoesntNeedMyFrame = Truffle.getRuntime().createAssumption();

        HPyExternalFunctionInvokeNode() {
            this.toSulongNode = HPyAllAsHandleNodeGen.create();
        }

        HPyExternalFunctionInvokeNode(HPyConvertArgsToSulongNode convertArgsNode) {
            this.toSulongNode = convertArgsNode != null ? convertArgsNode : HPyAllAsHandleNodeGen.create();
        }

        public abstract Object execute(VirtualFrame frame, String name, Object callable, Object[] frameArgs);

        @Specialization(limit = "1")
        Object doIt(VirtualFrame frame, String name, Object callable, Object[] frameArgs,
                        @CachedLibrary("callable") InteropLibrary lib,
                        @CachedContext(PythonLanguage.class) PythonContext ctx,
                        @Cached HPyAsPythonObjectNode asPythonObjectNode,
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
                return asPythonObjectNode.execute(hPyContext, lib.execute(callable, arguments));
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

    public static class HPyMethVarargsRoot extends PRootNode {
        private static final Signature SIGNATURE = new Signature(false, 1, false, new String[]{"self"}, new String[0]);
        @Child private ReadVarArgsNode readVarargsNode;

        @Child private CalleeContext calleeContext;
        @Child private HPyExternalFunctionInvokeNode invokeNode;
        @Child ReadIndexedArgumentNode readSelfNode = ReadIndexedArgumentNode.create(0);

        private final ConditionProfile customLocalsProfile = ConditionProfile.createCountingProfile();

        private final String name;
        private final Object callable;

        @TruffleBoundary
        public HPyMethVarargsRoot(PythonLanguage language, String name, Object callable) {
            super(language);
            this.name = name;
            this.callable = callable;
            this.calleeContext = CalleeContext.create();
            this.invokeNode = HPyExternalFunctionInvokeNodeGen.create(HPyVarargsToSulongNodeGen.create());
            this.readVarargsNode = ReadVarArgsNode.create(1, true);
        }

        @Override
        public Object execute(VirtualFrame frame) {
            CalleeContext.enter(frame, customLocalsProfile);
            try {
                Object self = readSelfNode.execute(frame);
                Object[] args = readVarargsNode.executeObjectArray(frame);
                return invokeNode.execute(frame, name, callable, new Object[]{self, new HPyArrayWrapper(args), (long) args.length});
            } finally {
                calleeContext.exit(frame, this);
            }
        }

        @Override
        public Signature getSignature() {
            return SIGNATURE;
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

    public static class HPyMethKeywordsRoot extends PRootNode {
        private static final Signature SIGNATURE = new Signature(-1, true, 1, false, new String[]{"self"}, new String[0]);

        @Child private ReadVarArgsNode readVarargsNode;
        @Child private ReadVarKeywordsNode readKwargsNode;

        @Child private CalleeContext calleeContext;
        @Child private HPyExternalFunctionInvokeNode invokeNode;
        @Child ReadIndexedArgumentNode readSelfNode = ReadIndexedArgumentNode.create(0);

        private final ConditionProfile customLocalsProfile = ConditionProfile.createCountingProfile();

        private final String name;
        private final Object callable;

        @TruffleBoundary
        public HPyMethKeywordsRoot(PythonLanguage language, String name, Object callable) {
            super(language);
            this.name = name;
            this.callable = callable;
            this.calleeContext = CalleeContext.create();
            this.invokeNode = HPyExternalFunctionInvokeNodeGen.create(HPyKeywordsToSulongNodeGen.create());
            this.readVarargsNode = ReadVarArgsNode.create(1, true);
            this.readKwargsNode = ReadVarKeywordsNode.create(new String[0]);
        }

        @Override
        public Object execute(VirtualFrame frame) {
            CalleeContext.enter(frame, customLocalsProfile);
            try {
                Object self = readSelfNode.execute(frame);
                Object[] args = readVarargsNode.executeObjectArray(frame);
                Object kw = readKwargsNode.execute(frame);
                return invokeNode.execute(frame, name, callable, new Object[]{self, new HPyArrayWrapper(args), (long) args.length, kw});
            } finally {
                calleeContext.exit(frame, this);
            }
        }

        @Override
        public Signature getSignature() {
            return SIGNATURE;
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

}
