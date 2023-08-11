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

import org.graalvm.collections.EconomicMap;

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
import com.oracle.graal.python.nodes.BuiltinNames;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.PNodeWithRaise;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.attributes.GetAttributeNode;
import com.oracle.graal.python.nodes.attributes.ReadAttributeFromObjectNode;
import com.oracle.graal.python.nodes.attributes.WriteAttributeToObjectNode;
import com.oracle.graal.python.nodes.call.CallNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
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
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.Idempotent;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.NeverDefault;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.ReportPolymorphism;
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
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.object.HiddenKey;
import com.oracle.truffle.api.profiles.InlinedBranchProfile;
import com.oracle.truffle.api.profiles.InlinedConditionProfile;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.api.strings.TruffleString;

@CoreFunctions(defineModule = "_sre")
public final class SREModuleBuiltins extends PythonBuiltins {

    private static HiddenKey TREGEX_CACHE = new HiddenKey("tregex_cache");

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return SREModuleBuiltinsFactory.getFactories();
    }

    @Override
    public void initialize(Python3Core core) {
        addBuiltinConstant("_with_tregex", core.getContext().getLanguage().getEngineOption(PythonOptions.WithTRegex));
        addBuiltinConstant("_with_sre", core.getContext().getLanguage().getEngineOption(PythonOptions.TRegexUsesSREFallback));
        addBuiltinConstant("_METHOD_SEARCH", PythonMethod.Search);
        addBuiltinConstant("_METHOD_MATCH", PythonMethod.Match);
        addBuiltinConstant("_METHOD_FULLMATCH", PythonMethod.FullMatch);
        super.initialize(core);
    }

    public enum PythonMethod {
        Search(tsLiteral("search")),
        Match(tsLiteral("match")),
        FullMatch(tsLiteral("fullmatch"));

        public static final int PYTHON_METHOD_COUNT = PythonMethod.values().length;

        private final TruffleString name;

        PythonMethod(TruffleString name) {
            this.name = name;
        }

        public TruffleString getMethodName() {
            return name;
        }

        public String getTRegexOption() {
            return "PythonMethod=" + name.toJavaStringUncached();
        }
    }

    public static final class TRegexCache {

        private final Object originalPattern;
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
            this.originalPattern = pattern;
            String patternStr;
            boolean binary = true;
            try {
                patternStr = CastToTruffleStringNode.executeUncached(pattern).toJavaStringUncached();
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
            this.pattern = patternStr;
            this.binary = binary;
            this.flags = getTRegexFlags(flags);
            this.localeSensitive = calculateLocaleSensitive();
            this.localeSensitiveRegexps = this.localeSensitive ? EconomicMap.create() : null;
        }

        public boolean isBinary() {
            return binary;
        }

        @Idempotent
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
                case Search:
                    if (mustAdvance) {
                        return mustAdvanceSearchRegexp;
                    } else {
                        return searchRegexp;
                    }
                case Match:
                    if (mustAdvance) {
                        return mustAdvanceMatchRegexp;
                    } else {
                        return matchRegexp;
                    }
                case FullMatch:
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
                case Search:
                    if (mustAdvance) {
                        mustAdvanceSearchRegexp = regexp;
                    } else {
                        searchRegexp = regexp;
                    }
                    break;
                case Match:
                    if (mustAdvance) {
                        mustAdvanceMatchRegexp = regexp;
                    } else {
                        matchRegexp = regexp;
                    }
                    break;
                case FullMatch:
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
                            PythonModule module = context.lookupBuiltinModule(BuiltinNames.T__SRE);
                            Object errorConstructor = PyObjectLookupAttr.executeUncached(module, T_ERROR);
                            PBaseException exception = (PBaseException) CallNode.getUncached().execute(errorConstructor, reason, originalPattern, position);
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

    @Builtin(name = "tregex_init_cache", minNumOfPositionalArgs = 3)
    @TypeSystemReference(PythonArithmeticTypes.class)
    @GenerateNodeFactory
    abstract static class TRegexInitCache extends PythonTernaryBuiltinNode {

        @Specialization
        Object call(VirtualFrame frame, Object patternObject, Object pattern, Object flags,
                        @Bind("this") Node inliningTarget,
                        @Cached PyLongAsIntNode flagsToIntNode,
                        @Cached WriteAttributeToObjectNode writeCacheNode) {
            int flagsStr = flagsToIntNode.execute(frame, inliningTarget, flags);
            TRegexCache tRegexCache = new TRegexCache(pattern, flagsStr);
            writeCacheNode.execute(patternObject, TREGEX_CACHE, tRegexCache);
            return PNone.NONE;
        }
    }

    @Builtin(name = "tregex_compile", minNumOfPositionalArgs = 3)
    @TypeSystemReference(PythonArithmeticTypes.class)
    @GenerateNodeFactory
    @ImportStatic(PythonMethod.class)
    abstract static class TRegexCompile extends PythonTernaryBuiltinNode {

        @Child private ReadAttributeFromObjectNode readCacheNode = ReadAttributeFromObjectNode.create();

        private static final TruffleString T__GETLOCALE = tsLiteral("_getlocale");

        // limit of 6 specializations = 3 Python methods * 2 values of mustAdvance
        protected static final int SPECIALIZATION_LIMIT = 2 * PythonMethod.PYTHON_METHOD_COUNT;

        @Specialization(guards = {"getTRegexCache(pattern) == cachedTRegexCache", "method == cachedMethod", "mustAdvance == cachedMustAdvance", "!cachedTRegexCache.isLocaleSensitive()"}, limit = "2")
        Object cached(Object pattern, PythonMethod method, boolean mustAdvance,
                        @Cached(value = "getTRegexCache(pattern)", weak = true) TRegexCache cachedTRegexCache,
                        @Cached("method") PythonMethod cachedMethod,
                        @Cached("mustAdvance") boolean cachedMustAdvance,
                        @Cached("getCompiledRegex(pattern, method, mustAdvance)") Object compiledRegex) {
            return compiledRegex;
        }

        protected Object getCompiledRegex(Object pattern, PythonMethod method, boolean mustAdvance) {
            return localeNonSensitive(pattern, method, mustAdvance, method, mustAdvance);
        }

        @Specialization(guards = {"method == cachedMethod", "mustAdvance == cachedMustAdvance", "!getTRegexCache(pattern).isLocaleSensitive()"}, limit = "SPECIALIZATION_LIMIT", replaces = "cached")
        Object localeNonSensitive(Object pattern, PythonMethod method, boolean mustAdvance,
                        @Cached("method") PythonMethod cachedMethod,
                        @Cached("mustAdvance") boolean cachedMustAdvance) {
            TRegexCache tRegexCache = getTRegexCache(pattern);
            final Object tRegex = tRegexCache.getRegexp(method, mustAdvance);
            if (tRegex != null) {
                return tRegex;
            } else {
                return tRegexCache.compile(getContext(), method, mustAdvance, null);
            }
        }

        @Specialization(guards = {"method == cachedMethod", "mustAdvance == cachedMustAdvance", "getTRegexCache(pattern).isLocaleSensitive()"}, limit = "SPECIALIZATION_LIMIT", replaces = "cached")
        @SuppressWarnings("truffle-static-method")
        Object localeSensitive(Object pattern, PythonMethod method, boolean mustAdvance,
                        @Bind("this") Node inliningTarget,
                        @Cached("method") PythonMethod cachedMethod,
                        @Cached("mustAdvance") boolean cachedMustAdvance,
                        @Cached("lookupGetLocaleFunction()") Object getLocale,
                        @Cached CallNode callGetLocale,
                        @Cached CastToTruffleStringNode castToTruffleStringNode) {
            TRegexCache tRegexCache = getTRegexCache(pattern);
            TruffleString locale = castToTruffleStringNode.execute(inliningTarget, callGetLocale.execute(getLocale));
            final Object tRegex = tRegexCache.getLocaleSensitiveRegexp(method, mustAdvance, locale);
            if (tRegex != null) {
                return tRegex;
            } else {
                return tRegexCache.compile(getContext(), method, mustAdvance, locale);
            }
        }

        @TruffleBoundary
        @NeverDefault
        protected Object lookupGetLocaleFunction() {
            PythonModule module = getContext().lookupBuiltinModule(BuiltinNames.T__SRE);
            return PyObjectLookupAttr.executeUncached(module, T__GETLOCALE);
        }

        protected TRegexCache getTRegexCache(Object pattern) {
            return (TRegexCache) readCacheNode.execute(pattern, TREGEX_CACHE);
        }
    }

    abstract static class RECheckInputTypeNode extends PNodeWithRaise {

        private static final TruffleString T_UNSUPPORTED_INPUT_TYPE = tsLiteral("expected string or bytes-like object");
        private static final TruffleString T_UNEXPECTED_BYTES = tsLiteral("cannot use a string pattern on a bytes-like object");
        private static final TruffleString T_UNEXPECTED_STR = tsLiteral("cannot use a bytes pattern on a string-like object");

        public abstract void execute(VirtualFrame frame, Object input, boolean expectBytes);

        @Specialization
        protected void check(VirtualFrame frame, Object input, boolean expectBytes,
                        @Bind("this") Node inliningTarget,
                        @Cached("getSupportedBinaryInputTypes()") PTuple supportedBinaryInputTypes,
                        @Cached BuiltinFunctions.IsInstanceNode isStringNode,
                        @Cached BuiltinFunctions.IsInstanceNode isBytesNode,
                        @Cached InlinedConditionProfile unsupportedInputTypeProfile,
                        @Cached InlinedConditionProfile unexpectedInputTypeProfile) {
            boolean isString = (boolean) isStringNode.execute(frame, input, PythonBuiltinClassType.PString);
            boolean isBytes = !isString && (boolean) isBytesNode.execute(frame, input, supportedBinaryInputTypes);
            if (unsupportedInputTypeProfile.profile(inliningTarget, !isString && !isBytes)) {
                throw getRaiseNode().raise(TypeError, T_UNSUPPORTED_INPUT_TYPE);
            }
            if (unexpectedInputTypeProfile.profile(inliningTarget, expectBytes != isBytes)) {
                if (expectBytes) {
                    throw getRaiseNode().raise(TypeError, T_UNEXPECTED_STR);
                } else {
                    throw getRaiseNode().raise(TypeError, T_UNEXPECTED_BYTES);
                }
            }
        }

        @NeverDefault
        protected PTuple getSupportedBinaryInputTypes() {
            return PythonObjectFactory.getUncached().createTuple(new Object[]{PythonBuiltinClassType.PBytes, PythonBuiltinClassType.PByteArray, PythonBuiltinClassType.PMMap,
                            PythonBuiltinClassType.PMemoryView, PythonBuiltinClassType.PArray});
        }
    }

    @GenerateInline
    @GenerateCached(false)
    abstract static class CreateMatchFromTRegexResultNode extends PNodeWithContext {

        private static final TruffleString T_MATCH_CONSTRUCTOR = tsLiteral("Match");
        private static final TruffleString T__PATTERN__INDEXGROUP = tsLiteral("_Pattern__indexgroup");

        public abstract Object execute(VirtualFrame frame, Node inliningTarget, Object pattern, int pos, int endPos, Object regexResult, Object input);

        @Specialization
        static Object createMatch(VirtualFrame frame, Node inliningTarget, Object pattern, int pos, int endPos, Object regexResult, Object input,
                        @Cached("lookupMatchConstructor()") Object matchConstructor,
                        @Cached InlinedConditionProfile matchProfile,
                        @CachedLibrary(limit = "1") InteropLibrary libResult,
                        @Cached PyObjectLookupAttr lookupIndexGroupNode,
                        @Cached(inline = false) CallNode constructResultNode) {
            try {
                if (matchProfile.profile(inliningTarget, (boolean) libResult.readMember(regexResult, "isMatch"))) {
                    Object indexGroup = lookupIndexGroupNode.execute(frame, inliningTarget, pattern, T__PATTERN__INDEXGROUP);
                    return constructResultNode.execute(matchConstructor, pattern, pos, endPos, regexResult, input, indexGroup);
                } else {
                    return PNone.NONE;
                }
            } catch (UnsupportedMessageException | UnknownIdentifierException e) {
                throw CompilerDirectives.shouldNotReachHere();
            }
        }

        @TruffleBoundary
        @NeverDefault
        protected Object lookupMatchConstructor() {
            PythonModule module = getContext().lookupBuiltinModule(BuiltinNames.T__SRE);
            return PyObjectLookupAttr.executeUncached(module, T_MATCH_CONSTRUCTOR);
        }
    }

    @Builtin(name = "tregex_search", minNumOfPositionalArgs = 6)
    @TypeSystemReference(PythonArithmeticTypes.class)
    @GenerateNodeFactory
    @ImportStatic(PythonMethod.class)
    abstract static class TRegexSearch extends PythonSenaryBuiltinNode {
        protected static final TruffleString T__PATTERN__FALLBACK_COMPILE = tsLiteral("_Pattern__fallback_compile");

        @Child private ReadAttributeFromObjectNode readCacheNode = ReadAttributeFromObjectNode.create();
        @Child private GetAttributeNode getFallbackCompileNode;
        @Child private CallNode callFallbackCompileNode;
        @Child private CallNode callFallbackMethodNode;
        @Child private SliceNodes.CreateSliceNode createSliceNode;
        @Child private PyObjectGetItem getItemNode;

        @Specialization(guards = {"isSingleContext()", "pattern == cachedPattern", "method == cachedMethod", "mustAdvance == cachedMustAdvance", "!tRegexCache.isLocaleSensitive()"}, limit = "1")
        @SuppressWarnings("truffle-static-method")
        protected Object doCached(VirtualFrame frame, Object pattern, Object input, Object posArg, Object endPosArg, PythonMethod method, boolean mustAdvance,
                        @Bind("this") Node inliningTarget,
                        @SuppressWarnings("unused") @Cached(value = "pattern", weak = true) Object cachedPattern,
                        @SuppressWarnings("unused") @Cached("method") PythonMethod cachedMethod,
                        @SuppressWarnings("unused") @Cached("mustAdvance") boolean cachedMustAdvance,
                        @SuppressWarnings("unused") @Cached @Shared TRegexCompile tRegexCompileNode,
                        @Cached(value = "getTRegexCache(pattern)", weak = true) TRegexCache tRegexCache,
                        @Cached(value = "tRegexCompileNode.execute(frame, pattern, method, mustAdvance)") Object compiledRegex,
                        @Cached @Shared InlinedConditionProfile fallbackProfile,
                        @Cached @Shared InlinedConditionProfile truncatingInputProfile,
                        @Cached @Shared RECheckInputTypeNode reCheckInputTypeNode,
                        @Cached @Shared PyNumberIndexNode indexNode,
                        @Cached @Shared PyNumberAsSizeNode asSizeNode,
                        @Cached @Shared PyObjectSizeNode lengthNode,
                        @CachedLibrary(limit = "1") @Shared InteropLibrary libCompiledRegex,
                        @Cached("create(method.getMethodName())") GetAttributeNode getFallbackMethodNode,
                        @Cached @Shared TRegexCallExec tRegexCallExec,
                        @Cached @Shared CreateMatchFromTRegexResultNode createMatchFromTRegexResultNode) {
            int pos = asSizeNode.executeExact(frame, inliningTarget, indexNode.execute(frame, inliningTarget, posArg));
            int endPos = asSizeNode.executeExact(frame, inliningTarget, indexNode.execute(frame, inliningTarget, endPosArg));
            int length = lengthNode.execute(frame, inliningTarget, input);
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

            reCheckInputTypeNode.execute(frame, input, tRegexCache.isBinary());

            if (fallbackProfile.profile(inliningTarget, libCompiledRegex.isNull(compiledRegex))) {
                Object fallbackRegex = getCallFallbackCompileNode().execute(getGetFallbackCompileNode().executeObject(frame, pattern));
                return getCallFallbackMethodNode().execute(getFallbackMethodNode.executeObject(frame, fallbackRegex), input, pos, endPos);
            }

            Object truncatedInput = input;
            if (truncatingInputProfile.profile(inliningTarget, endPos != length)) {
                truncatedInput = getGetItemNode().executeCached(frame, input, getCreateSliceNode().execute(0, endPos, 1));
            }
            Object regexResult = tRegexCallExec.execute(frame, compiledRegex, truncatedInput, pos);

            return createMatchFromTRegexResultNode.execute(frame, inliningTarget, pattern, pos, endPos, regexResult, input);
        }

        @Specialization(guards = {"tRegexCompileNode.execute(frame, pattern, method, mustAdvance) == compiledRegex", "method == cachedMethod",
                        "mustAdvance == cachedMustAdvance", "!tRegexCache.isLocaleSensitive()"}, limit = "1", replaces = "doCached")
        @SuppressWarnings("truffle-static-method")
        protected Object doCachedRegex(VirtualFrame frame, Object pattern, Object input, Object posArg, Object endPosArg, PythonMethod method, boolean mustAdvance,
                        @Bind("this") Node inliningTarget,
                        @Cached("method") PythonMethod cachedMethod,
                        @Cached("mustAdvance") boolean cachedMustAdvance,
                        @Cached @Shared TRegexCompile tRegexCompileNode,
                        @Cached(value = "getTRegexCache(pattern)", weak = true) TRegexCache tRegexCache,
                        @Cached(value = "tRegexCompileNode.execute(frame, pattern, method, mustAdvance)") Object compiledRegex,
                        @Cached @Shared InlinedConditionProfile fallbackProfile,
                        @Cached @Shared InlinedConditionProfile truncatingInputProfile,
                        @Cached @Shared RECheckInputTypeNode reCheckInputTypeNode,
                        @Cached @Shared PyNumberIndexNode indexNode,
                        @Cached @Shared PyNumberAsSizeNode asSizeNode,
                        @Cached @Shared PyObjectSizeNode lengthNode,
                        @CachedLibrary(limit = "1") @Shared InteropLibrary libCompiledRegex,
                        @Cached("create(method.getMethodName())") GetAttributeNode getFallbackMethodNode,
                        @Cached @Shared TRegexCallExec tRegexCallExec,
                        @Cached @Shared CreateMatchFromTRegexResultNode createMatchFromTRegexResultNode) {
            return doCached(frame, pattern, input, posArg, endPosArg, method, mustAdvance, inliningTarget, pattern, cachedMethod, mustAdvance, tRegexCompileNode, tRegexCache,
                            compiledRegex, fallbackProfile, truncatingInputProfile, reCheckInputTypeNode, indexNode, asSizeNode, lengthNode, libCompiledRegex, getFallbackMethodNode,
                            tRegexCallExec, createMatchFromTRegexResultNode);
        }

        @Specialization(guards = "method == cachedMethod", limit = "PYTHON_METHOD_COUNT", replaces = {"doCached", "doCachedRegex"})
        @SuppressWarnings("truffle-static-method")
        @ReportPolymorphism.Megamorphic
        protected Object doDynamic(VirtualFrame frame, Object pattern, Object input, Object posArg, Object endPosArg, PythonMethod method, boolean mustAdvance,
                        @Bind("this") Node inliningTarget,
                        @Cached("method") PythonMethod cachedMethod,
                        @Cached @Shared TRegexCompile tRegexCompileNode,
                        @Cached @Shared PyNumberIndexNode indexNode,
                        @Cached @Shared PyNumberAsSizeNode asSizeNode,
                        @Cached @Shared RECheckInputTypeNode reCheckInputTypeNode,
                        @Cached @Shared PyObjectSizeNode lengthNode,
                        @Cached @Shared InlinedConditionProfile fallbackProfile,
                        @Cached @Shared InlinedConditionProfile truncatingInputProfile,
                        @CachedLibrary(limit = "1") @Shared InteropLibrary libCompiledRegex,
                        @Cached("create(method.getMethodName())") GetAttributeNode getFallbackMethodNode,
                        @Cached @Shared TRegexCallExec tRegexCallExec,
                        @Cached @Shared CreateMatchFromTRegexResultNode createMatchFromTRegexResultNode) {
            TRegexCache tRegexCache = getTRegexCache(pattern);
            Object compiledRegex = tRegexCompileNode.execute(frame, pattern, method, mustAdvance);
            return doCached(frame, pattern, input, posArg, endPosArg, method, mustAdvance, inliningTarget, pattern, cachedMethod, mustAdvance, tRegexCompileNode, tRegexCache,
                            compiledRegex, fallbackProfile, truncatingInputProfile, reCheckInputTypeNode, indexNode, asSizeNode, lengthNode, libCompiledRegex, getFallbackMethodNode,
                            tRegexCallExec, createMatchFromTRegexResultNode);
        }

        protected TRegexCache getTRegexCache(Object pattern) {
            return (TRegexCache) readCacheNode.execute(pattern, TREGEX_CACHE);
        }

        private GetAttributeNode getGetFallbackCompileNode() {
            if (getFallbackCompileNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                getFallbackCompileNode = insert(GetAttributeNode.create(T__PATTERN__FALLBACK_COMPILE));
            }
            return getFallbackCompileNode;
        }

        private CallNode getCallFallbackCompileNode() {
            if (callFallbackCompileNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                callFallbackCompileNode = insert(CallNode.create());
            }
            return callFallbackCompileNode;
        }

        private CallNode getCallFallbackMethodNode() {
            if (callFallbackMethodNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                callFallbackMethodNode = insert(CallNode.create());
            }
            return callFallbackMethodNode;
        }

        private SliceNodes.CreateSliceNode getCreateSliceNode() {
            if (createSliceNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                createSliceNode = insert(SliceNodes.CreateSliceNode.create());
            }
            return createSliceNode;
        }

        private PyObjectGetItem getGetItemNode() {
            if (getItemNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                getItemNode = insert(PyObjectGetItem.create());
            }
            return getItemNode;
        }
    }

    @Builtin(name = "tregex_call_exec", minNumOfPositionalArgs = 3)
    @TypeSystemReference(PythonArithmeticTypes.class)
    @GenerateNodeFactory
    abstract static class TRegexCallExec extends PythonTernaryBuiltinNode {

        @Child private BufferToTruffleStringNode bufferToTruffleStringNode;

        // limit of 2 specializations to allow inlining of both a must_advance=False and a
        // must_advance=True version in re builtins like sub, split, findall
        @Specialization(guards = "callable == cachedCallable", limit = "2")
        @SuppressWarnings("truffle-static-method")
        Object doCached(VirtualFrame frame, Object callable, Object inputStringOrBytes, Number fromIndex,
                        @Bind("this") Node inliningTarget,
                        @Cached(value = "callable", weak = true) Object cachedCallable,
                        @Cached @Shared CastToTruffleStringNode cast,
                        @CachedLibrary(limit = "3") @Shared PythonBufferAcquireLibrary bufferAcquireLib,
                        @CachedLibrary(limit = "1") @Shared PythonBufferAccessLibrary bufferLib,
                        @CachedLibrary("callable") InteropLibrary interop,
                        @Cached @Shared InlinedBranchProfile binaryProfile) {
            TruffleString input;
            Object buffer = null;
            try {
                try {
                    // This would materialize the string if it was native
                    input = cast.execute(inliningTarget, inputStringOrBytes);
                } catch (CannotCastException e1) {
                    binaryProfile.enter(inliningTarget);
                    // It's bytes or other buffer object
                    buffer = bufferAcquireLib.acquireReadonly(inputStringOrBytes, frame, this);
                    input = getBufferToTruffleStringNode().execute(buffer, 0);
                }
                try {
                    return interop.invokeMember(cachedCallable, "exec", input, fromIndex);
                } catch (ArityException | UnsupportedTypeException | UnsupportedMessageException | UnknownIdentifierException e2) {
                    throw CompilerDirectives.shouldNotReachHere("could not call TRegex exec method", e2);
                }
            } finally {
                if (buffer != null) {
                    bufferLib.release(buffer, frame, this);
                }
            }
        }

        @Specialization(limit = "1", replaces = "doCached")
        @ReportPolymorphism.Megamorphic
        Object doUncached(VirtualFrame frame, Object callable, Object inputStringOrBytes, Number fromIndex,
                        @Bind("this") Node inliningTarget,
                        @Cached @Shared CastToTruffleStringNode cast,
                        @CachedLibrary(limit = "3") @Shared PythonBufferAcquireLibrary bufferAcquireLib,
                        @CachedLibrary(limit = "1") @Shared PythonBufferAccessLibrary bufferLib,
                        @CachedLibrary("callable") InteropLibrary interop,
                        @Cached @Shared InlinedBranchProfile binaryProfile) {
            return doCached(frame, callable, inputStringOrBytes, fromIndex, inliningTarget, callable, cast, bufferAcquireLib, bufferLib, interop, binaryProfile);
        }

        private BufferToTruffleStringNode getBufferToTruffleStringNode() {
            if (bufferToTruffleStringNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                bufferToTruffleStringNode = insert(BufferToTruffleStringNode.create());
            }
            return bufferToTruffleStringNode;
        }
    }
}
