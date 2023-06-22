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
package com.oracle.graal.python.builtins.modules.cext;

import static com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApiCallPath.Direct;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.ConstCharPtrAsTruffleString;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.Int;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PyFrameObject;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.Void;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApiBuiltin;
import com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApiTernaryBuiltinNode;
import com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApiUnaryBuiltinNode;
import com.oracle.graal.python.builtins.modules.cext.PythonCextCodeBuiltins.PyCode_NewEmpty;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.frame.PFrame;
import com.oracle.graal.python.builtins.objects.traceback.MaterializeLazyTracebackNode;
import com.oracle.graal.python.builtins.objects.traceback.PTraceback;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.PythonOptions;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.strings.TruffleString;

public final class PythonCextTracebackBuiltins {

    @CApiBuiltin(ret = Void, args = {ConstCharPtrAsTruffleString, ConstCharPtrAsTruffleString, Int}, call = Direct)
    abstract static class _PyTraceback_Add extends CApiTernaryBuiltinNode {
        @Specialization
        Object tbHere(TruffleString funcname, TruffleString filename, int lineno,
                        @Cached PyCode_NewEmpty newCode,
                        @Cached PyTraceBack_Here pyTraceBackHereNode) {
            PFrame frame = factory().createPFrame(null, newCode.execute(filename, funcname, lineno), factory().createDict(), factory().createDict());
            pyTraceBackHereNode.execute(frame);
            return PNone.NONE;
        }
    }

    @CApiBuiltin(ret = Int, args = {PyFrameObject}, call = Direct)
    abstract static class PyTraceBack_Here extends CApiUnaryBuiltinNode {
        @Specialization
        int tbHere(PFrame frame,
                        @Cached MaterializeLazyTracebackNode materializeLazyTracebackNode) {
            PythonLanguage language = getLanguage();
            PythonContext.PythonThreadState threadState = getContext().getThreadState(language);
            PException currentException = threadState.getCurrentException();
            if (currentException != null) {
                PTraceback traceback = null;
                if (currentException.getTraceback() != null) {
                    traceback = materializeLazyTracebackNode.execute(currentException.getTraceback());
                }
                PTraceback newTraceback = factory().createTraceback(frame, frame.getLine(), traceback);
                boolean withJavaStacktrace = PythonOptions.isPExceptionWithJavaStacktrace(language);
                threadState.setCurrentException(PException.fromExceptionInfo(currentException.getUnreifiedException(), newTraceback, withJavaStacktrace));
            }

            return 0;
        }
    }
}
