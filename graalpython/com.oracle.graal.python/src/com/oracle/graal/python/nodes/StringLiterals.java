/*
 * Copyright (c) 2018, 2022, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.nodes;

import static com.oracle.graal.python.util.PythonUtils.tsLiteral;

import com.oracle.truffle.api.strings.TruffleString;

public abstract class StringLiterals {
    public static final String J_AMPERSAND = "&";
    public static final TruffleString T_AMPERSAND = tsLiteral(J_AMPERSAND);

    public static final String J_EMPTY_STRING = "";
    public static final TruffleString T_EMPTY_STRING = tsLiteral(J_EMPTY_STRING);

    public static final String J_STRING_SOURCE = "<string>";
    public static final TruffleString T_STRING_SOURCE = tsLiteral(J_STRING_SOURCE);

    public static final String J_NEWLINE = "\n";
    public static final TruffleString T_NEWLINE = tsLiteral(J_NEWLINE);

    public static final String J_CRLF = "\r\n";
    public static final TruffleString T_CRLF = tsLiteral(J_CRLF);

    public static final String J_CR = "\r";
    public static final TruffleString T_CR = tsLiteral(J_CR);

    public static final String J_SPACE = " ";
    public static final TruffleString T_SPACE = tsLiteral(J_SPACE);

    public static final String J_DOT = ".";
    public static final TruffleString T_DOT = tsLiteral(J_DOT);

    public static final String J_DASH = "-";
    public static final TruffleString T_DASH = tsLiteral(J_DASH);

    public static final String J_MINUS = J_DASH;
    public static final TruffleString T_MINUS = T_DASH;

    public static final String J_UNDERSCORE = "_";
    public static final TruffleString T_UNDERSCORE = tsLiteral(J_UNDERSCORE);

    public static final String J_ZERO = "0";
    public static final TruffleString T_ZERO = tsLiteral(J_ZERO);

    public static final String J_NONE = "None";
    public static final TruffleString T_NONE = tsLiteral(J_NONE);

    public static final String J_LPAREN = "(";
    public static final TruffleString T_LPAREN = tsLiteral(J_LPAREN);

    public static final String J_RPAREN = ")";
    public static final TruffleString T_RPAREN = tsLiteral(J_RPAREN);

    public static final String J_EQ = "=";
    public static final TruffleString T_EQ = tsLiteral(J_EQ);

    public static final String J_QUESTIONMARK = "?";
    public static final TruffleString T_QUESTIONMARK = tsLiteral(J_QUESTIONMARK);

    public static final String J_COMMA = ",";
    public static final TruffleString T_COMMA = tsLiteral(J_COMMA);

    public static final String J_COLON = ":";
    public static final TruffleString T_COLON = tsLiteral(J_COLON);

    public static final String J_COMMA_SPACE = ", ";
    public static final TruffleString T_COMMA_SPACE = tsLiteral(J_COMMA_SPACE);

    public static final String J_LBRACE = "{";
    public static final TruffleString T_LBRACE = tsLiteral(J_LBRACE);

    public static final String J_RBRACE = "}";
    public static final TruffleString T_RBRACE = tsLiteral(J_RBRACE);

    public static final String J_LBRACKET = "[";
    public static final TruffleString T_LBRACKET = tsLiteral(J_LBRACKET);

    public static final String J_RBRACKET = "]";
    public static final TruffleString T_RBRACKET = tsLiteral(J_RBRACKET);

    public static final String J_LANGLE = "<";
    public static final TruffleString T_LANGLE = tsLiteral(J_LANGLE);

    public static final String J_RANGLE = ">";
    public static final TruffleString T_RANGLE = tsLiteral(J_RANGLE);

    public static final String J_SINGLE_QUOTE = "'";
    public static final TruffleString T_SINGLE_QUOTE = tsLiteral(J_SINGLE_QUOTE);

    public static final String J_DOUBLE_QUOTE = "\"";
    public static final TruffleString T_DOUBLE_QUOTE = tsLiteral(J_DOUBLE_QUOTE);

    public static final String J_SLASH = "/";
    public static final TruffleString T_SLASH = tsLiteral(J_SLASH);

    public static final String J_EMPTY_PARENS = "()";
    public static final TruffleString T_EMPTY_PARENS = tsLiteral(J_EMPTY_PARENS);

    public static final String J_EMPTY_BRACKETS = "[]";
    public static final TruffleString T_EMPTY_BRACKETS = tsLiteral(J_EMPTY_BRACKETS);

    public static final String J_EMPTY_BRACES = "{}";
    public static final TruffleString T_EMPTY_BRACES = tsLiteral(J_EMPTY_BRACES);

    public static final String J_ELLIPSIS = "...";
    public static final TruffleString T_ELLIPSIS = tsLiteral(J_ELLIPSIS);

    public static final String J_ELLIPSIS_IN_PARENS = "(...)";
    public static final TruffleString T_ELLIPSIS_IN_PARENS = tsLiteral(J_ELLIPSIS_IN_PARENS);

    public static final String J_ELLIPSIS_IN_BRACKETS = "[...]";
    public static final TruffleString T_ELLIPSIS_IN_BRACKETS = tsLiteral(J_ELLIPSIS_IN_BRACKETS);

    public static final String J_DATE = "date";
    public static final TruffleString T_DATE = tsLiteral(J_DATE);

    public static final String J_DATETIME = "datetime";
    public static final TruffleString T_DATETIME = tsLiteral(J_DATETIME);

    public static final String J_TIME = "time";
    public static final TruffleString T_TIME = tsLiteral(J_TIME);

    public static final String J_STRUCT_TIME = "struct_time";
    public static final TruffleString T_STRUCT_TIME = tsLiteral(J_STRUCT_TIME);

    public static final String J_GET_ = "get_";
    public static final TruffleString T_GET_ = tsLiteral(J_GET_);

    public static final String J_NAME = "name";
    public static final TruffleString T_NAME = tsLiteral(J_NAME);

    public static final String J_PATH = "path";
    public static final TruffleString T_PATH = tsLiteral(J_PATH);

    public static final String J_UTF8 = "utf-8";
    public static final TruffleString T_UTF8 = tsLiteral(J_UTF8);

    public static final String J_UTF_UNDERSCORE_8 = "utf_8";
    public static final TruffleString T_UTF_UNDERSCORE_8 = tsLiteral(J_UTF_UNDERSCORE_8);

    public static final String J_STRICT = "strict";
    public static final TruffleString T_STRICT = tsLiteral(J_STRICT);

    public static final String J_IGNORE = "ignore";
    public static final TruffleString T_IGNORE = tsLiteral(J_IGNORE);

    public static final String J_REPLACE = "replace";
    public static final TruffleString T_REPLACE = tsLiteral(J_REPLACE);

    public static final String J_ASCII_UPPERCASE = "ASCII";
    public static final TruffleString T_ASCII_UPPERCASE = tsLiteral(J_ASCII_UPPERCASE);

    public static final String J_TRUE = "True";
    public static final TruffleString T_TRUE = tsLiteral(J_TRUE);

    public static final String J_FALSE = "False";
    public static final TruffleString T_FALSE = tsLiteral(J_FALSE);

    public static final String J_BIG = "big";
    public static final TruffleString T_BIG = tsLiteral(J_BIG);

    public static final String J_LITTLE = "little";
    public static final TruffleString T_LITTLE = tsLiteral(J_LITTLE);

    public static final String J_READABLE = "readable";
    public static final TruffleString T_READABLE = tsLiteral(J_READABLE);

    public static final String J_WRITABLE = "writable";
    public static final TruffleString T_WRITABLE = tsLiteral(J_WRITABLE);

    public static final TruffleString T_TYPE_ID = tsLiteral("_typeid");

    public static final TruffleString T_HPY_SUFFIX = tsLiteral(".hpy.so");
    public static final TruffleString T_EXT_SO = tsLiteral(".so");
    public static final TruffleString T_EXT_DYLIB = tsLiteral(".dylib");
    public static final TruffleString T_EXT_SU = tsLiteral(".su");

    public static final TruffleString T_NATIVE = tsLiteral("native");

    public static final TruffleString T_BACKSLASHREPLACE = tsLiteral("backslashreplace");
    public static final TruffleString T_NAMEREPLACE = tsLiteral("namereplace");
    public static final TruffleString T_XMLCHARREFREPLACE = tsLiteral("xmlcharrefreplace");
    public static final TruffleString T_SURROGATEESCAPE = tsLiteral("surrogateescape");
    public static final TruffleString T_SURROGATEPASS = tsLiteral("surrogatepass");

    public static final String J_JAVA = "java";
    public static final TruffleString T_JAVA = tsLiteral(J_JAVA);

    public static final String J_LOCAL = "local";
    public static final TruffleString T_LOCAL = tsLiteral(J_LOCAL);

    public static final String J_VALUE_UNKNOWN = "<unknown>";
    public static final TruffleString T_VALUE_UNKNOWN = tsLiteral(J_VALUE_UNKNOWN);

    public static final String J_PY_EXTENSION = ".py";
    public static final TruffleString T_PY_EXTENSION = tsLiteral(J_PY_EXTENSION);

    public static final String J_VERSION = "version";
    public static final TruffleString T_VERSION = tsLiteral(J_VERSION);

    public static final TruffleString T_DEFAULT = tsLiteral("default");

    public static final String J_LLVM_LANGUAGE = "llvm";
    public static final TruffleString T_LLVM_LANGUAGE = tsLiteral(J_LLVM_LANGUAGE);

    public static final String J_ID = "id";
    public static final TruffleString T_ID = tsLiteral(J_ID);

    public static final String J_SITE = "site";
    public static final TruffleString T_SITE = tsLiteral(J_SITE);

    public static final String J_GRAALPYTHON = "graalpython";
    public static final TruffleString T_GRAALPYTHON = tsLiteral(J_GRAALPYTHON);

    public static final TruffleString T_WARNINGS = tsLiteral("warnings");

    public static final String J_REF = "ref";
    public static final TruffleString T_REF = tsLiteral(J_REF);

}
