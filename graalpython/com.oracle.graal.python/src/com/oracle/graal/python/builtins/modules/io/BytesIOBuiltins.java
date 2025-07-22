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

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.OverflowError;
import static com.oracle.graal.python.builtins.modules.io.BufferedIOUtil.SEEK_CUR;
import static com.oracle.graal.python.builtins.modules.io.BufferedIOUtil.SEEK_END;
import static com.oracle.graal.python.builtins.modules.io.BufferedIOUtil.SEEK_SET;
import static com.oracle.graal.python.builtins.modules.io.IONodes.J_CLOSE;
import static com.oracle.graal.python.builtins.modules.io.IONodes.J_CLOSED;
import static com.oracle.graal.python.builtins.modules.io.IONodes.J_FLUSH;
import static com.oracle.graal.python.builtins.modules.io.IONodes.J_GETBUFFER;
import static com.oracle.graal.python.builtins.modules.io.IONodes.J_GETVALUE;
import static com.oracle.graal.python.builtins.modules.io.IONodes.J_ISATTY;
import static com.oracle.graal.python.builtins.modules.io.IONodes.J_READ;
import static com.oracle.graal.python.builtins.modules.io.IONodes.J_READ1;
import static com.oracle.graal.python.builtins.modules.io.IONodes.J_READABLE;
import static com.oracle.graal.python.builtins.modules.io.IONodes.J_READINTO;
import static com.oracle.graal.python.builtins.modules.io.IONodes.J_READLINE;
import static com.oracle.graal.python.builtins.modules.io.IONodes.J_READLINES;
import static com.oracle.graal.python.builtins.modules.io.IONodes.J_SEEK;
import static com.oracle.graal.python.builtins.modules.io.IONodes.J_SEEKABLE;
import static com.oracle.graal.python.builtins.modules.io.IONodes.J_TELL;
import static com.oracle.graal.python.builtins.modules.io.IONodes.J_TRUNCATE;
import static com.oracle.graal.python.builtins.modules.io.IONodes.J_WRITABLE;
import static com.oracle.graal.python.builtins.modules.io.IONodes.J_WRITE;
import static com.oracle.graal.python.builtins.modules.io.IONodes.J_WRITELINES;
import static com.oracle.graal.python.nodes.ErrorMessages.INVALID_WHENCE_D_SHOULD_BE_0_1_OR_2;
import static com.oracle.graal.python.nodes.ErrorMessages.IO_CLOSED;
import static com.oracle.graal.python.nodes.ErrorMessages.NEGATIVE_SEEK_VALUE_D;
import static com.oracle.graal.python.nodes.ErrorMessages.NEGATIVE_SIZE_VALUE_D;
import static com.oracle.graal.python.nodes.ErrorMessages.NEW_POSITION_TOO_LARGE;
import static com.oracle.graal.python.nodes.ErrorMessages.POSITION_VALUE_CANNOT_BE_NEGATIVE;
import static com.oracle.graal.python.nodes.ErrorMessages.P_SETSTATE_ARGUMENT_SHOULD_BE_D_TUPLE_GOT_P;
import static com.oracle.graal.python.nodes.ErrorMessages.SECOND_ITEM_OF_STATE_MUST_BE_AN_INTEGER_NOT_P;
import static com.oracle.graal.python.nodes.ErrorMessages.THIRD_ITEM_OF_STATE_SHOULD_BE_A_DICT_GOT_A_P;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___GETSTATE__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___SETSTATE__;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.TypeError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.ValueError;

