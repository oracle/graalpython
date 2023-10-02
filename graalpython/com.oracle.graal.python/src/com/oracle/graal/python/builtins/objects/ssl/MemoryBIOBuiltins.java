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
package com.oracle.graal.python.builtins.objects.ssl;

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.MemoryError;
import static com.oracle.graal.python.nodes.ErrorMessages.SSL_CANNOT_WRITE_AFTER_EOF;

import java.util.List;

import com.oracle.graal.python.annotations.ArgumentClinic;
import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.buffer.PythonBufferAccessLibrary;
import com.oracle.graal.python.builtins.objects.bytes.PBytes;
import com.oracle.graal.python.nodes.PConstructAndRaiseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.clinic.ArgumentClinicProvider;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.graal.python.util.OverflowException;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.Node;

@CoreFunctions(extendClasses = PythonBuiltinClassType.PMemoryBIO)
public final class MemoryBIOBuiltins extends PythonBuiltins {
    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return MemoryBIOBuiltinsFactory.getFactories();
    }

    @Builtin(name = "MemoryBIO", constructsClass = PythonBuiltinClassType.PMemoryBIO, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class MemoryBIONode extends PythonUnaryBuiltinNode {
        @Specialization
        static PMemoryBIO create(Object type,
                        @Cached PythonObjectFactory factory) {
            return factory.createMemoryBIO(type);
        }
    }

    @Builtin(name = "pending", minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    abstract static class PendingNode extends PythonUnaryBuiltinNode {
        @Specialization
        static int getPending(PMemoryBIO self) {
            return self.getPending();
        }
    }

    @Builtin(name = "eof", minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    abstract static class EOFNode extends PythonUnaryBuiltinNode {
        @Specialization
        static boolean eof(PMemoryBIO self) {
            return self.isEOF();
        }
    }

    @Builtin(name = "read", minNumOfPositionalArgs = 1, numOfPositionalOnlyArgs = 1, parameterNames = {"$self", "size"})
    @ArgumentClinic(name = "size", conversion = ArgumentClinic.ClinicConversion.Int, defaultValue = "-1")
    @GenerateNodeFactory
    abstract static class ReadNode extends PythonBinaryClinicBuiltinNode {
        @Specialization
        static PBytes read(PMemoryBIO self, int size,
                        @Cached PythonObjectFactory factory) {
            int len = size >= 0 ? size : Integer.MAX_VALUE;
            return factory.createBytes(self.read(len));
        }

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return MemoryBIOBuiltinsClinicProviders.ReadNodeClinicProviderGen.INSTANCE;
        }
    }

    @Builtin(name = "write", minNumOfPositionalArgs = 2, numOfPositionalOnlyArgs = 2, parameterNames = {"$self", "buffer"})
    @ArgumentClinic(name = "buffer", conversion = ArgumentClinic.ClinicConversion.ReadableBuffer)
    @GenerateNodeFactory
    abstract static class WriteNode extends PythonBinaryClinicBuiltinNode {
        @Specialization(limit = "3")
        @SuppressWarnings("truffle-static-method")
        int write(VirtualFrame frame, PMemoryBIO self, Object buffer,
                        @Bind("this") Node inliningTarget,
                        @Cached PConstructAndRaiseNode.Lazy constructAndRaiseNode,
                        @CachedLibrary("buffer") PythonBufferAccessLibrary bufferLib) {
            try {
                if (self.didWriteEOF()) {
                    throw constructAndRaiseNode.get(inliningTarget).raiseSSLError(frame, SSL_CANNOT_WRITE_AFTER_EOF);
                }
                try {
                    byte[] bytes = bufferLib.getInternalOrCopiedByteArray(buffer);
                    int len = bufferLib.getBufferLength(buffer);
                    self.write(bytes, len);
                    return len;
                } catch (OverflowException | OutOfMemoryError e) {
                    throw raise(MemoryError);
                }
            } finally {
                bufferLib.release(buffer, frame, this);
            }
        }

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return MemoryBIOBuiltinsClinicProviders.WriteNodeClinicProviderGen.INSTANCE;
        }
    }

    @Builtin(name = "write_eof", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class WriteEOFNode extends PythonUnaryBuiltinNode {
        @Specialization
        static PNone writeEOF(PMemoryBIO self) {
            self.writeEOF();
            return PNone.NONE;
        }
    }
}
