/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.oracle.graal.python.pegparser.tokenizer;


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
        public static final int OP = 54;
        public static final int AWAIT = 55;
        public static final int ASYNC = 56;
        public static final int TYPE_IGNORE = 57;
        public static final int TYPE_COMMENT = 58;
        public static final int SOFT_KEYWORD = 59;
        public static final int ERRORTOKEN = 60;
    };

    public int type;
    public final int startOffset;
    public final int endOffset;
    public final int startLine;
    public final int startColumn;
    public final int endLine;
    public final int endColumn;
    public final Object extraData;

    public Token(int type, int startOffset, int endOffset, int startLine, int startColumn, int endLine, int endColumn) {
        this(type, startOffset, endOffset, startLine, startColumn, endLine, endColumn, null);
    }

    public Token(int type,
                 int startOffset, int endOffset,
                 int startLine, int startColumn,
                 int endLine, int endColumn,
                 Object extraData) {
        this.startOffset = startOffset;
        this.endOffset = endOffset;
        this.startLine = startLine;
        this.startColumn = startColumn;
        this.endLine = endLine;
        this.endColumn = endColumn;
        this.type = type;
        this.extraData = extraData;
    }

    static int oneChar(int c) {
        switch (c) {
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

}
