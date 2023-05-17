/*
 * Copyright (c) 2018, 2024, Oracle and/or its affiliates. All rights reserved.
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
    public static final TruffleString T_AMPERSAND = tsLiteral("&");
    public static final String J_EMPTY_STRING = "";
    public static final TruffleString T_EMPTY_STRING = tsLiteral(J_EMPTY_STRING);
    public static final String J_STRING_SOURCE = "<string>";
    public static final TruffleString T_STRING_SOURCE = tsLiteral(J_STRING_SOURCE);
    public static final String J_NEWLINE = "\n";
    public static final TruffleString T_NEWLINE = tsLiteral(J_NEWLINE);
    public static final TruffleString T_CRLF = tsLiteral("\r\n");
    public static final TruffleString T_CR = tsLiteral("\r");
    public static final TruffleString T_SPACE = tsLiteral(" ");
    public static final TruffleString T_DOT = tsLiteral(".");
    public static final String J_DASH = "-";
    public static final TruffleString T_DASH = tsLiteral(J_DASH);
    public static final TruffleString T_MINUS = T_DASH;
    public static final TruffleString T_UNDERSCORE = tsLiteral("_");
    public static final TruffleString T_ZERO = tsLiteral("0");
    public static final TruffleString T_NONE = tsLiteral("None");
    public static final TruffleString T_LPAREN = tsLiteral("(");
    public static final TruffleString T_RPAREN = tsLiteral(")");
    public static final TruffleString T_EQ = tsLiteral("=");
    public static final TruffleString T_QUESTIONMARK = tsLiteral("?");
    public static final TruffleString T_COMMA = tsLiteral(",");
    public static final TruffleString T_COLON = tsLiteral(":");
    public static final TruffleString T_COMMA_SPACE = tsLiteral(", ");
    public static final TruffleString T_LBRACE = tsLiteral("{");
    public static final TruffleString T_RBRACE = tsLiteral("}");
    public static final TruffleString T_LBRACKET = tsLiteral("[");
    public static final TruffleString T_RBRACKET = tsLiteral("]");
    public static final TruffleString T_LANGLE = tsLiteral("<");
    public static final TruffleString T_RANGLE = tsLiteral(">");
    public static final TruffleString T_SINGLE_QUOTE = tsLiteral("'");
    public static final TruffleString T_DOUBLE_QUOTE = tsLiteral("\"");
    public static final TruffleString T_SLASH = tsLiteral("/");
    public static final TruffleString T_EMPTY_PARENS = tsLiteral("()");
    public static final TruffleString T_EMPTY_BRACKETS = tsLiteral("[]");
    public static final TruffleString T_EMPTY_BRACES = tsLiteral("{}");
    public static final TruffleString T_ELLIPSIS = tsLiteral("...");
    public static final TruffleString T_ELLIPSIS_IN_PARENS = tsLiteral("(...)");
    public static final TruffleString T_ELLIPSIS_IN_BRACKETS = tsLiteral("[...]");
    public static final TruffleString T_DATE = tsLiteral("date");
    public static final TruffleString T_DATETIME = tsLiteral("datetime");
    public static final TruffleString T_TIME = tsLiteral("time");
    public static final String J_GET_ = "get_";
    public static final String J_SET_ = "set_";
    public static final TruffleString T_NAME = tsLiteral("name");
    public static final TruffleString T_PATH = tsLiteral("path");
    public static final TruffleString T_UTF8 = tsLiteral("utf-8");
    public static final TruffleString T_UTF_UNDERSCORE_8 = tsLiteral("utf_8");
    public static final String J_STRICT = "strict";
    public static final TruffleString T_STRICT = tsLiteral(J_STRICT);
    public static final TruffleString T_IGNORE = tsLiteral("ignore");
    public static final TruffleString T_REPLACE = tsLiteral("replace");
    public static final TruffleString T_ASCII_UPPERCASE = tsLiteral("ASCII");
    public static final TruffleString T_IDNA = tsLiteral("idna");
    public static final TruffleString T_TRUE = tsLiteral("True");
    public static final TruffleString T_FALSE = tsLiteral("False");
    public static final TruffleString T_BIG = tsLiteral("big");
    public static final TruffleString T_LITTLE = tsLiteral("little");
    public static final String J_READABLE = "readable";
    public static final TruffleString T_READABLE = tsLiteral(J_READABLE);
    public static final String J_WRITABLE = "writable";
    public static final TruffleString T_WRITABLE = tsLiteral(J_WRITABLE);
    public static final String J_TYPE_ID = "_typeid";
    public static final TruffleString T_TYPE_ID = tsLiteral(J_TYPE_ID);
    public static final String J_LIB_PREFIX = "lib";
    public static final String J_EXT_SO = ".so";
    public static final TruffleString T_EXT_SO = tsLiteral(J_EXT_SO);
    public static final String J_EXT_PYD = ".pyd";
    public static final TruffleString T_EXT_PYD = tsLiteral(J_EXT_PYD);
    public static final String J_EXT_DYLIB = ".dylib";
    public static final String J_EXT_DLL = ".dll";
    public static final String J_NATIVE = "native";
    public static final TruffleString T_NATIVE = tsLiteral(J_NATIVE);
    public static final TruffleString T_BACKSLASHREPLACE = tsLiteral("backslashreplace");
    public static final TruffleString T_NAMEREPLACE = tsLiteral("namereplace");
    public static final TruffleString T_XMLCHARREFREPLACE = tsLiteral("xmlcharrefreplace");
    public static final TruffleString T_SURROGATEESCAPE = tsLiteral("surrogateescape");
    public static final TruffleString T_SURROGATEPASS = tsLiteral("surrogatepass");
    public static final String J_JAVA = "java";
    public static final TruffleString T_JAVA = tsLiteral(J_JAVA);
    public static final TruffleString T_VALUE_UNKNOWN = tsLiteral("<unknown>");
    public static final String J_PY_EXTENSION = ".py";
    public static final TruffleString T_PY_EXTENSION = tsLiteral(J_PY_EXTENSION);
    public static final TruffleString T_VERSION = tsLiteral("version");
    public static final String J_DEFAULT = "default";
    public static final TruffleString T_DEFAULT = tsLiteral(J_DEFAULT);
    public static final String J_LLVM_LANGUAGE = "llvm";
    public static final TruffleString T_LLVM_LANGUAGE = tsLiteral(J_LLVM_LANGUAGE);
    public static final String J_NFI_LANGUAGE = "nfi";
    public static final TruffleString T_ID = tsLiteral("id");
    public static final TruffleString T_SITE = tsLiteral("site");
    public static final TruffleString T_GRAALPYTHON = tsLiteral("graalpy");
    public static final TruffleString T_WARNINGS = tsLiteral("warnings");
    public static final TruffleString T_REF = tsLiteral("ref");
    public static final TruffleString T_NULL_RESULT = tsLiteral("<NULL>");
    public static final TruffleString T_SINGLE = tsLiteral("single");
    public static final TruffleString T_EXEC = tsLiteral("exec");
    public static final TruffleString T_EVAL = tsLiteral("eval");
    public static final TruffleString T_FUNC_TYPE = tsLiteral("func_type");
    public static final TruffleString T_SUPER = tsLiteral("super");
    public static final String J_OB_REFCNT = "ob_refcnt";
    public static final String J_DEBUG = "debug";
    public static final String J_TRACE = "trace";
    public static final TruffleString T_READ = tsLiteral("read");
    public static final TruffleString T_READLINE = tsLiteral("readline");
    public static final TruffleString T_CODEC = tsLiteral("codec");
    public static final TruffleString T_STAR_ARGS = tsLiteral("*args");
    public static final TruffleString T_STAR_KWARGS = tsLiteral("**kwargs");
    public static final TruffleString T_ARGS = tsLiteral("args");
    public static final TruffleString T_KWARGS = tsLiteral("kwargs");
}
