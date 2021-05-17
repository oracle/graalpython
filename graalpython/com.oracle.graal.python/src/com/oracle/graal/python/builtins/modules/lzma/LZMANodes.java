/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.LZMAError;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.MemoryError;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.OSError;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.OverflowError;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.SystemError;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.ValueError;
import static com.oracle.graal.python.builtins.modules.lzma.LZMAModuleBuiltins.FILTERS;
import static com.oracle.graal.python.builtins.modules.lzma.LZMAModuleBuiltins.INITIAL_BUFFER_SIZE;
import static com.oracle.graal.python.builtins.modules.lzma.LZMAModuleBuiltins.LZMA_FILTERS_MAX;
import static com.oracle.graal.python.builtins.modules.lzma.LZMAModuleBuiltins.LZMA_JAVA_ERROR;
import static com.oracle.graal.python.builtins.modules.lzma.LZMAModuleBuiltins.LZMA_TELL_ANY_CHECK;
import static com.oracle.graal.python.builtins.modules.lzma.LZMAModuleBuiltins.LZMA_TELL_NO_CHECK;
import static com.oracle.graal.python.builtins.modules.lzma.LZMAModuleBuiltins.PRESET_DEFAULT;
import static com.oracle.graal.python.builtins.objects.bytes.BytesUtils.append;
import static com.oracle.graal.python.builtins.objects.bytes.BytesUtils.createOutputStream;
import static com.oracle.graal.python.builtins.objects.bytes.BytesUtils.toByteArray;
import static com.oracle.graal.python.nodes.ErrorMessages.FILTER_SPECIFIER_MUST_HAVE;
import static com.oracle.graal.python.nodes.ErrorMessages.FILTER_SPEC_MUST_BE_DICT;
import static com.oracle.graal.python.nodes.ErrorMessages.INTEGER_REQUIRED;
import static com.oracle.graal.python.nodes.ErrorMessages.INVALID_COMPRESSION_PRESET;
import static com.oracle.graal.python.nodes.ErrorMessages.INVALID_CONTAINER_FORMAT;
import static com.oracle.graal.python.nodes.ErrorMessages.INVALID_FILTER;
import static com.oracle.graal.python.nodes.ErrorMessages.INVALID_FILTER_CHAIN_FOR_FORMAT;
import static com.oracle.graal.python.nodes.ErrorMessages.VALUE_TOO_LARGE_TO_FIT_INTO_INDEX;
import static com.oracle.graal.python.runtime.NFILZMASupport.LZMA_ID_ERROR;
import static com.oracle.graal.python.runtime.NFILZMASupport.LZMA_PRESET_ERROR;
import static com.oracle.graal.python.runtime.NFILZMASupport.MAX_OPTS_INDEX;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.TypeError;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.util.Arrays;

import org.tukaani.xz.ARMOptions;
import org.tukaani.xz.ARMThumbOptions;
import org.tukaani.xz.CorruptedInputException;
import org.tukaani.xz.DeltaOptions;
import org.tukaani.xz.FilterOptions;
import org.tukaani.xz.IA64Options;
import org.tukaani.xz.LZMA2Options;
import org.tukaani.xz.MemoryLimitException;
import org.tukaani.xz.PowerPCOptions;
import org.tukaani.xz.SPARCOptions;
import org.tukaani.xz.UnsupportedOptionsException;
import org.tukaani.xz.X86Options;
import org.tukaani.xz.XZFormatException;
import org.tukaani.xz.check.Check;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.modules.lzma.LZMAObject.LZMACompressor;
import com.oracle.graal.python.builtins.modules.lzma.LZMAObject.LZMADecompressor;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.common.HashingStorage;
import com.oracle.graal.python.builtins.objects.common.HashingStorageLibrary;
import com.oracle.graal.python.builtins.objects.common.SequenceNodes;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.ints.PInt;
import com.oracle.graal.python.builtins.objects.object.PythonObjectLibrary;
import com.oracle.graal.python.lib.PyObjectSizeNode;
import com.oracle.graal.python.lib.PyObjectStrAsJavaStringNode;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.PNodeWithRaise;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.subscript.GetItemNode;
import com.oracle.graal.python.nodes.util.CannotCastException;
import com.oracle.graal.python.nodes.util.CastToJavaLongExactNode;
import com.oracle.graal.python.nodes.util.CastToJavaLongLossyNode;
import com.oracle.graal.python.runtime.NFILZMASupport;
import com.oracle.graal.python.runtime.NativeLibrary;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.exception.PythonErrorType;
import com.oracle.graal.python.util.OverflowException;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.CachedContext;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.api.profiles.ConditionProfile;

public class LZMANodes {

    public static final long MAX_UINT32 = 4294967296L; // 2**32

    /*- that's only defined in the native 'lzma/base.h' header
        LZMA_SYNC_FLUSH, LZMA_FULL_FLUSH, LZMA_FULL_BARRIER aren't needed.
     */
    protected static final int LZMA_RUN = 0;
    protected static final int LZMA_FINISH = 3;

    private static final int LZMA_OK = 0;
    private static final int LZMA_STREAM_END = 1;
    private static final int LZMA_NO_CHECK = 2;
    private static final int LZMA_UNSUPPORTED_CHECK = 3;
    private static final int LZMA_GET_CHECK = 4;
    private static final int LZMA_MEM_ERROR = 5;
    private static final int LZMA_MEMLIMIT_ERROR = 6;
    private static final int LZMA_FORMAT_ERROR = 7;
    private static final int LZMA_OPTIONS_ERROR = 8;
    private static final int LZMA_DATA_ERROR = 9;
    private static final int LZMA_BUF_ERROR = 10;
    private static final int LZMA_PROG_ERROR = 11;

    protected static class OptionsState {

        private final String filterType;
        private final Option[] optnames;
        private final long[] options;
        public final HashingStorage dictStorage;

        public OptionsState(String filterType, Option[] optnames, long[] options, HashingStorage dictStorage) {
            this.filterType = filterType;
            this.optnames = optnames;
            this.options = options;
            this.dictStorage = dictStorage;
        }
    }

    @ImportStatic(LZMANodes.class)
    public abstract static class ToUINT32Option extends PNodeWithRaise {

        private final boolean with32BitLimit;

        public ToUINT32Option(boolean with32BitLimit) {
            this.with32BitLimit = with32BitLimit;
        }

        public abstract long execute(Object o);

        @Specialization(guards = "i >= 0")
        long i(int i) {
            return i;
        }

        @Specialization(guards = {"l >= 0", "l <= MAX_UINT32"})
        long l(long l) {
            return l;
        }

        @Specialization
        long ll(long l) {
            if (l < 0) {
                throw raise(OverflowError, "can't convert negative int to unsigned");
            }
            if (l > MAX_UINT32 && with32BitLimit) {
                throw raise(OverflowError, "Value too large for uint32_t type");
            }
            return l;
        }

        @Specialization
        long o(Object o,
                        @Cached CastToJavaLongExactNode cast) {
            try {
                return ll(cast.execute(o));
            } catch (CannotCastException e) {
                throw raise(TypeError, INTEGER_REQUIRED);
            }
        }

