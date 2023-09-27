/*
 * Copyright (c) 2019, 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.modules.lzma;

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.EOFError;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.SystemError;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.TypeError;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.ValueError;
import static com.oracle.graal.python.builtins.modules.lzma.LZMAModuleBuiltins.CHECK_NONE;
import static com.oracle.graal.python.builtins.modules.lzma.LZMAModuleBuiltins.CHECK_UNKNOWN;
import static com.oracle.graal.python.builtins.modules.lzma.LZMAModuleBuiltins.FORMAT_ALONE;
import static com.oracle.graal.python.builtins.modules.lzma.LZMAModuleBuiltins.FORMAT_AUTO;
import static com.oracle.graal.python.builtins.modules.lzma.LZMAModuleBuiltins.FORMAT_RAW;
import static com.oracle.graal.python.builtins.modules.lzma.LZMAModuleBuiltins.FORMAT_XZ;
import static com.oracle.graal.python.builtins.modules.lzma.LZMAModuleBuiltins.T_LZMA_JAVA_ERROR;
import static com.oracle.graal.python.nodes.ErrorMessages.ALREADY_AT_END_OF_STREAM;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___INIT__;

import java.util.List;

import com.oracle.graal.python.annotations.ArgumentClinic;
import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.modules.lzma.LZMAObject.LZMADecompressor;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.bytes.BytesNodes;
import com.oracle.graal.python.builtins.objects.bytes.PBytes;
import com.oracle.graal.python.builtins.objects.bytes.PBytesLike;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.builtins.PythonQuaternaryClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonTernaryClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.clinic.ArgumentClinicProvider;
import com.oracle.graal.python.nodes.truffle.PythonArithmeticTypes;
import com.oracle.graal.python.nodes.util.CannotCastException;
import com.oracle.graal.python.nodes.util.CastToJavaIntExactNode;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.TypeSystemReference;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;

@CoreFunctions(extendClasses = PythonBuiltinClassType.PLZMADecompressor)
public final class LZMADecompressorBuiltins extends PythonBuiltins {

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return LZMADecompressorBuiltinsFactory.getFactories();
    }

    @ImportStatic(PGuards.class)
    @Builtin(name = J___INIT__, minNumOfPositionalArgs = 1, parameterNames = {"$self", "format", "memlimit", "filters"})
    @ArgumentClinic(name = "format", conversion = ArgumentClinic.ClinicConversion.Int, defaultValue = "LZMAModuleBuiltins.FORMAT_AUTO", useDefaultForNone = true)
    @GenerateNodeFactory
    @TypeSystemReference(PythonArithmeticTypes.class)
    public abstract static class InitNode extends PythonQuaternaryClinicBuiltinNode {

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return LZMADecompressorBuiltinsClinicProviders.InitNodeClinicProviderGen.INSTANCE;
        }

        @Specialization(guards = {"!isRaw(format)", "validFormat(format)", "!isPNone(memlimitObj)"})
        static PNone notRaw(VirtualFrame frame, LZMADecompressor self, int format, Object memlimitObj, @SuppressWarnings("unused") PNone filters,
                        @Bind("this") Node inliningTarget,
                        @Cached CastToJavaIntExactNode cast,
                        @Shared("d") @Cached LZMANodes.LZMADecompressInit decompressInit,
                        @Cached PRaiseNode.Lazy raiseNode) {
            int memlimit;
            try {
                memlimit = cast.execute(inliningTarget, memlimitObj);
            } catch (CannotCastException e) {
                throw raiseNode.get(inliningTarget).raise(TypeError, ErrorMessages.INTEGER_REQUIRED);
            }
            return doNotRaw(frame, self, format, memlimit, decompressInit);

        }

        @Specialization(guards = {"!isRaw(format)", "validFormat(format)"})
        static PNone notRaw(VirtualFrame frame, LZMADecompressor self, int format, @SuppressWarnings("unused") PNone memlimit, @SuppressWarnings("unused") PNone filters,
                        @Shared("d") @Cached LZMANodes.LZMADecompressInit decompressInit) {
            return doNotRaw(frame, self, format, Integer.MAX_VALUE, decompressInit);
        }

        private static PNone doNotRaw(VirtualFrame frame, LZMADecompressor self, int format, int memlimit, LZMANodes.LZMADecompressInit decompressInit) {
            self.setCheck(format == FORMAT_ALONE ? CHECK_NONE : CHECK_UNKNOWN);
            self.setFormat(format);
            self.setMemlimit(memlimit);
            decompressInit.execute(frame, self, format, memlimit);
            return PNone.NONE;
        }

        @SuppressWarnings("unused")
        @Specialization(guards = {"isRaw(format)", "!isPNone(filters)"})
        static PNone raw(VirtualFrame frame, LZMADecompressor self, int format, PNone memlimit, Object filters,
                        @Bind("this") Node inliningTarget,
                        @Cached LZMANodes.LZMARawDecompressInit decompressInit) {
            self.setCheck(CHECK_NONE);
            decompressInit.execute(frame, inliningTarget, self, filters);
            return PNone.NONE;
        }

        @SuppressWarnings("unused")
        @Specialization(guards = {"isRaw(format)", "!isPNone(memlimit)"})
        static PNone rawError(LZMADecompressor self, int format, Object memlimit, Object filters,
                        @Shared @Cached PRaiseNode raiseNode) {
            throw raiseNode.raise(ValueError, ErrorMessages.CANNOT_SPECIFY_MEM_LIMIT);
        }

        @SuppressWarnings("unused")
        @Specialization(guards = "isRaw(format)")
        static PNone rawFilterError(LZMADecompressor self, int format, Object memlimit, PNone filters,
                        @Shared @Cached PRaiseNode raiseNode) {
            throw raiseNode.raise(ValueError, ErrorMessages.MUST_SPECIFY_FILTERS);
        }

        @SuppressWarnings("unused")
        @Specialization(guards = {"!isRaw(format)", "!isPNone(filters)"})
        static PNone rawFilterError(LZMADecompressor self, int format, Object memlimit, Object filters,
                        @Shared @Cached PRaiseNode raiseNode) {
            throw raiseNode.raise(ValueError, ErrorMessages.CANNOT_SPECIFY_FILTERS);
        }

        @SuppressWarnings("unused")
        @Specialization(guards = "!validFormat(format)")
        static PNone invalidFormat(LZMADecompressor self, int format, Object memlimit, Object filters,
                        @Shared @Cached PRaiseNode raiseNode) {
            throw raiseNode.raise(ValueError, ErrorMessages.INVALID_CONTAINER_FORMAT, format);
        }

        protected static boolean validFormat(int format) {
            return format == FORMAT_AUTO ||
                            format == FORMAT_XZ ||
                            format == FORMAT_ALONE ||
                            format == FORMAT_RAW;
        }

        protected static boolean isRaw(int format) {
            return format == FORMAT_RAW;
        }
    }

    @Builtin(name = "decompress", minNumOfPositionalArgs = 2, parameterNames = {"$self", "$data", "max_length"}, needsFrame = true)
    @ArgumentClinic(name = "max_length", conversion = ArgumentClinic.ClinicConversion.Int, defaultValue = "-1", useDefaultForNone = true)
    @GenerateNodeFactory
    @TypeSystemReference(PythonArithmeticTypes.class)
    abstract static class DecompressNode extends PythonTernaryClinicBuiltinNode {

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return LZMADecompressorBuiltinsClinicProviders.DecompressNodeClinicProviderGen.INSTANCE;
        }

        @Specialization(guards = {"!self.isEOF()"})
        static PBytes doBytes(LZMADecompressor self, PBytesLike data, int maxLength,
                        @Bind("this") Node inliningTarget,
                        @Cached SequenceStorageNodes.GetInternalByteArrayNode toBytes,
                        @Exclusive @Cached LZMANodes.DecompressNode decompress,
                        @Shared @Cached PythonObjectFactory factory) {
            byte[] bytes = toBytes.execute(inliningTarget, data.getSequenceStorage());
            int len = data.getSequenceStorage().length();
            return factory.createBytes(decompress.execute(inliningTarget, self, bytes, len, maxLength));

        }

        @Specialization(guards = {"!self.isEOF()"})
        static PBytes doObject(VirtualFrame frame, LZMADecompressor self, Object data, int maxLength,
                        @Bind("this") Node inliningTarget,
                        @Cached BytesNodes.ToBytesNode toBytes,
                        @Exclusive @Cached LZMANodes.DecompressNode decompress,
                        @Shared @Cached PythonObjectFactory factory) {
            byte[] bytes = toBytes.execute(frame, data);
            int len = bytes.length;
            return factory.createBytes(decompress.execute(inliningTarget, self, bytes, len, maxLength));
        }

        @SuppressWarnings("unused")
        @Specialization(guards = {"self.isEOF()"})
        static Object err(LZMADecompressor self, Object data, int maxLength,
                        @Cached PRaiseNode raiseNode) {
            throw raiseNode.raise(EOFError, ALREADY_AT_END_OF_STREAM);
        }
    }

    @Builtin(name = "eof", minNumOfPositionalArgs = 1, parameterNames = {"self"}, isGetter = true)
    @GenerateNodeFactory
    @TypeSystemReference(PythonArithmeticTypes.class)
    abstract static class EofNode extends PythonUnaryBuiltinNode {

        @Specialization
        boolean doEof(LZMADecompressor self) {
            return self.isEOF();
        }

    }

    @Builtin(name = "needs_input", minNumOfPositionalArgs = 1, parameterNames = {"self"}, isGetter = true)
    @GenerateNodeFactory
    @TypeSystemReference(PythonArithmeticTypes.class)
    abstract static class NeedsInputNode extends PythonUnaryBuiltinNode {

        @Specialization
        boolean doNeedsInput(LZMADecompressor self) {
            return self.needsInput();
        }

    }

    @Builtin(name = "check", minNumOfPositionalArgs = 1, parameterNames = {"self"}, isGetter = true)
    @GenerateNodeFactory
    @TypeSystemReference(PythonArithmeticTypes.class)
    abstract static class CheckNode extends PythonUnaryBuiltinNode {

        @Specialization
        int doCheck(LZMADecompressor.Native self) {
            return self.getCheck();
        }

        @Specialization
        int doCheck(@SuppressWarnings("unused") LZMADecompressor.Java self) {
            throw raise(SystemError, T_LZMA_JAVA_ERROR);
        }

    }

    @Builtin(name = "unused_data", minNumOfPositionalArgs = 1, parameterNames = {"self"}, isGetter = true)
    @GenerateNodeFactory
    @TypeSystemReference(PythonArithmeticTypes.class)
    abstract static class UnusedDataNode extends PythonUnaryBuiltinNode {

        @Specialization
        static PBytes doUnusedData(LZMADecompressor self,
                        @Cached PythonObjectFactory factory) {
            return factory.createBytes(self.getUnusedData());
        }

    }

}
