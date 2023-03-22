/*
 * Copyright (c) 2021, 2023, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.TypeError;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.ValueError;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T_FILENO;

import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.call.CallNode;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;

/**
 * Equivalent of CPython's {@code PyObject_AsFileDescriptor}.
 */
@GenerateUncached
@GenerateInline
@GenerateCached(false)
public abstract class PyObjectAsFileDescriptor extends PNodeWithContext {
    public abstract int execute(Frame frame, Node inliningTarget, Object object);

    @Specialization
    static int doInt(@SuppressWarnings("unused") int object,
                    @Shared("raise") @Cached(inline = false) PRaiseNode raise) {
        return checkResult(object, raise);
    }

    @Specialization(guards = "longCheckNode.execute(inliningTarget, object)", limit = "1")
    static int doPyLong(VirtualFrame frame, @SuppressWarnings("unused") Node inliningTarget, Object object,
                    @SuppressWarnings("unused") @Exclusive @Cached PyLongCheckNode longCheckNode,
                    @Shared("asInt") @Cached PyLongAsIntNode asIntNode,
                    @Shared("raise") @Cached(inline = false) PRaiseNode raise) {
        return checkResult(asIntNode.execute(frame, inliningTarget, object), raise);
    }

    @Fallback
    static int doNotLong(VirtualFrame frame, Node inliningTarget, Object object,
                    @Cached PyObjectLookupAttr lookupFileno,
                    @Cached(inline = false) CallNode callFileno,
                    @Exclusive @Cached PyLongCheckNode checkResultNode,
                    @Shared("asInt") @Cached PyLongAsIntNode asIntNode,
                    @Shared("raise") @Cached(inline = false) PRaiseNode raise) {
        Object filenoMethod = lookupFileno.execute(frame, inliningTarget, object, T_FILENO);
        if (filenoMethod != PNone.NO_VALUE) {
            Object result = callFileno.execute(frame, filenoMethod);
            if (checkResultNode.execute(inliningTarget, result)) {
                return checkResult(asIntNode.execute(frame, inliningTarget, result), raise);
            }
            throw raise.raise(TypeError, ErrorMessages.RETURNED_NON_INTEGER, "fileno()");
        }
        throw raise.raise(TypeError, ErrorMessages.ARG_MUST_BE_INT_OR_HAVE_FILENO_METHOD);
    }

    private static int checkResult(int result, PRaiseNode raiseNode) {
        if (result < 0) {
            throw raiseNode.raise(ValueError, ErrorMessages.S_CANNOT_BE_NEGATIVE_INTEGER_D, "file descriptor", result);
        }
        return result;
    }
}