import java.util.List;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.annotations.ArgumentClinic;
import com.oracle.graal.python.annotations.Slot;
import com.oracle.graal.python.annotations.Slot.SlotKind;
import com.oracle.graal.python.annotations.Slot.SlotSignature;
import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.buffer.PythonBufferAccessLibrary;
import com.oracle.graal.python.builtins.objects.buffer.PythonBufferAcquireLibrary;
import com.oracle.graal.python.builtins.objects.bytes.PByteArray;
import com.oracle.graal.python.builtins.objects.bytes.PBytes;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageAddAllToOther;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes.GetInternalObjectArrayNode;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.builtins.objects.type.TpSlots;
import com.oracle.graal.python.builtins.objects.type.TypeNodes;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotIterNext.TpIterNextBuiltin;
import com.oracle.graal.python.lib.IteratorExhausted;
import com.oracle.graal.python.lib.PyIndexCheckNode;
import com.oracle.graal.python.lib.PyIterNextNode;
import com.oracle.graal.python.lib.PyMemoryViewFromObject;
import com.oracle.graal.python.lib.PyNumberAsSizeNode;
import com.oracle.graal.python.lib.PyObjectGetIter;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonTernaryClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.clinic.ArgumentClinicProvider;
import com.oracle.graal.python.nodes.object.GetOrCreateDictNode;
import com.oracle.graal.python.runtime.IndirectCallData;
import com.oracle.graal.python.runtime.object.PFactory;
import com.oracle.graal.python.runtime.sequence.storage.SequenceStorage;
import com.oracle.graal.python.util.ArrayBuilder;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.Node;

@CoreFunctions(extendClasses = PythonBuiltinClassType.PBytesIO)
public final class BytesIOBuiltins extends PythonBuiltins {