        public static ToUINT32Option create(boolean with32BitLimit) {
            return LZMANodesFactory.ToUINT32OptionNodeGen.create(with32BitLimit);
        }

        public static ToUINT32Option create() {
            return create(true);
        }
    }

    @ImportStatic(PGuards.class)
    public abstract static class GetOptionsDict extends PNodeWithRaise {
        public abstract HashingStorage execute(VirtualFrame frame, Object dict);

        @Specialization
        HashingStorage fast(VirtualFrame frame, PDict dict,
                        @Shared("id") @Cached ConditionProfile idErrorProfile,
                        @Shared("k") @Cached ConditionProfile hasKeyErrorProfile,
                        @Shared("h") @CachedLibrary(limit = "2") HashingStorageLibrary hlib) {
            HashingStorage storage = dict.getDictStorage();
            if (idErrorProfile.profile(!hlib.hasKeyWithFrame(storage, "id", hasKeyErrorProfile, frame))) {
                throw raise(ValueError, FILTER_SPECIFIER_MUST_HAVE);
            }
            return storage;
        }

        @Specialization(guards = "!isDict(dict)")
        HashingStorage slow(VirtualFrame frame, Object dict,
                        @Shared("id") @Cached ConditionProfile idErrorProfile,
                        @Shared("k") @Cached ConditionProfile hasKeyErrorProfile,
                        @Cached ConditionProfile dictErrorProfile,
                        @Shared("h") @CachedLibrary(limit = "2") HashingStorageLibrary hlib,
                        @CachedLibrary(limit = "2") PythonObjectLibrary getDictLib) {
            if (dictErrorProfile.profile(!getDictLib.hasDict(dict))) {
                throw raise(TypeError, FILTER_SPEC_MUST_BE_DICT);
            }
            return fast(frame, getDictLib.getDict(dict), idErrorProfile, hasKeyErrorProfile, hlib);
        }
    }

    protected abstract static class ForEachOption extends HashingStorageLibrary.ForEachNode<OptionsState> {

        @TruffleBoundary
        private static int getOptionIndex(String key, OptionsState s) {
            for (int i = 0; i < s.optnames.length; i++) {
                if (key.equals(s.optnames[i].OptName())) {
                    return i;
                }
            }
            return -1;
        }

        protected abstract OptionsState executeOptionState(Object key, OptionsState s);

        @Override
        public OptionsState execute(Object key, OptionsState s) {
            return executeOptionState(key, s);
        }

        @Specialization(limit = "2")
        OptionsState doit(Object key, OptionsState s,
                        @Cached PyObjectStrAsJavaStringNode strNode,
                        @Cached CastToJavaLongLossyNode toLong,
                        @Cached ConditionProfile errProfile,
                        @CachedLibrary("s.dictStorage") HashingStorageLibrary hlib,
                        @Cached PRaiseNode raise) {
            String skey = strNode.execute(null, key);
            int idx = getOptionIndex(skey, s);
            if (errProfile.profile(idx == -1)) {
                throw raise.raise(ValueError, "Invalid filter specifier for %s filter", s.filterType);
            }
            long l = toLong.execute(hlib.getItem(s.dictStorage, skey));
            if (errProfile.profile(l < 0)) {
                throw raise.raise(OverflowError, "can't convert negative int to unsigned");
            }
            if (errProfile.profile(l > MAX_UINT32 && idx > 0) /* filters are special case */) {
                throw raise.raise(OverflowError, "Value too large for uint32_t type");
            }
            s.options[idx] = l;
            return s;
        }
    }

    interface Option {
        String OptName();

        int ordinal();
    }

    enum LZMAFilter {
        LZMA_FILTER_LZMA1,
        LZMA_FILTER_LZMA2,
        LZMA_FILTER_DELTA,
        LZMA_FILTER_X86,
        LZMA_FILTER_POWERPC,
        LZMA_FILTER_IA64,
        LZMA_FILTER_ARM,
        LZMA_FILTER_ARMTHUMB,
        LZMA_FILTER_SPARC,
        UNKNOWN;

        public long v() {
            return FILTERS[ordinal()];
        }

        @ExplodeLoop
        public static LZMAFilter from(long v) {
            for (int i = 0; i < FILTERS.length; i++) {
                if (FILTERS[i] == v) {
                    return LZMAFilter.values()[i];
                }
            }
            return UNKNOWN;
        }
    }

    enum LZMAOption implements Option {

        id("id"),
        preset("preset"),
        dict_size("dict_size"),
        lc("lc"),
        lp("lp"),
        pb("pb"),
        mode("mode"),
        nice_len("nice_len"),
        mf("mf"),
        depth("depth");

        private final String optname;

        LZMAOption(String name) {
            this.optname = name;
        }

        @Override
        public String OptName() {
            return optname;
        }
    }

    enum DeltaOption implements Option {

        id("id"),
        dist("dist");

        private final String optname;

        DeltaOption(String name) {
            this.optname = name;
        }

        @Override
        public String OptName() {
            return optname;
        }
    }

    enum BCJOption implements Option {

        id("id"),
        start_offset("start_offset");

        private final String optname;

        BCJOption(String name) {
            this.optname = name;
        }

        @Override
        public String OptName() {
            return optname;
        }
    }

    // corresponds to 'lzma_filter_converter' in '_lzmamodule.c'
    public abstract static class LZMAFilterConverter extends PNodeWithRaise {

        public abstract long[] execute(VirtualFrame frame, Object spec);

        @Specialization
        long[] converter(VirtualFrame frame, Object spec,
                        @Cached ForEachOption getOptions,
                        @Cached CastToJavaLongLossyNode toLong,
                        @Cached GetOptionsDict getOptionsDict,
                        @CachedLibrary(limit = "2") HashingStorageLibrary hlib) {
            HashingStorage dict = getOptionsDict.execute(frame, spec);
            Object idObj = hlib.getItem(dict, "id");
            long id = toLong.execute(idObj);
            long[] options;
            OptionsState state;
            switch (LZMAFilter.from(id)) {
                case LZMA_FILTER_LZMA1:
                case LZMA_FILTER_LZMA2:
                    options = new long[LZMAOption.values().length];
                    Arrays.fill(options, -1);
                    options[LZMAOption.preset.ordinal()] = PRESET_DEFAULT;
                    state = new OptionsState("LZMA", LZMAOption.values(), options, dict);
                    break;
                case LZMA_FILTER_DELTA:
                    options = new long[DeltaOption.values().length];
                    Arrays.fill(options, -1);
                    state = new OptionsState("delta", DeltaOption.values(), options, dict);
                    break;
                case LZMA_FILTER_X86:
                case LZMA_FILTER_POWERPC:
                case LZMA_FILTER_IA64:
                case LZMA_FILTER_ARM:
                case LZMA_FILTER_ARMTHUMB:
                case LZMA_FILTER_SPARC:
                    options = new long[BCJOption.values().length];
                    Arrays.fill(options, -1);
                    state = new OptionsState("BCJ", BCJOption.values(), options, dict);
                    break;
                default:
                    throw raise(ValueError, INVALID_FILTER, id);
            }
            options[0] = id;
            hlib.forEach(dict, getOptions, state);
            return options;
        }
    }

    // corresponds to 'parse_filter_chain_spec' in '_lzmamodule.c'
    public abstract static class LZMAParseFilterChain extends PNodeWithRaise {

