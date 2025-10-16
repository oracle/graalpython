/*
 * Copyright (c) 2018, 2025, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.graal.python.nodes.ErrorMessages.BAD_CHAR_IN_GROUP_NAME;
import static com.oracle.graal.python.nodes.ErrorMessages.BAD_ESCAPE_END_OF_STRING;
import static com.oracle.graal.python.nodes.ErrorMessages.INVALID_GROUP_REFERENCE;
import static com.oracle.graal.python.nodes.ErrorMessages.MISSING_S;
import static com.oracle.graal.python.nodes.ErrorMessages.UNKNOWN_GROUP_NAME;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.TypeError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.ValueError;
import static com.oracle.graal.python.util.PythonUtils.TS_ENCODING;
import static com.oracle.graal.python.util.PythonUtils.tsLiteral;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import org.graalvm.collections.EconomicMap;

import com.oracle.graal.python.annotations.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.Python3Core;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.array.PArray;
import com.oracle.graal.python.builtins.objects.buffer.PythonBufferAccessLibrary;
import com.oracle.graal.python.builtins.objects.buffer.PythonBufferAcquireLibrary;
import com.oracle.graal.python.builtins.objects.bytes.BytesNodes;
import com.oracle.graal.python.builtins.objects.cext.common.NativePointer;
import com.oracle.graal.python.builtins.objects.exception.PBaseException;
import com.oracle.graal.python.builtins.objects.memoryview.PMemoryView;
import com.oracle.graal.python.builtins.objects.mmap.PMMap;
import com.oracle.graal.python.builtins.objects.module.PythonModule;
import com.oracle.graal.python.builtins.objects.object.PythonObject;
import com.oracle.graal.python.lib.PyLongAsIntNode;
import com.oracle.graal.python.lib.PyNumberAsSizeNode;
import com.oracle.graal.python.lib.PyNumberIndexNode;
import com.oracle.graal.python.lib.PyObjectLookupAttr;
import com.oracle.graal.python.lib.PyObjectSizeNode;
import com.oracle.graal.python.lib.PyUnicodeCheckNode;
import com.oracle.graal.python.nodes.BuiltinNames;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.HiddenAttr;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.attributes.GetFixedAttributeNode;
import com.oracle.graal.python.nodes.attributes.ReadAttributeFromModuleNode;
import com.oracle.graal.python.nodes.call.CallNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.builtins.PythonQuaternaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonSenaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonTernaryBuiltinNode;
import com.oracle.graal.python.nodes.util.CannotCastException;
import com.oracle.graal.python.nodes.util.CastToTruffleStringNode;
import com.oracle.graal.python.runtime.IndirectCallData.InteropCallData;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.PythonOptions;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.exception.PythonErrorType;
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
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.ExceptionType;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.InlinedBranchProfile;
import com.oracle.truffle.api.profiles.InlinedConditionProfile;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.api.strings.TruffleString;
import com.oracle.truffle.api.strings.TruffleStringBuilder;
import com.oracle.truffle.api.strings.TruffleStringBuilderUTF32;

@CoreFunctions(defineModule = "_sre")
public final class SREModuleBuiltins extends PythonBuiltins {

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

    public enum PythonMethod implements TruffleObject {
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
        public TRegexCache(Node node, Object pattern, int flags) {
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
                    throw PRaiseNode.raiseStatic(node, TypeError, ErrorMessages.EXPECTED_STR_OR_BYTESLIKE_OBJ);
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
        public Object compile(Node node, PythonContext context, PythonMethod method, boolean mustAdvance, TruffleString locale) {
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
                throw handleCompilationError(node, e, lib, context);
            }
            if (isLocaleSensitive()) {
                setLocaleSensitiveRegexp(method, mustAdvance, locale, regexp);
            } else {
                setRegexp(method, mustAdvance, regexp);
            }
            return regexp;
        }

        // No BoundaryCallContext: lookups attribute on a builtin module; constructs builtin
        // exceptions
        private RuntimeException handleCompilationError(Node node, RuntimeException e, InteropLibrary lib, PythonContext context) {
            try {
                if (lib.isException(e)) {
                    if (lib.getExceptionType(e) == ExceptionType.PARSE_ERROR) {
                        TruffleString reason = lib.asTruffleString(lib.getExceptionMessage(e)).switchEncodingUncached(TS_ENCODING);
                        if (reason.equalsUncached(T_VALUE_ERROR_UNICODE_FLAG_BYTES_PATTERN, TS_ENCODING) ||
                                        reason.equalsUncached(T_VALUE_ERROR_LOCALE_FLAG_STR_PATTERN, TS_ENCODING) ||
                                        reason.equalsUncached(T_VALUE_ERROR_ASCII_UNICODE_INCOMPATIBLE, TS_ENCODING) ||
                                        reason.equalsUncached(T_VALUE_ERROR_ASCII_LOCALE_INCOMPATIBLE, TS_ENCODING)) {
                            return PRaiseNode.raiseStatic(node, ValueError, reason);
                        } else {
                            SourceSection sourceSection = lib.getSourceLocation(e);
                            int position = sourceSection.getCharIndex();
                            PythonModule module = context.lookupBuiltinModule(BuiltinNames.T__SRE);
                            Object errorConstructor = PyObjectLookupAttr.executeUncached(module, T_ERROR);
                            PBaseException exception = (PBaseException) CallNode.executeUncached(errorConstructor, reason, originalPattern, position);
                            return PRaiseNode.raiseExceptionObjectStatic(node, exception);
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
    @GenerateNodeFactory
    abstract static class TRegexInitCache extends PythonTernaryBuiltinNode {

        @Specialization
        Object call(VirtualFrame frame, PythonObject patternObject, Object pattern, Object flags,
                        @Bind Node inliningTarget,
                        @Cached PyLongAsIntNode flagsToIntNode,
                        @Cached HiddenAttr.WriteNode writeCacheNode) {
            int flagsStr = flagsToIntNode.execute(frame, inliningTarget, flags);
            TRegexCache tRegexCache = new TRegexCache(inliningTarget, pattern, flagsStr);
            writeCacheNode.execute(inliningTarget, patternObject, HiddenAttr.TREGEX_CACHE, tRegexCache);
            return PNone.NONE;
        }
    }

    @Builtin(name = "tregex_compile", minNumOfPositionalArgs = 3)
    @GenerateNodeFactory
    @ImportStatic(PythonMethod.class)
    abstract static class TRegexCompile extends PythonTernaryBuiltinNode {

        @Child private HiddenAttr.ReadNode readCacheNode = HiddenAttr.ReadNode.create();

        private static final TruffleString T__GETLOCALE = tsLiteral("_getlocale");

        // limit of 6 specializations = 3 Python methods * 2 values of mustAdvance
        protected static final int SPECIALIZATION_LIMIT = 2 * PythonMethod.PYTHON_METHOD_COUNT;

        @Specialization(guards = {"getTRegexCache(pattern) == cachedTRegexCache", "method == cachedMethod", "mustAdvance == cachedMustAdvance", "!cachedTRegexCache.isLocaleSensitive()"}, limit = "2")
        @SuppressWarnings("unused")
        Object cached(PythonObject pattern, PythonMethod method, boolean mustAdvance,
                        @Cached(value = "getTRegexCache(pattern)", weak = true) TRegexCache cachedTRegexCache,
                        @Cached("method") PythonMethod cachedMethod,
                        @Cached("mustAdvance") boolean cachedMustAdvance,
                        @Cached("getCompiledRegex(pattern, method, mustAdvance)") Object compiledRegex) {
            return compiledRegex;
        }

        protected Object getCompiledRegex(PythonObject pattern, PythonMethod method, boolean mustAdvance) {
            return localeNonSensitive(pattern, method, mustAdvance, method, mustAdvance);
        }

        @Specialization(guards = {"method == cachedMethod", "mustAdvance == cachedMustAdvance", "!getTRegexCache(pattern).isLocaleSensitive()"}, limit = "SPECIALIZATION_LIMIT", replaces = "cached")
        Object localeNonSensitive(PythonObject pattern, PythonMethod method, boolean mustAdvance,
                        @Cached("method") @SuppressWarnings("unused") PythonMethod cachedMethod,
                        @Cached("mustAdvance") @SuppressWarnings("unused") boolean cachedMustAdvance) {
            TRegexCache tRegexCache = getTRegexCache(pattern);
            final Object tRegex = tRegexCache.getRegexp(method, mustAdvance);
            if (tRegex != null) {
                return tRegex;
            } else {
                return tRegexCache.compile(this, getContext(), method, mustAdvance, null);
            }
        }

        @Specialization(guards = {"method == cachedMethod", "mustAdvance == cachedMustAdvance", "getTRegexCache(pattern).isLocaleSensitive()"}, limit = "SPECIALIZATION_LIMIT", replaces = "cached")
        @SuppressWarnings("truffle-static-method")
        Object localeSensitive(VirtualFrame frame, PythonObject pattern, PythonMethod method, boolean mustAdvance,
                        @Bind Node inliningTarget,
                        @Cached("method") @SuppressWarnings("unused") PythonMethod cachedMethod,
                        @Cached("mustAdvance") @SuppressWarnings("unused") boolean cachedMustAdvance,
                        @Cached("lookupGetLocaleFunction()") Object getLocale,
                        @Cached CallNode callGetLocale,
                        @Cached CastToTruffleStringNode castToTruffleStringNode) {
            TRegexCache tRegexCache = getTRegexCache(pattern);
            TruffleString locale = castToTruffleStringNode.execute(inliningTarget, callGetLocale.execute(frame, getLocale));
            final Object tRegex = tRegexCache.getLocaleSensitiveRegexp(method, mustAdvance, locale);
            if (tRegex != null) {
                return tRegex;
            } else {
                return tRegexCache.compile(this, getContext(), method, mustAdvance, locale);
            }
        }

        @TruffleBoundary
        @NeverDefault
        protected Object lookupGetLocaleFunction() {
            PythonModule module = getContext().lookupBuiltinModule(BuiltinNames.T__SRE);
            return PyObjectLookupAttr.executeUncached(module, T__GETLOCALE);
        }

        protected TRegexCache getTRegexCache(PythonObject pattern) {
            return (TRegexCache) readCacheNode.executeCached(pattern, HiddenAttr.TREGEX_CACHE, null);
        }
    }

    @GenerateInline(false)       // footprint reduction 36 -> 17
    abstract static class RECheckInputTypeNode extends Node {

        private static final TruffleString T_UNSUPPORTED_INPUT_TYPE = tsLiteral("expected string or bytes-like object");
        private static final TruffleString T_UNEXPECTED_BYTES = tsLiteral("cannot use a string pattern on a bytes-like object");
        private static final TruffleString T_UNEXPECTED_STR = tsLiteral("cannot use a bytes pattern on a string-like object");

        public abstract void execute(VirtualFrame frame, Object input, boolean expectBytes);

        @Specialization
        static void check(Object input, boolean expectBytes,
                        @Bind Node inliningTarget,
                        @Cached PyUnicodeCheckNode unicodeCheckNode,
                        @Cached BytesNodes.BytesLikeCheck bytesLikeCheck,
                        @Cached PRaiseNode unexpectedStrRaise,
                        @Cached PRaiseNode unexpectedBytesRaise,
                        @Cached PRaiseNode unexpectedTypeRaise) {
            if (unicodeCheckNode.execute(inliningTarget, input)) {
                if (expectBytes) {
                    throw unexpectedStrRaise.raise(inliningTarget, TypeError, T_UNEXPECTED_STR);
                }
                return;
            }
            if (bytesLikeCheck.execute(inliningTarget, input) || input instanceof PMMap || input instanceof PMemoryView || input instanceof PArray) {
                if (!expectBytes) {
                    throw unexpectedBytesRaise.raise(inliningTarget, TypeError, T_UNEXPECTED_BYTES);
                }
                return;
            }
            throw unexpectedTypeRaise.raise(inliningTarget, TypeError, T_UNSUPPORTED_INPUT_TYPE);
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
                        @Cached GetMatchConstructorNode getConstructor,
                        @Cached InlinedConditionProfile matchProfile,
                        @CachedLibrary(limit = "1") InteropLibrary libResult,
                        @Cached PyObjectLookupAttr lookupIndexGroupNode,
                        @Cached(inline = false) CallNode constructResultNode) {
            try {
                if (matchProfile.profile(inliningTarget, (boolean) libResult.readMember(regexResult, "isMatch"))) {
                    Object indexGroup = lookupIndexGroupNode.execute(frame, inliningTarget, pattern, T__PATTERN__INDEXGROUP);
                    return constructResultNode.execute(frame, getConstructor.execute(inliningTarget), pattern, pos, endPos, regexResult, input, indexGroup);
                } else {
                    return PNone.NONE;
                }
            } catch (UnsupportedMessageException | UnknownIdentifierException e) {
                throw CompilerDirectives.shouldNotReachHere();
            }
        }

        @GenerateInline
        @GenerateCached(false)
        abstract static class GetMatchConstructorNode extends PNodeWithContext {
            public abstract Object execute(Node inliningTarget);

            @Specialization(guards = "isSingleContext()")
            static Object doSingleContext(
                            @Cached("lookupMatchConstructor()") Object constructor) {
                return constructor;
            }

            @Specialization(replaces = "doSingleContext")
            static Object doRead(
                            @Bind PythonContext context,
                            @Cached ReadAttributeFromModuleNode read) {
                PythonModule module = context.lookupBuiltinModule(BuiltinNames.T__SRE);
                return read.execute(module, T_MATCH_CONSTRUCTOR);
            }

            @TruffleBoundary
            @NeverDefault
            protected static Object lookupMatchConstructor() {
                PythonModule module = PythonContext.get(null).lookupBuiltinModule(BuiltinNames.T__SRE);
                return PyObjectLookupAttr.executeUncached(module, T_MATCH_CONSTRUCTOR);
            }
        }
    }

    @Builtin(name = "tregex_search", minNumOfPositionalArgs = 6)
    @GenerateNodeFactory
    @ImportStatic(PythonMethod.class)
    abstract static class TRegexSearch extends PythonSenaryBuiltinNode {
        protected static final TruffleString T__PATTERN__FALLBACK_COMPILE = tsLiteral("_Pattern__fallback_compile");

        @Child private HiddenAttr.ReadNode readCacheNode = HiddenAttr.ReadNode.create();
        @Child private GetFixedAttributeNode getFallbackCompileNode;
        @Child private CallNode callFallbackCompileNode;
        @Child private CallNode callFallbackMethodNode;

        @Specialization(guards = {"isSingleContext()", "pattern == cachedPattern", "method == cachedMethod", "mustAdvance == cachedMustAdvance", "!tRegexCache.isLocaleSensitive()"}, limit = "1")
        @SuppressWarnings({"truffle-static-method", "unused"})
        protected Object doCached(VirtualFrame frame, PythonObject pattern, Object input, Object posArg, Object endPosArg, PythonMethod method, boolean mustAdvance,
                        @Bind Node inliningTarget,
                        @SuppressWarnings("unused") @Cached(value = "pattern", weak = true) PythonObject cachedPattern,
                        @SuppressWarnings("unused") @Cached("method") PythonMethod cachedMethod,
                        @SuppressWarnings("unused") @Cached("mustAdvance") boolean cachedMustAdvance,
                        @SuppressWarnings("unused") @Cached @Shared TRegexCompile tRegexCompileNode,
                        @Cached(value = "getTRegexCache(pattern)", weak = true) TRegexCache tRegexCache,
                        @Cached(value = "tRegexCompileNode.execute(frame, pattern, method, mustAdvance)") Object compiledRegex,
                        @Cached @Shared InlinedConditionProfile fallbackProfile,
                        @Cached @Shared RECheckInputTypeNode reCheckInputTypeNode,
                        @Cached @Shared PyNumberIndexNode indexNode,
                        @Cached @Shared PyNumberAsSizeNode asSizeNode,
                        @Cached @Shared PyObjectSizeNode lengthNode,
                        @CachedLibrary(limit = "1") @Shared InteropLibrary libCompiledRegex,
                        @Cached("create(method.getMethodName())") GetFixedAttributeNode getFallbackMethodNode,
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
                GetFixedAttributeNode getFixedAttributeNode = getGetFallbackCompileNode();
                Object fallbackRegex = getCallFallbackCompileNode().executeWithoutFrame(getFixedAttributeNode.execute(frame, pattern));
                return getCallFallbackMethodNode().executeWithoutFrame(getFallbackMethodNode.execute(frame, fallbackRegex), input, pos, endPos);
            }

            Object regexResult = tRegexCallExec.execute(frame, compiledRegex, input, pos, endPos);

            return createMatchFromTRegexResultNode.execute(frame, inliningTarget, pattern, pos, endPos, regexResult, input);
        }

        @Specialization(guards = {"tRegexCompileNode.execute(frame, pattern, method, mustAdvance) == compiledRegex", "method == cachedMethod",
                        "mustAdvance == cachedMustAdvance", "!tRegexCache.isLocaleSensitive()"}, limit = "1", replaces = "doCached")
        @SuppressWarnings("truffle-static-method")
        protected Object doCachedRegex(VirtualFrame frame, PythonObject pattern, Object input, Object posArg, Object endPosArg, PythonMethod method, boolean mustAdvance,
                        @Bind Node inliningTarget,
                        @Cached("method") PythonMethod cachedMethod,
                        @Cached("mustAdvance") @SuppressWarnings("unused") boolean cachedMustAdvance,
                        @Cached @Shared TRegexCompile tRegexCompileNode,
                        @Cached(value = "getTRegexCache(pattern)", weak = true) TRegexCache tRegexCache,
                        @Cached(value = "tRegexCompileNode.execute(frame, pattern, method, mustAdvance)") Object compiledRegex,
                        @Cached @Shared InlinedConditionProfile fallbackProfile,
                        @Cached @Shared RECheckInputTypeNode reCheckInputTypeNode,
                        @Cached @Shared PyNumberIndexNode indexNode,
                        @Cached @Shared PyNumberAsSizeNode asSizeNode,
                        @Cached @Shared PyObjectSizeNode lengthNode,
                        @CachedLibrary(limit = "1") @Shared InteropLibrary libCompiledRegex,
                        @Cached("create(method.getMethodName())") GetFixedAttributeNode getFallbackMethodNode,
                        @Cached @Shared TRegexCallExec tRegexCallExec,
                        @Cached @Shared CreateMatchFromTRegexResultNode createMatchFromTRegexResultNode) {
            return doCached(frame, pattern, input, posArg, endPosArg, method, mustAdvance, inliningTarget, pattern, cachedMethod, mustAdvance, tRegexCompileNode, tRegexCache,
                            compiledRegex, fallbackProfile, reCheckInputTypeNode, indexNode, asSizeNode, lengthNode, libCompiledRegex, getFallbackMethodNode,
                            tRegexCallExec, createMatchFromTRegexResultNode);
        }

        @Specialization(guards = "method == cachedMethod", limit = "PYTHON_METHOD_COUNT", replaces = {"doCached", "doCachedRegex"})
        @SuppressWarnings("truffle-static-method")
        // @ReportPolymorphism.Megamorphic
        protected Object doDynamic(VirtualFrame frame, PythonObject pattern, Object input, Object posArg, Object endPosArg, PythonMethod method, boolean mustAdvance,
                        @Bind Node inliningTarget,
                        @Cached("method") PythonMethod cachedMethod,
                        @Cached @Shared TRegexCompile tRegexCompileNode,
                        @Cached @Shared PyNumberIndexNode indexNode,
                        @Cached @Shared PyNumberAsSizeNode asSizeNode,
                        @Cached @Shared RECheckInputTypeNode reCheckInputTypeNode,
                        @Cached @Shared PyObjectSizeNode lengthNode,
                        @Cached @Shared InlinedConditionProfile fallbackProfile,
                        @CachedLibrary(limit = "1") @Shared InteropLibrary libCompiledRegex,
                        @Cached("create(method.getMethodName())") GetFixedAttributeNode getFallbackMethodNode,
                        @Cached @Shared TRegexCallExec tRegexCallExec,
                        @Cached @Shared CreateMatchFromTRegexResultNode createMatchFromTRegexResultNode) {
            TRegexCache tRegexCache = getTRegexCache(pattern);
            Object compiledRegex = tRegexCompileNode.execute(frame, pattern, method, mustAdvance);
            return doCached(frame, pattern, input, posArg, endPosArg, method, mustAdvance, inliningTarget, pattern, cachedMethod, mustAdvance, tRegexCompileNode, tRegexCache,
                            compiledRegex, fallbackProfile, reCheckInputTypeNode, indexNode, asSizeNode, lengthNode, libCompiledRegex, getFallbackMethodNode,
                            tRegexCallExec, createMatchFromTRegexResultNode);
        }

        protected TRegexCache getTRegexCache(PythonObject pattern) {
            return (TRegexCache) readCacheNode.executeCached(pattern, HiddenAttr.TREGEX_CACHE, null);
        }

        private GetFixedAttributeNode getGetFallbackCompileNode() {
            if (getFallbackCompileNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                getFallbackCompileNode = insert(GetFixedAttributeNode.create(T__PATTERN__FALLBACK_COMPILE));
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
    }

    @Builtin(name = "tregex_call_exec", minNumOfPositionalArgs = 4)
    @GenerateNodeFactory
    abstract static class TRegexCallExec extends PythonQuaternaryBuiltinNode {

        @Child private BufferToTruffleStringNode bufferToTruffleStringNode;

        // limit of 2 specializations to allow inlining of both a must_advance=False and a
        // must_advance=True version in re builtins like sub, split, findall
        @Specialization(guards = "callable == cachedCallable", limit = "2")
        @SuppressWarnings("truffle-static-method")
        Object doCached(VirtualFrame frame, @SuppressWarnings("unused") Object callable, Object inputStringOrBytes, Number fromIndex, Number toIndex,
                        @Bind Node inliningTarget,
                        @Shared @Cached("createFor($node)") InteropCallData callData,
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
                    buffer = bufferAcquireLib.acquireReadonly(inputStringOrBytes, frame, callData);
                    input = getBufferToTruffleStringNode().execute(buffer);
                }
                try {
                    return interop.invokeMember(cachedCallable, "exec", input, fromIndex, toIndex, 0, toIndex);
                } catch (ArityException | UnsupportedTypeException | UnsupportedMessageException | UnknownIdentifierException e2) {
                    throw CompilerDirectives.shouldNotReachHere("could not call TRegex exec method", e2);
                }
            } finally {
                if (buffer != null) {
                    bufferLib.release(buffer, frame, callData);
                }
            }
        }

        @Specialization(limit = "1", replaces = "doCached")
        // @ReportPolymorphism.Megamorphic
        Object doUncached(VirtualFrame frame, Object callable, Object inputStringOrBytes, Number fromIndex, Number toIndex,
                        @Bind Node inliningTarget,
                        @Shared @Cached("createFor($node)") InteropCallData callData,
                        @Cached @Shared CastToTruffleStringNode cast,
                        @CachedLibrary(limit = "3") @Shared PythonBufferAcquireLibrary bufferAcquireLib,
                        @CachedLibrary(limit = "1") @Shared PythonBufferAccessLibrary bufferLib,
                        @CachedLibrary("callable") InteropLibrary interop,
                        @Cached @Shared InlinedBranchProfile binaryProfile) {
            return doCached(frame, callable, inputStringOrBytes, fromIndex, inliningTarget, callData, callable, cast, bufferAcquireLib, bufferLib, interop, binaryProfile);
        }

        private BufferToTruffleStringNode getBufferToTruffleStringNode() {
            if (bufferToTruffleStringNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                bufferToTruffleStringNode = insert(BufferToTruffleStringNode.create());
            }
            return bufferToTruffleStringNode;
        }

        @GenerateInline(false)
        abstract static class BufferToTruffleStringNode extends PNodeWithContext {

            public abstract TruffleString execute(Object buffer);

            @Specialization(limit = "4")
            static TruffleString convert(Object buffer,
                            @Bind Node inliningTarget,
                            @CachedLibrary(value = "buffer") PythonBufferAccessLibrary bufferLib,
                            @Cached TruffleString.FromByteArrayNode fromByteArrayNode,
                            @Cached TruffleString.FromNativePointerNode fromNativePointerNode,
                            @Cached InlinedBranchProfile internalArrayProfile,
                            @Cached InlinedBranchProfile nativeProfile,
                            @Cached InlinedBranchProfile fallbackProfile) {
                PythonBufferAccessLibrary.assertIsBuffer(buffer);
                int len = bufferLib.getBufferLength(buffer);
                if (bufferLib.hasInternalByteArray(buffer)) {
                    internalArrayProfile.enter(inliningTarget);
                    byte[] bytes = bufferLib.getInternalByteArray(buffer);
                    return fromByteArrayNode.execute(bytes, 0, len, TruffleString.Encoding.ISO_8859_1, false);
                }
                if (bufferLib.isNative(buffer)) {
                    nativeProfile.enter(inliningTarget);
                    Object ptr = bufferLib.getNativePointer(buffer);
                    if (ptr != null) {
                        if (ptr instanceof Long lptr) {
                            ptr = new NativePointer(lptr);
                        }
                        return fromNativePointerNode.execute(ptr, 0, len, TruffleString.Encoding.ISO_8859_1, false);
                    }
                }
                fallbackProfile.enter(inliningTarget);
                byte[] bytes = bufferLib.getCopiedByteArray(buffer);
                return fromByteArrayNode.execute(bytes, 0, len, TruffleString.Encoding.ISO_8859_1, false);
            }

            @NeverDefault
            public static BufferToTruffleStringNode create() {
                return SREModuleBuiltinsFactory.TRegexCallExecFactory.BufferToTruffleStringNodeGen.create();
            }
        }
    }

    @Builtin(name = "tregex_re_subn", minNumOfPositionalArgs = 6)
    @GenerateNodeFactory
    @ImportStatic(PythonMethod.class)
    abstract static class TRegexRESubN extends PythonSenaryBuiltinNode {

        @Specialization
        static Object doCached(VirtualFrame frame, TRegexCache pattern, TruffleString replacement, TruffleString string, int count,
                        @Bind Node inliningTarget) {
            TruffleStringBuilderUTF32 result = TruffleStringBuilder.createUTF32(Math.max(32, string.byteLength(TS_ENCODING)));
            int n = 0;
            int pos = 0;
            boolean mustAdvance = false;
            return null;
        }
    }

    private static abstract sealed class ReplacementToken {
    }

    private static final class Codepoint extends ReplacementToken {
        private final int codepoint;

        private Codepoint(int codepoint) {
            this.codepoint = codepoint;
        }
    }

    private static final class Literal extends ReplacementToken {
        private final TruffleString literal;

        private Literal(TruffleString literal) {
            this.literal = literal;
        }
    }

    private static final class GroupReference extends ReplacementToken {
        private final int groupNumber;

        private GroupReference(int groupNumber) {
            this.groupNumber = groupNumber;
        }
    }

    private static abstract sealed class ReplacementConsumer {
        abstract void codepoint(int codepoint);

        abstract void literal(TruffleString replacement, int fromIndex, int toIndex);

        abstract void groupReference(int groupNumber);
    }

    private static final class ReplacementTokenListConsumer extends ReplacementConsumer {
        private final ArrayList<ReplacementToken> tokens = new ArrayList<>();

        @Override
        void codepoint(int codepoint) {
            tokens.add(new Codepoint(codepoint));
        }

        @Override
        void literal(TruffleString replacement, int fromByteIndex, int toByteIndex) {
            tokens.add(new Literal(replacement.substringByteIndexUncached(fromByteIndex, toByteIndex, TS_ENCODING, false)));
        }

        @Override
        void groupReference(int groupNumber) {
            tokens.add(new GroupReference(groupNumber));
        }
    }

    private static final int CODEPOINT_LENGTH_ASCII = 4;
    // TODO
    private static final PythonBuiltinClassType PATTERN_ERROR = PythonErrorType.Exception;

    private static void parseReplacement(Object tregexCompiledRegex, TruffleString replacement, ReplacementConsumer consumer,
                    Node inliningTarget,
                    TruffleString.ByteIndexOfCodePointNode indexOfNode,
                    TruffleString.CodePointAtByteIndexNode codePointAtByteIndexNode,
                    TruffleString.SubstringByteIndexNode substringByteIndexNode,
                    TruffleString.GetCodeRangeNode getCodeRangeNode,
                    TRegexUtil.InteropReadMemberNode readGroupCountNode,
                    TRegexUtil.InteropReadMemberNode readNamedGroupsNode,
                    InteropLibrary genericInteropLib,
                    PRaiseNode raiseNode) {
        int length = replacement.byteLength(TS_ENCODING);
        if (length == 0) {
            return;
        }
        int numberOfCaptureGroups = TRegexUtil.TRegexCompiledRegexAccessor.groupCount(tregexCompiledRegex, inliningTarget, readGroupCountNode);
        int lastPos = 0;
        while (true) {
            int backslashPos = indexOfNode.execute(replacement, '\\', lastPos, length, TS_ENCODING);
            consumer.literal(replacement, lastPos, backslashPos < 0 ? length : backslashPos);
            if (backslashPos < 0) {
                return;
            }
            int nextCPPos = backslashPos + CODEPOINT_LENGTH_ASCII;
            if (nextCPPos >= length) {
                throw raiseNode.raise(inliningTarget, PATTERN_ERROR, BAD_ESCAPE_END_OF_STRING);
            }
            int firstCodepoint = codePointAtByteIndexNode.execute(replacement, nextCPPos, TS_ENCODING);
            nextCPPos += CODEPOINT_LENGTH_ASCII;
            int secondCodepoint = nextCPPos < length ? codePointAtByteIndexNode.execute(replacement, nextCPPos, TS_ENCODING) : -1;
            nextCPPos += CODEPOINT_LENGTH_ASCII;
            if (firstCodepoint == 'g') {
                if (secondCodepoint != '<') {
                    throw raiseNode.raise(inliningTarget, PATTERN_ERROR, MISSING_S, "<");
                }
                int nameStartPos = nextCPPos;
                int nameEndPos;
                if (nameStartPos >= length || (nameEndPos = indexOfNode.execute(replacement, '>', nameStartPos, length, TS_ENCODING)) < 0 || nameEndPos == nameStartPos) {
                    throw raiseNode.raise(inliningTarget, PATTERN_ERROR, MISSING_S, "group name");
                }
                int nameLength = nameEndPos - nameStartPos;
                assert nameLength > 0;
                TruffleString name = substringByteIndexNode.execute(replacement, nameStartPos, nameLength, TS_ENCODING, true);
                if (getCodeRangeNode.execute(name, TS_ENCODING) != TruffleString.CodeRange.ASCII) {
                    throw raiseNode.raise(inliningTarget, PATTERN_ERROR, BAD_CHAR_IN_GROUP_NAME, name);
                }
                int groupNumber = 0;
                for (int i = 0; i < nameLength; i += CODEPOINT_LENGTH_ASCII) {
                    int d = codePointAtByteIndexNode.execute(name, i, TS_ENCODING);
                    if (isDecimalDigit(d)) {
                        groupNumber = (groupNumber * 10) + (d - '0');
                    } else {
                        groupNumber = -1;
                        break;
                    }
                    if (groupNumber >= numberOfCaptureGroups) {
                        throw raiseNode.raise(inliningTarget, PATTERN_ERROR, INVALID_GROUP_REFERENCE, groupNumber);
                    }
                }
                if (groupNumber < 0) {
                    Object namedCaptureGroups = TRegexUtil.TRegexCompiledRegexAccessor.namedCaptureGroups(tregexCompiledRegex, inliningTarget, readNamedGroupsNode);
                    if (!TRegexUtil.TRegexNamedCaptureGroupsAccessor.hasGroup(namedCaptureGroups, name, genericInteropLib)) {
                        throw raiseNode.raise(inliningTarget, PythonErrorType.IndexError, UNKNOWN_GROUP_NAME, name);
                    }
                    int[] groupNumbers = TRegexUtil.TRegexNamedCaptureGroupsAccessor.getGroupNumbers(namedCaptureGroups, name, genericInteropLib, genericInteropLib);
                    assert groupNumbers.length == 1 : Arrays.toString(groupNumbers);
                    groupNumber = groupNumbers[0];
                }
                consumer.groupReference(groupNumber);
                lastPos = nameEndPos + CODEPOINT_LENGTH_ASCII;
            } else if (firstCodepoint == '0') {
                int octalEscape;
                if (isOctalDigit(secondCodepoint)) {
                    octalEscape = (secondCodepoint - '0');
                    if (nextCPPos < length) {
                        int thirdCodepoint = codePointAtByteIndexNode.execute(replacement, nextCPPos, TS_ENCODING);
                        if (isOctalDigit(thirdCodepoint)) {
                            octalEscape = (octalEscape * 8) + (thirdCodepoint - '0');
                            nextCPPos += CODEPOINT_LENGTH_ASCII;
                        }
                    }
                } else {
                    octalEscape = 0;
                    nextCPPos -= CODEPOINT_LENGTH_ASCII;
                }
                consumer.codepoint(octalEscape);
                lastPos = nextCPPos;
            } else if (isDecimalDigit(firstCodepoint)) {

            }
        }
    }

    private static boolean isDecimalDigit(int d) {
        return '0' <= d && d <= '9';
    }

    private static boolean isOctalDigit(int d) {
        return '0' <= d && d <= '7';
    }

}
