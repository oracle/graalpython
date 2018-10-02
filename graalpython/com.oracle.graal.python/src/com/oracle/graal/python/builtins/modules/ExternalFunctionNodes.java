/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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
import com.oracle.graal.python.builtins.modules.TruffleCextBuiltins.TrufflePInt_AsPrimitive;
import com.oracle.graal.python.builtins.modules.TruffleCextBuiltinsFactory.TrufflePInt_AsPrimitiveFactory;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.cext.CExtNodes;
import com.oracle.graal.python.builtins.objects.cext.CExtNodes.ConvertArgsToSulongNode;
import com.oracle.graal.python.builtins.objects.cext.NativeWrappers.PySequenceArrayWrapper;
import com.oracle.graal.python.builtins.objects.function.PArguments;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.nodes.argument.ReadIndexedArgumentNode;
import com.oracle.graal.python.nodes.argument.ReadVarArgsNode;
import com.oracle.graal.python.nodes.argument.ReadVarKeywordsNode;
import com.oracle.graal.python.nodes.interop.PForeignToPTypeNode;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.PythonCore;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleLanguage.ContextReference;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.Message;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.profiles.ConditionProfile;

public abstract class ExternalFunctionNodes {

    static class ExternalFunctionNode extends RootNode {
        private final TruffleObject callable;
        private final String name;
        @CompilationFinal ContextReference<PythonContext> ctxt;
        @Child private Node executeNode;
        @Child private CExtNodes.ConvertArgsToSulongNode toSulongNode;
        @Child private CExtNodes.AsPythonObjectNode asPythonObjectNode = CExtNodes.AsPythonObjectNode.create();
        @Child private TruffleCextBuiltins.CheckFunctionResultNode checkResultNode = TruffleCextBuiltins.CheckFunctionResultNode.create();
        @Child private PForeignToPTypeNode fromForeign = PForeignToPTypeNode.create();

        private ExternalFunctionNode(PythonLanguage lang, String name, TruffleObject callable, CExtNodes.ConvertArgsToSulongNode convertArgsNode) {
            super(lang);
            this.name = name;
            this.callable = callable;
            this.executeNode = Message.EXECUTE.createNode();
            this.toSulongNode = convertArgsNode;
        }

        public TruffleObject getCallable() {
            return callable;
        }

        @Override
        public Object execute(VirtualFrame frame) {
            Object[] frameArgs = frame.getArguments();
            try {
                Object[] arguments = new Object[frameArgs.length - PArguments.USER_ARGUMENTS_OFFSET];
                toSulongNode.executeInto(frameArgs, PArguments.USER_ARGUMENTS_OFFSET, arguments, 0);
                // save current exception state
                PException exceptionState = getContext().getCurrentException();
                // clear current exception such that native code has clean environment
                getContext().setCurrentException(null);

                Object result = fromNative(asPythonObjectNode.execute(checkResultNode.execute(name, ForeignAccess.sendExecute(executeNode, callable, arguments))));

                // restore previous exception state
                getContext().setCurrentException(exceptionState);
                return result;
            } catch (UnsupportedTypeException | ArityException | UnsupportedMessageException e) {
                CompilerDirectives.transferToInterpreter();
                throw new RuntimeException(e.toString());
            }
        }

        private Object fromNative(Object result) {
            return fromForeign.executeConvert(result);
        }

        public final PythonCore getCore() {
            return getContext().getCore();
        }

        public final PythonContext getContext() {
            if (ctxt == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                ctxt = PythonLanguage.getContextRef();
            }
            return ctxt.get();
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

        public static ExternalFunctionNode create(PythonLanguage lang, String name, TruffleObject callable) {
            return new ExternalFunctionNode(lang, name, callable, CExtNodes.AllToSulongNode.create());
        }

        public static ExternalFunctionNode create(PythonLanguage lang, String name, TruffleObject callable, ConvertArgsToSulongNode convertArgsNode) {
            return new ExternalFunctionNode(lang, name, callable, convertArgsNode);
        }
    }

    abstract static class MethodDescriptorRoot extends RootNode {
        @Child protected DirectCallNode directCallNode;
        @Child protected ReadIndexedArgumentNode readSelfNode;
        protected final PythonObjectFactory factory;

        @TruffleBoundary
        protected MethodDescriptorRoot(PythonLanguage language, PythonObjectFactory factory, CallTarget callTarget) {
            super(language);
            this.factory = factory;
            this.readSelfNode = ReadIndexedArgumentNode.create(0);
            this.directCallNode = Truffle.getRuntime().createDirectCallNode(callTarget);
        }

