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
package com.oracle.graal.python.builtins.objects.str;

import static com.oracle.graal.python.util.PythonUtils.TS_ENCODING;
import static com.oracle.truffle.api.CompilerDirectives.shouldNotReachHere;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

import org.graalvm.nativeimage.ImageInfo;

import org.graalvm.shadowed.com.ibm.icu.lang.UCharacter;
import org.graalvm.shadowed.com.ibm.icu.lang.UCharacterCategory;
import org.graalvm.shadowed.com.ibm.icu.lang.UProperty;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.strings.TruffleString;
import com.oracle.truffle.api.strings.TruffleStringBuilder;
import com.oracle.truffle.api.strings.TruffleStringBuilder.AppendCodePointNode;
import com.oracle.truffle.api.strings.TruffleStringBuilder.AppendLongNumberNode;
import com.oracle.truffle.api.strings.TruffleStringBuilder.AppendStringNode;
import com.oracle.truffle.api.strings.TruffleStringIterator;
import com.oracle.truffle.regex.chardata.UnicodeCharacterAliases;

public final class StringUtils {

    /**
     * The maximum length of the source string when creating a sing-codepoint substring.
     */
    public static final int LAZY_CODEPOINT_THRESHOLD = 20;

    public enum StripKind {
        LEFT,
        RIGHT,
        BOTH
    }

    /**
     * corresponds to {@code unicodeobject.c:_Py_ascii_whitespace}
     */
    private static final int[] ASCII_WHITESPACE = {
                    0, 0, 0, 0, 0, 0, 0, 0,
                    /* case 0x0009: * CHARACTER TABULATION */
                    /* case 0x000A: * LINE FEED */
                    /* case 0x000B: * LINE TABULATION */
                    /* case 0x000C: * FORM FEED */
                    /* case 0x000D: * CARRIAGE RETURN */
                    0, 1, 1, 1, 1, 1, 0, 0,
                    0, 0, 0, 0, 0, 0, 0, 0,
                    /* case 0x001C: * FILE SEPARATOR */
                    /* case 0x001D: * GROUP SEPARATOR */
                    /* case 0x001E: * RECORD SEPARATOR */
                    /* case 0x001F: * UNIT SEPARATOR */
                    0, 0, 0, 0, 1, 1, 1, 1,
                    /* case 0x0020: * SPACE */
                    1, 0, 0, 0, 0, 0, 0, 0,
                    0, 0, 0, 0, 0, 0, 0, 0,
                    0, 0, 0, 0, 0, 0, 0, 0,
                    0, 0, 0, 0, 0, 0, 0, 0,

                    0, 0, 0, 0, 0, 0, 0, 0,
                    0, 0, 0, 0, 0, 0, 0, 0,
                    0, 0, 0, 0, 0, 0, 0, 0,
                    0, 0, 0, 0, 0, 0, 0, 0,
                    0, 0, 0, 0, 0, 0, 0, 0,
                    0, 0, 0, 0, 0, 0, 0, 0,
                    0, 0, 0, 0, 0, 0, 0, 0,
                    0, 0, 0, 0, 0, 0, 0, 0
    };

    public static boolean isUnicodeWhitespace(int ch) {
        switch (ch) {
            case 0x0009:
            case 0x000A:
            case 0x000B:
            case 0x000C:
            case 0x000D:
            case 0x001C:
            case 0x001D:
            case 0x001E:
            case 0x001F:
            case 0x0020:
            case 0x0085:
            case 0x00A0:
            case 0x1680:
            case 0x2000:
            case 0x2001:
            case 0x2002:
            case 0x2003:
            case 0x2004:
            case 0x2005:
            case 0x2006:
            case 0x2007:
            case 0x2008:
            case 0x2009:
            case 0x200A:
            case 0x2028:
            case 0x2029:
            case 0x202F:
            case 0x205F:
            case 0x3000:
                return true;
            default:
                return false;
        }
    }

    public static boolean isUnicodeLineBreak(char ch) {
        switch (ch) {
            case 0x000A:
            case 0x000B:
            case 0x000C:
            case 0x000D:
            case 0x001C:
            case 0x001D:
            case 0x001E:
            case 0x0085:
            case 0x2028:
            case 0x2029:
                return true;
            default:
                return false;
        }
    }

    public static boolean isSpace(int ch) {
        if (ch < 128) {
            return ASCII_WHITESPACE[ch] == 1;
        }
        return isUnicodeWhitespace(ch);
    }

