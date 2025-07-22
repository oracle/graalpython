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
package com.oracle.graal.python.builtins.modules.io;

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.PBufferedRandom;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.PBufferedWriter;
import static com.oracle.graal.python.builtins.modules.io.IONodes.J_FLUSH;
import static com.oracle.graal.python.builtins.modules.io.IONodes.J_WRITABLE;
import static com.oracle.graal.python.builtins.modules.io.IONodes.J_WRITE;
import static com.oracle.graal.python.builtins.modules.io.IONodes.T_WRITABLE;

import java.util.List;

import com.oracle.graal.python.annotations.ArgumentClinic;
import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.modules.io.BufferedIONodes.CheckIsClosedNode;
import com.oracle.graal.python.builtins.modules.io.BufferedIONodes.EnterBufferedNode;
import com.oracle.graal.python.builtins.modules.io.BufferedIONodes.FlushAndRewindUnlockedNode;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.buffer.PythonBufferAccessLibrary;
import com.oracle.graal.python.lib.PyObjectCallMethodObjArgs;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.builtins.clinic.ArgumentClinicProvider;
import com.oracle.graal.python.runtime.IndirectCallData;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.Node;

@CoreFunctions(extendClasses = {PBufferedWriter, PBufferedRandom})
public final class BufferedWriterMixinBuiltins extends AbstractBufferedIOBuiltins {
    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return BufferedWriterMixinBuiltinsFactory.getFactories();
    }

    @Builtin(name = J_WRITABLE, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class WritableNode extends PythonUnaryWithInitErrorBuiltinNode {
        @Specialization(guards = "self.isOK()")
        static Object doit(VirtualFrame frame, PBuffered self,
                        @Bind Node inliningTarget,
                        @Cached PyObjectCallMethodObjArgs callMethod) {
            return callMethod.execute(frame, inliningTarget, self.getRaw(), T_WRITABLE);
        }
    }

    @Builtin(name = J_WRITE, minNumOfPositionalArgs = 1, parameterNames = {"$self", "buffer"})
    @ArgumentClinic(name = "buffer", conversion = ArgumentClinic.ClinicConversion.ReadableBuffer)
    @ImportStatic(IONodes.class)
    @GenerateNodeFactory
    abstract static class WriteNode extends PythonBinaryWithInitErrorClinicBuiltinNode {

        @Specialization(guards = "self.isOK()")
        static Object write(@SuppressWarnings("unused") VirtualFrame frame, PBuffered self, Object buffer,
                        @Bind Node inliningTarget,
                        @Cached("createFor($node)") IndirectCallData indirectCallData,
                        @CachedLibrary(limit = "3") PythonBufferAccessLibrary bufferLib,
                        @Cached EnterBufferedNode lock,
                        @Cached("create(T_WRITE)") CheckIsClosedNode checkIsClosedNode,
                        @Cached BufferedWriterNodes.WriteNode writeNode) {
            try {
                lock.enter(inliningTarget, self);
                checkIsClosedNode.execute(frame, self);
                return writeNode.execute(frame, inliningTarget, self, buffer);
            } finally {
                bufferLib.release(buffer, frame, indirectCallData);
                EnterBufferedNode.leave(self);
            }
        }

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return BufferedWriterMixinBuiltinsClinicProviders.WriteNodeClinicProviderGen.INSTANCE;
        }
    }

    @Builtin(name = J_FLUSH, minNumOfPositionalArgs = 1)
    @ImportStatic(IONodes.class)
    @GenerateNodeFactory
    abstract static class FlushNode extends PythonUnaryWithInitErrorBuiltinNode {

        @Specialization(guards = "self.isOK()")
        static Object doit(VirtualFrame frame, PBuffered self,
                        @Bind Node inliningTarget,
                        @Cached EnterBufferedNode lock,
                        @Cached("create(T_FLUSH)") CheckIsClosedNode checkIsClosedNode,
                        @Cached FlushAndRewindUnlockedNode flushAndRewindUnlockedNode) {
            checkIsClosedNode.execute(frame, self);
            try {
                lock.enter(inliningTarget, self);
                flushAndRewindUnlockedNode.execute(frame, inliningTarget, self);
            } finally {
                EnterBufferedNode.leave(self);
            }
            return PNone.NONE;
        }
    }
}