        public abstract long[][] execute(VirtualFrame frame, Object filterSpecs);

        @Specialization
        long[][] parseFilter(VirtualFrame frame, Object filterSpecs,
                        @Cached GetItemNode getItemNode,
                        @Cached LZMAFilterConverter converter,
                        @Cached ConditionProfile errProfile,
                        @Cached SequenceNodes.CheckIsSequenceNode checkIsSequenceNode,
                        @Cached PyObjectSizeNode sizeNode) {
            checkIsSequenceNode.execute(filterSpecs);
            int numFilters = sizeNode.execute(frame, filterSpecs);
            if (errProfile.profile(numFilters > LZMA_FILTERS_MAX)) {
                throw raise(ValueError, "Too many filters - liblzma supports a maximum of %d", LZMA_FILTERS_MAX);
            }
            long[][] filters = new long[numFilters][0];
            for (int i = 0; i < numFilters; i++) {
                filters[i] = converter.execute(frame, getItemNode.execute(frame, filterSpecs, i));
            }
            return filters;
        }
    }

    protected abstract static class NativeFilterChain extends PNodeWithRaise {

        public abstract void execute(VirtualFrame frame, Object lzmast, PythonContext context, Object filterSpecs);

        @Specialization
        void parseFilterChainSpec(VirtualFrame frame, Object lzmast, PythonContext context, Object filterSpecs,
                        @Cached NativeLibrary.InvokeNativeFunction setFilterSpecLZMA,
                        @Cached NativeLibrary.InvokeNativeFunction setFilterSpecDelta,
                        @Cached NativeLibrary.InvokeNativeFunction setFilterSpecBCJ,
                        @Cached ConditionProfile errorProfile,
                        @Cached LZMAParseFilterChain parseFilterChain) {
            long[][] filters = parseFilterChain.execute(frame, filterSpecs);
            for (int i = 0; i < filters.length; i++) {
                setFilterOptions(lzmast, filters[i], i, context, setFilterSpecLZMA, setFilterSpecDelta, setFilterSpecBCJ, errorProfile);

            }
        }

        private void setFilterOptions(Object lzmast, long[] filter, int fidx, PythonContext context,
                        NativeLibrary.InvokeNativeFunction setFilterSpecLZMA,
                        NativeLibrary.InvokeNativeFunction setFilterSpecDelta,
                        NativeLibrary.InvokeNativeFunction setFilterSpecBCJ,
                        ConditionProfile errorProfile) {
            NFILZMASupport lzmaSupport = context.getNFILZMASupport();
            Object opts = context.getEnv().asGuestValue(filter);
            int err;
            long id = filter[0];
            switch (LZMAFilter.from(id)) {
                case LZMA_FILTER_LZMA1:
                case LZMA_FILTER_LZMA2:
                    assert filter.length == 10;
                    err = lzmaSupport.setFilterSpecLZMA(lzmast, fidx, opts, setFilterSpecLZMA);
                    if (errorProfile.profile(err != LZMA_OK)) {
                        if (err == LZMA_PRESET_ERROR) {
                            throw raise(LZMAError, INVALID_COMPRESSION_PRESET, filter[LZMAOption.preset.ordinal()]);
                        }
                        errorHandling(err, getRaiseNode());
                    }
                    return;
                case LZMA_FILTER_DELTA:
                    assert filter.length == 2;
                    err = lzmaSupport.setFilterSpecDelta(lzmast, fidx, opts, setFilterSpecDelta);
                    if (errorProfile.profile(err != LZMA_OK)) {
                        errorHandling(err, getRaiseNode());
                    }
                    return;
                case LZMA_FILTER_X86:
                case LZMA_FILTER_POWERPC:
                case LZMA_FILTER_IA64:
                case LZMA_FILTER_ARM:
                case LZMA_FILTER_ARMTHUMB:
                case LZMA_FILTER_SPARC:
                    assert filter.length == 2;
                    err = lzmaSupport.setFilterSpecBCJ(lzmast, fidx, opts, setFilterSpecBCJ);
                    if (errorProfile.profile(err != LZMA_OK)) {
                        errorHandling(err, getRaiseNode());
                    }
                    return;
            }
            throw raise(ValueError, INVALID_FILTER, id);
        }
    }

    protected abstract static class JavaFilterChain extends PNodeWithRaise {

        public abstract FilterOptions[] execute(VirtualFrame frame, Object filterSpecs);

        @Specialization
        FilterOptions[] parseFilterChainSpec(VirtualFrame frame, Object filterSpecs,
                        @Cached LZMAParseFilterChain parseFilterChain) {
            long[][] filters = parseFilterChain.execute(frame, filterSpecs);
            FilterOptions[] optionsChain = new FilterOptions[filters.length];
            for (int i = 0; i < filters.length; i++) {
                try {
                    optionsChain[i] = getFilterOptions(filters[i]);
                } catch (UnsupportedOptionsException e) {
                    throw raise(ValueError, "%m", e);
                }

            }
            return optionsChain;
        }

        @TruffleBoundary
        private static String getMessage(UnsupportedOptionsException e) {
            return e.getMessage();
        }

        @TruffleBoundary
        private FilterOptions getFilterOptions(long[] longFilter) throws UnsupportedOptionsException {
            int[] filter = new int[longFilter.length];
            for (int i = 0; i < longFilter.length; i++) {
                // we'll assume its fine for the java implementation to lose magnitude
                filter[i] = (int) longFilter[i];
            }
            switch (LZMAFilter.from(longFilter[0])) {
                case LZMA_FILTER_LZMA1:
                case LZMA_FILTER_LZMA2:
                    LZMA2Options lzma2Options;
                    try {
                        lzma2Options = new LZMA2Options(filter[1]);
                    } catch (UnsupportedOptionsException e) {
                        throw raise(LZMAError, INVALID_COMPRESSION_PRESET, filter[LZMAOption.preset.ordinal()]);
                    }
                    for (int j = 2; j < filter.length; j++) {
                        setLZMAOption(lzma2Options, j, filter[j]);
                    }
                    return lzma2Options;
                case LZMA_FILTER_DELTA:
                    DeltaOptions deltaOptions;
                    if (filter[DeltaOption.dist.ordinal()] != -1) {
                        deltaOptions = new DeltaOptions(filter[DeltaOption.dist.ordinal()]);
                    } else {
                        deltaOptions = new DeltaOptions();
                    }
                    return deltaOptions;
                case LZMA_FILTER_X86:
                    X86Options x86Options = new X86Options();
                    if (filter[BCJOption.start_offset.ordinal()] != -1) {
                        x86Options.setStartOffset(filter[BCJOption.start_offset.ordinal()]);
                    }
                    return x86Options;
                case LZMA_FILTER_POWERPC:
                    PowerPCOptions powerPCOptions = new PowerPCOptions();
                    if (filter[BCJOption.start_offset.ordinal()] != -1) {
                        powerPCOptions.setStartOffset(filter[BCJOption.start_offset.ordinal()]);
                    }
                    return powerPCOptions;
                case LZMA_FILTER_IA64:
                    IA64Options ia64Options = new IA64Options();
                    if (filter[BCJOption.start_offset.ordinal()] != -1) {
                        ia64Options.setStartOffset(filter[BCJOption.start_offset.ordinal()]);
                    }
                    return ia64Options;
                case LZMA_FILTER_ARM:
                    ARMOptions armOptions = new ARMOptions();
                    if (filter[BCJOption.start_offset.ordinal()] != -1) {
                        armOptions.setStartOffset(filter[BCJOption.start_offset.ordinal()]);
                    }
                    return armOptions;
                case LZMA_FILTER_ARMTHUMB:
                    ARMThumbOptions armThumbOptions = new ARMThumbOptions();
                    if (filter[BCJOption.start_offset.ordinal()] != -1) {
                        armThumbOptions.setStartOffset(filter[BCJOption.start_offset.ordinal()]);
                    }
                    return armThumbOptions;
                case LZMA_FILTER_SPARC:
                    SPARCOptions sparcOptions = new SPARCOptions();
                    if (filter[BCJOption.start_offset.ordinal()] != -1) {
                        sparcOptions.setStartOffset(filter[BCJOption.start_offset.ordinal()]);
                    }
                    return sparcOptions;
            }
            throw raise(ValueError, INVALID_FILTER, longFilter[0]);
        }

