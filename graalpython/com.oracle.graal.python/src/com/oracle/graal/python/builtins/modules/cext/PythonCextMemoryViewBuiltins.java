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

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.BufferError;
import static com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApiCallPath.Direct;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.CHAR;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.Int;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PyObject;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PyObjectTransfer;
import static com.oracle.graal.python.nodes.ErrorMessages.UNDERLYING_BUFFER_IS_NOT_WRITABLE;
import static com.oracle.graal.python.nodes.ErrorMessages.WRITABLE_CONTIGUES_FOR_NON_CONTIGUOUS;
import static com.oracle.graal.python.util.BufferFormat.T_UINT_8_TYPE_CODE;
import static com.oracle.graal.python.util.PythonUtils.TS_ENCODING;

import com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApiBuiltin;
import com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApiTernaryBuiltinNode;
import com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApiUnaryBuiltinNode;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.memoryview.MemoryViewBuiltins.CastNode;
import com.oracle.graal.python.builtins.objects.memoryview.MemoryViewBuiltins.ContiguousNode;
import com.oracle.graal.python.builtins.objects.memoryview.MemoryViewBuiltins.ToBytesNode;
import com.oracle.graal.python.builtins.objects.memoryview.MemoryViewNodes.ReleaseNode;
import com.oracle.graal.python.builtins.objects.memoryview.PMemoryView;
import com.oracle.graal.python.lib.PyMemoryViewFromObject;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.strings.TruffleString;

public final class PythonCextMemoryViewBuiltins {

    public static final int PY_BUF_READ = 0x100;
    public static final int PY_BUF_WRITE = 0x200;

    @CApiBuiltin(ret = PyObjectTransfer, args = {PyObject, Int, CHAR}, call = Direct)
    abstract static class PyMemoryView_GetContiguous extends CApiTernaryBuiltinNode {
        @Specialization()
        static Object get(Object obj, int buffertype, byte orderByte,
                        @Bind("this") Node inliningTarget,
                        @Cached PyMemoryViewFromObject memoryViewFromObject,
                        @Cached ReleaseNode releaseNode,
                        @Cached ToBytesNode toBytesNode,
                        @Cached CastNode castNode,
                        @Cached ContiguousNode contiguousNode,
                        @Cached TruffleString.EqualNode eqNode,
                        @Cached TruffleString.FromCodePointNode fromCodePointNode,
                        @Cached PRaiseNode.Lazy raiseNode) {
            assert buffertype == PY_BUF_READ || buffertype == PY_BUF_WRITE;
            char order = (char) orderByte;
            assert order == 'C' || order == 'F' || order == 'A';

            PMemoryView mv = memoryViewFromObject.execute(null, obj);
            boolean release = true;
            try {
                if (buffertype == PY_BUF_WRITE && mv.isReadOnly()) {
                    throw raiseNode.get(inliningTarget).raise(BufferError, UNDERLYING_BUFFER_IS_NOT_WRITABLE);
                }
                if ((boolean) contiguousNode.execute(null, mv)) {
                    release = false;
                    return mv;
                }
                if (buffertype == PY_BUF_WRITE) {
                    throw raiseNode.get(inliningTarget).raise(BufferError, WRITABLE_CONTIGUES_FOR_NON_CONTIGUOUS);
                }
                PMemoryView mvBytes = memoryViewFromObject.execute(null, toBytesNode.execute(null, mv, fromCodePointNode.execute(order, TS_ENCODING, true)));
                if (eqNode.execute(T_UINT_8_TYPE_CODE, mv.getFormatString(), TS_ENCODING)) {
                    return mvBytes;
                } else {
                    try {
                        return castNode.execute(null, mvBytes, mv.getFormatString(), PNone.NONE);
                    } finally {
                        releaseNode.execute(mvBytes);
                    }
                }
            } finally {
                if (release && mv != null) {
                    releaseNode.execute(mv);
                }
            }
        }
    }

    @CApiBuiltin(ret = PyObjectTransfer, args = {PyObject}, call = Direct)
    abstract static class PyMemoryView_FromObject extends CApiUnaryBuiltinNode {
        @Specialization
        static Object wrap(Object object,
                        @Cached PyMemoryViewFromObject memoryViewNode) {
            return memoryViewNode.execute(null, object);
        }
    }
}
