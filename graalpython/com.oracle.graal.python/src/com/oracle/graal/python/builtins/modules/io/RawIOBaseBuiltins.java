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

import static com.oracle.graal.python.builtins.objects.bytes.BytesUtils.append;
import static com.oracle.graal.python.builtins.objects.bytes.BytesUtils.createOutputStream;
import static com.oracle.graal.python.builtins.objects.bytes.BytesUtils.getBytes;
import static com.oracle.graal.python.builtins.objects.bytes.BytesUtils.toByteArray;
import static com.oracle.graal.python.nodes.ErrorMessages.S_SHOULD_RETURN_BYTES;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.NotImplementedError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.TypeError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.ValueError;

import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import java.util.List;

import com.oracle.graal.python.annotations.ArgumentClinic;
import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.bytes.BytesNodes;
import com.oracle.graal.python.builtins.objects.bytes.PByteArray;
import com.oracle.graal.python.builtins.objects.object.PythonObjectLibrary;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.clinic.ArgumentClinicProvider;
import com.oracle.graal.python.nodes.lib.PyNumberAsSizeNode;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.profiles.ConditionProfile;

@CoreFunctions(extendClasses = PythonBuiltinClassType.PRawIOBase)
public class RawIOBaseBuiltins extends PythonBuiltins {

    protected static final String READ = "read";
    protected static final String READALL = "readall";
    protected static final String READINTO = "readinto";
    protected static final String WRITE = "write";

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return RawIOBaseBuiltinsFactory.getFactories();
    }

    @Builtin(name = READ, minNumOfPositionalArgs = 2, parameterNames = {"$self", "$size"})
    @ArgumentClinic(name = "$size", conversion = ArgumentClinic.ClinicConversion.Int, defaultValue = "-1", useDefaultForNone = true)
    @GenerateNodeFactory
    abstract static class ReadNode extends PythonBinaryClinicBuiltinNode {
        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return RawIOBaseBuiltinsClinicProviders.ReadNodeClinicProviderGen.INSTANCE;
        }

        /**
         * implementation of cpython/Modules/_io/iobase.c:_io__RawIOBase_read_impl
         */

        @Specialization(limit = "2", guards = "size < 0")
        Object readall(VirtualFrame frame, Object self, @SuppressWarnings("unused") int size,
                        @CachedLibrary("self") PythonObjectLibrary libSelf) {
            return libSelf.lookupAndCallRegularMethod(self, frame, READALL);
        }

        @Specialization(limit = "2", guards = "size >= 0")
        Object read(VirtualFrame frame, Object self, int size,
                        @Cached BytesNodes.ToBytesNode toBytes,
                        @Cached PyNumberAsSizeNode asSizeNode,
                        @CachedLibrary("self") PythonObjectLibrary libSelf) {
            PByteArray b = factory().createByteArray(new byte[size]);
            Object res = libSelf.lookupAndCallRegularMethod(self, frame, READINTO, b);
            int n = asSizeNode.executeExact(frame, res, ValueError);
            if (n == 0) {
                return factory().createBytes(PythonUtils.EMPTY_BYTE_ARRAY);
            }
            byte[] bytes = toBytes.execute(b);
            if (n < size) {
                return factory().createBytes(Arrays.copyOf(bytes, n));
            }
            return factory().createBytes(bytes);
        }
    }

    @Builtin(name = READALL, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class ReadallNode extends PythonBuiltinNode {

        /**
         * implementation of cpython/Modules/_io/iobase.c:_io__RawIOBase_readall_impl
         */
        @Specialization(limit = "2")
        Object readall(VirtualFrame frame, Object self,
                        @CachedLibrary(limit = "1") PythonObjectLibrary asBytes,
                        @CachedLibrary("self") PythonObjectLibrary libSelf,
                        @Cached ConditionProfile isBuffer) {
            ByteArrayOutputStream chunks = createOutputStream();
            while (true) {
                Object data = libSelf.lookupAndCallRegularMethod(self, frame, READ, IOModuleBuiltins.DEFAULT_BUFFER_SIZE);
                // TODO _PyIO_trap_eintr [GR-23297]
                if (data == PNone.NONE) {
                    if (chunks.size() == 0) {
                        return data;
                    }
                    break;
                }
                if (isBuffer.profile(!asBytes.isBuffer(data))) {
                    throw raise(TypeError, S_SHOULD_RETURN_BYTES, "read()");
                }
                byte[] bytes = getBytes(asBytes, data);
                if (bytes.length == 0) {
                    break;
                }
                append(chunks, bytes, bytes.length);
            }

            return factory().createBytes(toByteArray(chunks));
        }
    }

    @Builtin(name = READINTO, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class ReadIntoNode extends PythonBuiltinNode {

        /**
         * implementation of cpython/Modules/_io/iobase.c:rawiobase_readinto
         */
        @Specialization
        Object readinto(@SuppressWarnings("unused") Object self, @SuppressWarnings("unused") Object args) {
            throw raise(NotImplementedError);
        }
    }

    @Builtin(name = WRITE, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class WriteNode extends PythonBuiltinNode {

        /**
         * implementation of cpython/Modules/_io/iobase.c:rawiobase_write
         */
        @Specialization
        Object write(@SuppressWarnings("unused") Object self, @SuppressWarnings("unused") Object args) {
            throw raise(NotImplementedError);
        }
    }
}
