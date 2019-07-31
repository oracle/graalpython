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
package com.oracle.graal.python.builtins.modules;

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.PLZMACompressor;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.PLZMADecompressor;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.ValueError;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

import org.tukaani.xz.ARMOptions;
import org.tukaani.xz.ARMThumbOptions;
import org.tukaani.xz.DeltaOptions;
import org.tukaani.xz.FilterOptions;
import org.tukaani.xz.IA64Options;
import org.tukaani.xz.LZMA2Options;
import org.tukaani.xz.LZMAOutputStream;
import org.tukaani.xz.PowerPCOptions;
import org.tukaani.xz.SPARCOptions;
import org.tukaani.xz.UnsupportedOptionsException;
import org.tukaani.xz.X86Options;
import org.tukaani.xz.XZ;
import org.tukaani.xz.XZOutputStream;

import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.lzma.PLZMACompressor;
import com.oracle.graal.python.builtins.objects.lzma.PLZMADecompressor;
import com.oracle.graal.python.builtins.objects.type.LazyPythonClass;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.datamodel.IsSequenceNode;
import com.oracle.graal.python.nodes.datamodel.PDataModelEmulationNode.PDataModelEmulationContextManager;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.object.IsBuiltinClassProfile;
import com.oracle.graal.python.nodes.subscript.GetItemNode;
import com.oracle.graal.python.nodes.truffle.PythonArithmeticTypes;
import com.oracle.graal.python.nodes.util.CastToIndexNode;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.PythonCore;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleLanguage.ContextReference;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.TypeSystemReference;
import com.oracle.truffle.api.frame.VirtualFrame;

@CoreFunctions(defineModule = "_lzma")
public class LZMAModuleBuiltins extends PythonBuiltins {

    // that's only define in the native 'lzma/check.h' header
    private static final int LZMA_CHECK_ID_MAX = 15;

    /*
     * filter options; not exposed by the Java lib, so define manually; they are abstracted anyway
     */
    private static final int FILTER_LZMA1 = 0x20;
    private static final int FILTER_LZMA2 = 0x21;
    private static final int FILTER_DELTA = 0x3;
    private static final int FILTER_X86 = 0x4;
    private static final int FILTER_POWERPC = 0x5;
    private static final int FILTER_IA64 = 0x6;
    private static final int FILTER_ARM = 0x7;
    private static final int FILTER_ARMTHUMB = 0x8;
    private static final int FILTER_SPARC = 0x9;

    // defined in '_lzmamodule.c'
    public static final int FORMAT_AUTO = 0;
    public static final int FORMAT_XZ = 1;
    public static final int FORMAT_ALONE = 2;
    public static final int FORMAT_RAW = 3;

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinNode>> getNodeFactories() {
        return LZMAModuleBuiltinsFactory.getFactories();
    }

    @Override
    public void initialize(PythonCore core) {
        builtinConstants.put("CHECK_NONE", XZ.CHECK_NONE);
        builtinConstants.put("CHECK_CRC32", XZ.CHECK_CRC32);
        builtinConstants.put("CHECK_CRC64", XZ.CHECK_CRC64);
        builtinConstants.put("CHECK_SHA256", XZ.CHECK_SHA256);
        builtinConstants.put("CHECK_ID_MAX", LZMA_CHECK_ID_MAX);

        // as defined in '_lzmamodule.c'
        builtinConstants.put("CHECK_UNKNOWN", LZMA_CHECK_ID_MAX + 1);

        builtinConstants.put("FILTER_LZMA1", FILTER_LZMA1);
        builtinConstants.put("FILTER_LZMA2", FILTER_LZMA2);
        builtinConstants.put("FILTER_DELTA", FILTER_DELTA);
        builtinConstants.put("FILTER_X86", FILTER_X86);
        builtinConstants.put("FILTER_POWERPC", FILTER_POWERPC);
        builtinConstants.put("FILTER_IA64", FILTER_IA64);
        builtinConstants.put("FILTER_ARM", FILTER_ARM);
        builtinConstants.put("FILTER_ARMTHUMB", FILTER_ARMTHUMB);
        builtinConstants.put("FILTER_SPARC", FILTER_SPARC);

        builtinConstants.put("FORMAT_AUTO", FORMAT_AUTO);
        builtinConstants.put("FORMAT_XZ", FORMAT_XZ);
        builtinConstants.put("FORMAT_ALONE", FORMAT_ALONE);
        builtinConstants.put("FORMAT_RAW", FORMAT_RAW);

        builtinConstants.put("PRESET_DEFAULT", LZMA2Options.PRESET_DEFAULT);
        builtinConstants.put("PRESET_EXTREME", LZMA2Options.PRESET_MAX);

        builtinConstants.put("MODE_FAST", LZMA2Options.MODE_FAST);
        builtinConstants.put("MODE_NORMAL", LZMA2Options.MODE_NORMAL);

        builtinConstants.put("MF_BT2", LZMA2Options.MF_BT4 - 2);
        builtinConstants.put("MF_BT3", LZMA2Options.MF_BT4 - 1);
        builtinConstants.put("MF_BT4", LZMA2Options.MF_BT4);
        builtinConstants.put("MF_HC3", LZMA2Options.MF_HC4 - 1);
        builtinConstants.put("MF_HC4", LZMA2Options.MF_HC4);

        super.initialize(core);
    }

