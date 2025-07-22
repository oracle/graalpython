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

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.BZ2Decompressor;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.EOFError;
import static com.oracle.graal.python.builtins.modules.bz2.Bz2Nodes.BZ_OK;
import static com.oracle.graal.python.builtins.modules.bz2.Bz2Nodes.errorHandling;
import static com.oracle.graal.python.nodes.ErrorMessages.END_OF_STREAM_ALREADY_REACHED;

import java.util.List;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.annotations.ArgumentClinic;
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
import com.oracle.graal.python.nodes.function.builtins.PythonTernaryClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.clinic.ArgumentClinicProvider;
import com.oracle.graal.python.runtime.NFIBz2Support;
import com.oracle.graal.python.runtime.NativeLibrary;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.object.PFactory;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;

@CoreFunctions(extendClasses = BZ2Decompressor)
public final class BZ2DecompressorBuiltins extends PythonBuiltins {

    public static final TpSlots SLOTS = BZ2DecompressorBuiltinsSlotsGen.SLOTS;

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return BZ2DecompressorBuiltinsFactory.getFactories();
    }

    @Slot(value = SlotKind.tp_new, isComplex = true)
    @SlotSignature(name = "BZ2Decompressor", minNumOfPositionalArgs = 1, takesVarArgs = true, takesVarKeywordArgs = true)
    @GenerateNodeFactory
    public abstract static class BZ2DecompressorNode extends PythonBuiltinNode {
        @Specialization
        static BZ2Object.BZ2Decompressor doNew(@SuppressWarnings("unused") Object cls, @SuppressWarnings("unused") Object arg,
                        @Bind PythonLanguage language) {
            // data filled in subsequent __init__ call - see BZ2DecompressorBuiltins.InitNode
            return PFactory.createBZ2Decompressor(language);
        }
    }

    @Slot(value = SlotKind.tp_init, isComplex = true)
    @SlotSignature(name = "BZDecompressor", minNumOfPositionalArgs = 1, parameterNames = {"$self"})
    @GenerateNodeFactory
    public abstract static class InitNode extends PythonUnaryBuiltinNode {

        @Specialization
        static PNone init(BZ2Object.BZ2Decompressor self,
                        @Bind Node inliningTarget,
                        @Cached NativeLibrary.InvokeNativeFunction createStream,
                        @Cached NativeLibrary.InvokeNativeFunction compressInit,
                        @Cached PRaiseNode raiseNode) {
            NFIBz2Support bz2Support = PythonContext.get(inliningTarget).getNFIBz2Support();
            Object bzst = bz2Support.createStream(createStream);
            int err = bz2Support.decompressInit(bzst, compressInit);
            if (err != BZ_OK) {
                errorHandling(inliningTarget, err, raiseNode);
            }
            self.init(bzst, bz2Support);
            return PNone.NONE;
        }
    }

    @Builtin(name = "decompress", minNumOfPositionalArgs = 1, parameterNames = {"$self", "data", "max_length"})
    @ArgumentClinic(name = "max_length", conversion = ArgumentClinic.ClinicConversion.Int, defaultValue = "-1", useDefaultForNone = true)
    @GenerateNodeFactory
    abstract static class DecompressNode extends PythonTernaryClinicBuiltinNode {

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return BZ2DecompressorBuiltinsClinicProviders.DecompressNodeClinicProviderGen.INSTANCE;
        }

        @Specialization(guards = {"!self.isEOF()"})
        static PBytes doNativeBytes(BZ2Object.BZ2Decompressor self, PBytesLike data, int maxLength,
                        @Bind Node inliningTarget,
                        @Bind PythonLanguage language,
                        @Cached SequenceStorageNodes.GetInternalByteArrayNode toBytes,
                        @Exclusive @Cached Bz2Nodes.Bz2NativeDecompress decompress) {
            synchronized (self) {
                byte[] bytes = toBytes.execute(inliningTarget, data.getSequenceStorage());
                int len = data.getSequenceStorage().length();
                return PFactory.createBytes(language, decompress.execute(inliningTarget, self, bytes, len, maxLength));
            }
        }

        @Specialization(guards = {"!self.isEOF()"})
        static PBytes doNativeObject(VirtualFrame frame, BZ2Object.BZ2Decompressor self, Object data, int maxLength,
                        @Bind Node inliningTarget,
                        @Bind PythonLanguage language,
                        @Cached BytesNodes.ToBytesNode toBytes,
                        @Exclusive @Cached Bz2Nodes.Bz2NativeDecompress decompress) {
            synchronized (self) {
                byte[] bytes = toBytes.execute(frame, data);
                int len = bytes.length;
                return PFactory.createBytes(language, decompress.execute(inliningTarget, self, bytes, len, maxLength));
            }
        }

        @SuppressWarnings("unused")
        @Specialization(guards = {"self.isEOF()"})
        static Object err(BZ2Object.BZ2Decompressor self, PBytesLike data, int maxLength,
                        @Bind Node inliningTarget) {
            throw PRaiseNode.raiseStatic(inliningTarget, EOFError, END_OF_STREAM_ALREADY_REACHED);
        }
    }

    @Builtin(name = "unused_data", minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    abstract static class UnusedDataNode extends PythonUnaryBuiltinNode {
        @Specialization
        static PBytes doit(BZ2Object.BZ2Decompressor self,
                        @Bind PythonLanguage language) {
            return PFactory.createBytes(language, self.getUnusedData());
        }
    }

    @Builtin(name = "needs_input", minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    abstract static class NeedsInputNode extends PythonUnaryBuiltinNode {
        @Specialization
        boolean doit(BZ2Object.BZ2Decompressor self) {
            return self.needsInput();
        }
    }

    @Builtin(name = "eof", minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    abstract static class EOFNode extends PythonUnaryBuiltinNode {
        @Specialization
        boolean doit(BZ2Object.BZ2Decompressor self) {
            return self.isEOF();
        }
    }
}
