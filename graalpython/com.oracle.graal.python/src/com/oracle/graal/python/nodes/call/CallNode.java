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
package com.oracle.graal.python.nodes.call;

import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.PNone;
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
import com.oracle.graal.python.nodes.call.special.CallVarargsMethodNode;
import com.oracle.graal.python.nodes.truffle.PythonTypes;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.ReportPolymorphism;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.TypeSystemReference;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;

@TypeSystemReference(PythonTypes.class)
@ImportStatic({PGuards.class, SpecialMethodNames.class})
@ReportPolymorphism
@GenerateUncached
public abstract class CallNode extends PNodeWithContext {
    public static CallNode create() {
        return CallNodeGen.create();
    }

    public static CallNode getUncached() {
        return CallNodeGen.getUncached();
    }

    protected abstract Object executeInternal(Frame frame, Object callableObject, Object[] arguments, PKeyword[] keywords);

    /**
     * To be used when this node is called uncached or when no frame is available. Note that the
     * current thread state will be read from the context, so calls through this entry point are
     * potentially slower than if a frame is available.
     */
    public final Object execute(Object callableObject, Object[] arguments, PKeyword[] keywords) {
        return executeInternal(null, callableObject, arguments, keywords);
    }

    /**
     * To be used when this node is called uncached or when no frame is available. Note that the
     * current thread state will be read from the context, so calls through this entry point are
     * potentially slower than if a frame is available.
     */
    public final Object execute(Object callableObject, Object... arguments) {
        return executeInternal(null, callableObject, arguments, PKeyword.EMPTY_KEYWORDS);
    }

    public final Object execute(VirtualFrame frame, Object callableObject, Object[] arguments, PKeyword[] keywords) {
        return executeInternal(frame, callableObject, arguments, keywords);
    }

    public final Object execute(VirtualFrame frame, Object callableObject, Object... arguments) {
        return executeInternal(frame, callableObject, arguments, PKeyword.EMPTY_KEYWORDS);
    }

    @Specialization(guards = "!isCallable(callableObject) || isClass(callableObject)")
    protected Object doObjectAndType(VirtualFrame frame, Object callableObject, Object[] arguments, PKeyword[] keywords,
                    @Cached PRaiseNode raise,
                    @Cached("create(__CALL__)") LookupInheritedAttributeNode callAttrGetterNode,
                    @Cached("create()") CallVarargsMethodNode callCallNode) {
        Object call = callAttrGetterNode.execute(callableObject);
        return callCall(frame, callableObject, arguments, keywords, raise, callCallNode, call);
    }

    private static Object callCall(VirtualFrame frame, Object callableObject, Object[] arguments, PKeyword[] keywords, PRaiseNode raise, CallVarargsMethodNode callCallNode, Object call) {
        if (call == PNone.NO_VALUE) {
            throw raise.raise(PythonBuiltinClassType.TypeError, "'%p' object is not callable", callableObject);
        }
        return callCallNode.execute(frame, call, PositionalArgumentsNode.prependArgument(callableObject, arguments), keywords);
    }

    @Specialization
    protected Object decoratedMethodCall(VirtualFrame frame, PDecoratedMethod callable, Object[] arguments, PKeyword[] keywords,
                    @Cached CallNode recursive) {
        return recursive.execute(frame, callable.getCallable(), arguments, keywords);
    }

    @Specialization(guards = "isPBuiltinFunction(callable.getFunction())")
    protected Object methodCallBuiltinDirect(VirtualFrame frame, PMethod callable, Object[] arguments, PKeyword[] keywords,
                    @Shared("dispatchNode") @Cached CallDispatchNode dispatch,
                    @Shared("argsNode") @Cached CreateArgumentsNode createArgs) {
        // functions must be called directly otherwise the call stack is incorrect
        return dispatch.executeCall(frame, (PBuiltinFunction) callable.getFunction(), createArgs.execute(callable, arguments, keywords));
    }

    @Specialization(guards = "isPFunction(callable.getFunction())", replaces = "methodCallBuiltinDirect")
    protected Object methodCallDirect(VirtualFrame frame, PMethod callable, Object[] arguments, PKeyword[] keywords,
                    @Shared("dispatchNode") @Cached CallDispatchNode dispatch,
                    @Shared("argsNode") @Cached CreateArgumentsNode createArgs) {
        // functions must be called directly otherwise the call stack is incorrect
        return dispatch.executeCall(frame, (PFunction) callable.getFunction(), createArgs.execute(callable, arguments, keywords));
    }

    @Specialization(limit = "1", guards = {"callable == cachedCallable", "isPBuiltinFunction(cachedCallable.getFunction())"}, assumptions = "singleContextAssumption()")
    protected Object builtinMethodCallBuiltinDirectCached(VirtualFrame frame, @SuppressWarnings("unused") PBuiltinMethod callable, Object[] arguments, PKeyword[] keywords,
                    @Cached("callable") PBuiltinMethod cachedCallable,
                    @Shared("dispatchNode") @Cached CallDispatchNode dispatch,
                    @Shared("argsNode") @Cached CreateArgumentsNode createArgs) {
        // functions must be called directly otherwise the call stack is incorrect
        return dispatch.executeCall(frame, cachedCallable.getFunction(), createArgs.execute(cachedCallable, arguments, keywords));
    }