    abstract static class LZMANode extends PythonBuiltinNode {

        @Child private IsSequenceNode isSequenceNode;
        @Child private GetItemNode getItemNode;
        @Child private CastToIndexNode castToLongNode;
        @Child private BuiltinFunctions.LenNode lenNode;
        @CompilationFinal private IsBuiltinClassProfile keyErrorProfile;

        protected static LZMA2Options parseLZMAOptions(int preset) {
            // the easy one; uses 'preset'
            LZMA2Options lzmaOptions = null;
            try {
                lzmaOptions = new LZMA2Options();
                lzmaOptions.setPreset(preset);
            } catch (UnsupportedOptionsException e) {
                lzmaOptions = null;
                // TODO throw LZMAError
                e.printStackTrace();
            }
            return lzmaOptions;
        }

        // corresponds to 'parse_filter_chain_spec' in '_lzmamodule.c'
        protected FilterOptions[] parseFilterChainSpec(VirtualFrame frame, Object filters) {
            int n = len(frame, filters);
            FilterOptions[] optionsChain = new FilterOptions[n];
            for (int i = 0; i < n; i++) {
                optionsChain[i] = convertLZMAFilter(frame, getItem(frame, filters, i));
            }
            return optionsChain;
        }

        // corresponds to 'lzma_filter_converter' in '_lzmamodule.c'
        private FilterOptions convertLZMAFilter(VirtualFrame frame, Object spec) {
            if (!isSequence(frame, getContextRef(), spec)) {
                throw raise(PythonBuiltinClassType.TypeError, "Filter specifier must be a dict or dict-like object");
            }

            Object idObj = PNone.NONE;
            try {
                idObj = getItem(frame, spec, "id");
            } catch (PException e) {
                if (ensureKeyErrorProfile().profileException(e, PythonBuiltinClassType.KeyError)) {
                    throw raise(ValueError, "Filter specifier must have an \"id\" entry");
                }
            }

            int id = asInt(idObj);
            FilterOptions options = createFilterById(id);
            if (options == null) {
                throw raise(ValueError, "Invalid filter ID: %d", id);
            }
            return options;
        }

        @TruffleBoundary
        private static FilterOptions createFilterById(int id) {
            switch (id) {
                case FILTER_LZMA1:
                case FILTER_LZMA2:
                    return new LZMA2Options();
                case FILTER_DELTA:
                    return new DeltaOptions();
                case FILTER_X86:
                    return new X86Options();
                case FILTER_POWERPC:
                    return new PowerPCOptions();
                case FILTER_IA64:
                    return new IA64Options();
                case FILTER_ARM:
                    return new ARMOptions();
                case FILTER_ARMTHUMB:
                    return new ARMThumbOptions();
                case FILTER_SPARC:
                    return new SPARCOptions();
                default:
                    return null;
            }
        }

        private boolean isSequence(VirtualFrame frame, ContextReference<PythonContext> contextRef, Object obj) {
            if (isSequenceNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                isSequenceNode = insert(IsSequenceNode.create());
            }
            try (PDataModelEmulationContextManager cm = isSequenceNode.withGlobalState(contextRef, frame)) {
                return cm.execute(obj);
            }
        }

        private Object getItem(VirtualFrame frame, Object receiver, Object key) {
            if (getItemNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                getItemNode = insert(GetItemNode.create());
            }
            return getItemNode.executeWith(frame, receiver, key);
        }

        private IsBuiltinClassProfile ensureKeyErrorProfile() {
            if (keyErrorProfile == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                keyErrorProfile = IsBuiltinClassProfile.create();
            }
            return keyErrorProfile;
        }

        private int asInt(Object obj) {
            if (castToLongNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                castToLongNode = insert(CastToIndexNode.create());
            }
            return castToLongNode.execute(obj);
        }

        private int len(VirtualFrame frame, Object obj) {
            if (lenNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                lenNode = insert(BuiltinFunctionsFactory.LenNodeFactory.create());
            }
            return asInt(lenNode.execute(frame, obj));
        }

        protected static boolean isNoneOrNoValue(Object obj) {
            return PGuards.isNone(obj) || PGuards.isNoValue(obj);
        }

    }

    @Builtin(name = "LZMACompressor", parameterNames = {"cls", "format", "check", "preset", "filters"}, constructsClass = PLZMACompressor)
    @GenerateNodeFactory
    @TypeSystemReference(PythonArithmeticTypes.class)
    abstract static class LZMACompressorNode extends LZMANode {

        // as define in '_lzmamodule.c'
        private static final int INITIAL_BUFFER_SIZE = 8192;

