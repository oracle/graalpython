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

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.OverflowError;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.PBytesIOBuf;
import static com.oracle.graal.python.builtins.modules.io.BufferedIOUtil.SEEK_CUR;
import static com.oracle.graal.python.builtins.modules.io.BufferedIOUtil.SEEK_END;
import static com.oracle.graal.python.builtins.modules.io.BufferedIOUtil.SEEK_SET;
import static com.oracle.graal.python.builtins.modules.io.IONodes.CLOSE;
import static com.oracle.graal.python.builtins.modules.io.IONodes.CLOSED;
import static com.oracle.graal.python.builtins.modules.io.IONodes.FLUSH;
import static com.oracle.graal.python.builtins.modules.io.IONodes.GETBUFFER;
import static com.oracle.graal.python.builtins.modules.io.IONodes.GETVALUE;
import static com.oracle.graal.python.builtins.modules.io.IONodes.ISATTY;
import static com.oracle.graal.python.builtins.modules.io.IONodes.READ;
import static com.oracle.graal.python.builtins.modules.io.IONodes.READ1;
import static com.oracle.graal.python.builtins.modules.io.IONodes.READABLE;
import static com.oracle.graal.python.builtins.modules.io.IONodes.READINTO;
import static com.oracle.graal.python.builtins.modules.io.IONodes.READLINE;
import static com.oracle.graal.python.builtins.modules.io.IONodes.READLINES;
import static com.oracle.graal.python.builtins.modules.io.IONodes.SEEK;
import static com.oracle.graal.python.builtins.modules.io.IONodes.SEEKABLE;
import static com.oracle.graal.python.builtins.modules.io.IONodes.TELL;
import static com.oracle.graal.python.builtins.modules.io.IONodes.TRUNCATE;
import static com.oracle.graal.python.builtins.modules.io.IONodes.WRITABLE;
import static com.oracle.graal.python.builtins.modules.io.IONodes.WRITE;
import static com.oracle.graal.python.builtins.modules.io.IONodes.WRITELINES;
import static com.oracle.graal.python.builtins.modules.io.IONodes.getDict;
import static com.oracle.graal.python.nodes.ErrorMessages.EXISTING_EXPORTS_OF_DATA_OBJECT_CANNOT_BE_RE_SIZED;
import static com.oracle.graal.python.nodes.ErrorMessages.INVALID_WHENCE_D_SHOULD_BE_0_1_OR_2;
import static com.oracle.graal.python.nodes.ErrorMessages.IO_CLOSED;
import static com.oracle.graal.python.nodes.ErrorMessages.NEGATIVE_SEEK_VALUE_D;
import static com.oracle.graal.python.nodes.ErrorMessages.NEGATIVE_SIZE_VALUE_D;
import static com.oracle.graal.python.nodes.ErrorMessages.NEW_POSITION_TOO_LARGE;
import static com.oracle.graal.python.nodes.ErrorMessages.POSITION_VALUE_CANNOT_BE_NEGATIVE;
import static com.oracle.graal.python.nodes.ErrorMessages.P_SETSTATE_ARGUMENT_SHOULD_BE_D_TUPLE_GOT_P;
import static com.oracle.graal.python.nodes.ErrorMessages.SECOND_ITEM_OF_STATE_MUST_BE_AN_INTEGER_NOT_P;
import static com.oracle.graal.python.nodes.ErrorMessages.THIRD_ITEM_OF_STATE_SHOULD_BE_A_DICT_GOT_A_P;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__GETSTATE__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__INIT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__NEXT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__SETSTATE__;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.BufferError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.StopIteration;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.TypeError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.ValueError;

import java.util.List;