        @Override
        public boolean isCloningAllowed() {
            return true;
        }

        @Override
        public String getName() {
            return directCallNode.getCurrentRootNode().getName();
        }

        @Override
        public String toString() {
            return "<METH root " + directCallNode.getCurrentRootNode().getName() + ">";
        }
    }

    static class MethKeywordsRoot extends MethodDescriptorRoot {
        @Child private ReadVarArgsNode readVarargsNode;
        @Child private ReadVarKeywordsNode readKwargsNode;

        protected MethKeywordsRoot(PythonLanguage language, PythonObjectFactory factory, CallTarget callTarget) {
            super(language, factory, callTarget);
            this.readVarargsNode = ReadVarArgsNode.create(1, true);
            this.readKwargsNode = ReadVarKeywordsNode.create(new String[0]);
        }

        @Override
        public Object execute(VirtualFrame frame) {
            Object self = readSelfNode.execute(frame);
            Object[] args = readVarargsNode.executeObjectArray(frame);
            PKeyword[] kwargs = readKwargsNode.executePKeyword(frame);
            Object[] arguments = PArguments.create(3);
            PArguments.setArgument(arguments, 0, self);
            PArguments.setArgument(arguments, 1, factory.createTuple(args));
            PArguments.setArgument(arguments, 2, factory.createDict(kwargs));
            return directCallNode.call(arguments);
        }
    }

    static class MethVarargsRoot extends MethodDescriptorRoot {
        @Child private ReadVarArgsNode readVarargsNode;

        protected MethVarargsRoot(PythonLanguage language, PythonObjectFactory factory, CallTarget callTarget) {
            super(language, factory, callTarget);
            this.readVarargsNode = ReadVarArgsNode.create(1, true);
        }

        @Override
        public Object execute(VirtualFrame frame) {
            Object self = readSelfNode.execute(frame);
            Object[] args = readVarargsNode.executeObjectArray(frame);
            Object[] arguments = PArguments.create(2);
            PArguments.setArgument(arguments, 0, self);
            PArguments.setArgument(arguments, 1, factory.createTuple(args));
            return directCallNode.call(arguments);
        }
    }

    static class MethNoargsRoot extends MethodDescriptorRoot {
        protected MethNoargsRoot(PythonLanguage language, PythonObjectFactory factory, CallTarget callTarget) {
            super(language, factory, callTarget);
        }

        @Override
        public Object execute(VirtualFrame frame) {
            Object self = readSelfNode.execute(frame);
            Object[] arguments = PArguments.create(2);
            PArguments.setArgument(arguments, 0, self);
            PArguments.setArgument(arguments, 1, PNone.NONE);
            return directCallNode.call(arguments);
        }
    }

    static class MethORoot extends MethodDescriptorRoot {
        @Child private ReadIndexedArgumentNode readArgNode;

        protected MethORoot(PythonLanguage language, PythonObjectFactory factory, CallTarget callTarget) {
            super(language, factory, callTarget);
            this.readArgNode = ReadIndexedArgumentNode.create(1);
        }

        @Override
        public Object execute(VirtualFrame frame) {
            Object self = readSelfNode.execute(frame);
            Object arg = readArgNode.execute(frame);
            Object[] arguments = PArguments.create(2);
            PArguments.setArgument(arguments, 0, self);
            PArguments.setArgument(arguments, 1, factory.createTuple(new Object[]{arg}));
            return directCallNode.call(arguments);
        }
    }

    static class MethFastcallRoot extends MethodDescriptorRoot {
        @Child private ReadVarArgsNode readVarargsNode;
        @Child private ReadVarKeywordsNode readKwargsNode;

        protected MethFastcallRoot(PythonLanguage language, PythonObjectFactory factory, CallTarget callTarget) {
            super(language, factory, callTarget);
            this.readVarargsNode = ReadVarArgsNode.create(1, true);
            this.readKwargsNode = ReadVarKeywordsNode.create(new String[0]);
        }

        @Override
        public Object execute(VirtualFrame frame) {
            Object self = readSelfNode.execute(frame);
            Object[] args = readVarargsNode.executeObjectArray(frame);
            PKeyword[] kwargs = readKwargsNode.executePKeyword(frame);
            Object[] arguments = PArguments.create(4);
            PArguments.setArgument(arguments, 0, self);
            // TODO avoid tuple allocation
            PArguments.setArgument(arguments, 1, new PySequenceArrayWrapper(factory.createTuple(args), Long.BYTES));
            PArguments.setArgument(arguments, 2, (long) args.length);
            PArguments.setArgument(arguments, 3, factory.createDict(kwargs));
            return directCallNode.call(arguments);
        }
    }

