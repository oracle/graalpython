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
package com.oracle.graal.python.builtins.modules.cext;

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.BufferError;
import static com.oracle.graal.python.nodes.ErrorMessages.UNDERLYING_BUFFER_IS_NOT_WRITABLE;
import static com.oracle.graal.python.nodes.ErrorMessages.WRITABLE_CONTIGUES_FOR_NON_CONTIGUOUS;

import com.oracle.graal.python.builtins.Builtin;
import java.util.List;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.Python3Core;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.PRaiseNativeNode;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.TransformExceptionToNativeNode;
import com.oracle.graal.python.builtins.objects.memoryview.MemoryViewBuiltins.CastNode;
import com.oracle.graal.python.builtins.objects.memoryview.MemoryViewBuiltins.ContiguousNode;
import com.oracle.graal.python.builtins.objects.memoryview.MemoryViewBuiltins.ToBytesNode;
import com.oracle.graal.python.builtins.objects.memoryview.MemoryViewNodes.ReleaseNode;
import com.oracle.graal.python.builtins.objects.memoryview.PMemoryView;
import com.oracle.graal.python.lib.PyMemoryViewFromObject;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.builtins.PythonTernaryBuiltinNode;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;

@CoreFunctions(extendsModule = PythonCextBuiltins.PYTHON_CEXT)
@GenerateNodeFactory
public final class PythonCextMemoryViewBuiltins extends PythonBuiltins {

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return PythonCextMemoryViewBuiltinsFactory.getFactories();
    }

    @Override
    public void initialize(Python3Core core) {
        super.initialize(core);
    }

    public static int PY_BUF_READ = 0x100;
    public static int PY_BUF_WRITE = 0x200;

    @Builtin(name = "PyMemoryView_GetContiguous", minNumOfPositionalArgs = 3)
    @GenerateNodeFactory
    public abstract static class PyMemoryViewGetContiguousNode extends PythonTernaryBuiltinNode {
        @Specialization()
        public Object get(VirtualFrame frame, Object obj, int buffertype, int orderInt,
                        @Cached PyMemoryViewFromObject memoryViewFromObject,
                        @Cached ReleaseNode releaseNode,
                        @Cached ToBytesNode toBytesNode,
                        @Cached CastNode castNode,
                        @Cached ContiguousNode contiguousNode,
                        @Cached PRaiseNativeNode raiseNativeNode,
                        @Cached TransformExceptionToNativeNode transformExceptionToNativeNode) {
            assert buffertype == PY_BUF_READ || buffertype == PY_BUF_WRITE;
            char order = (char) orderInt;
            assert order == 'C' || order == 'F' || order == 'A';

            try {
                PMemoryView mv = memoryViewFromObject.execute(frame, obj);
                boolean release = true;
                try {
                    if (buffertype == PY_BUF_WRITE && mv.isReadOnly()) {
                        return raiseNativeNode.raise(frame, getContext().getNativeNull(), BufferError, UNDERLYING_BUFFER_IS_NOT_WRITABLE);
                    }
                    if ((boolean) contiguousNode.execute(frame, mv)) {
                        release = false;
                        return mv;
                    }
                    if (buffertype == PY_BUF_WRITE) {
                        return raiseNativeNode.raise(frame, getContext().getNativeNull(), BufferError, WRITABLE_CONTIGUES_FOR_NON_CONTIGUOUS);
                    }
                    PMemoryView mvBytes = memoryViewFromObject.execute(frame, toBytesNode.execute(frame, mv, Character.toString(order)));
                    if ("B".equals(mv.getFormatString())) {
                        return mvBytes;
                    } else {
                        try {
                            return castNode.execute(frame, mvBytes, mv.getFormatString(), PNone.NONE);
                        } finally {
                            releaseNode.execute(frame, mvBytes);
                        }
                    }
                } finally {
                    if (release && mv != null) {
                        releaseNode.execute(frame, mv);
                    }
                }
            } catch (PException e) {
                transformExceptionToNativeNode.execute(e);
                return getContext().getNativeNull();
            }
        }
    }

    @Builtin(name = "PyMemoryView_FromObject", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class PyTruffleMemoryViewFromObject extends PythonCextBuiltins.NativeBuiltin {
        @Specialization
        Object wrap(VirtualFrame frame, Object object,
                        @Cached PyMemoryViewFromObject memoryViewNode) {
            try {
                return memoryViewNode.execute(frame, object);
            } catch (PException e) {
                transformToNative(frame, e);
                return getContext().getNativeNull();
            }
        }
    }
}
