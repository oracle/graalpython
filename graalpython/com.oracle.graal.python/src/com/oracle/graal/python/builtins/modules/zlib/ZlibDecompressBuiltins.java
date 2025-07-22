/*
 * Copyright (c) 2020, 2025, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.NotImplementedError;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.ZlibDecompress;
import static com.oracle.graal.python.builtins.modules.zlib.ZLibModuleBuiltins.DEF_BUF_SIZE;
import static com.oracle.graal.python.builtins.modules.zlib.ZlibNodes.Z_OK;
import static com.oracle.graal.python.builtins.modules.zlib.ZlibNodes.Z_STREAM_ERROR;
import static com.oracle.graal.python.nodes.ErrorMessages.ERROR_D_S_S;
import static com.oracle.graal.python.nodes.ErrorMessages.INCONSISTENT_STREAM_STATE;
import static com.oracle.graal.python.nodes.ErrorMessages.S_MUST_BE_GREATER_THAN_ZERO;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___COPY__;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.ValueError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.ZLibError;
import static com.oracle.graal.python.util.PythonUtils.toTruffleStringUncached;

import java.util.List;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.annotations.ArgumentClinic;
import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.buffer.PythonBufferAccessLibrary;
import com.oracle.graal.python.builtins.objects.bytes.BytesNodes;
import com.oracle.graal.python.builtins.objects.bytes.PBytes;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonTernaryClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.clinic.ArgumentClinicProvider;
import com.oracle.graal.python.runtime.IndirectCallData;
import com.oracle.graal.python.runtime.NFIZlibSupport;
import com.oracle.graal.python.runtime.NativeLibrary;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.object.PFactory;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.Node;

@CoreFunctions(extendClasses = ZlibDecompress)
public final class ZlibDecompressBuiltins extends PythonBuiltins {
    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return ZlibDecompressBuiltinsFactory.getFactories();
    }

    @Builtin(name = "decompress", minNumOfPositionalArgs = 2, numOfPositionalOnlyArgs = 2, parameterNames = {"$self", "data", "max_length"})
    @ArgumentClinic(name = "data", conversion = ArgumentClinic.ClinicConversion.ReadableBuffer)
    @ArgumentClinic(name = "max_length", conversion = ArgumentClinic.ClinicConversion.Index, defaultValue = "0", useDefaultForNone = true)
    @GenerateNodeFactory
    abstract static class DecompressNode extends PythonTernaryClinicBuiltinNode {

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return ZlibDecompressBuiltinsClinicProviders.DecompressNodeClinicProviderGen.INSTANCE;
        }

        @Specialization
        static PBytes decompress(VirtualFrame frame, ZLibCompObject self, Object buffer, int maxLength,
                        @Bind Node inliningTarget,
                        @Bind PythonLanguage language,
                        @CachedLibrary(limit = "3") PythonBufferAccessLibrary bufferLib,
                        @Cached("createFor($node)") IndirectCallData indirectCallData,
                        @Cached DecompressInnerNode innerNode,
                        @Cached PRaiseNode raiseNode) {
            try {
                if (!self.isInitialized()) {
                    throw raiseNode.raise(inliningTarget, ZLibError, ERROR_D_S_S, Z_STREAM_ERROR, "while decompressing data", "inconsistent stream state");
                }
                if (maxLength < 0) {
                    throw raiseNode.raise(inliningTarget, ValueError, S_MUST_BE_GREATER_THAN_ZERO, "max_length");
                }
                byte[] bytes = bufferLib.getInternalOrCopiedByteArray(buffer);
                int len = bufferLib.getBufferLength(buffer);
                return PFactory.createBytes(language, innerNode.execute(frame, inliningTarget, self, bytes, len, maxLength));
            } finally {
                bufferLib.release(buffer, frame, indirectCallData);
            }
        }

        @GenerateInline
        @GenerateCached(false)
        abstract static class DecompressInnerNode extends Node {
            abstract byte[] execute(VirtualFrame frame, Node inliningTarget, Object self, byte[] bytes, int length, int maxLength);

            @Specialization
            static byte[] doNative(Node inliningTarget, NativeZlibCompObject self, byte[] bytes, int length, int maxLength,
                            @Cached ZlibNodes.ZlibNativeDecompressObj decompressObj) {
                synchronized (self) {
                    return decompressObj.execute(inliningTarget, self, PythonContext.get(inliningTarget), bytes, length, maxLength);
                }
            }

            @Specialization
            static byte[] doJava(VirtualFrame frame, Node inliningTarget, JavaDecompress self, byte[] bytes, int length, int maxLength,
                            @Cached(inline = false) BytesNodes.ToBytesNode toBytes) {
                return self.decompress(frame, bytes, length, maxLength, DEF_BUF_SIZE, inliningTarget, toBytes);
            }
        }
    }

    @GenerateInline
    @GenerateCached(false)
    abstract static class BaseCopyNode extends PNodeWithContext {

        public abstract Object execute(Node inliningTarget, ZLibCompObject self);

        @Specialization(guards = "self.isInitialized()")
        static Object doNative(Node inliningTarget, NativeZlibCompObject self,
                        @Bind PythonContext context,
                        @Cached(inline = false) NativeLibrary.InvokeNativeFunction createCompObject,
                        @Cached(inline = false) NativeLibrary.InvokeNativeFunction decompressObjCopy,
                        @Cached(inline = false) NativeLibrary.InvokeNativeFunction deallocateStream,
                        @Cached ZlibNodes.ZlibNativeErrorHandling errorHandling) {
            synchronized (self) {
                assert self.isInitialized();
                NFIZlibSupport zlibSupport = context.getNFIZlibSupport();
                Object zstNewCopy = zlibSupport.createCompObject(createCompObject);
                int err = zlibSupport.decompressObjCopy(self.getZst(), zstNewCopy, decompressObjCopy);
                if (err != Z_OK) {
                    zlibSupport.deallocateStream(zstNewCopy, deallocateStream);
                    errorHandling.execute(inliningTarget, self.getZst(), err, zlibSupport, false);
                }
                ZLibCompObject copy = PFactory.createNativeZLibCompObjectDecompress(context.getLanguage(inliningTarget), zstNewCopy, zlibSupport);
                copy.setEof(self.isEof());
                return copy;
            }
        }

        @Specialization(guards = {"self.isInitialized()", "self.canCopy()"})
        static Object doJava(Node inliningTarget, JavaDecompress self) {
            return self.copy(inliningTarget);
        }

        @SuppressWarnings("unused")
        @Specialization(guards = {"self.isInitialized()", "!self.canCopy()"})
        static PNone error(JavaDecompress self,
                        @Bind Node inliningTarget) {
            throw PRaiseNode.raiseStatic(inliningTarget, NotImplementedError, toTruffleStringUncached("JDK based zlib doesn't support copying"));
        }

        @SuppressWarnings("unused")
        @Specialization(guards = "!self.isInitialized()")
        static PNone error(ZLibCompObject self,
                        @Bind Node inliningTarget) {
            throw PRaiseNode.raiseStatic(inliningTarget, ValueError, INCONSISTENT_STREAM_STATE);
        }
    }

    @Builtin(name = "copy", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class CopyNode extends PythonUnaryBuiltinNode {
        @Specialization
        static Object doit(ZLibCompObject self,
                        @Bind Node inliningTarget,
                        @Cached BaseCopyNode copyNode) {
            return copyNode.execute(inliningTarget, self);
        }
    }

    @Builtin(name = J___COPY__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class UndescoreCopyNode extends CopyNode {
    }

    @Builtin(name = "__deepcopy__", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class DeepCopyNode extends PythonBinaryBuiltinNode {
        @Specialization
        static Object doit(ZLibCompObject self, @SuppressWarnings("unused") Object memo,
                        @Bind Node inliningTarget,
                        @Cached BaseCopyNode copyNode) {
            return copyNode.execute(inliningTarget, self);
        }
    }

    @Builtin(name = "flush", minNumOfPositionalArgs = 1, parameterNames = {"$self", "length"})
    @ArgumentClinic(name = "length", conversion = ArgumentClinic.ClinicConversion.Index, defaultValue = "ZLibModuleBuiltins.DEF_BUF_SIZE", useDefaultForNone = true)
    @GenerateNodeFactory
    abstract static class FlushNode extends PythonBinaryClinicBuiltinNode {

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return ZlibDecompressBuiltinsClinicProviders.FlushNodeClinicProviderGen.INSTANCE;
        }

        @Specialization(guards = {"length > 0", "!self.isEof()", "self.isInitialized()"})
        static PBytes doit(NativeZlibCompObject self, int length,
                        @Bind Node inliningTarget,
                        @Bind PythonContext context,
                        @Cached NativeLibrary.InvokeNativeFunction decompressObjFlush,
                        @Cached ZlibNodes.GetNativeBufferNode getBuffer,
                        @Cached NativeLibrary.InvokeNativeFunction getIsInitialised,
                        @Cached ZlibNodes.NativeDeallocation processDeallocation,
                        @Cached ZlibNodes.ZlibNativeErrorHandling errorHandling) {
            synchronized (self) {
                assert self.isInitialized();
                NFIZlibSupport zlibSupport = context.getNFIZlibSupport();
                int err = zlibSupport.decompressObjFlush(self.getZst(), length, decompressObjFlush);
                if (err != Z_OK) {
                    errorHandling.execute(inliningTarget, self.getZst(), err, zlibSupport, false);
                }
                byte[] resultArray = getBuffer.getOutputBuffer(inliningTarget, self.getZst(), context);
                if (zlibSupport.getIsInitialised(self.getZst(), getIsInitialised) == 0) {
                    processDeallocation.execute(inliningTarget, self, context, false);
                }
                return PFactory.createBytes(context.getLanguage(inliningTarget), resultArray);
            }
        }

        @Specialization(guards = {"length > 0", "!self.isEof()", "self.isInitialized()"})
        PBytes doit(VirtualFrame frame, JavaDecompress self, int length,
                        @Bind PythonLanguage language,
                        @Cached BytesNodes.ToBytesNode toBytes) {
            byte[] res;
            try {
                byte[] bytes = toBytes.execute(self.getUnconsumedTail());
                res = self.decompress(frame, bytes, bytes.length, 0, length, this, toBytes);
            } catch (PException e) {
                // CPython ignores errors here
                res = PythonUtils.EMPTY_BYTE_ARRAY;
            }
            self.setUninitialized();
            return PFactory.createBytes(language, res);
        }

        @SuppressWarnings("unused")
        @Specialization(guards = {"length > 0", "self.isEof() || !self.isInitialized()"})
        static PBytes empty(JavaDecompress self, int length,
                        @Bind PythonLanguage language) {
            self.setUninitialized();
            return PFactory.createEmptyBytes(language);
        }

        @SuppressWarnings("unused")
        @Specialization(guards = {"length > 0", "self.isEof() || !self.isInitialized()"})
        static PBytes empty(NativeZlibCompObject self, int length,
                        @Bind PythonLanguage language) {
            return PFactory.createEmptyBytes(language);
        }

        @SuppressWarnings("unused")
        @Specialization(guards = "length <= 0")
        static PBytes error(ZLibCompObject self, int length,
                        @Bind Node inliningTarget) {
            throw PRaiseNode.raiseStatic(inliningTarget, ValueError, S_MUST_BE_GREATER_THAN_ZERO, "length");
        }
    }

    @Builtin(name = "unused_data", minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    abstract static class UnusedDataNode extends PythonUnaryBuiltinNode {
        @Specialization(guards = "self.isInitialized()")
        static PBytes doit(NativeZlibCompObject self,
                        @Bind Node inliningTarget,
                        @Bind PythonContext context,
                        @Cached ZlibNodes.GetNativeBufferNode getBuffer) {
            synchronized (self) {
                assert self.isInitialized();
                return PFactory.createBytes(context.getLanguage(inliningTarget), getBuffer.getUnusedDataBuffer(inliningTarget, self.getZst(), context));
            }
        }

        @Specialization(guards = "!self.isInitialized()")
        static PBytes doeof(NativeZlibCompObject self) {
            return self.getUnusedData();
        }

        @Specialization
        static PBytes doit(JavaDecompress self) {
            return self.getUnusedData();
        }
    }

    @Builtin(name = "unconsumed_tail", minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    abstract static class UnconsumedTailNode extends PythonUnaryBuiltinNode {
        @Specialization(guards = "self.isInitialized()")
        static PBytes doit(NativeZlibCompObject self,
                        @Bind Node inliningTarget,
                        @Bind PythonContext context,
                        @Cached ZlibNodes.GetNativeBufferNode getBuffer) {
            synchronized (self) {
                assert self.isInitialized();
                return PFactory.createBytes(context.getLanguage(inliningTarget), getBuffer.getUnconsumedTailBuffer(inliningTarget, self.getZst(), context));
            }
        }

        @Specialization(guards = "!self.isInitialized()")
        static PBytes doeof(NativeZlibCompObject self) {
            return self.getUnconsumedTail();
        }

        @Specialization
        static PBytes doit(JavaDecompress self) {
            return self.getUnconsumedTail();
        }
    }

    @Builtin(name = "eof", minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    abstract static class EOFNode extends PythonUnaryBuiltinNode {
        @Specialization(guards = "self.isEof() || !self.isInitialized()")
        static boolean doit(NativeZlibCompObject self) {
            return self.isEof();
        }

        @Specialization(guards = {"!self.isEof()", "self.isInitialized()"})
        boolean getit(NativeZlibCompObject self,
                        @Cached NativeLibrary.InvokeNativeFunction getEOF) {
            synchronized (self) {
                assert self.isInitialized();
                NFIZlibSupport zlibSupport = PythonContext.get(this).getNFIZlibSupport();
                self.setEof(zlibSupport.getEOF(self.getZst(), getEOF) == 1);
                return self.isEof();
            }
        }

        @Specialization
        static boolean doit(JavaDecompress self) {
            return self.isEof();
        }
    }
}
