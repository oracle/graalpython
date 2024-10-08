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
package com.oracle.graal.python.pegparser.tokenizer;

import com.oracle.graal.python.pegparser.sst.ConstantValue;

/**
 * Kept close to CPython's token.c
 */
public class Token {

    public static final class Kind {
        public static final int ENDMARKER = 0;
        public static final int NAME = 1;
        public static final int NUMBER = 2;
        public static final int STRING = 3;
        public static final int NEWLINE = 4;
        public static final int INDENT = 5;
        public static final int DEDENT = 6;
        public static final int LPAR = 7;
        public static final int RPAR = 8;
        public static final int LSQB = 9;
        public static final int RSQB = 10;
        public static final int COLON = 11;
        public static final int COMMA = 12;
        public static final int SEMI = 13;
        public static final int PLUS = 14;
        public static final int MINUS = 15;
        public static final int STAR = 16;
        public static final int SLASH = 17;
        public static final int VBAR = 18;
        public static final int AMPER = 19;
        public static final int LESS = 20;
        public static final int GREATER = 21;
        public static final int EQUAL = 22;
        public static final int DOT = 23;
        public static final int PERCENT = 24;
        public static final int LBRACE = 25;
        public static final int RBRACE = 26;
        public static final int EQEQUAL = 27;
        public static final int NOTEQUAL = 28;
        public static final int LESSEQUAL = 29;
        public static final int GREATEREQUAL = 30;
        public static final int TILDE = 31;
        public static final int CIRCUMFLEX = 32;
        public static final int LEFTSHIFT = 33;
        public static final int RIGHTSHIFT = 34;
        public static final int DOUBLESTAR = 35;
        public static final int PLUSEQUAL = 36;
        public static final int MINEQUAL = 37;
        public static final int STAREQUAL = 38;
        public static final int SLASHEQUAL = 39;
        public static final int PERCENTEQUAL = 40;
        public static final int AMPEREQUAL = 41;
        public static final int VBAREQUAL = 42;
        public static final int CIRCUMFLEXEQUAL = 43;
        public static final int LEFTSHIFTEQUAL = 44;
        public static final int RIGHTSHIFTEQUAL = 45;
        public static final int DOUBLESTAREQUAL = 46;
        public static final int DOUBLESLASH = 47;
        public static final int DOUBLESLASHEQUAL = 48;
        public static final int AT = 49;
        public static final int ATEQUAL = 50;
        public static final int RARROW = 51;
        public static final int ELLIPSIS = 52;
        public static final int COLONEQUAL = 53;
        public static final int EXCLAMATION = 54;
        public static final int OP = 55;
        public static final int AWAIT = 56;
        public static final int ASYNC = 57;
        public static final int TYPE_IGNORE = 58;
        public static final int TYPE_COMMENT = 59;
        public static final int SOFT_KEYWORD = 60;
        public static final int FSTRING_START = 61;
        public static final int FSTRING_MIDDLE = 62;
        public static final int FSTRING_END = 63;
        public static final int COMMENT = 64;
        public static final int NL = 65;
        public static final int ERRORTOKEN = 66;
        public static final int N_TOKENS = 68;

        public static final String[] TOKEN_NAMES = new String[]{
                        "ENDMARKER",
                        "NAME",
                        "NUMBER",
                        "STRING",
                        "NEWLINE",
                        "INDENT",
                        "DEDENT",
                        "LPAR",
                        "RPAR",
                        "LSQB",
                        "RSQB",
                        "COLON",
                        "COMMA",
                        "SEMI",
                        "PLUS",
                        "MINUS",
                        "STAR",
                        "SLASH",
                        "VBAR",
                        "AMPER",
                        "LESS",
                        "GREATER",
                        "EQUAL",
                        "DOT",
                        "PERCENT",
                        "LBRACE",
                        "RBRACE",
                        "EQEQUAL",
                        "NOTEQUAL",
                        "LESSEQUAL",
                        "GREATEREQUAL",
                        "TILDE",
                        "CIRCUMFLEX",
                        "LEFTSHIFT",
                        "RIGHTSHIFT",
                        "DOUBLESTAR",
                        "PLUSEQUAL",
                        "MINEQUAL",
                        "STAREQUAL",
                        "SLASHEQUAL",
                        "PERCENTEQUAL",
                        "AMPEREQUAL",
                        "VBAREQUAL",
                        "CIRCUMFLEXEQUAL",
                        "LEFTSHIFTEQUAL",
                        "RIGHTSHIFTEQUAL",
                        "DOUBLESTAREQUAL",
                        "DOUBLESLASH",
                        "DOUBLESLASHEQUAL",
                        "AT",
                        "ATEQUAL",
                        "RARROW",
                        "ELLIPSIS",
                        "COLONEQUAL",
                        "EXCLAMATION",
                        "OP",
                        "AWAIT",
                        "ASYNC",
                        "TYPE_IGNORE",
                        "TYPE_COMMENT",
                        "SOFT_KEYWORD",
                        "FSTRING_START",
                        "FSTRING_MIDDLE",
                        "FSTRING_END",
                        "COMMENT",
                        "NL",
                        "<ERRORTOKEN>",
                        "<ENCODING>",
                        "<N_TOKENS>",
        };
    }

    public int type;
    public int level;
    public final SourceRange sourceRange;
    public final Object extraData;
    public final int startOffset;
    public final int endOffset;
    public final ConstantValue metadata;

