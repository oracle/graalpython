/*
 * Copyright (c) 2018, 2023, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.graal.python.runtime.exception.PythonErrorType.TypeError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.ValueError;
import static com.oracle.graal.python.util.PythonUtils.TS_ENCODING;
import static com.oracle.graal.python.util.PythonUtils.tsLiteral;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;

import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.Python3Core;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.buffer.PythonBufferAccessLibrary;
import com.oracle.graal.python.builtins.objects.buffer.PythonBufferAcquireLibrary;
import com.oracle.graal.python.builtins.objects.exception.PBaseException;
import com.oracle.graal.python.builtins.objects.module.PythonModule;
import com.oracle.graal.python.builtins.objects.slice.SliceNodes;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.lib.PyLongAsIntNode;
import com.oracle.graal.python.lib.PyNumberAsSizeNode;
import com.oracle.graal.python.lib.PyNumberIndexNode;
import com.oracle.graal.python.lib.PyObjectGetItem;
import com.oracle.graal.python.lib.PyObjectLookupAttr;
import com.oracle.graal.python.lib.PyObjectSizeNode;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.attributes.GetAttributeNode;
import com.oracle.graal.python.nodes.call.CallNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonSenaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonTernaryBuiltinNode;
import com.oracle.graal.python.nodes.truffle.PythonArithmeticTypes;
import com.oracle.graal.python.nodes.util.BufferToTruffleStringNode;
import com.oracle.graal.python.nodes.util.CannotCastException;
import com.oracle.graal.python.nodes.util.CastToTruffleStringNode;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.PythonOptions;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NeverDefault;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.TypeSystemReference;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.ExceptionType;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.api.profiles.ConditionProfile;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.api.strings.TruffleString;
import org.graalvm.collections.EconomicMap;

@CoreFunctions(defineModule = "_sre")
public class SREModuleBuiltins extends PythonBuiltins {

    private static final TruffleString T__SRE = tsLiteral("_sre");

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return SREModuleBuiltinsFactory.getFactories();
    }

    @Override
    public void initialize(Python3Core core) {
        addBuiltinConstant("_with_tregex", core.getContext().getLanguage().getEngineOption(PythonOptions.WithTRegex));
        addBuiltinConstant("_with_sre", core.getContext().getLanguage().getEngineOption(PythonOptions.TRegexUsesSREFallback));
        addBuiltinConstant("_METHOD_SEARCH", PythonMethod.search.ordinal());
        addBuiltinConstant("_METHOD_MATCH", PythonMethod.match.ordinal());
        addBuiltinConstant("_METHOD_FULLMATCH", PythonMethod.fullmatch.ordinal());
        super.initialize(core);
    }

    public enum PythonMethod {
        search(tsLiteral("search")),
        match(tsLiteral("match")),
        fullmatch(tsLiteral("fullmatch"));

        private final TruffleString name;

        private static final PythonMethod[] VALUES = PythonMethod.values();

        PythonMethod(TruffleString name) {
            this.name = name;
        }

        public static PythonMethod fromOrdinal(int ordinal) {
            return VALUES[ordinal];
        }

        public TruffleString getMethodName() {
            return name;
        }

        public String getTRegexOption() {
            return "PythonMethod=" + name.toJavaStringUncached();
        }
    }

    public static final class TRegexCache {

        private final Object patternOrig;
        private final String pattern;
        private final String flags;
        private final boolean binary;
        private final boolean localeSensitive;

        private Object searchRegexp;
        private Object matchRegexp;
        private Object fullMatchRegexp;

        private Object mustAdvanceSearchRegexp;
        private Object mustAdvanceMatchRegexp;
        private Object mustAdvanceFullMatchRegexp;
        private final EconomicMap<RegexKey, Object> localeSensitiveRegexps;

        private static final String ENCODING_UTF_32 = "Encoding=UTF-32";
        private static final String ENCODING_LATIN_1 = "Encoding=LATIN-1";
        private static final TruffleString T_ERROR = tsLiteral("error");
        private static final TruffleString T_VALUE_ERROR_UNICODE_FLAG_BYTES_PATTERN = tsLiteral("cannot use UNICODE flag with a bytes pattern");
        private static final TruffleString T_VALUE_ERROR_LOCALE_FLAG_STR_PATTERN = tsLiteral("cannot use LOCALE flag with a str pattern");
        private static final TruffleString T_VALUE_ERROR_ASCII_UNICODE_INCOMPATIBLE = tsLiteral("ASCII and UNICODE flags are incompatible");
        private static final TruffleString T_VALUE_ERROR_ASCII_LOCALE_INCOMPATIBLE = tsLiteral("ASCII and LOCALE flags are incompatible");

        private static final int FLAG_IGNORECASE = 2;
        private static final int FLAG_LOCALE = 4;
        private static final int FLAG_MULTILINE = 8;
        private static final int FLAG_DOTALL = 16;
        private static final int FLAG_UNICODE = 32;
        private static final int FLAG_VERBOSE = 64;
        private static final int FLAG_ASCII = 256;

        @TruffleBoundary
        public TRegexCache(Object pattern, int flags) {
            String patternStr;
            boolean binary = true;
            try {
                patternStr = CastToTruffleStringNode.getUncached().execute(pattern).toJavaStringUncached();
                binary = false;
            } catch (CannotCastException ce) {
                Object buffer;
                try {
                    buffer = PythonBufferAcquireLibrary.getUncached().acquireReadonly(pattern);
                } catch (PException e) {
                    throw PRaiseNode.getUncached().raise(TypeError, ErrorMessages.EXPECTED_STR_OR_BYTESLIKE_OBJ);
                }
                PythonBufferAccessLibrary bufferLib = PythonBufferAccessLibrary.getUncached();
                try {
                    byte[] bytes = bufferLib.getInternalOrCopiedByteArray(buffer);
                    int bytesLen = bufferLib.getBufferLength(buffer);
                    patternStr = new String(bytes, 0, bytesLen, StandardCharsets.ISO_8859_1);
                } finally {
                    bufferLib.release(buffer);
                }
            }
            this.patternOrig = pattern;
            this.pattern = patternStr;
            this.binary = binary;
            this.flags = getTRegexFlags(flags);
            this.localeSensitive = calculateLocaleSensitive();
            this.localeSensitiveRegexps = this.localeSensitive ? EconomicMap.create() : null;
        }

        public boolean isBinary() {
            return binary;
        }

        public boolean isLocaleSensitive() {
            return localeSensitive;
        }

        private static String getTRegexFlags(int flags) {
            StringBuilder sb = new StringBuilder();
            if ((flags & FLAG_IGNORECASE) != 0) {
                sb.append('i');
            }
            if ((flags & FLAG_LOCALE) != 0) {
                sb.append('L');
            }
            if ((flags & FLAG_MULTILINE) != 0) {
                sb.append('m');
            }
            if ((flags & FLAG_DOTALL) != 0) {
                sb.append('s');
            }
            if ((flags & FLAG_UNICODE) != 0) {
                sb.append('u');
            }
            if ((flags & FLAG_VERBOSE) != 0) {
                sb.append('x');
            }
            if ((flags & FLAG_ASCII) != 0) {
                sb.append('a');
            }
            return sb.toString();
        }

        /**
         * Tests whether the regex is locale-sensitive. It is not completely precise. In some
         * instances, it will return {@code true} even though the regex is *not* locale-sensitive.
         * This is the case when sequences resembling inline flags appear in character classes or
         * comments.
         */
        private boolean calculateLocaleSensitive() {
            if (!isBinary()) {
                return false;
            }
            if (flags.indexOf('L') != -1) {
                return true;
            }
            int position = 0;
            while (position < pattern.length()) {
                position = pattern.indexOf("(?", position);
                if (position == -1) {
                    break;
                }
                int backslashPosition = position - 1;
                while (backslashPosition >= 0 && pattern.charAt(backslashPosition) == '\\') {
                    backslashPosition--;
                }
                // jump over '(?'
                position += 2;
                if ((position - backslashPosition) % 2 == 0) {
                    // found odd number of backslashes, the parentheses is a literal
                    continue;
                }
                while (position < pattern.length() && "aiLmsux".indexOf(pattern.charAt(position)) != -1) {
                    if (pattern.charAt(position) == 'L') {
                        return true;
                    }
                    position++;
                }
            }
            return false;
        }

        public Object getRegexp(PythonMethod method, boolean mustAdvance) {
            assert !isLocaleSensitive();
            switch (method) {
                case search:
                    if (mustAdvance) {
                        return mustAdvanceSearchRegexp;
                    } else {
                        return searchRegexp;
                    }
                case match:
                    if (mustAdvance) {
                        return mustAdvanceMatchRegexp;
                    } else {
                        return matchRegexp;
                    }
                case fullmatch:
                    if (mustAdvance) {
                        return mustAdvanceFullMatchRegexp;
                    } else {
                        return fullMatchRegexp;
                    }
                default:
                    throw CompilerDirectives.shouldNotReachHere();
            }
        }

        @TruffleBoundary
        public Object getLocaleSensitiveRegexp(PythonMethod method, boolean mustAdvance, TruffleString locale) {
            assert isLocaleSensitive();
            return localeSensitiveRegexps.get(new RegexKey(method, mustAdvance, locale));
        }

        private void setRegexp(PythonMethod method, boolean mustAdvance, Object regexp) {
            assert !isLocaleSensitive();
            switch (method) {
                case search:
                    if (mustAdvance) {
                        mustAdvanceSearchRegexp = regexp;
                    } else {
                        searchRegexp = regexp;
                    }
                    break;
                case match:
                    if (mustAdvance) {
                        mustAdvanceMatchRegexp = regexp;
                    } else {
                        matchRegexp = regexp;
                    }
                    break;
                case fullmatch:
                    if (mustAdvance) {
                        mustAdvanceFullMatchRegexp = regexp;
                    } else {
                        fullMatchRegexp = regexp;
                    }
                    break;
            }
        }

        @TruffleBoundary
        private void setLocaleSensitiveRegexp(PythonMethod method, boolean mustAdvance, TruffleString locale, Object regexp) {
            assert isLocaleSensitive();
            localeSensitiveRegexps.put(new RegexKey(method, mustAdvance, locale), regexp);
        }

        private String getTRegexOptions(String encoding, PythonMethod pythonMethod, boolean mustAdvance, TruffleString locale) {
            StringBuilder sb = new StringBuilder();
            sb.append("Flavor=Python");
            sb.append(',');
            sb.append(encoding);
            sb.append(',');
            sb.append(pythonMethod.getTRegexOption());
            if (mustAdvance) {
                sb.append(',');
                sb.append("MustAdvance=true");
            }
            if (locale != null) {
                sb.append(',');
                sb.append("PythonLocale=" + locale.toJavaStringUncached());
            }
            return sb.toString();
        }

        @TruffleBoundary
        public Object compile(PythonContext context, PythonMethod method, boolean mustAdvance, TruffleString locale) {
            String encoding = isBinary() ? ENCODING_LATIN_1 : ENCODING_UTF_32;
            String options = getTRegexOptions(encoding, method, mustAdvance, locale);
            InteropLibrary lib = InteropLibrary.getUncached();
            Object regexp;
            try {
                Source regexSource = Source.newBuilder("regex", options + '/' + pattern + '/' + flags, "re").mimeType("application/tregex").internal(true).build();
                Object compiledRegex = context.getEnv().parseInternal(regexSource).call();
                if (lib.isNull(compiledRegex)) {
                    regexp = PNone.NONE;
                } else {
                    regexp = compiledRegex;
                }
            } catch (RuntimeException e) {
                throw handleCompilationError(e, lib, context);
            }
            if (isLocaleSensitive()) {
                setLocaleSensitiveRegexp(method, mustAdvance, locale, regexp);
            } else {
                setRegexp(method, mustAdvance, regexp);
            }
            return regexp;
        }

        private RuntimeException handleCompilationError(RuntimeException e, InteropLibrary lib, PythonContext context) {
            try {
                if (lib.isException(e)) {
                    if (lib.getExceptionType(e) == ExceptionType.PARSE_ERROR) {
                        TruffleString reason = lib.asTruffleString(lib.getExceptionMessage(e)).switchEncodingUncached(TS_ENCODING);
                        if (reason.equalsUncached(T_VALUE_ERROR_UNICODE_FLAG_BYTES_PATTERN, TS_ENCODING) ||
                                        reason.equalsUncached(T_VALUE_ERROR_LOCALE_FLAG_STR_PATTERN, TS_ENCODING) ||
                                        reason.equalsUncached(T_VALUE_ERROR_ASCII_UNICODE_INCOMPATIBLE, TS_ENCODING) ||
                                        reason.equalsUncached(T_VALUE_ERROR_ASCII_LOCALE_INCOMPATIBLE, TS_ENCODING)) {
                            return PRaiseNode.getUncached().raise(ValueError, reason);
                        } else {
                            SourceSection sourceSection = lib.getSourceLocation(e);
                            int position = sourceSection.getCharIndex();
                            PythonModule module = context.lookupBuiltinModule(T__SRE);
                            Object errorConstructor = PyObjectLookupAttr.getUncached().execute(null, module, T_ERROR);
                            PBaseException exception = (PBaseException) CallNode.getUncached().execute(errorConstructor, reason, patternOrig, position);
                            return PRaiseNode.getUncached().raiseExceptionObject(exception);
                        }
                    }
                }
            } catch (UnsupportedMessageException e1) {
                return CompilerDirectives.shouldNotReachHere();
            }
            // just re-throw
            return e;
        }

        private static final class RegexKey {
            private final PythonMethod pythonMethod;
            private final boolean mustAdvance;
            private final TruffleString pythonLocale;

            RegexKey(PythonMethod pythonMethod, boolean mustAdvance, TruffleString pythonLocale) {
                this.pythonMethod = pythonMethod;
                this.mustAdvance = mustAdvance;
                this.pythonLocale = pythonLocale;
            }

            @Override
            public boolean equals(Object obj) {
                if (!(obj instanceof RegexKey)) {
                    return false;
                }
                RegexKey other = (RegexKey) obj;
                return this.pythonMethod == other.pythonMethod && this.mustAdvance == other.mustAdvance && this.pythonLocale.equalsUncached(other.pythonLocale, TS_ENCODING);
            }

            @Override
            public int hashCode() {
                return Objects.hash(pythonMethod, mustAdvance, pythonLocale);
            }
        }
    }

    @Builtin(name = "tregex_init_cache", minNumOfPositionalArgs = 2)
    @TypeSystemReference(PythonArithmeticTypes.class)
    @GenerateNodeFactory
    abstract static class TRegexInitCache extends PythonBinaryBuiltinNode {

        @Specialization
        Object call(VirtualFrame frame, Object pattern, Object flags,
                        @Cached PyLongAsIntNode flagsToIntNode) {
            int flagsStr = flagsToIntNode.execute(frame, flags);
            return new TRegexCache(pattern, flagsStr);
        }
    }

    @Builtin(name = "tregex_compile", minNumOfPositionalArgs = 3)
    @TypeSystemReference(PythonArithmeticTypes.class)
    @GenerateNodeFactory
    abstract static class TRegexCompile extends PythonTernaryBuiltinNode {

        private static final TruffleString T__GETLOCALE = tsLiteral("_getlocale");

        @Specialization(guards = {"method == cachedMethod", "mustAdvance == cachedMustAdvance", "!tRegexCache.isLocaleSensitive()"}, limit = "6")
        Object localeNonSensitive(TRegexCache tRegexCache, int method, boolean mustAdvance,
                        @Cached("method") int cachedMethod,
                        @Cached("mustAdvance") boolean cachedMustAdvance,
                        @Cached("fromOrdinal(method)") PythonMethod pythonMethod) {
            final Object tRegex = tRegexCache.getRegexp(pythonMethod, mustAdvance);
            if (tRegex != null) {
                return tRegex;
            } else {
                return tRegexCache.compile(getContext(), pythonMethod, mustAdvance, null);
            }
        }

        @Specialization(guards = {"method == cachedMethod", "mustAdvance == cachedMustAdvance", "tRegexCache.isLocaleSensitive()"}, limit = "6")
        Object localeSensitive(TRegexCache tRegexCache, int method, boolean mustAdvance,
                        @Cached("method") int cachedMethod,
                        @Cached("mustAdvance") boolean cachedMustAdvance,
                        @Cached("fromOrdinal(method)") PythonMethod pythonMethod,
                        @Cached("lookupGetLocaleFunction()") Object getLocale,
                        @Cached CallNode callGetLocale,
                        @Cached CastToTruffleStringNode castToTruffleStringNode) {
            TruffleString locale = castToTruffleStringNode.execute(callGetLocale.execute(getLocale));
            final Object tRegex = tRegexCache.getLocaleSensitiveRegexp(pythonMethod, mustAdvance, locale);
            if (tRegex != null) {
                return tRegex;
            } else {
                return tRegexCache.compile(getContext(), pythonMethod, mustAdvance, locale);
            }
        }

        @TruffleBoundary
        @NeverDefault
        protected Object lookupGetLocaleFunction() {
            PythonModule module = getContext().lookupBuiltinModule(T__SRE);
            return PyObjectLookupAttr.getUncached().execute(null, module, T__GETLOCALE);
        }

        protected static PythonMethod fromOrdinal(int ordinal) {
            return PythonMethod.fromOrdinal(ordinal);
        }
    }

    @Builtin(name = "tregex_search", minNumOfPositionalArgs = 6)
    @TypeSystemReference(PythonArithmeticTypes.class)
    @GenerateNodeFactory
    abstract static class TRegexSearch extends PythonSenaryBuiltinNode {

        private static final PTuple SUPPORTED_BINARY_INPUT_TYPES = PythonObjectFactory.getUncached().createTuple(new Object[]{PythonBuiltinClassType.PBytes, PythonBuiltinClassType.PByteArray,
                        PythonBuiltinClassType.PMMap, PythonBuiltinClassType.PMemoryView, PythonBuiltinClassType.PArray});
        private static final TruffleString T_UNSUPPORTED_INPUT_TYPE = tsLiteral("expected string or bytes-like object");
        private static final TruffleString T_UNEXPECTED_BYTES = tsLiteral("cannot use a string pattern on a bytes-like object");
        private static final TruffleString T_UNEXPECTED_STR = tsLiteral("cannot use a bytes pattern on a string-like object");

        private static final TruffleString T_MATCH_CONSTRUCTOR = tsLiteral("Match");
        private static final TruffleString T__PATTERN__TREGEX_CACHE = tsLiteral("_Pattern__tregex_cache");
        private static final TruffleString T__PATTERN__INDEXGROUP = tsLiteral("_Pattern__indexgroup");
        protected static final TruffleString T__PATTERN__FALLBACK_COMPILE = tsLiteral("_Pattern__fallback_compile");

        @Specialization(guards = "method == cachedMethod", limit = "3")
        Object search(VirtualFrame frame, Object pattern, Object inputStringOrBytes, Object posArg, Object endPosArg, int method, boolean mustAdvance,
                        @Cached("method") int cachedMethod,
                        @Cached PyNumberIndexNode indexNode,
                        @Cached PyNumberAsSizeNode asSizeNode,
                        @Cached PyObjectLookupAttr lookupCacheNode,
                        @Cached PyObjectLookupAttr lookupIndexGroupNode,
                        @Cached BuiltinFunctions.IsInstanceNode isStringNode,
                        @Cached BuiltinFunctions.IsInstanceNode isBytesNode,
                        @Cached ConditionProfile unsupportedInputTypeProfile,
                        @Cached ConditionProfile unexpectedInputTypeProfile,
                        @Cached PyObjectSizeNode lengthNode,
                        @Cached ConditionProfile truncatingInputProfile,
                        @Cached SliceNodes.CreateSliceNode createSliceNode,
                        @Cached PyObjectGetItem getItemNode,
                        @Cached TRegexCompile tRegexCompile,
                        @CachedLibrary(limit = "1") InteropLibrary libCompiledRegex,
                        @Cached ConditionProfile fallbackProfile,
                        @Cached("create(T__PATTERN__FALLBACK_COMPILE)") GetAttributeNode getFallbackCompileNode,
                        @Cached CallNode callFallbackCompileNode,
                        @Cached("create(getMethodName(method))") GetAttributeNode getFallbackMethodNode,
                        @Cached CallNode callFallbackMethodNode,
                        @Cached TRegexCallExec tRegexCallExec,
                        @CachedLibrary(limit = "1") InteropLibrary libResult,
                        @Cached ConditionProfile matchProfile,
                        @Cached("lookupMatchConstructor()") Object matchConstructor,
                        @Cached CallNode constructResultNode) {
            int pos = asSizeNode.executeExact(frame, indexNode.execute(frame, posArg));
            int endPos = asSizeNode.executeExact(frame, indexNode.execute(frame, endPosArg));
            TRegexCache tRegexCache = (TRegexCache) lookupCacheNode.execute(frame, pattern, T__PATTERN__TREGEX_CACHE);
            boolean isString = (boolean) isStringNode.execute(frame, inputStringOrBytes, PythonBuiltinClassType.PString);
            boolean isBytes = !isString && (boolean) isBytesNode.execute(frame, inputStringOrBytes, SUPPORTED_BINARY_INPUT_TYPES);
            if (unsupportedInputTypeProfile.profile(!isString && !isBytes)) {
                throw getRaiseNode().raise(TypeError, T_UNSUPPORTED_INPUT_TYPE);
            }
            if (unexpectedInputTypeProfile.profile(tRegexCache.isBinary() != isBytes)) {
                if (tRegexCache.isBinary()) {
                    throw getRaiseNode().raise(TypeError, T_UNEXPECTED_STR);
                } else {
                    throw getRaiseNode().raise(TypeError, T_UNEXPECTED_BYTES);
                }
            }
            int length = lengthNode.execute(frame, inputStringOrBytes);
            if (pos < 0) {
                pos = 0;
            } else if (pos > length) {
                pos = length;
            }
            if (endPos < 0) {
                endPos = 0;
            } else if (endPos > length) {
                endPos = length;
            }
            Object truncatedInput = inputStringOrBytes;
            if (truncatingInputProfile.profile(endPos != length)) {
                truncatedInput = getItemNode.execute(frame, inputStringOrBytes, createSliceNode.execute(0, endPos, 1));
            }
            Object compiledRegex = tRegexCompile.execute(frame, tRegexCache, method, mustAdvance);
            if (fallbackProfile.profile(libCompiledRegex.isNull(compiledRegex))) {
                Object fallbackRegex = callFallbackCompileNode.execute(getFallbackCompileNode.executeObject(frame, pattern));
                return callFallbackMethodNode.execute(getFallbackMethodNode.executeObject(frame, fallbackRegex), inputStringOrBytes, pos, endPos);
            }
            Object regexResult = tRegexCallExec.execute(frame, compiledRegex, truncatedInput, pos);
            try {
                if (matchProfile.profile((boolean) libResult.readMember(regexResult, "isMatch"))) {
                    Object indexGroup = lookupIndexGroupNode.execute(frame, pattern, T__PATTERN__INDEXGROUP);
                    return constructResultNode.execute(matchConstructor, pattern, pos, endPos, regexResult, inputStringOrBytes, indexGroup);
                } else {
                    return PNone.NONE;
                }
            } catch (UnsupportedMessageException | UnknownIdentifierException e) {
                throw CompilerDirectives.shouldNotReachHere();
            }
        }

        protected static TruffleString getMethodName(int method) {
            return PythonMethod.fromOrdinal(method).getMethodName();
        }

        @TruffleBoundary
        @NeverDefault
        protected Object lookupMatchConstructor() {
            PythonModule module = getContext().lookupBuiltinModule(T__SRE);
            return PyObjectLookupAttr.getUncached().execute(null, module, T_MATCH_CONSTRUCTOR);
        }
    }

    @Builtin(name = "tregex_call_exec", minNumOfPositionalArgs = 3)
    @TypeSystemReference(PythonArithmeticTypes.class)
    @GenerateNodeFactory
    abstract static class TRegexCallExec extends PythonTernaryBuiltinNode {

        @Specialization(limit = "1")
        Object call(VirtualFrame frame, Object callable, Object inputStringOrBytes, Number fromIndex,
                        @Cached CastToTruffleStringNode cast,
                        @CachedLibrary(limit = "3") PythonBufferAcquireLibrary bufferAcquireLib,
                        @CachedLibrary(limit = "1") PythonBufferAccessLibrary bufferLib,
                        @Cached BufferToTruffleStringNode bufferToTruffleStringNode,
                        @CachedLibrary("callable") InteropLibrary interop,
                        @Cached BranchProfile binaryProfile) {
            TruffleString input;
            Object buffer = null;
            try {
                try {
                    // This would materialize the string if it was native
                    input = cast.execute(inputStringOrBytes);
                } catch (CannotCastException e1) {
                    binaryProfile.enter();
                    // It's bytes or other buffer object
                    buffer = bufferAcquireLib.acquireReadonly(inputStringOrBytes, frame, this);
                    input = bufferToTruffleStringNode.execute(buffer, 0);
                }
                try {
                    return interop.invokeMember(callable, "exec", input, fromIndex);
                } catch (ArityException | UnsupportedTypeException | UnsupportedMessageException | UnknownIdentifierException e2) {
                    throw CompilerDirectives.shouldNotReachHere("could not call TRegex exec method", e2);
                }
            } finally {
                if (buffer != null) {
                    bufferLib.release(buffer, frame, this);
                }
            }
        }
    }
}
