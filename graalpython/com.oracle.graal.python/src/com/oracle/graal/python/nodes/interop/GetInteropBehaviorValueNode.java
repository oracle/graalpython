/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.nodes.interop;

import static com.oracle.graal.python.builtins.modules.PolyglotModuleBuiltins.RegisterInteropBehaviorNode.HOST_INTEROP_BEHAVIOR;
import static com.oracle.graal.python.nodes.ErrorMessages.FUNC_TAKES_EXACTLY_D_ARGS;
import static com.oracle.graal.python.util.PythonUtils.tsLiteral;

import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.PythonAbstractObject;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.runtime.GilNode;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.NeverDefault;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.IndirectCallNode;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.object.DynamicObject;
import com.oracle.truffle.api.object.DynamicObjectLibrary;
import com.oracle.truffle.api.profiles.InlinedConditionProfile;

@GenerateUncached
@GenerateInline
@SuppressWarnings("truffle-inlining") // some of the cached nodes in the specialization are not
                                      // inlineable
public abstract class GetInteropBehaviorValueNode extends PNodeWithContext {
    public abstract Object execute(Node inliningTarget, PythonAbstractObject receiver, InteropBehaviorMethod method, Object[] extraArguments);

    public final boolean executeBoolean(Node inliningTarget, PythonAbstractObject receiver, InteropBehaviorMethod method, boolean defaultValue) {
        assert method.isConstantBoolean() : "InteropBehaviorMethod must be a constant boolean";
        Object value = execute(inliningTarget, receiver, method);
        if (value != PNone.NO_VALUE) {
            return (boolean) value;
        } else {
            return defaultValue;
        }
    }

    public final Object execute(Node inliningTarget, PythonAbstractObject receiver, InteropBehaviorMethod method) {
        assert method.extraArguments == 0 : "InteropBehaviorMethod called with exactly 0 arguments, expected more";
        return execute(inliningTarget, receiver, method, PythonUtils.EMPTY_OBJECT_ARRAY);
    }

    public final Object execute(Node inliningTarget, PythonAbstractObject receiver, InteropBehaviorMethod method, Object arg1) {
        assert method.extraArguments == 1 : "InteropBehaviorMethod called with exactly 1 argument, expected 0 or more than 1";
        return execute(inliningTarget, receiver, method, new Object[]{arg1});
    }

    public final Object execute(Node inliningTarget, PythonAbstractObject receiver, InteropBehaviorMethod method, Object arg1, Object arg2) {
        assert method.extraArguments == 2 : "InteropBehaviorMethod called with exactly 2 arguments, expected 0, 1 or more than 2";
        return execute(inliningTarget, receiver, method, new Object[]{arg1, arg2});
    }

    private static Object getClass(Node inliningTarget, PythonAbstractObject receiver, GetClassNode getClassNode) {
        Object klass = getClassNode.execute(inliningTarget, receiver);
        if (klass instanceof PythonBuiltinClassType pythonBuiltinClassType) {
            klass = PythonContext.get(getClassNode).lookupType(pythonBuiltinClassType);
        }
        return klass;
    }

    @Specialization(guards = {"method.constantBoolean == true"})
    static Object getValueConstantBoolean(Node inliningTarget, PythonAbstractObject receiver, InteropBehaviorMethod method, @SuppressWarnings("unused") Object[] extraArguments,
                    @Shared @Cached GetClassNode getClassNode,
                    @Shared @Cached InlinedConditionProfile isMethodDefined,
                    @Shared @CachedLibrary(limit = "1") DynamicObjectLibrary dylib) {
        Object klass = getClass(inliningTarget, receiver, getClassNode);
        Object value = dylib.getOrDefault((DynamicObject) klass, HOST_INTEROP_BEHAVIOR, null);
        if (value instanceof InteropBehavior behavior && isMethodDefined.profile(inliningTarget, behavior.isDefined(method))) {
            return behavior.getConstantValue(method);
        }
        return PNone.NO_VALUE;
    }

