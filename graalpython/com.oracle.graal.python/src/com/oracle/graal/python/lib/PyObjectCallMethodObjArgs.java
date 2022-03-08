/*
 * Copyright (c) 2021, 2022, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.lib;

import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.builtins.objects.type.SpecialMethodSlot;
import com.oracle.graal.python.nodes.call.CallNode;
import com.oracle.graal.python.nodes.call.special.CallBinaryMethodNode;
import com.oracle.graal.python.nodes.call.special.CallQuaternaryMethodNode;
import com.oracle.graal.python.nodes.call.special.CallTernaryMethodNode;
import com.oracle.graal.python.nodes.call.special.CallUnaryMethodNode;
import com.oracle.graal.python.nodes.call.special.MaybeBindDescriptorNode.BoundDescriptor;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.ConditionProfile;
import com.oracle.truffle.api.strings.TruffleString;

/**
 * Equivalent to use for the various PyObject_CallMethod* functions available in CPython.
 */
@GenerateUncached
@ImportStatic(SpecialMethodSlot.class)
public abstract class PyObjectCallMethodObjArgs extends Node {

    public final Object execute(Frame frame, Object receiver, TruffleString name, Object... arguments) {
        return executeInternal(frame, receiver, name, arguments);
    }

    protected abstract Object executeInternal(Frame frame, Object receiver, TruffleString name, Object[] arguments);

    @Specialization(guards = "arguments.length == 0")
    static Object callUnary(Frame frame, Object receiver, TruffleString name, @SuppressWarnings("unused") Object[] arguments,
                    @Shared("getMethod") @Cached PyObjectGetMethod getMethod,
                    @Cached CallUnaryMethodNode callNode) {
        Object callable = getMethod.execute(frame, receiver, name);
        return callNode.executeObject(frame, callable, receiver);
    }

    @Specialization(guards = "arguments.length == 1")
    static Object callBinary(Frame frame, Object receiver, TruffleString name, Object[] arguments,
                    @Shared("getMethod") @Cached PyObjectGetMethod getMethod,
                    @Cached CallBinaryMethodNode callNode) {
        Object callable = getMethod.execute(frame, receiver, name);
        return callNode.executeObject(frame, callable, receiver, arguments[0]);
    }

    @Specialization(guards = "arguments.length == 2")
    static Object callTernary(Frame frame, Object receiver, TruffleString name, Object[] arguments,
                    @Shared("getMethod") @Cached PyObjectGetMethod getMethod,
                    @Cached CallTernaryMethodNode callNode) {
        Object callable = getMethod.execute(frame, receiver, name);
        return callNode.execute(frame, callable, receiver, arguments[0], arguments[1]);
    }

    @Specialization(guards = "arguments.length == 3")
    static Object callQuad(Frame frame, Object receiver, TruffleString name, Object[] arguments,
                    @Shared("getMethod") @Cached PyObjectGetMethod getMethod,
                    @Cached CallQuaternaryMethodNode callNode) {
        Object callable = getMethod.execute(frame, receiver, name);
        return callNode.execute(frame, callable, receiver, arguments[0], arguments[1], arguments[2]);
    }

    @Specialization(replaces = {"callUnary", "callBinary", "callTernary", "callQuad"})
    static Object call(Frame frame, Object receiver, TruffleString name, Object[] arguments,
                    @Shared("getMethod") @Cached PyObjectGetMethod getMethod,
                    @Cached CallNode callNode,
                    @Cached ConditionProfile isBoundProfile) {
        Object callable = getMethod.execute(frame, receiver, name);
        if (isBoundProfile.profile(callable instanceof BoundDescriptor)) { // not a method
            return callNode.execute(frame, ((BoundDescriptor) callable).descriptor, arguments, PKeyword.EMPTY_KEYWORDS);
        } else {
            Object[] unboundArguments = new Object[arguments.length + 1];
            unboundArguments[0] = receiver;
            PythonUtils.arraycopy(arguments, 0, unboundArguments, 1, arguments.length);
            return callNode.execute(frame, callable, unboundArguments, PKeyword.EMPTY_KEYWORDS);
        }
    }

    public static PyObjectCallMethodObjArgs create() {
        return PyObjectCallMethodObjArgsNodeGen.create();
    }

    public static PyObjectCallMethodObjArgs getUncached() {
        return PyObjectCallMethodObjArgsNodeGen.getUncached();
    }
}
