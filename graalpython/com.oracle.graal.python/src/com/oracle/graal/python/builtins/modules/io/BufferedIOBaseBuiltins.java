/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.graal.python.builtins.objects.bytes.BytesUtils.getBytes;
import static com.oracle.graal.python.builtins.objects.bytes.BytesUtils.getBytesLength;
import static com.oracle.graal.python.nodes.ErrorMessages.S_RETURNED_TOO_MUCH_DATA;
import static com.oracle.graal.python.nodes.ErrorMessages.S_SHOULD_RETURN_BYTES;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.IOUnsupportedOperation;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.ValueError;

import java.util.List;

import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes;
import com.oracle.graal.python.builtins.objects.object.PythonObjectLibrary;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.profiles.ConditionProfile;

@CoreFunctions(extendClasses = PythonBuiltinClassType.PBufferedIOBase)
public class BufferedIOBaseBuiltins extends PythonBuiltins {

    protected static final String DETACH = "detach";
    protected static final String READ = "read";
    protected static final String READ1 = "read1";
    protected static final String READINTO = "readinto";
    protected static final String READINTO1 = "readinto1";
    protected static final String WRITE = "write";

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return BufferedIOBaseBuiltinsFactory.getFactories();
    }

    @Builtin(name = DETACH, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class DetachNode extends PythonBuiltinNode {

        /**
         * implementation of cpython/Modules/_io/bufferedio.h:_io__BufferedIOBase_detach
         */
        @Specialization
        Object detach(@SuppressWarnings("unused") Object self) {
            throw raise(IOUnsupportedOperation, DETACH);
        }
    }

    @Builtin(name = READ, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class ReadNode extends PythonBuiltinNode {

        /**
         * implementation of cpython/Modules/_io/bufferedio.c:bufferediobase_read
         */
        @Specialization
        Object read(@SuppressWarnings("unused") Object self) {
            throw raise(IOUnsupportedOperation, READ);
        }
    }

    @Builtin(name = READ1, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class Read1Node extends PythonBuiltinNode {

        /**
         * implementation of cpython/Modules/_io/bufferedio.c:bufferediobase_read1
         */
        @Specialization
        Object read1(@SuppressWarnings("unused") Object self) {
            throw raise(IOUnsupportedOperation, READ1);
        }
    }

    abstract static class ReadIntoGenericNode extends PythonBinaryBuiltinNode {
        protected String method() {
            return null;
        }

        /**
         * implementation of cpython/Modules/_io/bufferedio.c:_bufferediobase_readinto_generic
         */
        @Specialization(limit = "2")
        Object readinto(VirtualFrame frame, Object self, Object buffer,
                        @CachedLibrary(limit = "2") PythonObjectLibrary asByte,
                        @CachedLibrary("self") PythonObjectLibrary libSelf,
                        @Cached ConditionProfile isBuffer,
                        @Cached ConditionProfile oversize,
                        @Cached SequenceStorageNodes.BytesMemcpyNode memcpyNode) {
            int len = getBytesLength(asByte, buffer);
            Object data = libSelf.lookupAndCallRegularMethod(self, frame, method(), len);
            if (isBuffer.profile(!asByte.isBuffer(data))) {
                throw raise(ValueError, S_SHOULD_RETURN_BYTES, "read()");
            }
            byte[] bytes = getBytes(asByte, data);
            int dataLen = bytes.length;
            if (oversize.profile(dataLen > len)) {
                throw raise(ValueError, S_RETURNED_TOO_MUCH_DATA, "read()", len, dataLen);
            }
            memcpyNode.execute(frame, buffer, 0, bytes, 0, dataLen);
            return dataLen;
        }
    }

    @Builtin(name = READINTO, minNumOfPositionalArgs = 2, parameterNames = {"$self", "$buffer"})
    @GenerateNodeFactory
    abstract static class ReadIntoNode extends ReadIntoGenericNode {
        @Override
        protected final String method() {
            return READ;
        }
    }

    @Builtin(name = READINTO1, minNumOfPositionalArgs = 2, parameterNames = {"$self", "$buffer"})
    @GenerateNodeFactory
    abstract static class ReadInto1Node extends ReadIntoGenericNode {
        @Override
        protected final String method() {
            return READ1;
        }
    }

    @Builtin(name = WRITE, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class WriteNode extends PythonBuiltinNode {

        /**
         * implementation of cpython/Modules/_io/bufferedio.c:bufferediobase_write
         */
        @Specialization
        Object write(@SuppressWarnings("unused") Object self) {
            throw raise(IOUnsupportedOperation, WRITE);
        }
    }
}
