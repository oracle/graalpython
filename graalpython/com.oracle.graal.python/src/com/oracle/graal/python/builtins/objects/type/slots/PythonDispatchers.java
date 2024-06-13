/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.objects.type.slots;

import com.oracle.graal.python.builtins.objects.function.PArguments;
import com.oracle.graal.python.builtins.objects.function.PFunction;
import com.oracle.graal.python.builtins.objects.function.Signature;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.call.BoundDescriptor;
import com.oracle.graal.python.nodes.call.CallNode;
import com.oracle.graal.python.nodes.call.FunctionInvokeNode;
import com.oracle.graal.python.nodes.call.special.MaybeBindDescriptorNode;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.HostCompilerDirectives.InliningCutoff;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Idempotent;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.NeverDefault;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.utilities.TruffleWeakReference;

abstract class PythonDispatchers {
    private PythonDispatchers() {
    }

    abstract static class PythonSlotDispatcherNodeBase extends PNodeWithContext {
        @Idempotent
        static boolean isSimpleSignature(PFunction callable, int positionArgsCount) {
            Signature signature = callable.getCode().getSignature();
            boolean result = signature.takesPositionalOnly() && signature.getMaxNumOfPositionalArgs() == positionArgsCount;
            CompilerAsserts.partialEvaluationConstant(result); // should hold in single context
            return result;
        }

        @NeverDefault
        static FunctionInvokeNode createInvokeNode(PFunction callee) {
            return FunctionInvokeNode.create(callee);
        }
    }

    @GenerateUncached
    @GenerateInline
    @GenerateCached(false)
    @ImportStatic(PGuards.class)
    abstract static class UnaryPythonSlotDispatcherNode extends PythonSlotDispatcherNodeBase {
        final Object execute(VirtualFrame frame, Node inliningTarget, Object callable, Object type, Object self) {
            assert !(callable instanceof TruffleWeakReference<?>);
            assert !(type instanceof TruffleWeakReference<?>);
            return executeImpl(frame, inliningTarget, callable, type, self);
        }

        abstract Object executeImpl(VirtualFrame frame, Node inliningTarget, Object callable, Object type, Object self);

        @Specialization(guards = {"isSingleContext()", "callee == cachedCallee", "isSimpleSignature(cachedCallee, 1)"}, limit = "getCallSiteInlineCacheMaxDepth()")
        protected static Object doCachedPFunction(VirtualFrame frame, @SuppressWarnings("unused") PFunction callee, @SuppressWarnings("unused") Object type, Object self,
                        @SuppressWarnings("unused") @Cached("callee") PFunction cachedCallee,
                        @Cached("createInvokeNode(cachedCallee)") FunctionInvokeNode invoke) {
            Object[] arguments = PArguments.create(1);
            PArguments.setArgument(arguments, 0, self);
            return invoke.execute(frame, arguments);
        }

        @Specialization(replaces = "doCachedPFunction")
        @InliningCutoff
        static Object doGeneric(VirtualFrame frame, Node inliningTarget, Object callableObj, Object type, Object self,
                        @Cached MaybeBindDescriptorNode bindDescriptorNode,
                        @Cached(inline = false) CallNode callNode) {
            Object bound = bindDescriptorNode.execute(frame, inliningTarget, callableObj, self, type);
            Object[] arguments;
            Object callable;
            if (bound instanceof BoundDescriptor boundDescr) {
                callable = boundDescr.descriptor;
                arguments = PythonUtils.EMPTY_OBJECT_ARRAY;
            } else {
                callable = bound;
                arguments = new Object[]{self};
            }
            return callNode.execute(frame, callable, arguments);
        }
    }

    @GenerateUncached
    @GenerateInline
    @GenerateCached(false)
    abstract static class BinaryPythonSlotDispatcherNode extends PythonSlotDispatcherNodeBase {
        final Object execute(VirtualFrame frame, Node inliningTarget, Object callable, Object type, Object self, Object arg1) {
            assert !(callable instanceof TruffleWeakReference<?>);
            assert !(type instanceof TruffleWeakReference<?>);
            return executeImpl(frame, inliningTarget, callable, type, self, arg1);
        }

        abstract Object executeImpl(VirtualFrame frame, Node inliningTarget, Object callable, Object type, Object self, Object arg1);

