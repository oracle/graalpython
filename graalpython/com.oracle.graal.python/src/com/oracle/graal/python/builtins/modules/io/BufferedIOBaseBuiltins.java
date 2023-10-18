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

import static com.oracle.graal.python.builtins.modules.io.IONodes.J_DETACH;
import static com.oracle.graal.python.builtins.modules.io.IONodes.J_READ;
import static com.oracle.graal.python.builtins.modules.io.IONodes.J_READ1;
import static com.oracle.graal.python.builtins.modules.io.IONodes.J_READINTO;
import static com.oracle.graal.python.builtins.modules.io.IONodes.J_READINTO1;
import static com.oracle.graal.python.builtins.modules.io.IONodes.J_WRITE;
import static com.oracle.graal.python.builtins.modules.io.IONodes.T_DETACH;
import static com.oracle.graal.python.builtins.modules.io.IONodes.T_READ;
import static com.oracle.graal.python.builtins.modules.io.IONodes.T_READ1;
import static com.oracle.graal.python.builtins.modules.io.IONodes.T_WRITE;
import static com.oracle.graal.python.nodes.ErrorMessages.S_RETURNED_TOO_MUCH_DATA;
import static com.oracle.graal.python.nodes.ErrorMessages.S_SHOULD_RETURN_BYTES;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.IOUnsupportedOperation;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.ValueError;

import java.util.List;

import com.oracle.graal.python.annotations.ArgumentClinic;
import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.buffer.PythonBufferAccessLibrary;
import com.oracle.graal.python.builtins.objects.bytes.PBytes;
import com.oracle.graal.python.lib.PyObjectCallMethodObjArgs;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.clinic.ArgumentClinicProvider;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.InlinedConditionProfile;
import com.oracle.truffle.api.strings.TruffleString;

@CoreFunctions(extendClasses = PythonBuiltinClassType.PBufferedIOBase)
public final class BufferedIOBaseBuiltins extends PythonBuiltins {

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return BufferedIOBaseBuiltinsFactory.getFactories();
    }

    @Builtin(name = J_DETACH, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class DetachNode extends PythonBuiltinNode {

        /**
         * implementation of cpython/Modules/_io/bufferedio.h:_io__BufferedIOBase_detach
         */
        @Specialization
        static Object detach(@SuppressWarnings("unused") Object self,
                        @Cached PRaiseNode raiseNode) {
            throw raiseNode.raise(IOUnsupportedOperation, T_DETACH);
        }
    }

    @Builtin(name = J_READ, minNumOfPositionalArgs = 1, takesVarArgs = true)
    @GenerateNodeFactory
    abstract static class ReadNode extends PythonBuiltinNode {

        /**
         * implementation of cpython/Modules/_io/bufferedio.c:bufferediobase_read
         */
        @SuppressWarnings("unused")
        @Specialization
        static Object read(Object self, Object args,
                        @Cached PRaiseNode raiseNode) {
            throw raiseNode.raise(IOUnsupportedOperation, T_READ);
        }
    }

    @Builtin(name = J_READ1, minNumOfPositionalArgs = 1, takesVarArgs = true)
    @GenerateNodeFactory
    abstract static class Read1Node extends PythonBuiltinNode {

        /**
         * implementation of cpython/Modules/_io/bufferedio.c:bufferediobase_read1
         */
        @SuppressWarnings("unused")
        @Specialization
        static Object read1(Object self, Object args,
                        @Cached PRaiseNode raiseNode) {
            throw raiseNode.raise(IOUnsupportedOperation, T_READ1);
        }
    }

    abstract static class ReadIntoGenericNode extends PythonBinaryClinicBuiltinNode {

        @SuppressWarnings("unused")
        protected TruffleString getMethodName() {
            throw CompilerDirectives.shouldNotReachHere("abstract");
        }

        /**
         * implementation of cpython/Modules/_io/bufferedio.c:_bufferediobase_readinto_generic
         */
        @Specialization
        Object readinto(VirtualFrame frame, Object self, Object buffer,
                        @Bind("this") Node inliningTarget,
                        @CachedLibrary(limit = "3") PythonBufferAccessLibrary bufferLib,
                        @Cached PyObjectCallMethodObjArgs callMethod,
                        @Cached InlinedConditionProfile isBytes,
                        @Cached InlinedConditionProfile oversize,
                        @Cached PRaiseNode.Lazy raiseNode) {
            try {
                int len = bufferLib.getBufferLength(buffer);
                Object data = callMethod.execute(frame, inliningTarget, self, getMethodName(), len);
                if (isBytes.profile(inliningTarget, !(data instanceof PBytes))) {
                    throw raiseNode.get(inliningTarget).raise(ValueError, S_SHOULD_RETURN_BYTES, "read()");
                }
                // Directly using data as buffer because CPython also accesses the underlying memory
                // of the bytes object
                int dataLen = bufferLib.getBufferLength(data);
                if (oversize.profile(inliningTarget, dataLen > len)) {
                    throw raiseNode.get(inliningTarget).raise(ValueError, S_RETURNED_TOO_MUCH_DATA, "read()", len, dataLen);
                }
                bufferLib.readIntoBuffer(data, 0, buffer, 0, dataLen, bufferLib);
                return dataLen;
            } finally {
                bufferLib.release(buffer, frame, this);
            }
        }

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            throw CompilerDirectives.shouldNotReachHere("abstract");
        }
    }

    @Builtin(name = J_READINTO, minNumOfPositionalArgs = 2, numOfPositionalOnlyArgs = 2, parameterNames = {"$self", "buffer"})
    @ArgumentClinic(name = "buffer", conversion = ArgumentClinic.ClinicConversion.WritableBuffer)
    @GenerateNodeFactory
    abstract static class ReadIntoNode extends ReadIntoGenericNode {
        @Override
        protected final TruffleString getMethodName() {
            return T_READ;
        }

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return BufferedIOBaseBuiltinsClinicProviders.ReadIntoNodeClinicProviderGen.INSTANCE;
        }
    }

    @Builtin(name = J_READINTO1, minNumOfPositionalArgs = 2, numOfPositionalOnlyArgs = 2, parameterNames = {"$self", "buffer"})
    @ArgumentClinic(name = "buffer", conversion = ArgumentClinic.ClinicConversion.WritableBuffer)
    @GenerateNodeFactory
    abstract static class ReadInto1Node extends ReadIntoGenericNode {
        @Override
        protected final TruffleString getMethodName() {
            return T_READ1;
        }

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return BufferedIOBaseBuiltinsClinicProviders.ReadInto1NodeClinicProviderGen.INSTANCE;
        }
    }

    @Builtin(name = J_WRITE, minNumOfPositionalArgs = 1, takesVarArgs = true)
    @GenerateNodeFactory
    abstract static class WriteNode extends PythonBuiltinNode {

        /**
         * implementation of cpython/Modules/_io/bufferedio.c:bufferediobase_write
         */
        @SuppressWarnings("unused")
        @Specialization
        static Object write(Object self, Object args,
                        @Cached PRaiseNode raiseNode) {
            throw raiseNode.raise(IOUnsupportedOperation, T_WRITE);
        }
    }
}