    /**
     * Wrapper root node for C function type {@code allocfunc} and {@code ssizeargfunc}.
     */
    static class AllocFuncRootNode extends MethodDescriptorRoot {
        @Child private ReadIndexedArgumentNode readArgNode;
        @Child private TrufflePInt_AsPrimitive asSsizeTNode;

        protected AllocFuncRootNode(PythonLanguage language, PythonObjectFactory factory, CallTarget callTarget) {
            super(language, factory, callTarget);
            this.readArgNode = ReadIndexedArgumentNode.create(1);
            this.asSsizeTNode = TrufflePInt_AsPrimitiveFactory.create();
        }

        @Override
        public Object execute(VirtualFrame frame) {
            Object self = readSelfNode.execute(frame);
            Object arg = readArgNode.execute(frame);
            Object[] arguments = PArguments.create(2);
            PArguments.setArgument(arguments, 0, self);
            PArguments.setArgument(arguments, 1, asSsizeTNode.executeLong(arg, 1, Long.BYTES));
            return directCallNode.call(arguments);
        }
    }

    /**
     * Wrapper root node for a get attribute function (C type {@code getattrfunc}).
     */
    static class GetAttrFuncRootNode extends MethodDescriptorRoot {
        @Child private ReadIndexedArgumentNode readArgNode;
        @Child private CExtNodes.AsCharPointer asCharPointerNode;

        protected GetAttrFuncRootNode(PythonLanguage language, PythonObjectFactory factory, CallTarget callTarget) {
            super(language, factory, callTarget);
            this.readArgNode = ReadIndexedArgumentNode.create(1);
            this.asCharPointerNode = CExtNodes.AsCharPointer.create();
        }

        @Override
        public Object execute(VirtualFrame frame) {
            Object self = readSelfNode.execute(frame);
            Object arg = readArgNode.execute(frame);
            Object[] arguments = PArguments.create(2);
            PArguments.setArgument(arguments, 0, self);
            // TODO we should use "CStringWrapper" but it does currently not support PString
            PArguments.setArgument(arguments, 1, asCharPointerNode.execute(arg));
            return directCallNode.call(arguments);
        }
    }

    /**
     * Wrapper root node for a set attribute function (C type {@code setattrfunc}).
     */
    static class SetAttrFuncRootNode extends MethodDescriptorRoot {
        @Child private ReadIndexedArgumentNode readArg1Node;
        @Child private ReadIndexedArgumentNode readArg2Node;
        @Child private CExtNodes.AsCharPointer asCharPointerNode;

        protected SetAttrFuncRootNode(PythonLanguage language, PythonObjectFactory factory, CallTarget callTarget) {
            super(language, factory, callTarget);
            this.readArg1Node = ReadIndexedArgumentNode.create(1);
            this.readArg2Node = ReadIndexedArgumentNode.create(2);
            this.asCharPointerNode = CExtNodes.AsCharPointer.create();
        }

        @Override
        public Object execute(VirtualFrame frame) {
            Object self = readSelfNode.execute(frame);
            Object arg1 = readArg1Node.execute(frame);
            Object arg2 = readArg2Node.execute(frame);
            Object[] arguments = PArguments.create(3);
            PArguments.setArgument(arguments, 0, self);
            // TODO we should use "CStringWrapper" but it does currently not support PString
            PArguments.setArgument(arguments, 1, asCharPointerNode.execute(arg1));
            PArguments.setArgument(arguments, 2, arg2);
            return directCallNode.call(arguments);
        }
    }

    /**
     * Wrapper root node for a rich compare function (C type {@code richcmpfunc}).
     */
    static class RichCmpFuncRootNode extends MethodDescriptorRoot {
        @Child private ReadIndexedArgumentNode readArg1Node;
        @Child private ReadIndexedArgumentNode readArg2Node;
        @Child private TrufflePInt_AsPrimitive asSsizeTNode;

        protected RichCmpFuncRootNode(PythonLanguage language, PythonObjectFactory factory, CallTarget callTarget) {
            super(language, factory, callTarget);
            this.readArg1Node = ReadIndexedArgumentNode.create(1);
            this.readArg2Node = ReadIndexedArgumentNode.create(2);
            this.asSsizeTNode = TrufflePInt_AsPrimitiveFactory.create();
        }