        @Specialization
        PLZMACompressor doCreate(VirtualFrame frame, LazyPythonClass cls, Object formatObj, Object checkObj, Object presetObj, Object filters,
                        @Cached CastToIndexNode castFormatToIntNode,
                        @Cached CastToIndexNode castCheckToIntNode,
                        @Cached CastToIndexNode castToIntNode) {

            int format = FORMAT_XZ;
            int check = -1;
            int preset = LZMA2Options.PRESET_DEFAULT;

            if (!isNoneOrNoValue(formatObj)) {
                format = castFormatToIntNode.execute(formatObj);
            }

            if (!isNoneOrNoValue(checkObj)) {
                check = castCheckToIntNode.execute(checkObj);
            }

            if (format != FORMAT_XZ && check != -1 && check != XZ.CHECK_NONE) {
                throw raise(ValueError, "Integrity checks are only supported by FORMAT_XZ");
            }
            if (!isNoneOrNoValue(presetObj) && !isNoneOrNoValue(filters)) {
                throw raise(ValueError, "Cannot specify both preset and filter chain");
            }

            if (!isNoneOrNoValue(presetObj)) {
                preset = castToIntNode.execute(presetObj);
            }

            try {
                ByteArrayOutputStream bos = new ByteArrayOutputStream(INITIAL_BUFFER_SIZE);
                switch (format) {
                    case FORMAT_XZ:
                        if (check == -1) {
                            check = XZ.CHECK_CRC64;
                        }

                        XZOutputStream xzOutputStream;
                        if (isNoneOrNoValue(filters)) {
                            LZMA2Options lzmaOptions = parseLZMAOptions(preset);
                            xzOutputStream = new XZOutputStream(bos, lzmaOptions, check);
                        } else {
                            FilterOptions[] optionsChain = parseFilterChainSpec(frame, filters);
                            xzOutputStream = new XZOutputStream(bos, optionsChain, check);
                        }
                        return factory().createLZMACompressor(cls, xzOutputStream, bos);

                    case FORMAT_ALONE:
                        LZMAOutputStream lzmaOutputStream;
                        if (isNoneOrNoValue(filters)) {
                            LZMA2Options lzmaOptions = parseLZMAOptions(preset);
                            lzmaOutputStream = new LZMAOutputStream(bos, lzmaOptions, check);
                        } else {
                            FilterOptions[] optionsChain = parseFilterChainSpec(frame, filters);
                            if (optionsChain.length != 1 && !(optionsChain[0] instanceof LZMA2Options)) {
                                throw raise(ValueError, "Invalid filter chain for FORMAT_ALONE - must be a single LZMA1 filter");
                            }
                            lzmaOutputStream = new LZMAOutputStream(bos, (LZMA2Options) optionsChain[0], check);
                        }
                        return factory().createLZMACompressor(cls, lzmaOutputStream, bos);

                    case FORMAT_RAW:
                        throw raise(ValueError, "RAW format unsupported");

                    default:
                        throw raise(ValueError, "Invalid container format: %d", format);
                }
            } catch (IOException e) {
                // TODO throw LZMAError
                throw raise(ValueError, "%m", e);
            }
        }
    }

    @Builtin(name = "LZMADecompressor", parameterNames = {"cls", "format", "memlimit", "filters"}, constructsClass = PLZMADecompressor)
    @GenerateNodeFactory
    @TypeSystemReference(PythonArithmeticTypes.class)
    abstract static class LZMADecompressorNode extends LZMANode {

        @Child private IsSequenceNode isSequenceNode;
        @Child private GetItemNode getItemNode;
        @Child private CastToIndexNode castToLongNode;
        @Child private BuiltinFunctions.LenNode lenNode;

        @CompilationFinal private IsBuiltinClassProfile keyErrorProfile;

        @Specialization
        PLZMADecompressor doCreate(LazyPythonClass cls, Object formatObj, Object memlimitObj, Object filters,
                        @Cached CastToIndexNode castFormatToIntNode,
                        @Cached CastToIndexNode castCheckToIntNode) {

            int format = FORMAT_AUTO;
            int memlimit = Integer.MAX_VALUE;

            if (!isNoneOrNoValue(formatObj)) {
                format = castFormatToIntNode.execute(formatObj);
            }

            if (!isNoneOrNoValue(memlimitObj)) {
                if (format == FORMAT_RAW) {
                    throw raise(ValueError, "Cannot specify memory limit with FORMAT_RAW");
                }
                memlimit = castCheckToIntNode.execute(memlimitObj);
            }

            if (format == FORMAT_RAW && isNoneOrNoValue(filters)) {
                throw raise(ValueError, "Must specify filters for FORMAT_RAW");
            } else if (format != FORMAT_RAW && !isNoneOrNoValue(filters)) {
                throw raise(ValueError, "Cannot specify filters except with FORMAT_RAW");
            }

            // this switch is just to validate the 'format' argument
            switch (format) {
                case FORMAT_AUTO:
                case FORMAT_XZ:
                case FORMAT_ALONE:
                    return factory().createLZMADecompressor(cls, format, memlimit);

                case FORMAT_RAW:
                    throw raise(ValueError, "RAW format unsupported");

                default:
                    throw raise(ValueError, "Invalid container format: %d", format);
            }
        }
    }

}
