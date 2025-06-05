/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.modules.zlib;

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.ZlibDecompressor;
import static com.oracle.graal.python.builtins.modules.zlib.ZLibModuleBuiltins.DEF_BUF_SIZE;
import static com.oracle.graal.python.builtins.modules.zlib.ZLibModuleBuiltins.MAX_WBITS;
import static com.oracle.graal.python.builtins.modules.zlib.ZlibNodes.Z_OK;
import static com.oracle.graal.python.builtins.modules.zlib.ZlibNodes.Z_STREAM_ERROR;
import static com.oracle.graal.python.nodes.ErrorMessages.ERROR_D_S_S;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.ZLibError;

import java.util.List;
import java.util.zip.Inflater;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.annotations.ArgumentClinic;
import com.oracle.graal.python.annotations.Slot;
import com.oracle.graal.python.annotations.Slot.SlotKind;
import com.oracle.graal.python.annotations.Slot.SlotSignature;
import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.modules.zlib.ZLibModuleBuiltins.ExpectByteLikeNode;
import com.oracle.graal.python.builtins.objects.buffer.PythonBufferAccessLibrary;
import com.oracle.graal.python.builtins.objects.bytes.BytesNodes;
import com.oracle.graal.python.builtins.objects.bytes.PBytes;
import com.oracle.graal.python.builtins.objects.type.TpSlots;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.builtins.PythonClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonTernaryClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.clinic.ArgumentClinicProvider;
import com.oracle.graal.python.runtime.IndirectCallData;
import com.oracle.graal.python.runtime.NFIZlibSupport;
import com.oracle.graal.python.runtime.NativeLibrary;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.object.PFactory;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.NonIdempotent;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.Node;

@CoreFunctions(extendClasses = ZlibDecompressor)
public final class ZlibDecompressorBuiltins extends PythonBuiltins {

