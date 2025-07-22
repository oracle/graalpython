/*
 * Copyright (c) 2021, 2025, Oracle and/or its affiliates. All rights reserved.
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
import com.oracle.graal.python.builtins.objects.cext.common.CExtCommonNodes;
import com.oracle.graal.python.builtins.objects.exception.ExceptionNodes;
import com.oracle.graal.python.builtins.objects.exception.PBaseException;
import com.oracle.graal.python.builtins.objects.frame.PFrame;
import com.oracle.graal.python.builtins.objects.traceback.PTraceback;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.object.PFactory;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.strings.TruffleString;

public final class PythonCextTracebackBuiltins {

    @CApiBuiltin(ret = Void, args = {ConstCharPtrAsTruffleString, ConstCharPtrAsTruffleString, Int}, call = Direct)
    abstract static class _PyTraceback_Add extends CApiTernaryBuiltinNode {
        @Specialization
        static Object tbHere(TruffleString funcname, TruffleString filename, int lineno,
                        @Cached PyCode_NewEmpty newCode,
                        @Cached PyTraceBack_Here pyTraceBackHereNode,
                        @Bind PythonLanguage language) {
            PFrame frame = PFactory.createPFrame(language, null, newCode.execute(filename, funcname, lineno), PFactory.createDict(language), PFactory.createDict(language));
            pyTraceBackHereNode.execute(frame);
            return PNone.NONE;
        }
    }

    @CApiBuiltin(ret = Int, args = {PyFrameObject}, call = Direct)
    abstract static class PyTraceBack_Here extends CApiUnaryBuiltinNode {
        @Specialization
        static int tbHere(PFrame frame,
                        @Bind Node inliningTarget,
                        @Bind PythonContext context,
                        @Cached CExtCommonNodes.ReadAndClearNativeException readAndClearNativeException,
                        @Cached CExtCommonNodes.TransformExceptionToNativeNode transformExceptionToNativeNode) {
            PythonLanguage language = context.getLanguage(inliningTarget);
            PythonContext.PythonThreadState threadState = context.getThreadState(language);
            Object currentException = readAndClearNativeException.execute(inliningTarget, threadState);
            if (currentException instanceof PBaseException) {
                Object traceback = ExceptionNodes.GetTracebackNode.executeUncached(currentException);
                PTraceback newTraceback = PFactory.createTraceback(language, frame, frame.getLine(), traceback instanceof PTraceback ptb ? ptb : null);
                ExceptionNodes.SetTracebackNode.executeUncached(currentException, newTraceback);
            }
            transformExceptionToNativeNode.execute(inliningTarget, currentException);
            return 0;
        }
    }
}
