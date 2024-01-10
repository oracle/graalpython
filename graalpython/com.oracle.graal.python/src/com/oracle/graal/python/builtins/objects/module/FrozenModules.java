/*
 * Copyright (c) 2021, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.objects.module;

import com.oracle.graal.python.builtins.PythonOS;

public final class FrozenModules {

    private static final class Map {
        private static final PythonFrozenModule IMPORTLIB__BOOTSTRAP = new PythonFrozenModule("IMPORTLIB__BOOTSTRAP", "importlib._bootstrap", false);
        private static final PythonFrozenModule IMPORTLIB__BOOTSTRAP_EXTERNAL = new PythonFrozenModule("IMPORTLIB__BOOTSTRAP_EXTERNAL", "importlib._bootstrap_external", false);
        private static final PythonFrozenModule ZIPIMPORT = new PythonFrozenModule("ZIPIMPORT", "zipimport", false);
        private static final PythonFrozenModule ABC = new PythonFrozenModule("ABC", "abc", false);
        private static final PythonFrozenModule CODECS = new PythonFrozenModule("CODECS", "codecs", false);
        private static final PythonFrozenModule ENCODINGS = new PythonFrozenModule("ENCODINGS", "encodings", true);
        private static final PythonFrozenModule ENCODINGS_ALIASES = new PythonFrozenModule("ENCODINGS_ALIASES", "encodings.aliases", false);
        private static final PythonFrozenModule ENCODINGS_ASCII = new PythonFrozenModule("ENCODINGS_ASCII", "encodings.ascii", false);
        private static final PythonFrozenModule ENCODINGS_BASE64_CODEC = new PythonFrozenModule("ENCODINGS_BASE64_CODEC", "encodings.base64_codec", false);
        private static final PythonFrozenModule ENCODINGS_BIG5 = new PythonFrozenModule("ENCODINGS_BIG5", "encodings.big5", false);
        private static final PythonFrozenModule ENCODINGS_BIG5HKSCS = new PythonFrozenModule("ENCODINGS_BIG5HKSCS", "encodings.big5hkscs", false);
        private static final PythonFrozenModule ENCODINGS_BZ2_CODEC = new PythonFrozenModule("ENCODINGS_BZ2_CODEC", "encodings.bz2_codec", false);
        private static final PythonFrozenModule ENCODINGS_CHARMAP = new PythonFrozenModule("ENCODINGS_CHARMAP", "encodings.charmap", false);
        private static final PythonFrozenModule ENCODINGS_CP037 = new PythonFrozenModule("ENCODINGS_CP037", "encodings.cp037", false);
        private static final PythonFrozenModule ENCODINGS_CP1006 = new PythonFrozenModule("ENCODINGS_CP1006", "encodings.cp1006", false);
        private static final PythonFrozenModule ENCODINGS_CP1026 = new PythonFrozenModule("ENCODINGS_CP1026", "encodings.cp1026", false);
        private static final PythonFrozenModule ENCODINGS_CP1125 = new PythonFrozenModule("ENCODINGS_CP1125", "encodings.cp1125", false);
        private static final PythonFrozenModule ENCODINGS_CP1140 = new PythonFrozenModule("ENCODINGS_CP1140", "encodings.cp1140", false);
        private static final PythonFrozenModule ENCODINGS_CP1250 = new PythonFrozenModule("ENCODINGS_CP1250", "encodings.cp1250", false);
        private static final PythonFrozenModule ENCODINGS_CP1251 = new PythonFrozenModule("ENCODINGS_CP1251", "encodings.cp1251", false);
        private static final PythonFrozenModule ENCODINGS_CP1252 = new PythonFrozenModule("ENCODINGS_CP1252", "encodings.cp1252", false);
        private static final PythonFrozenModule ENCODINGS_CP1253 = new PythonFrozenModule("ENCODINGS_CP1253", "encodings.cp1253", false);
        private static final PythonFrozenModule ENCODINGS_CP1254 = new PythonFrozenModule("ENCODINGS_CP1254", "encodings.cp1254", false);
        private static final PythonFrozenModule ENCODINGS_CP1255 = new PythonFrozenModule("ENCODINGS_CP1255", "encodings.cp1255", false);
        private static final PythonFrozenModule ENCODINGS_CP1256 = new PythonFrozenModule("ENCODINGS_CP1256", "encodings.cp1256", false);
        private static final PythonFrozenModule ENCODINGS_CP1257 = new PythonFrozenModule("ENCODINGS_CP1257", "encodings.cp1257", false);
        private static final PythonFrozenModule ENCODINGS_CP1258 = new PythonFrozenModule("ENCODINGS_CP1258", "encodings.cp1258", false);
        private static final PythonFrozenModule ENCODINGS_CP273 = new PythonFrozenModule("ENCODINGS_CP273", "encodings.cp273", false);
        private static final PythonFrozenModule ENCODINGS_CP424 = new PythonFrozenModule("ENCODINGS_CP424", "encodings.cp424", false);
        private static final PythonFrozenModule ENCODINGS_CP437 = new PythonFrozenModule("ENCODINGS_CP437", "encodings.cp437", false);
        private static final PythonFrozenModule ENCODINGS_CP500 = new PythonFrozenModule("ENCODINGS_CP500", "encodings.cp500", false);
        private static final PythonFrozenModule ENCODINGS_CP720 = new PythonFrozenModule("ENCODINGS_CP720", "encodings.cp720", false);
        private static final PythonFrozenModule ENCODINGS_CP737 = new PythonFrozenModule("ENCODINGS_CP737", "encodings.cp737", false);
        private static final PythonFrozenModule ENCODINGS_CP775 = new PythonFrozenModule("ENCODINGS_CP775", "encodings.cp775", false);
        private static final PythonFrozenModule ENCODINGS_CP850 = new PythonFrozenModule("ENCODINGS_CP850", "encodings.cp850", false);
        private static final PythonFrozenModule ENCODINGS_CP852 = new PythonFrozenModule("ENCODINGS_CP852", "encodings.cp852", false);
        private static final PythonFrozenModule ENCODINGS_CP855 = new PythonFrozenModule("ENCODINGS_CP855", "encodings.cp855", false);
        private static final PythonFrozenModule ENCODINGS_CP856 = new PythonFrozenModule("ENCODINGS_CP856", "encodings.cp856", false);
        private static final PythonFrozenModule ENCODINGS_CP857 = new PythonFrozenModule("ENCODINGS_CP857", "encodings.cp857", false);
        private static final PythonFrozenModule ENCODINGS_CP858 = new PythonFrozenModule("ENCODINGS_CP858", "encodings.cp858", false);
        private static final PythonFrozenModule ENCODINGS_CP860 = new PythonFrozenModule("ENCODINGS_CP860", "encodings.cp860", false);
        private static final PythonFrozenModule ENCODINGS_CP861 = new PythonFrozenModule("ENCODINGS_CP861", "encodings.cp861", false);
        private static final PythonFrozenModule ENCODINGS_CP862 = new PythonFrozenModule("ENCODINGS_CP862", "encodings.cp862", false);
        private static final PythonFrozenModule ENCODINGS_CP863 = new PythonFrozenModule("ENCODINGS_CP863", "encodings.cp863", false);
        private static final PythonFrozenModule ENCODINGS_CP864 = new PythonFrozenModule("ENCODINGS_CP864", "encodings.cp864", false);
        private static final PythonFrozenModule ENCODINGS_CP865 = new PythonFrozenModule("ENCODINGS_CP865", "encodings.cp865", false);
        private static final PythonFrozenModule ENCODINGS_CP866 = new PythonFrozenModule("ENCODINGS_CP866", "encodings.cp866", false);
        private static final PythonFrozenModule ENCODINGS_CP869 = new PythonFrozenModule("ENCODINGS_CP869", "encodings.cp869", false);
        private static final PythonFrozenModule ENCODINGS_CP874 = new PythonFrozenModule("ENCODINGS_CP874", "encodings.cp874", false);
        private static final PythonFrozenModule ENCODINGS_CP875 = new PythonFrozenModule("ENCODINGS_CP875", "encodings.cp875", false);
        private static final PythonFrozenModule ENCODINGS_CP932 = new PythonFrozenModule("ENCODINGS_CP932", "encodings.cp932", false);
        private static final PythonFrozenModule ENCODINGS_CP949 = new PythonFrozenModule("ENCODINGS_CP949", "encodings.cp949", false);
        private static final PythonFrozenModule ENCODINGS_CP950 = new PythonFrozenModule("ENCODINGS_CP950", "encodings.cp950", false);
        private static final PythonFrozenModule ENCODINGS_EUC_JIS_2004 = new PythonFrozenModule("ENCODINGS_EUC_JIS_2004", "encodings.euc_jis_2004", false);
        private static final PythonFrozenModule ENCODINGS_EUC_JISX0213 = new PythonFrozenModule("ENCODINGS_EUC_JISX0213", "encodings.euc_jisx0213", false);
        private static final PythonFrozenModule ENCODINGS_EUC_JP = new PythonFrozenModule("ENCODINGS_EUC_JP", "encodings.euc_jp", false);
        private static final PythonFrozenModule ENCODINGS_EUC_KR = new PythonFrozenModule("ENCODINGS_EUC_KR", "encodings.euc_kr", false);
        private static final PythonFrozenModule ENCODINGS_GB18030 = new PythonFrozenModule("ENCODINGS_GB18030", "encodings.gb18030", false);
        private static final PythonFrozenModule ENCODINGS_GB2312 = new PythonFrozenModule("ENCODINGS_GB2312", "encodings.gb2312", false);
        private static final PythonFrozenModule ENCODINGS_GBK = new PythonFrozenModule("ENCODINGS_GBK", "encodings.gbk", false);
        private static final PythonFrozenModule ENCODINGS_HEX_CODEC = new PythonFrozenModule("ENCODINGS_HEX_CODEC", "encodings.hex_codec", false);
        private static final PythonFrozenModule ENCODINGS_HP_ROMAN8 = new PythonFrozenModule("ENCODINGS_HP_ROMAN8", "encodings.hp_roman8", false);
        private static final PythonFrozenModule ENCODINGS_HZ = new PythonFrozenModule("ENCODINGS_HZ", "encodings.hz", false);
        private static final PythonFrozenModule ENCODINGS_IDNA = new PythonFrozenModule("ENCODINGS_IDNA", "encodings.idna", false);
        private static final PythonFrozenModule ENCODINGS_ISO2022_JP = new PythonFrozenModule("ENCODINGS_ISO2022_JP", "encodings.iso2022_jp", false);
        private static final PythonFrozenModule ENCODINGS_ISO2022_JP_1 = new PythonFrozenModule("ENCODINGS_ISO2022_JP_1", "encodings.iso2022_jp_1", false);
        private static final PythonFrozenModule ENCODINGS_ISO2022_JP_2 = new PythonFrozenModule("ENCODINGS_ISO2022_JP_2", "encodings.iso2022_jp_2", false);
        private static final PythonFrozenModule ENCODINGS_ISO2022_JP_2004 = new PythonFrozenModule("ENCODINGS_ISO2022_JP_2004", "encodings.iso2022_jp_2004", false);
        private static final PythonFrozenModule ENCODINGS_ISO2022_JP_3 = new PythonFrozenModule("ENCODINGS_ISO2022_JP_3", "encodings.iso2022_jp_3", false);
        private static final PythonFrozenModule ENCODINGS_ISO2022_JP_EXT = new PythonFrozenModule("ENCODINGS_ISO2022_JP_EXT", "encodings.iso2022_jp_ext", false);
        private static final PythonFrozenModule ENCODINGS_ISO2022_KR = new PythonFrozenModule("ENCODINGS_ISO2022_KR", "encodings.iso2022_kr", false);
        private static final PythonFrozenModule ENCODINGS_ISO8859_1 = new PythonFrozenModule("ENCODINGS_ISO8859_1", "encodings.iso8859_1", false);
        private static final PythonFrozenModule ENCODINGS_ISO8859_10 = new PythonFrozenModule("ENCODINGS_ISO8859_10", "encodings.iso8859_10", false);
        private static final PythonFrozenModule ENCODINGS_ISO8859_11 = new PythonFrozenModule("ENCODINGS_ISO8859_11", "encodings.iso8859_11", false);
        private static final PythonFrozenModule ENCODINGS_ISO8859_13 = new PythonFrozenModule("ENCODINGS_ISO8859_13", "encodings.iso8859_13", false);
        private static final PythonFrozenModule ENCODINGS_ISO8859_14 = new PythonFrozenModule("ENCODINGS_ISO8859_14", "encodings.iso8859_14", false);
        private static final PythonFrozenModule ENCODINGS_ISO8859_15 = new PythonFrozenModule("ENCODINGS_ISO8859_15", "encodings.iso8859_15", false);
        private static final PythonFrozenModule ENCODINGS_ISO8859_16 = new PythonFrozenModule("ENCODINGS_ISO8859_16", "encodings.iso8859_16", false);
        private static final PythonFrozenModule ENCODINGS_ISO8859_2 = new PythonFrozenModule("ENCODINGS_ISO8859_2", "encodings.iso8859_2", false);
        private static final PythonFrozenModule ENCODINGS_ISO8859_3 = new PythonFrozenModule("ENCODINGS_ISO8859_3", "encodings.iso8859_3", false);
        private static final PythonFrozenModule ENCODINGS_ISO8859_4 = new PythonFrozenModule("ENCODINGS_ISO8859_4", "encodings.iso8859_4", false);
        private static final PythonFrozenModule ENCODINGS_ISO8859_5 = new PythonFrozenModule("ENCODINGS_ISO8859_5", "encodings.iso8859_5", false);
        private static final PythonFrozenModule ENCODINGS_ISO8859_6 = new PythonFrozenModule("ENCODINGS_ISO8859_6", "encodings.iso8859_6", false);
        private static final PythonFrozenModule ENCODINGS_ISO8859_7 = new PythonFrozenModule("ENCODINGS_ISO8859_7", "encodings.iso8859_7", false);
        private static final PythonFrozenModule ENCODINGS_ISO8859_8 = new PythonFrozenModule("ENCODINGS_ISO8859_8", "encodings.iso8859_8", false);
        private static final PythonFrozenModule ENCODINGS_ISO8859_9 = new PythonFrozenModule("ENCODINGS_ISO8859_9", "encodings.iso8859_9", false);
        private static final PythonFrozenModule ENCODINGS_JOHAB = new PythonFrozenModule("ENCODINGS_JOHAB", "encodings.johab", false);
        private static final PythonFrozenModule ENCODINGS_KOI8_R = new PythonFrozenModule("ENCODINGS_KOI8_R", "encodings.koi8_r", false);
        private static final PythonFrozenModule ENCODINGS_KOI8_T = new PythonFrozenModule("ENCODINGS_KOI8_T", "encodings.koi8_t", false);
        private static final PythonFrozenModule ENCODINGS_KOI8_U = new PythonFrozenModule("ENCODINGS_KOI8_U", "encodings.koi8_u", false);
        private static final PythonFrozenModule ENCODINGS_KZ1048 = new PythonFrozenModule("ENCODINGS_KZ1048", "encodings.kz1048", false);
        private static final PythonFrozenModule ENCODINGS_LATIN_1 = new PythonFrozenModule("ENCODINGS_LATIN_1", "encodings.latin_1", false);
        private static final PythonFrozenModule ENCODINGS_MAC_ARABIC = new PythonFrozenModule("ENCODINGS_MAC_ARABIC", "encodings.mac_arabic", false);
        private static final PythonFrozenModule ENCODINGS_MAC_CROATIAN = new PythonFrozenModule("ENCODINGS_MAC_CROATIAN", "encodings.mac_croatian", false);
        private static final PythonFrozenModule ENCODINGS_MAC_CYRILLIC = new PythonFrozenModule("ENCODINGS_MAC_CYRILLIC", "encodings.mac_cyrillic", false);
        private static final PythonFrozenModule ENCODINGS_MAC_FARSI = new PythonFrozenModule("ENCODINGS_MAC_FARSI", "encodings.mac_farsi", false);
        private static final PythonFrozenModule ENCODINGS_MAC_GREEK = new PythonFrozenModule("ENCODINGS_MAC_GREEK", "encodings.mac_greek", false);
        private static final PythonFrozenModule ENCODINGS_MAC_ICELAND = new PythonFrozenModule("ENCODINGS_MAC_ICELAND", "encodings.mac_iceland", false);
        private static final PythonFrozenModule ENCODINGS_MAC_LATIN2 = new PythonFrozenModule("ENCODINGS_MAC_LATIN2", "encodings.mac_latin2", false);
        private static final PythonFrozenModule ENCODINGS_MAC_ROMAN = new PythonFrozenModule("ENCODINGS_MAC_ROMAN", "encodings.mac_roman", false);
        private static final PythonFrozenModule ENCODINGS_MAC_ROMANIAN = new PythonFrozenModule("ENCODINGS_MAC_ROMANIAN", "encodings.mac_romanian", false);
        private static final PythonFrozenModule ENCODINGS_MAC_TURKISH = new PythonFrozenModule("ENCODINGS_MAC_TURKISH", "encodings.mac_turkish", false);
        private static final PythonFrozenModule ENCODINGS_MBCS = new PythonFrozenModule("ENCODINGS_MBCS", "encodings.mbcs", false);
        private static final PythonFrozenModule ENCODINGS_OEM = new PythonFrozenModule("ENCODINGS_OEM", "encodings.oem", false);
        private static final PythonFrozenModule ENCODINGS_PALMOS = new PythonFrozenModule("ENCODINGS_PALMOS", "encodings.palmos", false);
        private static final PythonFrozenModule ENCODINGS_PTCP154 = new PythonFrozenModule("ENCODINGS_PTCP154", "encodings.ptcp154", false);
        private static final PythonFrozenModule ENCODINGS_PUNYCODE = new PythonFrozenModule("ENCODINGS_PUNYCODE", "encodings.punycode", false);
        private static final PythonFrozenModule ENCODINGS_QUOPRI_CODEC = new PythonFrozenModule("ENCODINGS_QUOPRI_CODEC", "encodings.quopri_codec", false);
        private static final PythonFrozenModule ENCODINGS_RAW_UNICODE_ESCAPE = new PythonFrozenModule("ENCODINGS_RAW_UNICODE_ESCAPE", "encodings.raw_unicode_escape", false);
        private static final PythonFrozenModule ENCODINGS_ROT_13 = new PythonFrozenModule("ENCODINGS_ROT_13", "encodings.rot_13", false);
        private static final PythonFrozenModule ENCODINGS_SHIFT_JIS = new PythonFrozenModule("ENCODINGS_SHIFT_JIS", "encodings.shift_jis", false);
        private static final PythonFrozenModule ENCODINGS_SHIFT_JIS_2004 = new PythonFrozenModule("ENCODINGS_SHIFT_JIS_2004", "encodings.shift_jis_2004", false);
        private static final PythonFrozenModule ENCODINGS_SHIFT_JISX0213 = new PythonFrozenModule("ENCODINGS_SHIFT_JISX0213", "encodings.shift_jisx0213", false);
        private static final PythonFrozenModule ENCODINGS_TIS_620 = new PythonFrozenModule("ENCODINGS_TIS_620", "encodings.tis_620", false);
        private static final PythonFrozenModule ENCODINGS_UNDEFINED = new PythonFrozenModule("ENCODINGS_UNDEFINED", "encodings.undefined", false);
        private static final PythonFrozenModule ENCODINGS_UNICODE_ESCAPE = new PythonFrozenModule("ENCODINGS_UNICODE_ESCAPE", "encodings.unicode_escape", false);
        private static final PythonFrozenModule ENCODINGS_UTF_16 = new PythonFrozenModule("ENCODINGS_UTF_16", "encodings.utf_16", false);
        private static final PythonFrozenModule ENCODINGS_UTF_16_BE = new PythonFrozenModule("ENCODINGS_UTF_16_BE", "encodings.utf_16_be", false);
        private static final PythonFrozenModule ENCODINGS_UTF_16_LE = new PythonFrozenModule("ENCODINGS_UTF_16_LE", "encodings.utf_16_le", false);
        private static final PythonFrozenModule ENCODINGS_UTF_32 = new PythonFrozenModule("ENCODINGS_UTF_32", "encodings.utf_32", false);
        private static final PythonFrozenModule ENCODINGS_UTF_32_BE = new PythonFrozenModule("ENCODINGS_UTF_32_BE", "encodings.utf_32_be", false);
        private static final PythonFrozenModule ENCODINGS_UTF_32_LE = new PythonFrozenModule("ENCODINGS_UTF_32_LE", "encodings.utf_32_le", false);
        private static final PythonFrozenModule ENCODINGS_UTF_7 = new PythonFrozenModule("ENCODINGS_UTF_7", "encodings.utf_7", false);
        private static final PythonFrozenModule ENCODINGS_UTF_8 = new PythonFrozenModule("ENCODINGS_UTF_8", "encodings.utf_8", false);
        private static final PythonFrozenModule ENCODINGS_UTF_8_SIG = new PythonFrozenModule("ENCODINGS_UTF_8_SIG", "encodings.utf_8_sig", false);
        private static final PythonFrozenModule ENCODINGS_UU_CODEC = new PythonFrozenModule("ENCODINGS_UU_CODEC", "encodings.uu_codec", false);
        private static final PythonFrozenModule ENCODINGS_ZLIB_CODEC = new PythonFrozenModule("ENCODINGS_ZLIB_CODEC", "encodings.zlib_codec", false);
        private static final PythonFrozenModule IO = new PythonFrozenModule("IO", "io", false);
        private static final PythonFrozenModule _PY_ABC = new PythonFrozenModule("_PY_ABC", "_py_abc", false);
        private static final PythonFrozenModule _WEAKREFSET = new PythonFrozenModule("_WEAKREFSET", "_weakrefset", false);
        private static final PythonFrozenModule TYPES = new PythonFrozenModule("TYPES", "types", false);
        private static final PythonFrozenModule ENUM = new PythonFrozenModule("ENUM", "enum", false);
        private static final PythonFrozenModule SRE_CONSTANTS = new PythonFrozenModule("SRE_CONSTANTS", "sre_constants", false);
        private static final PythonFrozenModule SRE_PARSE = new PythonFrozenModule("SRE_PARSE", "sre_parse", false);
        private static final PythonFrozenModule SRE_COMPILE = new PythonFrozenModule("SRE_COMPILE", "sre_compile", false);
        private static final PythonFrozenModule OPERATOR = new PythonFrozenModule("OPERATOR", "operator", false);
        private static final PythonFrozenModule KEYWORD = new PythonFrozenModule("KEYWORD", "keyword", false);
        private static final PythonFrozenModule HEAPQ = new PythonFrozenModule("HEAPQ", "heapq", false);
        private static final PythonFrozenModule REPRLIB = new PythonFrozenModule("REPRLIB", "reprlib", false);
        private static final PythonFrozenModule COLLECTIONS = new PythonFrozenModule("COLLECTIONS", "collections", true);
        private static final PythonFrozenModule COLLECTIONS_ABC = new PythonFrozenModule("COLLECTIONS_ABC", "collections.abc", false);
        private static final PythonFrozenModule FUNCTOOLS = new PythonFrozenModule("FUNCTOOLS", "functools", false);
        private static final PythonFrozenModule COPYREG = new PythonFrozenModule("COPYREG", "copyreg", false);
        private static final PythonFrozenModule RE = new PythonFrozenModule("RE", "re", true);
        private static final PythonFrozenModule RE__CASEFIX = new PythonFrozenModule("RE__CASEFIX", "re._casefix", false);
        private static final PythonFrozenModule RE__COMPILER = new PythonFrozenModule("RE__COMPILER", "re._compiler", false);
        private static final PythonFrozenModule RE__CONSTANTS = new PythonFrozenModule("RE__CONSTANTS", "re._constants", false);
        private static final PythonFrozenModule RE__PARSER = new PythonFrozenModule("RE__PARSER", "re._parser", false);
        private static final PythonFrozenModule LOCALE = new PythonFrozenModule("LOCALE", "locale", false);
        private static final PythonFrozenModule RLCOMPLETER = new PythonFrozenModule("RLCOMPLETER", "rlcompleter", false);
        private static final PythonFrozenModule _COLLECTIONS_ABC = new PythonFrozenModule("_COLLECTIONS_ABC", "_collections_abc", false);
        private static final PythonFrozenModule _SITEBUILTINS = new PythonFrozenModule("_SITEBUILTINS", "_sitebuiltins", false);
        private static final PythonFrozenModule GENERICPATH = new PythonFrozenModule("GENERICPATH", "genericpath", false);
        private static final PythonFrozenModule NTPATH = new PythonFrozenModule("NTPATH", "ntpath", false);
        private static final PythonFrozenModule POSIXPATH = new PythonFrozenModule("POSIXPATH", "posixpath", false);
        private static final PythonFrozenModule OS = new PythonFrozenModule("OS", "os", false);
        private static final PythonFrozenModule SITE = new PythonFrozenModule("SITE", "site", false);
        private static final PythonFrozenModule STAT = new PythonFrozenModule("STAT", "stat", false);
        private static final PythonFrozenModule DATETIME = new PythonFrozenModule("DATETIME", "datetime", false);
        private static final PythonFrozenModule CONTEXTLIB = new PythonFrozenModule("CONTEXTLIB", "contextlib", false);
        private static final PythonFrozenModule WARNINGS = new PythonFrozenModule("WARNINGS", "warnings", false);
        private static final PythonFrozenModule INSPECT = new PythonFrozenModule("INSPECT", "inspect", false);
        private static final PythonFrozenModule __HELLO__ = new PythonFrozenModule("__HELLO__", "__hello__", false);
        private static final PythonFrozenModule FROZEN_ONLY = new PythonFrozenModule("FROZEN_ONLY", "frozen_only", false);
        private static final PythonFrozenModule _SYSCONFIGDATA = new PythonFrozenModule("_SYSCONFIGDATA", "_sysconfigdata", false);
        private static final PythonFrozenModule GRAALPY___GRAALPYTHON__ = new PythonFrozenModule("GRAALPY___GRAALPYTHON__", "graalpy.__graalpython__", false);
        private static final PythonFrozenModule GRAALPY__INTEROP_BEHAVIOR = new PythonFrozenModule("GRAALPY__INTEROP_BEHAVIOR", "graalpy._interop_behavior", false);
        private static final PythonFrozenModule GRAALPY__SRE = new PythonFrozenModule("GRAALPY__SRE", "graalpy._sre", false);
        private static final PythonFrozenModule GRAALPY__STRUCT = new PythonFrozenModule("GRAALPY__STRUCT", "graalpy._struct", false);
        private static final PythonFrozenModule GRAALPY__SYSCONFIG = new PythonFrozenModule("GRAALPY__SYSCONFIG", "graalpy._sysconfig", false);
        private static final PythonFrozenModule GRAALPY__WEAKREF = new PythonFrozenModule("GRAALPY__WEAKREF", "graalpy._weakref", false);
        private static final PythonFrozenModule GRAALPY_BUILTINS = new PythonFrozenModule("GRAALPY_BUILTINS", "graalpy.builtins", false);
        private static final PythonFrozenModule GRAALPY_FUNCTION = new PythonFrozenModule("GRAALPY_FUNCTION", "graalpy.function", false);
        private static final PythonFrozenModule GRAALPY_JAVA = new PythonFrozenModule("GRAALPY_JAVA", "graalpy.java", false);
        private static final PythonFrozenModule GRAALPY_PIP_HOOK = new PythonFrozenModule("GRAALPY_PIP_HOOK", "graalpy.pip_hook", false);
        private static final PythonFrozenModule GRAALPY_UNICODEDATA = new PythonFrozenModule("GRAALPY_UNICODEDATA", "graalpy.unicodedata", false);
        private static final PythonFrozenModule GRAALPY_SULONG_SUPPORT = new PythonFrozenModule("GRAALPY_SULONG_SUPPORT", "graalpy.sulong_support", false);
    }

    public static final PythonFrozenModule lookup(String name) {
        switch (name) {
            case "_frozen_importlib":
                return Map.IMPORTLIB__BOOTSTRAP;
            case "_frozen_importlib_external":
                return Map.IMPORTLIB__BOOTSTRAP_EXTERNAL;
            case "zipimport":
                return Map.ZIPIMPORT;
            case "abc":
                return Map.ABC;
            case "codecs":
                return Map.CODECS;
            case "encodings":
                return Map.ENCODINGS;
            case "encodings.__init__":
                return Map.ENCODINGS.asPackage(false);
            case "encodings.aliases":
                return Map.ENCODINGS_ALIASES;
            case "encodings.ascii":
                return Map.ENCODINGS_ASCII;
            case "encodings.base64_codec":
                return Map.ENCODINGS_BASE64_CODEC;
            case "encodings.big5":
                return Map.ENCODINGS_BIG5;
            case "encodings.big5hkscs":
                return Map.ENCODINGS_BIG5HKSCS;
            case "encodings.bz2_codec":
                return Map.ENCODINGS_BZ2_CODEC;
            case "encodings.charmap":
                return Map.ENCODINGS_CHARMAP;
            case "encodings.cp037":
                return Map.ENCODINGS_CP037;
            case "encodings.cp1006":
                return Map.ENCODINGS_CP1006;
            case "encodings.cp1026":
                return Map.ENCODINGS_CP1026;
            case "encodings.cp1125":
                return Map.ENCODINGS_CP1125;
            case "encodings.cp1140":
                return Map.ENCODINGS_CP1140;
            case "encodings.cp1250":
                return Map.ENCODINGS_CP1250;
            case "encodings.cp1251":
                return Map.ENCODINGS_CP1251;
            case "encodings.cp1252":
                return Map.ENCODINGS_CP1252;
            case "encodings.cp1253":
                return Map.ENCODINGS_CP1253;
            case "encodings.cp1254":
                return Map.ENCODINGS_CP1254;
            case "encodings.cp1255":
                return Map.ENCODINGS_CP1255;
            case "encodings.cp1256":
                return Map.ENCODINGS_CP1256;
            case "encodings.cp1257":
                return Map.ENCODINGS_CP1257;
            case "encodings.cp1258":
                return Map.ENCODINGS_CP1258;
            case "encodings.cp273":
                return Map.ENCODINGS_CP273;
            case "encodings.cp424":
                return Map.ENCODINGS_CP424;
            case "encodings.cp437":
                return Map.ENCODINGS_CP437;
            case "encodings.cp500":
                return Map.ENCODINGS_CP500;
            case "encodings.cp720":
                return Map.ENCODINGS_CP720;
            case "encodings.cp737":
                return Map.ENCODINGS_CP737;
            case "encodings.cp775":
                return Map.ENCODINGS_CP775;
            case "encodings.cp850":
                return Map.ENCODINGS_CP850;
            case "encodings.cp852":
                return Map.ENCODINGS_CP852;
            case "encodings.cp855":
                return Map.ENCODINGS_CP855;
            case "encodings.cp856":
                return Map.ENCODINGS_CP856;
            case "encodings.cp857":
                return Map.ENCODINGS_CP857;
            case "encodings.cp858":
                return Map.ENCODINGS_CP858;
            case "encodings.cp860":
                return Map.ENCODINGS_CP860;
            case "encodings.cp861":
                return Map.ENCODINGS_CP861;
            case "encodings.cp862":
                return Map.ENCODINGS_CP862;
            case "encodings.cp863":
                return Map.ENCODINGS_CP863;
            case "encodings.cp864":
                return Map.ENCODINGS_CP864;
            case "encodings.cp865":
                return Map.ENCODINGS_CP865;
            case "encodings.cp866":
                return Map.ENCODINGS_CP866;
            case "encodings.cp869":
                return Map.ENCODINGS_CP869;
            case "encodings.cp874":
                return Map.ENCODINGS_CP874;
            case "encodings.cp875":
                return Map.ENCODINGS_CP875;
            case "encodings.cp932":
                return Map.ENCODINGS_CP932;
            case "encodings.cp949":
                return Map.ENCODINGS_CP949;
            case "encodings.cp950":
                return Map.ENCODINGS_CP950;
            case "encodings.euc_jis_2004":
                return Map.ENCODINGS_EUC_JIS_2004;
            case "encodings.euc_jisx0213":
                return Map.ENCODINGS_EUC_JISX0213;
            case "encodings.euc_jp":
                return Map.ENCODINGS_EUC_JP;
            case "encodings.euc_kr":
                return Map.ENCODINGS_EUC_KR;
            case "encodings.gb18030":
                return Map.ENCODINGS_GB18030;
            case "encodings.gb2312":
                return Map.ENCODINGS_GB2312;
            case "encodings.gbk":
                return Map.ENCODINGS_GBK;
            case "encodings.hex_codec":
                return Map.ENCODINGS_HEX_CODEC;
            case "encodings.hp_roman8":
                return Map.ENCODINGS_HP_ROMAN8;
            case "encodings.hz":
                return Map.ENCODINGS_HZ;
            case "encodings.idna":
                return Map.ENCODINGS_IDNA;
            case "encodings.iso2022_jp":
                return Map.ENCODINGS_ISO2022_JP;
            case "encodings.iso2022_jp_1":
                return Map.ENCODINGS_ISO2022_JP_1;
            case "encodings.iso2022_jp_2":
                return Map.ENCODINGS_ISO2022_JP_2;
            case "encodings.iso2022_jp_2004":
                return Map.ENCODINGS_ISO2022_JP_2004;
            case "encodings.iso2022_jp_3":
                return Map.ENCODINGS_ISO2022_JP_3;
            case "encodings.iso2022_jp_ext":
                return Map.ENCODINGS_ISO2022_JP_EXT;
            case "encodings.iso2022_kr":
                return Map.ENCODINGS_ISO2022_KR;
            case "encodings.iso8859_1":
                return Map.ENCODINGS_ISO8859_1;
            case "encodings.iso8859_10":
                return Map.ENCODINGS_ISO8859_10;
            case "encodings.iso8859_11":
                return Map.ENCODINGS_ISO8859_11;
            case "encodings.iso8859_13":
                return Map.ENCODINGS_ISO8859_13;
            case "encodings.iso8859_14":
                return Map.ENCODINGS_ISO8859_14;
            case "encodings.iso8859_15":
                return Map.ENCODINGS_ISO8859_15;
            case "encodings.iso8859_16":
                return Map.ENCODINGS_ISO8859_16;
            case "encodings.iso8859_2":
                return Map.ENCODINGS_ISO8859_2;
            case "encodings.iso8859_3":
                return Map.ENCODINGS_ISO8859_3;
            case "encodings.iso8859_4":
                return Map.ENCODINGS_ISO8859_4;
            case "encodings.iso8859_5":
                return Map.ENCODINGS_ISO8859_5;
            case "encodings.iso8859_6":
                return Map.ENCODINGS_ISO8859_6;
            case "encodings.iso8859_7":
                return Map.ENCODINGS_ISO8859_7;
            case "encodings.iso8859_8":
                return Map.ENCODINGS_ISO8859_8;
            case "encodings.iso8859_9":
                return Map.ENCODINGS_ISO8859_9;
            case "encodings.johab":
                return Map.ENCODINGS_JOHAB;
            case "encodings.koi8_r":
                return Map.ENCODINGS_KOI8_R;
            case "encodings.koi8_t":
                return Map.ENCODINGS_KOI8_T;
            case "encodings.koi8_u":
                return Map.ENCODINGS_KOI8_U;
            case "encodings.kz1048":
                return Map.ENCODINGS_KZ1048;
            case "encodings.latin_1":
                return Map.ENCODINGS_LATIN_1;
            case "encodings.mac_arabic":
                return Map.ENCODINGS_MAC_ARABIC;
            case "encodings.mac_croatian":
                return Map.ENCODINGS_MAC_CROATIAN;
            case "encodings.mac_cyrillic":
                return Map.ENCODINGS_MAC_CYRILLIC;
            case "encodings.mac_farsi":
                return Map.ENCODINGS_MAC_FARSI;
            case "encodings.mac_greek":
                return Map.ENCODINGS_MAC_GREEK;
            case "encodings.mac_iceland":
                return Map.ENCODINGS_MAC_ICELAND;
            case "encodings.mac_latin2":
                return Map.ENCODINGS_MAC_LATIN2;
            case "encodings.mac_roman":
                return Map.ENCODINGS_MAC_ROMAN;
            case "encodings.mac_romanian":
                return Map.ENCODINGS_MAC_ROMANIAN;
            case "encodings.mac_turkish":
                return Map.ENCODINGS_MAC_TURKISH;
            case "encodings.mbcs":
                return Map.ENCODINGS_MBCS;
            case "encodings.oem":
                return Map.ENCODINGS_OEM;
            case "encodings.palmos":
                return Map.ENCODINGS_PALMOS;
            case "encodings.ptcp154":
                return Map.ENCODINGS_PTCP154;
            case "encodings.punycode":
                return Map.ENCODINGS_PUNYCODE;
            case "encodings.quopri_codec":
                return Map.ENCODINGS_QUOPRI_CODEC;
            case "encodings.raw_unicode_escape":
                return Map.ENCODINGS_RAW_UNICODE_ESCAPE;
            case "encodings.rot_13":
                return Map.ENCODINGS_ROT_13;
            case "encodings.shift_jis":
                return Map.ENCODINGS_SHIFT_JIS;
            case "encodings.shift_jis_2004":
                return Map.ENCODINGS_SHIFT_JIS_2004;
            case "encodings.shift_jisx0213":
                return Map.ENCODINGS_SHIFT_JISX0213;
            case "encodings.tis_620":
                return Map.ENCODINGS_TIS_620;
            case "encodings.undefined":
                return Map.ENCODINGS_UNDEFINED;
            case "encodings.unicode_escape":
                return Map.ENCODINGS_UNICODE_ESCAPE;
            case "encodings.utf_16":
                return Map.ENCODINGS_UTF_16;
            case "encodings.utf_16_be":
                return Map.ENCODINGS_UTF_16_BE;
            case "encodings.utf_16_le":
                return Map.ENCODINGS_UTF_16_LE;
            case "encodings.utf_32":
                return Map.ENCODINGS_UTF_32;
            case "encodings.utf_32_be":
                return Map.ENCODINGS_UTF_32_BE;
            case "encodings.utf_32_le":
                return Map.ENCODINGS_UTF_32_LE;
            case "encodings.utf_7":
                return Map.ENCODINGS_UTF_7;
            case "encodings.utf_8":
                return Map.ENCODINGS_UTF_8;
            case "encodings.utf_8_sig":
                return Map.ENCODINGS_UTF_8_SIG;
            case "encodings.uu_codec":
                return Map.ENCODINGS_UU_CODEC;
            case "encodings.zlib_codec":
                return Map.ENCODINGS_ZLIB_CODEC;
            case "io":
                return Map.IO;
            case "_py_abc":
                return Map._PY_ABC;
            case "_weakrefset":
                return Map._WEAKREFSET;
            case "types":
                return Map.TYPES;
            case "enum":
                return Map.ENUM;
            case "sre_constants":
                return Map.SRE_CONSTANTS;
            case "sre_parse":
                return Map.SRE_PARSE;
            case "sre_compile":
                return Map.SRE_COMPILE;
            case "operator":
                return Map.OPERATOR;
            case "keyword":
                return Map.KEYWORD;
            case "heapq":
                return Map.HEAPQ;
            case "reprlib":
                return Map.REPRLIB;
            case "collections":
                return Map.COLLECTIONS;
            case "collections.__init__":
                return Map.COLLECTIONS.asPackage(false);
            case "collections.abc":
                return Map.COLLECTIONS_ABC;
            case "functools":
                return Map.FUNCTOOLS;
            case "copyreg":
                return Map.COPYREG;
            case "re":
                return Map.RE;
            case "re.__init__":
                return Map.RE.asPackage(false);
            case "re._casefix":
                return Map.RE__CASEFIX;
            case "re._compiler":
                return Map.RE__COMPILER;
            case "re._constants":
                return Map.RE__CONSTANTS;
            case "re._parser":
                return Map.RE__PARSER;
            case "locale":
                return Map.LOCALE;
            case "rlcompleter":
                return Map.RLCOMPLETER;
            case "_collections_abc":
                return Map._COLLECTIONS_ABC;
            case "_sitebuiltins":
                return Map._SITEBUILTINS;
            case "genericpath":
                return Map.GENERICPATH;
            case "ntpath":
                return Map.NTPATH;
            case "posixpath":
                return Map.POSIXPATH;
            case "os.path":
                return PythonOS.getPythonOS() != PythonOS.PLATFORM_WIN32 ? Map.POSIXPATH : Map.NTPATH;
            case "os":
                return Map.OS;
            case "site":
                return Map.SITE;
            case "stat":
                return Map.STAT;
            case "datetime":
                return Map.DATETIME;
            case "contextlib":
                return Map.CONTEXTLIB;
            case "warnings":
                return Map.WARNINGS;
            case "inspect":
                return Map.INSPECT;
            case "__hello__":
                return Map.__HELLO__;
            case "__hello_alias__":
                return Map.__HELLO__;
            case "__phello_alias__":
                return Map.__HELLO__.asPackage(true);
            case "__phello_alias__.spam":
                return Map.__HELLO__;
            case "__phello__":
                return Map.__HELLO__.asPackage(true);
            case "__phello__.spam":
                return Map.__HELLO__;
            case "__hello_only__":
                return Map.FROZEN_ONLY;
            case "_sysconfigdata":
                return Map._SYSCONFIGDATA;
            case "graalpy.__graalpython__":
                return Map.GRAALPY___GRAALPYTHON__;
            case "graalpy._interop_behavior":
                return Map.GRAALPY__INTEROP_BEHAVIOR;
            case "graalpy._sre":
                return Map.GRAALPY__SRE;
            case "graalpy._struct":
                return Map.GRAALPY__STRUCT;
            case "graalpy._sysconfig":
                return Map.GRAALPY__SYSCONFIG;
            case "graalpy._weakref":
                return Map.GRAALPY__WEAKREF;
            case "graalpy.builtins":
                return Map.GRAALPY_BUILTINS;
            case "graalpy.function":
                return Map.GRAALPY_FUNCTION;
            case "graalpy.java":
                return Map.GRAALPY_JAVA;
            case "graalpy.pip_hook":
                return Map.GRAALPY_PIP_HOOK;
            case "graalpy.unicodedata":
                return Map.GRAALPY_UNICODEDATA;
            case "graalpy.sulong_support":
                return Map.GRAALPY_SULONG_SUPPORT;
            default:
                return null;
        }
    }
}
