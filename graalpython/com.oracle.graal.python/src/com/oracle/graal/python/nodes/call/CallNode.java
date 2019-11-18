/*
 * Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.nodes.call;

import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.function.PArguments;
import com.oracle.graal.python.builtins.objects.function.PBuiltinFunction;
import com.oracle.graal.python.builtins.objects.function.PFunction;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.builtins.objects.method.PBuiltinMethod;
import com.oracle.graal.python.builtins.objects.method.PDecoratedMethod;
import com.oracle.graal.python.builtins.objects.method.PMethod;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.SpecialMethodNames;
import com.oracle.graal.python.nodes.argument.CreateArgumentsNode;
import com.oracle.graal.python.nodes.argument.positional.PositionalArgumentsNode;
import com.oracle.graal.python.nodes.attributes.LookupInheritedAttributeNode;
import com.oracle.graal.python.nodes.call.CallNodeFactory.CachedCallNodeGen;
import com.oracle.graal.python.nodes.call.special.CallVarargsMethodNode;
import com.oracle.graal.python.nodes.function.ClassBodyRootNode;
import com.oracle.graal.python.nodes.truffle.PythonTypes;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.TypeSystemReference;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.NodeCost;

@TypeSystemReference(PythonTypes.class)
@ImportStatic({PGuards.class, SpecialMethodNames.class})
public abstract class CallNode extends PNodeWithContext {
    private static final CallNode UNCACHED = new UncachedCallNode();

    public static CallNode create() {
        return CachedCallNodeGen.create();
    }

    public static CallNode getUncached() {
        return UNCACHED;
    }

    public abstract Object execute(VirtualFrame frame, Object callableObject, Object[] arguments, PKeyword[] keywords);

    public final Object execute(VirtualFrame frame, Object callableObject, Object... arguments) {
        return execute(frame, callableObject, arguments, PKeyword.EMPTY_KEYWORDS);
    }

    abstract static class CachedCallNode extends CallNode {
        @Child private CreateArgumentsNode createArguments = CreateArgumentsNode.create();
        @Child private CallDispatchNode dispatch = CallDispatchNode.create();

        @Specialization(guards = {"!isCallable(callableObject) || isClass(callableObject)"})
        protected Object specialCall(VirtualFrame frame, Object callableObject, Object[] arguments, PKeyword[] keywords,
                        @Cached PRaiseNode raise,
                        @Cached("create(__CALL__)") LookupInheritedAttributeNode callAttrGetterNode,
                        @Cached("create()") CallVarargsMethodNode callCallNode) {
            Object call = callAttrGetterNode.execute(callableObject);
            if (call == PNone.NO_VALUE) {
                throw raise.raise(PythonBuiltinClassType.TypeError, "'%p' object is not callable", callableObject);
            }
            return callCallNode.execute(frame, call, PositionalArgumentsNode.prependArgument(callableObject, arguments), keywords);
        }

        private CreateArgumentsNode ensureCreateArguments() {
            if (createArguments == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                createArguments = insert(CreateArgumentsNode.create());
            }
            return createArguments;
        }

        private CallDispatchNode ensureDispatch() {
            if (dispatch == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                dispatch = insert(CallDispatchNode.create());
            }
            return dispatch;
        }

        @Specialization
        protected Object decoratedMethodCall(VirtualFrame frame, PDecoratedMethod callable, Object[] arguments, PKeyword[] keywords,
                        @Cached PRaiseNode raise,
                        @Cached("create(__CALL__)") LookupInheritedAttributeNode callAttrGetterNode,
                        @Cached("create()") CallVarargsMethodNode callCallNode) {
            return specialCall(frame, callable.getCallable(), arguments, keywords, raise, callAttrGetterNode, callCallNode);
        }

        @Specialization(guards = "isPFunction(callable.getFunction())")
        protected Object methodCallDirect(VirtualFrame frame, PMethod callable, Object[] arguments, PKeyword[] keywords) {
            // functions must be called directly otherwise the call stack is incorrect
            return ensureDispatch().executeCall(frame, (PFunction) callable.getFunction(), ensureCreateArguments().execute(callable, arguments, keywords));
        }

        @Specialization(guards = "isPBuiltinFunction(callable.getFunction())")
        protected Object methodCallBuiltinDirect(VirtualFrame frame, PMethod callable, Object[] arguments, PKeyword[] keywords) {
            // functions must be called directly otherwise the call stack is incorrect
            return ensureDispatch().executeCall(frame, (PBuiltinFunction) callable.getFunction(), ensureCreateArguments().execute(callable, arguments, keywords));
        }

        @Specialization(guards = "isPFunction(callable.getFunction())")
        protected Object builtinMethodCallDirect(VirtualFrame frame, PBuiltinMethod callable, Object[] arguments, PKeyword[] keywords) {
            // functions must be called directly otherwise the call stack is incorrect
            return ensureDispatch().executeCall(frame, (PFunction) callable.getFunction(), ensureCreateArguments().execute(callable, arguments, keywords));
        }

        @Specialization(limit = "1", guards = {"callable == cachedCallable", "isPBuiltinFunction(cachedCallable.getFunction())"}, assumptions = "singleContextAssumption()")
        protected Object builtinMethodCallBuiltinDirectCached(VirtualFrame frame, @SuppressWarnings("unused") PBuiltinMethod callable, Object[] arguments, PKeyword[] keywords,
                        @Cached("callable") PBuiltinMethod cachedCallable) {
            // functions must be called directly otherwise the call stack is incorrect
            return ensureDispatch().executeCall(frame, (PBuiltinFunction) cachedCallable.getFunction(), ensureCreateArguments().execute(cachedCallable, arguments, keywords));
        }

        @Specialization(guards = "isPBuiltinFunction(callable.getFunction())")
        protected Object builtinMethodCallBuiltinDirect(VirtualFrame frame, PBuiltinMethod callable, Object[] arguments, PKeyword[] keywords) {
            // functions must be called directly otherwise the call stack is incorrect
            return ensureDispatch().executeCall(frame, (PBuiltinFunction) callable.getFunction(), ensureCreateArguments().execute(callable, arguments, keywords));
        }

        @Specialization(guards = "!isFunction(callable.getFunction())")
        protected Object methodCall(VirtualFrame frame, PMethod callable, Object[] arguments, PKeyword[] keywords,
                        @Cached PRaiseNode raise,
                        @Cached("create(__CALL__)") LookupInheritedAttributeNode callAttrGetterNode,
                        @Cached("create()") CallVarargsMethodNode callCallNode) {
            return specialCall(frame, callable, arguments, keywords, raise, callAttrGetterNode, callCallNode);
        }

        @Specialization(guards = "!isFunction(callable.getFunction())")
        protected Object builtinMethodCall(VirtualFrame frame, PBuiltinMethod callable, Object[] arguments, PKeyword[] keywords,
                        @Cached PRaiseNode raise,
                        @Cached("create(__CALL__)") LookupInheritedAttributeNode callAttrGetterNode,
                        @Cached("create()") CallVarargsMethodNode callCallNode) {
            return specialCall(frame, callable, arguments, keywords, raise, callAttrGetterNode, callCallNode);
        }

        @Specialization
        protected Object functionCall(VirtualFrame frame, PFunction callable, Object[] arguments, PKeyword[] keywords) {
            return ensureDispatch().executeCall(frame, callable, ensureCreateArguments().execute(callable, arguments, keywords));
        }

        @Specialization
        protected Object builtinFunctionCall(VirtualFrame frame, PBuiltinFunction callable, Object[] arguments, PKeyword[] keywords) {
            return ensureDispatch().executeCall(frame, callable, ensureCreateArguments().execute(callable, arguments, keywords));
        }
    }

    private static final class UncachedCallNode extends CallNode {
        private final CreateArgumentsNode createArgs = CreateArgumentsNode.getUncached();
        private final GenericInvokeNode invokeNode = GenericInvokeNode.getUncached();

        @Override
        public Object execute(VirtualFrame frame, Object callableObject, Object[] args, PKeyword[] keywords) {
            RootCallTarget ct = null;
            Object[] arguments = null;

            if (callableObject instanceof PFunction) {
                PFunction function = (PFunction) callableObject;
                arguments = createArgs.execute(function, args, keywords);
                ct = function.getCallTarget();
                PArguments.setClosure(arguments, function.getClosure());
                PArguments.setGlobals(arguments, function.getGlobals());
            } else if (callableObject instanceof PBuiltinFunction) {
                PBuiltinFunction builtinFunction = (PBuiltinFunction) callableObject;
                arguments = createArgs.execute(builtinFunction, args, keywords);
                ct = builtinFunction.getCallTarget();
            } else if (callableObject instanceof PMethod) {
                PMethod method = (PMethod) callableObject;
                Object func = method.getFunction();
                if (func instanceof PFunction) {
                    arguments = createArgs.execute(method, args, keywords);
                    ct = ((PFunction) func).getCallTarget();
                } else {
                    arguments = createArgs.execute(method, args, keywords);
                    ct = ((PBuiltinFunction) func).getCallTarget();
                }
            } else if (callableObject instanceof PBuiltinMethod) {
                PBuiltinMethod builtinMethod = (PBuiltinMethod) callableObject;
                Object func = builtinMethod.getFunction();
                if (func instanceof PFunction) {
                    arguments = createArgs.execute(builtinMethod, args, keywords);
                    ct = ((PFunction) func).getCallTarget();
                } else {
                    arguments = createArgs.execute(builtinMethod, args, keywords);
                    ct = ((PBuiltinFunction) func).getCallTarget();
                }
            }

            if (ct == null || arguments == null) {
                Object attrCall = LookupInheritedAttributeNode.Dynamic.getUncached().execute(callableObject, SpecialMethodNames.__CALL__);
                if (attrCall == PNone.NO_VALUE) {
                    CompilerDirectives.transferToInterpreter();
                    throw PRaiseNode.getUncached().raise(PythonBuiltinClassType.TypeError, "'%p' object is not callable", callableObject);
                }
                return CallVarargsMethodNode.getUncached().execute(frame, attrCall, PositionalArgumentsNode.prependArgument(callableObject, args), keywords);
            } else {
                if (ct.getRootNode() instanceof ClassBodyRootNode) {
                    PArguments.setSpecialArgument(arguments, ct.getRootNode());
                }
                return invokeNode.execute(frame, ct, arguments);
            }
        }

        @Override
        public NodeCost getCost() {
            return NodeCost.MEGAMORPHIC;
        }

        @Override
        public boolean isAdoptable() {
            return false;
        }
    }
}
