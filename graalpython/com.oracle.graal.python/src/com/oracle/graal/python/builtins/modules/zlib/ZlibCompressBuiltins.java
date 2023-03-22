/*
 * Copyright (c) 2020, 2023, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.graal.python.annotations.ArgumentClinic;
import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.modules.zlib.ZlibNodes.JavaCompressNode;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.bytes.BytesNodes;
import com.oracle.graal.python.builtins.objects.bytes.PBytes;
import com.oracle.graal.python.builtins.objects.bytes.PBytesLike;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.clinic.ArgumentClinicProvider;
import com.oracle.graal.python.runtime.NFIZlibSupport;
import com.oracle.graal.python.runtime.NativeLibrary;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;

@CoreFunctions(extendClasses = ZlibCompress)
public final class ZlibCompressBuiltins extends PythonBuiltins {
    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return ZlibCompressBuiltinsFactory.getFactories();
    }

    @Builtin(name = "compress", minNumOfPositionalArgs = 2, parameterNames = {"$self", ""})
    @GenerateNodeFactory
    abstract static class CompressNode extends PythonBinaryBuiltinNode {

        @Specialization(guards = "self.isInitialized()")
        PBytes doNativeBytes(ZLibCompObject.NativeZlibCompObject self, PBytesLike data,
                        @Bind("this") Node inliningTarget,
                        @Cached SequenceStorageNodes.GetInternalByteArrayNode toBytes,
                        @Shared("co") @Cached ZlibNodes.ZlibNativeCompressObj compressObj) {
            synchronized (self) {
                assert self.isInitialized();
                byte[] bytes = toBytes.execute(inliningTarget, data.getSequenceStorage());
                int len = data.getSequenceStorage().length();
                return factory().createBytes(compressObj.execute(inliningTarget, self, PythonContext.get(this), bytes, len));
            }
        }

        @Specialization(guards = {"self.isInitialized()", "!isBytes(data)"})
        PBytes doNativeObject(VirtualFrame frame, ZLibCompObject.NativeZlibCompObject self, Object data,
                        @Bind("this") Node inliningTarget,
                        @Shared("bb") @Cached BytesNodes.ToBytesNode toBytes,
                        @Shared("co") @Cached ZlibNodes.ZlibNativeCompressObj compressObj) {
            synchronized (self) {
                assert self.isInitialized();
                byte[] bytes = toBytes.execute(frame, data);
                int len = bytes.length;
                return factory().createBytes(compressObj.execute(inliningTarget, self, PythonContext.get(this), bytes, len));
            }
        }

        @Specialization(guards = "self.isInitialized()")
        PBytes doit(VirtualFrame frame, ZLibCompObject.JavaZlibCompObject self, Object data,
                        @Shared("bb") @Cached BytesNodes.ToBytesNode toBytes) {
            byte[] bytes = toBytes.execute(frame, data);
            self.setDeflaterInput(bytes);
            return JavaCompressNode.execute(self, Z_NO_FLUSH, factory());
        }

        @SuppressWarnings("unused")
        @Specialization(guards = "!self.isInitialized()")
        PBytes error(ZLibCompObject self, Object data) {
            throw raise(ZLibError, ERROR_D_S_S, Z_STREAM_ERROR, "while compressing data", "inconsistent stream state");
        }
    }

    @GenerateInline
    @GenerateCached(false)
    abstract static class BaseCopyNode extends PNodeWithContext {

        public abstract Object execute(Node inliningTarget, ZLibCompObject self, PythonContext ctxt, PythonObjectFactory factory);

        @Specialization(guards = "self.isInitialized()")
        static Object doNative(Node inliningTarget, ZLibCompObject.NativeZlibCompObject self, PythonContext ctxt, PythonObjectFactory factory,
                        @Cached(inline = false) NativeLibrary.InvokeNativeFunction createCompObject,
                        @Cached(inline = false) NativeLibrary.InvokeNativeFunction compressObjCopy,
                        @Cached(inline = false) NativeLibrary.InvokeNativeFunction deallocateStream,
                        @Cached ZlibNodes.ZlibNativeErrorHandling errorHandling) {
            synchronized (self) {
                assert self.isInitialized();
                NFIZlibSupport zlibSupport = ctxt.getNFIZlibSupport();
                Object zstNewCopy = zlibSupport.createCompObject(createCompObject);
                int err = zlibSupport.compressObjCopy(self.getZst(), zstNewCopy, compressObjCopy);
                if (err != Z_OK) {
                    zlibSupport.deallocateStream(zstNewCopy, deallocateStream);
                    errorHandling.execute(inliningTarget, self.getZst(), err, zlibSupport, false);
                }
                return factory.createNativeZLibCompObject(ZlibCompress, zstNewCopy, zlibSupport);
            }
        }

        @Specialization(guards = {"self.isInitialized()", "self.canCopy()"})
        static Object doJava(ZLibCompObject.JavaZlibCompObject self, @SuppressWarnings("unused") PythonContext ctxt, PythonObjectFactory factory) {
            return self.copyCompressObj(factory);
        }

        @SuppressWarnings("unused")
        @Specialization(guards = {"self.isInitialized()", "!self.canCopy()"})
        static PNone error(ZLibCompObject.JavaZlibCompObject self, PythonContext ctxt, PythonObjectFactory factory,
                        @Cached.Shared @Cached(inline = false) PRaiseNode raise) {
            throw raise.raise(NotImplementedError, toTruffleStringUncached("JDK based zlib doesn't support copying"));
        }

        @SuppressWarnings("unused")
        @Specialization(guards = "!self.isInitialized()")
        static PNone error(ZLibCompObject self, PythonContext ctxt, PythonObjectFactory factory,
                        @Cached.Shared @Cached(inline = false) PRaiseNode raise) {
            throw raise.raise(ValueError, ErrorMessages.INCONSISTENT_STREAM_STATE);
        }
    }

    @Builtin(name = "copy", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class CopyNode extends PythonUnaryBuiltinNode {
        @Specialization
        @SuppressWarnings("truffle-static-method")
        Object doit(ZLibCompObject self,
                        @Bind("this") Node inliningTarget,
                        @Cached BaseCopyNode copyNode) {
            return copyNode.execute(inliningTarget, self, PythonContext.get(this), factory());
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
        @SuppressWarnings("truffle-static-method")
        Object doit(ZLibCompObject self, @SuppressWarnings("unused") Object memo,
                        @Bind("this") Node inliningTarget,
                        @Cached BaseCopyNode copyNode) {
            return copyNode.execute(inliningTarget, self, PythonContext.get(this), factory());
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
        PBytes empty(ZLibCompObject self, int mode) {
            return factory().createBytes(PythonUtils.EMPTY_BYTE_ARRAY);
        }

        @Specialization(guards = {"mode != Z_NO_FLUSH", "self.isInitialized()"})
        @SuppressWarnings("truffle-static-method") // factory
        PBytes doit(ZLibCompObject.NativeZlibCompObject self, int mode,
                        @Bind("this") Node inliningTarget,
                        @Cached NativeLibrary.InvokeNativeFunction compressObjFlush,
                        @Cached ZlibNodes.GetNativeBufferNode getBuffer,
                        @Cached NativeLibrary.InvokeNativeFunction getIsInitialised,
                        @Cached ZlibNodes.NativeDeallocation processDeallocation,
                        @Cached ZlibNodes.ZlibNativeErrorHandling errorHandling) {
            synchronized (self) {
                assert self.isInitialized();
                PythonContext ctxt = PythonContext.get(this);
                NFIZlibSupport zlibSupport = ctxt.getNFIZlibSupport();
                Object lastInput;
                if (self.lastInput == null) {
                    // all previous input data has been processed or nothing has been compressed.
                    lastInput = ctxt.getEnv().asGuestValue(PythonUtils.EMPTY_BYTE_ARRAY);
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
                byte[] resultArray = getBuffer.getOutputBuffer(inliningTarget, self.getZst(), ctxt);
                if (zlibSupport.getIsInitialised(self.getZst(), getIsInitialised) == 0) {
                    processDeallocation.execute(inliningTarget, self, ctxt, factory(), true);
                }
                return factory().createBytes(resultArray);
            }
        }

        @Specialization(guards = {"mode != Z_NO_FLUSH", "self.isInitialized()"})
        PBytes doit(ZLibCompObject.JavaZlibCompObject self, int mode) {
            return ZlibNodes.JavaCompressNode.execute(self, mode, factory());
        }

        @SuppressWarnings("unused")
        @Specialization(guards = "!self.isInitialized()")
        PNone error(ZLibCompObject self, int mode) {
            throw raise(ZLibError, ERROR_D_S_S, Z_STREAM_ERROR, "while compressing data", "inconsistent stream state");
        }
    }

}
