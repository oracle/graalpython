/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.modules.re;

import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.buffer.PythonBufferAccessLibrary;
import com.oracle.graal.python.builtins.objects.buffer.PythonBufferAcquireLibrary;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.util.CannotCastException;
import com.oracle.graal.python.nodes.util.CastToTruffleStringNode;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Idempotent;
import com.oracle.truffle.api.interop.ExceptionType;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.api.strings.TruffleString;
import org.graalvm.collections.EconomicMap;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

import static com.oracle.graal.python.runtime.exception.PythonErrorType.TypeError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.ValueError;
import static com.oracle.graal.python.util.PythonUtils.TS_ENCODING;
import static com.oracle.graal.python.util.PythonUtils.tsLiteral;

public final class TRegexCache {

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
    private static final TruffleString T_VALUE_ERROR_UNICODE_FLAG_BYTES_PATTERN = tsLiteral("cannot use UNICODE flag with a bytes pattern");
    private static final TruffleString T_VALUE_ERROR_LOCALE_FLAG_STR_PATTERN = tsLiteral("cannot use LOCALE flag with a str pattern");
    private static final TruffleString T_VALUE_ERROR_ASCII_UNICODE_INCOMPATIBLE = tsLiteral("ASCII and UNICODE flags are incompatible");
    private static final TruffleString T_VALUE_ERROR_ASCII_LOCALE_INCOMPATIBLE = tsLiteral("ASCII and LOCALE flags are incompatible");

    public static final int FLAG_IGNORECASE = 2;
    public static final int FLAG_LOCALE = 4;
    public static final int FLAG_MULTILINE = 8;
    public static final int FLAG_DOTALL = 16;
    public static final int FLAG_UNICODE = 32;
    public static final int FLAG_VERBOSE = 64;
    public static final int FLAG_ASCII = 256;

    @CompilerDirectives.TruffleBoundary
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

    public Object getPattern() {
        return pattern;
    }

    public String getFlags() {
        return flags;
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
     * Tests whether the regex is locale-sensitive. It is not completely precise. In some instances,
     * it will return {@code true} even though the regex is *not* locale-sensitive. This is the case
     * when sequences resembling inline flags appear in character classes or comments.
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

    @CompilerDirectives.TruffleBoundary
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

    @CompilerDirectives.TruffleBoundary
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

    @CompilerDirectives.TruffleBoundary
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
            throw handleCompilationError(node, e, lib);
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
    private RuntimeException handleCompilationError(Node node, RuntimeException e, InteropLibrary lib) {
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
                        return PatternBuiltins.RaiseRegexErrorNode.executeWithPatternAndPositionUncached(reason, originalPattern, position, node);
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