        @Specialization(guards = {"isSingleContext()", "callee == cachedCallee", "isSimpleSignature(cachedCallee, 2)"}, limit = "getCallSiteInlineCacheMaxDepth()")
        protected static Object doCachedPFunction(VirtualFrame frame, @SuppressWarnings("unused") PFunction callee, @SuppressWarnings("unused") Object type, Object self, Object arg1,
                        @SuppressWarnings("unused") @Cached("callee") PFunction cachedCallee,
                        @Cached("createInvokeNode(cachedCallee)") FunctionInvokeNode invoke) {
            Object[] arguments = PArguments.create(2);
            PArguments.setArgument(arguments, 0, self);
            PArguments.setArgument(arguments, 1, arg1);
            return invoke.execute(frame, arguments);
        }

        @Specialization(replaces = "doCachedPFunction")
        @InliningCutoff
        static Object doGeneric(VirtualFrame frame, Node inliningTarget, Object callableObj, Object type, Object self, Object arg1,
                        @Cached MaybeBindDescriptorNode bindDescriptorNode,
                        @Cached(inline = false) CallNode callNode) {
            Object bound = bindDescriptorNode.execute(frame, inliningTarget, callableObj, self, type);
            Object[] arguments;
            Object callable;
            if (bound instanceof BoundDescriptor boundDescr) {
                callable = boundDescr.descriptor;
                arguments = new Object[]{arg1};
            } else {
                callable = bound;
                arguments = new Object[]{self, arg1};
            }
            return callNode.execute(frame, callable, arguments);
        }
    }

    @GenerateUncached
    @GenerateInline
    @GenerateCached(false)
    abstract static class TernaryOrBinaryPythonSlotDispatcherNode extends PythonSlotDispatcherNodeBase {
        final Object execute(VirtualFrame frame, Node inliningTarget, boolean callTernary, Object callable, Object type, Object self, Object arg1, Object arg2) {
            assert !(callable instanceof TruffleWeakReference<?>);
            assert !(type instanceof TruffleWeakReference<?>);
            return executeImpl(frame, inliningTarget, callTernary, callable, type, self, arg1, arg2);
        }

        abstract Object executeImpl(VirtualFrame frame, Node inliningTarget, boolean callTernary, Object callable, Object type, Object self, Object arg1, Object arg2);

        @Idempotent
        static int getArgsCount(boolean callTernary) {
            return callTernary ? 3 : 2;
        }

        @Specialization(guards = {"isSingleContext()", "callee == cachedCallee", "isSimpleSignature(cachedCallee, getArgsCount(callTernary))"}, limit = "getCallSiteInlineCacheMaxDepth()")
        protected static Object doCachedPFunction(VirtualFrame frame, boolean callTernary, @SuppressWarnings("unused") PFunction callee, @SuppressWarnings("unused") Object type, Object self,
                        Object arg1, Object arg2,
                        @SuppressWarnings("unused") @Cached("callee") PFunction cachedCallee,
                        @Cached("createInvokeNode(cachedCallee)") FunctionInvokeNode invoke) {
            Object[] arguments = PArguments.create(getArgsCount(callTernary));
            PArguments.setArgument(arguments, 0, self);
            PArguments.setArgument(arguments, 1, arg1);
            if (callTernary) {
                PArguments.setArgument(arguments, 2, arg2);
            }
            return invoke.execute(frame, arguments);
        }

        @Specialization(replaces = "doCachedPFunction")
        @InliningCutoff
        static Object doGeneric(VirtualFrame frame, Node inliningTarget, boolean callTernary, Object callableObj, Object type, Object self, Object arg1, Object arg2,
                        @Cached MaybeBindDescriptorNode bindDescriptorNode,
                        @Cached(inline = false) CallNode callNode) {
            Object bound = bindDescriptorNode.execute(frame, inliningTarget, callableObj, self, type);
            int argsCount = 1 + asInt(callTernary) + asInt(!(bound instanceof BoundDescriptor));
            Object[] arguments = new Object[argsCount];
            int argIndex = 0;
            Object callable;
            if (bound instanceof BoundDescriptor boundDescr) {
                callable = boundDescr.descriptor;
            } else {
                callable = bound;
                arguments[argIndex++] = self;
            }
            arguments[argIndex++] = arg1;
            if (callTernary) {
                arguments[argIndex] = arg2;
            }
            return callNode.execute(frame, callable, arguments);
        }

        private static int asInt(boolean b) {
            return b ? 1 : 0;
        }
    }
}
