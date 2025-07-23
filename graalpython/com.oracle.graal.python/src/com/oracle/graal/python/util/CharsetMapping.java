/*
 * Copyright (c) 2020, 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.util;

import static com.oracle.graal.python.util.PythonUtils.TS_ENCODING;
import static com.oracle.graal.python.util.PythonUtils.toTruffleStringUncached;
import static com.oracle.graal.python.util.PythonUtils.tsLiteral;

import java.nio.ByteOrder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.charset.UnsupportedCharsetException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.graalvm.shadowed.com.ibm.icu.charset.CharsetICU;

import com.oracle.graal.python.charset.PythonRawUnicodeEscapeCharset;
import com.oracle.graal.python.charset.PythonUTF32CharsetWrapper;
import com.oracle.graal.python.charset.PythonUnicodeEscapeCharset;
import com.oracle.graal.python.util.CharsetMappingFactory.NormalizeEncodingNameNodeGen;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.strings.TruffleString;
import com.oracle.truffle.api.strings.TruffleStringBuilder;
import com.oracle.truffle.api.strings.TruffleStringIterator;

/**
 * Utility class for mapping Python encodings to Java charsets
 */
public class CharsetMapping {
    private static final Charset UTF_32LE = new PythonUTF32CharsetWrapper(Charset.forName("UTF-32LE"), ByteOrder.LITTLE_ENDIAN);
    private static final Charset UTF_32LE_BOM = new PythonUTF32CharsetWrapper(Charset.forName("UTF-32LE-BOM"), ByteOrder.LITTLE_ENDIAN);
    private static final Charset UTF_32BE = new PythonUTF32CharsetWrapper(Charset.forName("UTF-32BE"), ByteOrder.BIG_ENDIAN);
    private static final Charset UTF_32BE_BOM = new PythonUTF32CharsetWrapper(Charset.forName("UTF-32BE-BOM"), ByteOrder.BIG_ENDIAN);
    private static final ConcurrentMap<String, Charset> JAVA_CHARSETS = new ConcurrentHashMap<>();
    // Name maps are populated by static initializer and are immutable afterwards
    private static final Map<TruffleString, String> CHARSET_NAME_MAP = new HashMap<>();
    private static final Map<String, TruffleString> CHARSET_NAME_MAP_REVERSE = new HashMap<>();
    private static final TruffleString T_UTF_16_UNDERSCORE = tsLiteral("utf_16");
    private static final TruffleString T_UTF_32_UNDERSCORE = tsLiteral("utf_32");

    @TruffleBoundary
    public static Charset getCharsetNormalized(TruffleString normalizedEncoding) {
        String name = CHARSET_NAME_MAP.get(normalizedEncoding);
        if (name != null) {
            return getJavaCharset(name);
        }
        return null;
    }

    @TruffleBoundary
    public static Charset getCharsetForDecodingNormalized(TruffleString normalizedEncoding, byte[] bytes, int len) {
        if (ByteOrder.nativeOrder() == ByteOrder.LITTLE_ENDIAN) {
            /*
             * JDK's charsets for UTF-16 and UTF-32 default to big endian irrespective of the
             * platform if there is no BOM. The UTF-16-LE and UTF-32-LE charsets reject big endian
             * BOM. CPython defaults to platform endian and accepts both BOMs. So, in order to get
             * the behavior we need, we have to take a peek at the possible BOM and if it has a BOM
             * use the UTF-16/32 encoding and let it detect, otherwise default to UTF-16/32-LE.
             */
            if (T_UTF_16_UNDERSCORE.equalsUncached(normalizedEncoding, TS_ENCODING) && hasUTF16BOM(bytes, len)) {
                return StandardCharsets.UTF_16;
            } else if (T_UTF_32_UNDERSCORE.equalsUncached(normalizedEncoding, TS_ENCODING)) {
                Charset charset = getUTF32CharsetForBOM(bytes, len);
                if (charset != null) {
                    return charset;
                }
            }
        }
        String name = CHARSET_NAME_MAP.get(normalizedEncoding);
        if (name != null) {
            return getJavaCharset(name);
        }
        return null;
    }

    private static boolean hasUTF16BOM(byte[] bytes, int len) {
        if (len < 2) {
            return false;
        }
        short head = PythonUtils.ARRAY_ACCESSOR.getShort(bytes, 0);
        return head == (short) 0xFFFE || head == (short) 0xFEFF;
    }

