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

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.PLZMACompressor;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.PLZMADecompressor;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.ValueError;
import static com.oracle.graal.python.runtime.NFILZMASupport.CHECK_CRC32_INDEX;
import static com.oracle.graal.python.runtime.NFILZMASupport.CHECK_CRC64_INDEX;
import static com.oracle.graal.python.runtime.NFILZMASupport.CHECK_ID_MAX_INDEX;
import static com.oracle.graal.python.runtime.NFILZMASupport.CHECK_NONE_INDEX;
import static com.oracle.graal.python.runtime.NFILZMASupport.CHECK_SHA256_INDEX;
import static com.oracle.graal.python.runtime.NFILZMASupport.CHECK_UNKNOWN_INDEX;
import static com.oracle.graal.python.runtime.NFILZMASupport.FILTER_ARMTHUMB_INDEX;
import static com.oracle.graal.python.runtime.NFILZMASupport.FILTER_ARM_INDEX;
import static com.oracle.graal.python.runtime.NFILZMASupport.FILTER_DELTA_INDEX;
import static com.oracle.graal.python.runtime.NFILZMASupport.FILTER_IA64_INDEX;
import static com.oracle.graal.python.runtime.NFILZMASupport.FILTER_LZMA1_INDEX;
import static com.oracle.graal.python.runtime.NFILZMASupport.FILTER_LZMA2_INDEX;
import static com.oracle.graal.python.runtime.NFILZMASupport.FILTER_POWERPC_INDEX;
import static com.oracle.graal.python.runtime.NFILZMASupport.FILTER_SPARC_INDEX;
import static com.oracle.graal.python.runtime.NFILZMASupport.FILTER_X86_INDEX;
import static com.oracle.graal.python.runtime.NFILZMASupport.FORMAT_ALONE_INDEX;
import static com.oracle.graal.python.runtime.NFILZMASupport.FORMAT_AUTO_INDEX;
import static com.oracle.graal.python.runtime.NFILZMASupport.FORMAT_RAW_INDEX;
import static com.oracle.graal.python.runtime.NFILZMASupport.FORMAT_XZ_INDEX;
import static com.oracle.graal.python.runtime.NFILZMASupport.MF_BT2_INDEX;
import static com.oracle.graal.python.runtime.NFILZMASupport.MF_BT3_INDEX;
import static com.oracle.graal.python.runtime.NFILZMASupport.MF_BT4_INDEX;
import static com.oracle.graal.python.runtime.NFILZMASupport.MF_HC3_INDEX;
import static com.oracle.graal.python.runtime.NFILZMASupport.MF_HC4_INDEX;
import static com.oracle.graal.python.runtime.NFILZMASupport.MODE_FAST_INDEX;
import static com.oracle.graal.python.runtime.NFILZMASupport.MODE_NORMAL_INDEX;
import static com.oracle.graal.python.runtime.NFILZMASupport.PRESET_DEFAULT_INDEX;
import static com.oracle.graal.python.runtime.NFILZMASupport.PRESET_EXTREME_INDEX;
import static com.oracle.graal.python.util.PythonUtils.tsLiteral;

import java.util.List;

import org.tukaani.xz.FilterOptions;
import org.tukaani.xz.LZMA2Options;
import org.tukaani.xz.XZ;
import org.tukaani.xz.XZOutputStream;

import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.Python3Core;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.bytes.BytesNodes;
import com.oracle.graal.python.builtins.objects.bytes.PBytes;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.module.PythonModule;
import com.oracle.graal.python.lib.PyNumberAsSizeNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.truffle.PythonArithmeticTypes;
import com.oracle.graal.python.nodes.util.CastToJavaLongLossyNode;
import com.oracle.graal.python.runtime.NFILZMASupport;
import com.oracle.graal.python.runtime.NativeLibrary;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.TypeSystemReference;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.strings.TruffleString;

@CoreFunctions(defineModule = LZMAModuleBuiltins.J__LZMA)
public final class LZMAModuleBuiltins extends PythonBuiltins {

    public static final String J__LZMA = "_lzma";
    public static final TruffleString T__LZMA = tsLiteral(J__LZMA);