        @Override
        public Object execute(VirtualFrame frame) {
            Object self = readSelfNode.execute(frame);
            Object arg1 = readArg1Node.execute(frame);
            Object arg2 = readArg2Node.execute(frame);
            Object[] arguments = PArguments.create(3);
            PArguments.setArgument(arguments, 0, self);
            PArguments.setArgument(arguments, 1, arg1);
            PArguments.setArgument(arguments, 2, asSsizeTNode.executeInt(arg2, 1, Integer.BYTES));
            return directCallNode.call(arguments);
        }
    }

    /**
     * Wrapper root node for C function type {@code ssizeobjargproc}.
     */
    static class SSizeObjArgProcRootNode extends MethodDescriptorRoot {
        @Child private ReadIndexedArgumentNode readArg1Node;
        @Child private ReadIndexedArgumentNode readArg2Node;
        @Child private TrufflePInt_AsPrimitive asSsizeTNode;

        protected SSizeObjArgProcRootNode(PythonLanguage language, PythonObjectFactory factory, CallTarget callTarget) {
            super(language, factory, callTarget);
            this.readArg1Node = ReadIndexedArgumentNode.create(1);
            this.readArg2Node = ReadIndexedArgumentNode.create(2);
            this.asSsizeTNode = TrufflePInt_AsPrimitiveFactory.create();
        }

        @Override
        public Object execute(VirtualFrame frame) {
            Object self = readSelfNode.execute(frame);
            Object arg1 = readArg1Node.execute(frame);
            Object arg2 = readArg2Node.execute(frame);
            Object[] arguments = PArguments.create(3);
            PArguments.setArgument(arguments, 0, self);
            PArguments.setArgument(arguments, 1, asSsizeTNode.executeLong(arg1, 1, Long.BYTES));
            PArguments.setArgument(arguments, 2, arg2);
            return directCallNode.call(arguments);
        }
    }

    /**
     * Wrapper root node for reverse binary operations.
     */
    static class MethReverseRootNode extends MethodDescriptorRoot {
        @Child private ReadIndexedArgumentNode readArg0Node;
        @Child private ReadIndexedArgumentNode readArg1Node;

        protected MethReverseRootNode(PythonLanguage language, PythonObjectFactory factory, CallTarget callTarget) {
            super(language, factory, callTarget);
            this.readArg0Node = ReadIndexedArgumentNode.create(0);
            this.readArg1Node = ReadIndexedArgumentNode.create(1);
        }

        @Override
        public Object execute(VirtualFrame frame) {
            Object arg0 = readArg0Node.execute(frame);
            Object arg1 = readArg1Node.execute(frame);
            Object[] arguments = PArguments.create(2);
            PArguments.setArgument(arguments, 0, arg1);
            PArguments.setArgument(arguments, 1, arg0);
            return directCallNode.call(arguments);
        }
    }

    /**
     * Wrapper root node for native power function (with an optional third argument).
     */
    static class MethPowRootNode extends MethodDescriptorRoot {
        @Child private ReadVarArgsNode readVarargsNode;

        private final ConditionProfile profile = ConditionProfile.createBinaryProfile();

        protected MethPowRootNode(PythonLanguage language, PythonObjectFactory factory, CallTarget callTarget) {
            super(language, factory, callTarget);
            this.readVarargsNode = ReadVarArgsNode.create(1, true);
        }

        @Override
        public Object execute(VirtualFrame frame) {
            Object self = readSelfNode.execute(frame);
            Object[] args = readVarargsNode.executeObjectArray(frame);
            Object[] arguments = PArguments.create(3);
            PArguments.setArgument(arguments, 0, self);
            PArguments.setArgument(arguments, 1, args[0]);
            if (profile.profile(args.length > 1)) {
                PArguments.setArgument(arguments, 2, args[1]);
            } else {
                PArguments.setArgument(arguments, 2, PNone.NONE);
            }
            return directCallNode.call(arguments);
        }
    }

    /**
     * Wrapper root node for native power function (with an optional third argument).
     */
    static class MethRichcmpOpRootNode extends MethodDescriptorRoot {
        @Child private ReadIndexedArgumentNode readArg1Node;

        private final int op;

        protected MethRichcmpOpRootNode(PythonLanguage language, PythonObjectFactory factory, CallTarget callTarget, int op) {
            super(language, factory, callTarget);
            this.readArg1Node = ReadIndexedArgumentNode.create(1);
            this.op = op;
        }

        @Override
        public Object execute(VirtualFrame frame) {
            Object self = readSelfNode.execute(frame);
            Object arg1 = readArg1Node.execute(frame);
            Object[] arguments = PArguments.create(3);
            PArguments.setArgument(arguments, 0, self);
            PArguments.setArgument(arguments, 1, arg1);
            PArguments.setArgument(arguments, 2, op);
            return directCallNode.call(arguments);
        }
    }
}