    public static TruffleString strip(TruffleString str, StripKind stripKind, TruffleString.CodePointLengthNode codePointLengthNode, TruffleString.CodePointAtIndexNode codePointAtIndexNode,
                    TruffleString.SubstringNode substringNode) {
        int i = 0;
        int len = codePointLengthNode.execute(str, TS_ENCODING);
        if (stripKind != StripKind.RIGHT) {
            while (i < len) {
                int cp = codePointAtIndexNode.execute(str, i, TS_ENCODING);
                if (!isSpace(cp)) {
                    break;
                }
                i++;
            }
        }

        int j = len;
        if (stripKind != StripKind.LEFT) {
            j--;
            while (j >= i) {
                int cp = codePointAtIndexNode.execute(str, j, TS_ENCODING);
                if (!isSpace(cp)) {
                    break;
                }
                j--;
            }
            j++;
        }

        return substringNode.execute(str, i, j - i, TS_ENCODING, false);
    }

    public static TruffleString strip(TruffleString str, TruffleString chars, StripKind stripKind, TruffleString.CodePointLengthNode codePointLengthNode,
                    TruffleString.CodePointAtIndexNode codePointAtIndexNode, TruffleString.IndexOfCodePointNode indexOfCodePointNode, TruffleString.SubstringNode substringNode) {
        int i = 0;
        int len = codePointLengthNode.execute(str, TS_ENCODING);
        int charsLen = codePointLengthNode.execute(chars, TS_ENCODING);
        // TODO: cpython uses a bloom filter for to skip chars that are not in the sep list:
        // to avoid the linear search in chars
        if (stripKind != StripKind.RIGHT) {
            while (i < len) {
                int cp = codePointAtIndexNode.execute(str, i, TS_ENCODING);
                if (indexOfCodePointNode.execute(chars, cp, 0, charsLen, TS_ENCODING) < 0) {
                    break;
                }
                i++;
            }
        }

        int j = len;
        if (stripKind != StripKind.LEFT) {
            j--;
            while (j >= i) {
                int cp = codePointAtIndexNode.execute(str, j, TS_ENCODING);
                if (indexOfCodePointNode.execute(chars, cp, 0, charsLen, TS_ENCODING) < 0) {
                    break;
                }
                j--;
            }
            j++;
        }

        return substringNode.execute(str, i, j - i, TS_ENCODING, false);
    }

    public static Object[] toCharacterArray(TruffleString arg, TruffleString.CodePointLengthNode codePointLengthNode, TruffleString.CreateCodePointIteratorNode createCodePointIteratorNode,
                    TruffleStringIterator.NextNode nextNode, TruffleString.FromCodePointNode fromCodePointNode) {
        Object[] values = new Object[codePointLengthNode.execute(arg, TS_ENCODING)];
        TruffleStringIterator it = createCodePointIteratorNode.execute(arg, TS_ENCODING);
        int i = 0;
        while (it.hasNext()) {
            // TODO: GR-37219: use SubstringNode with lazy=true?
            int codePoint = nextNode.execute(it);
            values[i++] = fromCodePointNode.execute(codePoint, TS_ENCODING, true);
        }
        return values;
    }

    @TruffleBoundary
    public static boolean isPrintable(int codepoint) {
        if (ImageInfo.inImageBuildtimeCode()) {
            // Executing ICU4J at image build time causes issues with runtime/build time
            // initialization
            assert codepoint < 0x100;
            return codepoint >= 32;
        }
        return isPrintableICU(codepoint);
    }

    @TruffleBoundary
    private static boolean isPrintableICU(int codepoint) {
        // ICU's definition of printability is different from CPython, so we cannot use
        // UCharacter.isPrintable
        int category = UCharacter.getType(codepoint);
        switch (category) {
            case UCharacterCategory.CONTROL:
            case UCharacterCategory.FORMAT:
            case UCharacterCategory.SURROGATE:
            case UCharacterCategory.PRIVATE_USE:
            case UCharacterCategory.UNASSIGNED:
            case UCharacterCategory.LINE_SEPARATOR:
            case UCharacterCategory.PARAGRAPH_SEPARATOR:
                return false;
            case UCharacterCategory.SPACE_SEPARATOR:
                return codepoint == ' ';
        }
        return true;
    }

    @TruffleBoundary
    public static String toLowerCase(String self) {
        if (ImageInfo.inImageBuildtimeCode()) {
            // Avoid initializing ICU4J in image build
            return self.toLowerCase();
        }
        return UCharacter.toLowerCase(Locale.ROOT, self);
    }

    @TruffleBoundary
    public static String toUpperCase(String str) {
        if (ImageInfo.inImageBuildtimeCode()) {
            // Avoid initializing ICU4J in image build
            return str.toUpperCase();
        }
        return UCharacter.toUpperCase(Locale.ROOT, str);
    }