import com.oracle.graal.python.annotations.ArgumentClinic;
import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.bytes.BytesBuiltins;
import com.oracle.graal.python.builtins.objects.bytes.BytesNodes;
import com.oracle.graal.python.builtins.objects.bytes.PBytes;
import com.oracle.graal.python.builtins.objects.common.HashingStorageLibrary;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.memoryview.PMemoryView;
import com.oracle.graal.python.builtins.objects.object.PythonObjectLibrary;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.lib.PyNumberAsSizeNode;
import com.oracle.graal.python.lib.PyNumberIndexNode;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.control.GetNextNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonTernaryClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.clinic.ArgumentClinicProvider;
import com.oracle.graal.python.nodes.object.IsBuiltinClassProfile;
import com.oracle.graal.python.nodes.truffle.PythonArithmeticTypes;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.graal.python.util.ArrayBuilder;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.TypeSystemReference;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.profiles.ConditionProfile;

@CoreFunctions(extendClasses = PythonBuiltinClassType.PBytesIO)
public class BytesIOBuiltins extends PythonBuiltins {

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return BytesIOBuiltinsFactory.getFactories();
    }

    abstract static class ClosedCheckPythonUnaryBuiltinNode extends PythonUnaryBuiltinNode {
        @Specialization(guards = "!self.hasBuf()")
        @SuppressWarnings("unused")
        Object closedError(PBytesIO self) {
            throw raise(ValueError, IO_CLOSED);
        }
    }

    abstract static class ClosedCheckPythonBinaryBuiltinNode extends PythonBinaryBuiltinNode {
        @Specialization(guards = "!self.hasBuf()")
        @SuppressWarnings("unused")
        Object closedError(PBytesIO self, Object arg) {
            throw raise(ValueError, IO_CLOSED);
        }
    }

    abstract static class ClosedCheckPythonBinaryClinicBuiltinNode extends PythonBinaryClinicBuiltinNode {

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            throw CompilerDirectives.shouldNotReachHere();
        }

        @Specialization(guards = "!self.hasBuf()")
        @SuppressWarnings("unused")
        Object closedError(PBytesIO self, Object arg) {
            throw raise(ValueError, IO_CLOSED);
        }
    }

    protected static final byte[] EMPTY_BYTE_ARRAY = PythonUtils.EMPTY_BYTE_ARRAY;

    @Builtin(name = __INIT__, minNumOfPositionalArgs = 1, parameterNames = {"$self", "initial_bytes"})
    @ArgumentClinic(name = "initial_bytes", conversionClass = BytesBuiltins.ExpectByteLikeNode.class, defaultValue = "BytesIOBuiltins.EMPTY_BYTE_ARRAY")
    @GenerateNodeFactory
    public abstract static class InitNode extends PythonBinaryClinicBuiltinNode {

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return BytesIOBuiltinsClinicProviders.InitNodeClinicProviderGen.INSTANCE;
        }

        @Specialization(guards = "checkExports(self)")
        PNone init(PBytesIO self, byte[] initialBytes) {
            /* In case, __init__ is called multiple times. */
            self.setStringSize(0);
            self.setPos(0);
            int len = initialBytes.length;
            if (len > 0) {
                self.setBuf(factory().createBytes(PythonUtils.arrayCopyOf(initialBytes, len)));
                self.setStringSize(len);
            }
            return PNone.NONE;
        }

        protected static boolean checkExports(PBytesIO self) {
            return self.getExports() == 0;
        }

        @Specialization(guards = "!checkExports(self)")
        @SuppressWarnings("unused")
        Object exportsError(PBytesIO self, Object size) {
            throw raise(BufferError, EXISTING_EXPORTS_OF_DATA_OBJECT_CANNOT_BE_RE_SIZED);
        }
    }

    static PBytes readBytes(PBytesIO self, int size,
                    SequenceStorageNodes.GetInternalByteArrayNode getBytes,
                    PythonObjectFactory factory) {
        if (size == 0) {
            return factory.createBytes(PythonUtils.EMPTY_BYTE_ARRAY);
        }
        byte[] buf = getBytes.execute(self.getBuf().getSequenceStorage());

        assert self.hasBuf();
        assert (size <= self.getStringSize());
        if (size > 1 && self.getPos() == 0 && size == buf.length && self.getExports() == 0) {
            self.incPos(size);
            return self.getBuf();
        }

        PBytes output = factory.createBytes(buf, self.getPos(), size);
        self.incPos(size);
        return output;
    }

    @Builtin(name = READ, minNumOfPositionalArgs = 1, parameterNames = {"$self", "size"})
    @ArgumentClinic(name = "size", conversion = ArgumentClinic.ClinicConversion.Int, defaultValue = "-1", useDefaultForNone = true)
    @GenerateNodeFactory
    abstract static class ReadNode extends ClosedCheckPythonBinaryClinicBuiltinNode {

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return BytesIOBuiltinsClinicProviders.ReadNodeClinicProviderGen.INSTANCE;
        }

        @Specialization(guards = "self.hasBuf()")
        Object read(PBytesIO self, int len,
                        @Cached SequenceStorageNodes.GetInternalByteArrayNode getBytes) {
            int size = len;
            /* adjust invalid sizes */
            int n = self.getStringSize() - self.getPos();
            if (size < 0 || size > n) {
                size = n;
                if (size < 0) {
                    size = 0;
                }
            }

            return readBytes(self, size, getBytes, factory());
        }
    }

    @Builtin(name = READ1, minNumOfPositionalArgs = 1, parameterNames = {"$self", "size"})
    @ArgumentClinic(name = "size", conversion = ArgumentClinic.ClinicConversion.Int, defaultValue = "-1", useDefaultForNone = true)
    @GenerateNodeFactory
    abstract static class Read1Node extends ReadNode {
        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return BytesIOBuiltinsClinicProviders.Read1NodeClinicProviderGen.INSTANCE;
        }
    }

    static int scanEOL(PBytesIO self, int l,
                    SequenceStorageNodes.GetInternalByteArrayNode getBytes) {
        assert (self.hasBuf());
        assert (self.getPos() >= 0);

        if (self.getPos() >= self.getStringSize()) {
            return 0;
        }

        /* Move to the end of the line, up to the end of the string, s. */
        int maxlen = self.getStringSize() - self.getPos();
        int len = l;
        if (len < 0 || len > maxlen) {
            len = maxlen;
        }

        byte[] buf = getBytes.execute(self.getBuf().getSequenceStorage());
        if (len > 0) {
            int n = -1;
            for (int i = self.getPos(); i < (self.getPos() + len); i++) {
                if (buf[i] == '\n') {
                    n = i;
                    break;
                }
            }
            if (n != -1) {
                /* Get the length from the current position to the end of the line. */
                len = n - self.getPos() + 1;
            }
        }
        assert (len >= 0);
        assert (self.getPos() < Integer.MAX_VALUE - len);

        return len;
    }

    @Builtin(name = READLINE, minNumOfPositionalArgs = 1, parameterNames = {"$self", "size"})
    @ArgumentClinic(name = "size", conversion = ArgumentClinic.ClinicConversion.Int, defaultValue = "-1", useDefaultForNone = true)
    @GenerateNodeFactory
    abstract static class ReadlineNode extends ClosedCheckPythonBinaryClinicBuiltinNode {

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return BytesIOBuiltinsClinicProviders.ReadlineNodeClinicProviderGen.INSTANCE;
        }

        @Specialization(guards = "self.hasBuf()")
        Object readline(PBytesIO self, int size,
                        @Cached SequenceStorageNodes.GetInternalByteArrayNode getBytes) {
            int n = scanEOL(self, size, getBytes);
            return readBytes(self, n, getBytes, factory());
        }
    }

    @Builtin(name = READLINES, minNumOfPositionalArgs = 1, parameterNames = {"$self", "size"})
    @ArgumentClinic(name = "size", conversion = ArgumentClinic.ClinicConversion.Int, defaultValue = "-1", useDefaultForNone = true)
    @GenerateNodeFactory
    abstract static class ReadlinesNode extends ClosedCheckPythonBinaryClinicBuiltinNode {

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return BytesIOBuiltinsClinicProviders.ReadlinesNodeClinicProviderGen.INSTANCE;
        }

        @Specialization(guards = "self.hasBuf()")
        Object readlines(PBytesIO self, int maxsize,
                        @Cached SequenceStorageNodes.GetInternalByteArrayNode getBytes) {
            ArrayBuilder<Object> result = new ArrayBuilder<>();

            int n;
            byte[] bytes = getBytes.execute(self.getBuf().getSequenceStorage());
            int cur = self.getPos();
            int size = 0;
            while ((n = scanEOL(self, -1, getBytes)) != 0) {
                self.incPos(n);
                PBytes line = factory().createBytes(bytes, cur, n);
                result.add(line);
                size += n;
                if (maxsize > 0 && size >= maxsize) {
                    break;
                }
                cur += n;
            }
            return factory().createList(result.toArray(new Object[0]));
        }
    }

    @Builtin(name = READINTO, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class ReadIntoNode extends ClosedCheckPythonBinaryBuiltinNode {

        @Specialization(guards = {"self.hasBuf()", "!isPBytes(buffer)"}, limit = "2")
        Object readinto(VirtualFrame frame, PBytesIO self, Object buffer,
                        @CachedLibrary("buffer") PythonObjectLibrary lib,
                        @Cached ConditionProfile isBuffer,
                        @Cached SequenceStorageNodes.GetInternalByteArrayNode getBytes,
                        @Cached SequenceStorageNodes.BytesMemcpyNode memcpyNode) {
            if (isBuffer.profile(!lib.isBuffer(buffer))) {
                return error(self, buffer);
            }
            /* adjust invalid sizes */
            int len = lib.length(buffer);
            int n = self.getStringSize() - self.getPos();
            if (len > n) {
                len = n;
                if (len < 0) {
                    return 0;
                }
            }

            byte[] buf = getBytes.execute(self.getBuf().getSequenceStorage());
            memcpyNode.execute(frame, buffer, 0, buf, self.getPos(), len);
            assert (self.getPos() + len < Integer.MAX_VALUE);
            assert (len >= 0);
            self.incPos(len);

            return len;
        }

        @Specialization(guards = {"self.hasBuf()", "isPBytes(buffer)"})
        Object error(@SuppressWarnings("unused") PBytesIO self, Object buffer) {
            throw raise(TypeError, "%s() argument must be read-write bytes-like object, not %p", READINTO, buffer);
        }
    }

    @Builtin(name = TRUNCATE, minNumOfPositionalArgs = 1, parameterNames = {"$self", "size"})
    @ArgumentClinic(name = "size", defaultValue = "PNone.NONE", useDefaultForNone = true)
    @TypeSystemReference(PythonArithmeticTypes.class)
    @GenerateNodeFactory
    abstract static class TruncateNode extends ClosedCheckPythonBinaryClinicBuiltinNode {

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return BytesIOBuiltinsClinicProviders.TruncateNodeClinicProviderGen.INSTANCE;
        }

        @Specialization(guards = {"self.hasBuf()", "checkExports(self)"})
        static Object truncate(PBytesIO self, @SuppressWarnings("unused") PNone size,
                        @Cached SequenceStorageNodes.SetLenNode setLenNode) {
            return truncate(self, self.getPos(), setLenNode);
        }

        @Specialization(guards = {"self.hasBuf()", "checkExports(self)", "size >= 0", "size < self.getStringSize()"})
        static Object truncate(PBytesIO self, int size,
                        @Cached SequenceStorageNodes.SetLenNode setLenNode) {
            self.setStringSize(size);
            setLenNode.execute(self.getBuf().getSequenceStorage(), size);
            return size;
        }

        @Specialization(guards = {"self.hasBuf()", "checkExports(self)", "size >= 0", "size >= self.getStringSize()"})
        static Object same(@SuppressWarnings("unused") PBytesIO self, int size) {
            return size;
        }

        @Specialization(guards = {"self.hasBuf()", "checkExports(self)", "!isInteger(arg)", "!isPNone(arg)"})
        Object obj(VirtualFrame frame, PBytesIO self, Object arg,
                        @Cached PyNumberAsSizeNode asSizeNode,
                        @Cached PyNumberIndexNode indexNode,
                        @Cached SequenceStorageNodes.SetLenNode setLenNode) {
            int size = asSizeNode.executeExact(frame, indexNode.execute(frame, arg), OverflowError);
            if (size >= 0) {
                if (size < self.getStringSize()) {
                    return truncate(self, size, setLenNode);
                }
                return size;
            }
            return negSize(self, size);
        }

        @Specialization(guards = {"self.hasBuf()", "checkExports(self)", "size < 0"})
        Object negSize(@SuppressWarnings("unused") PBytesIO self, int size) {
            throw raise(ValueError, NEGATIVE_SIZE_VALUE_D, size);
        }

        protected static boolean checkExports(PBytesIO self) {
            return self.getExports() == 0;
        }

        @Specialization(guards = "!checkExports(self)")
        Object exportsError(@SuppressWarnings("unused") PBytesIO self, @SuppressWarnings("unused") Object size) {
            throw raise(BufferError, EXISTING_EXPORTS_OF_DATA_OBJECT_CANNOT_BE_RE_SIZED);
        }
    }

    @Builtin(name = WRITE, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class WriteNode extends ClosedCheckPythonBinaryBuiltinNode {

        @Specialization(guards = {"self.hasBuf()", "checkExports(self)"})
        static Object doWrite(VirtualFrame frame, PBytesIO self, Object b,
                        @Cached BytesNodes.GetBuffer getBuffer,
                        @Cached SequenceStorageNodes.EnsureCapacityNode ensureCapacityNode,
                        @Cached SequenceStorageNodes.BytesMemcpyNode memcpyNode,
                        @Cached SequenceStorageNodes.SetLenNode setLenNode) {
            byte[] buf = getBuffer.execute(b);
            int len = buf.length;
            if (len == 0) {
                return 0;
            }
            write(frame, self, buf, ensureCapacityNode, memcpyNode, setLenNode);
            return len;
        }

        static void write(VirtualFrame frame, PBytesIO self, byte[] buf,
                        SequenceStorageNodes.EnsureCapacityNode ensureCapacityNode,
                        SequenceStorageNodes.BytesMemcpyNode memcpyNode,
                        SequenceStorageNodes.SetLenNode setLenNode) {
            int len = buf.length;
            int pos = self.getPos();
            int size = self.getStringSize();
            int endpos = self.getPos() + len;
            ensureCapacityNode.execute(self.getBuf().getSequenceStorage(), endpos);
            if (pos > size) {
                byte[] nil = new byte[pos - size];
                memcpyNode.execute(frame, self.getBuf(), size, nil, 0, nil.length);
            }
            memcpyNode.execute(frame, self.getBuf(), pos, buf, 0, len);
            self.setPos(endpos);
            if (size < endpos) {
                setLenNode.execute(self.getBuf().getSequenceStorage(), endpos);
                self.setStringSize(endpos);
            }
        }

        protected static boolean checkExports(PBytesIO self) {
            return self.getExports() == 0;
        }

        @Specialization(guards = "!checkExports(self)")
        @SuppressWarnings("unused")
        Object exportsError(PBytesIO self, Object size) {
            throw raise(BufferError, EXISTING_EXPORTS_OF_DATA_OBJECT_CANNOT_BE_RE_SIZED);
        }
    }

    @Builtin(name = WRITELINES, minNumOfPositionalArgs = 2, parameterNames = {"$self", "lines"})
    @GenerateNodeFactory
    abstract static class WriteLinesNode extends ClosedCheckPythonBinaryBuiltinNode {
        @Specialization(guards = {"self.hasBuf()", "checkExports(self)"}, limit = "2")
        static Object writeLines(VirtualFrame frame, PBytesIO self, Object lines,
                        @Cached GetNextNode getNextNode,
                        @Cached WriteNode writeNode,
                        @Cached IsBuiltinClassProfile errorProfile,
                        @CachedLibrary("lines") PythonObjectLibrary libLines) {
            Object iter = libLines.getIteratorWithFrame(lines, frame);
            while (true) {
                Object line;
                try {
                    line = getNextNode.execute(frame, iter);
                } catch (PException e) {
                    e.expectStopIteration(errorProfile);
                    break;
                }
                writeNode.call(frame, self, line);
            }
            return PNone.NONE;
        }

        protected static boolean checkExports(PBytesIO self) {
            return self.getExports() == 0;
        }

        @Specialization(guards = "!checkExports(self)")
        @SuppressWarnings("unused")
        Object exportsError(PBytesIO self, Object size) {
            throw raise(BufferError, EXISTING_EXPORTS_OF_DATA_OBJECT_CANNOT_BE_RE_SIZED);
        }
    }

    @Builtin(name = ISATTY, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class IsAttyNode extends ClosedCheckPythonUnaryBuiltinNode {

        @SuppressWarnings("unused")
        @Specialization(guards = "self.hasBuf()")
        static boolean atty(PBytesIO self) {
            return false;
        }
    }

    @Builtin(name = TELL, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class TellNode extends ClosedCheckPythonUnaryBuiltinNode {

        @Specialization(guards = "self.hasBuf()")
        static Object tell(PBytesIO self) {
            return self.getPos();
        }
    }

    @Builtin(name = SEEK, minNumOfPositionalArgs = 2, parameterNames = {"$self", "pos", "whence"})
    @ArgumentClinic(name = "pos", conversionClass = IONodes.SeekPosNode.class)
    @ArgumentClinic(name = "whence", conversion = ArgumentClinic.ClinicConversion.Int, defaultValue = "BufferedIOUtil.SEEK_SET", useDefaultForNone = true)
    @GenerateNodeFactory
    abstract static class SeekNode extends PythonTernaryClinicBuiltinNode {
        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return BytesIOBuiltinsClinicProviders.SeekNodeClinicProviderGen.INSTANCE;
        }

        protected static boolean isSupportedWhence(int whence) {
            return whence == SEEK_SET || whence == SEEK_CUR || whence == SEEK_END;
        }

        protected static boolean isLargePos(int pos, int to) {
            return pos > Integer.MAX_VALUE - to;
        }

        protected static boolean validPos(PBytesIO self, int pos, int whence) {
            return !(pos < 0 && whence == 0) &&
                            !(isLargePos(pos, self.getStringSize()) && whence == 2) &&
                            !(isLargePos(pos, self.getPos()) && whence == 1);
        }

        @Specialization(guards = {"self.hasBuf()", "isSupportedWhence(whence)", "validPos(self, pos, whence)"})
        static Object seek(PBytesIO self, int pos, int whence) {
            /*-
             * whence = 0: offset relative to beginning of the string. 
             * whence = 1: offset relative to current position. 
             * whence = 2: offset relative the end of the string.
             */
            int p = pos;
            if (whence == 1) {
                p += self.getPos();
            } else if (whence == 2) {
                p += self.getStringSize();
            }

            if (p < 0) {
                p = 0;
            }
            self.setPos(p);
            return p;
        }

        @Specialization(guards = {"self.hasBuf()", "!isSupportedWhence(whence)"})
        Object whenceError(@SuppressWarnings("unused") PBytesIO self, @SuppressWarnings("unused") int pos, int whence) {
            throw raise(ValueError, INVALID_WHENCE_D_SHOULD_BE_0_1_OR_2, whence);
        }

        @SuppressWarnings("unused")
        @Specialization(guards = {"self.hasBuf()", "isLargePos(pos, self.getPos())", "whence == 1"})
        Object largePos1(PBytesIO self, int pos, int whence) {
            throw raise(OverflowError, NEW_POSITION_TOO_LARGE);
        }

        @SuppressWarnings("unused")
        @Specialization(guards = {"self.hasBuf()", "isLargePos(pos, self.getStringSize())", "whence == 2"})
        Object largePos2(PBytesIO self, int pos, int whence) {
            throw raise(OverflowError, NEW_POSITION_TOO_LARGE);
        }

        @Specialization(guards = {"self.hasBuf()", "pos < 0", "whence == 0"})
        Object negPos(@SuppressWarnings("unused") PBytesIO self, int pos, @SuppressWarnings("unused") int whence) {
            throw raise(ValueError, NEGATIVE_SEEK_VALUE_D, pos);
        }

        @SuppressWarnings("unused")
        @Specialization(guards = "!self.hasBuf()")
        Object closedError(PBytesIO self, int pos, int whence) {
            throw raise(ValueError, IO_CLOSED);
        }
    }

    @Builtin(name = GETBUFFER, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class GetBufferNode extends ClosedCheckPythonUnaryBuiltinNode {
        @Specialization(guards = "self.hasBuf()")
        Object doit(PBytesIO self) {
            PBytesIOBuffer buf = factory().createBytesIOBuf(PBytesIOBuf, self);
            int length = self.getStringSize();
            return factory().createMemoryView(getContext(), self.getManagedBuffer(), buf,
                            length, true, 1, "B",
                            1, null, 0, new int[]{length}, new int[]{1},
                            null, PMemoryView.FLAG_C | PMemoryView.FLAG_FORTRAN);
        }
    }

    @Builtin(name = GETVALUE, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class GetValueNode extends ClosedCheckPythonUnaryBuiltinNode {

        protected static boolean shouldCopy(PBytesIO self) {
            return self.getStringSize() <= 1 || self.getExports() > 0;
        }

        @Specialization(guards = {"self.hasBuf()", "shouldCopy(self)"})
        Object copy(PBytesIO self,
                        @Cached SequenceStorageNodes.GetInternalByteArrayNode getBytes) {
            byte[] buf = getBytes.execute(self.getBuf().getSequenceStorage());
            return factory().createBytes(PythonUtils.arrayCopyOf(buf, self.getStringSize()));
        }

        @Specialization(guards = {"self.hasBuf()", "!shouldCopy(self)"})
        static Object doit(PBytesIO self,
                        @Cached SequenceStorageNodes.SetLenNode setLenNode) {
            setLenNode.execute(self.getBuf().getSequenceStorage(), self.getStringSize());
            return self.getBuf();
        }
    }

    @Builtin(name = __GETSTATE__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class GetStateNode extends ClosedCheckPythonUnaryBuiltinNode {
        @Specialization(guards = "self.hasBuf()", limit = "1")
        Object doit(VirtualFrame frame, PBytesIO self,
                        @Cached GetValueNode getValueNode,
                        @CachedLibrary("self") PythonObjectLibrary libSelf) {
            Object initValue = getValueNode.call(frame, self);
            PDict dict = getDict(self, libSelf, factory());
            Object[] state = new Object[]{initValue, self.getPos(), dict};
            return factory().createTuple(state);
        }
    }

    @Builtin(name = __SETSTATE__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class SetStateNode extends PythonBinaryBuiltinNode {
        @Specialization(guards = "checkExports(self)")
        Object doit(VirtualFrame frame, PBytesIO self, PTuple state,
                        @Cached SequenceStorageNodes.GetInternalObjectArrayNode getArray,
                        @Cached WriteNode writeNode,
                        @Cached PyNumberAsSizeNode asSizeNode,
                        @CachedLibrary(limit = "1") HashingStorageLibrary hlib,
                        @CachedLibrary(limit = "3") PythonObjectLibrary lib) {
            Object[] array = getArray.execute(state.getSequenceStorage());
            if (array.length < 3) {
                return notTuple(self, state);
            }
            /*
             * Reset the object to its default state. This is only needed to handle the case of
             * repeated calls to __setstate__.
             */
            self.setStringSize(0);
            self.setPos(0);
            /*
             * Set the value of the internal buffer. If state[0] does not support the buffer
             * protocol, bytesio_write will raise the appropriate TypeError.
             */
            writeNode.call(frame, self, array[0]);
            /*
             * Set carefully the position value. Alternatively, we could use the seek method instead
             * of modifying self.getPos() directly to better protect the object internal state
             * against erroneous (or malicious) inputs.
             */
            if (!lib.canBeJavaLong(array[1])) {
                throw raise(TypeError, SECOND_ITEM_OF_STATE_MUST_BE_AN_INTEGER_NOT_P, array[1]);
            }
            int pos = asSizeNode.executeExact(frame, array[1]);
            if (pos < 0) {
                throw raise(ValueError, POSITION_VALUE_CANNOT_BE_NEGATIVE);
            }
            self.setPos(pos);

            /* Set the dictionary of the instance variables. */
            if (!PGuards.isNone(array[2])) {
                if (!PGuards.isDict(array[2])) {
                    throw raise(TypeError, THIRD_ITEM_OF_STATE_SHOULD_BE_A_DICT_GOT_A_P, array[2]);
                }
                /*
                 * Alternatively, we could replace the internal dictionary completely. However, it
                 * seems more practical to just update it.
                 */
                PDict dict = getDict(self, lib, factory());
                hlib.addAllToOther(((PDict) array[2]).getDictStorage(), dict.getDictStorage());
            }
            return PNone.NONE;
        }

        protected static boolean checkExports(PBytesIO self) {
            return self.getExports() == 0;
        }

        @Specialization(guards = "!checkExports(self)")
        @SuppressWarnings("unused")
        Object exportsError(PBytesIO self, Object state) {
            throw raise(BufferError, EXISTING_EXPORTS_OF_DATA_OBJECT_CANNOT_BE_RE_SIZED);
        }

        @Specialization(guards = {"checkExports(self)", "!isPTuple(state)"})
        Object notTuple(PBytesIO self, Object state) {
            throw raise(TypeError, P_SETSTATE_ARGUMENT_SHOULD_BE_D_TUPLE_GOT_P, self, 3, state);
        }
    }

    @Builtin(name = FLUSH, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class FlushNode extends ClosedCheckPythonUnaryBuiltinNode {

        @Specialization(guards = "self.hasBuf()")
        static Object doit(@SuppressWarnings("unused") PBytesIO self) {
            return PNone.NONE;
        }
    }

    @Builtin(name = SEEKABLE, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class SeekableNode extends ClosedCheckPythonUnaryBuiltinNode {

        @Specialization(guards = "self.hasBuf()")
        static boolean seekable(@SuppressWarnings("unused") PBytesIO self) {
            return true;
        }
    }

    @Builtin(name = READABLE, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class ReadableNode extends ClosedCheckPythonUnaryBuiltinNode {

        @Specialization(guards = "self.hasBuf()")
        static boolean readable(@SuppressWarnings("unused") PBytesIO self) {
            return true;
        }
    }

    @Builtin(name = WRITABLE, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class WritableNode extends ClosedCheckPythonUnaryBuiltinNode {

        @Specialization(guards = "self.hasBuf()")
        static boolean writable(@SuppressWarnings("unused") PBytesIO self) {
            return true;
        }
    }

    @Builtin(name = CLOSED, minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    abstract static class ClosedNode extends PythonUnaryBuiltinNode {

        @Specialization
        static boolean closed(PBytesIO self) {
            return !self.hasBuf();
        }
    }

    @Builtin(name = CLOSE, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class CloseNode extends PythonUnaryBuiltinNode {

        @Specialization(guards = "checkExports(self)")
        static Object close(PBytesIO self) {
            self.setBuf(null);
            return PNone.NONE;
        }

        protected static boolean checkExports(PBytesIO self) {
            return self.getExports() == 0;
        }

        @Specialization(guards = "!checkExports(self)")
        Object exportsError(@SuppressWarnings("unused") PBytesIO self) {
            throw raise(BufferError, EXISTING_EXPORTS_OF_DATA_OBJECT_CANNOT_BE_RE_SIZED);
        }

    }

    @Builtin(name = __NEXT__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class IternextNode extends ClosedCheckPythonUnaryBuiltinNode {

        @Specialization(guards = "self.hasBuf()")
        Object doit(PBytesIO self,
                        @Cached SequenceStorageNodes.GetInternalByteArrayNode getBytes) {
            int n = scanEOL(self, -1, getBytes);
            if (n == 0) {
                throw raise(StopIteration);
            }
            return readBytes(self, n, getBytes, factory());
        }
    }
}