    public static final TruffleString T_LZMA_JAVA_ERROR = tsLiteral("This feature is only supported with native library being loaded. Please install 'lzma' library in your system.");

    // defined in '_lzmamodule.c'
    public static final int INITIAL_BUFFER_SIZE = 8192;

    @CompilationFinal public static int CHECK_NONE = XZ.CHECK_NONE;
    @CompilationFinal public static int CHECK_CRC32 = XZ.CHECK_CRC32;
    @CompilationFinal public static int CHECK_CRC64 = XZ.CHECK_CRC64;
    @CompilationFinal public static int CHECK_SHA256 = XZ.CHECK_SHA256;
    // that's only defined in the native 'lzma/check.h' header
    @CompilationFinal public static int CHECK_ID_MAX = 15;
    @CompilationFinal public static int CHECK_UNKNOWN = CHECK_ID_MAX + 1;

    /**
     * that's defined in the native 'lzma/check.h' header and in the condition of
     *
     * @see XZOutputStream#updateFilters(FilterOptions)
     */
    public static final int LZMA_FILTERS_MAX = 4;

    // that's only defined in the native 'lzma/container.h' header
    public static final int LZMA_TELL_NO_CHECK = 0x01;
    public static final int LZMA_TELL_ANY_CHECK = 0x04;
    public static final int LZMA_IGNORE_CHECK = 0x10;

    @CompilationFinal public static long PRESET_DEFAULT = LZMA2Options.PRESET_DEFAULT;
    @CompilationFinal public static long PRESET_EXTREME = LZMA2Options.PRESET_MAX;

    @CompilationFinal public static int MODE_FAST = LZMA2Options.MODE_FAST;
    @CompilationFinal public static int MODE_NORMAL = LZMA2Options.MODE_NORMAL;

    /*
     * filter options; not exposed by the Java lib, so define manually; they are abstracted anyway
     */
    @CompilationFinal(dimensions = 1) public static final long[] FILTERS = {
                    0x20, // FILTER_LZMA1
                    0x21, // FILTER_LZMA2
                    0x03, // FILTER_DELTA
                    0x04, // FILTER_X86
                    0x05, // FILTER_POWERPC
                    0x06, // FILTER_IA64
                    0x07, // FILTER_ARM
                    0x08, // FILTER_ARMTHUMB
                    0x09, // FILTER_SPARC
    };

    @CompilationFinal public static int MF_HC3 = LZMA2Options.MF_HC4 - 1;
    @CompilationFinal public static int MF_HC4 = LZMA2Options.MF_HC4;
    @CompilationFinal public static int MF_BT2 = LZMA2Options.MF_BT4 - 2;
    @CompilationFinal public static int MF_BT3 = LZMA2Options.MF_BT4 - 1;
    @CompilationFinal public static int MF_BT4 = LZMA2Options.MF_BT4;