    private static Charset getUTF32CharsetForBOM(byte[] bytes, int len) {
        if (len < 4) {
            return null;
        }
        int head = PythonUtils.ARRAY_ACCESSOR.getInt(bytes, 0);
        if (head == 0xFFFE0000) {
            return UTF_32BE_BOM;
        }
        if (head == 0x0000FEFF) {
            return UTF_32LE_BOM;
        }
        return null;
    }

    @TruffleBoundary
    public static TruffleString getPythonEncodingNameFromJavaName(String javaEncodingName) {
        return CHARSET_NAME_MAP_REVERSE.get(javaEncodingName.toLowerCase());
    }

    @GenerateUncached
    @GenerateInline
    @GenerateCached(false)
    public abstract static class NormalizeEncodingNameNode extends Node {
        public abstract TruffleString execute(Node inliningTarget, TruffleString encoding);

        @Specialization
        static TruffleString normalize(TruffleString encoding,
                        @Cached(inline = false) TruffleString.CreateCodePointIteratorNode createCodePointIteratorNode,
                        @Cached(inline = false) TruffleStringIterator.NextNode nextNode,
                        @Cached(inline = false) TruffleStringBuilder.AppendCodePointNode appendCodePointNode,
                        @Cached(inline = false) TruffleStringBuilder.ToStringNode toStringNode) {
            TruffleStringBuilder str = TruffleStringBuilder.create(TS_ENCODING, encoding.byteLength(TS_ENCODING));
            boolean lastCharInvalid = false;
            TruffleStringIterator it = createCodePointIteratorNode.execute(encoding, TS_ENCODING);
            while (it.hasNext()) {
                int c = nextNode.execute(it, TS_ENCODING);
                if ((c >= 'A' && c <= 'Z')) {
                    appendCodePointNode.execute(str, c - 'A' + 'a', 1, true);
                    lastCharInvalid = false;
                } else if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '.' || c == '_') {
                    appendCodePointNode.execute(str, c, 1, true);
                    lastCharInvalid = false;
                } else {
                    if (!lastCharInvalid) {
                        appendCodePointNode.execute(str, '_', 1, true);
                        lastCharInvalid = true;
                    }
                }
            }
            return toStringNode.execute(str);
        }
    }

    @TruffleBoundary
    public static TruffleString normalizeUncached(TruffleString encoding) {
        return NormalizeEncodingNameNodeGen.getUncached().execute(null, encoding);
    }

    public static Charset getJavaCharset(String name) {
        return JAVA_CHARSETS.computeIfAbsent(name, key -> {
            // Important: When adding additional ICU4J charset, the implementation class needs to be
            // added to reflect-config.json
            if (name.equals("UTF-7") || name.equals("HZ")) {
                try {
                    return CharsetICU.forNameICU(name);
                } catch (UnsupportedCharsetException e) {
                    return null;
                }
            } else {
                try {
                    return Charset.forName(name);
                } catch (UnsupportedCharsetException e) {
                    return null;
                }
            }
        });
    }

    private static void addMapping(String pythonName, String javaName) {
        TruffleString normalized = normalizeUncached(toTruffleStringUncached(pythonName));
        CHARSET_NAME_MAP.put(normalized, javaName);
        if (javaName != null) {
            CHARSET_NAME_MAP_REVERSE.put(javaName.toLowerCase(), toTruffleStringUncached(pythonName.replace('_', '-')));
        }
    }

    private static void addAlias(String alias, String pythonName) {
        TruffleString normalized = normalizeUncached(toTruffleStringUncached(pythonName));
        assert CHARSET_NAME_MAP.containsKey(normalized) : normalized;
        CHARSET_NAME_MAP.put(normalizeUncached(toTruffleStringUncached(alias)), CHARSET_NAME_MAP.get(normalized));
    }

    static {
        // Pre-initialize standard charset entries
        JAVA_CHARSETS.put("US-ASCII", StandardCharsets.US_ASCII);
        JAVA_CHARSETS.put("ISO-8859-1", StandardCharsets.ISO_8859_1);
        JAVA_CHARSETS.put("UTF-8", StandardCharsets.UTF_8);
        JAVA_CHARSETS.put("UTF-16BE", StandardCharsets.UTF_16BE);
        JAVA_CHARSETS.put("UTF-16LE", StandardCharsets.UTF_16LE);
        JAVA_CHARSETS.put("UTF-16", ByteOrder.nativeOrder() == ByteOrder.LITTLE_ENDIAN ? Charset.forName("UnicodeLittle") : StandardCharsets.UTF_16);
        JAVA_CHARSETS.put("UTF-32BE", UTF_32BE);
        JAVA_CHARSETS.put("UTF-32LE", UTF_32LE);
        JAVA_CHARSETS.put("UTF-32", ByteOrder.nativeOrder() == ByteOrder.LITTLE_ENDIAN ? UTF_32LE_BOM : UTF_32BE_BOM);

        // Add our custom charsets
        addMapping("raw_unicode_escape", "x-python-raw-unicode-escape");
        addMapping("unicode-escape", "x-python-unicode-escape");
        addMapping("unicodeescape", "x-python-unicode-escape");
        JAVA_CHARSETS.put("x-python-raw-unicode-escape", new PythonRawUnicodeEscapeCharset());
        JAVA_CHARSETS.put("x-python-unicode-escape", new PythonUnicodeEscapeCharset());

        addMapping("ascii", "US-ASCII");
        addMapping("big5hkscs", "Big5-HKSCS");
        addMapping("big5", "Big5");
        addMapping("cp037", "IBM037");
        addMapping("cp1006", "x-IBM1006");
        addMapping("cp1026", "IBM1026");
        addMapping("cp1125", null);
        addMapping("cp1140", "IBM01140");
        addMapping("cp1250", "windows-1250");
        addMapping("cp1251", "windows-1251");
        addMapping("cp1252", "windows-1252");
        addMapping("cp1253", "windows-1253");
        addMapping("cp1254", "windows-1254");
        addMapping("cp1255", "windows-1255");
        addMapping("cp1256", "windows-1256");
        addMapping("cp1257", "windows-1257");
        addMapping("cp1258", "windows-1258");
        addMapping("cp273", "IBM273");
        addMapping("cp424", "IBM424");
        addMapping("cp437", "IBM437");
        addMapping("cp500", "IBM500");
        addMapping("cp720", null);
        addMapping("cp737", "x-IBM737");
        addMapping("cp775", "IBM775");
        addMapping("cp850", "IBM850");
        addMapping("cp852", "IBM852");
        addMapping("cp855", "IBM855");
        addMapping("cp856", "x-IBM856");
        addMapping("cp857", "IBM857");
        addMapping("cp858", "IBM00858");
        addMapping("cp860", "IBM860");
        addMapping("cp861", "IBM861");
        addMapping("cp862", "IBM862");
        addMapping("cp863", "IBM863");
        addMapping("cp864", "IBM864");
        addMapping("cp865", "IBM865");
        addMapping("cp866", "IBM866");
        addMapping("cp869", "IBM869");
        addMapping("cp874", "x-IBM874");
        addMapping("cp875", "x-IBM875");
        addMapping("cp932", "windows-31j");
        addMapping("cp949", "x-windows-949");
        addMapping("cp950", "x-IBM950");
        addMapping("euc_jis_2004", null);
        addMapping("euc_jisx0213", null);
        addMapping("euc_jp", "EUC-JP");
        addMapping("euc_kr", "EUC-KR");
        addMapping("gb18030", "GB18030");
        addMapping("gb2312", "GB2312");
        addMapping("gbk", "GBK");
        addMapping("hp_roman8", null);
        addMapping("hz", "HZ");
        addMapping("iso2022_jp_1", null);
        addMapping("iso2022_jp_2004", null);
        addMapping("iso2022_jp_2", "ISO-2022-JP-2");
        addMapping("iso2022_jp_3", null);
        addMapping("iso2022_jp_ext", null);
        addMapping("iso2022_jp", "ISO-2022-JP");
        addMapping("iso2022_kr", "ISO-2022-KR");
        addMapping("iso8859_10", null);
        addMapping("iso8859_11", "x-iso-8859-11");
        addMapping("iso8859_13", "ISO-8859-13");
        addMapping("iso8859_14", null);
        addMapping("iso8859_15", "ISO-8859-15");
        addMapping("iso8859_16", null);
        addMapping("iso8859_1", "ISO-8859-1");
        addMapping("iso8859_2", "ISO-8859-2");
        addMapping("iso8859_3", "ISO-8859-3");
        addMapping("iso8859_4", "ISO-8859-4");
        addMapping("iso8859_5", "ISO-8859-5");
        addMapping("iso8859_6", "ISO-8859-6");
        addMapping("iso8859_7", "ISO-8859-7");
        addMapping("iso8859_8", "ISO-8859-8");
        addMapping("iso8859_9", "ISO-8859-9");
        addMapping("johab", "x-Johab");
        addMapping("koi8_r", "KOI8-R");
        addMapping("koi8_t", null);
        addMapping("koi8_u", "KOI8-U");
        addMapping("kz1048", null);
        addMapping("latin_1", "ISO-8859-1");
        addMapping("mac_arabic", "x-MacArabic");
        addMapping("mac_centeuro", "x-MacCentralEurope");
        addMapping("mac_croatian", "x-MacCroatian");
        addMapping("mac_cyrillic", "x-MacCyrillic");
        addMapping("mac_farsi", null);
        addMapping("mac_greek", "x-MacGreek");
        addMapping("mac_iceland", "x-MacIceland");
        addMapping("mac_latin2", "x-MacCentralEurope");
        addMapping("mac_romanian", "x-MacRomania");
        addMapping("mac_roman", "x-MacRoman");
        addMapping("mac_turkish", "x-MacTurkish");
        addMapping("palmos", null);
        addMapping("ptcp154", null);
        addMapping("shift_jis_2004", "Shift_JISX0213");
        addMapping("shift_jis", "Shift_JIS");
        addMapping("shift_jisx0213", "x-SJIS_0213");
        addMapping("utf_16_be", "UTF-16BE");
        addMapping("utf_16_le", "UTF-16LE");
        addMapping("utf_16", "UTF-16");
        addMapping("utf_32_be", "UTF-32BE");
        addMapping("utf_32_le", "UTF-32LE");
        addMapping("utf_32", "UTF-32");
        addMapping("utf_7", "UTF-7");
        addMapping("utf_8", "UTF-8");

        // Generated from encodings.aliases.aliases, removed non-language encodings like base64
        addAlias("646", "ascii");
        addAlias("ansi_x3.4_1968", "ascii");
        addAlias("ansi_x3_4_1968", "ascii");
        addAlias("ansi_x3.4_1986", "ascii");
        addAlias("cp367", "ascii");
        addAlias("csascii", "ascii");
        addAlias("ibm367", "ascii");
        addAlias("iso646_us", "ascii");
        addAlias("iso_646.irv_1991", "ascii");
        addAlias("iso_ir_6", "ascii");
        addAlias("us", "ascii");
        addAlias("us_ascii", "ascii");
        addAlias("big5_tw", "big5");
        addAlias("csbig5", "big5");
        addAlias("big5_hkscs", "big5hkscs");
        addAlias("hkscs", "big5hkscs");
        addAlias("037", "cp037");
        addAlias("csibm037", "cp037");
        addAlias("ebcdic_cp_ca", "cp037");
        addAlias("ebcdic_cp_nl", "cp037");
        addAlias("ebcdic_cp_us", "cp037");
        addAlias("ebcdic_cp_wt", "cp037");
        addAlias("ibm037", "cp037");
        addAlias("ibm039", "cp037");
        addAlias("1026", "cp1026");
        addAlias("csibm1026", "cp1026");
        addAlias("ibm1026", "cp1026");
        addAlias("1125", "cp1125");
        addAlias("ibm1125", "cp1125");
        addAlias("cp866u", "cp1125");
        addAlias("ruscii", "cp1125");
        addAlias("1140", "cp1140");
        addAlias("ibm1140", "cp1140");
        addAlias("1250", "cp1250");
        addAlias("windows_1250", "cp1250");
        addAlias("1251", "cp1251");
        addAlias("windows_1251", "cp1251");
        addAlias("1252", "cp1252");
        addAlias("windows_1252", "cp1252");
        addAlias("1253", "cp1253");
        addAlias("windows_1253", "cp1253");
        addAlias("1254", "cp1254");
        addAlias("windows_1254", "cp1254");
        addAlias("1255", "cp1255");
        addAlias("windows_1255", "cp1255");
        addAlias("1256", "cp1256");
        addAlias("windows_1256", "cp1256");
        addAlias("1257", "cp1257");
        addAlias("windows_1257", "cp1257");
        addAlias("1258", "cp1258");
        addAlias("windows_1258", "cp1258");
        addAlias("273", "cp273");
        addAlias("ibm273", "cp273");
        addAlias("csibm273", "cp273");
        addAlias("424", "cp424");
        addAlias("csibm424", "cp424");
        addAlias("ebcdic_cp_he", "cp424");
        addAlias("ibm424", "cp424");
        addAlias("437", "cp437");
        addAlias("cspc8codepage437", "cp437");
        addAlias("ibm437", "cp437");
        addAlias("500", "cp500");
        addAlias("csibm500", "cp500");
        addAlias("ebcdic_cp_be", "cp500");
        addAlias("ebcdic_cp_ch", "cp500");
        addAlias("ibm500", "cp500");
        addAlias("775", "cp775");
        addAlias("cspc775baltic", "cp775");
        addAlias("ibm775", "cp775");
        addAlias("850", "cp850");
        addAlias("cspc850multilingual", "cp850");
        addAlias("ibm850", "cp850");
        addAlias("852", "cp852");
        addAlias("cspcp852", "cp852");
        addAlias("ibm852", "cp852");
        addAlias("855", "cp855");
        addAlias("csibm855", "cp855");
        addAlias("ibm855", "cp855");
        addAlias("857", "cp857");
        addAlias("csibm857", "cp857");
        addAlias("ibm857", "cp857");
        addAlias("858", "cp858");
        addAlias("csibm858", "cp858");
        addAlias("ibm858", "cp858");
        addAlias("860", "cp860");
        addAlias("csibm860", "cp860");
        addAlias("ibm860", "cp860");
        addAlias("861", "cp861");
        addAlias("cp_is", "cp861");
        addAlias("csibm861", "cp861");
        addAlias("ibm861", "cp861");
        addAlias("862", "cp862");
        addAlias("cspc862latinhebrew", "cp862");
        addAlias("ibm862", "cp862");
        addAlias("863", "cp863");
        addAlias("csibm863", "cp863");
        addAlias("ibm863", "cp863");
        addAlias("864", "cp864");
        addAlias("csibm864", "cp864");
        addAlias("ibm864", "cp864");
        addAlias("865", "cp865");
        addAlias("csibm865", "cp865");
        addAlias("ibm865", "cp865");
        addAlias("866", "cp866");
        addAlias("csibm866", "cp866");
        addAlias("ibm866", "cp866");
        addAlias("869", "cp869");
        addAlias("cp_gr", "cp869");
        addAlias("csibm869", "cp869");
        addAlias("ibm869", "cp869");
        addAlias("932", "cp932");
        addAlias("ms932", "cp932");
        addAlias("mskanji", "cp932");
        addAlias("ms_kanji", "cp932");
        addAlias("949", "cp949");
        addAlias("ms949", "cp949");
        addAlias("uhc", "cp949");
        addAlias("950", "cp950");
        addAlias("ms950", "cp950");
        addAlias("jisx0213", "euc_jis_2004");
        addAlias("eucjis2004", "euc_jis_2004");
        addAlias("euc_jis2004", "euc_jis_2004");
        addAlias("eucjisx0213", "euc_jisx0213");
        addAlias("eucjp", "euc_jp");
        addAlias("ujis", "euc_jp");
        addAlias("u_jis", "euc_jp");
        addAlias("euckr", "euc_kr");
        addAlias("korean", "euc_kr");
        addAlias("ksc5601", "euc_kr");
        addAlias("ks_c_5601", "euc_kr");
        addAlias("ks_c_5601_1987", "euc_kr");
        addAlias("ksx1001", "euc_kr");
        addAlias("ks_x_1001", "euc_kr");
        addAlias("gb18030_2000", "gb18030");
        addAlias("chinese", "gb2312");
        addAlias("csiso58gb231280", "gb2312");
        addAlias("euc_cn", "gb2312");
        addAlias("euccn", "gb2312");
        addAlias("eucgb2312_cn", "gb2312");
        addAlias("gb2312_1980", "gb2312");
        addAlias("gb2312_80", "gb2312");
        addAlias("iso_ir_58", "gb2312");
        addAlias("936", "gbk");
        addAlias("cp936", "gbk");
        addAlias("ms936", "gbk");
        addAlias("roman8", "hp_roman8");
        addAlias("r8", "hp_roman8");
        addAlias("csHPRoman8", "hp_roman8");
        addAlias("cp1051", "hp_roman8");
        addAlias("ibm1051", "hp_roman8");
        addAlias("hzgb", "hz");
        addAlias("hz_gb", "hz");
        addAlias("hz_gb_2312", "hz");
        addAlias("csiso2022jp", "iso2022_jp");
        addAlias("iso2022jp", "iso2022_jp");
        addAlias("iso_2022_jp", "iso2022_jp");
        addAlias("iso2022jp_1", "iso2022_jp_1");
        addAlias("iso_2022_jp_1", "iso2022_jp_1");
        addAlias("iso2022jp_2", "iso2022_jp_2");
        addAlias("iso_2022_jp_2", "iso2022_jp_2");
        addAlias("iso_2022_jp_2004", "iso2022_jp_2004");
        addAlias("iso2022jp_2004", "iso2022_jp_2004");
        addAlias("iso2022jp_3", "iso2022_jp_3");
        addAlias("iso_2022_jp_3", "iso2022_jp_3");
        addAlias("iso2022jp_ext", "iso2022_jp_ext");
        addAlias("iso_2022_jp_ext", "iso2022_jp_ext");
        addAlias("csiso2022kr", "iso2022_kr");
        addAlias("iso2022kr", "iso2022_kr");
        addAlias("iso_2022_kr", "iso2022_kr");
        addAlias("csisolatin6", "iso8859_10");
        addAlias("iso_8859_10", "iso8859_10");
        addAlias("iso_8859_10_1992", "iso8859_10");
        addAlias("iso_ir_157", "iso8859_10");
        addAlias("l6", "iso8859_10");
        addAlias("latin6", "iso8859_10");
        addAlias("thai", "iso8859_11");
        addAlias("iso_8859_11", "iso8859_11");
        addAlias("iso_8859_11_2001", "iso8859_11");
        addAlias("iso_8859_13", "iso8859_13");
        addAlias("l7", "iso8859_13");
        addAlias("latin7", "iso8859_13");
        addAlias("iso_8859_14", "iso8859_14");
        addAlias("iso_8859_14_1998", "iso8859_14");
        addAlias("iso_celtic", "iso8859_14");
        addAlias("iso_ir_199", "iso8859_14");
        addAlias("l8", "iso8859_14");
        addAlias("latin8", "iso8859_14");
        addAlias("iso_8859_15", "iso8859_15");
        addAlias("l9", "iso8859_15");
        addAlias("latin9", "iso8859_15");
        addAlias("iso_8859_16", "iso8859_16");
        addAlias("iso_8859_16_2001", "iso8859_16");
        addAlias("iso_ir_226", "iso8859_16");
        addAlias("l10", "iso8859_16");
        addAlias("latin10", "iso8859_16");
        addAlias("csisolatin2", "iso8859_2");
        addAlias("iso_8859_2", "iso8859_2");
        addAlias("iso_8859_2_1987", "iso8859_2");
        addAlias("iso_ir_101", "iso8859_2");
        addAlias("l2", "iso8859_2");
        addAlias("latin2", "iso8859_2");
        addAlias("csisolatin3", "iso8859_3");
        addAlias("iso_8859_3", "iso8859_3");
        addAlias("iso_8859_3_1988", "iso8859_3");
        addAlias("iso_ir_109", "iso8859_3");
        addAlias("l3", "iso8859_3");
        addAlias("latin3", "iso8859_3");
        addAlias("csisolatin4", "iso8859_4");
        addAlias("iso_8859_4", "iso8859_4");
        addAlias("iso_8859_4_1988", "iso8859_4");
        addAlias("iso_ir_110", "iso8859_4");
        addAlias("l4", "iso8859_4");
        addAlias("latin4", "iso8859_4");
        addAlias("csisolatincyrillic", "iso8859_5");
        addAlias("cyrillic", "iso8859_5");
        addAlias("iso_8859_5", "iso8859_5");
        addAlias("iso_8859_5_1988", "iso8859_5");
        addAlias("iso_ir_144", "iso8859_5");
        addAlias("arabic", "iso8859_6");
        addAlias("asmo_708", "iso8859_6");
        addAlias("csisolatinarabic", "iso8859_6");
        addAlias("ecma_114", "iso8859_6");
        addAlias("iso_8859_6", "iso8859_6");
        addAlias("iso_8859_6_1987", "iso8859_6");
        addAlias("iso_ir_127", "iso8859_6");
        addAlias("csisolatingreek", "iso8859_7");
        addAlias("ecma_118", "iso8859_7");
        addAlias("elot_928", "iso8859_7");
        addAlias("greek", "iso8859_7");
        addAlias("greek8", "iso8859_7");
        addAlias("iso_8859_7", "iso8859_7");
        addAlias("iso_8859_7_1987", "iso8859_7");
        addAlias("iso_ir_126", "iso8859_7");
        addAlias("csisolatinhebrew", "iso8859_8");
        addAlias("hebrew", "iso8859_8");
        addAlias("iso_8859_8", "iso8859_8");
        addAlias("iso_8859_8_1988", "iso8859_8");
        addAlias("iso_ir_138", "iso8859_8");
        addAlias("csisolatin5", "iso8859_9");
        addAlias("iso_8859_9", "iso8859_9");
        addAlias("iso_8859_9_1989", "iso8859_9");
        addAlias("iso_ir_148", "iso8859_9");
        addAlias("l5", "iso8859_9");
        addAlias("latin5", "iso8859_9");
        addAlias("cp1361", "johab");
        addAlias("ms1361", "johab");
        addAlias("cskoi8r", "koi8_r");
        addAlias("kz_1048", "kz1048");
        addAlias("rk1048", "kz1048");
        addAlias("strk1048_2002", "kz1048");
        addAlias("8859", "latin_1");
        addAlias("cp819", "latin_1");
        addAlias("csisolatin1", "latin_1");
        addAlias("ibm819", "latin_1");
        addAlias("iso8859", "latin_1");
        addAlias("iso8859_1", "latin_1");
        addAlias("iso_8859_1", "latin_1");
        addAlias("iso_8859_1_1987", "latin_1");
        addAlias("iso_ir_100", "latin_1");
        addAlias("l1", "latin_1");
        addAlias("latin", "latin_1");
        addAlias("latin1", "latin_1");
        addAlias("maccyrillic", "mac_cyrillic");
        addAlias("macgreek", "mac_greek");
        addAlias("maciceland", "mac_iceland");
        addAlias("maccentraleurope", "mac_latin2");
        addAlias("maclatin2", "mac_latin2");
        addAlias("macintosh", "mac_roman");
        addAlias("macroman", "mac_roman");
        addAlias("macturkish", "mac_turkish");
        addAlias("csptcp154", "ptcp154");
        addAlias("pt154", "ptcp154");
        addAlias("cp154", "ptcp154");
        addAlias("cyrillic_asian", "ptcp154");
        addAlias("csshiftjis", "shift_jis");
        addAlias("shiftjis", "shift_jis");
        addAlias("sjis", "shift_jis");
        addAlias("s_jis", "shift_jis");
        addAlias("shiftjis2004", "shift_jis_2004");
        addAlias("sjis_2004", "shift_jis_2004");
        addAlias("s_jis_2004", "shift_jis_2004");
        addAlias("shiftjisx0213", "shift_jisx0213");
        addAlias("sjisx0213", "shift_jisx0213");
        addAlias("s_jisx0213", "shift_jisx0213");
        addAlias("u16", "utf_16");
        addAlias("utf16", "utf_16");
        addAlias("unicodebigunmarked", "utf_16_be");
        addAlias("utf_16be", "utf_16_be");
        addAlias("unicodelittleunmarked", "utf_16_le");
        addAlias("utf_16le", "utf_16_le");
        addAlias("u32", "utf_32");
        addAlias("utf32", "utf_32");
        addAlias("utf_32be", "utf_32_be");
        addAlias("utf_32le", "utf_32_le");
        addAlias("u7", "utf_7");
        addAlias("utf7", "utf_7");
        addAlias("unicode_1_1_utf_7", "utf_7");
        addAlias("u8", "utf_8");
        addAlias("utf", "utf_8");
        addAlias("utf8", "utf_8");
        addAlias("utf8_ucs2", "utf_8");
        addAlias("utf8_ucs4", "utf_8");
        addAlias("cp65001", "utf_8");
        addAlias("x_mac_japanese", "shift_jis");
        addAlias("x_mac_korean", "euc_kr");
        addAlias("x_mac_simp_chinese", "gb2312");
        addAlias("x_mac_trad_chinese", "big5");
    }
}