    @Specialization(guards = "isPBuiltinFunction(callable.getFunction())", replaces = "builtinMethodCallBuiltinDirectCached")
    protected Object builtinMethodCallBuiltinDirect(VirtualFrame frame, PBuiltinMethod callable, Object[] arguments, PKeyword[] keywords,
                    @Shared("dispatchNode") @Cached CallDispatchNode dispatch,
                    @Shared("argsNode") @Cached CreateArgumentsNode createArgs) {
        // functions must be called directly otherwise the call stack is incorrect
        return dispatch.executeCall(frame, callable.getFunction(), createArgs.execute(callable, arguments, keywords));
    }

    @Specialization(guards = "!isFunction(callable.getFunction())")
    protected Object methodCall(VirtualFrame frame, PMethod callable, Object[] arguments, PKeyword[] keywords,
                    @Cached PRaiseNode raise,
                    @Cached("create(__CALL__)") LookupInheritedAttributeNode callAttrGetterNode,
                    @Cached("create()") CallVarargsMethodNode callCallNode) {
        return doObjectAndType(frame, callable, arguments, keywords, raise, callAttrGetterNode, callCallNode);
    }

    @Specialization(guards = "!isFunction(callable.getFunction())")
    protected Object builtinMethodCall(VirtualFrame frame, PBuiltinMethod callable, Object[] arguments, PKeyword[] keywords,
                    @Cached PRaiseNode raise,
                    @Cached("create(__CALL__)") LookupInheritedAttributeNode callAttrGetterNode,
                    @Cached("create()") CallVarargsMethodNode callCallNode) {
        return doObjectAndType(frame, callable, arguments, keywords, raise, callAttrGetterNode, callCallNode);
    }

    @Specialization
    protected Object functionCall(VirtualFrame frame, PFunction callable, Object[] arguments, PKeyword[] keywords,
                    @Shared("dispatchNode") @Cached CallDispatchNode dispatch,
                    @Shared("argsNode") @Cached CreateArgumentsNode createArgs) {
        return dispatch.executeCall(frame, callable, createArgs.execute(callable, arguments, keywords));
    }

    @Specialization
    protected Object builtinFunctionCall(VirtualFrame frame, PBuiltinFunction callable, Object[] arguments, PKeyword[] keywords,
                    @Shared("dispatchNode") @Cached CallDispatchNode dispatch,
                    @Shared("argsNode") @Cached CreateArgumentsNode createArgs) {
        return dispatch.executeCall(frame, callable, createArgs.execute(callable, arguments, keywords));
    }

    @Specialization(replaces = {"doObjectAndType", "decoratedMethodCall", "methodCallBuiltinDirect", "methodCallDirect", "builtinMethodCallBuiltinDirectCached",
                    "builtinMethodCallBuiltinDirect", "methodCall", "builtinMethodCall", "functionCall", "builtinFunctionCall"})
    protected Object doGeneric(VirtualFrame frame, Object callableObject, Object[] arguments, PKeyword[] keywords,
                    @Shared("dispatchNode") @Cached CallDispatchNode dispatch,
                    @Shared("argsNode") @Cached CreateArgumentsNode createArgs,
                    @Cached PRaiseNode raise,
                    @Cached LookupInheritedAttributeNode.Dynamic callAttrGetterNode,
                    @Cached("create()") CallVarargsMethodNode callCallNode) {
        if (callableObject instanceof PFunction) {
            return functionCall(frame, (PFunction) callableObject, arguments, keywords, dispatch, createArgs);
        } else if (callableObject instanceof PBuiltinFunction) {
            return builtinFunctionCall(frame, (PBuiltinFunction) callableObject, arguments, keywords, dispatch, createArgs);
        } else if (callableObject instanceof PMethod) {
            PMethod method = (PMethod) callableObject;
            Object func = method.getFunction();
            if (func instanceof PFunction) {
                return methodCallDirect(frame, method, arguments, keywords, dispatch, createArgs);
            } else if (func instanceof PBuiltinFunction) {
                return methodCallBuiltinDirect(frame, method, arguments, keywords, dispatch, createArgs);
            }
        } else if (callableObject instanceof PBuiltinMethod) {
            PBuiltinMethod method = (PBuiltinMethod) callableObject;
            return builtinMethodCallBuiltinDirect(frame, method, arguments, keywords, dispatch, createArgs);
        }
        return callCall(frame, callableObject, arguments, keywords, raise, callCallNode, callAttrGetterNode.execute(callableObject, SpecialMethodNames.__CALL__));
    }

    @Override
    public Node copy() {
        if (isAdoptable()) {
            return create();
        } else {
            return super.copy();
        }
    }
}
