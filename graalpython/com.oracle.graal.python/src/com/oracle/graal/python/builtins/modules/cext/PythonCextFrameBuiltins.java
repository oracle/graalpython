/*
 * Copyright (c) 2023, 2024, Oracle and/or its affiliates. All rights reserved.
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
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.Int;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PyCodeObjectTransfer;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PyFrameObject;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PyFrameObjectTransfer;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PyObjectTransfer;

import com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApiBuiltin;
import com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApiUnaryBuiltinNode;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.frame.FrameBuiltins;
import com.oracle.graal.python.builtins.objects.frame.PFrame;
import com.oracle.graal.python.nodes.frame.GetFrameLocalsNode;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.Node;

public final class PythonCextFrameBuiltins {
    @CApiBuiltin(ret = PyCodeObjectTransfer, args = {PyFrameObject}, call = Direct)
    abstract static class PyFrame_GetCode extends CApiUnaryBuiltinNode {
        @Specialization
        static Object get(PFrame frame,
                        @Cached FrameBuiltins.GetCodeNode getCodeNode) {
            return getCodeNode.executeObject(null, frame);
        }
    }

    @CApiBuiltin(ret = Int, args = {PyFrameObject}, call = Direct)
    abstract static class PyFrame_GetLineNumber extends CApiUnaryBuiltinNode {
        @Specialization
        static int get(PFrame frame) {
            // do not sync location here since we have no VirtualFrame
            return frame.getLine();
        }
    }

    @CApiBuiltin(ret = Int, args = {PyFrameObject}, call = Direct)
    abstract static class PyFrame_GetLasti extends CApiUnaryBuiltinNode {
        @Specialization
        static int get(PFrame frame) {
            return frame.getLasti();
        }
    }

    @CApiBuiltin(ret = PyObjectTransfer, args = {PyFrameObject}, call = Direct)
    abstract static class PyFrame_GetLocals extends CApiUnaryBuiltinNode {
        @Specialization
        static Object get(PFrame frame,
                        @Bind("this") Node inliningTarget,
                        @Cached GetFrameLocalsNode getFrameLocalsNode) {
            return getFrameLocalsNode.execute(inliningTarget, frame);
        }
    }

    @CApiBuiltin(ret = PyObjectTransfer, args = {PyFrameObject}, call = Direct)
    abstract static class PyFrame_GetGlobals extends CApiUnaryBuiltinNode {
        @Specialization
        static Object get(PFrame frame,
                        @Cached FrameBuiltins.GetGlobalsNode getGlobalsNode) {
            return getGlobalsNode.execute(null, frame);
        }
    }

    @CApiBuiltin(ret = PyFrameObjectTransfer, args = {PyFrameObject}, call = Direct)
    abstract static class PyFrame_GetBack extends CApiUnaryBuiltinNode {
        @Specialization
        Object get(PFrame frame,
                        @Cached FrameBuiltins.GetBackrefNode getBackNode) {
            Object back = getBackNode.execute(null, frame);
            if (back == PNone.NONE) {
                return getNativeNull();
            }
            return back;
        }
    }

    @CApiBuiltin(ret = PyObjectTransfer, args = {PyFrameObject}, call = Direct)
    abstract static class PyFrame_GetBuiltins extends CApiUnaryBuiltinNode {
        @Specialization
        static Object get(PFrame frame,
                        @Cached FrameBuiltins.GetBuiltinsNode getBuiltinsNode) {
            return getBuiltinsNode.execute(null, frame);
        }
    }
}