        @TruffleBoundary
        private static void setLZMAOption(LZMA2Options lzma2Options, int idx, int value) throws UnsupportedOptionsException {
            if (value == -1) {
                return;
            }
            switch (LZMAOption.values()[idx]) {
                case dict_size:
                    lzma2Options.setDictSize(value);
                    break;
                case lc:
                    lzma2Options.setLc(value);
                    break;
                case lp:
                    lzma2Options.setLp(value);
                    break;
                case pb:
                    lzma2Options.setPb(value);
                    break;
                case mode:
                    lzma2Options.setMode(value);
                    break;
                case nice_len:
                    lzma2Options.setNiceLen(value);
                    break;
                case mf:
                    lzma2Options.setMatchFinder(value);
                    break;
                case depth:
                    lzma2Options.setDepthLimit(value);
                    break;
            }
        }
    }

    @ImportStatic(LZMAModuleBuiltins.class)
    public abstract static class LZMACompressInit extends PNodeWithRaise {

        public abstract void execute(VirtualFrame frame, LZMACompressor self, int format, long preset, Object filters);

        @Specialization(guards = "format == FORMAT_XZ")
        void xz(LZMACompressor.Native self, @SuppressWarnings("unused") int format, long preset, @SuppressWarnings("unused") PNone filters,
                        @Shared("ct") @CachedContext(PythonLanguage.class) PythonContext ctxt,
                        @Shared("cs") @Cached NativeLibrary.InvokeNativeFunction createStream,
                        @Cached NativeLibrary.InvokeNativeFunction lzmaEasyEncoder,
                        @Cached ConditionProfile errProfile) {
            NFILZMASupport lzmaSupport = ctxt.getNFILZMASupport();
            Object lzmast = lzmaSupport.createStream(createStream);
            self.init(lzmast, lzmaSupport);
            int lzret = lzmaSupport.lzmaEasyEncoder(lzmast, preset, self.getCheck(), lzmaEasyEncoder);
            if (errProfile.profile(lzret != LZMA_OK)) {
                errorHandling(lzret, getRaiseNode());
            }
        }

        @Specialization(guards = "format == FORMAT_XZ")
        void xz(VirtualFrame frame, LZMACompressor.Native self, @SuppressWarnings("unused") int format, @SuppressWarnings("unused") long preset, Object filters,
                        @Shared("ct") @CachedContext(PythonLanguage.class) PythonContext ctxt,
                        @Shared("cs") @Cached NativeLibrary.InvokeNativeFunction createStream,
                        @Cached NativeLibrary.InvokeNativeFunction lzmaStreamEncoder,
                        @Cached NativeFilterChain filterChain,
                        @Cached ConditionProfile errProfile) {
            NFILZMASupport lzmaSupport = ctxt.getNFILZMASupport();
            Object lzmast = lzmaSupport.createStream(createStream);
            self.init(lzmast, lzmaSupport);
            filterChain.execute(frame, lzmast, ctxt, filters);
            int lzret = lzmaSupport.lzmaStreamEncoder(lzmast, self.getCheck(), lzmaStreamEncoder);
            if (errProfile.profile(lzret != LZMA_OK)) {
                errorHandling(lzret, getRaiseNode());
            }
        }

        @SuppressWarnings("unused")
        @Specialization(guards = "format == FORMAT_ALONE")
        void alone(LZMACompressor.Native self, int format, long preset, PNone filters,
                        @Shared("ct") @CachedContext(PythonLanguage.class) PythonContext ctxt,
                        @Shared("cs") @Cached NativeLibrary.InvokeNativeFunction createStream,
                        @Cached NativeLibrary.InvokeNativeFunction lzmaAloneEncoderPreset,
                        @Cached ConditionProfile errProfile) {
            NFILZMASupport lzmaSupport = ctxt.getNFILZMASupport();
            Object lzmast = lzmaSupport.createStream(createStream);
            self.init(lzmast, lzmaSupport);
            int lzret = lzmaSupport.lzmaAloneEncoderPreset(lzmast, preset, lzmaAloneEncoderPreset);
            if (errProfile.profile(lzret != LZMA_OK)) {
                errorHandling(lzret, getRaiseNode());
            }
        }

        @SuppressWarnings("unused")
        @Specialization(guards = "format == FORMAT_ALONE")
        void alone(VirtualFrame frame, LZMACompressor.Native self, int format, long preset, Object filters,
                        @Shared("ct") @CachedContext(PythonLanguage.class) PythonContext ctxt,
                        @Shared("cs") @Cached NativeLibrary.InvokeNativeFunction createStream,
                        @Cached NativeLibrary.InvokeNativeFunction lzmaAloneEncoder,
                        @Cached NativeFilterChain filterChain,
                        @Cached ConditionProfile errProfile) {
            NFILZMASupport lzmaSupport = ctxt.getNFILZMASupport();
            Object lzmast = lzmaSupport.createStream(createStream);
            self.init(lzmast, lzmaSupport);
            filterChain.execute(frame, lzmast, ctxt, filters);
            int lzret = lzmaSupport.lzmaAloneEncoder(lzmast, lzmaAloneEncoder);
            if (errProfile.profile(lzret != LZMA_OK)) {
                errorHandling(lzret, getRaiseNode());
            }
        }

        @SuppressWarnings("unused")
        @Specialization(guards = "format == FORMAT_RAW")
        void raw(VirtualFrame frame, LZMACompressor.Native self, int format, long preset, Object filters,
                        @Shared("ct") @CachedContext(PythonLanguage.class) PythonContext ctxt,
                        @Shared("cs") @Cached NativeLibrary.InvokeNativeFunction createStream,
                        @Cached NativeLibrary.InvokeNativeFunction lzmaRawEncoder,
                        @Cached NativeFilterChain filterChain,
                        @Cached ConditionProfile errProfile) {
            NFILZMASupport lzmaSupport = ctxt.getNFILZMASupport();
            Object lzmast = lzmaSupport.createStream(createStream);
            self.init(lzmast, lzmaSupport);
            filterChain.execute(frame, lzmast, ctxt, filters);
            int lzret = lzmaSupport.lzmaRawEncoder(lzmast, lzmaRawEncoder);
            if (errProfile.profile(lzret != LZMA_OK)) {
                errorHandling(lzret, getRaiseNode());
            }
        }

