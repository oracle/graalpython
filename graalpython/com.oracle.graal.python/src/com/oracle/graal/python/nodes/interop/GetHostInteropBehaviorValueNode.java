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

import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.PythonAbstractObject;
import com.oracle.graal.python.builtins.objects.polyglot.PHostInteropBehavior;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.call.GenericInvokeNode;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.runtime.GilNode;
import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.NeverDefault;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.object.DynamicObject;
import com.oracle.truffle.api.object.DynamicObjectLibrary;
import com.oracle.truffle.api.profiles.InlinedConditionProfile;

@GenerateUncached
@GenerateInline
@SuppressWarnings("truffle-inlining") // some of the cached nodes in the specialization are not
                                      // inlineable
public abstract class GetHostInteropBehaviorValueNode extends PNodeWithContext {
    public abstract Object execute(Node inlineTarget, PythonAbstractObject receiver, HostInteropBehaviorMethod method);

    @Specialization(guards = {"method.constantBoolean == true"})
    static Object getValueConstantBoolean(Node inlineTarget, PythonAbstractObject receiver, HostInteropBehaviorMethod method,
                    @Shared @Cached GetClassNode getClassNode,
                    @Shared @Cached InlinedConditionProfile isMethodDefined,
                    @Shared @CachedLibrary(limit = "1") DynamicObjectLibrary dylib) {
        Object klass = getClassNode.execute(inlineTarget, receiver);
        Object value = dylib.getOrDefault((DynamicObject) klass, HOST_INTEROP_BEHAVIOR, null);
        if (value instanceof PHostInteropBehavior behavior && isMethodDefined.profile(inlineTarget, behavior.isDefined(method))) {
            return behavior.getConstantValue(method);
        }
        return PNone.NO_VALUE;
    }

    @Specialization(guards = {"method.constantBoolean == false"})
    static Object getValueComputed(Node inlineTarget, PythonAbstractObject receiver, HostInteropBehaviorMethod method,
                    @Cached GenericInvokeNode invokeNode,
                    @Shared @Cached GetClassNode getClassNode,
                    @Shared @Cached InlinedConditionProfile isMethodDefined,
                    @Cached GilNode gil,
                    @Shared @CachedLibrary(limit = "1") DynamicObjectLibrary dylib) {
        Object klass = getClassNode.execute(inlineTarget, receiver);
        Object value = dylib.getOrDefault((DynamicObject) klass, HOST_INTEROP_BEHAVIOR, null);
        if (value instanceof PHostInteropBehavior behavior && isMethodDefined.profile(inlineTarget, behavior.isDefined(method))) {
            CallTarget callTarget = behavior.getCallTarget(method);
            Object[] pArguments = behavior.createArguments(method, receiver);
            boolean mustRelease = gil.acquire();
            try {
                return invokeNode.execute(callTarget, pArguments);
            } finally {
                gil.release(mustRelease);
            }
        }
        return PNone.NO_VALUE;
    }

    @NeverDefault
    public static GetHostInteropBehaviorValueNode create() {
        return GetHostInteropBehaviorValueNodeGen.create();
    }

    public static GetHostInteropBehaviorValueNode getUncached() {
        return GetHostInteropBehaviorValueNodeGen.getUncached();
    }
}
