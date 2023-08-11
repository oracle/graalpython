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

import static com.oracle.graal.python.builtins.modules.io.IOModuleBuiltins.DEFAULT_BUFFER_SIZE;
import static com.oracle.graal.python.builtins.modules.io.IONodes.J_READ;
import static com.oracle.graal.python.builtins.modules.io.IONodes.J_READALL;
import static com.oracle.graal.python.builtins.modules.io.IONodes.J_READINTO;
import static com.oracle.graal.python.builtins.modules.io.IONodes.J_WRITE;
import static com.oracle.graal.python.builtins.modules.io.IONodes.T_READ;
import static com.oracle.graal.python.builtins.modules.io.IONodes.T_READALL;
import static com.oracle.graal.python.builtins.modules.io.IONodes.T_READINTO;
import static com.oracle.graal.python.builtins.objects.bytes.BytesUtils.append;
import static com.oracle.graal.python.builtins.objects.bytes.BytesUtils.createOutputStream;
import static com.oracle.graal.python.builtins.objects.bytes.BytesUtils.toByteArray;
import static com.oracle.graal.python.nodes.ErrorMessages.S_SHOULD_RETURN_BYTES;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.NotImplementedError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.TypeError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.ValueError;

import java.io.ByteArrayOutputStream;
import java.util.List;

import com.oracle.graal.python.annotations.ArgumentClinic;
import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.buffer.PythonBufferAccessLibrary;
import com.oracle.graal.python.builtins.objects.bytes.BytesNodes;
import com.oracle.graal.python.builtins.objects.bytes.PByteArray;
import com.oracle.graal.python.builtins.objects.bytes.PBytes;
import com.oracle.graal.python.lib.PyNumberAsSizeNode;
import com.oracle.graal.python.lib.PyObjectCallMethodObjArgs;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.clinic.ArgumentClinicProvider;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.InlinedConditionProfile;
import com.oracle.truffle.api.profiles.InlinedCountingConditionProfile;

@CoreFunctions(extendClasses = PythonBuiltinClassType.PRawIOBase)
public final class RawIOBaseBuiltins extends PythonBuiltins {

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return RawIOBaseBuiltinsFactory.getFactories();
    }

    @Builtin(name = J_READ, minNumOfPositionalArgs = 1, parameterNames = {"$self", "$size"})
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

        @Specialization(guards = "size < 0")
        static Object readall(VirtualFrame frame, Object self, @SuppressWarnings("unused") int size,
                        @Bind("this") Node inliningTarget,
                        @Exclusive @Cached PyObjectCallMethodObjArgs callMethod) {
            return callMethod.execute(frame, inliningTarget, self, T_READALL);
        }

        @Specialization(guards = "size >= 0")
        @SuppressWarnings("truffle-static-method")
        Object read(VirtualFrame frame, Object self, int size,
                        @Bind("this") Node inliningTarget,
                        @Cached BytesNodes.ToBytesNode toBytes,
                        @Exclusive @Cached PyObjectCallMethodObjArgs callMethodReadInto,
                        @Cached PyNumberAsSizeNode asSizeNode) {
            PByteArray b = factory().createByteArray(new byte[size]);
            Object res = callMethodReadInto.execute(frame, inliningTarget, self, T_READINTO, b);
            if (res == PNone.NONE) {
                return res;
            }
            int n = asSizeNode.executeExact(frame, inliningTarget, res, ValueError);
            if (n == 0) {
                return factory().createBytes(PythonUtils.EMPTY_BYTE_ARRAY);
            }
            byte[] bytes = toBytes.execute(b);
            if (n < size) {
                return factory().createBytes(PythonUtils.arrayCopyOf(bytes, n));
            }
            return factory().createBytes(bytes);
        }
    }

    @Builtin(name = J_READALL, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class ReadallNode extends PythonBuiltinNode {

        /**
         * implementation of cpython/Modules/_io/iobase.c:_io__RawIOBase_readall_impl
         */
        @Specialization
        Object readall(VirtualFrame frame, Object self,
                        @Bind("this") Node inliningTarget,
                        @Cached PyObjectCallMethodObjArgs callMethodRead,
                        @Cached InlinedConditionProfile dataNoneProfile,
                        @Cached InlinedConditionProfile chunksSize0Profile,
                        @Cached InlinedCountingConditionProfile bytesLen0Profile,
                        @CachedLibrary(limit = "1") PythonBufferAccessLibrary bufferLib) {
            ByteArrayOutputStream chunks = createOutputStream();
            while (true) {
                Object data = callMethodRead.execute(frame, inliningTarget, self, T_READ, DEFAULT_BUFFER_SIZE);
                // TODO _PyIO_trap_eintr [GR-23297]
                if (dataNoneProfile.profile(inliningTarget, data == PNone.NONE)) {
                    if (chunksSize0Profile.profile(inliningTarget, chunks.size() == 0)) {
                        return data;
                    }
                    break;
                }
                if (!(data instanceof PBytes)) {
                    throw raise(TypeError, S_SHOULD_RETURN_BYTES, "read()");
                }
                byte[] bytes = bufferLib.getInternalOrCopiedByteArray(data);
                int bytesLen = bufferLib.getBufferLength(data);
                if (bytesLen0Profile.profile(inliningTarget, bytesLen == 0)) {
                    break;
                }
                append(chunks, bytes, bytesLen);
            }

            return factory().createBytes(toByteArray(chunks));
        }
    }

    @Builtin(name = J_READINTO, minNumOfPositionalArgs = 2)
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

    @Builtin(name = J_WRITE, minNumOfPositionalArgs = 2)
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