    public static final TpSlots SLOTS = BytesIOBuiltinsSlotsGen.SLOTS;

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return BytesIOBuiltinsFactory.getFactories();
    }

    abstract static class ClosedCheckPythonUnaryBuiltinNode extends PythonUnaryBuiltinNode {
        @Specialization(guards = "!self.hasBuf()")
        @SuppressWarnings("unused")
        static Object closedError(PBytesIO self,
                        @Bind Node inliningTarget) {
            throw PRaiseNode.raiseStatic(inliningTarget, ValueError, IO_CLOSED);
        }
    }

    abstract static class ClosedCheckPythonBinaryBuiltinNode extends PythonBinaryBuiltinNode {
        @Specialization(guards = "!self.hasBuf()")
        @SuppressWarnings("unused")
        static Object closedError(PBytesIO self, Object arg,
                        @Bind Node inliningTarget) {
            throw PRaiseNode.raiseStatic(inliningTarget, ValueError, IO_CLOSED);
        }
    }

    abstract static class ClosedCheckPythonBinaryClinicBuiltinNode extends PythonBinaryClinicBuiltinNode {

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            throw CompilerDirectives.shouldNotReachHere();
        }

        @Specialization(guards = "!self.hasBuf()")
        @SuppressWarnings("unused")
        static Object closedError(PBytesIO self, Object arg,
                        @Bind Node inliningTarget) {
            throw PRaiseNode.raiseStatic(inliningTarget, ValueError, IO_CLOSED);
        }
    }

    protected static final byte[] EMPTY_BYTE_ARRAY = PythonUtils.EMPTY_BYTE_ARRAY;

    @Slot(value = SlotKind.tp_new, isComplex = true)
    @SlotSignature(name = "BytesIO", minNumOfPositionalArgs = 1, takesVarArgs = true, takesVarKeywordArgs = true)
    @GenerateNodeFactory
    public abstract static class BytesIONode extends PythonBuiltinNode {
        @Specialization
        static PBytesIO doNew(Object cls, @SuppressWarnings("unused") Object arg,
                        @Bind PythonLanguage language,
                        @Cached TypeNodes.GetInstanceShape getInstanceShape) {
            // data filled in subsequent __init__ call - see BytesIONodeBuiltins.InitNode
            PBytesIO bytesIO = PFactory.createBytesIO(language, cls, getInstanceShape.execute(cls));
            bytesIO.setBuf(PFactory.createByteArray(language, PythonUtils.EMPTY_BYTE_ARRAY));
            return bytesIO;
        }
    }

    @Slot(value = SlotKind.tp_init, isComplex = true)
    @SlotSignature(name = "BytesIO", minNumOfPositionalArgs = 1, parameterNames = {"$self", "initial_bytes"})
    @GenerateNodeFactory
    public abstract static class InitNode extends PythonBinaryBuiltinNode {

        @Specialization
        @SuppressWarnings("unused")
        static PNone init(PBytesIO self, PNone initvalue,
                        @Bind Node inliningTarget,
                        @Shared @Cached PRaiseNode raiseNode) {
            self.checkExports(inliningTarget, raiseNode);
            self.setPos(0);
            return PNone.NONE;
        }

        @Specialization(guards = "!isPNone(initvalue)")
        static PNone init(VirtualFrame frame, PBytesIO self, Object initvalue,
                        @Bind Node inliningTarget,
                        @Cached WriteNode writeNode,
                        @Shared @Cached PRaiseNode raiseNode) {
            /* In case, __init__ is called multiple times. */
            self.setStringSize(0);
            self.setPos(0);
            self.checkExports(inliningTarget, raiseNode);
            writeNode.execute(frame, self, initvalue);
            self.setPos(0);
            return PNone.NONE;
        }
    }

    static PBytes readBytes(PBytesIO self, int size,
                    PythonBufferAccessLibrary bufferLib,
                    PythonLanguage language) {
        if (size == 0) {
            return PFactory.createEmptyBytes(language);
        }

        assert (size <= self.getStringSize());
        PByteArray buffer = self.getBuf();
        if (self.getPos() == 0 && bufferLib.hasInternalByteArray(buffer) && self.getExports() == 0 && size > bufferLib.getBufferLength(buffer) / 2) {
            self.incPos(size);
            self.markEscaped();
            return PFactory.createBytes(language, bufferLib.getInternalByteArray(buffer), size);
        }

        PBytes output = PFactory.createBytes(language, bufferLib.getCopyOfRange(buffer, self.getPos(), self.getPos() + size));
        self.incPos(size);
        return output;
    }

    @Builtin(name = J_READ, minNumOfPositionalArgs = 1, parameterNames = {"$self", "size"})
    @ArgumentClinic(name = "size", conversion = ArgumentClinic.ClinicConversion.Int, defaultValue = "-1", useDefaultForNone = true)
    @GenerateNodeFactory
    abstract static class ReadNode extends ClosedCheckPythonBinaryClinicBuiltinNode {

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return BytesIOBuiltinsClinicProviders.ReadNodeClinicProviderGen.INSTANCE;
        }

        @Specialization(guards = "self.hasBuf()")
        static Object read(PBytesIO self, int len,
                        @Bind PythonLanguage language,
                        @CachedLibrary(limit = "1") PythonBufferAccessLibrary bufferLib) {
            int size = len;
            /* adjust invalid sizes */
            int n = self.getStringSize() - self.getPos();
            if (size < 0 || size > n) {
                size = n;
                if (size < 0) {
                    size = 0;
                }
            }
            return readBytes(self, size, bufferLib, language);
        }
    }

    @Builtin(name = J_READ1, minNumOfPositionalArgs = 1, parameterNames = {"$self", "size"})
    @ArgumentClinic(name = "size", conversion = ArgumentClinic.ClinicConversion.Int, defaultValue = "-1", useDefaultForNone = true)
    @GenerateNodeFactory
    abstract static class Read1Node extends ReadNode {
        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return BytesIOBuiltinsClinicProviders.Read1NodeClinicProviderGen.INSTANCE;
        }
    }

    static int scanEOL(PBytesIO self, int l,
                    PythonBufferAccessLibrary bufferLib) {
        PByteArray buffer = self.getBuf();
        byte[] buf = bufferLib.getInternalOrCopiedByteArray(buffer);
        return scanEOL(self, l, buf);
    }

    private static int scanEOL(PBytesIO self, int l, byte[] buf) {
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

    @Builtin(name = J_READLINE, minNumOfPositionalArgs = 1, parameterNames = {"$self", "size"})
    @ArgumentClinic(name = "size", conversion = ArgumentClinic.ClinicConversion.Int, defaultValue = "-1", useDefaultForNone = true)
    @GenerateNodeFactory
    abstract static class ReadlineNode extends ClosedCheckPythonBinaryClinicBuiltinNode {

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return BytesIOBuiltinsClinicProviders.ReadlineNodeClinicProviderGen.INSTANCE;
        }

        @Specialization(guards = "self.hasBuf()")
        Object readline(PBytesIO self, int size,
                        @Bind PythonLanguage language,
                        @CachedLibrary(limit = "1") PythonBufferAccessLibrary bufferLib) {
            int n = scanEOL(self, size, bufferLib);
            return readBytes(self, n, bufferLib, language);
        }
    }

    @Builtin(name = J_READLINES, minNumOfPositionalArgs = 1, parameterNames = {"$self", "size"})
    @ArgumentClinic(name = "size", conversion = ArgumentClinic.ClinicConversion.Int, defaultValue = "-1", useDefaultForNone = true)
    @GenerateNodeFactory
    abstract static class ReadlinesNode extends ClosedCheckPythonBinaryClinicBuiltinNode {

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return BytesIOBuiltinsClinicProviders.ReadlinesNodeClinicProviderGen.INSTANCE;
        }

        @Specialization(guards = "self.hasBuf()")
        static Object readlines(PBytesIO self, int maxsize,
                        @Bind PythonLanguage language,
                        @CachedLibrary(limit = "1") PythonBufferAccessLibrary bufferLib) {
            ArrayBuilder<Object> result = new ArrayBuilder<>();

            int n;
            PByteArray buffer = self.getBuf();
            byte[] buf = bufferLib.getInternalOrCopiedByteArray(buffer);
            int cur = self.getPos();
            int size = 0;
            while ((n = scanEOL(self, -1, buf)) != 0) {
                self.incPos(n);
                PBytes line = PFactory.createBytes(language, PythonUtils.arrayCopyOfRange(buf, cur, cur + n));
                result.add(line);
                size += n;
                if (maxsize > 0 && size >= maxsize) {
                    break;
                }
                cur += n;
            }
            return PFactory.createList(language, result.toArray(new Object[0]));
        }
    }

    @Builtin(name = J_READINTO, minNumOfPositionalArgs = 2, numOfPositionalOnlyArgs = 2, parameterNames = {"$self", "buffer"})
    @ArgumentClinic(name = "buffer", conversion = ArgumentClinic.ClinicConversion.WritableBuffer)
    @GenerateNodeFactory
    abstract static class ReadIntoNode extends ClosedCheckPythonBinaryClinicBuiltinNode {

        @Specialization(guards = "self.hasBuf()")
        static Object readinto(VirtualFrame frame, PBytesIO self, Object buffer,
                        @Cached("createFor($node)") IndirectCallData indirectCallData,
                        @CachedLibrary(limit = "3") PythonBufferAccessLibrary bufferLib) {
            try {
                /* adjust invalid sizes */
                int len = bufferLib.getBufferLength(buffer);
                int n = self.getStringSize() - self.getPos();
                if (len > n) {
                    len = n;
                    if (len < 0) {
                        return 0;
                    }
                }

                bufferLib.readIntoBuffer(self.getBuf(), self.getPos(), buffer, 0, len, bufferLib);
                assert (self.getPos() + len < Integer.MAX_VALUE);
                assert (len >= 0);
                self.incPos(len);

                return len;
            } finally {
                bufferLib.release(buffer, frame, indirectCallData);
            }
        }

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return BytesIOBuiltinsClinicProviders.ReadIntoNodeClinicProviderGen.INSTANCE;
        }
    }

    @Builtin(name = J_TRUNCATE, minNumOfPositionalArgs = 1, parameterNames = {"$self", "size"})
    @GenerateNodeFactory
    abstract static class TruncateNode extends ClosedCheckPythonBinaryBuiltinNode {

        @Specialization(guards = "self.hasBuf()")
        static Object truncate(PBytesIO self, int size,
                        @Bind Node inliningTarget,
                        @Bind PythonLanguage language,
                        @Shared("lib") @CachedLibrary(limit = "1") PythonBufferAccessLibrary bufferLib,
                        @Shared @Cached PRaiseNode raiseNode) {
            self.checkExports(inliningTarget, raiseNode);
            if (size < 0) {
                throw raiseNode.raise(inliningTarget, ValueError, NEGATIVE_SIZE_VALUE_D, size);
            }
            if (size < self.getStringSize()) {
                self.unshareAndResize(bufferLib, language, size, true);
                self.setStringSize(size);
            }
            return size;
        }

        @Specialization(guards = "self.hasBuf()")
        static Object truncate(PBytesIO self, @SuppressWarnings("unused") PNone size,
                        @Bind Node inliningTarget,
                        @Bind PythonLanguage language,
                        @Shared("lib") @CachedLibrary(limit = "1") PythonBufferAccessLibrary bufferLib,
                        @Shared @Cached PRaiseNode raiseNode) {
            return truncate(self, self.getPos(), inliningTarget, language, bufferLib, raiseNode);
        }

        @Specialization(guards = {"self.hasBuf()", "!isPNone(size)"})
        static Object truncate(VirtualFrame frame, PBytesIO self, Object size,
                        @Bind Node inliningTarget,
                        @Bind PythonLanguage language,
                        @Cached PyNumberAsSizeNode asSizeNode,
                        @Shared("lib") @CachedLibrary(limit = "1") PythonBufferAccessLibrary bufferLib,
                        @Exclusive @Cached PRaiseNode raiseNode) {
            return truncate(self, asSizeNode.executeExact(frame, inliningTarget, size), inliningTarget, language, bufferLib, raiseNode);
        }
    }

    @Builtin(name = J_WRITE, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class WriteNode extends ClosedCheckPythonBinaryBuiltinNode {

        @Specialization(guards = "self.hasBuf()", limit = "3")
        static Object doWrite(VirtualFrame frame, PBytesIO self, Object b,
                        @Bind Node inliningTarget,
                        @Bind PythonLanguage language,
                        @Cached("createFor($node)") IndirectCallData indirectCallData,
                        @CachedLibrary("b") PythonBufferAcquireLibrary acquireLib,
                        @CachedLibrary(limit = "2") PythonBufferAccessLibrary bufferLib,
                        @Cached PRaiseNode raiseNode) {
            self.checkExports(inliningTarget, raiseNode);
            Object buffer = acquireLib.acquireReadonly(b, frame, indirectCallData);
            try {
                int len = bufferLib.getBufferLength(buffer);
                if (len == 0) {
                    return 0;
                }
                int pos = self.getPos();
                int endpos = pos + len;
                self.unshareAndResize(bufferLib, language, endpos, false);
                bufferLib.readIntoBuffer(buffer, 0, self.getBuf(), pos, len, bufferLib);
                self.setPos(endpos);
                if (endpos > self.getStringSize()) {
                    self.setStringSize(endpos);
                }
                return len;
            } finally {
                bufferLib.release(buffer, frame, indirectCallData);
            }
        }
    }

    @Builtin(name = J_WRITELINES, minNumOfPositionalArgs = 2, parameterNames = {"$self", "lines"})
    @GenerateNodeFactory
    abstract static class WriteLinesNode extends ClosedCheckPythonBinaryBuiltinNode {
        @Specialization(guards = "self.hasBuf()")
        static Object writeLines(VirtualFrame frame, PBytesIO self, Object lines,
                        @Bind Node inliningTarget,
                        @Cached WriteNode writeNode,
                        @Cached PyObjectGetIter getIter,
                        @Cached PyIterNextNode nextNode,
                        @Cached PRaiseNode raiseNode) {
            self.checkExports(inliningTarget, raiseNode);
            Object iter = getIter.execute(frame, inliningTarget, lines);
            while (true) {
                Object line;
                try {
                    line = nextNode.execute(frame, inliningTarget, iter);
                } catch (IteratorExhausted e) {
                    break;
                }
                writeNode.execute(frame, self, line);
            }
            return PNone.NONE;
        }
    }

    @Builtin(name = J_ISATTY, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class IsAttyNode extends ClosedCheckPythonUnaryBuiltinNode {

        @SuppressWarnings("unused")
        @Specialization(guards = "self.hasBuf()")
        static boolean atty(PBytesIO self) {
            return false;
        }
    }

    @Builtin(name = J_TELL, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class TellNode extends ClosedCheckPythonUnaryBuiltinNode {

        @Specialization(guards = "self.hasBuf()")
        static Object tell(PBytesIO self) {
            return self.getPos();
        }
    }

    @Builtin(name = J_SEEK, minNumOfPositionalArgs = 2, parameterNames = {"$self", "pos", "whence"})
    @ArgumentClinic(name = "pos", conversion = ArgumentClinic.ClinicConversion.Index)
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
        static Object whenceError(@SuppressWarnings("unused") PBytesIO self, @SuppressWarnings("unused") int pos, int whence,
                        @Bind Node inliningTarget) {
            throw PRaiseNode.raiseStatic(inliningTarget, ValueError, INVALID_WHENCE_D_SHOULD_BE_0_1_OR_2, whence);
        }

        @SuppressWarnings("unused")
        @Specialization(guards = {"self.hasBuf()", "isLargePos(pos, self.getPos())", "whence == 1"})
        static Object largePos1(PBytesIO self, int pos, int whence,
                        @Bind Node inliningTarget) {
            throw PRaiseNode.raiseStatic(inliningTarget, OverflowError, NEW_POSITION_TOO_LARGE);
        }

        @SuppressWarnings("unused")
        @Specialization(guards = {"self.hasBuf()", "isLargePos(pos, self.getStringSize())", "whence == 2"})
        static Object largePos2(PBytesIO self, int pos, int whence,
                        @Bind Node inliningTarget) {
            throw PRaiseNode.raiseStatic(inliningTarget, OverflowError, NEW_POSITION_TOO_LARGE);
        }

        @Specialization(guards = {"self.hasBuf()", "pos < 0", "whence == 0"})
        static Object negPos(@SuppressWarnings("unused") PBytesIO self, int pos, @SuppressWarnings("unused") int whence,
                        @Bind Node inliningTarget) {
            throw PRaiseNode.raiseStatic(inliningTarget, ValueError, NEGATIVE_SEEK_VALUE_D, pos);
        }

        @SuppressWarnings("unused")
        @Specialization(guards = "!self.hasBuf()")
        static Object closedError(PBytesIO self, int pos, int whence,
                        @Bind Node inliningTarget) {
            throw PRaiseNode.raiseStatic(inliningTarget, ValueError, IO_CLOSED);
        }
    }

    @Builtin(name = J_GETBUFFER, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class GetBufferNode extends ClosedCheckPythonUnaryBuiltinNode {
        @Specialization
        static Object doit(VirtualFrame frame, PBytesIO self,
                        @CachedLibrary(limit = "1") PythonBufferAccessLibrary bufferLib,
                        @Bind Node inliningTarget,
                        @Bind PythonLanguage language,
                        @Cached PyMemoryViewFromObject memoryViewNode,
                        @Cached SequenceStorageNodes.SetLenNode setLenNode) {
            self.unshareIfNecessary(bufferLib, language);
            setLenNode.execute(inliningTarget, self.getBuf().getSequenceStorage(), self.getStringSize());
            PBytesIOBuffer buf = PFactory.createBytesIOBuf(language, self);
            return memoryViewNode.execute(frame, buf);
        }
    }

    @Builtin(name = J_GETVALUE, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class GetValueNode extends ClosedCheckPythonUnaryBuiltinNode {
        @Specialization(guards = "self.hasBuf()")
        static Object doCopy(PBytesIO self,
                        @Bind PythonLanguage language,
                        @CachedLibrary(limit = "1") PythonBufferAccessLibrary bufferLib) {
            if (bufferLib.hasInternalByteArray(self.getBuf()) && self.getExports() == 0) {
                self.markEscaped();
            }
            return PFactory.createBytes(language, bufferLib.getInternalOrCopiedByteArray(self.getBuf()), self.getStringSize());
        }
    }

    @Builtin(name = J___GETSTATE__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class GetStateNode extends ClosedCheckPythonUnaryBuiltinNode {
        @Specialization(guards = "self.hasBuf()")
        static Object doit(VirtualFrame frame, PBytesIO self,
                        @Bind Node inliningTarget,
                        @Bind PythonLanguage language,
                        @Cached GetValueNode getValueNode,
                        @Cached GetOrCreateDictNode getDict) {
            Object initValue = getValueNode.execute(frame, self);
            Object[] state = new Object[]{initValue, self.getPos(), getDict.execute(inliningTarget, self)};
            return PFactory.createTuple(language, state);
        }
    }

    @Builtin(name = J___SETSTATE__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class SetStateNode extends PythonBinaryBuiltinNode {
        @Specialization
        static Object doit(VirtualFrame frame, PBytesIO self, PTuple state,
                        @Bind Node inliningTarget,
                        @Cached GetInternalObjectArrayNode getArray,
                        @Cached WriteNode writeNode,
                        @Cached PyIndexCheckNode indexCheckNode,
                        @Cached PyNumberAsSizeNode asSizeNode,
                        @Cached GetOrCreateDictNode getDict,
                        @Cached HashingStorageAddAllToOther addAllToOtherNode,
                        @Cached PRaiseNode raiseNode) {
            self.checkExports(inliningTarget, raiseNode);
            SequenceStorage storage = state.getSequenceStorage();
            Object[] array = getArray.execute(inliningTarget, storage);
            if (storage.length() < 3) {
                return notTuple(self, state, raiseNode);
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
            writeNode.execute(frame, self, array[0]);
            /*
             * Set carefully the position value. Alternatively, we could use the seek method instead
             * of modifying self.getPos() directly to better protect the object internal state
             * against erroneous (or malicious) inputs.
             */
            if (!indexCheckNode.execute(inliningTarget, array[1])) {
                throw raiseNode.raise(inliningTarget, TypeError, SECOND_ITEM_OF_STATE_MUST_BE_AN_INTEGER_NOT_P, array[1]);
            }
            int pos = asSizeNode.executeExact(frame, inliningTarget, array[1]);
            if (pos < 0) {
                throw raiseNode.raise(inliningTarget, ValueError, POSITION_VALUE_CANNOT_BE_NEGATIVE);
            }
            self.setPos(pos);

            /* Set the dictionary of the instance variables. */
            if (!PGuards.isNone(array[2])) {
                if (!PGuards.isDict(array[2])) {
                    throw raiseNode.raise(inliningTarget, TypeError, THIRD_ITEM_OF_STATE_SHOULD_BE_A_DICT_GOT_A_P, array[2]);
                }
                /*
                 * Alternatively, we could replace the internal dictionary completely. However, it
                 * seems more practical to just update it.
                 */
                PDict dict = getDict.execute(inliningTarget, self);
                addAllToOtherNode.execute(frame, inliningTarget, ((PDict) array[2]).getDictStorage(), dict);
            }
            return PNone.NONE;
        }

        @Specialization(guards = "!isPTuple(state)")
        static Object notTuple(PBytesIO self, Object state,
                        @Bind Node inliningTarget) {
            throw PRaiseNode.raiseStatic(inliningTarget, TypeError, P_SETSTATE_ARGUMENT_SHOULD_BE_D_TUPLE_GOT_P, self, 3, state);
        }
    }

    @Builtin(name = J_FLUSH, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class FlushNode extends ClosedCheckPythonUnaryBuiltinNode {

        @Specialization(guards = "self.hasBuf()")
        static Object doit(@SuppressWarnings("unused") PBytesIO self) {
            return PNone.NONE;
        }
    }

    @Builtin(name = J_SEEKABLE, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class SeekableNode extends ClosedCheckPythonUnaryBuiltinNode {

        @Specialization(guards = "self.hasBuf()")
        static boolean seekable(@SuppressWarnings("unused") PBytesIO self) {
            return true;
        }
    }

    @Builtin(name = J_READABLE, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class ReadableNode extends ClosedCheckPythonUnaryBuiltinNode {

        @Specialization(guards = "self.hasBuf()")
        static boolean readable(@SuppressWarnings("unused") PBytesIO self) {
            return true;
        }
    }

    @Builtin(name = J_WRITABLE, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class WritableNode extends ClosedCheckPythonUnaryBuiltinNode {

        @Specialization(guards = "self.hasBuf()")
        static boolean writable(@SuppressWarnings("unused") PBytesIO self) {
            return true;
        }
    }

    @Builtin(name = J_CLOSED, minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    abstract static class ClosedNode extends PythonUnaryBuiltinNode {

        @Specialization
        static boolean closed(PBytesIO self) {
            return !self.hasBuf();
        }
    }

    @Builtin(name = J_CLOSE, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class CloseNode extends PythonUnaryBuiltinNode {

        @Specialization
        static Object close(PBytesIO self,
                        @Bind Node inliningTarget,
                        @Cached PRaiseNode raiseNode) {
            self.checkExports(inliningTarget, raiseNode);
            self.setBuf(null);
            return PNone.NONE;
        }
    }

    @Slot(value = SlotKind.tp_iternext, isComplex = true)
    @GenerateNodeFactory
    abstract static class IternextNode extends ClosedCheckPythonUnaryBuiltinNode {

        @Specialization(guards = "self.hasBuf()")
        static Object doit(PBytesIO self,
                        @Bind PythonLanguage language,
                        @CachedLibrary(limit = "1") PythonBufferAccessLibrary bufferLib) {
            int n = scanEOL(self, -1, bufferLib);
            if (n == 0) {
                throw TpIterNextBuiltin.iteratorExhausted();
            }
            return readBytes(self, n, bufferLib, language);
        }
    }
}