        @Specialization(guards = "format == FORMAT_XZ")
        void xz(LZMACompressor.Java self, @SuppressWarnings("unused") int format, long preset, @SuppressWarnings("unused") PNone filters) {
            try {
                self.lzmaEasyEncoder(parseLZMAOptions(preset));
            } catch (IOException e) {
                throw raise(PythonErrorType.ValueError, "%m", e);
            }
        }

        @Specialization(guards = "format == FORMAT_XZ")
        void xz(VirtualFrame frame, LZMACompressor.Java self, @SuppressWarnings("unused") int format, @SuppressWarnings("unused") long preset, Object filters,
                        @Cached JavaFilterChain filterChain) {
            try {
                self.lzmaStreamEncoder(filterChain.execute(frame, filters));
            } catch (IOException e) {
                throw raise(ValueError, "%m", e);
            }
        }

        @SuppressWarnings("unused")
        @Specialization(guards = "format == FORMAT_ALONE")
        void alone(LZMACompressor.Java self, int format, long preset, PNone filters) {
            try {
                self.lzmaAloneEncoder(parseLZMAOptions(preset));
            } catch (IOException e) {
                throw raise(ValueError, "%m", e);
            }
        }

        @SuppressWarnings("unused")
        @Specialization(guards = "format == FORMAT_ALONE")
        void alone(VirtualFrame frame, LZMACompressor.Java self, int format, long preset, Object filters,
                        @Cached JavaFilterChain filterChain) {
            FilterOptions[] optionsChain = filterChain.execute(frame, filters);
            if (optionsChain.length != 1 && !(optionsChain[0] instanceof LZMA2Options)) {
                throw raise(ValueError, INVALID_FILTER_CHAIN_FOR_FORMAT);
            }
            try {
                self.lzmaAloneEncoder((LZMA2Options) optionsChain[0]);
            } catch (IOException e) {
                throw raise(ValueError, "%m", e);
            }
        }

        @SuppressWarnings("unused")
        @Specialization(guards = "format == FORMAT_RAW")
        void raw(VirtualFrame frame, LZMACompressor.Java self, int format, long preset, Object filters,
                        @Cached JavaFilterChain filterChain) {
            FilterOptions[] optionsChain = filterChain.execute(frame, filters);
            if (optionsChain.length != 1 && !(optionsChain[0] instanceof LZMA2Options)) {
                throw raise(ValueError, INVALID_FILTER_CHAIN_FOR_FORMAT);
            }
            try {
                self.lzmaRawEncoder(optionsChain);
            } catch (IOException e) {
                throw raise(ValueError, "%m", e);
            }
        }

        @Fallback
        @SuppressWarnings("unused")
        void error(VirtualFrame frame, LZMACompressor self, int format, long preset, Object filters) {
            throw raise(ValueError, INVALID_CONTAINER_FORMAT, format);
        }
    }

    @ImportStatic(LZMANodes.class)
    public abstract static class CompressNode extends PNodeWithRaise {

        public abstract byte[] execute(LZMACompressor self, PythonContext context, byte[] bytes, int len, int action);

        public byte[] compress(LZMACompressor self, PythonContext context, byte[] bytes, int len) {
            return execute(self, context, bytes, len, LZMA_RUN);
        }

        public byte[] flush(LZMACompressor self, PythonContext context) {
            return execute(self, context, PythonUtils.EMPTY_BYTE_ARRAY, 0, LZMA_FINISH);
        }

        @Specialization
        byte[] nativeCompress(LZMACompressor.Native self, PythonContext context, byte[] bytes, int len, int action,
                        @Cached NativeLibrary.InvokeNativeFunction compress,
                        @Cached GetOutputNativeBufferNode getBuffer,
                        @Cached ConditionProfile errProfile) {
            NFILZMASupport lzmaSupport = context.getNFILZMASupport();
            Object inGuest = context.getEnv().asGuestValue(bytes);
            int err = lzmaSupport.compress(self.getLzs(), inGuest, len, action, INITIAL_BUFFER_SIZE, compress);
            if (errProfile.profile(err != LZMA_OK)) {
                errorHandling(err, getRaiseNode());
            }
            return getBuffer.execute(self.getLzs(), context);
        }

        @SuppressWarnings("unused")
        @Specialization(guards = "action == LZMA_RUN")
        byte[] javaCompress(LZMACompressor.Java self, PythonContext context, byte[] bytes, int len, int action) {
            try {
                self.write(bytes);
                byte[] result = self.getByteArray();
                self.resetBuffer();
                return result;
            } catch (IOException e) {
                throw raise(LZMAError, "%m", e);
            }
        }

        @SuppressWarnings("unused")
        @Specialization(guards = "action == LZMA_FINISH")
        byte[] javaFlush(LZMACompressor.Java self, PythonContext context, byte[] bytes, int len, int action) {
            try {
                self.finish();
                return self.getByteArray();
            } catch (IOException e) {
                throw raise(LZMAError, "%m", e);
            }
        }
    }

    @ImportStatic(LZMAModuleBuiltins.class)
    public abstract static class LZMADecompressInit extends PNodeWithRaise {

        public abstract void execute(VirtualFrame frame, LZMADecompressor self, int format, Object memlimit);

        @SuppressWarnings("unused")
        @Specialization
        void init(LZMADecompressor.Java self, int format, int memlimit) {
        }

        @Specialization(guards = "format == FORMAT_AUTO")
        void auto(LZMADecompressor.Native self, @SuppressWarnings("unused") int format, int memlimit,
                        @Shared("ct") @CachedContext(PythonLanguage.class) PythonContext context,
                        @Shared("cs") @Cached NativeLibrary.InvokeNativeFunction createStream,
                        @Cached NativeLibrary.InvokeNativeFunction lzmaAutoDecoder,
                        @Cached ConditionProfile errProfile) {
            NFILZMASupport lzmaSupport = context.getNFILZMASupport();
            Object lzmast = lzmaSupport.createStream(createStream);
            self.init(lzmast, lzmaSupport);
            int decoderFlags = LZMA_TELL_ANY_CHECK | LZMA_TELL_NO_CHECK;
            int lzret = lzmaSupport.lzmaAutoDecoder(lzmast, memlimit, decoderFlags, lzmaAutoDecoder);
            if (errProfile.profile(lzret != LZMA_OK)) {
                errorHandling(lzret, getRaiseNode());
            }
        }

        @Specialization(guards = "format == FORMAT_XZ")
        void xz(LZMADecompressor.Native self, @SuppressWarnings("unused") int format, int memlimit,
                        @Shared("ct") @CachedContext(PythonLanguage.class) PythonContext context,
                        @Shared("cs") @Cached NativeLibrary.InvokeNativeFunction createStream,
                        @Cached NativeLibrary.InvokeNativeFunction lzmaStreamDecoder,
                        @Cached ConditionProfile errProfile) {
            NFILZMASupport lzmaSupport = context.getNFILZMASupport();
            Object lzmast = lzmaSupport.createStream(createStream);
            self.init(lzmast, lzmaSupport);
            int decoderFlags = LZMA_TELL_ANY_CHECK | LZMA_TELL_NO_CHECK;
            int lzret = lzmaSupport.lzmaStreamDecoder(lzmast, memlimit, decoderFlags, lzmaStreamDecoder);
            if (errProfile.profile(lzret != LZMA_OK)) {
                errorHandling(lzret, getRaiseNode());
            }
        }

