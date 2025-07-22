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
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.ZlibCompress;
import static com.oracle.graal.python.builtins.modules.zlib.ZLibModuleBuiltins.DEF_BUF_SIZE;
import static com.oracle.graal.python.builtins.modules.zlib.ZLibModuleBuiltins.Z_NO_FLUSH;
import static com.oracle.graal.python.builtins.modules.zlib.ZlibNodes.Z_OK;
import static com.oracle.graal.python.builtins.modules.zlib.ZlibNodes.Z_STREAM_ERROR;
import static com.oracle.graal.python.nodes.ErrorMessages.ERROR_D_S_S;
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
import com.oracle.graal.python.builtins.objects.bytes.PBytes;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.clinic.ArgumentClinicProvider;
import com.oracle.graal.python.runtime.IndirectCallData;
import com.oracle.graal.python.runtime.NFIZlibSupport;
import com.oracle.graal.python.runtime.NativeLibrary;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.object.PFactory;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.Node;

@CoreFunctions(extendClasses = ZlibCompress)
public final class ZlibCompressBuiltins extends PythonBuiltins {
    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return ZlibCompressBuiltinsFactory.getFactories();
    }

    @Builtin(name = "compress", minNumOfPositionalArgs = 2, numOfPositionalOnlyArgs = 2, parameterNames = {"$self", "data"})
    @ArgumentClinic(name = "data", conversion = ArgumentClinic.ClinicConversion.ReadableBuffer)
    @GenerateNodeFactory
    abstract static class CompressNode extends PythonBinaryClinicBuiltinNode {

        @Specialization
        static PBytes compress(VirtualFrame frame, ZLibCompObject self, Object buffer,
                        @Bind Node inliningTarget,
                        @Bind PythonLanguage language,
                        @CachedLibrary(limit = "3") PythonBufferAccessLibrary bufferLib,
                        @Cached("createFor($node)") IndirectCallData indirectCallData,
                        @Cached CompressInnerNode innerNode,
                        @Cached PRaiseNode raiseNode) {
            try {
                if (!self.isInitialized()) {
                    throw raiseNode.raise(inliningTarget, ZLibError, ERROR_D_S_S, Z_STREAM_ERROR, "while compressing data", "inconsistent stream state");
                }
                byte[] bytes = bufferLib.getInternalOrCopiedByteArray(buffer);
                int len = bufferLib.getBufferLength(buffer);
                return PFactory.createBytes(language, innerNode.execute(inliningTarget, self, bytes, len));
            } finally {
                bufferLib.release(buffer, frame, indirectCallData);
            }
        }

        @GenerateInline
        @GenerateCached(false)
        abstract static class CompressInnerNode extends Node {
            abstract byte[] execute(Node inliningTarget, Object self, byte[] bytes, int length);

            @Specialization
            static byte[] doNative(Node inliningTarget, NativeZlibCompObject self, byte[] bytes, int length,
                            @Cached ZlibNodes.ZlibNativeCompressObj compressObj) {
                synchronized (self) {
                    return compressObj.execute(inliningTarget, self, PythonContext.get(inliningTarget), bytes, length);
                }
            }

            @Specialization
            static byte[] doJava(JavaCompress self, byte[] bytes, int length) {
                self.setDeflaterInput(bytes, length);
                return self.compress(Z_NO_FLUSH);
            }
        }

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return ZlibCompressBuiltinsClinicProviders.CompressNodeClinicProviderGen.INSTANCE;
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
                        @Cached(inline = false) NativeLibrary.InvokeNativeFunction compressObjCopy,
                        @Cached(inline = false) NativeLibrary.InvokeNativeFunction deallocateStream,
                        @Cached ZlibNodes.ZlibNativeErrorHandling errorHandling) {
            synchronized (self) {
                assert self.isInitialized();
                NFIZlibSupport zlibSupport = context.getNFIZlibSupport();
                Object zstNewCopy = zlibSupport.createCompObject(createCompObject);
                int err = zlibSupport.compressObjCopy(self.getZst(), zstNewCopy, compressObjCopy);
                if (err != Z_OK) {
                    zlibSupport.deallocateStream(zstNewCopy, deallocateStream);
                    errorHandling.execute(inliningTarget, self.getZst(), err, zlibSupport, false);
                }
                return PFactory.createNativeZLibCompObjectCompress(context.getLanguage(inliningTarget), zstNewCopy, zlibSupport);
            }
        }

        @Specialization(guards = {"self.isInitialized()", "self.canCopy()"})
        static Object doJava(JavaCompress self) {
            return self.copy();
        }

        @SuppressWarnings("unused")
        @Specialization(guards = {"self.isInitialized()", "!self.canCopy()"})
        static PNone error(JavaCompress self,
                        @Bind Node inliningTarget) {
            throw PRaiseNode.raiseStatic(inliningTarget, NotImplementedError, toTruffleStringUncached("JDK based zlib doesn't support copying"));
        }

        @SuppressWarnings("unused")
        @Specialization(guards = "!self.isInitialized()")
        static PNone error(ZLibCompObject self,
                        @Bind Node inliningTarget) {
            throw PRaiseNode.raiseStatic(inliningTarget, ValueError, ErrorMessages.INCONSISTENT_STREAM_STATE);
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

    @ImportStatic(ZLibModuleBuiltins.class)
    @Builtin(name = "flush", minNumOfPositionalArgs = 1, parameterNames = {"$self", "mode"})
    @ArgumentClinic(name = "mode", conversion = ArgumentClinic.ClinicConversion.Int, defaultValue = "ZLibModuleBuiltins.Z_FINISH", useDefaultForNone = true)
    @GenerateNodeFactory
    abstract static class FlushNode extends PythonBinaryClinicBuiltinNode {

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return ZlibCompressBuiltinsClinicProviders.FlushNodeClinicProviderGen.INSTANCE;
        }

        @SuppressWarnings("unused")
        @Specialization(guards = "mode == Z_NO_FLUSH")
        static PBytes empty(ZLibCompObject self, int mode,
                        @Bind PythonLanguage language) {
            return PFactory.createEmptyBytes(language);
        }

        @Specialization(guards = {"mode != Z_NO_FLUSH", "self.isInitialized()"})
        static PBytes doit(NativeZlibCompObject self, int mode,
                        @Bind Node inliningTarget,
                        @Cached NativeLibrary.InvokeNativeFunction compressObjFlush,
                        @Cached ZlibNodes.GetNativeBufferNode getBuffer,
                        @Cached NativeLibrary.InvokeNativeFunction getIsInitialised,
                        @Cached ZlibNodes.NativeDeallocation processDeallocation,
                        @Cached ZlibNodes.ZlibNativeErrorHandling errorHandling) {
            synchronized (self) {
                assert self.isInitialized();
                PythonContext context = PythonContext.get(inliningTarget);
                NFIZlibSupport zlibSupport = context.getNFIZlibSupport();
                byte[] lastInput;
                if (self.lastInput == null) {
                    // all previous input data has been processed or nothing has been compressed.
                    lastInput = PythonUtils.EMPTY_BYTE_ARRAY;
                } else {
                    // pass the last data input to continue processing.
                    // all other needed info, e.g. size and offset, about the last data input is
                    // stored in the native stream.
                    lastInput = self.lastInput;
                }
                int err = zlibSupport.compressObjFlush(self.getZst(), lastInput, DEF_BUF_SIZE, mode, compressObjFlush);
                if (err != Z_OK) {
                    errorHandling.execute(inliningTarget, self.getZst(), err, zlibSupport, false);
                }
                byte[] resultArray = getBuffer.getOutputBuffer(inliningTarget, self.getZst(), context);
                if (zlibSupport.getIsInitialised(self.getZst(), getIsInitialised) == 0) {
                    processDeallocation.execute(inliningTarget, self, context, true);
                }
                return PFactory.createBytes(context.getLanguage(inliningTarget), resultArray);
            }
        }

        @Specialization(guards = {"mode != Z_NO_FLUSH", "self.isInitialized()"})
        static PBytes doit(JavaCompress self, int mode,
                        @Bind PythonLanguage language) {
            return PFactory.createBytes(language, self.compress(mode));
        }

        @SuppressWarnings("unused")
        @Specialization(guards = "!self.isInitialized()")
        static PNone error(ZLibCompObject self, int mode,
                        @Bind Node inliningTarget) {
            throw PRaiseNode.raiseStatic(inliningTarget, ZLibError, ERROR_D_S_S, Z_STREAM_ERROR, "while compressing data", "inconsistent stream state");
        }
    }

}