    // defined in '_lzmamodule.c'
    @CompilationFinal public static int FORMAT_AUTO = 0;
    @CompilationFinal public static int FORMAT_XZ = 1;
    @CompilationFinal public static int FORMAT_ALONE = 2;
    @CompilationFinal public static int FORMAT_RAW = 3;

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return LZMAModuleBuiltinsFactory.getFactories();
    }

    @Override
    public void initialize(Python3Core core) {
        super.initialize(core);
    }

    private static Object as(Python3Core core, int[] a) {
        return core.getContext().getEnv().asGuestValue(a);
    }

    private static Object as(Python3Core core, long[] a) {
        return core.getContext().getEnv().asGuestValue(a);
    }

    @Override
    public void postInitialize(Python3Core c) {
        super.postInitialize(c);
        NFILZMASupport lzmaSupport = c.getContext().getNFILZMASupport();
        PythonModule lzmaModule = c.lookupBuiltinModule(T__LZMA);
        int[] formats = new int[4];
        int[] checks = new int[6];
        int[] mfs = new int[5];
        int[] modes = new int[2];
        long[] preset = new long[2];
        if (lzmaSupport.isAvailable()) {
            try {
                lzmaSupport.getMacros(as(c, formats),
                                as(c, checks), as(c, FILTERS),
                                as(c, mfs), as(c, modes), as(c, preset));
                FORMAT_AUTO = formats[FORMAT_AUTO_INDEX];
                FORMAT_XZ = formats[FORMAT_XZ_INDEX];
                FORMAT_ALONE = formats[FORMAT_ALONE_INDEX];
                FORMAT_RAW = formats[FORMAT_RAW_INDEX];
                CHECK_NONE = checks[CHECK_NONE_INDEX];
                CHECK_CRC32 = checks[CHECK_CRC32_INDEX];
                CHECK_CRC64 = checks[CHECK_CRC64_INDEX];
                CHECK_SHA256 = checks[CHECK_SHA256_INDEX];
                CHECK_ID_MAX = checks[CHECK_ID_MAX_INDEX];
                CHECK_UNKNOWN = checks[CHECK_UNKNOWN_INDEX];
                MF_HC3 = mfs[MF_HC3_INDEX];
                MF_HC4 = mfs[MF_HC4_INDEX];
                MF_BT2 = mfs[MF_BT2_INDEX];
                MF_BT3 = mfs[MF_BT3_INDEX];
                MF_BT4 = mfs[MF_BT4_INDEX];
                MODE_FAST = modes[MODE_FAST_INDEX];
                MODE_NORMAL = modes[MODE_NORMAL_INDEX];
                PRESET_DEFAULT = preset[PRESET_DEFAULT_INDEX];
                PRESET_EXTREME = preset[PRESET_EXTREME_INDEX];
            } catch (NativeLibrary.NativeLibraryCannotBeLoaded e) {
                lzmaSupport.notAvailable();
                // ignore and proceed without native lzma support and use the java port.
            }
        }
        lzmaModule.setAttribute(tsLiteral("CHECK_NONE"), CHECK_NONE);
        lzmaModule.setAttribute(tsLiteral("CHECK_CRC32"), CHECK_CRC32);
        lzmaModule.setAttribute(tsLiteral("CHECK_CRC64"), CHECK_CRC64);
        lzmaModule.setAttribute(tsLiteral("CHECK_SHA256"), CHECK_SHA256);
        lzmaModule.setAttribute(tsLiteral("CHECK_ID_MAX"), CHECK_ID_MAX);
        lzmaModule.setAttribute(tsLiteral("CHECK_UNKNOWN"), CHECK_UNKNOWN);
        lzmaModule.setAttribute(tsLiteral("FILTER_LZMA1"), FILTERS[FILTER_LZMA1_INDEX]);
        lzmaModule.setAttribute(tsLiteral("FILTER_LZMA2"), FILTERS[FILTER_LZMA2_INDEX]);
        lzmaModule.setAttribute(tsLiteral("FILTER_DELTA"), FILTERS[FILTER_DELTA_INDEX]);
        lzmaModule.setAttribute(tsLiteral("FILTER_X86"), FILTERS[FILTER_X86_INDEX]);
        lzmaModule.setAttribute(tsLiteral("FILTER_POWERPC"), FILTERS[FILTER_POWERPC_INDEX]);
        lzmaModule.setAttribute(tsLiteral("FILTER_IA64"), FILTERS[FILTER_IA64_INDEX]);
        lzmaModule.setAttribute(tsLiteral("FILTER_ARM"), FILTERS[FILTER_ARM_INDEX]);
        lzmaModule.setAttribute(tsLiteral("FILTER_ARMTHUMB"), FILTERS[FILTER_ARMTHUMB_INDEX]);
        lzmaModule.setAttribute(tsLiteral("FILTER_SPARC"), FILTERS[FILTER_SPARC_INDEX]);
        lzmaModule.setAttribute(tsLiteral("FORMAT_AUTO"), FORMAT_AUTO);
        lzmaModule.setAttribute(tsLiteral("FORMAT_XZ"), FORMAT_XZ);
        lzmaModule.setAttribute(tsLiteral("FORMAT_ALONE"), FORMAT_ALONE);
        lzmaModule.setAttribute(tsLiteral("FORMAT_RAW"), FORMAT_RAW);
        lzmaModule.setAttribute(tsLiteral("PRESET_DEFAULT"), PRESET_DEFAULT);
        lzmaModule.setAttribute(tsLiteral("PRESET_EXTREME"), PRESET_EXTREME);
        lzmaModule.setAttribute(tsLiteral("MODE_FAST"), MODE_FAST);
        lzmaModule.setAttribute(tsLiteral("MODE_NORMAL"), MODE_NORMAL);
        lzmaModule.setAttribute(tsLiteral("MF_BT2"), MF_BT2);
        lzmaModule.setAttribute(tsLiteral("MF_BT3"), MF_BT3);
        lzmaModule.setAttribute(tsLiteral("MF_BT4"), MF_BT4);
        lzmaModule.setAttribute(tsLiteral("MF_HC3"), MF_HC3);
        lzmaModule.setAttribute(tsLiteral("MF_HC4"), MF_HC4);
    }

    @Builtin(name = "LZMACompressor", minNumOfPositionalArgs = 1, takesVarArgs = true, takesVarKeywordArgs = true, constructsClass = PLZMACompressor)
    @GenerateNodeFactory
    @TypeSystemReference(PythonArithmeticTypes.class)
    abstract static class LZMACompressorNode extends PythonBuiltinNode {

        @Specialization
        LZMAObject doNew(Object cls, @SuppressWarnings("unused") Object arg) {
            // data filled in subsequent __init__ call - see LZMACompressorBuiltins.InitNode
            return factory().createLZMACompressor(cls, getContext().getNFILZMASupport().isAvailable());
        }
    }

    @Builtin(name = "LZMADecompressor", minNumOfPositionalArgs = 1, takesVarArgs = true, takesVarKeywordArgs = true, constructsClass = PLZMADecompressor)
    @GenerateNodeFactory
    @TypeSystemReference(PythonArithmeticTypes.class)
    abstract static class LZMADecompressorNode extends PythonBuiltinNode {

        @Specialization
        LZMAObject doNew(Object cls, @SuppressWarnings("unused") Object arg) {
            // data filled in subsequent __init__ call - see LZMADecompressorBuiltins.InitNode
            return factory().createLZMADecompressor(cls, getContext().getNFILZMASupport().isAvailable());
        }
    }

    @Builtin(name = "is_check_supported", minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    @TypeSystemReference(PythonArithmeticTypes.class)
    abstract static class IsCheckSupportedNode extends PythonUnaryBuiltinNode {

        @Specialization
        static boolean doInt(VirtualFrame frame, Object checkID,
                        @Bind("this") Node inliningTarget,
                        @Cached PyNumberAsSizeNode asSizeNode,
                        @Cached LZMANodes.IsCheckSupported isCheckSupported) {
            return isCheckSupported.execute(asSizeNode.executeExact(frame, inliningTarget, checkID, ValueError));
        }
    }

    @Builtin(name = "_encode_filter_properties", minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    @TypeSystemReference(PythonArithmeticTypes.class)
    abstract static class EncodeFilterPropertiesNode extends PythonUnaryBuiltinNode {

        @Specialization
        PBytes encode(VirtualFrame frame, Object filter,
                        @Cached LZMANodes.EncodeFilterProperties encodeFilterProperties) {
            return factory().createBytes(encodeFilterProperties.execute(frame, filter));
        }
    }

    @Builtin(name = "_decode_filter_properties", minNumOfPositionalArgs = 2, maxNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    @TypeSystemReference(PythonArithmeticTypes.class)
    abstract static class DecodeFilterPropertiesNode extends PythonBinaryBuiltinNode {

        @Specialization
        PDict encode(VirtualFrame frame, Object id, Object encodedProps,
                        @Bind("this") Node inliningTarget,
                        @Cached CastToJavaLongLossyNode toLong,
                        @Cached BytesNodes.ToBytesNode toBytes,
                        @Cached LZMANodes.DecodeFilterProperties decodeFilterProperties) {
            byte[] bytes = toBytes.execute(frame, encodedProps);
            PDict dict = factory().createDict();
            decodeFilterProperties.execute(frame, toLong.execute(inliningTarget, id), bytes, dict);
            return dict;
        }
    }
}
