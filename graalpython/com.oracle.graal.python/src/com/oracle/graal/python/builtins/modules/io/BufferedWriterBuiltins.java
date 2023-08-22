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
package com.oracle.graal.python.builtins.modules.io;

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.PBufferedWriter;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___INIT__;

import java.util.List;

import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.modules.io.IOBaseBuiltins.CheckBoolMethodHelperNode;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.nodes.object.GetClassNode.GetPythonObjectClassNode;
import com.oracle.graal.python.runtime.PosixSupportLibrary;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;

@CoreFunctions(extendClasses = PBufferedWriter)
public final class BufferedWriterBuiltins extends AbstractBufferedIOBuiltins {
    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return BufferedWriterBuiltinsFactory.getFactories();
    }

    @GenerateInline
    @GenerateCached(false)
    public abstract static class BufferedWriterInit extends Node {

        public abstract void execute(VirtualFrame frame, Node inliningTarget, PBuffered self, Object raw, int bufferSize, PythonObjectFactory factory);

        @Specialization
        static void doInit(VirtualFrame frame, @SuppressWarnings("unused") Node ignored, PBuffered self, Object raw, int bufferSize, PythonObjectFactory factory,
                        @Bind("this") Node inliningTarget,
                        @Cached CheckBoolMethodHelperNode checkWritableNode,
                        @Cached BufferedInitNode bufferedInitNode,
                        @Cached GetPythonObjectClassNode getSelfClass,
                        @Cached GetClassNode getRawClass) {
            self.setOK(false);
            self.setDetached(false);
            checkWritableNode.checkWriteable(frame, inliningTarget, raw);
            self.setRaw(raw, isFileIO(self, raw, PBufferedWriter, inliningTarget, getSelfClass, getRawClass));
            bufferedInitNode.execute(frame, inliningTarget, self, bufferSize, factory);
            self.resetWrite();
            self.setPos(0);
            self.setOK(true);
        }

        public static void internalInit(PBuffered self, PFileIO raw, int bufferSize, PythonObjectFactory factory,
                        Object posixSupport,
                        PosixSupportLibrary posixLib) {
            self.setDetached(false);
            self.setRaw(raw, true);
            BufferedInitNode.internalInit(self, bufferSize, factory, posixSupport, posixLib);
            self.resetWrite();
            self.setPos(0);
            self.setOK(true);

        }
    }

    // BufferedWriter(raw[, buffer_size=DEFAULT_BUFFER_SIZE])
    @Builtin(name = J___INIT__, minNumOfPositionalArgs = 2, parameterNames = {"self", "raw", "buffer_size"}, raiseErrorName = "BufferedWriter")
    @GenerateNodeFactory
    public abstract static class InitNode extends PythonBuiltinNode {
        @Specialization
        static Object doIt(VirtualFrame frame, PBuffered self, Object raw, Object bufferSize,
                        @Bind("this") Node inliningTarget,
                        @Cached InitBufferSizeNode initBufferSizeNode,
                        @Cached BufferedWriterInit init,
                        @Cached PythonObjectFactory factory) {
            int size = initBufferSizeNode.execute(frame, inliningTarget, bufferSize);
            init.execute(frame, inliningTarget, self, raw, size, factory);
            return PNone.NONE;
        }
    }
}
