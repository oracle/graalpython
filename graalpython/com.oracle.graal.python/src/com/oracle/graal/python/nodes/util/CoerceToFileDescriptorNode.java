/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.nodes.util;

import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.ints.PInt;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.SpecialMethodNames;
import com.oracle.graal.python.nodes.attributes.GetAttributeNode.GetFixedAttributeNode;
import com.oracle.graal.python.nodes.call.CallNode;
import com.oracle.graal.python.nodes.object.GetLazyClassNode;
import com.oracle.graal.python.nodes.object.IsBuiltinClassProfile;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.profiles.BranchProfile;

/**
 * Coerces a given primitive or object to a file descriptor (i.e. Java {@code int}). This node
 * follows the semantics of CPython's function {@code PyObject_AsFileDescriptor}.
 */
public abstract class CoerceToFileDescriptorNode extends PNodeWithContext {

    private static final String ERROR_MESSAGE = "Python int too large to convert to int";

    public abstract int execute(VirtualFrame frame, Object x);

    public abstract int executeInt(VirtualFrame frame, int x);

    public abstract int executeLong(VirtualFrame frame, long x);

    @Specialization
    static int doInt(int x) {
        return x;
    }

    @Specialization(replaces = "doInt")
    static int doLong(long x,
                    @Shared("raiseNode") @Cached PRaiseNode raiseNode,
                    @Exclusive @Cached CastToJavaIntNode castToJavaIntNode) {
        try {
            return castToJavaIntNode.execute(x);
        } catch (PException e) {
            // we need to convert the TypeError to an OverflowError
            throw raiseNode.raise(PythonBuiltinClassType.OverflowError, ERROR_MESSAGE);
        }
    }

    @Specialization(replaces = {"doInt", "doLong"})
    static int doPInt(PInt x,
                    @Shared("raiseNode") @Cached PRaiseNode raiseNode,
                    @Exclusive @Cached CastToJavaIntNode castToJavaIntNode) {
        try {
            return castToJavaIntNode.execute(x);
        } catch (PException e) {
            throw raiseNode.raise(PythonBuiltinClassType.OverflowError, ERROR_MESSAGE);
        }
    }

    @Specialization(replaces = {"doInt", "doLong", "doPInt"})
    static int doGeneric(VirtualFrame frame, Object x,
                    @Exclusive @Cached CastToJavaIntNode castToJavaIntNode,
                    @Cached("createFileno()") GetFixedAttributeNode getFixedAttributeNode,
                    @Cached BranchProfile noFilenoMethodProfile,
                    @Cached CallNode callFilenoNode,
                    @Cached GetLazyClassNode getClassNode,
                    @Cached IsBuiltinClassProfile isIntProfile,
                    @Shared("raiseNode") @Cached PRaiseNode raiseNode) {
        try {
            if (x instanceof Integer || x instanceof Long || x instanceof PInt) {
                return castToJavaIntNode.execute(x);
            }
        } catch (PException e) {
            throw raiseNode.raise(PythonBuiltinClassType.OverflowError, ERROR_MESSAGE);
        }

        Object attrFileno = getFixedAttributeNode.executeObject(frame, x);
        if (attrFileno == PNone.NO_VALUE) {
            noFilenoMethodProfile.enter();
            throw raiseNode.raise(PythonBuiltinClassType.TypeError, "argument must be an int, or have a fileno() method.");
        }

        Object result = callFilenoNode.execute(frame, attrFileno);
        if (isIntProfile.profileClass(getClassNode.execute(result), PythonBuiltinClassType.PInt)) {
            try {
                return castToJavaIntNode.execute(result);
            } catch (PException e) {
                throw raiseNode.raise(PythonBuiltinClassType.OverflowError, ERROR_MESSAGE);
            }
        } else {
            throw raiseNode.raise(PythonBuiltinClassType.TypeError, "fileno() returned a non-integer");
        }
    }

    static GetFixedAttributeNode createFileno() {
        return GetFixedAttributeNode.create(SpecialMethodNames.FILENO);
    }
}
