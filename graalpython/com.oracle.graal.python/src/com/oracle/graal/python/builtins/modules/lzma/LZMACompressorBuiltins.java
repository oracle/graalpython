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

import static com.oracle.graal.python.builtins.modules.lzma.LZMAModuleBuiltins.CHECK_CRC64;
import static com.oracle.graal.python.builtins.modules.lzma.LZMAModuleBuiltins.CHECK_NONE;
import static com.oracle.graal.python.builtins.modules.lzma.LZMAModuleBuiltins.FORMAT_RAW;
import static com.oracle.graal.python.builtins.modules.lzma.LZMAModuleBuiltins.FORMAT_XZ;
import static com.oracle.graal.python.builtins.modules.lzma.LZMAModuleBuiltins.PRESET_DEFAULT;
import static com.oracle.graal.python.nodes.ErrorMessages.CANNOT_SPECIFY_PREST_AND_FILTER_CHAIN;
import static com.oracle.graal.python.nodes.ErrorMessages.COMPRESSOR_HAS_BEEN_FLUSHED;
import static com.oracle.graal.python.nodes.ErrorMessages.INTEGRITY_CHECKS_ONLY_SUPPORTED_BY;
import static com.oracle.graal.python.nodes.ErrorMessages.MUST_SPECIFY_FILTERS;
import static com.oracle.graal.python.nodes.ErrorMessages.REPEATED_CALL_TO_FLUSH;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___INIT__;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.ValueError;

import java.util.List;

import com.oracle.graal.python.annotations.ArgumentClinic;
import com.oracle.graal.python.annotations.ArgumentClinic.ClinicConversion;
import com.oracle.graal.python.annotations.ClinicConverterFactory;
import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.modules.lzma.LZMAObject.LZMACompressor;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.bytes.BytesNodes;
import com.oracle.graal.python.builtins.objects.bytes.PBytes;
import com.oracle.graal.python.builtins.objects.bytes.PBytesLike;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.clinic.ArgumentCastNode;
import com.oracle.graal.python.nodes.function.builtins.clinic.ArgumentClinicProvider;
import com.oracle.graal.python.nodes.truffle.PythonArithmeticTypes;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.graal.python.runtime.sequence.storage.SequenceStorage;
import com.oracle.truffle.api.CompilerDirectives.ValueType;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NeverDefault;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.TypeSystemReference;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;

