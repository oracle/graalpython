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
package com.oracle.graal.python.builtins.modules.bz2;

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.BZ2Compressor;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.ValueError;
import static com.oracle.graal.python.builtins.modules.bz2.Bz2Nodes.BZ_OK;
import static com.oracle.graal.python.builtins.modules.bz2.Bz2Nodes.errorHandling;
import static com.oracle.graal.python.nodes.ErrorMessages.COMPRESSLEVEL_MUST_BE_BETWEEN_1_AND_9;
import static com.oracle.graal.python.nodes.ErrorMessages.COMPRESSOR_HAS_BEEN_FLUSHED;
import static com.oracle.graal.python.nodes.ErrorMessages.REPEATED_CALL_TO_FLUSH;

import java.util.List;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.annotations.ArgumentClinic;
import com.oracle.graal.python.annotations.ArgumentClinic.ClinicConversion;
import com.oracle.graal.python.annotations.Slot;
import com.oracle.graal.python.annotations.Slot.SlotKind;
import com.oracle.graal.python.annotations.Slot.SlotSignature;
import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.bytes.BytesNodes;
import com.oracle.graal.python.builtins.objects.bytes.PBytes;
import com.oracle.graal.python.builtins.objects.bytes.PBytesLike;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes;
import com.oracle.graal.python.builtins.objects.type.TpSlots;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.clinic.ArgumentClinicProvider;
import com.oracle.graal.python.runtime.GilNode;
import com.oracle.graal.python.runtime.NFIBz2Support;
import com.oracle.graal.python.runtime.NativeLibrary;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.object.PFactory;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;

@CoreFunctions(extendClasses = BZ2Compressor)
public final class BZ2CompressorBuiltins extends PythonBuiltins {

    public static final TpSlots SLOTS = BZ2CompressorBuiltinsSlotsGen.SLOTS;

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return BZ2CompressorBuiltinsFactory.getFactories();
    }

    @Slot(value = SlotKind.tp_new, isComplex = true)
    @SlotSignature(name = "BZ2Compressor", minNumOfPositionalArgs = 1, takesVarArgs = true, takesVarKeywordArgs = true)
    @GenerateNodeFactory
    public abstract static class BZ2CompressorNode extends PythonBuiltinNode {
        @Specialization
        static BZ2Object.BZ2Compressor doNew(@SuppressWarnings("unused") Object cls, @SuppressWarnings("unused") Object arg,
                        @Bind PythonLanguage language) {
            // data filled in subsequent __init__ call - see BZ2CompressorBuiltins.InitNode
            return PFactory.createBZ2Compressor(language);
        }
    }

    @Slot(value = SlotKind.tp_init, isComplex = true)
    @SlotSignature(name = "BZ2Compressor", minNumOfPositionalArgs = 1, parameterNames = {"$self", "compresslevel"})
    @ArgumentClinic(name = "compresslevel", conversion = ClinicConversion.Int, defaultValue = "9", useDefaultForNone = true)
    @GenerateNodeFactory
    public abstract static class InitNode extends PythonBinaryClinicBuiltinNode {

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return BZ2CompressorBuiltinsClinicProviders.InitNodeClinicProviderGen.INSTANCE;
        }

        @Specialization(guards = {"compresslevel >= 1", "compresslevel <= 9"})
        PNone init(BZ2Object.BZ2Compressor self, int compresslevel,
                        @Bind Node inliningTarget,
                        @Cached NativeLibrary.InvokeNativeFunction createStream,
                        @Cached NativeLibrary.InvokeNativeFunction compressInit,
                        @Cached GilNode gil,
                        @Cached PRaiseNode raiseNode) {
            gil.release(true);
            try {
                NFIBz2Support bz2Support = PythonContext.get(this).getNFIBz2Support();
                Object bzst = bz2Support.createStream(createStream);
                int err = bz2Support.compressInit(bzst, compresslevel, compressInit);
                if (err != BZ_OK) {
                    errorHandling(inliningTarget, err, raiseNode);
                }
                self.init(bzst, bz2Support);
                return PNone.NONE;
            } finally {
                gil.acquire();
            }
        }

        @SuppressWarnings("unused")
        @Fallback
        static Object err(Object self, Object compresslevel,
                        @Bind Node inliningTarget) {
            throw PRaiseNode.raiseStatic(inliningTarget, ValueError, COMPRESSLEVEL_MUST_BE_BETWEEN_1_AND_9);
        }
    }

    @Builtin(name = "compress", minNumOfPositionalArgs = 2, parameterNames = {"$self", "$data"})
    @GenerateNodeFactory
    abstract static class CompressNode extends PythonBinaryBuiltinNode {

        @Specialization(guards = {"!self.isFlushed()"})
        static PBytes doNativeBytes(BZ2Object.BZ2Compressor self, PBytesLike data,
                        @Bind Node inliningTarget,
                        @Bind PythonContext context,
                        @Cached SequenceStorageNodes.GetInternalByteArrayNode toBytes,
                        @Shared("c") @Cached Bz2Nodes.Bz2NativeCompress compress) {
            byte[] bytes = toBytes.execute(inliningTarget, data.getSequenceStorage());
            int len = data.getSequenceStorage().length();
            return PFactory.createBytes(context.getLanguage(inliningTarget), compress.compress(self, context, bytes, len));
        }

        @Specialization(guards = {"!self.isFlushed()"})
        static PBytes doNativeObject(VirtualFrame frame, BZ2Object.BZ2Compressor self, Object data,
                        @Bind Node inliningTarget,
                        @Bind PythonContext context,
                        @Cached BytesNodes.ToBytesNode toBytes,
                        @Shared("c") @Cached Bz2Nodes.Bz2NativeCompress compress) {
            byte[] bytes = toBytes.execute(frame, data);
            int len = bytes.length;
            return PFactory.createBytes(context.getLanguage(inliningTarget), compress.compress(self, context, bytes, len));
        }

        @SuppressWarnings("unused")
        @Specialization(guards = "self.isFlushed()")
        static PNone error(BZ2Object.BZ2Compressor self, Object data,
                        @Bind Node inliningTarget) {
            throw PRaiseNode.raiseStatic(inliningTarget, ValueError, COMPRESSOR_HAS_BEEN_FLUSHED);
        }
    }

    @Builtin(name = "flush", minNumOfPositionalArgs = 1, parameterNames = {"$self"})
    @GenerateNodeFactory
    abstract static class FlushNode extends PythonUnaryBuiltinNode {

        @Specialization(guards = {"!self.isFlushed()"})
        static PBytes doit(BZ2Object.BZ2Compressor self,
                        @Bind Node inliningTarget,
                        @Bind PythonContext context,
                        @Cached Bz2Nodes.Bz2NativeCompress compress) {
            self.setFlushed();
            return PFactory.createBytes(context.getLanguage(inliningTarget), compress.flush(self, context));
        }

        @SuppressWarnings("unused")
        @Specialization(guards = "self.isFlushed()")
        static PNone error(BZ2Object.BZ2Compressor self,
                        @Bind Node inliningTarget) {
            throw PRaiseNode.raiseStatic(inliningTarget, ValueError, REPEATED_CALL_TO_FLUSH);
        }
    }
}