    public static final TpSlots SLOTS = ZlibDecompressorBuiltinsSlotsGen.SLOTS;

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return ZlibDecompressorBuiltinsFactory.getFactories();
    }

    @ImportStatic(ZLibModuleBuiltins.class)
    @Slot(value = SlotKind.tp_new, isComplex = true)
    @SlotSignature(name = "_ZlibDecompressor", minNumOfPositionalArgs = 1, takesVarArgs = true, takesVarKeywordArgs = true, //
                    parameterNames = {"$cls", "wbits", "zdict"})
    @ArgumentClinic(name = "wbits", conversion = ArgumentClinic.ClinicConversion.Int, defaultValue = "ZLibModuleBuiltins.MAX_WBITS", useDefaultForNone = true)
    @ArgumentClinic(name = "zdict", conversionClass = ExpectByteLikeNode.class, defaultValue = "ZLibModuleBuiltins.EMPTY_BYTE_ARRAY", useDefaultForNone = true)
    @GenerateNodeFactory
    abstract static class ZlibDecompressorNewNode extends PythonClinicBuiltinNode {

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return ZlibDecompressorBuiltinsClinicProviders.ZlibDecompressorNewNodeClinicProviderGen.INSTANCE;
        }

        @NonIdempotent
        protected boolean useNative() {
            return PythonContext.get(this).getNFIZlibSupport().isAvailable();
        }

        protected static boolean isValidWBitRange(int wbits) {
            return wbits < -7 || (wbits > 7 && wbits <= MAX_WBITS) || (wbits > (MAX_WBITS + 9) && wbits <= (MAX_WBITS + 16));
        }

        @Specialization(guards = {"useNative()"})
        static Object doNative(@SuppressWarnings("unused") Object type, int wbits, byte[] zdict,
                        @Bind("this") Node inliningTarget,
                        @Bind PythonContext context,
                        @Cached NativeLibrary.InvokeNativeFunction createCompObject,
                        @Cached NativeLibrary.InvokeNativeFunction decompressObjInit,
                        @Cached ZlibNodes.ZlibNativeErrorHandling errorHandling) {
            NFIZlibSupport zlibSupport = context.getNFIZlibSupport();
            Object zst = zlibSupport.createCompObject(createCompObject);

            int err;
            if (zdict.length > 0) {
                err = zlibSupport.decompressObjInitWithDict(zst, wbits, context.getEnv().asGuestValue(zdict), zdict.length, decompressObjInit);
            } else {
                err = zlibSupport.decompressObjInit(zst, wbits, decompressObjInit);
            }
            if (err != Z_OK) {
                errorHandling.execute(inliningTarget, zst, err, zlibSupport, true);
            }
            return PFactory.createNativeZlibDecompressorObject(context.getLanguage(inliningTarget), zst, zlibSupport);
        }

        @TruffleBoundary
        @Specialization(guards = {"!useNative()", "isValidWBitRange(wbits)"})
        static Object doJava(@SuppressWarnings("unused") Object type, int wbits, byte[] zdict) {
            // wbits < 0: generate a RAW stream, i.e., no wrapping
            // wbits 25..31: gzip container, i.e., no wrapping
            // Otherwise: wrap stream with zlib header and trailer
            boolean isRAW = wbits < 0;
            Inflater inflater = new Inflater(isRAW || wbits > (MAX_WBITS + 9));
            if (isRAW && zdict.length > 0) {
                inflater.setDictionary(zdict);
            }
            PythonLanguage language = PythonLanguage.get(null);
            ZlibDecompressorObject obj = PFactory.createJavaZlibDecompressorObject(language, inflater, wbits, zdict);
            obj.setUnusedData(PFactory.createEmptyBytes(language));
            obj.setUnconsumedTail(PFactory.createEmptyBytes(language));
            return obj;
        }
    }

    @Builtin(name = "decompress", minNumOfPositionalArgs = 2, numOfPositionalOnlyArgs = 2, parameterNames = {"$self", "data", "max_length"})
    @ArgumentClinic(name = "data", conversion = ArgumentClinic.ClinicConversion.ReadableBuffer)
    @ArgumentClinic(name = "max_length", conversion = ArgumentClinic.ClinicConversion.Index, defaultValue = "-1", useDefaultForNone = true)
    @GenerateNodeFactory
    abstract static class DecompressNode extends PythonTernaryClinicBuiltinNode {

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return ZlibDecompressorBuiltinsClinicProviders.DecompressNodeClinicProviderGen.INSTANCE;
        }

        @Specialization
        static PBytes decompress(VirtualFrame frame, ZlibDecompressorObject self, Object buffer, int maxLength,
                        @Bind("this") Node inliningTarget,
                        @Bind PythonLanguage language,
                        @CachedLibrary(limit = "3") PythonBufferAccessLibrary bufferLib,
                        @Cached("createFor(this)") IndirectCallData indirectCallData,
                        @Cached DecompressBufInnerNode innerNode,
                        @Cached PRaiseNode raiseNode) {
            try {
                if (!self.isInitialized()) {
                    throw raiseNode.raise(inliningTarget, ZLibError, ERROR_D_S_S, Z_STREAM_ERROR, "while decompressing data", "inconsistent stream state");
                }
                byte[] bytes = bufferLib.getInternalOrCopiedByteArray(buffer);
                int len = bufferLib.getBufferLength(buffer);

                PBytes tail = self.getUnconsumedTail();
                int tailLen = tail == null ? 0 : bufferLib.getBufferLength(tail);
                if (tailLen > 0) {
                    byte[] tailBytes = bufferLib.getInternalOrCopiedByteArray(self.getUnconsumedTail());
                    byte[] tmp = new byte[tailLen + len];
                    PythonUtils.arraycopy(tailBytes, 0, tmp, 0, tailLen);
                    PythonUtils.arraycopy(bytes, 0, tmp, tailLen, len);
                    bytes = tmp;
                    len += tailLen;
                }

                return PFactory.createBytes(language, innerNode.execute(frame, inliningTarget, self, bytes, len, maxLength));
            } finally {
                bufferLib.release(buffer, frame, indirectCallData);
            }
        }

        @GenerateInline
        @GenerateCached(false)
        abstract static class DecompressBufInnerNode extends Node {
            abstract byte[] execute(VirtualFrame frame, Node inliningTarget, Object self, byte[] bytes, int length, int maxLength);

            @Specialization(guards = {"self.isNative()"})
            static byte[] doNative(Node inliningTarget, ZlibDecompressorObject self, byte[] bytes, int length, int maxLength,
                            @Cached ZlibNodes.ZlibNativeDecompressor decompress) {
                synchronized (self) {
                    return decompress.execute(inliningTarget, self, PythonContext.get(inliningTarget), bytes, length, maxLength);
                }
            }

            @Specialization(guards = {"!self.isNative()"})
            static byte[] doJava(VirtualFrame frame, Node inliningTarget, ZlibDecompressorObject self, byte[] bytes, int length, int maxLength,
                            @Cached(inline = false) BytesNodes.ToBytesNode toBytes) {
                byte[] ret = ZlibNodes.JavaDecompressor.execute(frame, self.getStream(), bytes, length, maxLength, DEF_BUF_SIZE, inliningTarget, toBytes);
                if (self.isEof()) {
                    self.setNeedsInput(false);
                } else if (self.getUnconsumedTail() == null || self.getUnconsumedTail().getSequenceStorage().length() == 0) {
                    self.setNeedsInput(true);
                } else {
                    self.setNeedsInput(false);
                }
                return ret;
            }
        }
    }

    @Builtin(name = "unused_data", minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    abstract static class UnusedDataNode extends PythonUnaryBuiltinNode {
        @Specialization(guards = {"self.isInitialized()", "self.isNative()"})
        static PBytes doit(ZlibDecompressorObject self,
                        @Bind("this") Node inliningTarget,
                        @Bind PythonContext context,
                        @Cached ZlibNodes.GetNativeBufferNode getBuffer) {
            synchronized (self) {
                assert self.isInitialized();
                return PFactory.createBytes(context.getLanguage(inliningTarget), getBuffer.getUnusedDataBuffer(inliningTarget, self.getZst(), context));
            }
        }

        @Specialization(guards = {"!self.isInitialized()", "self.isNative()"})
        static PBytes doeof(ZlibDecompressorObject self) {
            return self.getUnusedData();
        }

        @Specialization(guards = "!self.isNative()")
        static PBytes doit(ZlibDecompressorObject self) {
            return self.getUnusedData();
        }
    }

    @Builtin(name = "eof", minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    abstract static class EOFNode extends PythonUnaryBuiltinNode {
        @Specialization(guards = {"self.isEof() || !self.isInitialized()", "self.isNative()"})
        static boolean doit(ZlibDecompressorObject self) {
            return self.isEof();
        }

        @Specialization(guards = {"!self.isEof()", "self.isInitialized()", "self.isNative()"})
        boolean doNative(ZlibDecompressorObject self,
                        @Cached NativeLibrary.InvokeNativeFunction getEOF) {
            synchronized (self) {
                assert self.isInitialized();
                NFIZlibSupport zlibSupport = PythonContext.get(this).getNFIZlibSupport();
                self.setEof(zlibSupport.getEOF(self.getZst(), getEOF) == 1);
                return self.isEof();
            }
        }

        @Specialization(guards = "!self.isNative()")
        static boolean doJava(ZlibDecompressorObject self) {
            return self.isEof();
        }
    }

    @Builtin(name = "needs_input", minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    abstract static class NeedsInputNode extends PythonUnaryBuiltinNode {

        @Specialization
        static boolean doit(ZlibDecompressorObject self) {
            return self.isNeedsInput();
        }
    }
}