    @Specialization(guards = {"method.constantBoolean == false", "method.checkArity(extraArguments)"})
    static Object getValueComputed(Node inliningTarget, PythonAbstractObject receiver, InteropBehaviorMethod method, Object[] extraArguments,
                    @Cached SimpleInvokeNodeDispatch invokeNode,
                    @Shared @Cached GetClassNode getClassNode,
                    @Cached(inline = false) GilNode gil,
                    @Shared @Cached InlinedConditionProfile isMethodDefined,
                    @Cached ConvertJavaStringArguments convertArgs,
                    @Shared @CachedLibrary(limit = "1") DynamicObjectLibrary dylib) {
        Object klass = getClass(inliningTarget, receiver, getClassNode);
        Object value = dylib.getOrDefault((DynamicObject) klass, HOST_INTEROP_BEHAVIOR, null);
        if (value instanceof InteropBehavior behavior && isMethodDefined.profile(inliningTarget, behavior.isDefined(method))) {
            CallTarget callTarget = behavior.getCallTarget(method);
            Object[] pArguments = behavior.createArguments(method, receiver, convertArgs.execute(inliningTarget, extraArguments));
            boolean mustRelease = gil.acquire();
            try {
                return invokeNode.execute(inliningTarget, callTarget, pArguments);
            } finally {
                gil.release(mustRelease);
            }
        }
        return PNone.NO_VALUE;
    }

    @Specialization(guards = {"method.constantBoolean == false", "!method.checkArity(extraArguments)"})
    @SuppressWarnings("unused")
    static Object getValueComputedWrongArity(Node inliningTarget, PythonAbstractObject receiver, InteropBehaviorMethod method, Object[] extraArguments,
                    @Cached PRaiseNode raiseNode) {
        throw raiseNode.raise(PythonBuiltinClassType.TypeError, FUNC_TAKES_EXACTLY_D_ARGS, method.extraArguments, extraArguments.length);
    }

    @NeverDefault
    public static GetInteropBehaviorValueNode create() {
        return GetInteropBehaviorValueNodeGen.create();
    }

    public static GetInteropBehaviorValueNode getUncached() {
        return GetInteropBehaviorValueNodeGen.getUncached();
    }

    @GenerateUncached
    @GenerateInline
    abstract static class SimpleInvokeNodeDispatch extends Node {
        public abstract Object execute(Node inliningTarget, CallTarget callTarget, Object[] arguments);

        @Specialization(guards = {"cachedCallTarget == callTarget"}, limit = "3")
        static Object doDirectCall(Node inliningTarget, CallTarget callTarget, Object[] arguments,
                        @Cached("callTarget") CallTarget cachedCallTarget,
                        @Cached("create(callTarget)") DirectCallNode directCallNode) {
            return directCallNode.call(arguments);
        }

        @Specialization(replaces = "doDirectCall")
        static Object doIndirectCall(Node inliningTarget, CallTarget callTarget, Object[] arguments,
                        @Cached IndirectCallNode indirectCallNode) {
            return indirectCallNode.call(callTarget, arguments);
        }
    }

    @GenerateUncached
    @GenerateInline
    abstract static class ConvertJavaStringArguments extends Node {
        public abstract Object[] execute(Node inliningTarget, Object[] arguments);

        static boolean containsJavaString(Object[] arguments) {
            for (Object arg : arguments) {
                if (arg instanceof String) {
                    return true;
                }
            }
            return false;
        }

        @Specialization(guards = {"containsJavaString(arguments)"})
        @TruffleBoundary
        static Object[] converted(@SuppressWarnings("unused") Node inliningTarget, Object[] arguments) {
            Object[] convertedArgs = new Object[arguments.length];
            for (int i = 0; i < arguments.length; i++) {
                Object arg = arguments[i];
                if (arg instanceof String javaString) {
                    convertedArgs[i] = tsLiteral(javaString);
                } else {
                    convertedArgs[i] = arg;
                }
            }
            return convertedArgs;
        }

        @Specialization(guards = {"!containsJavaString(arguments)"})
        static Object[] notConverted(@SuppressWarnings("unused") Node inliningTarget, Object[] arguments) {
            return arguments;
        }
    }
}
