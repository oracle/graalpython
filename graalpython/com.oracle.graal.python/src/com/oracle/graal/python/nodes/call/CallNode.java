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
package com.oracle.graal.python.nodes.call;

import com.oracle.graal.python.builtins.objects.function.PBuiltinFunction;
import com.oracle.graal.python.builtins.objects.function.PFunction;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.builtins.objects.function.PythonCallable;
import com.oracle.graal.python.builtins.objects.method.PBuiltinMethod;
import com.oracle.graal.python.builtins.objects.method.PMethod;
import com.oracle.graal.python.nodes.PBaseNode;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.SpecialMethodNames;
import com.oracle.graal.python.nodes.argument.CreateArgumentsNode;
import com.oracle.graal.python.nodes.argument.positional.PositionalArgumentsNode;
import com.oracle.graal.python.nodes.attributes.LookupInheritedAttributeNode;
import com.oracle.graal.python.nodes.call.special.CallVarargsMethodNode;
import com.oracle.graal.python.nodes.truffle.PythonTypes;
import com.oracle.graal.python.runtime.exception.PythonErrorType;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.TypeSystemReference;

@TypeSystemReference(PythonTypes.class)
@ImportStatic({PGuards.class, SpecialMethodNames.class})
public abstract class CallNode extends PBaseNode {
    public static CallNode create() {
        return CallNodeGen.create();
    }

    @Child CreateArgumentsNode createArguments = CreateArgumentsNode.create();
    @Child CallDispatchNode dispatch = CallDispatchNode.create();

    public abstract Object execute(Object callableObject, Object[] arguments, PKeyword[] keywords);

    protected static boolean isNoCallable(Object callee) {
        return !(callee instanceof PythonCallable);
    }

    @Specialization(guards = {"isNoCallable(callableObject) || isClass(callableObject)"})
    protected Object specialCall(Object callableObject, Object[] arguments, PKeyword[] keywords,
                    @Cached("create(__CALL__)") LookupInheritedAttributeNode callAttrGetterNode,
                    @Cached("create()") CallVarargsMethodNode callCallNode) {
        Object call = callAttrGetterNode.execute(callableObject);
        if (isNoCallable(call)) {
            CompilerDirectives.transferToInterpreter();
            throw raise(PythonErrorType.TypeError, "'%p' object is not callable", callableObject);
        }
        return callCallNode.execute(call, PositionalArgumentsNode.prependArgument(callableObject, arguments, arguments.length), keywords);
    }

    @Specialization
    protected Object methodCall(PMethod callable, Object[] arguments, PKeyword[] keywords) {
        return dispatch.executeCall(callable, createArguments.executeWithSelf(callable.getSelf(), arguments), keywords);
    }

    @Specialization
    protected Object builtinMethodCall(PBuiltinMethod callable, Object[] arguments, PKeyword[] keywords) {
        return dispatch.executeCall(callable, createArguments.executeWithSelf(callable.getSelf(), arguments), keywords);
    }

    @Specialization
    protected Object functionCall(PFunction callable, Object[] arguments, PKeyword[] keywords) {
        return dispatch.executeCall(callable, createArguments.execute(arguments), keywords);
    }

    @Specialization
    protected Object builtinFunctionCall(PBuiltinFunction callable, Object[] arguments, PKeyword[] keywords) {
        return dispatch.executeCall(callable, createArguments.execute(arguments), keywords);
    }
}