    @TruffleBoundary
    public static boolean isAlnum(int codePoint) {
        if (ImageInfo.inImageBuildtimeCode()) {
            // Avoid initializing ICU4J in image build
            return Character.isLetterOrDigit(codePoint);
        }
        return isAlnumICU(codePoint);
    }

    private static boolean isAlnumICU(int codePoint) {
        if (UCharacter.isLetter(codePoint) || UCharacter.isDigit(codePoint) || UCharacter.hasBinaryProperty(codePoint, UProperty.NUMERIC_TYPE)) {
            return true;
        }
        int numericType = UCharacter.getIntPropertyValue(codePoint, UProperty.NUMERIC_TYPE);
        return numericType == UCharacter.NumericType.DECIMAL || numericType == UCharacter.NumericType.DIGIT || numericType == UCharacter.NumericType.NUMERIC;
    }

    @TruffleBoundary(allowInlining = true)
    public static String toString(StringBuilder sb) {
        return sb.toString();
    }

    public static int compareStrings(TruffleString a, TruffleString b, TruffleString.CompareIntsUTF32Node compareIntsUTF32Node) {
        assert TS_ENCODING == TruffleString.Encoding.UTF_32;
        return compareIntsUTF32Node.execute(a, b);
    }

    @TruffleBoundary(allowInlining = true)
    public static int compareStringsUncached(TruffleString a, TruffleString b) {
        return compareStrings(a, b, TruffleString.CompareIntsUTF32Node.getUncached());
    }

    /**
     * Python identifiers are defined to start with an XID_Start or '_' character, followed by any
     * number of XID_Continue characters. Python keywords are not treated in a special way, so they
     * are identifiers as well.
     */
    @GenerateUncached
    public abstract static class IsIdentifierNode extends Node {

        public abstract boolean execute(TruffleString str);

        @Specialization
        boolean doString(TruffleString str,
                        @Cached TruffleString.CreateCodePointIteratorNode createCodePointIteratorNode,
                        @Cached TruffleStringIterator.NextNode nextNode) {
            if (str.isEmpty()) {
                return false;
            }
            TruffleStringIterator it = createCodePointIteratorNode.execute(str, TS_ENCODING);
            int c = nextNode.execute(it);
            if (c != '_' && !hasProperty(c, UProperty.XID_START)) {
                return false;
            }
            while (it.hasNext()) {
                c = nextNode.execute(it);
                if (!hasProperty(c, UProperty.XID_CONTINUE)) {
                    return false;
                }
            }
            return true;
        }

        @TruffleBoundary
        static boolean hasProperty(int codePoint, int property) {
            return UCharacter.hasBinaryProperty(codePoint, property);
        }

        public static IsIdentifierNode getUncached() {
            return StringUtilsFactory.IsIdentifierNodeGen.getUncached();
        }
    }

    @TruffleBoundary
    public static boolean isIdentifierUncached(TruffleString value) {
        return IsIdentifierNode.getUncached().execute(value);
    }

    @TruffleBoundary
    public static TruffleString joinUncached(TruffleString delimiter, Iterable<TruffleString> values) {
        Iterator<TruffleString> it = values.iterator();
        if (!it.hasNext()) {
            return TS_ENCODING.getEmpty();
        }
        TruffleString first = it.next();
        if (!it.hasNext()) {
            return first;
        }
        TruffleStringBuilder sb = TruffleStringBuilder.create(TS_ENCODING);
        sb.appendStringUncached(first);
        while (it.hasNext()) {
            sb.appendStringUncached(delimiter);
            sb.appendStringUncached(it.next());
        }
        return sb.toStringUncached();
    }

    public static TruffleString[] split(TruffleString s, TruffleString sep, TruffleString.CodePointLengthNode codePointLengthNode, TruffleString.IndexOfStringNode indexOfStringNode,
                    TruffleString.SubstringNode substringNode, TruffleString.EqualNode eqNode) {
        int selfLen = codePointLengthNode.execute(s, TS_ENCODING);
        int lastEnd = 0;
        int sepLen = codePointLengthNode.execute(sep, TS_ENCODING);
        if (selfLen == sepLen && eqNode.execute(s, sep, TS_ENCODING)) {
            return PythonUtils.EMPTY_TRUFFLESTRING_ARRAY;
        }
        List<TruffleString> l = new ArrayList<>();
        while (lastEnd < selfLen) {
            int nextIndex = indexOfStringNode.execute(s, sep, lastEnd, selfLen, TS_ENCODING);
            if (nextIndex < 0) {
                break;
            }
            add(l, substringNode.execute(s, lastEnd, nextIndex - lastEnd, TS_ENCODING, false));
            lastEnd = nextIndex + sepLen;
        }
        add(l, substringNode.execute(s, lastEnd, selfLen - lastEnd, TS_ENCODING, false));
        return l.toArray(new TruffleString[l.size()]);
    }

