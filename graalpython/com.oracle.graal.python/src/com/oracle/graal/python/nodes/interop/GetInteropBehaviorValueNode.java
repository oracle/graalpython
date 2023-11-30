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

import static com.oracle.graal.python.nodes.ErrorMessages.FUNC_TAKES_EXACTLY_D_ARGS;
import static com.oracle.graal.python.nodes.ErrorMessages.S_MUST_BE_S_NOT_P;
import static com.oracle.graal.python.util.PythonUtils.tsLiteral;

import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.PythonAbstractObject;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.util.CannotCastException;
import com.oracle.graal.python.nodes.util.CastToJavaBooleanNode;
import com.oracle.graal.python.nodes.util.CastToJavaByteNode;
import com.oracle.graal.python.nodes.util.CastToJavaDoubleNode;
import com.oracle.graal.python.nodes.util.CastToJavaIntExactNode;
import com.oracle.graal.python.nodes.util.CastToJavaLongExactNode;
import com.oracle.graal.python.nodes.util.CastToJavaShortNode;
import com.oracle.graal.python.nodes.util.CastToJavaStringNode;
import com.oracle.graal.python.runtime.GilNode;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.NeverDefault;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.IndirectCallNode;
import com.oracle.truffle.api.nodes.Node;

@GenerateUncached
@GenerateInline
@SuppressWarnings("truffle-inlining") // some of the cached nodes in the specialization are not
                                      // inlineable
public abstract class GetInteropBehaviorValueNode extends PNodeWithContext {
    public final boolean executeBoolean(Node inliningTarget, InteropBehavior behavior, InteropBehaviorMethod method, CastToJavaBooleanNode toBooleanNode, PRaiseNode.Lazy raiseNode,
                    PythonAbstractObject receiver, Object... extraArguments) {
        assert extraArguments.length == method.extraArguments : "number of passed arguments to GetInteropBehaviorValueNode does not match expected number of arguments for method";
        Object value = execute(inliningTarget, behavior, method, receiver, extraArguments);
        try {
            return toBooleanNode.execute(inliningTarget, value);
        } catch (CannotCastException cce) {
            throw raiseNode.get(inliningTarget).raise(PythonBuiltinClassType.TypeError, S_MUST_BE_S_NOT_P, "return value", "a boolean", value);
        }
    }

    public final byte executeByte(Node inliningTarget, InteropBehavior behavior, InteropBehaviorMethod method, CastToJavaByteNode toByteNode, PRaiseNode.Lazy raiseNode, PythonAbstractObject receiver,
                    Object... extraArguments) {
        assert extraArguments.length == method.extraArguments : "number of passed arguments to GetInteropBehaviorValueNode does not match expected number of arguments for method";
        Object value = execute(inliningTarget, behavior, method, receiver, extraArguments);
        try {
            return toByteNode.execute(inliningTarget, value);
        } catch (CannotCastException cce) {
            throw raiseNode.get(inliningTarget).raise(PythonBuiltinClassType.TypeError, S_MUST_BE_S_NOT_P, "return value", "a byte", value);
        }
    }

    public final short executeShort(Node inliningTarget, InteropBehavior behavior, InteropBehaviorMethod method, CastToJavaShortNode toShortNode, PRaiseNode.Lazy raiseNode,
                    PythonAbstractObject receiver, Object... extraArguments) {
        assert extraArguments.length == method.extraArguments : "number of passed arguments to GetInteropBehaviorValueNode does not match expected number of arguments for method";
        Object value = execute(inliningTarget, behavior, method, receiver, extraArguments);
        try {
            return toShortNode.execute(inliningTarget, value);
        } catch (CannotCastException cce) {
            throw raiseNode.get(inliningTarget).raise(PythonBuiltinClassType.TypeError, S_MUST_BE_S_NOT_P, "return value", "a short", value);
        }
    }

    public final int executeInt(Node inliningTarget, InteropBehavior behavior, InteropBehaviorMethod method, CastToJavaIntExactNode toIntNode, PRaiseNode.Lazy raiseNode, PythonAbstractObject receiver,
                    Object... extraArguments) {
        assert extraArguments.length == method.extraArguments : "number of passed arguments to GetInteropBehaviorValueNode does not match expected number of arguments for method";
        Object value = execute(inliningTarget, behavior, method, receiver, extraArguments);
        try {
            return toIntNode.execute(inliningTarget, value);
        } catch (CannotCastException cce) {
            throw raiseNode.get(inliningTarget).raise(PythonBuiltinClassType.TypeError, S_MUST_BE_S_NOT_P, "return value", "an int", value);
        }
    }

