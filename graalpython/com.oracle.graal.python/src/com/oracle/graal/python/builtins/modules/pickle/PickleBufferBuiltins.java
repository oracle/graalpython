/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.modules.pickle;

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.BufferError;

import java.util.List;

import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.buffer.PythonBufferAccessLibrary;
import com.oracle.graal.python.builtins.objects.memoryview.MemoryViewNodes;
import com.oracle.graal.python.builtins.objects.memoryview.PMemoryView;
import com.oracle.graal.python.lib.PyMemoryViewFromObject;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.graal.python.util.BufferFormat;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.Node;

@CoreFunctions(extendClasses = PythonBuiltinClassType.PickleBuffer)
public class PickleBufferBuiltins extends PythonBuiltins {
    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return PickleBufferBuiltinsFactory.getFactories();
    }

    // functions
    @Builtin(name = "raw", minNumOfPositionalArgs = 1, parameterNames = {"$self"})
    @GenerateNodeFactory
    public abstract static class PickleBufferRawNode extends PythonUnaryBuiltinNode {
        @Specialization
        Object raw(VirtualFrame frame, PPickleBuffer self,
                        @Bind("this") Node inliningTarget,
                        @Cached PyMemoryViewFromObject memoryViewFromObject,
                        @Cached MemoryViewNodes.ReleaseNode releaseNode,
                        @Cached PythonObjectFactory factory,
                        @Cached PRaiseNode.Lazy raiseNode) {
            final Object view = self.getView();
            if (view == null) {
                throw raiseNode.get(inliningTarget).raise(PythonBuiltinClassType.ValueError, ErrorMessages.OP_FORBIDDEN_ON_OBJECT, "PickleBuffer");
            }
            PMemoryView mv = memoryViewFromObject.execute(frame, self);
            // Make it into raw (1-dimensional bytes) memoryview
            try {
                if (!mv.isAnyContiguous()) {
                    throw raiseNode.get(inliningTarget).raise(BufferError, ErrorMessages.CANNOT_EXTRACT_RAW_BUFFER_FROM_NON_CONTIGUOUS);
                }
                int[] shape = new int[]{mv.getLength()};
                int[] strides = new int[]{1};
                return factory.createMemoryView(getContext(), mv.getLifecycleManager(), mv.getBuffer(), mv.getOwner(), mv.getLength(),
                                mv.isReadOnly(), 1, BufferFormat.UINT_8, BufferFormat.T_UINT_8_TYPE_CODE, 1,
                                mv.getBufferPointer(), mv.getOffset(), shape, strides,
                                null, PMemoryView.FLAG_C | PMemoryView.FLAG_FORTRAN);
            } finally {
                releaseNode.execute(frame, mv);
            }
        }
    }

    @Builtin(name = "release", minNumOfPositionalArgs = 1, parameterNames = {"$self"})
    @GenerateNodeFactory
    public abstract static class PickleBufferReleaseNode extends PythonUnaryBuiltinNode {
        @Specialization
        Object release(PPickleBuffer self,
                        @CachedLibrary(limit = "3") PythonBufferAccessLibrary bufferLib) {
            Object view = self.getView();
            if (view != null) {
                bufferLib.release(view);
                self.release();
            }
            return PNone.NONE;
        }
    }
}