        @Specialization(guards = "format == FORMAT_ALONE")
        void alone(LZMADecompressor.Native self, @SuppressWarnings("unused") int format, int memlimit,
                        @Shared("ct") @CachedContext(PythonLanguage.class) PythonContext context,
                        @Shared("cs") @Cached NativeLibrary.InvokeNativeFunction createStream,
                        @Cached NativeLibrary.InvokeNativeFunction lzmaAloneDecoder,
                        @Cached ConditionProfile errProfile) {
            NFILZMASupport lzmaSupport = context.getNFILZMASupport();
            Object lzmast = lzmaSupport.createStream(createStream);
            self.init(lzmast, lzmaSupport);
            int lzret = lzmaSupport.lzmaAloneDecoder(lzmast, memlimit, lzmaAloneDecoder);
            if (errProfile.profile(lzret != LZMA_OK)) {
                errorHandling(lzret, getRaiseNode());
            }
        }
    }

    @ImportStatic(LZMAModuleBuiltins.class)
    public abstract static class LZMARawDecompressInit extends PNodeWithRaise {

        public abstract void execute(VirtualFrame frame, LZMADecompressor self, Object filters);

        @SuppressWarnings("unused")
        @Specialization
        void rawJava(VirtualFrame frame, LZMADecompressor.Java self, Object filters) {
            throw raise(SystemError, LZMA_JAVA_ERROR);
        }

        @Specialization
        void rawNative(VirtualFrame frame, LZMADecompressor.Native self, Object filters,
                        @CachedContext(PythonLanguage.class) PythonContext context,
                        @Cached NativeLibrary.InvokeNativeFunction createStream,
                        @Cached NativeLibrary.InvokeNativeFunction lzmaRawDecoder,
                        @Cached NativeFilterChain filterChain,
                        @Cached ConditionProfile errProfile) {
            NFILZMASupport lzmaSupport = context.getNFILZMASupport();
            Object lzmast = lzmaSupport.createStream(createStream);
            self.init(lzmast, lzmaSupport);
            filterChain.execute(frame, lzmast, context, filters);
            int lzret = lzmaSupport.lzmaRawDecoder(lzmast, lzmaRawDecoder);
            if (errProfile.profile(lzret != LZMA_OK)) {
                errorHandling(lzret, getRaiseNode());
            }
        }
    }

    public abstract static class DecompressNode extends Node {

        public abstract byte[] execute(LZMADecompressor self, byte[] data, int len, int maxLength);

        @Specialization
        byte[] nativeDecompress(LZMADecompressor self, byte[] bytes, int len, int maxLength,
                        @Cached InternalDecompressNode decompress) {
            boolean inputBufferInUse;
            /* Prepend unconsumed input if necessary */
            if (self.getNextIn() != null) {
                /* Number of bytes we can append to input buffer */
                int availNow = self.getInputBufferSize() - (self.getNextInIndex() + self.getLzsAvailIn());

                /*
                 * Number of bytes we can append if we move existing contents to beginning of buffer
                 * (overwriting consumed input)
                 */
                int availTotal = self.getInputBufferSize() - self.getLzsAvailIn();

                if (availTotal < len) {
                    int newSize = self.getInputBufferSize() + len - availNow;

                    /*
                     * Assign to temporary variable first, so we don't lose address of allocated
                     * buffer if realloc fails
                     */
                    self.resizeInputBuffer(newSize);
                    self.setNextIn(self.getInputBuffer());
                } else if (availNow < len) {
                    PythonUtils.arraycopy(self.getNextIn(), self.getNextInIndex(), self.getInputBuffer(), 0, self.getLzsAvailIn());
                    self.setNextIn(self.getInputBuffer());
                    self.setNextInIndex(0);
                }
                PythonUtils.arraycopy(bytes, 0, self.getNextIn(), self.getNextInIndex() + self.getLzsAvailIn(), len);
                // memcpy((void*)(lzs->next_in + self.getLzsAvailIn()), data, len);
                self.incLzsAvailIn(len);
                inputBufferInUse = true;
            } else {
                self.setNextIn(bytes);
                self.setLzsAvailIn(len);
                inputBufferInUse = false;
            }

            byte[] result = decompress.execute(self, maxLength);

            if (self.isEOF()) {
                self.setNeedsInput(false);
                if (self.getLzsAvailIn() > 0) {
                    self.setUnusedData();
                }
            } else if (self.getLzsAvailIn() == 0) {
                self.clearNextIn();
                self.setNextInIndex(0);
                /*
                 * (avail_in==0 && avail_out==0) Maybe lzs's internal state still have a few bytes
                 * can be output, try to output them next time.
                 */
                self.setNeedsInput(self.getLzsAvailOut() != 0);
            } else {
                self.setNeedsInput(false);

                /*
                 * If we did not use the input buffer, we now have to copy the tail from the
                 * caller's buffer into the input buffer
                 */
                if (!inputBufferInUse) {

                    /*
                     * Discard buffer if it's too small (resizing it may needlessly copy the current
                     * contents)
                     */
                    if (self.getInputBuffer() != null && self.getInputBufferSize() < self.getLzsAvailIn()) {
                        self.discardInputBuffer();
                    }

                    /* Allocate if necessary */
                    if (self.getInputBuffer() == null) {
                        self.createInputBuffer(self.getLzsAvailIn());
                    }

                    /* Copy tail */
                    // memcpy(d->input_buffer, lzs->next_in, self.getLzsAvailIn());
                    PythonUtils.arraycopy(self.getNextIn(), self.getNextInIndex(), self.getInputBuffer(), 0, self.getLzsAvailIn());
                    self.setNextIn(self.getInputBuffer());
                    self.setNextInIndex(0);
                }
            }

            return result;
        }
    }

    public abstract static class InternalDecompressNode extends PNodeWithRaise {

        public abstract byte[] execute(LZMADecompressor self, int maxLength);

