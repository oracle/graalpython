/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.objects.lzma;

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.OSError;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.TypeError;

import java.io.IOException;
import java.util.List;

import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.modules.LZMAModuleBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.bytes.BytesNodes;
import com.oracle.graal.python.builtins.objects.bytes.PBytes;
import com.oracle.graal.python.builtins.objects.bytes.PIBytesLike;
import com.oracle.graal.python.builtins.objects.common.SequenceNodes;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes;
import com.oracle.graal.python.builtins.objects.function.PArguments;
import com.oracle.graal.python.builtins.objects.object.PythonObjectLibrary;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.builtins.PythonTernaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.truffle.PythonArithmeticTypes;
import com.oracle.graal.python.nodes.util.CastToByteNode;
import com.oracle.graal.python.runtime.sequence.PSequence;
import com.oracle.graal.python.runtime.sequence.storage.SequenceStorage;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.TypeSystemReference;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.library.CachedLibrary;

@CoreFunctions(extendClasses = PythonBuiltinClassType.PLZMADecompressor)
public class LZMADecompressorBuiltins extends PythonBuiltins {

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return LZMADecompressorBuiltinsFactory.getFactories();
    }

    @Builtin(name = "decompress", minNumOfPositionalArgs = 2, parameterNames = {"self", "data", "max_length"}, needsFrame = true)
    @GenerateNodeFactory
    @TypeSystemReference(PythonArithmeticTypes.class)
    abstract static class DecompressNode extends PythonTernaryBuiltinNode {

        @Specialization(guards = "isNoValue(maxLength)")
        PBytes doBytesLike(VirtualFrame frame, PLZMADecompressor self, PIBytesLike bytesLike, @SuppressWarnings("unused") PNone maxLength,
                        @Cached BytesNodes.ToBytesNode toBytesNode) {
            byte[] decompress;
            try {
                decompress = self.decompress(toBytesNode.execute(frame, bytesLike));
                return factory().createBytes(decompress);
            } catch (IOException e) {
                throw raise(OSError, e);
            }
        }

        @Specialization
        PBytes doBytesLikeWithMaxLengthI(VirtualFrame frame, PLZMADecompressor self, PIBytesLike bytesLike, int maxLength,
                        @Cached SequenceNodes.GetSequenceStorageNode getSequenceStorageNode,
                        @Cached SequenceNodes.LenNode lenNode,
                        @Cached SequenceStorageNodes.GetItemNode getItemNode,
                        @Cached CastToByteNode castToByteNode) {
            SequenceStorage storage = getSequenceStorageNode.execute(bytesLike);
            int len = lenNode.execute((PSequence) bytesLike);
            byte[] compressed = new byte[Math.min(len, maxLength)];
            for (int i = 0; i < compressed.length; i++) {
                castToByteNode.execute(frame, getItemNode.execute(frame, storage, i));
            }

            try {
                return factory().createBytes(self.decompress(compressed));
            } catch (IOException e) {
                throw raise(OSError, e);
            }
        }

        @Specialization(replaces = "doBytesLikeWithMaxLengthI", limit = "getCallSiteInlineCacheMaxDepth()")
        PBytes doBytesLikeWithMaxLength(VirtualFrame frame, PLZMADecompressor self, PIBytesLike bytesLike, Object maxLength,
                        @CachedLibrary("maxLength") PythonObjectLibrary lib,
                        @Cached SequenceNodes.GetSequenceStorageNode getSequenceStorageNode,
                        @Cached SequenceNodes.LenNode lenNode,
                        @Cached SequenceStorageNodes.GetItemNode getItemNode,
                        @Cached CastToByteNode castToByteNode) {
            return doBytesLikeWithMaxLengthI(frame, self, bytesLike, lib.asSizeWithState(maxLength, PArguments.getThreadState(frame)), getSequenceStorageNode, lenNode, getItemNode, castToByteNode);
        }

        @Fallback
        PBytes doError(@SuppressWarnings("unused") Object self, Object obj, @SuppressWarnings("unused") Object maxLength) {
            throw raise(TypeError, "a bytes-like object is required, not '%p'", obj);
        }

        @TruffleBoundary
        private static byte[] addBytes(PLZMACompressor self, byte[] data) throws IOException {
            self.getLzmaStream().write(data);
            return self.getBos().toByteArray();
        }
    }

    @Builtin(name = "eof", minNumOfPositionalArgs = 1, parameterNames = {"self"}, isGetter = true)
    @GenerateNodeFactory
    @TypeSystemReference(PythonArithmeticTypes.class)
    abstract static class EofNode extends PythonUnaryBuiltinNode {

        @Specialization
        boolean doEof(PLZMADecompressor self) {
            return self.getEof();
        }

    }

    @Builtin(name = "needs_input", minNumOfPositionalArgs = 1, parameterNames = {"self"}, isGetter = true)
    @GenerateNodeFactory
    @TypeSystemReference(PythonArithmeticTypes.class)
    abstract static class NeedsInputNode extends PythonUnaryBuiltinNode {

        @Specialization
        boolean doNeedsInput(PLZMADecompressor self) {
            return self.isNeedsInput();
        }

    }

    @Builtin(name = "check", minNumOfPositionalArgs = 1, parameterNames = {"self"}, isGetter = true)
    @GenerateNodeFactory
    @TypeSystemReference(PythonArithmeticTypes.class)
    abstract static class CheckNode extends PythonUnaryBuiltinNode {

        @Specialization
        int doCheck(@SuppressWarnings("unused") PLZMADecompressor self) {
            // TODO implement
            return LZMAModuleBuiltins.LZMA_CHECK_UNKNOWN;
        }

    }

    @Builtin(name = "unused_data", minNumOfPositionalArgs = 1, parameterNames = {"self"}, isGetter = true)
    @GenerateNodeFactory
    @TypeSystemReference(PythonArithmeticTypes.class)
    abstract static class UnusedDataNode extends PythonUnaryBuiltinNode {

        @Specialization
        PBytes doUnusedData(@SuppressWarnings("unused") PLZMADecompressor self) {
            // TODO implement
            return factory().createBytes(new byte[0]);
        }

    }

}