    @TruffleBoundary
    private static void add(List<TruffleString> l, TruffleString s) {
        l.add(s);
    }

    @TruffleBoundary
    public static TruffleString cat(TruffleString arg1, TruffleString arg2) {
        return arg1.concatUncached(arg2, TS_ENCODING, false);
    }

    @TruffleBoundary
    public static TruffleString cat(TruffleString... args) {
        if (args.length == 2) {
            return args[0].concatUncached(args[1], TS_ENCODING, false);
        }
        TruffleStringBuilder sb = TruffleStringBuilder.create(TS_ENCODING);
        for (TruffleString arg : args) {
            sb.appendStringUncached(arg);
        }
        return sb.toStringUncached();
    }

    /**
     * Helper node for formatting simple strings, e.g. in {@code __repr__}. Basically just a
     * convenient way of using {@link TruffleStringBuilder}. The only supported directives are:
     * <ul>
     * <li>{@code %%} - replaced by a single {@code '%'}</li>
     * <li>{@code %s} - {@linkplain AppendStringNode replaced} by the next argument, which must be a
     * {@link TruffleString} or {@link String}</li>
     * <li>{@code %c} - {@linkplain AppendCodePointNode replaced} by a single codepoint, the
     * argument must be an {@code int} or {@code char}</li>
     * <li>{@code %d} - {@linkplain AppendLongNumberNode replaced} by the decimal representation of
     * the next argument, which must be an <code>int</code> or <code>long</code></li>
     * </ul>
     */
    @GenerateUncached
    public abstract static class SimpleTruffleStringFormatNode extends Node {

        public final TruffleString format(TruffleString format, Object... args) {
            return executeInternal(format, args);
        }

        public final TruffleString format(String format, Object... args) {
            return executeInternal(format, args);
        }

        abstract TruffleString executeInternal(TruffleString format, Object[] args);

        abstract TruffleString executeInternal(String format, Object[] args);

        @Specialization
        TruffleString doTruffleString(TruffleString format, Object[] args,
                        @Shared("sbToString") @Cached TruffleStringBuilder.ToStringNode toStringNode,
                        @Shared("appendSubstr") @Cached TruffleStringBuilder.AppendSubstringByteIndexNode appendSubstringByteIndexNode,
                        @Shared("appendCP") @Cached TruffleStringBuilder.AppendCodePointNode appendCodePointNode,
                        @Shared("appendStr") @Cached TruffleStringBuilder.AppendStringNode appendStringNode,
                        @Shared("appendLong") @Cached TruffleStringBuilder.AppendLongNumberNode appendLongNumberNode,
                        @Shared("byteIndexOfCodePoint") @Cached TruffleString.ByteIndexOfCodePointNode byteIndexOfCodePointNode,
                        @Shared("cpAtByteIndex") @Cached TruffleString.CodePointAtByteIndexNode codePointAtByteIndexNode,
                        @Shared("byteLenOfCP") @Cached TruffleString.ByteLengthOfCodePointNode byteLengthOfCodePointNode,
                        @Shared("js2ts") @Cached TruffleString.FromJavaStringNode fromJavaStringNode) {
            TruffleStringBuilder sb = TruffleStringBuilder.create(TS_ENCODING);
            int i = 0;
            int len = format.byteLength(TS_ENCODING);
            int nextArg = 0;
            while (i < len) {
                int j = byteIndexOfCodePointNode.execute(format, '%', i, len, TS_ENCODING);
                if (j < 0) {
                    break;
                }
                appendSubstringByteIndexNode.execute(sb, format, i, j - i);
                i = j;
                i += byteLengthOfCodePointNode.execute(format, i, TS_ENCODING);
                if (i >= len) {
                    throw shouldNotReachHere("Truncated format directive");
                }
                int c = codePointAtByteIndexNode.execute(format, i, TS_ENCODING);
                i += byteLengthOfCodePointNode.execute(format, i, TS_ENCODING);
                switch (c) {
                    case '%':
                        appendCodePointNode.execute(sb, '%', 1, true);
                        break;
                    case 's':
                        appendStringNode.execute(sb, asString(fetchArg(args, nextArg++), fromJavaStringNode));
                        break;
                    case 'c':
                        appendCodePointNode.execute(sb, asCodePoint(fetchArg(args, nextArg++)), 1, true);
                        break;
                    case 'd':
                        appendLongNumberNode.execute(sb, asLong(fetchArg(args, nextArg++)));
                        break;
                    default:
                        throw shouldNotReachHere("Invalid format directive");
                }
            }
            if (nextArg != args.length) {
                throw shouldNotReachHere("Extra unprocessed arguments");
            }
            if (i < len) {
                appendSubstringByteIndexNode.execute(sb, format, i, len - i);
            }
            return toStringNode.execute(sb);
        }