        @Specialization
        byte[] nativeInternalDecompress(LZMADecompressor.Native self, int maxLength,
                        @CachedContext(PythonLanguage.class) PythonContext context,
                        @Cached NativeLibrary.InvokeNativeFunction decompress,
                        @Cached NativeLibrary.InvokeNativeFunction getLzsAvailIn,
                        @Cached NativeLibrary.InvokeNativeFunction getLzsAvailOut,
                        @Cached NativeLibrary.InvokeNativeFunction getNextInIndex,
                        @Cached NativeLibrary.InvokeNativeFunction getLzsCheck,
                        @Cached GetOutputNativeBufferNode getBuffer,
                        @Cached ConditionProfile errProfile,
                        @Cached BranchProfile ofProfile) {
            NFILZMASupport lzmaSupport = context.getNFILZMASupport();
            Object inGuest = context.getEnv().asGuestValue(self.getNextIn());
            int offset = self.getNextInIndex();
            int err = lzmaSupport.decompress(self.getLzs(), inGuest, offset, maxLength, INITIAL_BUFFER_SIZE, self.getLzsAvailIn(), decompress);
            long nextInIdx = lzmaSupport.getNextInIndex(self.getLzs(), getNextInIndex);
            long lzsAvailIn = lzmaSupport.getLzsAvailIn(self.getLzs(), getLzsAvailIn);
            long lzsAvailOut = lzmaSupport.getLzsAvailOut(self.getLzs(), getLzsAvailOut);
            self.setCheck(lzmaSupport.getLzsCheck(self.getLzs(), getLzsCheck));
            try {
                self.setNextInIndex(nextInIdx);
                self.setLzsAvailIn(lzsAvailIn);
                self.setLzsAvailOut(lzsAvailOut);
            } catch (OverflowException of) {
                ofProfile.enter();
                throw raise(SystemError, VALUE_TOO_LARGE_TO_FIT_INTO_INDEX);
            }
            if (err == LZMA_STREAM_END) {
                self.setEOF();
            } else if (errProfile.profile(err != LZMA_OK)) {
                errorHandling(err, getRaiseNode());
            }
            return getBuffer.execute(self.getLzs(), context);
        }

        @TruffleBoundary
        @Specialization
        byte[] javaInternalDecompress(LZMADecompressor.Java self, int maxLength) {
            if (maxLength == 0) {
                return PythonUtils.EMPTY_BYTE_ARRAY;
            }
            self.setInput();
            int maxLen = maxLength == -1 ? Integer.MAX_VALUE : maxLength;
            byte[] result = new byte[Math.min(maxLen, INITIAL_BUFFER_SIZE)];
            ByteArrayOutputStream baos = createOutputStream(result.length);
            int dataSize = -1;
            boolean isInitialized = self.isInitialized();
            try {
                while (true) {
                    try {
                        if (!isInitialized) {
                            self.initialize();
                        }
                        doDecompression(self, baos, result, maxLen);
                    } catch (IOException ioe) {
                        isInitialized = true;
                        if (self.isFormatAuto()) {
                            if (dataSize != baos.size()) {
                                dataSize = baos.size();
                                self.switchStream();
                                continue;
                            }
                        } else {
                            throw ioe;
                        }
                    }
                    break;
                }
            } catch (UnsupportedOptionsException o) {
                errorHandling(LZMA_OPTIONS_ERROR, getRaiseNode());
            } catch (CorruptedInputException c) {
                if (self.isFormatAuto() && baos.size() > 0) {
                    self.setEOF();
                } else {
                    errorHandling(LZMA_DATA_ERROR, getRaiseNode());
                }
            } catch (MemoryLimitException m) {
                errorHandling(LZMA_MEMLIMIT_ERROR, getRaiseNode());
            } catch (XZFormatException f) {
                errorHandling(LZMA_FORMAT_ERROR, getRaiseNode());
            } catch (EOFException eof) {
                self.setEOF();
            } catch (IOException e) {
                throw raise(OSError, e);
            }
            byte[] ret = toByteArray(baos);
            self.decompressedData(ret.length);
            self.update(maxLen - ret.length);
            return ret;
        }

        @TruffleBoundary
        private static void doDecompression(LZMADecompressor.Java self, ByteArrayOutputStream baos, byte[] result, int maxLen) throws IOException {
            while (baos.size() < maxLen) {
                int read;
                try {
                    read = self.read(result);
                } catch (EOFException eof) {
                    if (self.sameData()) {
                        self.setEOF();
                    }
                    break;
                }
                if (read == -1) {
                    self.setEOF();
                    break;
                }
                append(baos, result, 0, read);
            }
        }

    }

    public abstract static class GetOutputNativeBufferNode extends PNodeWithRaise {

        public abstract byte[] execute(Object lzmast, PythonContext context);

        @Specialization
        byte[] getBuffer(Object lzmast, PythonContext context,
                        @Cached NativeLibrary.InvokeNativeFunction getBufferSize,
                        @Cached NativeLibrary.InvokeNativeFunction getBuffer,
                        @Cached BranchProfile ofProfile) {
            NFILZMASupport lzmaSupport = context.getNFILZMASupport();
            int size;
            try {
                size = PInt.intValueExact(lzmaSupport.getOutputBufferSize(lzmast, getBufferSize));
            } catch (OverflowException of) {
                ofProfile.enter();
                throw raise(SystemError, VALUE_TOO_LARGE_TO_FIT_INTO_INDEX);
            }
            if (size == 0) {
                return PythonUtils.EMPTY_BYTE_ARRAY;
            }
            byte[] resultArray = new byte[size];
            Object out = context.getEnv().asGuestValue(resultArray);
            /* this will clear the native output once retrieved */
            lzmaSupport.getOutputBuffer(lzmast, out, getBuffer);
            return resultArray;
        }
    }

    public abstract static class IsCheckSupported extends PNodeWithRaise {

        public abstract boolean execute(int checkId);

        protected boolean useNative(PythonContext ctxt) {
            return ctxt.getNFILZMASupport().isAvailable();
        }

        @Specialization(guards = "useNative(ctxt)")
        boolean checkNative(int checkId,
                        @Cached NativeLibrary.InvokeNativeFunction checkIsSupported,
                        @Shared("c") @CachedContext(PythonLanguage.class) PythonContext ctxt) {
            return ctxt.getNFILZMASupport().checkIsSupported(checkId, checkIsSupported) == 1;
        }

        @TruffleBoundary
        @Specialization(guards = "!useNative(ctxt)")
        boolean checkJava(int checkId,
                        @SuppressWarnings("unused") @Shared("c") @CachedContext(PythonLanguage.class) PythonContext ctxt) {
            try {
                Check.getInstance(checkId);
                return true;
            } catch (UnsupportedOptionsException e) {
                return false;
            }
        }
    }

    public abstract static class EncodeFilterProperties extends PNodeWithRaise {

        public abstract byte[] execute(VirtualFrame frame, Object filter);

        protected boolean useNative(PythonContext ctxt) {
            return ctxt.getNFILZMASupport().isAvailable();
        }

        @Specialization(guards = "useNative(ctxt)")
        byte[] encodeNative(VirtualFrame frame, Object filter,
                        @Shared("c") @CachedContext(PythonLanguage.class) PythonContext ctxt,
                        @Cached LZMAFilterConverter filterConverter,
                        @Cached GetOutputNativeBufferNode getBuffer,
                        @Cached NativeLibrary.InvokeNativeFunction createStream,
                        @Cached NativeLibrary.InvokeNativeFunction encodeFilter,
                        @Cached NativeLibrary.InvokeNativeFunction deallocateStream,
                        @Cached ConditionProfile errProfile) {
            NFILZMASupport lzmaSupport = ctxt.getNFILZMASupport();
            Object lzmast = lzmaSupport.createStream(createStream);
            long[] opts = filterConverter.execute(frame, filter);
            int lzret = lzmaSupport.encodeFilter(lzmast, ctxt.getEnv().asGuestValue(opts), encodeFilter);
            if (errProfile.profile(lzret != LZMA_OK)) {
                lzmaSupport.deallocateStream(lzmast, deallocateStream);
                if (lzret == LZMA_PRESET_ERROR) {
                    throw raise(LZMAError, INVALID_COMPRESSION_PRESET, opts[LZMAOption.preset.ordinal()]);
                }
                errorHandling(lzret, getRaiseNode());
            }
            byte[] encoded = getBuffer.execute(lzmast, ctxt);
            lzmaSupport.deallocateStream(lzmast, deallocateStream);
            return encoded;
        }