    public final long executeLong(Node inliningTarget, InteropBehavior behavior, InteropBehaviorMethod method, CastToJavaLongExactNode toLongNode, PRaiseNode.Lazy raiseNode,
                    PythonAbstractObject receiver, Object... extraArguments) {
        assert extraArguments.length == method.extraArguments : "number of passed arguments to GetInteropBehaviorValueNode does not match expected number of arguments for method";
        Object value = execute(inliningTarget, behavior, method, receiver, extraArguments);
        try {
            return toLongNode.execute(inliningTarget, value);
        } catch (CannotCastException cce) {
            throw raiseNode.get(inliningTarget).raise(PythonBuiltinClassType.TypeError, S_MUST_BE_S_NOT_P, "return value", "a long", value);
        }
    }

    public final double executeDouble(Node inliningTarget, InteropBehavior behavior, InteropBehaviorMethod method, CastToJavaDoubleNode toDoubleNode, PRaiseNode.Lazy raiseNode,
                    PythonAbstractObject receiver, Object... extraArguments) {
        assert extraArguments.length == method.extraArguments : "number of passed arguments to GetInteropBehaviorValueNode does not match expected number of arguments for method";
        Object value = execute(inliningTarget, behavior, method, receiver, extraArguments);
        try {
            return toDoubleNode.execute(inliningTarget, value);
        } catch (CannotCastException cce) {
            throw raiseNode.get(inliningTarget).raise(PythonBuiltinClassType.TypeError, S_MUST_BE_S_NOT_P, "return value", "a double", value);
        }
    }

    public final String executeString(Node inliningTarget, InteropBehavior behavior, InteropBehaviorMethod method, CastToJavaStringNode toStringNode, PRaiseNode.Lazy raiseNode,
                    PythonAbstractObject receiver, Object... extraArguments) {
        assert extraArguments.length == method.extraArguments : "number of passed arguments to GetInteropBehaviorValueNode does not match expected number of arguments for method";
        Object value = execute(inliningTarget, behavior, method, receiver, extraArguments);
        try {
            return toStringNode.execute(value);
        } catch (CannotCastException cce) {
            throw raiseNode.get(inliningTarget).raise(PythonBuiltinClassType.TypeError, S_MUST_BE_S_NOT_P, "return value", "a string", value);
        }
    }

    public final Object execute(Node inliningTarget, InteropBehavior behavior, InteropBehaviorMethod method, PythonAbstractObject receiver, Object arg1, Object arg2) {
        assert method.extraArguments == 2 : "method must take 2 arguments only";
        return execute(inliningTarget, behavior, method, receiver, new Object[]{arg1, arg2});
    }

    public final Object execute(Node inliningTarget, InteropBehavior behavior, InteropBehaviorMethod method, PythonAbstractObject receiver, Object arg1) {
        assert method.extraArguments == 1 : "method must take 1 argument only";
        return execute(inliningTarget, behavior, method, receiver, new Object[]{arg1});
    }

    public final Object execute(Node inliningTarget, InteropBehavior behavior, InteropBehaviorMethod method, PythonAbstractObject receiver) {
        assert method.extraArguments == 0 : "method must not take arguments";
        return execute(inliningTarget, behavior, method, receiver, PythonUtils.EMPTY_OBJECT_ARRAY);
    }

    public abstract Object execute(Node inliningTarget, InteropBehavior behavior, InteropBehaviorMethod method, PythonAbstractObject receiver, Object[] extraArguments);

    @Specialization(guards = {"behavior.isConstant(method)"})
    static Object getValueConstantBoolean(@SuppressWarnings("unused") Node inliningTarget, InteropBehavior behavior, InteropBehaviorMethod method,
                    @SuppressWarnings("unused") PythonAbstractObject receiver, @SuppressWarnings("unused") Object[] extraArguments) {
        assert behavior.isDefined(method) : "interop behavior method is not defined!";
        return behavior.getConstantValue(method);
    }

    @Specialization(guards = {"!behavior.isConstant(method)", "method.checkArity(extraArguments)"})
    static Object getValue(Node inliningTarget, InteropBehavior behavior, InteropBehaviorMethod method, PythonAbstractObject receiver, Object[] extraArguments,
                    @Cached SimpleInvokeNodeDispatch invokeNode,
                    @Cached(inline = false) GilNode gil,
                    @Cached ConvertJavaStringArguments convertArgs) {
        assert behavior.isDefined(method) : "interop behavior method is not defined!";
        CallTarget callTarget = behavior.getCallTarget(method);
        Object[] pArguments = behavior.createArguments(method, receiver, convertArgs.execute(inliningTarget, extraArguments));
        boolean mustRelease = gil.acquire();
        try {
            return invokeNode.execute(inliningTarget, callTarget, pArguments);
        } finally {
            gil.release(mustRelease);
        }
    }

    @Specialization(guards = {"!behavior.isConstant(method)", "!method.checkArity(extraArguments)"})
    @SuppressWarnings("unused")
    static Object getValueComputedWrongArity(Node inliningTarget, InteropBehavior behavior, InteropBehaviorMethod method, PythonAbstractObject receiver, Object[] extraArguments,
                    @Cached PRaiseNode raiseNode) {
        assert behavior.isDefined(method) : "interop behavior method is not defined!";
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