        @Specialization
        TruffleString doJavaString(String format, Object[] args,
                        @Shared("sbToString") @Cached TruffleStringBuilder.ToStringNode toStringNode,
                        @Shared("appendSubstr") @Cached TruffleStringBuilder.AppendSubstringByteIndexNode appendSubstringByteIndexNode,
                        @Shared("appendCP") @Cached TruffleStringBuilder.AppendCodePointNode appendCodePointNode,
                        @Shared("appendStr") @Cached TruffleStringBuilder.AppendStringNode appendStringNode,
                        @Shared("appendLong") @Cached TruffleStringBuilder.AppendLongNumberNode appendLongNumberNode,
                        @Shared("byteIndexOfCodePoint") @Cached TruffleString.ByteIndexOfCodePointNode byteIndexOfCodePointNode,
                        @Shared("cpAtByteIndex") @Cached TruffleString.CodePointAtByteIndexNode codePointAtByteIndexNode,
                        @Shared("byteLenOfCP") @Cached TruffleString.ByteLengthOfCodePointNode byteLengthOfCodePointNode,
                        @Shared("js2ts") @Cached TruffleString.FromJavaStringNode fromJavaStringNode) {
            return doTruffleString(fromJavaStringNode.execute(format, TS_ENCODING), args, toStringNode, appendSubstringByteIndexNode, appendCodePointNode, appendStringNode, appendLongNumberNode,
                            byteIndexOfCodePointNode, codePointAtByteIndexNode, byteLengthOfCodePointNode, fromJavaStringNode);
        }

        private static Object fetchArg(Object[] args, int nextArg) {
            if (nextArg >= args.length) {
                throw shouldNotReachHere("Not enough arguments for formatting");
            }
            return args[nextArg];
        }

        private static TruffleString asString(Object arg, TruffleString.FromJavaStringNode fromJavaStringNode) {
            if (arg instanceof TruffleString) {
                return (TruffleString) arg;
            }
            if (arg instanceof String) {
                return fromJavaStringNode.execute((String) arg, TS_ENCODING);
            }
            throw shouldNotReachHere("Expected a string argument");
        }

        private static long asLong(Object arg) {
            if (arg instanceof Long) {
                return (Long) arg;
            }
            if (arg instanceof Integer) {
                return (Integer) arg;
            }
            throw shouldNotReachHere("Expected an int or long argument");
        }

        private static int asCodePoint(Object arg) {
            if (arg instanceof Integer) {
                return (Integer) arg;
            }
            if (arg instanceof Character) {
                return (Character) arg;
            }
            throw shouldNotReachHere("Expected an int or char argument");
        }

        public static SimpleTruffleStringFormatNode getUncached() {
            return StringUtilsFactory.SimpleTruffleStringFormatNodeGen.getUncached();
        }
    }

    @TruffleBoundary
    public static TruffleString simpleTruffleStringFormatUncached(TruffleString format, Object... args) {
        return SimpleTruffleStringFormatNode.getUncached().format(format, args);
    }

    @TruffleBoundary
    public static TruffleString simpleTruffleStringFormatUncached(String format, Object... args) {
        return SimpleTruffleStringFormatNode.getUncached().format(format, args);
    }

    @TruffleBoundary
    public static int getCodePoint(String characterName) {
        // CPython's logic for resolving these character names goes like this:
        // 1) handle Hangul Syllables in region AC00-D7A3
        // 2) handle CJK Ideographs
        // 3) handle character names as given in UnicodeData.txt
        // 4) handle all aliases as given in NameAliases.txt
        // With ICU's UCharacter, we get cases 1), 2) and 3). As for 4), the aliases, ICU only
        // handles aliases of type 'correction'. Therefore, we extract the contents of
        // NameAliases.txt and handle aliases by ourselves.
        String normalizedName = characterName.trim().toUpperCase(Locale.ROOT);
        if (UnicodeCharacterAliases.CHARACTER_ALIASES.containsKey(normalizedName)) {
            return UnicodeCharacterAliases.CHARACTER_ALIASES.get(normalizedName);
        } else {
            return UCharacter.getCharFromName(characterName);
        }
    }
}