        @SuppressWarnings("unused")
        @Specialization(guards = "!useNative(ctxt)")
        byte[] encodeJava(Object filter,
                        @SuppressWarnings("unused") @Shared("c") @CachedContext(PythonLanguage.class) PythonContext ctxt) {
            throw raise(SystemError, LZMA_JAVA_ERROR);
        }
    }

    public abstract static class DecodeFilterProperties extends PNodeWithRaise {

        public abstract void execute(VirtualFrame frame, long id, byte[] encoded, PDict dict);

        protected boolean useNative(PythonContext ctxt) {
            return ctxt.getNFILZMASupport().isAvailable();
        }

        @Specialization(guards = "useNative(ctxt)")
        void decodeNative(VirtualFrame frame, long id, byte[] encoded, PDict dict,
                        @Shared("c") @CachedContext(PythonLanguage.class) PythonContext ctxt,
                        @Cached NativeLibrary.InvokeNativeFunction decodeFilter,
                        @CachedLibrary(limit = "1") HashingStorageLibrary lib,
                        @Cached ConditionProfile hasFrameProfile,
                        @Cached ConditionProfile errProfile) {
            NFILZMASupport lzmaSupport = ctxt.getNFILZMASupport();
            long[] opts = new long[MAX_OPTS_INDEX];
            int len = encoded.length;
            Object encodedProps = ctxt.getEnv().asGuestValue(encoded);
            Object filter = ctxt.getEnv().asGuestValue(opts);
            int lzret = lzmaSupport.decodeFilter(id, encodedProps, len, filter, decodeFilter);
            if (errProfile.profile(lzret != LZMA_OK)) {
                if (lzret == LZMA_ID_ERROR) {
                    throw raise(LZMAError, INVALID_FILTER, opts[LZMAOption.id.ordinal()]);
                }
                errorHandling(lzret, getRaiseNode());
            }
            buildFilterSpec(frame, hasFrameProfile, opts, dict, lib);
        }

        @SuppressWarnings("unused")
        @Specialization(guards = "!useNative(ctxt)")
        void decodeJava(long id, byte[] encoded, PDict dict,
                        @Shared("c") @CachedContext(PythonLanguage.class) PythonContext ctxt) {
            throw raise(SystemError, LZMA_JAVA_ERROR);
        }

        void buildFilterSpec(VirtualFrame frame, ConditionProfile hasFrameProfile, long[] opts, PDict dict, HashingStorageLibrary lib) {
            long id = opts[LZMAOption.id.ordinal()];
            addField(frame, lib, hasFrameProfile, dict, LZMAOption.id.OptName(), id);
            switch (LZMAFilter.from(id)) {
                case LZMA_FILTER_LZMA1:
                    addField(frame, lib, hasFrameProfile, dict, LZMAOption.lc.OptName(), opts[LZMAOption.lc.ordinal()]);
                    addField(frame, lib, hasFrameProfile, dict, LZMAOption.lp.OptName(), opts[LZMAOption.lp.ordinal()]);
                    addField(frame, lib, hasFrameProfile, dict, LZMAOption.pb.OptName(), opts[LZMAOption.pb.ordinal()]);
                    addField(frame, lib, hasFrameProfile, dict, LZMAOption.dict_size.OptName(), opts[LZMAOption.dict_size.ordinal()]);
                    break;
                case LZMA_FILTER_LZMA2:
                    addField(frame, lib, hasFrameProfile, dict, LZMAOption.dict_size.OptName(), opts[LZMAOption.dict_size.ordinal()]);
                    break;
                case LZMA_FILTER_DELTA:
                    addField(frame, lib, hasFrameProfile, dict, DeltaOption.dist.OptName(), opts[DeltaOption.dist.ordinal()]);
                    break;
                case LZMA_FILTER_X86:
                case LZMA_FILTER_POWERPC:
                case LZMA_FILTER_IA64:
                case LZMA_FILTER_ARM:
                case LZMA_FILTER_ARMTHUMB:
                case LZMA_FILTER_SPARC:
                    addField(frame, lib, hasFrameProfile, dict, BCJOption.start_offset.OptName(), opts[BCJOption.start_offset.ordinal()]);
                    break;
            }
        }

        void addField(VirtualFrame frame, HashingStorageLibrary lib, ConditionProfile hasFrameProfile, PDict dict, String key, long val) {
            dict.setDictStorage(lib.setItemWithFrame(dict.getDictStorage(), key, val, hasFrameProfile, frame));
        }
    }

    @TruffleBoundary
    protected static LZMA2Options parseLZMAOptions(long preset) {
        // the easy one; uses 'preset'
        LZMA2Options lzmaOptions = null;
        try {
            lzmaOptions = new LZMA2Options();
            lzmaOptions.setPreset((int) preset); // java port presets are ints
        } catch (UnsupportedOptionsException e) {
            try {
                lzmaOptions.setPreset(LZMA2Options.PRESET_MAX);
            } catch (UnsupportedOptionsException e1) {
                throw new IllegalStateException("should not be reached");
            }
        }
        return lzmaOptions;
    }

    protected static class LZMAByteInputStream extends ByteArrayInputStream {

        public LZMAByteInputStream(byte[] buf, int offset, int length) {
            super(buf, offset, length);
        }

        public void setBuffer(byte[] bytes, int off) {
            this.buf = bytes;
            this.pos = off;
            this.mark = off;
            this.count = bytes.length;
        }

        public int getNextInIndex() {
            return pos;
        }

        public int getAvailIn() {
            return available();
        }
    }

    protected static int errorHandling(int lzret, PRaiseNode raise) {
        switch (lzret) {
            case LZMA_OK:
            case LZMA_GET_CHECK:
            case LZMA_NO_CHECK:
            case LZMA_STREAM_END:
                return 0;
            case LZMA_UNSUPPORTED_CHECK:
                throw raise.raise(LZMAError, "Unsupported integrity check");
            case LZMA_MEM_ERROR:
                throw raise.raise(MemoryError);
            case LZMA_MEMLIMIT_ERROR:
                throw raise.raise(LZMAError, "Memory usage limit exceeded");
            case LZMA_FORMAT_ERROR:
                throw raise.raise(LZMAError, "Input format not supported by decoder");
            case LZMA_OPTIONS_ERROR:
                throw raise.raise(LZMAError, "Invalid or unsupported options");
            case LZMA_DATA_ERROR:
                throw raise.raise(LZMAError, "Corrupt input data");
            case LZMA_BUF_ERROR:
                throw raise.raise(LZMAError, "Insufficient buffer space");
            case LZMA_PROG_ERROR:
                throw raise.raise(LZMAError, "Internal error");
            default:
                throw raise.raise(LZMAError, "Unrecognized error from liblzma: %d", lzret);
        }
    }
}
