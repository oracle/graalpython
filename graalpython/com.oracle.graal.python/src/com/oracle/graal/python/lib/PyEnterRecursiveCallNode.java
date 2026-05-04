/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.RecursionError;

import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.PythonContext.GetThreadStateNode;
import com.oracle.graal.python.runtime.PythonContext.PythonThreadState;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.InlinedBranchProfile;
import com.oracle.truffle.api.strings.TruffleString;

/**
 * Java-side equivalent of the generic CPython recursion check used around recursive dispatch such
 * as {@code PyObject_Repr} and {@code PyObject_Str}; see CPython's {@code _Py_CheckRecursiveCall}
 * and {@code _Py_EnterRecursiveCall}.
 */
@GenerateUncached
@GenerateInline(inlineByDefault = true)
@GenerateCached(false)
public abstract class PyEnterRecursiveCallNode extends PNodeWithContext {
    public abstract PythonThreadState execute(Node inliningTarget, TruffleString errorMessage, Object formatArg, boolean withFormatArg);

    @Specialization
    static PythonThreadState doIt(Node inliningTarget, TruffleString errorMessage, Object formatArg, boolean withFormatArg,
                    @Cached GetThreadStateNode getThreadStateNode,
                    @Cached InlinedBranchProfile errorProfile) {
        PythonContext context = PythonContext.get(inliningTarget);
        PythonThreadState threadState = getThreadStateNode.execute(inliningTarget, context);
        if (++threadState.recursionDepth > context.getSysModuleState().getRecursionLimit()) {
            threadState.recursionDepth--;
            errorProfile.enter(inliningTarget);
            if (withFormatArg) {
                throw PRaiseNode.raiseStatic(inliningTarget, RecursionError, errorMessage, formatArg);
            }
            throw PRaiseNode.raiseStatic(inliningTarget, RecursionError, errorMessage);
        }
        return threadState;
    }

    public final PythonThreadState execute(Node inliningTarget, TruffleString errorMessage) {
        return execute(inliningTarget, errorMessage, null, false);
    }

    public final PythonThreadState execute(Node inliningTarget, TruffleString errorMessage, Object formatArg) {
        return execute(inliningTarget, errorMessage, formatArg, true);
    }

    public static PythonThreadState executeUncached(Node inliningTarget, TruffleString errorMessage) {
        return PyEnterRecursiveCallNodeGen.getUncached().execute(inliningTarget, errorMessage, null, false);
    }

    public static PythonThreadState executeUncached(Node inliningTarget, TruffleString errorMessage, Object formatArg) {
        return PyEnterRecursiveCallNodeGen.getUncached().execute(inliningTarget, errorMessage, formatArg, true);
    }

    public static void leave(PythonThreadState threadState) {
        threadState.recursionDepth--;
    }
}