@CoreFunctions(extendClasses = PythonBuiltinClassType.PLZMACompressor)
public final class LZMACompressorBuiltins extends PythonBuiltins {

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return LZMACompressorBuiltinsFactory.getFactories();
    }

    @Builtin(name = J___INIT__, minNumOfPositionalArgs = 1, parameterNames = {"$self", "format", "check", "preset", "filters"})
    @ArgumentClinic(name = "format", conversion = ClinicConversion.Int, defaultValue = "LZMAModuleBuiltins.FORMAT_XZ", useDefaultForNone = true)
    @ArgumentClinic(name = "check", conversion = ClinicConversion.Int, defaultValue = "-1", useDefaultForNone = true)
    @ArgumentClinic(name = "preset", conversionClass = ExpectUINT32Node.class, defaultValue = "PNone.NO_VALUE")
    @GenerateNodeFactory
    @TypeSystemReference(PythonArithmeticTypes.class)
    public abstract static class InitNode extends PythonClinicBuiltinNode {

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return LZMACompressorBuiltinsClinicProviders.InitNodeClinicProviderGen.INSTANCE;
        }

        @Specialization(guards = {"!badIntegrity(format, check)", "!badRawFilter(format, filters)"})
        PNone init(VirtualFrame frame, LZMACompressor self, int format, int check, long preset, PNone filters,
                        @Shared("i") @Cached LZMANodes.LZMACompressInit compressInit) {
            self.setCheck(check == -1 && format == FORMAT_XZ ? CHECK_CRC64 : check);
            compressInit.execute(frame, self, format, preset, filters);
            return PNone.NONE;
        }

        @Specialization(guards = {"!badIntegrity(format, check)", "!badRawFilter(format, filters)"})
        PNone init(VirtualFrame frame, LZMACompressor self, int format, int check, @SuppressWarnings("unused") PNone preset, Object filters,
                        @Shared("i") @Cached LZMANodes.LZMACompressInit compressInit) {
            self.setCheck(check == -1 && format == FORMAT_XZ ? CHECK_CRC64 : check);
            compressInit.execute(frame, self, format, PRESET_DEFAULT, filters);
            return PNone.NONE;
        }

        @Specialization(guards = "badIntegrity(format, check)")
        @SuppressWarnings("unused")
        PNone integrityError(LZMACompressor self, long format, long check, Object preset, Object filters) {
            throw raise(ValueError, INTEGRITY_CHECKS_ONLY_SUPPORTED_BY);
        }

        @Specialization(guards = {"!badIntegrity(format, check)", "badPresetFilters(preset, filters)"})
        @SuppressWarnings("unused")
        PNone presetError(LZMACompressor self, long format, long check, Object preset, Object filters) {
            throw raise(ValueError, CANNOT_SPECIFY_PREST_AND_FILTER_CHAIN);
        }

        @Specialization(guards = {"!badIntegrity(format, check)", "!badPresetFilters(preset, filters)", "badRawFilter(format, filters)"})
        @SuppressWarnings("unused")
        PNone rawError(LZMACompressor self, long format, long check, Object preset, PNone filters) {
            throw raise(ValueError, MUST_SPECIFY_FILTERS);
        }

        protected static boolean badIntegrity(long format, long check) {
            return format != FORMAT_XZ && check != -1 && check != CHECK_NONE;
        }

        protected static boolean badPresetFilters(Object preset, Object filters) {
            return !PGuards.isPNone(preset) && !PGuards.isPNone(filters);
        }

        protected static boolean badRawFilter(long format, Object filters) {
            return format == FORMAT_RAW && PGuards.isPNone(filters);
        }

        protected static boolean isValid(long format, long check, Object preset, Object filters) {
            return !badIntegrity(format, check) && !badPresetFilters(preset, filters) && !badRawFilter(format, filters);
        }
    }

    @Builtin(name = "compress", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    @TypeSystemReference(PythonArithmeticTypes.class)
    abstract static class CompressNode extends PythonBinaryBuiltinNode {

        @Specialization(guards = {"!self.isFlushed()"})
        static PBytes doBytes(VirtualFrame frame, LZMACompressor self, Object data,
                        @Bind("this") Node inliningTarget,
                        @Cached GetArrayAndLengthHelperNode getArrayAndLengthHelperNode,
                        @Cached LZMANodes.CompressNode compress,
                        @Cached PythonObjectFactory factory) {
            ArrayAndLength aal = getArrayAndLengthHelperNode.execute(frame, inliningTarget, data);
            return factory.createBytes(compress.compress(inliningTarget, self, PythonContext.get(inliningTarget), aal.array, aal.length));
        }

        @SuppressWarnings("unused")
        @Specialization(guards = "self.isFlushed()")
        PNone error(LZMACompressor self, Object data) {
            throw raise(ValueError, COMPRESSOR_HAS_BEEN_FLUSHED);
        }

        @ValueType
        record ArrayAndLength(byte[] array, int length) {
        }

        @GenerateInline
        @GenerateCached(false)
        abstract static class GetArrayAndLengthHelperNode extends Node {
            abstract ArrayAndLength execute(VirtualFrame frame, Node inliningTarget, Object data);

            @Specialization
            static ArrayAndLength doBytes(Node inliningTarget, PBytesLike data,
                            @Cached SequenceStorageNodes.GetInternalByteArrayNode toBytes) {
                SequenceStorage sequenceStorage = data.getSequenceStorage();
                byte[] bytes = toBytes.execute(inliningTarget, sequenceStorage);
                return new ArrayAndLength(bytes, sequenceStorage.length());
            }

            @Fallback
            static ArrayAndLength doObject(VirtualFrame frame, Object data,
                            @Cached(inline = false) BytesNodes.ToBytesNode toBytes) {
                byte[] bytes = toBytes.execute(frame, data);
                return new ArrayAndLength(bytes, bytes.length);
            }
        }
    }

    @Builtin(name = "flush", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    @TypeSystemReference(PythonArithmeticTypes.class)
    abstract static class FlushNode extends PythonUnaryBuiltinNode {

        @Specialization(guards = {"!self.isFlushed()"})
        PBytes doit(LZMACompressor self,
                        @Bind("this") Node inliningTarget,
                        @Cached LZMANodes.CompressNode compress,
                        @Cached PythonObjectFactory factory) {
            self.setFlushed();
            return factory.createBytes(compress.flush(inliningTarget, self, PythonContext.get(this)));
        }

        @SuppressWarnings("unused")
        @Specialization(guards = "self.isFlushed()")
        PNone error(LZMACompressor self) {
            throw raise(ValueError, REPEATED_CALL_TO_FLUSH);
        }
    }

    public abstract static class ExpectUINT32Node extends ArgumentCastNode.ArgumentCastNodeWithRaise {
        private final Object defaultValue;

        protected ExpectUINT32Node(Object defaultValue) {
            this.defaultValue = defaultValue;
        }

        @Override
        public abstract Object execute(VirtualFrame frame, Object value);

        @Specialization
        Object none(@SuppressWarnings("unused") PNone none) {
            return defaultValue;
        }

        @Specialization(guards = "!isPNone(value)")
        Object doOthers(Object value,
                        @Cached LZMANodes.ToUINT32Option toUINT32Option) {
            return toUINT32Option.execute(value);
        }

        protected ExpectUINT32Node createRec() {
            return LZMACompressorBuiltinsFactory.ExpectUINT32NodeGen.create(defaultValue);
        }

        @ClinicConverterFactory
        @NeverDefault
        public static ExpectUINT32Node create(@ClinicConverterFactory.DefaultValue Object defaultValue) {
            return LZMACompressorBuiltinsFactory.ExpectUINT32NodeGen.create(defaultValue);
        }
    }

}