    public Token(int type, int level, int startOffset, int endOffset, SourceRange sourceRange, Object extraData, ConstantValue metadata) {
        this.type = type;
        this.level = level;
        this.startOffset = startOffset;
        this.endOffset = endOffset;
        this.sourceRange = sourceRange;
        this.extraData = extraData;
        this.metadata = metadata;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("Token ");
        if (this.type < Token.Kind.TOKEN_NAMES.length) {
            sb.append(Token.Kind.TOKEN_NAMES[this.type]);
        } else {
            sb.append(this.type);
        }
        sb.append(" [").append(this.startOffset).append(", ").append(this.endOffset).append(']');
        return sb.toString();
    }

    static int oneChar(int c) {
        switch (c) {
            case '!':
                return Kind.EXCLAMATION;
            case '%':
                return Kind.PERCENT;
            case '&':
                return Kind.AMPER;
            case '(':
                return Kind.LPAR;
            case ')':
                return Kind.RPAR;
            case '*':
                return Kind.STAR;
            case '+':
                return Kind.PLUS;
            case ',':
                return Kind.COMMA;
            case '-':
                return Kind.MINUS;
            case '.':
                return Kind.DOT;
            case '/':
                return Kind.SLASH;
            case ':':
                return Kind.COLON;
            case ';':
                return Kind.SEMI;
            case '<':
                return Kind.LESS;
            case '=':
                return Kind.EQUAL;
            case '>':
                return Kind.GREATER;
            case '@':
                return Kind.AT;
            case '[':
                return Kind.LSQB;
            case ']':
                return Kind.RSQB;
            case '^':
                return Kind.CIRCUMFLEX;
            case '{':
                return Kind.LBRACE;
            case '|':
                return Kind.VBAR;
            case '}':
                return Kind.RBRACE;
            case '~':
                return Kind.TILDE;
        }
        return Kind.OP;
    }

    static int twoChars(int c1, int c2) {
        switch (c1) {
            case '!':
                switch (c2) {
                    case '=':
                        return Kind.NOTEQUAL;
                }
                break;
            case '%':
                switch (c2) {
                    case '=':
                        return Kind.PERCENTEQUAL;
                }
                break;
            case '&':
                switch (c2) {
                    case '=':
                        return Kind.AMPEREQUAL;
                }
                break;
            case '*':
                switch (c2) {
                    case '*':
                        return Kind.DOUBLESTAR;
                    case '=':
                        return Kind.STAREQUAL;
                }
                break;
            case '+':
                switch (c2) {
                    case '=':
                        return Kind.PLUSEQUAL;
                }
                break;
            case '-':
                switch (c2) {
                    case '=':
                        return Kind.MINEQUAL;
                    case '>':
                        return Kind.RARROW;
                }
                break;
            case '/':
                switch (c2) {
                    case '/':
                        return Kind.DOUBLESLASH;
                    case '=':
                        return Kind.SLASHEQUAL;
                }
                break;
            case ':':
                switch (c2) {
                    case '=':
                        return Kind.COLONEQUAL;
                }
                break;
            case '<':
                switch (c2) {
                    case '<':
                        return Kind.LEFTSHIFT;
                    case '=':
                        return Kind.LESSEQUAL;
                    case '>':
                        return Kind.NOTEQUAL;
                }
                break;
            case '=':
                switch (c2) {
                    case '=':
                        return Kind.EQEQUAL;
                }
                break;
            case '>':
                switch (c2) {
                    case '=':
                        return Kind.GREATEREQUAL;
                    case '>':
                        return Kind.RIGHTSHIFT;
                }
                break;
            case '@':
                switch (c2) {
                    case '=':
                        return Kind.ATEQUAL;
                }
                break;
            case '^':
                switch (c2) {
                    case '=':
                        return Kind.CIRCUMFLEXEQUAL;
                }
                break;
            case '|':
                switch (c2) {
                    case '=':
                        return Kind.VBAREQUAL;
                }
                break;
        }
        return Kind.OP;
    }

    static int threeChars(int c1, int c2, int c3) {
        switch (c1) {
            case '*':
                switch (c2) {
                    case '*':
                        switch (c3) {
                            case '=':
                                return Kind.DOUBLESTAREQUAL;
                        }
                        break;
                }
                break;
            case '.':
                switch (c2) {
                    case '.':
                        switch (c3) {
                            case '.':
                                return Kind.ELLIPSIS;
                        }
                        break;
                }
                break;
            case '/':
                switch (c2) {
                    case '/':
                        switch (c3) {
                            case '=':
                                return Kind.DOUBLESLASHEQUAL;
                        }
                        break;
                }
                break;
            case '<':
                switch (c2) {
                    case '<':
                        switch (c3) {
                            case '=':
                                return Kind.LEFTSHIFTEQUAL;
                        }
                        break;
                }
                break;
            case '>':
                switch (c2) {
                    case '>':
                        switch (c3) {
                            case '=':
                                return Kind.RIGHTSHIFTEQUAL;
                        }
                        break;
                }
                break;
        }
        return Kind.OP;
    }

    public String typeName() {
        if (type < Kind.TOKEN_NAMES.length) {
            return Kind.TOKEN_NAMES[type];
        } else {
            return Integer.toString(type);
        }
    }

    public SourceRange getSourceRange() {
        return sourceRange;
    }
}
